package app.betternotes.core.edit

import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem

/** Where in the stack of a page's items some of them go. The stack is in paint order: the last item is on top. */
enum class ZPlace { FRONT, MIDDLE, BACK }

/** The edit that moves items in the stack, and where the items are after it. */
class Rearrangement(val edit: ItemsEdit, val newIndices: IntArray)

/**
 * Moves the items at [indices] (ascending) to the front, the back, or the middle of [content]'s stack, keeping their order among
 * themselves and the order of everything else. The middle is halfway through what is left. Null when they are already there.
 */
fun rearrange(pageId: Long, content: PageContent, indices: IntArray, place: ZPlace): Rearrangement? {
    val count = indices.size
    if (count == 0 || count == content.size) return null
    val start = when (place) {
        ZPlace.FRONT -> content.size - count
        ZPlace.BACK -> 0
        ZPlace.MIDDLE -> (content.size - count) / 2
    }
    val target = IntArray(count) { start + it }
    if (target.contentEquals(indices)) return null
    val items: List<PageItem> = indices.map { content[it] }
    return Rearrangement(ItemsEdit(pageId, indices, items, target, items), target)
}
