package app.betternotes.core.library

import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import java.text.Normalizer

/** What the library remembers about a document, so that its tile needs no read from the document. */
data class DocumentInfo(
    val kind: DocumentKind,
    /** The first page's paper, which the tile shows until its thumbnail is ready. */
    val paperColor: Int,
    /** When the document's description was last written. A different time on disk means this entry is stale. */
    val savedAtMs: Long,
)

/**
 * A folder, or a document if [document] is set. Both are organized in exactly the same ways, so they
 * share one type. [name] and [modifiedAtMs] of a document copy what its own file says.
 */
data class LibraryEntry(
    val id: Long,
    val name: String,
    /** The folder this is in, or null at the top of the library. */
    val parentId: Long?,
    val modifiedAtMs: Long,
    val starred: Boolean = false,
    val tags: Set<String> = emptySet(),
    /** When this went to the bin, or null if it is not there. */
    val trashedAtMs: Long? = null,
    val document: DocumentInfo? = null,
) {
    val isFolder get() = document == null
}

/** What the home screen is showing. */
sealed interface LibraryView {
    /** The items directly in a folder, or at the top of the library if [id] is null. */
    data class InFolder(val id: Long?) : LibraryView
    data object Starred : LibraryView
    data class Tagged(val tag: String) : LibraryView
    data object Bin : LibraryView

    /** Items anywhere outside the bin whose names contain [query]. */
    data class Search(val query: String) : LibraryView
}

enum class LibrarySort { MODIFIED, NAME }

/** A document's current description as found on the disk. */
data class ScannedDocument(val id: Long, val name: String, val modifiedAtMs: Long, val info: DocumentInfo) {
    constructor(document: Document, savedAtMs: Long) : this(
        document.id,
        document.title,
        document.modifiedAtMs,
        DocumentInfo(document.kind, firstPagePaper(document), savedAtMs),
    )
}

private const val WHITE = 0xFFFFFFFF.toInt()

/** A PDF page is rendered onto white, whatever its description's paper says. */
private fun firstPagePaper(document: Document) =
    if (document.kind == DocumentKind.PDF) WHITE else document.pages[0].background.color

/**
 * The folders and documents on the home screen, and how they are organized. Immutable: every change
 * returns a new library, so a snapshot can go to another thread to be saved.
 *
 * Documents live in their own folders on the disk, whatever folder the library shows them in. Moving
 * one here touches no document file.
 */
class Library(entries: Collection<LibraryEntry> = emptyList()) {
    private val byId = LinkedHashMap<Long, LibraryEntry>(entries.size * 2).apply {
        for (entry in entries) put(entry.id, entry)
    }

    private val children by lazy(LazyThreadSafetyMode.NONE) { byId.values.groupBy { it.parentId } }

    /** The latest change to anything inside each folder, so a folder sorts as if it was just used. */
    private val lastActivity by lazy(LazyThreadSafetyMode.NONE) {
        HashMap<Long, Long>(byId.size * 2).also { activity ->
            fun visit(entry: LibraryEntry): Long {
                var latest = entry.modifiedAtMs
                for (child in childrenOf(entry.id)) {
                    val childLatest = visit(child)
                    if (child.trashedAtMs == null && childLatest > latest) latest = childLatest
                }
                activity[entry.id] = latest
                return latest
            }
            childrenOf(null).forEach(::visit)
        }
    }

    val entries: Collection<LibraryEntry> get() = byId.values

    operator fun get(id: Long): LibraryEntry? = byId[id]

    /** Every tag in use outside the bin, in alphabetical order. */
    val tags: List<String>
        get() = byId.values.filter(::isLive).flatMapTo(sortedSetOf()) { it.tags }.toList()

    /** How many items a folder's tile counts: those directly inside it, leaving out the bin. */
    fun itemCount(folderId: Long) = childrenOf(folderId).count { it.trashedAtMs == null }

    fun list(view: LibraryView, sort: LibrarySort = LibrarySort.MODIFIED): List<LibraryEntry> {
        val found = when (view) {
            is LibraryView.InFolder -> childrenOf(view.id).filter { it.trashedAtMs == null }
            LibraryView.Starred -> byId.values.filter { it.starred && isLive(it) }
            is LibraryView.Tagged -> byId.values.filter { view.tag in it.tags && isLive(it) }
            LibraryView.Bin -> byId.values.filter { it.trashedAtMs != null && !parentInBin(it) }
            is LibraryView.Search -> {
                val query = foldForSearch(view.query.trim())
                if (query.isEmpty()) emptyList()
                else byId.values.filter { isLive(it) && foldForSearch(it.name).contains(query) }
            }
        }
        return when (sort) {
            LibrarySort.MODIFIED -> found.sortedByDescending { lastActivityMs(it) }
            LibrarySort.NAME -> found.sortedBy { foldForSearch(it.name) }
        }
    }

