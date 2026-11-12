package app.betternotes.core.tools

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.edit.UndoHistory
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.TextItem
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBlocksTest {
    private val document = Document.newNote("n", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val target = FakeTarget(document)
    private val pageId = document.pages[0].id
    private val content = target.content(pageId)
    private val history = UndoHistory()
    private val frame = Frame(10f, 10f, 200f, 30f)

    private fun box(text: String) = TextItem(7, frame, text, 12f)

    private fun texts() = content.toList().filterIsInstance<TextItem>().map { it.text }

    /** Writes [text] as the next block of the box, as the editor does when a block ends. */
    private fun block(text: String) {
        val before = content.toList().filterIsInstance<TextItem>().firstOrNull { it.id == 7L }
        TextBlocks.between(pageId, content, before, box(text))?.let { history.perform(it, target) }
    }

    @Test
    fun `undo takes away the last block written and leaves the ones before it`() {
        block("one")
        block("one two")
        block("one two three")
        assertTrue(history.undo(target))
        assertEquals(listOf("one two"), texts())
        assertTrue(history.undo(target))
        assertEquals(listOf("one"), texts())
        assertTrue(history.redo(target))
        assertEquals(listOf("one two"), texts())
    }

    @Test
    fun `a box of one block has one step, and undoing it takes the whole box`() {
        block("only")
        assertTrue(history.undo(target))
        assertEquals(emptyList<String>(), texts())
        assertFalse(history.undo(target))
    }

    @Test
    fun `leaving the box with nothing new written adds no step`() {
        block("one")
        block("one")
        assertTrue(history.undo(target))
        assertFalse(history.canUndo)
    }

    @Test
    fun `a box emptied is taken off the page, and written in again it is put back`() {
        block("one")
        block("")
        assertEquals(emptyList<String>(), texts())
        block("again")
        assertEquals(listOf("again"), texts())
        assertTrue(history.undo(target))
        assertEquals(emptyList<String>(), texts())
    }

    @Test
    fun `nothing is added for a box with no text`() {
        assertNull(TextBlocks.between(pageId, content, null, box("")))
    }
}
