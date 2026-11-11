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

        /** Adds [item] on top of the active layer, which is the top of the page unless a lower layer is being worked on. */
        fun addOnTop(pageId: Long, content: PageContent, item: PageItem): ItemsEdit {
            val placed = item.onLayer(content.activeLayer)
            return ItemsEdit(pageId, NONE, emptyList(), intArrayOf(content.insertionIndex(placed.layer)), listOf(placed))
        }

        /** Adds [items], in order, on top of the active layer. The new items' places are in [addedAt] of the result. */
        fun addAllOnTop(pageId: Long, content: PageContent, items: List<PageItem>): ItemsEdit {
            val at = content.insertionIndex(content.activeLayer)
            return ItemsEdit(pageId, NONE, emptyList(), IntArray(items.size) { at + it }, items.map { it.onLayer(content.activeLayer) })
        }

        /** Swaps each item at [indices] for the matching one in [replacements], keeping paint order and layer. */
        fun replace(pageId: Long, content: PageContent, indices: IntArray, replacements: List<PageItem>) =
            ItemsEdit(
                pageId, indices, indices.map { content[it] }, indices,
                replacements.mapIndexed { k, item -> item.onLayer(content[indices[k]].layer) },
            )

        /**
         * Swaps each item at [indices] (ascending) for the matching one in [replacements], and puts them on top of their layers, in the
         * order they had, so that what was last changed lies over the rest. The new places are in [addedAt] of the result.
         */
        fun replaceOnTop(pageId: Long, content: PageContent, indices: IntArray, replacements: List<PageItem>): ItemsEdit {
            val moved = replacements.mapIndexed { k, item -> item.onLayer(content[indices[k]].layer) }
            val gone = indices.toHashSet()
            // The ranks of what stays, bottom first, into which the changed items are put one at a time.
            val ranks = ArrayList<Int>(content.size)
            for (i in 0 until content.size) if (i !in gone) ranks.add(content.layers.rank(content[i].layer))
            val order = moved.indices.sortedWith(compareBy({ content.layers.rank(moved[it].layer) }, { it }))
            val at = IntArray(moved.size)
            val items = ArrayList<PageItem>(moved.size)
            for (k in order) {
                val rank = content.layers.rank(moved[k].layer)
                var place = ranks.size
                while (place > 0 && ranks[place - 1] > rank) place--
                ranks.add(place, rank)
                at[items.size] = place
                items.add(moved[k])
            }
            return ItemsEdit(pageId, indices, indices.map { content[it] }, at, items)
        }

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
