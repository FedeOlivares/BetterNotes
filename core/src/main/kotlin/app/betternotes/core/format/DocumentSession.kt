package app.betternotes.core.format

import app.betternotes.core.edit.EditTarget
import app.betternotes.core.edit.UndoHistory
import app.betternotes.core.model.Document
import app.betternotes.core.model.PageContent

/**
 * An open document: its undo history, and the pages currently in memory. Pages load the first time
 * they are asked for, so a thousand-page PDF costs only the pages being looked at. An undo that
 * reaches an evicted page reloads it, in the state the edit expects, since it was saved on eviction.
 *
 * Not thread safe, and loading or saving blocks on disk, so use it from one thread.
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

    // A page saved on eviction leaves the modified time in the description out of date.
    private var descriptionStale = false

    override fun content(pageId: Long): PageContent = loaded.getOrPut(pageId) {
        PageContent(store.readPage(pageId)).also { savedVersions[pageId] = it.version }
    }

    /** True if something has changed since the last [save]. */
    val hasUnsavedChanges: Boolean
        get() = descriptionStale || document.version != savedDocumentVersion ||
            loaded.any { (id, page) -> page.version != savedVersions[id] }

    /**
     * Writes whatever changed. Pages go first: if the app dies between the two steps, a new page's
     * file is an orphan to clean up, which beats a document listing a page whose ink never landed.
     */
    fun save() {
        if (!hasUnsavedChanges) return
        for ((id, page) in loaded) {
            if (page.version != savedVersions[id]) savePage(id, page)
        }
        document.modifiedAtMs = clock()
        store.writeDocument(document)
        savedDocumentVersion = document.version
        descriptionStale = false
    }

    /** Saves and drops every loaded page not in [pageIds], which keeps memory proportional to the view. */
    fun retainPages(pageIds: Set<Long>) {
        val iterator = loaded.entries.iterator()
        while (iterator.hasNext()) {
            val (id, page) = iterator.next()
            if (id in pageIds) continue
            if (page.version != savedVersions[id]) {
                savePage(id, page)
                descriptionStale = true
            }
            iterator.remove()
            savedVersions.remove(id)
        }
    }

    private fun savePage(id: Long, page: PageContent) {
        store.writePage(id, page.toList())
        savedVersions[id] = page.version
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
