package app.betternotes.core.edit

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.ids
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.Page
import app.betternotes.core.stroke
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTest {
    private val note = Document.newNote("Note", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val target = FakeTarget(note)
    private val history = UndoHistory()
    private val pageId = note.pages[0].id
    private val content = target.content(pageId)

    private fun add(id: Long) = history.perform(ItemsEdit.addOnTop(pageId, content, stroke(id, 0f, 0f)), target)

    @Test
    fun `undo and redo walk back and forth`() {
        add(1)
        add(2)
        assertTrue(history.undo(target))
        assertEquals(listOf(1L), content.ids())
        assertTrue(history.undo(target))
        assertEquals(emptyList<Long>(), content.ids())
        assertFalse(history.undo(target))
        assertTrue(history.redo(target))
        assertTrue(history.redo(target))
        assertEquals(listOf(1L, 2L), content.ids())
        assertFalse(history.redo(target))
    }

    @Test
    fun `a new edit clears redo`() {
        add(1)
        history.undo(target)
        assertTrue(history.canRedo)
        add(2)
        assertFalse(history.canRedo)
    }

    @Test
    fun `history forgets the oldest edit past its limit`() {
        val short = UndoHistory(limit = 2)
        repeat(3) { i -> short.perform(ItemsEdit.addOnTop(pageId, content, stroke(i.toLong(), 0f, 0f)), target) }
        assertTrue(short.undo(target))
        assertTrue(short.undo(target))
        assertFalse(short.undo(target))
        assertEquals(listOf(0L), content.ids())
    }

    @Test
    fun `replace keeps paint order and undoes`() {
        add(1)
        add(2)
        add(3)
        history.perform(ItemsEdit.replace(pageId, content, intArrayOf(1), listOf(stroke(9, 5f, 5f))), target)
        assertEquals(listOf(1L, 9L, 3L), content.ids())
        history.undo(target)
        assertEquals(listOf(1L, 2L, 3L), content.ids())
    }

    @Test
    fun `items go to the front, the middle or the back and undo`() {
        for (id in 1L..6L) add(id)
        // Items 2 and 4 to the front: they keep their order, and the rest keeps its own.
        history.perform(rearrange(pageId, content, intArrayOf(1, 3), ZPlace.FRONT)!!.edit, target)
        assertEquals(listOf(1L, 3L, 5L, 6L, 2L, 4L), content.ids())
        history.undo(target)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), content.ids())
        // Item 6 to the back, and item 1 to the middle of what is left: halfway through five others.
        history.perform(rearrange(pageId, content, intArrayOf(5), ZPlace.BACK)!!.edit, target)
        assertEquals(listOf(6L, 1L, 2L, 3L, 4L, 5L), content.ids())
        history.perform(rearrange(pageId, content, intArrayOf(1), ZPlace.MIDDLE)!!.edit, target)
        assertEquals(listOf(6L, 2L, 1L, 3L, 4L, 5L), content.ids())
        // Where they already are, nothing happens.
        assertEquals(null, rearrange(pageId, content, intArrayOf(5), ZPlace.FRONT))
    }

    @Test
    fun `remove restores items to their old positions`() {
        add(1)
        add(2)
        add(3)
        history.perform(ItemsEdit.remove(pageId, content, intArrayOf(0, 2)), target)
        assertEquals(listOf(2L), content.ids())
        history.undo(target)
        assertEquals(listOf(1L, 2L, 3L), content.ids())
    }

    @Test
    fun `batch undoes as one, in reverse`() {
        add(1)
        val batch = BatchEdit(
            listOf(
                ItemsEdit(pageId, intArrayOf(0), listOf(content[0]), intArrayOf(0, 1), listOf(stroke(2, 0f, 0f), stroke(3, 0f, 0f))),
                ItemsEdit(pageId, intArrayOf(1), listOf(stroke(3, 0f, 0f)), intArrayOf(), emptyList()),
            ),
        )
        history.perform(batch, target)
        assertEquals(listOf(2L), content.ids())
        history.undo(target)
        assertEquals(listOf(1L), content.ids())
        history.redo(target)
        assertEquals(listOf(2L), content.ids())
    }

    @Test
    fun `page edits undo`() {
        val second = Page(50, PageSize.A5)
        val third = Page(51, PageSize.Letter)
        history.perform(InsertPage(1, second), target)
        history.perform(InsertPage(2, third), target)
        history.perform(MovePage(2, 0), target)
        assertEquals(listOf(51L, pageId, 50L), note.pages.map { it.id })

        history.perform(RemovePage(1, note.pages[1]), target)
        assertEquals(listOf(51L, 50L), note.pages.map { it.id })

        history.undo(target)
        assertEquals(listOf(51L, pageId, 50L), note.pages.map { it.id })
        history.undo(target)
        assertEquals(listOf(pageId, 50L, 51L), note.pages.map { it.id })
    }

    @Test
    fun `note pages resize, and the change undoes`() {
        val page = note.pages[0]
        val taller = page.copy(size = PageSize(595f, 1200f))
        history.perform(UpdatePage(page, taller), target)
        assertEquals(1200f, note.pages[0].size!!.heightPt, 0f)
        history.undo(target)
        assertEquals(PageSize.A4, note.pages[0].size)
    }

    @Test
    fun `background of any page can change`() {
        val pdf = Document.fromPdf("Pdf", "a.pdf", listOf(PageSize.A4), 0L, CountingIds())
        val page = pdf.pages[0]
        val lined = page.copy(background = PageBackground(template = PageTemplate.Lined()))
        UpdatePage(page, lined).apply(FakeTarget(pdf))
        assertEquals(lined, pdf.pages[0])
    }

    @Test
    fun `a pdf page refuses a new size`() {
        val pdf = Document.fromPdf("Pdf", "a.pdf", listOf(PageSize.A4, PageSize.Letter), 0L, CountingIds())
        val page = pdf.pages[1]
        assertFalse(page.isResizable)
        assertThrows(IllegalArgumentException::class.java) {
            UpdatePage(page, page.copy(size = PageSize.A5)).apply(FakeTarget(pdf))
        }
        assertEquals(PageSize.Letter, pdf.pages[1].size)
    }

    @Test
    fun `a blank page added to a pdf can resize`() {
        val pdf = Document.fromPdf("Pdf", "a.pdf", listOf(PageSize.A4), 0L, CountingIds())
        val blank = Page(77, PageSize.A4)
        InsertPage(1, blank).apply(FakeTarget(pdf))
        UpdatePage(blank, blank.copy(size = PageSize.A5)).apply(FakeTarget(pdf))
        assertEquals(PageSize.A5, pdf.pages[1].size)
    }

    @Test
    fun `an infinite canvas stays one page`() {
        val canvas = Document.newCanvas("Canvas", PageBackground(), 0L, CountingIds())
        assertEquals(DocumentKind.CANVAS, canvas.kind)
        assertFalse(canvas.canAddPages)
        assertFalse(canvas.canRemovePages)
        assertTrue(canvas.pages[0].isResizable)
        assertThrows(IllegalArgumentException::class.java) {
            InsertPage(1, Page(5, PageSize.A4)).apply(FakeTarget(canvas))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemovePage(0, canvas.pages[0]).apply(FakeTarget(canvas))
        }
    }

    @Test
    fun `the last page cannot be removed`() {
        assertFalse(note.canRemovePages)
        assertThrows(IllegalArgumentException::class.java) {
            RemovePage(0, note.pages[0]).apply(target)
        }
    }

    @Test
    fun `a page cannot change its source`() {
        val page = note.pages[0]
        assertThrows(IllegalArgumentException::class.java) {
            UpdatePage(page, page.copy(source = app.betternotes.core.model.PageSource.PdfPage("a", 0))).apply(target)
        }
    }

    @Test
    fun `only a lone note or canvas page may be infinite`() {
        Document(1, "x", DocumentKind.NOTE, 0, 0, listOf(Page(1, null)))
        assertThrows(IllegalArgumentException::class.java) {
            Document(1, "x", DocumentKind.NOTE, 0, 0, listOf(Page(1, null), Page(2, PageSize.A4)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Document(1, "x", DocumentKind.PDF, 0, 0, listOf(Page(1, null)))
        }
    }
}
