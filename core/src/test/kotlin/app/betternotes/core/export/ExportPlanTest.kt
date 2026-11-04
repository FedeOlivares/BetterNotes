package app.betternotes.core.export

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Page
import app.betternotes.core.stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlanTest {
    @Test
    fun `selections resolve within the document`() {
        assertEquals(listOf(0, 1, 2), PageSelection.All.resolve(3))
        assertEquals(listOf(1, 2), PageSelection.Range(1, 9).resolve(3))
        assertEquals(listOf(0), PageSelection.Range(-4, 0).resolve(3))
        assertEquals(emptyList<Int>(), PageSelection.Range(5, 9).resolve(3))
        assertEquals(listOf(2, 0), PageSelection.Pages(listOf(2, 0, 2, 7, -1)).resolve(3))
    }

    @Test
    fun `a page exports whole however little is on it`() {
        val page = Page(1, PageSize(100f, 200f))
        assertEquals(Bounds(0f, 0f, 100f, 200f), ExportFrame.areaOf(page, emptyList()))
    }

    @Test
    fun `a canvas exports its ink with a margin and nothing if empty`() {
        val canvas = Page(1, null)
        assertNull(ExportFrame.areaOf(canvas, emptyList()))
        val area = ExportFrame.areaOf(canvas, listOf(stroke(1, 100f, 100f, 200f, 300f), stroke(2, 150f, 50f, 160f, 60f)))!!
        val margin = ExportFrame.CANVAS_MARGIN_PT
        assertTrue(area.left <= 100f - margin && area.top <= 50f - margin)
        assertTrue(area.right >= 200f + margin && area.bottom >= 300f + margin)
    }

    @Test
    fun `one point stays one point in a pdf`() {
        val frame = ExportFrame.forPdf(Bounds(0f, 0f, 595.276f, 841.89f))
        assertEquals(1f, frame.scale)
        assertEquals(596, frame.outputWidth)
        assertEquals(842, frame.outputHeight)
    }

    @Test
    fun `a huge canvas shrinks to fit a pdf page`() {
        val frame = ExportFrame.forPdf(Bounds(-50000f, 0f, 50000f, 100f))
        assertEquals(14400, frame.outputWidth)
        assertEquals(0.144f, frame.scale, 0.0001f)
    }

    @Test
    fun `an image follows the requested resolution`() {
        val frame = ExportFrame.forImage(Bounds(0f, 0f, 72f, 144f), dpi = 300f)
        assertEquals(300, frame.outputWidth)
        assertEquals(600, frame.outputHeight)
    }

    @Test
    fun `an image stays under the side and pixel limits`() {
        val long = ExportFrame.forImage(Bounds(0f, 0f, 100000f, 10f), dpi = 300f)
        assertTrue(long.outputWidth <= ExportFrame.MAX_IMAGE_SIDE_PX.toInt())
        val big = ExportFrame.forImage(Bounds(0f, 0f, 14400f, 14400f), dpi = 600f)
        assertTrue(big.outputWidth.toLong() * big.outputHeight <= ExportFrame.MAX_IMAGE_PIXELS.toLong() * 101 / 100)
    }

    @Test
    fun `file names lose unsafe characters and sort by page`() {
        assertEquals("Math_ ch 1_2.pdf", exportFileName("Math: ch 1/2", "pdf"))
        assertEquals("Better Notes.pdf", exportFileName("  ..  ", "pdf"))
        assertEquals("Note - 007.png", exportFileName("Note", "png", pageNumber = 7, pageCount = 120))
    }
}
