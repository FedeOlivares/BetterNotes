package app.betternotes.core.edit

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.format.PageCodec
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.withGroup
import app.betternotes.core.model.withLocked
import app.betternotes.core.stroke
import app.betternotes.core.template.PageBackground
import app.betternotes.core.tools.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {
    private val document = Document.newNote("n", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val pageId = document.pages[0].id
    private val target = FakeTarget(document)
    private val content = target.content(pageId)
    private val ids = IdSource { 77L }

    private fun image(id: Long) = ImageItem(id, Frame(id.toFloat() * 10f, 0f, 5f, 5f), "a")

    private fun fill(vararg items: PageItem) {
        content.splice(IntArray(0), IntArray(items.size) { it }, items.toList())
    }

    private fun select(vararg indices: Int) = Selection(pageId, indices, indices.map { content[it] })

    @Test
    fun `grouping puts every selected item in one group and undo takes them out`() {
        fill(image(1), image(2), image(3))
        val edit = Grouping.groupEdit(select(0, 2), ids)!!
        edit.apply(target)
        assertEquals(listOf(77L, 0L, 77L), content.toList().map { it.group })
        edit.revert(target)
        assertEquals(listOf(0L, 0L, 0L), content.toList().map { it.group })
    }

    @Test
    fun `the selection is one group only when every item is in the same group`() {
        val a = image(1).withGroup(5L)
        val b = image(2).withGroup(5L)
        val c = image(3).withGroup(6L)
        assertTrue(Grouping.isOneGroup(listOf(a, b)))
        assertTrue(!Grouping.isOneGroup(listOf(a, c)))
        assertTrue(!Grouping.isOneGroup(listOf(a, image(4))))
        assertTrue(!Grouping.isOneGroup(listOf(image(4), image(5))))
        assertTrue(!Grouping.isOneGroup(emptyList()))
    }

    @Test
    fun `one item cannot be grouped, and a group is not made of nothing`() {
        fill(image(1), image(2))
        assertNull(Grouping.groupEdit(select(0), ids))
        assertTrue(!Grouping.canGroup(emptyList()))
    }

    @Test
    fun `selecting one member selects the whole group`() {
        fill(image(1).withGroup(5), image(2), image(3).withGroup(5), image(4).withGroup(6))
        val expanded = Grouping.expand(select(0), content)
        assertEquals(listOf(0, 2), expanded.indices.toList())
        assertEquals(listOf(1L, 3L), expanded.items.map { it.id })
    }

    @Test
    fun `a selection of ungrouped items is returned as it is`() {
        fill(image(1), image(2))
        val selection = select(0, 1)
        assertTrue(Grouping.expand(selection, content) === selection)
    }

    @Test
    fun `a locked member is left out when locked items were not asked for`() {
        fill(image(1).withGroup(5), image(2).withGroup(5).withLocked(true))
        assertEquals(listOf(0), Grouping.expand(select(0), content, includeLocked = false).indices.toList())
        assertEquals(listOf(0, 1), Grouping.expand(select(0), content, includeLocked = true).indices.toList())
    }

    @Test
    fun `ungrouping frees every selected item`() {
        fill(image(1).withGroup(5), image(2).withGroup(5), image(3))
        val edit = Grouping.ungroupEdit(select(0, 1))!!
        edit.apply(target)
        assertEquals(listOf(0L, 0L, 0L), content.toList().map { it.group })
        edit.revert(target)
        assertEquals(listOf(5L, 5L, 0L), content.toList().map { it.group })
        assertNull(Grouping.ungroupEdit(select(2)))
    }

    @Test
    fun `grouping a group with a loose item makes one group`() {
        fill(image(1).withGroup(5), image(2).withGroup(5), image(3))
        Grouping.groupEdit(select(0, 1, 2), ids)!!.apply(target)
        assertEquals(setOf(77L), content.toList().map { it.group }.toSet())
    }

    @Test
    fun `copies keep their groups apart from the originals`() {
        var next = 100L
        val copies = Grouping.withFreshGroups(
            listOf(image(1).withGroup(5), image(2).withGroup(5), image(3).withGroup(6), image(4)),
            IdSource { next++ },
        )
        assertEquals(listOf(100L, 100L, 101L, 0L), copies.map { it.group })
    }

    @Test
    fun `groups survive the page file, and ungrouped items keep their old bytes`() {
        val items = listOf(
            stroke(1, 0f, 0f, 5f, 5f).withGroup(9),
            image(2).withGroup(9).withLocked(true),
            StickyNoteItem(3, Frame(0f, 0f, 20f, 20f), 0xFFFFFF00.toInt(), items = listOf(image(4).withGroup(3))).withGroup(8),
            image(5),
        )
        assertEquals(items, PageCodec.decode(PageCodec.encode(items)))
        val plain = listOf(image(5), stroke(6, 0f, 0f, 1f, 1f))
        // Without groups the only difference from format 8 is the version number in the header.
        val v8 = PageCodec.encode(plain, version = 8)
        val v9 = PageCodec.encode(plain, version = 9)
        assertEquals(v8.size, v9.size)
        assertEquals(plain, PageCodec.decode(v8))
    }

    @Test
    fun `a file from before groups opens with every item on its own`() {
        val items = listOf(image(1).withGroup(4), image(2))
        val older = PageCodec.decode(PageCodec.encode(items, version = 8))
        assertEquals(listOf(0L, 0L), older.map { it.group })
        assertNotEquals(items, older)
    }
}
