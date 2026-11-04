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
}
