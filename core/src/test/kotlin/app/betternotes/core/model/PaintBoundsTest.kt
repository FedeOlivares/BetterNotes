package app.betternotes.core.model

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an item paints must lie inside its paint box, or a tile that shows only the overflow never draws it. See CLAUDE.md. */
class PaintBoundsTest {
    @After
    fun forgetMeasurer() {
        TextReach.layoutHeight = null
    }

    private fun text(frame: Frame, text: String = "word ".repeat(40), style: TextStyle = TextStyle(), size: Float = 12f, spans: List<TextSpan> = emptyList()) =
        TextItem(1, frame, text, size, style = style, spans = spans)

    @Test
    fun `text running below its box is in the paint box`() {
        TextReach.layoutHeight = { _, _, _, _, _ -> 300f }
        val item = text(Frame(10f, 20f, 100f, 30f))
        assertTrue(item.paintBounds.bottom >= 20f + 300f)
        assertTrue(item.paintBounds.contains(item.bounds))
    }

    @Test
    fun `bottom aligned text running above its box is in the paint box`() {
        TextReach.layoutHeight = { _, _, _, _, _ -> 100f }
        val item = text(Frame(10f, 200f, 100f, 30f), style = TextStyle(verticalAlign = VerticalAlign.BOTTOM))
        assertTrue(item.paintBounds.top <= 200f + 30f - 100f)
    }

    @Test
    fun `middle aligned overflow reaches both ways`() {
        TextReach.layoutHeight = { _, _, _, _, _ -> 90f }
        val item = text(Frame(0f, 100f, 100f, 30f), style = TextStyle(verticalAlign = VerticalAlign.MIDDLE))
        assertTrue(item.paintBounds.top <= 100f - 30f)
        assertTrue(item.paintBounds.bottom >= 130f + 30f)
    }

    @Test
    fun `glyphs overhang the sides of a box`() {
        TextReach.layoutHeight = { _, size, _, _, _ -> size }
        val item = text(Frame(100f, 100f, 50f, 20f), "Wj", size = 40f, style = TextStyle(italic = true))
        assertTrue(item.paintBounds.left < 100f - 10f)
        assertTrue(item.paintBounds.right > 150f + 10f)
    }

    @Test
    fun `the largest span sets the overhang`() {
        TextReach.layoutHeight = { _, size, _, _, _ -> size }
        val plain = text(Frame(100f, 100f, 50f, 20f), "Wj", size = 10f)
        val spanned = text(Frame(100f, 100f, 50f, 20f), "Wj", size = 10f, spans = listOf(TextSpan(0, 1, SpanLook(size = 60f))))
        assertTrue(spanned.paintBounds.left < plain.paintBounds.left - 10f)
    }

    @Test
    fun `turned overflowing text is in the paint box`() {
        TextReach.layoutHeight = { _, _, _, _, _ -> 200f }
        val item = text(Frame(0f, 0f, 100f, 20f, rotation = 90f))
        // Turned a quarter, the text that runs down from the box runs left of it, from the box's center.
        val box = item.paintBounds
        assertTrue("$box", box.left <= 50f - 10f - 200f + 20f)
    }

    @Test
    fun `without a measurer the guess is never shorter than a real layout`() {
        val item = text(Frame(0f, 0f, 100f, 10f), "a".repeat(200))
        // 200 letters at 12 pt in 100 pt: even at a full em each, 9 per line, 23 lines.
        assertTrue(item.paintBounds.height >= 23 * 12f)
    }

    @Test
    fun `an empty text paints inside its box`() {
        val item = text(Frame(0f, 0f, 100f, 10f), "")
        assertEquals(item.bounds, item.paintBounds)
    }

    @Test
    fun `table cells that overflow are in the paint box`() {
        TextReach.layoutHeight = { _, _, _, _, _ -> 150f }
        val table = TableItem(1, Frame(0f, 0f, 100f, 40f), listOf(1f, 1f), listOf(1f, 1f), listOf("a", "", "", ""))
        assertTrue(table.paintBounds.bottom >= TableItem.CELL_PADDING + 150f)
    }

    @Test
    fun `an arrow's head is in its paint box`() {
        val arrow = ShapeItem(1, Frame(0f, 100f, 200f, 0f), ShapeKind.ARROW, strokeWidth = 2f)
        // The head spreads a quarter of the length at most; it must not be cut by a tile that the thin frame does not reach.
        assertTrue(arrow.paintBounds.top <= 100f - 5f)
        assertTrue(arrow.paintBounds.bottom >= 100f + 5f)
    }

    @Test
    fun `a miter join is in the paint box`() {
        val shape = ShapeItem(1, Frame(0f, 0f, 100f, 100f), ShapeKind.TRIANGLE, strokeWidth = 10f)
        assertTrue(shape.paintBounds.left <= -10f)
    }

    @Test
    fun `a page finds text that overflows into a tile its box does not reach`() {
        TextReach.layoutHeight = { _, _, _, _, _ -> 300f }
        val content = PageContent(listOf(text(Frame(0f, 0f, 100f, 20f))))
        var found = 0
        content.forEachPaintingIn(Bounds(0f, 250f, 100f, 260f)) { _, _ -> found++ }
        assertEquals(1, found)
        // And the change of its removal covers the overflow too.
        content.splice(intArrayOf(0), intArrayOf(), emptyList())
        assertTrue(content.takeDirtyBounds()!!.bottom >= 300f)
    }
}
