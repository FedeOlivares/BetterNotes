package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class TapeOptionsTest {
    @Test
    fun aStripTakesTheSlidersThicknessAndOpacity() {
        val options = TapeOptions(opacity = 0.5f).withWidth(40)
        val strip = options.item(1L, 0f, 0f, 100f, 0f)
        assertEquals(40f, strip.frame.height, 0.001f)
        assertEquals(0.5f, strip.opacity, 0.001f)
        // The slider changed the slot in use, as the pen's does, and left the others.
        assertEquals(listOf(12, 40, 32), options.widths)
    }

    @Test
    fun changingATapesThicknessKeepsItsMiddleLengthAndAngle() {
        val strip = TapeOptions().item(1L, 10f, 10f, 110f, 60f)
        val thicker = strip.withTape(48f, 0.3f)
        assertEquals(strip.frame.centerX, thicker.frame.centerX, 0.001f)
        assertEquals(strip.frame.centerY, thicker.frame.centerY, 0.001f)
        assertEquals(strip.frame.width, thicker.frame.width, 0.001f)
        assertEquals(strip.frame.rotation, thicker.frame.rotation, 0.001f)
        assertEquals(48f, thicker.frame.height, 0.001f)
        assertEquals(0.3f, thicker.opacity, 0.001f)
        // A null leaves a value alone.
        val same = thicker.withTape(null, null)
        assertEquals(thicker, same)
    }

    @Test
    fun tapeThatFollowsThePenKeepsItsEndsAndDropsCrowdedSamples() {
        val raw = floatArrayOf(0f, 0f, 1f, 0f, 2f, 0f, 10f, 0f, 20f, 5f, 21f, 5f)
        val path = smoothTapePath(raw, minGap = 3f)
        assertEquals(0f, path[0], 0f)
        assertEquals(21f, path[path.size - 2], 0f)
        assertEquals(5f, path[path.size - 1], 0f)
        // Samples 1, 2 and the last-but-one were within 3 points of their neighbour.
        assertEquals(4, path.size / 2)
    }

    @Test
    fun aStripAlongAPathTakesTheOptionsAndRestylesLikeAStraightOne() {
        val options = TapeOptions(opacity = 0.6f, pattern = app.betternotes.core.model.TapePattern.DOTS).withWidth(30)
        val strip = options.pathItem(5L, floatArrayOf(0f, 0f, 50f, 0f, 100f, 40f))
        assertEquals(30f, strip.width, 0.001f)
        assertEquals(0.6f, strip.opacity, 0.001f)
        val thicker = strip.withTape(50f, 0.2f)
        assertEquals(50f, thicker.width, 0.001f)
        assertEquals(0.2f, thicker.opacity, 0.001f)
        assertEquals(strip.points.toList(), thicker.points.toList())
        // Its bounds reach half a width beyond its points.
        assertEquals(true, strip.bounds.left <= -15f && strip.bounds.right >= 115f)
    }
}
