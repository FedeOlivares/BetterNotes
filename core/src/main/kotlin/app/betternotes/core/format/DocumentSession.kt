package app.betternotes.core.format

import app.betternotes.core.edit.EditTarget
import app.betternotes.core.edit.UndoHistory
import app.betternotes.core.model.Document
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageLayers

/**
 * An open document: its undo history, and the pages currently in memory. Pages load the first time
 * they are asked for, or arrive through [install] from a background thread, so a thousand-page PDF
 * costs only the pages being looked at. An undo that reaches an evicted page reloads it, in the state
 * the edit expects, since only saved pages are evicted.
 *
 * Not thread safe, so use it from one thread. To keep that thread from blocking on the disk, split a
 * save with [prepareSave], [PendingSave.write] and [finishSave].
 */
class DocumentSession private constructor(
    private val store: DocumentStore,
    override val document: Document,
    private val clock: () -> Long,
) : EditTarget {
    val history = UndoHistory()

    private val loaded = HashMap<Long, PageContent>()
    private val savedVersions = HashMap<Long, Long>()
    private var savedDocumentVersion = document.version

    override fun content(pageId: Long): PageContent = loaded.getOrPut(pageId) {
        store.readPageWithLayers(pageId).let { PageContent(it.items, it.layers) }.also { savedVersions[pageId] = it.version }
    }

    /** The page if it is in memory, without touching the disk. */
    fun loadedContent(pageId: Long): PageContent? = loaded[pageId]

    /** Hands over items read on another thread. Returns false if the page got loaded in the meantime. */
    fun install(pageId: Long, items: List<PageItem>, layers: PageLayers = PageLayers.Single): Boolean {
        if (pageId in loaded) return false
        val page = PageContent(items, layers)
        loaded[pageId] = page
        savedVersions[pageId] = page.version
        return true
    }

    /** Every page in memory, in no particular order. */
    val loadedContents: Collection<PageContent> get() = loaded.values

    /** True if something has changed since the last save. */
    val hasUnsavedChanges: Boolean
        get() = document.version != savedDocumentVersion ||
            loaded.any { (id, page) -> page.version != savedVersions[id] }

    /** Writes whatever changed, and blocks until it is on the disk. */
    fun save() {
        val pending = prepareSave() ?: return
        pending.write()
        finishSave(pending)
    }

    /**
     * Captures what needs writing, or returns null if nothing does. Cheap, because items are immutable
     * and only the list of them is copied. Edits made after this go into the next save.
     */
    fun prepareSave(): PendingSave? {
        if (!hasUnsavedChanges) return null
        val changed = loaded.filter { (id, page) -> page.version != savedVersions[id] }
            .map { (id, page) -> PendingSave.PageSnapshot(id, page.version, page.toList(), page.layers) }
        document.modifiedAtMs = clock()
        return PendingSave(store, changed, DocumentCodec.encode(document), document.version)
    }

    /** Records a finished [PendingSave.write], on this session's thread. */
    fun finishSave(save: PendingSave) {
        for (page in save.pages) {
            if (page.id in loaded) savedVersions[page.id] = page.version
        }
        savedDocumentVersion = save.documentVersion
    }

    /**
     * Drops every loaded page not in [pageIds], which keeps memory proportional to the view. A page
     * with unsaved changes stays until they are saved, so this never touches the disk.
     */
    fun retainPages(pageIds: Set<Long>) {
        val iterator = loaded.entries.iterator()
        while (iterator.hasNext()) {
            val (id, page) = iterator.next()
            if (id in pageIds || page.version != savedVersions[id]) continue
            iterator.remove()
            savedVersions.remove(id)
        }
    }

    companion object {
        /** Starts a session for a document that is new, which writes it to disk straight away. */
        fun create(
            store: DocumentStore,
            document: Document,
            clock: () -> Long = System::currentTimeMillis,
        ): DocumentSession {
            store.writeDocument(document)
            return DocumentSession(store, document, clock)
        }

        /** Opens a saved document, and clears files left behind by removed pages or a crash. */
        fun open(store: DocumentStore, clock: () -> Long = System::currentTimeMillis): DocumentSession {
            val document = store.readDocument()
            store.deleteOrphanPages(document)
            return DocumentSession(store, document, clock)
        }
    }
}

/**
 * A snapshot of what a save will write. [write] can run on any thread, but saves must run one at a
 * time, in the order they were prepared.
 */
class PendingSave internal constructor(
    private val store: DocumentStore,
    internal val pages: List<PageSnapshot>,
    private val documentBytes: ByteArray,
    internal val documentVersion: Long,
) {
    internal class PageSnapshot(val id: Long, val version: Long, val items: List<PageItem>, val layers: PageLayers)

    /**
     * Pages go first: if the app dies between the two steps, a new page's file is an orphan to clean
     * up, which beats a document listing a page whose ink never landed.
     */
    fun write() {
        for (page in pages) store.writePage(page.id, page.items, page.layers)
        store.writeDocumentBytes(documentBytes)
    }
}