    fun lastActivityMs(entry: LibraryEntry): Long = lastActivity[entry.id] ?: entry.modifiedAtMs

    fun plus(entry: LibraryEntry) = Library(byId.values + entry)

    fun renamed(id: Long, name: String) = updated(id) { it.copy(name = name) }

    fun withStar(id: Long, starred: Boolean) = updated(id) { it.copy(starred = starred) }

    fun withTags(id: Long, tags: Set<String>) = updated(id) { it.copy(tags = tags) }

    /** True if [id] can move into [folderId]. A folder cannot go inside itself. */
    fun canMove(id: Long, folderId: Long?): Boolean {
        if (folderId == null) return true
        val target = byId[folderId] ?: return false
        if (!target.isFolder || !isLive(target)) return false
        var ancestor: Long? = folderId
        while (ancestor != null) {
            if (ancestor == id) return false
            ancestor = byId[ancestor]?.parentId
        }
        return true
    }

    /** The folders [id] can move to, by name. */
    fun moveTargets(id: Long): List<LibraryEntry> =
        byId.values.filter { it.isFolder && it.id != byId[id]?.parentId && canMove(id, it.id) }
            .sortedBy { foldForSearch(it.name) }

    fun moved(id: Long, folderId: Long?): Library {
        require(canMove(id, folderId)) { "Cannot move $id into $folderId" }
        return updated(id) { it.copy(parentId = folderId) }
    }

    /** Sends an item to the bin. A folder takes what is inside it along, without those items changing. */
    fun trashed(id: Long, nowMs: Long) = updated(id) { it.copy(trashedAtMs = nowMs) }

    /** Takes an item out of the bin, to the top of the library if its folder is gone or in the bin too. */
    fun restored(id: Long) = updated(id) { entry ->
        val parent = entry.parentId?.let { byId[it] }
        entry.copy(trashedAtMs = null, parentId = parent?.takeIf(::isLive)?.id)
    }

    /** Forgets an item and everything inside it. The caller deletes the files of [documentsWithin]. */
    fun deleted(id: Long): Library {
        val gone = HashSet<Long>()
        collectWithin(id, gone)
        return Library(byId.values.filter { it.id !in gone })
    }

    /** The documents at or inside [id]. */
    fun documentsWithin(id: Long): List<Long> {
        val within = HashSet<Long>()
        collectWithin(id, within)
        return within.filter { byId[it]?.document != null }
    }

    /** The documents that a scan's [onDisk] saved times show are new or changed. */
    fun staleDocuments(onDisk: Map<Long, Long>): List<Long> =
        onDisk.filter { (id, savedAtMs) -> byId[id]?.document?.savedAtMs != savedAtMs }.keys.toList()

    /** The documents listed here whose files are gone. */
    fun missingDocuments(onDisk: Map<Long, Long>): Set<Long> =
        byId.values.filter { it.document != null && it.id !in onDisk }.mapTo(HashSet()) { it.id }

    /**
     * Brings the library in line with a scan: updates what [scanned] found, leaving how it is organized
     * alone, and forgets [missing]. A document nothing knows about appears at the top of the library.
     */
    fun afterScan(scanned: List<ScannedDocument>, missing: Set<Long>): Library {
        val next = LinkedHashMap(byId)
        for (id in missing) next.remove(id)
        for (found in scanned) {
            val current = next[found.id]
            next[found.id] = current?.copy(name = found.name, modifiedAtMs = found.modifiedAtMs, document = found.info)
                ?: LibraryEntry(found.id, found.name, null, found.modifiedAtMs, document = found.info)
        }
        return Library(next.values)
    }

    private fun childrenOf(folderId: Long?): List<LibraryEntry> = children[folderId].orEmpty()

    private fun updated(id: Long, change: (LibraryEntry) -> LibraryEntry): Library {
        val next = LinkedHashMap(byId)
        next[id] = change(requireNotNull(byId[id]) { "No entry $id" })
        return Library(next.values)
    }

    private fun collectWithin(id: Long, into: MutableSet<Long>) {
        if (!into.add(id)) return
        for (child in childrenOf(id)) collectWithin(child.id, into)
    }

    /** Not in the bin, and not inside a folder that is. */
    private fun isLive(entry: LibraryEntry) = entry.trashedAtMs == null && !parentInBin(entry)

    private fun parentInBin(entry: LibraryEntry): Boolean {
        var ancestor = entry.parentId?.let { byId[it] }
        while (ancestor != null) {
            if (ancestor.trashedAtMs != null) return true
            ancestor = ancestor.parentId?.let { byId[it] }
        }
        return false
    }

    private companion object {
        val MARKS = Regex("\\p{Mn}+")

        /** So "quirurgica" finds "Quirúrgica", which matters for Spanish typed in a hurry. */
        fun foldForSearch(text: String): String =
            MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
    }
}
