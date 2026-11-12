package app.betternotes.core.geometry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnedBoxTest {
    private fun inside(x: Float, y: Float, turn: Float = 0f, margin: Float = 0f) =
        TurnedBox.contains(x, y, left = 100f, top = 200f, width = 300f, height = 50f, turnDegrees = turn, pivotX = 250f, pivotY = 225f, margin = margin)

    @Test
    fun `a point in an upright box is in it, and one beyond the edge is not`() {
        assertTrue(inside(120f, 210f))
        assertFalse(inside(95f, 210f))
        assertFalse(inside(120f, 260f))
    }

    @Test
    fun `a margin takes in the strip along every edge, and no more`() {
        assertTrue(inside(95f, 210f, margin = 8f))
        assertTrue(inside(405f, 245f, margin = 8f))
        assertTrue(inside(250f, 195f, margin = 8f))
        assertFalse(inside(90f, 210f, margin = 8f))
        assertFalse(inside(250f, 262f, margin = 8f))
    }

    @Test
    fun `a box turned a quarter has its long side up and down`() {
        // Turned 90 degrees about its middle, the 300 by 50 box stands 50 across and 300 high.
        assertTrue(inside(250f, 100f, turn = 90f))
        assertFalse(inside(120f, 225f, turn = 90f))
        assertTrue(inside(250f, 90f, turn = 90f, margin = 15f))
    }
}
