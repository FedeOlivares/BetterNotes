package app.betternotes.core.layout

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentLayoutTest {
    private val big = Page(1, PageSize(100f, 200f))
    private val small = Page(2, PageSize(50f, 100f))
    private val infinite = Page(9, null)

    private fun pagesIn(layout: DocumentLayout, region: Bounds): List<Int> {
        val found = ArrayList<Int>()
        layout.forEachPageIn(region) { found += it }
        return found
    }

    @Test
    fun `vertical pages stack with a gap and center across`() {
        val layout = DocumentLayout(listOf(big, small), ScrollDirection.VERTICAL, gapPt = 10f)
        assertEquals(0f, layout.originY(0), 0f)
        assertEquals(210f, layout.originY(1), 0f)
        assertEquals(0f, layout.originX(0), 0f)
        assertEquals(25f, layout.originX(1), 0f)
        assertEquals(Bounds(0f, 0f, 100f, 310f), layout.bounds)
    }

    @Test
    fun `left to right pages run along x and center across`() {
        val layout = DocumentLayout(listOf(big, small), ScrollDirection.LEFT_TO_RIGHT, gapPt = 10f)
        assertEquals(0f, layout.originX(0), 0f)
        assertEquals(110f, layout.originX(1), 0f)
        assertEquals(50f, layout.originY(1), 0f)
        assertEquals(Bounds(0f, 0f, 160f, 200f), layout.bounds)
    }

    @Test
    fun `right to left puts the first page on the right`() {
        val layout = DocumentLayout(listOf(big, small), ScrollDirection.RIGHT_TO_LEFT, gapPt = 10f)
        assertEquals(60f, layout.originX(0), 0f)
        assertEquals(0f, layout.originX(1), 0f)
    }

    @Test
    fun `a point finds its page, and a gap finds none`() {
        val layout = DocumentLayout(listOf(big, small), ScrollDirection.VERTICAL, gapPt = 10f)
        assertEquals(0, layout.pageIndexAt(50f, 100f))
        assertEquals(1, layout.pageIndexAt(50f, 250f))
        assertEquals(-1, layout.pageIndexAt(50f, 205f))
        assertEquals(-1, layout.pageIndexAt(10f, 250f))
    }

    @Test
    fun `a region finds the pages it touches, in order`() {
        val layout = DocumentLayout(listOf(big, small), ScrollDirection.VERTICAL, gapPt = 10f)
        assertEquals(listOf(0), pagesIn(layout, Bounds(0f, 0f, 10f, 10f)))
        assertEquals(listOf(0, 1), pagesIn(layout, Bounds(30f, 150f, 40f, 260f)))
        assertEquals(emptyList<Int>(), pagesIn(layout, Bounds(0f, 202f, 100f, 208f)))
    }

    @Test
    fun `an infinite canvas is one page at the origin that reaches everywhere`() {
        val layout = DocumentLayout(listOf(infinite))
        assertTrue(layout.isInfinite)
        assertEquals(0f, layout.originX(0), 0f)
        assertEquals(0f, layout.originY(0), 0f)
        assertEquals(0, layout.pageIndexAt(-30_000f, 40_000f))
        assertEquals(listOf(0), pagesIn(layout, Bounds(-5f, -5f, 5f, 5f)))
    }

    @Test
    fun `a paged layout is not infinite`() {
        assertFalse(DocumentLayout(listOf(big)).isInfinite)
    }

    @Test
    fun `content narrower than the screen stays centered`() {
        val layout = DocumentLayout(listOf(Page(1, PageSize(72f, 72f))))
        val viewport = Viewport(72f)
        viewport.panX = 999f
        viewport.panY = -999f
        layout.clampPan(viewport, 172, 272, 0f)
        assertEquals(50f, viewport.panX, 0.001f)
        assertEquals(100f, viewport.panY, 0.001f)
    }

    @Test
    fun `content wider than the screen stops at its edges plus the margin`() {
        val layout = DocumentLayout(listOf(Page(1, PageSize(1000f, 1000f))))
        val viewport = Viewport(72f)
        viewport.panX = 999f
        viewport.panY = -9999f
        layout.clampPan(viewport, 100, 100, 20f)
        assertEquals(20f, viewport.panX, 0.001f)
        assertEquals(-920f, viewport.panY, 0.001f)
    }

    @Test
    fun `clamping uses the current zoom`() {
        val layout = DocumentLayout(listOf(Page(1, PageSize(100f, 100f))))
        val viewport = Viewport(72f)
        viewport.zoomTo(10f, 0f, 0f)
        viewport.panX = -5000f
        layout.clampPan(viewport, 100, 100, 0f)
        assertEquals(-900f, viewport.panX, 0.001f)
    }

    @Test
    fun `moving to a page wider than the screen shows its top left corner`() {
        val layout = DocumentLayout(listOf(big, big), ScrollDirection.VERTICAL, gapPt = 10f)
        val viewport = Viewport(72f)
        layout.moveToPage(viewport, 1, 50, 50, 0f)
        assertEquals(0f, viewport.panX, 0.001f)
        assertEquals(-210f, viewport.panY, 0.001f)
    }

    @Test
    fun `moving to a page that fits centers it across the screen`() {
        val layout = DocumentLayout(listOf(big))
        val viewport = Viewport(72f)
        layout.moveToPage(viewport, 0, 300, 100, 0f)
        assertEquals(100f, viewport.panX, 0.001f)
        assertEquals(0f, viewport.panY, 0.001f)
    }

    @Test
    fun `a region of an upright page moves by the page's origin`() {
        val layout = DocumentLayout(listOf(big, small), ScrollDirection.VERTICAL, gapPt = 10f)
        assertEquals(Bounds(35f, 215f, 45f, 225f), layout.pageBoundsToWorld(1, Bounds(10f, 5f, 20f, 15f)))
    }

    @Test
    fun `a region of a turned page is turned with it`() {
        val layout = DocumentLayout(listOf(Page(1, PageSize(100f, 200f), rotation = 90f)), ScrollDirection.VERTICAL, gapPt = 10f)
        val world = layout.pageBoundsToWorld(0, Bounds(0f, 0f, 10f, 10f))
        // The page's top-left corner goes to the top-right of the turned page's box, so the region is not where an upright one would be.
        val upright = Bounds(layout.originX(0), layout.originY(0), layout.originX(0) + 10f, layout.originY(0) + 10f)
        assertTrue(world != upright)
        assertEquals(10f, world.width, 0.01f)
        assertEquals(10f, world.height, 0.01f)
        val corner = FloatArray(2)
        layout.pageToWorld(0, 0f, 0f, corner)
        assertTrue(world.contains(corner[0], corner[1]))
    }
}
