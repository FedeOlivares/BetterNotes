package app.betternotes.core.tools

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.edit.UndoHistory
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Capsule
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.ids
import app.betternotes.core.model.Document
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.stroke
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EraserTest {
    private val ids = CountingIds()
    private val eps = 1e-3f

    private fun session(content: PageContent, mode: EraseMode, radius: Float = 5f) =
        EraseSession(1, content, mode, radius, ids)

    private fun PageContent.strokeAt(i: Int) = this[i] as StrokeItem

    @Test
    fun `erasing the middle splits a stroke and interpolates the cuts`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 100f, 0f, width = 0f)))
        val erase = session(content, EraseMode.PARTIAL, radius = 10f)
        erase.eraseTo(50f, 0f)
        assertEquals(2, content.size)

        val left = content.strokeAt(0).points
        val right = content.strokeAt(1).points
        assertEquals(2, left.size)
        assertEquals(40f, left.x(1), eps)
        assertEquals(60f, right.x(0), eps)
        assertEquals(100f, right.x(1), eps)
        assertEquals("time is interpolated", 4f, left.timeMs(1), eps)
        assertEquals("pressure is interpolated", 0.04f, left.pressure(1), eps)
    }

    @Test
    fun `the cut allows for the width of the stroke`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 100f, 0f, width = 4f)))
        session(content, EraseMode.PARTIAL, radius = 10f).eraseTo(50f, 0f)
        assertEquals(38f, content.strokeAt(0).points.x(1), eps)
        assertEquals(62f, content.strokeAt(1).points.x(0), eps)
    }

    @Test
    fun `a wide stroke is cut when only its edge is touched`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 100f, 0f, width = 20f)))
        val edit = session(content, EraseMode.PARTIAL, radius = 5f).apply { eraseTo(50f, 12f) }.finish()
        assertTrue(edit != null)
        assertEquals(2, content.size)
    }

    @Test
    fun `erasing an end shortens a stroke and leaves one piece`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 100f, 0f, width = 0f)))
        session(content, EraseMode.PARTIAL, radius = 10f).eraseTo(0f, 0f)
        assertEquals(1, content.size)
        assertEquals(10f, content.strokeAt(0).points.x(0), eps)
    }

    @Test
    fun `erasing across a corner point removes it`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 50f, 0f, 50f, 50f, width = 0f)))
        session(content, EraseMode.PARTIAL, radius = 10f).eraseTo(50f, 0f)
        assertEquals(2, content.size)
        assertEquals(40f, content.strokeAt(0).points.x(1), eps)
        assertEquals(10f, content.strokeAt(1).points.y(0), eps)
    }

    @Test
    fun `erasing over the whole stroke removes it`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 10f, 0f, 20f, 0f, width = 0f)))
        session(content, EraseMode.PARTIAL, radius = 100f).eraseTo(10f, 0f)
        assertEquals(0, content.size)
    }

    @Test
    fun `a dot is removed when touched and left when not`() {
        val content = PageContent(listOf(stroke(1, 50f, 50f, width = 0f)))
        session(content, EraseMode.PARTIAL, radius = 5f).eraseTo(0f, 0f)
        assertEquals(1, content.size)
        session(content, EraseMode.PARTIAL, radius = 5f).eraseTo(52f, 50f)
        assertEquals(0, content.size)
    }

    @Test
    fun `fast moves erase the whole path between events`() {
        val content = PageContent(listOf(stroke(1, 50f, -20f, 50f, 20f, width = 0f)))
        val erase = session(content, EraseMode.PARTIAL, radius = 1f)
        erase.eraseTo(0f, 0f)
        assertEquals(1, content.size)
        erase.eraseTo(100f, 0f)
        assertEquals(2, content.size)
    }

    @Test
    fun `no contact means no edit`() {
        val content = PageContent(listOf(stroke(1, 0f, 0f, 100f, 0f, width = 0f)))
        val erase = session(content, EraseMode.PARTIAL)
        erase.eraseTo(50f, 50f)
        assertNull(erase.finish())
        assertEquals(listOf(1L), content.ids())
    }

    @Test
    fun `fragments keep their place in paint order`() {
        val content = PageContent(
            listOf(
                stroke(1, 0f, 50f, 100f, 50f, width = 0f),
                stroke(2, 0f, 0f, 100f, 0f, width = 0f),
                stroke(3, 0f, 50f, 100f, 50f, width = 0f),
            ),
        )
        session(content, EraseMode.PARTIAL).eraseTo(50f, 0f)
        assertEquals(1L, content[0].id)
        assertEquals(3L, content[3].id)
        assertEquals(4, content.size)
    }

    @Test
    fun `several strokes split in one move land in the right places`() {
        val content = PageContent(
            listOf(
                stroke(1, 50f, -50f, 50f, 50f, width = 0f),
                stroke(2, 200f, 0f, 300f, 0f, width = 0f),
                stroke(3, 0f, 0f, 100f, 0f, width = 0f),
            ),
        )
        session(content, EraseMode.PARTIAL).eraseTo(50f, 0f)
        assertEquals(5, content.size)
        assertEquals(2L, content[2].id)
    }

    @Test
    fun `whole mode removes a touched stroke and shape, nothing else`() {
        val frame = Frame(0f, 0f, 100f, 100f)
        val hollow = ShapeItem(2, frame, ShapeKind.RECTANGLE)
        val content = PageContent(
            listOf(
                stroke(1, 0f, 0f, 100f, 0f, width = 0f),
                hollow,
                ImageItem(3, frame, "img"),
                stroke(4, 500f, 500f, 600f, 500f, width = 0f),
            ),
        )
        session(content, EraseMode.WHOLE, radius = 1f).eraseTo(50f, 0f)
        assertEquals(listOf(3L, 4L), content.ids())
    }

    @Test
    fun `whole mode ignores the inside of a hollow shape but not a filled one`() {
        val frame = Frame(0f, 0f, 100f, 100f)
        val hollow = PageContent(listOf(ShapeItem(1, frame, ShapeKind.RECTANGLE)))
        session(hollow, EraseMode.WHOLE, radius = 1f).eraseTo(50f, 50f)
        assertEquals(1, hollow.size)

        val filled = PageContent(listOf(ShapeItem(1, frame, ShapeKind.RECTANGLE, fillColor = 0xFF00FF00.toInt())))
        session(filled, EraseMode.WHOLE, radius = 1f).eraseTo(50f, 50f)
        assertEquals(0, filled.size)
    }

    @Test
    fun `whole mode finds lines and ellipses by their outline`() {
        val line = ShapeItem(1, Frame(0f, 50f, 100f, 0f), ShapeKind.LINE)
        val content = PageContent(listOf(line))
        session(content, EraseMode.WHOLE, radius = 1f).eraseTo(50f, 60f)
        assertEquals(1, content.size)
        session(content, EraseMode.WHOLE, radius = 1f).eraseTo(50f, 50f)
        assertEquals(0, content.size)

        val ellipse = PageContent(listOf(ShapeItem(1, Frame(0f, 0f, 100f, 100f), ShapeKind.ELLIPSE)))
        session(ellipse, EraseMode.WHOLE, radius = 1f).eraseTo(2f, 2f)
        assertEquals("the corner of the frame is outside an ellipse", 1, ellipse.size)
        session(ellipse, EraseMode.WHOLE, radius = 1f).eraseTo(100f, 50f)
        assertEquals(0, ellipse.size)
    }

    @Test
    fun `partial mode leaves shapes alone`() {
        val content = PageContent(listOf(ShapeItem(1, Frame(0f, 0f, 100f, 100f), ShapeKind.RECTANGLE)))
        val erase = session(content, EraseMode.PARTIAL)
        erase.eraseTo(0f, 50f)
        assertNull(erase.finish())
    }

    @Test
    fun `one drag undoes as one step and redoes exactly`() {
        val document = Document.newNote("n", PageSize.A4, PageBackground(), 0L, CountingIds())
        val pageId = document.pages[0].id
        val target = FakeTarget(document)
        val content = target.content(pageId)
        val original = listOf(
            stroke(1, 0f, 0f, 200f, 0f, width = 0f),
            stroke(2, 0f, 50f, 200f, 50f, width = 0f),
        )
        content.splice(IntArray(0), intArrayOf(0, 1), original)

        val erase = EraseSession(pageId, content, EraseMode.PARTIAL, 5f, ids)
        for (x in 0..200 step 5) {
            erase.eraseTo(x.toFloat(), 25f * (x % 2))
        }
        val afterErase = content.toList()

        val history = UndoHistory()
        history.record(erase.finish()!!)
        history.undo(target)
        assertEquals(original, content.toList())
        history.redo(target)
        assertEquals(afterErase, content.toList())
    }

    /** The defining property of the partial eraser, checked against random ink. */
    @Test
    fun `random drags remove exactly what the tip passed over and undo restores it`() {
        val random = Random(7)
        val original = List(20) { i ->
            val xy = FloatArray(30) { random.nextFloat() * 400f }
            stroke(i.toLong(), *xy, width = 2f)
        }
        val content = PageContent(original)
        val radius = 15f
        val erase = EraseSession(1, content, EraseMode.PARTIAL, radius, ids)
        val path = List(20) { random.nextFloat() * 400f to random.nextFloat() * 400f }
        path.forEach { (x, y) -> erase.eraseTo(x, y) }

        // No surviving ink may sit inside the swept path by more than rounding error.
        val tips = path.zipWithNext { a, b -> Capsule(a.first, a.second, b.first, b.second, radius - 0.01f) }
        for (item in content.toList()) {
            item as StrokeItem
            for (tip in tips) {
                for (i in 0 until item.points.size - 1) {
                    val hit = tip.clip(item.points.x(i), item.points.y(i), item.points.x(i + 1), item.points.y(i + 1), 0.99f)
                    assertTrue("ink left under the eraser", !hit)
                }
            }
        }

        val target = FakeTarget(Document.newNote("n", PageSize.A4, PageBackground(), 0L, ids))
        val edit = erase.finish()!!
        // Undo goes through a fresh target's content, so rebuild the erased page there.
        val page = target.content(1)
        page.splice(IntArray(0), IntArray(content.size) { it }, content.toList())
        edit.revert(target)
        assertEquals(original, page.toList())
    }

    @Test
    fun `clearing a whole page removes everything`() {
        val content = PageContent(
            listOf(stroke(1, 0f, 0f, 10f, 0f), ImageItem(2, Frame(0f, 0f, 10f, 10f), "img")),
        )
        val edit = clearEdit(1, content, region = null, ids = ids)!!
        content.splice(edit.removedAt, edit.addedAt, edit.added)
        assertEquals(0, content.size)
    }

    @Test
    fun `clearing an empty page is no edit`() {
        assertNull(clearEdit(1, PageContent(), null, ids))
    }

    @Test
    fun `clearing the displayed region of a canvas spares what is outside`() {
        val region = Bounds(0f, 0f, 100f, 100f)
        val content = PageContent(
            listOf(
                stroke(1, 10f, 10f, 20f, 20f, width = 0f),
                stroke(2, 500f, 500f, 600f, 500f, width = 0f),
                stroke(3, 50f, 50f, 150f, 50f, width = 0f),
                ImageItem(4, Frame(10f, 10f, 20f, 20f), "inside"),
                ImageItem(5, Frame(90f, 90f, 20f, 20f), "straddling"),
            ),
        )
        val edit = clearEdit(1, content, region, ids)!!
        content.splice(edit.removedAt, edit.addedAt, edit.added)

        val left = content.toList()
        assertEquals(listOf(2L, 1000L, 5L), left.map { it.id })
        val cut = left[1] as StrokeItem
        assertEquals("the crossing stroke is cut at the edge", 100f, cut.points.x(0), eps)
        assertEquals(150f, cut.points.x(1), eps)
    }
}
