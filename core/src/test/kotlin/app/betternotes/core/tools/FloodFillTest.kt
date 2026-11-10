package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodFillTest {
    private val w = 40
    private val h = 40

    private fun grid() = BooleanArray(w * h)

    /** A one pixel thick square outline from (x0, y0) to (x1, y1). */
    private fun BooleanArray.square(x0: Int, y0: Int, x1: Int, y1: Int, gapAtTop: Int = 0) {
        for (x in x0..x1) {
            if (x - x0 >= 10 && x - x0 < 10 + gapAtTop) continue
            this[y0 * w + x] = true
            this[y1 * w + x] = true
        }
        for (y in y0..y1) {
            this[y * w + x0] = true
            this[y * w + x1] = true
        }
    }

    private fun BooleanArray.count() = count { it }

    @Test
    fun aClosedShapeIsFilledInside() {
        val walls = grid().apply { square(5, 5, 25, 25) }
        val region = FloodFill.region(walls, w, h, 15, 15, gapPx = 0)
        assertNotNull(region)
        // 19 by 19 pixels inside a square whose edge is one pixel thick.
        assertEquals(19 * 19, region!!.count())
        assertFalse(region[5 * w + 5])
    }

    @Test
    fun anOpenShapeIsNotFilled() {
        val walls = grid().apply { square(5, 5, 25, 25, gapAtTop = 3) }
        assertNull(FloodFill.region(walls, w, h, 15, 15, gapPx = 0))
    }

    @Test
    fun aSmallGapIsBridgedAndALargeOneIsNot() {
        val small = grid().apply { square(5, 5, 25, 25, gapAtTop = 3) }
        assertNotNull(FloodFill.region(small, w, h, 15, 15, gapPx = 2))
        val large = grid().apply { square(5, 5, 25, 25, gapAtTop = 9) }
        assertNull(FloodFill.region(large, w, h, 15, 15, gapPx = 2))
    }

    @Test
    fun aBridgedGapIsCutCleanlyAndTheColorDoesNotSpillOutside() {
        val walls = grid().apply { square(5, 5, 25, 25, gapAtTop = 3) }
        val region = FloodFill.region(walls, w, h, 15, 15, gapPx = 2)!!
        // Right up to the line, the row beside it is filled, and across the gap too, ending where the line would be...
        assertTrue(region[6 * w + 11])
        assertTrue(region[6 * w + 16])
        // ...but nothing beyond the line.
        assertFalse(region[4 * w + 11])
        assertFalse(region[4 * w + 16])
        // ...and nothing outside the square is, not even by the gap.
        for (y in 0 until 4) for (x in 0 until w) assertFalse("($x,$y) is outside", region[y * w + x])
        for (y in 0 until h) for (x in 27 until w) assertFalse("($x,$y) is outside", region[y * w + x])
    }

    @Test
    fun theWholeInsideIsFilledIncludingNarrowChannelsBesideTheLines() {
        val walls = grid()
        walls.square(5, 5, 25, 25)
        // A short line from the left side into the room, leaving a channel too narrow to flood once the lines are thickened.
        for (x in 5..20) walls[12 * w + x] = true
        for (x in 5..20) walls[14 * w + x] = true
        val region = FloodFill.region(walls, w, h, 15, 20, gapPx = 2)!!
        // The channel between the two short lines is part of the inside.
        assertTrue(region[13 * w + 10])
    }

    @Test
    fun theColorDoesNotCrossAThickLineIntoTheRoomBeyond() {
        // Two rooms side by side, divided by a wall four pixels thick.
        val walls = grid()
        walls.square(2, 5, 37, 25)
        for (y in 5..25) for (x in 18..21) walls[y * w + x] = true
        val left = FloodFill.region(walls, w, h, 10, 15, gapPx = 2)!!
        for (y in 6..24) for (x in 19..37) assertFalse("($x,$y) is past the wall", left[y * w + x])
        assertTrue(left[15 * w + 17])
    }

    @Test
    fun aThickLineIsFilledUpToItsMiddle() {
        // A room whose walls are four pixels thick: the fill goes two pixels into each wall, to its middle.
        val walls = grid()
        for (y in 4..27) for (x in 4..27) walls[y * w + x] = y < 8 || y > 23 || x < 8 || x > 23
        val region = FloodFill.region(walls, w, h, 15, 15, gapPx = 0)!!
        assertTrue(region[15 * w + 6])
        assertFalse(region[15 * w + 5])
        assertTrue(region[6 * w + 15])
        assertFalse(region[5 * w + 15])
    }

    @Test
    fun aRegionOnePixelWideIsFilled() {
        val walls = grid().apply { square(5, 5, 25, 25) }
        for (y in 6..24) walls[y * w + 15] = true
        for (y in 6..24) walls[y * w + 17] = true
        val region = FloodFill.region(walls, w, h, 16, 15, gapPx = 0)!!
        assertTrue(region[15 * w + 16])
    }

    @Test
    fun aPointOnALineFillsNothing() {
        val walls = grid().apply { square(5, 5, 25, 25) }
        assertNull(FloodFill.region(walls, w, h, 5, 15, gapPx = 0))
    }

    @Test
    fun theFillGrowsBackToTheLinesAfterBridgingAGap() {
        val walls = grid().apply { square(5, 5, 25, 25) }
        val region = FloodFill.region(walls, w, h, 15, 15, gapPx = 2)!!
        // Without growing back it would stop two pixels short of each line.
        assertTrue(region[6 * w + 6])
        assertTrue(region[24 * w + 24])
    }

    @Test
    fun aRegionOutlineIsOneLoopAndAnIslandAddsAHole() {
        val plain = grid().apply { square(5, 5, 25, 25) }
        val region = FloodFill.region(plain, w, h, 15, 15, gapPx = 0)!!
        val loops = FloodFill.outlines(region, w, h, 0.5f)
        assertEquals(1, loops.size)
        // A square needs four corners once its straight runs are taken out.
        assertEquals(4, loops[0].size / 2)

        val withIsland = grid().apply {
            square(5, 5, 30, 30)
            square(14, 14, 20, 20)
        }
        val ring = FloodFill.region(withIsland, w, h, 8, 8, gapPx = 0)!!
        assertEquals(2, FloodFill.outlines(ring, w, h, 0.5f).size)
    }

    @Test
    fun simplifyKeepsTheShape() {
        val loop = floatArrayOf(0f, 0f, 5f, 0f, 10f, 0f, 10f, 5f, 10f, 10f, 5f, 10f, 0f, 10f, 0f, 5f)
        assertEquals(4, FloodFill.simplify(loop, 0.1f).size / 2)
    }

    @Test
    fun aFillKnowsItsAreaWithHolesTakenAway() {
        val outer = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        val hole = floatArrayOf(2f, 2f, 2f, 4f, 4f, 4f, 4f, 2f)
        assertEquals(100f, app.betternotes.core.model.FillItem(1, listOf(outer), 0).area, 0.001f)
        assertEquals(96f, app.betternotes.core.model.FillItem(1, listOf(outer, hole), 0).area, 0.001f)
    }
}
