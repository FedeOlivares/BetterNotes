package app.betternotes.canvas

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.ink.strokes.Stroke
import app.betternotes.core.edit.Edit
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.format.PendingSave
import app.betternotes.core.model.Document
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * An open document as the canvas sees it. Everything here runs on the main thread, except disk work
 * and meshing, which go to one background thread so that drawing never waits on them.
 */
class NoteEditor(
    private val store: DocumentStore,
    private val session: DocumentSession,
) {
    internal val strokes = StrokeCache()

    /** Called on the main thread when a page's ink arrives from the disk. */
    var onPageLoaded: (pageId: Long) -> Unit = {}

    /** The file an asset lives in. */
    fun assetFile(assetId: String): File = store.assetFile(assetId)

    /** Copies any file into the document's assets, off the main thread, and answers on the main thread. */
    fun importAsset(resolver: ContentResolver, uri: Uri, done: (assetId: String?) -> Unit) {
        if (closed) return
        background.execute {
            val assetId = UUID.randomUUID().toString()
            val copied = try {
                val opened = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                opened.use { input -> store.assetFile(assetId).outputStream().use { input.copyTo(it) } }
                true
            } catch (e: IOException) {
                Log.e(TAG, "Could not import a file", e)
                false
            }
            main.post { done(if (copied) assetId else null) }
        }
    }

    /** Called on the main thread when a picture has been decoded and can now be painted. */
    var onImageLoaded: () -> Unit = {}

    private val images = ImageAssets(store)
    private val decoding = HashSet<String>()

    /** Called whenever what can be undone or redone may have changed. */
    var onHistoryChanged: () -> Unit = {}

    val canUndo get() = session.history.canUndo
    val canRedo get() = session.history.canRedo

    private val main = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor { task -> Thread(task, "note-io") }
    private val loading = HashSet<Long>()
    private val autosave = Runnable { saveInBackground() }
    private var closed = false

    val document: Document get() = session.document

    /** The page's items if they are in memory. If they are not, starts loading them and returns null. */
    fun contentOrLoad(pageId: Long): PageContent? {
        val content = session.loadedContent(pageId)
        if (content == null && loading.add(pageId)) loadInBackground(pageId)
        return content
    }

    /** The page's items, reading them right here if need be, for an edit that cannot wait. */
    fun content(pageId: Long): PageContent = session.content(pageId)

    /** The page's items if they are in memory, with no side effects. */
    fun loadedPageOrNull(pageId: Long): PageContent? = session.loadedContent(pageId)

    fun perform(edit: Edit) {
        session.history.perform(edit, session)
        scheduleSave()
        onHistoryChanged()
    }

    /** Records an edit whose changes are already on the page. */
    fun record(edit: Edit) {
        session.history.record(edit)
        scheduleSave()
        onHistoryChanged()
    }

    /** Takes back an edit that was never recorded. */
    fun discard(edit: Edit) {
        edit.revert(session)
        scheduleSave()
    }

    fun undo(): Boolean = session.history.undo(session).also { if (it) historyStepped() }

    fun redo(): Boolean = session.history.redo(session).also { if (it) historyStepped() }

    fun retainPages(pageIds: Set<Long>) {
        session.retainPages(pageIds)
        strokes.sweep(session)
    }

    /** Writes everything and waits, for when the app is leaving and may be killed. */
    fun saveNow() {
        main.removeCallbacks(autosave)
        val pending = session.prepareSave() ?: return
        background.submit { write(pending) }.get()
        session.finishSave(pending)
    }

    /** Saves and stops the background thread. Only a stroke that had already ended can still arrive. */
    fun close() {
        saveNow()
        closed = true
        background.shutdown()
    }

    private fun historyStepped() {
        scheduleSave()
        onHistoryChanged()
    }

    /** The picture if it is decoded. If it is not, starts decoding it and returns null. */
    fun imageOrLoad(assetId: String): Bitmap? {
        val bitmap = images.cached(assetId)
        if (bitmap != null || closed || images.isMissing(assetId) || !decoding.add(assetId)) return bitmap
        background.execute {
            val decoded = images.decode(assetId)
            main.post {
                decoding.remove(assetId)
                if (decoded != null) images.install(assetId, decoded) else images.markMissing(assetId)
                onImageLoaded()
            }
        }
        return null
    }

    /** Copies a picture into the document, off the main thread, and answers on the main thread. */
    internal fun importImage(resolver: ContentResolver, uri: Uri, done: (ImportedImage?) -> Unit) {
        if (closed) return
        background.execute {
            val imported = images.import(resolver, uri)
            main.post { done(imported) }
        }
    }

    private fun scheduleSave() {
        if (closed) {
            session.prepareSave()?.let { if (write(it)) session.finishSave(it) }
            return
        }
        main.removeCallbacks(autosave)
        main.postDelayed(autosave, AUTOSAVE_DELAY_MS)
    }

    private fun saveInBackground() {
        val pending = session.prepareSave() ?: return
        background.execute {
            if (write(pending)) main.post { session.finishSave(pending) }
        }
    }

    /** A failed write leaves the page marked unsaved, so the next save tries again. */
    private fun write(pending: PendingSave): Boolean = try {
        pending.write()
        true
    } catch (e: IOException) {
        Log.e(TAG, "Could not save the document", e)
        false
    }

    private fun loadInBackground(pageId: Long) {
        background.execute {
            val items = try {
                store.readPage(pageId)
            } catch (e: IOException) {
                Log.e(TAG, "Could not read page $pageId", e)
                main.post { loading.remove(pageId) }
                return@execute
            }
            val meshed = meshAll(items)
            main.post { install(pageId, items, meshed) }
        }
    }

    private fun install(pageId: Long, items: List<PageItem>, meshed: List<Pair<StrokeItem, Stroke>>) {
        loading.remove(pageId)
        if (!session.install(pageId, items)) return
        for ((item, stroke) in meshed) strokes.put(item, stroke)
        onPageLoaded(pageId)
    }

    private companion object {
        const val TAG = "NoteEditor"
        const val AUTOSAVE_DELAY_MS = 1000L
    }
}
