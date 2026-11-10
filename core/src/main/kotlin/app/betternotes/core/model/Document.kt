package app.betternotes.core.model

import app.betternotes.core.geometry.PageSize
import app.betternotes.core.planner.PlannerPages
import app.betternotes.core.planner.PlannerSetup
import app.betternotes.core.template.PageBackground

enum class DocumentKind {
    /** Pages of a chosen, resizable size. */
    NOTE,

    /** One page with no edges. */
    CANVAS,

    /** Pages imported from a PDF, plus any blank pages added to it. */
    PDF,

    /** A typed notebook: one page holding one text item. */
    TEXT,

    /** A planner: a cover and about 500 pages made from the dates the planner covers. Its pages are fixed, apart from what is drawn on them. */
    PLANNER,
}

/** Where a page's base image comes from. */
sealed interface PageSource {
    data object Blank : PageSource

    /** Page [pageIndex], counted from 0, of the PDF stored under [assetId]. */
    data class PdfPage(val assetId: String, val pageIndex: Int) : PageSource
}

/** A page's description. Its items are stored separately, so a long document can load them lazily. */
data class Page(
    val id: Long,
    /** Null on an infinite canvas. */
    val size: PageSize?,
    val background: PageBackground = PageBackground(),
    val source: PageSource = PageSource.Blank,
    /** The angle in degrees the page is shown at, clockwise. Only the view turns; the page's content does not. */
    val rotation: Float = 0f,
    /** The id of the cover design when this is the document's cover page, which is locked in place as the first page. */
    val cover: String? = null,
) {
    val isCover get() = cover != null

    /** A page taken from a PDF keeps the PDF's size. A blank page may be sized or infinite. */
    val isResizable get() = source == PageSource.Blank && !isCover
}

/**
 * A note, canvas or PDF. Page changes go through edits so they can be undone, which is why the
 * mutators here are internal. Not thread safe.
 */
