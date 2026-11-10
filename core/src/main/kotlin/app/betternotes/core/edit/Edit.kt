package app.betternotes.core.edit

import app.betternotes.core.model.Document
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem

/** What edits act on. Asking for a page's content may load it, since it may have been evicted. */
interface EditTarget {
    val document: Document
    fun content(pageId: Long): PageContent
}

/**
 * One undoable change. Undo is strictly last-in, first-out, so a page is back in exactly the state
 * an edit expects when it runs, and edits can safely remember plain positions.
 */
sealed interface Edit {
    fun apply(target: EditTarget)
    fun revert(target: EditTarget)
}

/**
 * Takes some items off a page and puts others on. Adding, deleting, erasing, resizing and reordering
 * are all this one edit with different arguments. See [PageContent.splice] for the index rules.
 */
class ItemsEdit(
    val pageId: Long,
    val removedAt: IntArray,
    val removed: List<PageItem>,
    val addedAt: IntArray,
    val added: List<PageItem>,
) : Edit {
    override fun apply(target: EditTarget) = target.content(pageId).splice(removedAt, addedAt, added)
    override fun revert(target: EditTarget) = target.content(pageId).splice(addedAt, removedAt, removed)

    companion object {
        private val NONE = IntArray(0)

        /** Adds [item] on top of everything else on the page. */
        fun addOnTop(pageId: Long, content: PageContent, item: PageItem) =
            ItemsEdit(pageId, NONE, emptyList(), intArrayOf(content.size), listOf(item))

        /** Swaps each item at [indices] for the matching one in [replacements], keeping paint order. */
        fun replace(pageId: Long, content: PageContent, indices: IntArray, replacements: List<PageItem>) =
            ItemsEdit(pageId, indices, indices.map { content[it] }, indices, replacements)

        fun remove(pageId: Long, content: PageContent, indices: IntArray) =
            ItemsEdit(pageId, indices, indices.map { content[it] }, NONE, emptyList())
    }
}

class InsertPage(val index: Int, val page: Page) : Edit {
    override fun apply(target: EditTarget) = target.document.insertPage(index, page)
    override fun revert(target: EditTarget) = target.document.removePage(index)
}

/** Removing a page leaves its file on disk, so undoing brings its items back. */
class RemovePage(val index: Int, val page: Page) : Edit {
    override fun apply(target: EditTarget) = target.document.removePage(index)
    override fun revert(target: EditTarget) = target.document.insertPage(index, page)
}

/**
 * Adds, changes or takes out the cover page. [before] and [after] are the cover pages, or null for none. A changed
 * design keeps its page id, so what was drawn on the cover stays. A page taken out leaves its file, so undo brings its items back.
 */
class SetCover(val before: Page?, val after: Page?) : Edit {
    override fun apply(target: EditTarget) = target.document.replaceCover(after)
    override fun revert(target: EditTarget) = target.document.replaceCover(before)
}

/**
 * Replaces a planner's pages when its dates change. [before] and [after] are the whole page lists. A page that is in both keeps its id,
 * so what was drawn on it stays; one taken out leaves its file, so undo brings its items back.
 */
class SetPlannerPages(val before: List<Page>, val after: List<Page>) : Edit {
    override fun apply(target: EditTarget) = target.document.replacePages(after)
    override fun revert(target: EditTarget) = target.document.replacePages(before)
}

class MovePage(val from: Int, val to: Int) : Edit {
    override fun apply(target: EditTarget) = target.document.movePage(from, to)
    override fun revert(target: EditTarget) = target.document.movePage(to, from)
}

/** Changes a page's size or background. A page from a PDF refuses a new size. */
class UpdatePage(val before: Page, val after: Page) : Edit {
    init {
        require(before.id == after.id) { "Both versions must be the same page" }
    }

    override fun apply(target: EditTarget) = target.document.updatePage(after)
    override fun revert(target: EditTarget) = target.document.updatePage(before)
}

/** Several edits that undo together, such as one eraser drag. */
class BatchEdit(val edits: List<Edit>) : Edit {
    override fun apply(target: EditTarget) = edits.forEach { it.apply(target) }
    override fun revert(target: EditTarget) = edits.asReversed().forEach { it.revert(target) }
}
