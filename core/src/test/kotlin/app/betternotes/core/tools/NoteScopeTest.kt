package app.betternotes.core.tools

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.edit.UndoHistory
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.template.PageBackground
import app.betternotes.core.model.Document
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteScopeTest {
    private val document = Document.newNote("n", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val pageId = document.pages[0].id
    private val target = FakeTarget(document)
    private val page = target.content(pageId)

    private fun box(id: Long, x: Float, y: Float) =
        ShapeItem(id, Frame(x, y, 20f, 20f), ShapeKind.RECTANGLE, strokeColor = 0, strokeWidth = 1f, fillColor = 0)

    /** An open post-it at 100, 100 holding two boxes, at 40, 40 and 150, 150 of its own. */
    private fun note(open: Boolean = true) =
        StickyNoteItem(1, Frame(100f, 100f, 200f, 200f), color = 0, closedWidth = 20f, closedHeight = 20f, isOpen = open, items = listOf(box(10, 40f, 40f), box(11, 150f, 150f)))

    /** A loop around the first box, wholly inside the post-it. */
    private val inside = floatArrayOf(130f, 130f, 180f, 130f, 180f, 180f, 130f, 180f)

    @Test
    fun `a loop inside a post-it finds the post-it, one that leaves it does not`() {
        page.splice(IntArray(0), intArrayOf(0), listOf(note()))
        assertEquals(0, NoteScope.noteHolding(page, inside))
        assertEquals(-1, NoteScope.noteHolding(page, floatArrayOf(90f, 130f, 180f, 130f, 180f, 180f, 90f, 180f)))
        page.splice(intArrayOf(0), intArrayOf(0), listOf(note(open = false)))
        assertEquals(-1, NoteScope.noteHolding(page, inside))
    }

    @Test
    fun `the lasso inside a post-it picks only the items it surrounds, where they are on the page, and never the post-it`() {
        page.splice(IntArray(0), intArrayOf(0), listOf(note()))
        val scope = NoteScope.open(pageId, page, 0)!!
        val picked = selectWithLasso(pageId, scope.content, inside)
        assertEquals(listOf(10L), picked.items.map { it.id })
        assertEquals(Frame(140f, 140f, 20f, 20f), (picked.items[0] as ShapeItem).frame)
        // On the page itself the same loop does not take the post-it, as it never leaves it.
        assertTrue(selectWithLasso(pageId, page, inside).isEmpty)
        assertTrue(selectWithLasso(pageId, page, inside, anyPortion = true).isEmpty)
        // A loop that leaves the post-it takes it.
        assertEquals(listOf(1L), selectWithLasso(pageId, page, floatArrayOf(50f, 50f, 350f, 50f, 350f, 350f, 50f, 350f)).items.map { it.id })
    }

    @Test
    fun `a move inside a post-it is one edit of the post-it, which undo takes back, and the items stay in it`() {
        page.splice(IntArray(0), intArrayOf(0), listOf(note()))
        val before = page[0]
        val history = UndoHistory()
        val scope = NoteScope.open(pageId, page, 0)!!
        val picked = selectWithLasso(pageId, scope.content, inside)
        val move = picked.transformEdit(Affine.translate(10f, 5f))
        move.apply(object : app.betternotes.core.edit.EditTarget {
            override val document get() = this@NoteScopeTest.document
            override fun content(pageId: Long) = scope.content
        })
        // While it is under way the page shows it, unrecorded.
        scope.mirror(page)
        assertEquals(Frame(50f, 45f, 20f, 20f), ((page[0] as StickyNoteItem).items[0] as ShapeItem).frame)
        val edit = scope.commit(page)!!
        history.perform(edit, target)
        val after = page[0] as StickyNoteItem
        assertEquals(1, page.size)
        assertEquals(listOf(10L, 11L), after.items.map { it.id })
        assertEquals(Frame(50f, 45f, 20f, 20f), (after.items[0] as ShapeItem).frame)
        assertNull("nothing more to record", scope.commit(page))
        history.undo(target)
        assertEquals(before, page[0])
    }

    @Test
    fun `a delete inside a post-it takes the item out of it, and a change never recorded is put back`() {
        page.splice(IntArray(0), intArrayOf(0), listOf(note()))
        val before = page[0]
        val scope = NoteScope.open(pageId, page, 0)!!
        val picked = selectWithLasso(pageId, scope.content, inside)
        scope.content.splice(picked.indices, IntArray(0), emptyList())
        scope.mirror(page)
        assertEquals(listOf(11L), (page[0] as StickyNoteItem).items.map { it.id })
        // Let go without recording: the page is as the history saw it.
        scope.restore(page)
        assertEquals(before, page[0])
        // Recorded, the item is gone from the post-it.
        val edit = scope.commit(page)!!
        edit.apply(target)
        assertEquals(listOf(11L), (page[0] as StickyNoteItem).items.map { it.id })
    }

    @Test
    fun `a loop works in a turned post-it only inside its drawn edges, and never takes the post-it`() {
        val turned = note().copy(frame = Frame(100f, 100f, 200f, 200f, rotation = 45f))
        page.splice(IntArray(0), intArrayOf(0), listOf(turned))
        // In the box of the turned post-it, but over its cut-off corner: on the page, where the post-it is not caught either.
        val corner = floatArrayOf(102f, 102f, 120f, 102f, 120f, 120f, 102f, 120f)
        assertNull(NoteScope.forLoop(pageId, page, corner))
        assertTrue(selectWithLasso(pageId, page, corner).isEmpty)
        val middle = floatArrayOf(180f, 180f, 220f, 180f, 220f, 220f, 180f, 220f)
        assertEquals(1L, NoteScope.forLoop(pageId, page, middle)?.noteId)
        assertTrue(selectWithLasso(pageId, page, middle).isEmpty)
        // A loop around the whole post-it takes it.
        assertEquals(listOf(1L), selectWithLasso(pageId, page, floatArrayOf(0f, 0f, 400f, 0f, 400f, 400f, 0f, 400f)).items.map { it.id })
    }

    @Test
    fun `restore leaves a post-it that undo changed since`() {
        page.splice(IntArray(0), intArrayOf(0), listOf(note()))
        val scope = NoteScope.open(pageId, page, 0)!!
        val older = note().copy(items = emptyList())
        page.splice(intArrayOf(0), intArrayOf(0), listOf(older))
        scope.restore(page)
        assertEquals(older, page[0])
    }
}
