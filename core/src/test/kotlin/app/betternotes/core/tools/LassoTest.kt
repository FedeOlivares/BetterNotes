package app.betternotes.core.tools

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.edit.UndoHistory
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.ids
import app.betternotes.core.model.Document
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.withLocked
import app.betternotes.core.model.withGroup
import app.betternotes.core.edit.Grouping
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.stroke
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoTest {
    private val eps = 1e-3f
    private val square = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
    private val document = Document.newNote("n", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val pageId = document.pages[0].id
    private val target = FakeTarget(document)
    private val content = target.content(pageId)

    private fun fill(vararg items: PageItem) {
        content.splice(IntArray(0), IntArray(items.size) { it }, items.toList())
    }

    @Test
    fun `strokes mostly inside are caught, others not`() {
        fill(
            stroke(1, 10f, 10f, 20f, 20f),
            stroke(2, 90f, 50f, 95f, 50f, 98f, 50f, 110f, 50f),
            stroke(3, 90f, 50f, 110f, 50f, 120f, 50f, 130f, 50f),
            stroke(4, 500f, 500f, 510f, 510f),
        )
        val selection = selectWithLasso(pageId, content, square)
        assertEquals(listOf(1L, 2L), selection.items.map { it.id })
        assertEquals(listOf(0, 1), selection.indices.toList())
    }

    @Test
    fun `other items are caught by their center`() {
        fill(
            ImageItem(1, Frame(80f, 40f, 30f, 20f), "center inside"),
            ImageItem(2, Frame(90f, 40f, 30f, 20f), "center outside"),
        )
        assertEquals(listOf(1L), selectWithLasso(pageId, content, square).items.map { it.id })
    }

    @Test
    fun `any portion catches whatever the lasso touches, even only by crossing`() {
        fill(
            // Mostly outside, with its end in the lasso.
            stroke(1, 90f, 50f, 150f, 50f, 200f, 50f),
            // Passing right through with no sample inside.
            stroke(2, -50f, 50f, 150f, 50f),
            // Not touching.
            stroke(3, 300f, 300f, 310f, 310f),
            // An image whose box overlaps a corner, and one that surrounds the lasso.
            ImageItem(4, Frame(90f, 90f, 40f, 40f), "overlaps"),
            ImageItem(5, Frame(-50f, -50f, 300f, 300f), "surrounds"),
            ImageItem(6, Frame(400f, 400f, 30f, 30f), "away"),
        )
        val ids = selectWithLasso(pageId, content, square, anyPortion = true).items.map { it.id }
        assertEquals(listOf(1L, 2L, 4L, 5L), ids)
        // Without it, only what is mostly inside or centered inside is caught.
        val plain = selectWithLasso(pageId, content, square).items.map { it.id }
        assertTrue(1L !in plain && 2L !in plain && 4L !in plain)
    }

    @Test
    fun `a concave lasso excludes what its notch leaves out`() {
        val notched = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 50f, 20f, 0f, 100f)
        fill(stroke(1, 50f, 60f, 51f, 61f), stroke(2, 50f, 10f, 51f, 11f))
        assertEquals(listOf(2L), selectWithLasso(pageId, content, notched).items.map { it.id })
    }

    @Test
    fun `a long stroke is tested with a bounded number of samples`() {
        val xy = FloatArray(20_000) { if (it % 2 == 0) (it / 2) * 0.01f else 50f }
        fill(stroke(1, *xy))
        assertEquals(1, selectWithLasso(pageId, content, square).items.size)
    }

    @Test
    fun `degenerate lasso selects nothing`() {
        fill(stroke(1, 10f, 10f, 20f, 20f))
        val selection = selectWithLasso(pageId, content, floatArrayOf(0f, 0f, 100f, 100f))
        assertTrue(selection.isEmpty)
        assertNull(selection.bounds)
    }

    @Test
    fun `bounds cover the selection`() {
        fill(ImageItem(1, Frame(10f, 10f, 20f, 20f), "a"), ImageItem(2, Frame(40f, 50f, 20f, 20f), "b"))
        val bounds = selectWithLasso(pageId, content, square).bounds!!
        assertEquals(Bounds(10f, 10f, 60f, 70f), bounds)
    }

    @Test
    fun `dragging a corner scales about the opposite corner`() {
        val bounds = Bounds(10f, 10f, 30f, 50f)
        val t = resizeTransform(bounds, ResizeHandle.BOTTOM_RIGHT, 50f, 90f)
        assertEquals(10f, t.mapX(10f, 10f), eps)
        assertEquals(10f, t.mapY(10f, 10f), eps)
        assertEquals(50f, t.mapX(30f, 50f), eps)
        assertEquals(90f, t.mapY(30f, 50f), eps)
    }

    @Test
    fun `dragging the top left keeps the bottom right fixed`() {
        val bounds = Bounds(10f, 10f, 30f, 50f)
        val t = resizeTransform(bounds, ResizeHandle.TOP_LEFT, 0f, 0f)
        assertEquals(30f, t.mapX(30f, 50f), eps)
        assertEquals(50f, t.mapY(30f, 50f), eps)
        assertEquals(0f, t.mapX(10f, 10f), eps)
        assertEquals(0f, t.mapY(10f, 10f), eps)
    }

    @Test
    fun `dragging a side scales one axis`() {
        val t = resizeTransform(Bounds(0f, 0f, 10f, 10f), ResizeHandle.RIGHT, 30f, 999f)
        assertEquals(3f, t.a, eps)
        assertEquals(1f, t.d, eps)
    }

    @Test
    fun `keeping the aspect ratio makes a corner uniform`() {
        val t = resizeTransform(Bounds(0f, 0f, 10f, 20f), ResizeHandle.BOTTOM_RIGHT, 30f, 30f, keepAspect = true)
        assertEquals(3f, t.a, eps)
        assertEquals(3f, t.d, eps)
    }

    @Test
    fun `dragging past the opposite side stops at the minimum instead of flipping`() {
        val t = resizeTransform(Bounds(0f, 0f, 100f, 100f), ResizeHandle.RIGHT, -50f, 0f, minSize = 5f)
        assertEquals(0.05f, t.a, eps)
    }

    @Test
    fun `a flat selection does not divide by zero`() {
        val t = resizeTransform(Bounds(0f, 5f, 100f, 5f), ResizeHandle.BOTTOM_RIGHT, 200f, 50f)
        assertEquals(2f, t.a, eps)
        assertEquals(1f, t.d, eps)
    }

    @Test
    fun `resizing a selection scales the items and undoes as one step`() {
        val a = stroke(1, 10f, 10f, 20f, 20f, width = 2f)
        val b = ImageItem(2, Frame(30f, 30f, 10f, 10f), "b")
        val outsider = stroke(3, 500f, 500f, 510f, 510f)
        fill(a, outsider, b)
        val history = UndoHistory()

        val selection = selectWithLasso(pageId, content, square)
        val edit = selection.transformEdit(Affine(a = 2f, d = 2f))
        history.perform(edit, target)

        assertEquals(listOf(1L, 3L, 2L), content.ids())
        val scaled = content[0] as StrokeItem
        assertEquals(40f, scaled.points.x(1), eps)
        assertEquals(4f, scaled.style.width, eps)
        assertEquals(20f, (content[2] as ImageItem).frame.width, eps)
        assertEquals(outsider, content[1])

        val next = selection.afterTransform(edit)
        assertEquals(edit.added, next.items)

        history.undo(target)
        assertEquals(listOf(a, outsider, b), content.toList())
    }

    @Test
    fun `deleting a selection undoes back to the same order`() {
        val a = stroke(1, 10f, 10f, 20f, 20f)
        val outsider = stroke(2, 500f, 500f, 510f, 510f)
        val b = stroke(3, 30f, 30f, 40f, 40f)
        fill(a, outsider, b)
        val history = UndoHistory()
        history.perform(selectWithLasso(pageId, content, square).deleteEdit(), target)
        assertEquals(listOf(2L), content.ids())
        history.undo(target)
        assertEquals(listOf(a, outsider, b), content.toList())
    }

    @Test
    fun `a locked item is a layer below, so a tap finds an unlocked one first`() {
        fill(
            ImageItem(1, Frame(0f, 0f, 100f, 100f), "unlocked"),
            ImageItem(2, Frame(0f, 0f, 100f, 100f), "locked", locked = true),
        )
        // The locked picture is later in the page, but lies below.
        assertEquals(0, content.topItemAt(50f, 50f))
    }

    @Test
    fun `a locked item is still found where nothing unlocked is`() {
        fill(ImageItem(1, Frame(0f, 0f, 100f, 100f), "locked", locked = true))
        assertEquals(0, content.topItemAt(50f, 50f))
    }

    @Test
    fun `tap finds the topmost item within tolerance`() {
        fill(
            stroke(1, 0f, 50f, 100f, 50f, width = 2f),
            ImageItem(2, Frame(0f, 0f, 100f, 100f), "below"),
            stroke(3, 0f, 50f, 100f, 50f, width = 2f),
        )
        assertEquals(2, content.topItemAt(50f, 50f))
        assertEquals(1, content.topItemAt(50f, 20f))
        assertEquals(-1, content.topItemAt(500f, 500f))
        assertEquals(2, content.topItemAt(105f, 50f, tolerance = 6f))
    }

    @Test
    fun `tapping beside a closed sticky note misses where its open size would be`() {
        val note = StickyNoteItem(1, Frame(0f, 0f, 200f, 200f), color = 0, closedWidth = 20f, closedHeight = 20f)
        fill(note)
        assertEquals(0, content.topItemAt(10f, 10f))
        assertEquals(-1, content.topItemAt(100f, 100f))
    }

    @Test
    fun `a loop wholly inside an open post-it does not take the post-it, one leaving it does`() {
        fill(StickyNoteItem(1, Frame(0f, 0f, 200f, 200f), color = 0, closedWidth = 20f, closedHeight = 20f, isOpen = true))
        val inside = floatArrayOf(60f, 60f, 140f, 60f, 140f, 140f, 60f, 140f)
        assertTrue(selectWithLasso(pageId, content, inside).items.isEmpty())
        assertTrue(selectWithLasso(pageId, content, inside, anyPortion = true).items.isEmpty())
        val leaving = floatArrayOf(60f, 60f, 260f, 60f, 260f, 140f, 60f, 140f)
        assertEquals(listOf(1L), selectWithLasso(pageId, content, leaving, anyPortion = true).items.map { it.id })
        val around = floatArrayOf(-10f, -10f, 210f, -10f, 210f, 210f, -10f, 210f)
        assertEquals(listOf(1L), selectWithLasso(pageId, content, around).items.map { it.id })
    }

    private fun background(id: Long, locked: Boolean) =
        ShapeItem(id, Frame(0f, 0f, 100f, 100f), ShapeKind.RECTANGLE, strokeColor = 0, strokeWidth = 0f, fillColor = 0xFF123456.toInt()).withLocked(locked)

    @Test
    fun `a cover's locked background is not selected even when locked items are`() {
        fill(background(1, locked = true), ImageItem(2, Frame(10f, 10f, 20f, 20f), "logo", locked = true))
        val cover = PageSize(100f, 100f)
        val all = floatArrayOf(-5f, -5f, 105f, -5f, 105f, 105f, -5f, 105f)
        assertEquals(listOf(1L, 2L), selectWithLasso(pageId, content, all, includeLocked = true).items.map { it.id })
        assertEquals(listOf(2L), selectWithLasso(pageId, content, all, includeLocked = true, coverSize = cover).items.map { it.id })
    }

    @Test
    fun `a cover's background is never selected, locked or not, alone or in a group, whatever else the loop holds`() {
        val cover = PageSize(100f, 100f)
        val all = floatArrayOf(-5f, -5f, 105f, -5f, 105f, 105f, -5f, 105f)
        for (locked in listOf(false, true)) {
            for (includeLocked in listOf(false, true)) {
                content.splice(IntArray(content.size) { it }, IntArray(0), emptyList())
                fill(background(1, locked = locked).withGroup(7L), ImageItem(2, Frame(10f, 10f, 20f, 20f), "logo", group = 7L))
                val picked = selectWithLasso(pageId, content, all, includeLocked = includeLocked, coverSize = cover)
                assertTrue(1L !in picked.items.map { it.id })
                val expanded = Grouping.expand(picked, content, includeLocked, cover)
                assertEquals(listOf(2L), expanded.items.map { it.id })
            }
        }
        content.splice(IntArray(content.size) { it }, IntArray(0), emptyList())
        fill(background(1, locked = false))
        assertTrue(selectWithLasso(pageId, content, square, coverSize = cover).items.isEmpty())
    }
}
