package app.betternotes.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGridTest {
    @Test
    fun `visible range covers the screen and no more`() {
        assertEquals(0, TileGrid.firstVisible(0f, 100f))
        assertEquals(9, TileGrid.lastVisible(0f, 100f, 1000))
        assertEquals(1, TileGrid.firstVisible(-150f, 100f))
        assertEquals(11, TileGrid.lastVisible(-150f, 100f, 1000))
    }

    @Test
    fun `visible range works left of the origin`() {
        assertEquals(-2, TileGrid.firstVisible(150f, 100f))
        assertEquals(8, TileGrid.lastVisible(150f, 100f, 1000))
    }

    @Test
    fun `tiles around the view never share a slot, even at the most shrunk ratio`() {
        val grid = TileGrid(512, 3200)
        val tileOnScreen = 512 * TileGrid.MIN_RATIO
        for (pan in listOf(0f, -1234.5f, 98765.25f, -7_000_000f)) {
            val first = TileGrid.firstVisible(pan, tileOnScreen) - TileGrid.RING
            val last = TileGrid.lastVisible(pan, tileOnScreen, 3200) + TileGrid.RING
            val slots = (first..last).map { column -> grid.slot(column, 0) }
            assertEquals(slots.size, slots.toSet().size)
        }
    }

    @Test
    fun `slots stay in range for negative tiles`() {
        val grid = TileGrid(512, 3200)
        for (column in -50..50) {
            for (row in -50..50) {
                assertTrue(grid.slot(column, row) in 0 until grid.slotCount)
            }
        }
    }

    @Test
    fun `a tile is redrawn only once it strays too far from its scale`() {
        assertFalse(TileGrid.needsRescale(1f))
        assertFalse(TileGrid.needsRescale(TileGrid.MIN_RATIO))
        assertFalse(TileGrid.needsRescale(TileGrid.MAX_RATIO))
        assertTrue(TileGrid.needsRescale(TileGrid.MIN_RATIO - 0.01f))
        assertTrue(TileGrid.needsRescale(TileGrid.MAX_RATIO + 0.01f))
    }

    private val tilePx = 512
    private val overlap = 1

    @Test
    fun `neighbouring tiles share their edge pixels`() {
        val scale = 2f
        val left = TileGrid.tileBounds(0, 0, tilePx, overlap, scale)
        val right = TileGrid.tileBounds(1, 0, tilePx, overlap, scale)
        assertEquals(-0.5f, left.left, 0f)
        assertEquals(256.5f, left.right, 0f)
        assertEquals(255.5f, right.left, 0f)
    }

    @Test
    fun `a change that paints one pixel into a tile redraws it`() {
        val scale = 2f
        val next = TileGrid.tileBounds(1, 0, tilePx, overlap, scale)
        // An item ending exactly on the first pixel of the next tile, which is the edge case of the rule.
        val item = Bounds(100f, 10f, next.left, 20f)
        assertTrue(TileGrid.isAffectedBy(next, item, scale))
        // And one that ends a pixel short of it still can colour it, through antialiasing.
        val short = Bounds(100f, 10f, next.left - 1f / scale, 20f)
        assertTrue(TileGrid.isAffectedBy(next, short, scale))
    }

    @Test
    fun `a change far from a tile leaves it alone`() {
        val scale = 2f
        val next = TileGrid.tileBounds(1, 0, tilePx, overlap, scale)
        val far = Bounds(100f, 10f, next.left - (TileGrid.MARGIN_PX + 1f) / scale, 20f)
        assertFalse(TileGrid.isAffectedBy(next, far, scale))
    }

    @Test
    fun `every tile an item reaches by even one pixel draws it and is redrawn for it`() {
        for (scale in listOf(0.6f, 1f, 1.7f, 4f)) {
            // Paint boxes that end 1 px inside, on, and 1 px outside every tile edge along a row.
            for (column in -3..5) {
                val tile = TileGrid.tileBounds(column, 2, tilePx, overlap, scale)
                for (shift in listOf(-1f, 0f, 1f)) {
                    val item = Bounds(tile.left - 40f / scale + shift / scale, tile.top + 5f, tile.left + shift / scale, tile.top + 9f)
                    val region = TileGrid.cullRegion(tile, scale)
                    assertTrue("cull at scale $scale, column $column, shift $shift", region.intersects(item))
                    assertTrue("redraw at scale $scale, column $column, shift $shift", TileGrid.isAffectedBy(tile, item, scale))
                }
            }
        }
    }
}
