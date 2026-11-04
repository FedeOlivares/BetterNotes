package app.betternotes.core.model

import app.betternotes.core.geometry.PageSize
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
) {
    /** A page taken from a PDF keeps the PDF's size. */
    val isResizable get() = source == PageSource.Blank && size != null
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

    val canAddPages get() = kind == DocumentKind.NOTE || kind == DocumentKind.PDF

    /** A document keeps at least one page. */
    val canRemovePages get() = pageList.size > 1

    init {
        require(pageList.isNotEmpty()) { "A document needs a page" }
        require(pageList.map { it.id }.toSet().size == pageList.size) { "Duplicate page id" }
        when (kind) {
            DocumentKind.CANVAS -> require(pageList.single().size == null) { "A canvas is one infinite page" }
            else -> require(pageList.all { it.size != null }) { "Only a canvas has infinite pages" }
        }
    }

    fun indexOfPage(pageId: Long) = pageList.indexOfFirst { it.id == pageId }

    fun page(pageId: Long): Page = pageList[requireIndex(pageId)]

    internal fun insertPage(index: Int, page: Page) {
        require(canAddPages) { "A canvas has exactly one page" }
        require(page.size != null) { "Only a canvas has infinite pages" }
        require(indexOfPage(page.id) < 0) { "Duplicate page id" }
        pageList.add(index, page)
        version++
    }

    internal fun removePage(index: Int) {
        require(canRemovePages) { "A document keeps at least one page" }
        pageList.removeAt(index)
        version++
    }

    internal fun movePage(from: Int, to: Int) {
        pageList.add(to, pageList.removeAt(from))
        version++
    }

    internal fun updatePage(updated: Page) {
        val index = requireIndex(updated.id)
        val current = pageList[index]
        require(updated.source == current.source) { "A page keeps its source" }
        require(updated.size == current.size || current.isResizable && updated.size != null) {
            "This page cannot change size"
        }
        pageList[index] = updated
        version++
    }

    private fun requireIndex(pageId: Long): Int {
        val index = indexOfPage(pageId)
        require(index >= 0) { "No page $pageId" }
        return index
    }

    companion object {
        fun newNote(title: String, size: PageSize, background: PageBackground, nowMs: Long, ids: IdSource = IdSource.Random) =
            Document(ids.next(), title, DocumentKind.NOTE, nowMs, nowMs, listOf(Page(ids.next(), size, background)))

        fun newText(title: String, size: PageSize, background: PageBackground, nowMs: Long, ids: IdSource = IdSource.Random) =
            Document(ids.next(), title, DocumentKind.TEXT, nowMs, nowMs, listOf(Page(ids.next(), size, background)))

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
