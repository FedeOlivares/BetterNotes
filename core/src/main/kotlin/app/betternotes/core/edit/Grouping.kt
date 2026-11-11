package app.betternotes.core.edit

import app.betternotes.core.model.IdSource
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.withGroup
import app.betternotes.core.tools.Selection
import app.betternotes.core.tools.isCoverBackground

/**
 * Items grouped together are selected, moved, resized and copied as one. A group is only the id that its items share, kept in each item, so
 * grouping and ungrouping are ordinary item edits and undo like any other.
 */
object Grouping {
    /** Whether the selection holds more than one item, which is what can be grouped. */
    fun canGroup(items: List<PageItem>) = items.size > 1

    /** Whether every selected item is in one and the same group, so that the selection is that group and nothing else. */
    fun isOneGroup(items: List<PageItem>) = items.isNotEmpty() && items[0].group != 0L && items.all { it.group == items[0].group }

    /** Whether any selected item is in a group. */
    fun canUngroup(items: List<PageItem>) = items.any { it.group != 0L }

    /**
     * [selection] with every item of every group it touches, as one lasso or tap would pick them. A locked member joins only
     * if the selection takes locked items ([includeLocked]), so a group with a locked item does not freeze what is not locked.
     */
    fun expand(selection: Selection, content: PageContent, includeLocked: Boolean = true, coverSize: app.betternotes.core.geometry.PageSize? = null): Selection {
        val groups = selection.items.mapNotNullTo(HashSet()) { it.group.takeIf { group -> group != 0L } }
        if (groups.isEmpty()) return selection
        val chosen = HashSet<Int>()
        for (index in selection.indices) chosen += index
        for (index in 0 until content.size) {
            val item = content[index]
            if (item.group in groups && content.isEditable(item) && (includeLocked || !item.locked) &&
                !(coverSize != null && item.isCoverBackground(coverSize))
            ) chosen += index
        }
        if (chosen.size == selection.indices.size) return selection
        val indices = chosen.sorted().toIntArray()
        return Selection(selection.pageId, indices, indices.map { content[it] })
    }

    /** The edit that puts every selected item in one new group, taking them out of any group they were in; null if fewer than two are selected. */
    fun groupEdit(selection: Selection, ids: IdSource = IdSource.Random): ItemsEdit? {
        if (!canGroup(selection.items)) return null
        val group = nonZero(ids)
        return ItemsEdit(selection.pageId, selection.indices, selection.items, selection.indices, selection.items.map { it.withGroup(group) })
    }

    /** The edit that takes every selected item out of its group; null if none is in one. */
    fun ungroupEdit(selection: Selection): ItemsEdit? {
        if (!canUngroup(selection.items)) return null
        return ItemsEdit(selection.pageId, selection.indices, selection.items, selection.indices, selection.items.map { it.withGroup(0L) })
    }

    /**
     * [items] as copies that keep their groups apart from the originals': the items of one group stay together in a group of their own,
     * so that selecting a copy does not pick up what it was copied from.
     */
    fun withFreshGroups(items: List<PageItem>, ids: IdSource = IdSource.Random): List<PageItem> {
        if (items.none { it.group != 0L }) return items
        val fresh = HashMap<Long, Long>()
        return items.map { item -> if (item.group == 0L) item else item.withGroup(fresh.getOrPut(item.group) { nonZero(ids) }) }
    }

    private fun nonZero(ids: IdSource): Long {
        while (true) ids.next().let { if (it != 0L) return it }
    }
}
