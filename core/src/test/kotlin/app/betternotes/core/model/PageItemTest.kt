package app.betternotes.core.model

import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import app.betternotes.core.stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PageItemTest {
    private val double = Affine(a = 2f, d = 2f)

    @Test
    fun `stroke bounds include its width`() {
        val s = stroke(1, 10f, 10f, 20f, 10f, width = 4f)
        assertTrue(s.bounds.left < 8.1f && s.bounds.left > 7f)
        assertTrue(s.bounds.right > 21.9f)
    }

    @Test
    fun `scaling a stroke scales its points and width but keeps pressure and time`() {
        val s = stroke(1, 10f, 10f, 20f, 10f, width = 4f)
        val scaled = s.transformed(double)
        assertEquals(40f, scaled.points.x(1), 0f)
        assertEquals(8f, scaled.style.width, 1e-5f)
        assertEquals(s.points.pressure(1), scaled.points.pressure(1), 0f)
        assertEquals(s.points.timeMs(1), scaled.points.timeMs(1), 0f)
        assertEquals("the original is immutable", 10f, s.points.x(0), 0f)
    }

    @Test
    fun `equal strokes compare by content`() {
        assertEquals(stroke(1, 1f, 2f), stroke(1, 1f, 2f))
    }

    @Test
    fun `fill counts only with alpha`() {
        val frame = Frame(0f, 0f, 10f, 10f)
        assertFalse(ShapeItem(1, frame, ShapeKind.RECTANGLE, fillColor = 0x00FF0000).isFilled)
        assertTrue(ShapeItem(1, frame, ShapeKind.RECTANGLE, fillColor = 0x80FF0000.toInt()).isFilled)
    }

    @Test
    fun `scaling a shape scales its stroke and corners`() {
        val shape = ShapeItem(1, Frame(0f, 0f, 10f, 10f), ShapeKind.RECTANGLE, strokeWidth = 1f, cornerRadius = 2f)
        val scaled = shape.transformed(double)
        assertEquals(20f, scaled.frame.width, 0f)
        assertEquals(2f, scaled.strokeWidth, 1e-5f)
        assertEquals(4f, scaled.cornerRadius, 1e-5f)
    }

    @Test
    fun `closed sticky note occupies less than an open one`() {
        val closed = StickyNoteItem(1, Frame(10f, 10f, 200f, 100f), color = 0, closedWidth = 20f, closedHeight = 30f)
        assertEquals(20f, closed.bounds.width, 0f)
        assertEquals(30f, closed.bounds.height, 0f)
        val open = closed.copy(isOpen = true)
        assertEquals(200f, open.bounds.width, 0f)
    }

    @Test
    fun `moving a sticky note leaves its contents relative to it`() {
        val inner = stroke(2, 1f, 1f, 2f, 2f)
        val note = StickyNoteItem(1, Frame(0f, 0f, 100f, 100f), color = 0, items = listOf(inner))
        val moved = note.transformed(Affine.translate(50f, 50f))
        assertEquals(50f, moved.frame.left, 0f)
        assertSame(note.items, moved.items)
    }

    @Test
    fun `resizing a sticky note scales its contents with it`() {
        val inner = stroke(2, 10f, 10f, 20f, 20f)
        val note = StickyNoteItem(1, Frame(5f, 5f, 100f, 100f), color = 0, items = listOf(inner))
        val resized = note.transformed(double)
        val movedStroke = resized.items.single() as StrokeItem
        assertEquals(20f, movedStroke.points.x(0), 1e-4f)
        assertEquals(40f, movedStroke.points.y(1), 1e-4f)
    }

    @Test
    fun `an open note paints what runs past its edge`() {
        // In the note's own coordinates, from inside it to 150 pt past its right edge.
        val out = stroke(2, 50f, 50f, 250f, 50f, width = 0f)
        val note = StickyNoteItem(1, Frame(10f, 20f, 100f, 100f), color = 0, isOpen = true, items = listOf(out))
        assertEquals(110f, note.bounds.right, 0f)
        assertTrue("${note.paintBounds}", note.paintBounds.right >= 10f + 250f)
        assertTrue(note.paintBounds.contains(note.bounds))
    }

    @Test
    fun `a closed note paints only itself`() {
        val out = stroke(2, 50f, 50f, 250f, 50f)
        val note = StickyNoteItem(1, Frame(10f, 20f, 100f, 100f), color = 0, isOpen = false, items = listOf(out))
        assertEquals(note.bounds, note.paintBounds)
    }

    @Test
    fun `a turned note paints past its edge where the turn takes the line`() {
        // A quarter turn about the middle (60, 70): the local point (250, 50) lies at (10+250, 20+50) = (260, 70), 200 right of the middle,
        // and turns to 200 below it.
        val out = stroke(2, 50f, 50f, 250f, 50f, width = 0f)
        val note = StickyNoteItem(1, Frame(10f, 20f, 100f, 100f, rotation = 90f), color = 0, isOpen = true, items = listOf(out))
        assertTrue("${note.paintBounds}", note.paintBounds.bottom >= 70f + 200f - 1e-3f)
        assertTrue("${note.paintBounds}", note.paintBounds.right < 260f)
    }

    @Test
    fun `a tile that only the part past the note touches still gets the note`() {
        val out = stroke(2, 50f, 50f, 250f, 50f, width = 0f)
        val note = StickyNoteItem(1, Frame(0f, 0f, 100f, 100f), color = 0, isOpen = true, items = listOf(out))
        val content = PageContent(listOf(note))
        val seen = mutableListOf<Long>()
        // One point wide, 1 pt past the note, as a tile boundary would be.
        content.forEachPaintingIn(Bounds(200f, 0f, 201f, 100f)) { _, it -> seen += it.id }
        assertEquals(listOf(1L), seen)
        // Selection and hit tests still see only the note.
        val hit = mutableListOf<Long>()
        content.forEachIntersecting(Bounds(200f, 0f, 201f, 100f)) { _, it -> hit += it.id }
        assertTrue(hit.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sticky notes do not nest`() {
        val inner = StickyNoteItem(2, Frame(0f, 0f, 1f, 1f), color = 0)
        StickyNoteItem(1, Frame(0f, 0f, 10f, 10f), color = 0, items = listOf(inner))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `table must have a cell for every position`() {
        TableItem(1, Frame(0f, 0f, 10f, 10f), listOf(1f, 1f), listOf(1f, 1f), cells = listOf("a"))
    }

    @Test
    fun `scaling a table keeps its proportions`() {
        val table = TableItem(1, Frame(0f, 0f, 10f, 10f), listOf(1f, 3f), listOf(1f), cells = listOf("a", "b"))
        val scaled = table.transformed(double)
        assertEquals(listOf(1f, 3f), scaled.columnWeights)
        assertEquals(20f, scaled.frame.width, 0f)
    }
}