class Document(
    val id: Long,
    title: String,
    val kind: DocumentKind,
    val createdAtMs: Long,
    modifiedAtMs: Long,
    pages: List<Page>,
) {
    private val pageList = pages.toMutableList()

    var title = title
        set(value) {
            field = value
            version++
        }

    var modifiedAtMs = modifiedAtMs
        internal set

    val pages: List<Page> get() = pageList

    /** Changes when the title or page list does, so saving can skip an unchanged manifest. */
    var version = 0L
        private set

    /** An infinite page is a document's only page, so pages can't be added while one exists. */
    val canAddPages get() = kind != DocumentKind.TEXT && kind != DocumentKind.PLANNER && pageList.none { it.size == null }

    /** Only a note or canvas with a single page can have an infinite page, which is how a canvas is made. */
    val canBeInfinite get() = (kind == DocumentKind.NOTE || kind == DocumentKind.CANVAS) && pageList.size == 1

    /** A document keeps at least one page, and its cover stays. */
    val canRemovePages get() = pageList.size > 1 && kind != DocumentKind.PLANNER

    /** The cover design's id, or null when the document has none. The cover is always the first page. */
    val coverId get() = pageList.first().cover

    /** The cover page is locked: it cannot be removed, moved, resized, or have a page put before it. */
    fun isLocked(pageId: Long) = page(pageId).isCover

    init {
        require(pageList.isNotEmpty()) { "A document needs a page" }
        require(pageList.drop(1).none { it.isCover }) { "Only the first page can be a cover" }
        require(pageList.map { it.id }.toSet().size == pageList.size) { "Duplicate page id" }
        require(pageList.all { it.size != null } || canBeInfinite) { "Only a lone note or canvas page can be infinite" }
    }

    fun indexOfPage(pageId: Long) = pageList.indexOfFirst { it.id == pageId }

    fun page(pageId: Long): Page = pageList[requireIndex(pageId)]

    internal fun insertPage(index: Int, page: Page) {
        require(canAddPages) { "This document cannot take more pages" }
        require(page.size != null) { "Only a lone page can be infinite" }
        require(!page.isCover) { "A cover is made with the document" }
        require(!(index == 0 && coverId != null)) { "Nothing goes before the cover" }
        require(indexOfPage(page.id) < 0) { "Duplicate page id" }
        pageList.add(index, page)
        version++
    }

    internal fun removePage(index: Int) {
        require(canRemovePages) { "A document keeps at least one page" }
        require(!pageList[index].isCover) { "The cover cannot be removed" }
        pageList.removeAt(index)
        version++
    }

    /**
     * Puts [cover] in front as the cover page, taking the one there out; null takes the cover out. A document that
     * can't take pages (an infinite canvas, a typed notebook) gets none.
     */
    internal fun replaceCover(cover: Page?) {
        require(cover == null || (cover.isCover && cover.size != null)) { "A cover is a page with a design and a size" }
        val had = coverId != null
        require(cover == null || had || canAddPages) { "This document cannot take a cover" }
        require(cover == null || pageList.none { it.id == cover.id && !it.isCover }) { "Duplicate page id" }
        if (had) pageList.removeAt(0)
        if (cover != null) pageList.add(0, cover)
        require(pageList.isNotEmpty()) { "A document needs a page" }
        version++
    }

    /** Swaps the whole page list, as a planner does when its dates change. The first page stays the cover. */
    internal fun replacePages(pages: List<Page>) {
        require(kind == DocumentKind.PLANNER) { "Only a planner has its pages replaced" }
        require(pages.isNotEmpty() && pages.first().isCover && pages.drop(1).none { it.isCover }) { "A planner keeps its cover first" }
        require(pages.map { it.id }.toSet().size == pages.size) { "Duplicate page id" }
        pageList.clear()
        pageList.addAll(pages)
        version++
    }

    internal fun movePage(from: Int, to: Int) {
        require(coverId == null || (from > 0 && to > 0)) { "The cover cannot move, and nothing goes before it" }
        pageList.add(to, pageList.removeAt(from))
        version++
    }

    /**
     * Turns the view of a page. It is not an edit, so undo skips it: only how the page is shown changes, never its content.
     * A document with a page turned saves again, because the page list is saved with it.
     */
    fun setPageRotation(pageId: Long, degrees: Float) {
        val index = requireIndex(pageId)
        if (pageList[index].rotation == degrees) return
        pageList[index] = pageList[index].copy(rotation = degrees)
        version++
    }

    internal fun updatePage(updated: Page) {
        val index = requireIndex(updated.id)
        val current = pageList[index]
        require(updated.source == current.source) { "A page keeps its source" }
        require(updated.size == current.size || current.isResizable) { "This page cannot change size" }
        require(updated.cover == current.cover) { "A page keeps its cover" }
        require(updated.size != null || canBeInfinite) { "This document cannot have an infinite page" }
        pageList[index] = updated
        version++
    }

    private fun requireIndex(pageId: Long): Int {
        val index = indexOfPage(pageId)
        require(index >= 0) { "No page $pageId" }
        return index
    }

    companion object {
        /**
         * A copy of a new, one-page or imported [document] with a cover page in front, the size of its first page.
         * [coverId] null leaves it as it is. A document that already has a cover, or an infinite page, is returned unchanged.
         */
        fun withCover(document: Document, coverId: String?, ids: IdSource = IdSource.Random): Document {
            val first = document.pages.first()
            if (coverId == null || first.isCover || first.size == null) return document
            val cover = Page(ids.next(), first.size, PageBackground(), cover = coverId)
            return Document(
                document.id,
                document.title,
                document.kind,
                document.createdAtMs,
                document.modifiedAtMs,
                listOf(cover) + document.pages,
            )
        }

        fun newNote(title: String, size: PageSize, background: PageBackground, nowMs: Long, ids: IdSource = IdSource.Random) =
            Document(ids.next(), title, DocumentKind.NOTE, nowMs, nowMs, listOf(Page(ids.next(), size, background)))

        fun newText(title: String, size: PageSize, background: PageBackground, nowMs: Long, ids: IdSource = IdSource.Random) =
            Document(ids.next(), title, DocumentKind.TEXT, nowMs, nowMs, listOf(Page(ids.next(), size, background)))

        /** A planner for the dates of [setup]: the cover, then every page in order. */
        fun newPlanner(title: String, setup: PlannerSetup, nowMs: Long, ids: IdSource = IdSource.Random): Document {
            val pages = PlannerPages.build(setup, emptyMap(), ids)
            return Document(ids.next(), title, DocumentKind.PLANNER, nowMs, nowMs, pages)
        }

        fun newCanvas(title: String, background: PageBackground, nowMs: Long, ids: IdSource = IdSource.Random) =
            Document(ids.next(), title, DocumentKind.CANVAS, nowMs, nowMs, listOf(Page(ids.next(), null, background)))

        /** [pageSizes] holds the size of each PDF page, in order. */
        fun fromPdf(title: String, assetId: String, pageSizes: List<PageSize>, nowMs: Long, ids: IdSource = IdSource.Random) =
            Document(
                ids.next(),
                title,
                DocumentKind.PDF,
                nowMs,
                nowMs,
                pageSizes.mapIndexed { i, size ->
                    Page(ids.next(), size, source = PageSource.PdfPage(assetId, i))
                },
            )
    }
}
