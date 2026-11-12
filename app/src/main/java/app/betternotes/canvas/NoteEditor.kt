package app.betternotes.canvas

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.ink.strokes.Stroke
import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.StepKind
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.format.PendingSave
import app.betternotes.core.model.Document
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.StrokeItem
import java.io.File
import app.betternotes.core.search.TextMatch
import app.betternotes.pdf.PdfPages
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import app.betternotes.core.geometry.PageSize
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * An open document as the canvas sees it. Everything here runs on the main thread, except disk work
 * and meshing, which go to one background thread so that drawing never waits on them.
 */
class NoteEditor(
    internal val store: DocumentStore,
    private val session: DocumentSession,
) {
    internal val strokes = StrokeCache()
    internal val ink = InkRenderer()

    /** The PDF behind this document's pages, if it has any. */
    internal val pdf: PdfPages? =
        if (session.document.pages.any { it.source is PageSource.PdfPage }) PdfPages(store, session.document) else null
    internal val paintSource = object : PaintSource {
        override val pages get() = document.pages
        override fun contentOrLoad(pageId: Long) = this@NoteEditor.contentOrLoad(pageId)
        override fun mesh(item: StrokeItem) = strokes.get(item)
        override fun opaqueMesh(item: StrokeItem) = strokes.getOpaque(item)
        override fun image(assetId: String) = imageOrLoad(assetId)

        override fun paintPdfTile(
            canvas: Canvas,
            page: Page,
            pdf: PageSource.PdfPage,
            originX: Float,
            originY: Float,
            x: Double,
            y: Double,
            scale: Float,
        ) {
            this@NoteEditor.pdf?.paint(canvas, page, pdf, originX, originY, x, y, scale)
        }
    }

    /** Called on the main thread when a page's ink arrives from the disk. */
    var onPageLoaded: (pageId: Long) -> Unit = {}

    /** Copies a video into the document, off the main thread, and answers on the main thread. */
    internal fun importVideo(resolver: ContentResolver, uri: Uri, done: (ImportedVideo?) -> Unit) {
        if (closed) return
        background.execute {
            val imported = images.importVideo(resolver, uri)
            main.post { done(imported) }
        }
    }

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

    /** Copies a PDF into the assets and reads its page sizes, off the main thread. Null if it cannot be read. */
    fun importPdf(resolver: ContentResolver, uri: Uri, done: (assetId: String, sizes: List<PageSize>?) -> Unit) {
        if (closed) return
        background.execute {
            val assetId = UUID.randomUUID().toString()
            val sizes = try {
                val opened = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                val file = store.assetFile(assetId)
                opened.use { input -> file.outputStream().use { input.copyTo(it) } }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        List(renderer.pageCount) { i -> renderer.openPage(i).use { PageSize(it.width.toFloat(), it.height.toFloat()) } }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Could not import a PDF", e)
                null
            } catch (e: SecurityException) {
                null // an encrypted file
            }
            if (sizes == null) store.assetFile(assetId).delete()
            main.post { done(assetId, sizes?.takeIf { it.isNotEmpty() }) }
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
    /** Called on the main thread after edits reach the disk, for whatever reads the saved files. */
    var onSaved: () -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor { task -> Thread(task, "note-io") }
    private val loading = HashSet<Long>()
    private val autosave = Runnable { saveInBackground() }
    private var closed = false

    val document: Document get() = session.document

    /** The page's items if they are in memory. If they are not, starts loading them and returns null. */
    fun contentOrLoad(pageId: Long): PageContent? {
        val content = session.loadedContent(pageId)
        if (content == null && !closed && loading.add(pageId)) loadInBackground(pageId)
        return content
    }

    /** The page's items, reading them right here if need be, for an edit that cannot wait. */
    fun content(pageId: Long): PageContent = session.content(pageId)

    /** The page's items if they are in memory, with no side effects. */
    fun loadedPageOrNull(pageId: Long): PageContent? = session.loadedContent(pageId)

    /** Makes [edit] and records it; of a [kind] other than a step, it is not a step of undo and redo of its own, as a post-it opening or closing is not. */
    fun perform(edit: Edit, kind: StepKind = StepKind.STEP) {
        session.history.perform(edit, session, kind)
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
    /**
     * Searches the text of the document's PDF pages, in page order, and replaces any search under way.
     * [onMatches] gets each page's matches as they are found. A document with no PDF finishes at once.
     */
    fun searchPdfText(query: String, onMatches: (List<TextMatch>) -> Unit, onFinished: () -> Unit) {
        val pdf = pdf
        if (pdf == null) onFinished() else pdf.search(query, onMatches, onFinished)
    }

    fun cancelPdfSearch() {
        pdf?.cancelSearch()
    }


    fun redo(): Boolean = session.history.redo(session).also { if (it) historyStepped() }

    fun retainPages(pageIds: Set<Long>) {
        session.retainPages(pageIds)
        strokes.sweep(session)
    }

    /** The page to continue from, if one was saved and is still in the document. */
    fun lastPageIndex(): Int = store.readLastPage()?.let(document::indexOfPage) ?: -1

    /** Remembers the page to continue from. It doesn't matter if this write is lost. */
    fun rememberLastPage(pageId: Long) {
        if (closed) return
        background.execute {
            try {
                store.writeLastPage(pageId)
            } catch (e: IOException) {
                Log.w(TAG, "Could not remember the page", e)
            }
        }
    }

    /** Writes everything and waits, for when the app is leaving and may be killed. */
    fun saveNow() {
        main.removeCallbacks(autosave)
        val pending = session.prepareSave() ?: return
        background.submit { write(pending) }.get()
        session.finishSave(pending)
        onSaved()
    }

    /** Saves and stops the background thread. Only a stroke that had already ended can still arrive. */
    fun close() {
        saveNow()
        closed = true
        background.shutdown()
        pdf?.close()
    }

    /** Remembers the angle a page is shown at. It is not an edit, so undo does not turn it back. */
    fun setPageRotation(pageId: Long, degrees: Float) {
        if (closed) return
        document.setPageRotation(pageId, degrees)
        scheduleSave()
    }

    private fun historyStepped() {
        scheduleSave()
        onHistoryChanged()
    }

    /** The picture, decoded here and now if it is not yet. For a background thread, as the bucket's, never the main one. */
    fun imageNow(assetId: String): Bitmap? = images.cached(assetId) ?: images.decode(assetId)

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
            if (write(pending)) {
                main.post {
                    session.finishSave(pending)
                    onSaved()
                }
            }
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
            val page = try {
                store.readPageWithLayers(pageId)
            } catch (e: IOException) {
                Log.e(TAG, "Could not read page $pageId", e)
                main.post { loading.remove(pageId) }
                return@execute
            }
            val meshed = meshAll(page.items)
            ink.warm(meshed.map { it.second })
            main.post { install(pageId, page, meshed) }
        }
    }

    private fun install(pageId: Long, page: app.betternotes.core.format.DecodedPage, meshed: List<Pair<StrokeItem, Stroke>>) {
        loading.remove(pageId)
        if (!session.install(pageId, page.items, page.layers)) return
        for ((item, stroke) in meshed) strokes.put(item, stroke)
        onPageLoaded(pageId)
    }

    private companion object {
        const val TAG = "NoteEditor"
        const val AUTOSAVE_DELAY_MS = 1000L
    }
}
