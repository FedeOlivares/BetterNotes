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
    fun `the default and the common covers exist`() {
        assertNotNull(CoverDesign.byId(CoverDesign.DEFAULT_ID))
        assertEquals(4, CoverDesign.COMMON.size)
        assertEquals(null, CoverDesign.byId("no-such-cover"))
    }
}
