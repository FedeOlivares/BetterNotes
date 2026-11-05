package app.betternotes.core.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineStyleTest {
    @Test
    fun `a pattern takes a new line color and thickness`() {
        val grid = PageTemplate.Grid().withLineColor(0xFF112233.toInt()).withLineWeight(2f)
        assertEquals(0xFF112233.toInt(), grid.lineColor)
        assertEquals(2f, grid.lineWeight)
        assertNull(PageTemplate.Blank.lineColor)
    }

    @Test
    fun `an element keeps its transparency when the color changes`() {
        val highlighter = LayoutElement.Line(0f, 0f, 10f, 0f, 14f, 0x66FFEB3B)
        assertEquals(0x663E78EE, highlighter.withStrokeColor(0xFF3E78EE.toInt()).strokeColor)
    }

    @Test
    fun `element thickness stays in range and a sticky note has none`() {
        val box = LayoutElement.Rectangle(0f, 0f, 10f, 10f, 1f, 0)
        assertEquals(4f, box.withStrokeWidth(50f).strokeWidth)
        val sticky = LayoutElement.Sticky(0f, 0f, 10f, 10f, 0)
        assertNull(sticky.withStrokeWidth(2f).strokeWidth)
    }
}
