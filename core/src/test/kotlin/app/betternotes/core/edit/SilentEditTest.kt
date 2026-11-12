package app.betternotes.core.edit

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.ids
import app.betternotes.core.model.Document
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.stroke
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is not a step of undo and redo of its own stays on the stack for the sake of those after it, and never changes what undo and redo do to the rest. */
class SilentEditTest {
    private val note = Document.newNote("Note", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val target = FakeTarget(note)
    private val history = UndoHistory()
    private val pageId = note.pages[0].id
    private val content = target.content(pageId)

    private fun add(id: Long, kind: StepKind = StepKind.STEP) = history.perform(ItemsEdit.addOnTop(pageId, content, stroke(id, 0f, 0f)), target, kind)

    @Test
    fun `an edit that is not a step is nothing to undo, and undo leaves it`() {
        add(1, StepKind.FOLD)
        assertFalse(history.canUndo)
        assertFalse(history.undo(target))
        assertEquals(listOf(1L), content.ids())
    }

    @Test
    fun `a fold is taken back with the step before it, and redo puts them back together`() {
        add(1)
        add(2)
        add(3, StepKind.FOLD)
        add(4, StepKind.FOLD)
        assertTrue(history.undo(target))
        assertEquals(listOf(1L), content.ids())
        assertTrue(history.redo(target))
        assertEquals(listOf(1L, 2L, 3L, 4L), content.ids())
        assertFalse(history.canRedo)
    }

    @Test
    fun `folds before a step stay when that step is undone`() {
        add(1, StepKind.FOLD)
        add(2)
        assertTrue(history.undo(target))
        assertEquals(listOf(1L), content.ids())
        assertFalse(history.canUndo)
    }

    @Test
    fun `a new edit, of any kind, ends what could be redone`() {
        add(1)
        add(2)
        history.undo(target)
        add(3, StepKind.OPEN_CLOSE)
        assertFalse(history.canRedo)
    }

    private fun sticky(open: Boolean, x: Float = 100f) = StickyNoteItem(5, Frame(x, 100f, 200f, 200f), 0, open, 20f, 20f)

    private fun noteOnPage() = content.toList().filterIsInstance<StickyNoteItem>().single()

    private fun move(to: Float) {
        val before = noteOnPage()
        val at = (0 until content.size).first { content[it].id == 5L }
        history.perform(ItemsEdit.replace(pageId, content, intArrayOf(at), listOf(before.copy(frame = before.frame.copy(left = to)))), target)
    }

    private fun setOpen(open: Boolean) {
        val before = noteOnPage()
        val at = (0 until content.size).first { content[it].id == 5L }
        history.perform(ItemsEdit.replace(pageId, content, intArrayOf(at), listOf(before.copy(isOpen = open))), target, StepKind.OPEN_CLOSE)
    }

    @Test
    fun `moving a post-it and closing it, undo takes back the move only and the post-it stays closed, and redo keeps it closed`() {
        content.splice(IntArray(0), intArrayOf(0), listOf(sticky(open = true)))
        move(300f)
        setOpen(false)
        assertTrue(history.undo(target))
        assertEquals(100f, noteOnPage().frame.left, 0f)
        assertFalse(noteOnPage().isOpen)
        assertTrue(history.redo(target))
        assertEquals(300f, noteOnPage().frame.left, 0f)
        assertFalse(noteOnPage().isOpen)
    }

    @Test
    fun `a post-it opened after a move stays open when the move is undone and redone`() {
        content.splice(IntArray(0), intArrayOf(0), listOf(sticky(open = false)))
        move(300f)
        setOpen(true)
        assertTrue(history.undo(target))
        assertEquals(100f, noteOnPage().frame.left, 0f)
        assertTrue(noteOnPage().isOpen)
        assertTrue(history.redo(target))
        assertTrue(noteOnPage().isOpen)
    }

    @Test
    fun `opening and closing alone is nothing to undo`() {
        content.splice(IntArray(0), intArrayOf(0), listOf(sticky(open = true)))
        setOpen(false)
        setOpen(true)
        assertFalse(history.canUndo)
        assertFalse(history.undo(target))
        assertTrue(noteOnPage().isOpen)
    }
}
