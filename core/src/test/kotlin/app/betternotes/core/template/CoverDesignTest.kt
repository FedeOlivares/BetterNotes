package app.betternotes.core.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverDesignTest {
    private class Counting : TemplateSink {
        var marks = 0
        override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) { marks++ }
        override fun dot(x: Float, y: Float, radius: Float, color: Int) { marks++ }
        override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) { marks++ }
    }

    @Test
    fun `every design has a unique id and draws something`() {
        assertEquals(CoverDesign.ALL.size, CoverDesign.ALL.map { it.id }.toSet().size)
        for (design in CoverDesign.ALL) {
            val sink = Counting()
            design.draw(sink, 595f, 842f)
            assertTrue(design.id, sink.marks > 0)
        }
    }

    @Test
    fun `a design becomes items the owner can edit, and the custom cover starts with none but its paper`() {
        for (design in CoverDesign.ALL) {
            val items = design.toItems(595f, 842f)
            assertTrue(design.id, items.isNotEmpty())
        }
        // A cover shows nothing but white once it holds its own items, so no design is drawn under them.
        assertEquals(1, CoverDesign.byId(CoverDesign.CUSTOM_ID)!!.toItems(595f, 842f).size)
    }

    @Test
    fun `the background of a design comes locked, and nothing small does`() {
        for (design in CoverDesign.ALL) {
            val items = design.toItems(595f, 842f)
            assertTrue(design.id, items.first().locked)
            assertTrue(design.id, items.filter { it.locked }.all { it.bounds.width >= 595f && it.bounds.height >= 842f })
        }
    }

    @Test
    fun `the palette lists the main colors and recoloring swaps them`() {
        val design = CoverDesign.byId("solid-blue")!!
        val colors = design.palette()
        assertTrue(colors.isNotEmpty() && colors.size <= 6)
        val first = colors.first()
        val seen = HashSet<Int>()
        val sink = object : TemplateSink {
            override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) { seen += color }
            override fun dot(x: Float, y: Float, radius: Float, color: Int) { seen += color }
            override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) { seen += color }
        }
        design.draw(sink, 595f, 842f, mapOf(first to 0xFF123456.toInt()))
        assertTrue(0xFF123456.toInt() in seen)
        assertTrue(first !in seen)
        // A blend is listed by its two ends, not by its many bands.
        assertEquals(2, CoverDesign.byId("enclosed-1")!!.palette().count { it != 0xFFFAFAFA.toInt() && it != 0xFF6F6F6F.toInt() })
    }

    @Test
    fun `the default and the common covers exist`() {
        assertNotNull(CoverDesign.byId(CoverDesign.DEFAULT_ID))
        assertEquals(4, CoverDesign.COMMON.size)
        assertEquals(null, CoverDesign.byId("no-such-cover"))
    }
}
