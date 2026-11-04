package app.betternotes.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTest {

    private val padDpi = 345f

    @Test
    fun `at zoom 1 a world centimetre covers a physical centimetre`() {
        val viewport = Viewport(padDpi)
        val oneCmInPx = viewport.toScreenX(LengthUnit.CM.toPt(1f)) - viewport.toScreenX(0f)
        assertEquals(padDpi / 2.54f, oneCmInPx, 0.01f)
    }

    @Test
    fun `A4 width at zoom 1 matches the physical width in pixels`() {
        val viewport = Viewport(padDpi)
        assertEquals(padDpi * 210f / 25.4f, viewport.toScreenX(PageSize.A4.widthPt), 0.5f)
    }

    @Test
    fun `zoom is limited to 1500 percent`() {
        val viewport = Viewport(padDpi)
        viewport.zoomTo(100f, 0f, 0f)
        assertEquals(15f, viewport.zoom, 0f)
    }

    @Test
    fun `zooming keeps the world point under the focus fixed`() {
        val viewport = Viewport(padDpi)
        viewport.panX = 40f
        viewport.panY = -25f
        val worldX = viewport.toWorldX(300f)
        val worldY = viewport.toWorldY(500f)

        viewport.zoomTo(3f, 300f, 500f)

        assertEquals(300f, viewport.toScreenX(worldX), 0.01f)
        assertEquals(500f, viewport.toScreenY(worldY), 0.01f)
    }
}
