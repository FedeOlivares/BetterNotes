package app.betternotes.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.library.Library
import app.betternotes.core.library.LibraryEntry
import app.betternotes.core.library.LibraryStore
import app.betternotes.core.library.LibraryView
import app.betternotes.core.library.ScannedDocument
import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.pdf.PdfImportResult
import app.betternotes.pdf.PdfImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The library for the whole app. The screen sees changes at once, because they apply here on the main
 * thread, and one background thread at a time does the matching disk work.
 *
 * Call everything from the main thread.
 */
class LibraryRepository private constructor(context: Context) {
    private val store = LibraryStore(context.filesDir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val disk = Dispatchers.IO.limitedParallelism(1)
    private val current = MutableStateFlow<Library?>(null)

    /** Null until the saved library has been read. */
    val library: StateFlow<Library?> = current.asStateFlow()

    val thumbnails = ThumbnailStore(context, store)

    init {
        scope.launch {
            current.value = withContext(disk) { readSaved() }
        }
    }

    fun documentStore(id: Long): DocumentStore = store.documentStore(id)

    /**
     * Compares the library with the documents on the disk, and corrects what differs. Nothing needs to
     * tell the library about a saved note: it finds out here, when it comes back into view.
     */
    fun refresh() {
        scope.launch {
            val snapshot = current.filterNotNull().first()
            val (scanned, missing) = withContext(disk) {
                val onDisk = store.scan()
                val changed = snapshot.staleDocuments(onDisk).mapNotNull { id -> readOrNull(id, onDisk.getValue(id)) }
                changed to snapshot.missingDocuments(onDisk)
            }
            if (scanned.isNotEmpty() || missing.isNotEmpty()) update { it.afterScan(scanned, missing) }
        }
    }

    /** Creates a note of one A4 page, and passes its id on once it is on the disk. */
    fun createNote(title: String, folderId: Long?, onCreated: (Long) -> Unit) = create(folderId, onCreated) { now ->
        Document.newNote(title, PageSize.A4, newPageBackground(), now)
    }

    /** Creates an infinite canvas, and passes its id on once it is on the disk. */
    fun createCanvas(title: String, folderId: Long?, onCreated: (Long) -> Unit) = create(folderId, onCreated) { now ->
        Document.newCanvas(title, newPageBackground(), now)
    }

    fun createFolder(name: String, parentId: Long?) {
        val entry = LibraryEntry(IdSource.Random.next(), name, parentId, System.currentTimeMillis())
        update { it.plus(entry) }
    }

    /** Starts a note with one page per picture, and passes its id on once it is on the disk. */
    fun createFromPhotos(
        resolver: ContentResolver,
        uris: List<Uri>,
        title: String,
        layout: PhotoLayout,
        folderId: Long?,
        onFailed: () -> Unit,
        onCreated: (Long) -> Unit,
    ) = createFromMedia(folderId, onFailed, onCreated) { documents, now ->
        MediaImport.photos(resolver, uris, documents, title, layout, newPageBackground(), now)
    }

    /** Starts a note holding a recording, and passes its id on once it is on the disk. */
    fun createFromAudio(
        resolver: ContentResolver,
        uri: Uri,
        title: String,
        folderId: Long?,
        onFailed: () -> Unit,
        onCreated: (Long) -> Unit,
    ) = createFromMedia(folderId, onFailed, onCreated) { documents, now ->
        MediaImport.audio(resolver, uri, documents, title, newPageBackground(), now)
    }

    /** Suspends while the file is copied. On success the document is already in the library. */
    suspend fun importPdf(resolver: ContentResolver, uri: Uri, folderId: Long?, password: String?): PdfImportResult {
        val result = withContext(Dispatchers.IO) { PdfImporter.import(resolver, uri, store.documentsDir, password) }
        if (result is PdfImportResult.Imported) {
            val savedAtMs = withContext(Dispatchers.IO) { result.store.descriptionModifiedMs() }
            add(ScannedDocument(result.document, savedAtMs), folderId)
        }
        return result
    }

    fun rename(entry: LibraryEntry, name: String) {
        update { it.renamed(entry.id, name) }
        if (entry.document == null) return
        scope.launch {
            withContext(disk) {
                try {
                    val documents = store.documentStore(entry.id)
                    documents.writeDocument(documents.readDocument().apply { title = name })
                } catch (e: IOException) {
                    Log.e(TAG, "Could not rename document ${entry.id}", e)
                }
            }
            refresh()
        }
    }

    fun setStarred(id: Long, starred: Boolean) = update { it.withStar(id, starred) }

    fun setTags(id: Long, tags: Set<String>) = update { it.withTags(id, tags) }

    fun move(id: Long, folderId: Long?) = update { it.moved(id, folderId) }

    fun trash(id: Long) = update { it.trashed(id, System.currentTimeMillis()) }

    fun restore(id: Long) = update { it.restored(id) }

    /** Removes an item and what is inside it for good, files included. */
    fun deleteForever(id: Long) {
        val documents = current.value?.documentsWithin(id) ?: return
        update { it.deleted(id) }
        scope.launch(disk) {
            for (document in documents) {
                store.deleteDocument(document)
                thumbnails.forget(document)
            }
        }
    }

    fun emptyBin() {
        current.value?.list(LibraryView.Bin)?.forEach { deleteForever(it.id) }
    }

    private fun create(folderId: Long?, onCreated: (Long) -> Unit, build: (nowMs: Long) -> Document) {
        scope.launch {
            val scanned = withContext(disk) {
                val document = build(System.currentTimeMillis())
                val documents = store.documentStore(document.id)
                documents.writeDocument(document)
                ScannedDocument(document, documents.descriptionModifiedMs())
            }
            add(scanned, folderId)
            onCreated(scanned.id)
        }
    }

    /**
     * The document's id is only known once it has been built, so it is written in a temporary folder
     * and renamed at the end. A failed or interrupted import leaves no document behind.
     */
    private fun createFromMedia(
        folderId: Long?,
        onFailed: () -> Unit,
        onCreated: (Long) -> Unit,
        build: (DocumentStore, nowMs: Long) -> Document,
    ) {
        scope.launch {
            val scanned = withContext(Dispatchers.IO) {
                val temp = File(store.documentsDir, ".import-" + System.nanoTime())
                try {
                    val document = build(DocumentStore(temp), System.currentTimeMillis())
                    val documents = store.documentStore(document.id)
                    Files.move(temp.toPath(), documents.root.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    ScannedDocument(document, documents.descriptionModifiedMs())
                } catch (e: IOException) {
                    Log.e(TAG, "Could not import", e)
                    temp.deleteRecursively()
                    null
                }
            }
            if (scanned == null) {
                onFailed()
            } else {
                add(scanned, folderId)
                onCreated(scanned.id)
            }
        }
    }

    private fun add(scanned: ScannedDocument, folderId: Long?) = update { library ->
        val added = library.afterScan(listOf(scanned), emptySet())
        if (added.canMove(scanned.id, folderId)) added.moved(scanned.id, folderId) else added
    }

    private fun update(change: (Library) -> Library) {
        val next = change(current.value ?: return)
        current.value = next
        scope.launch(disk) {
            // Only the newest matters, so a burst of changes writes once.
            if (current.value !== next) return@launch
            try {
                store.write(next)
            } catch (e: IOException) {
                Log.e(TAG, "Could not save the library", e)
            }
        }
    }

    /** A damaged file costs the folders and stars. The documents are safe, and a scan finds them again. */
    private fun readSaved(): Library = try {
        store.read()
    } catch (e: IOException) {
        Log.e(TAG, "Could not read the library", e)
        Library()
    }

    private fun readOrNull(id: Long, savedAtMs: Long): ScannedDocument? = try {
        store.readDocument(id, savedAtMs)
    } catch (e: IOException) {
        Log.e(TAG, "Could not read document $id", e)
        null
    }

    private fun newPageBackground() = PageBackground(template = PageTemplate.Dotted())

    companion object {
        private const val TAG = "Library"

        @Volatile
        private var instance: LibraryRepository? = null

        fun get(context: Context): LibraryRepository = instance ?: synchronized(this) {
            instance ?: LibraryRepository(context.applicationContext).also { instance = it }
        }
    }
}
