package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class TapeOptionsTest {
    @Test
    fun aStripTakesTheSlidersThicknessAndOpacity() {
        val options = TapeOptions(thickness = 40, opacity = 0.5f)
        val strip = options.item(1L, 0f, 0f, 100f, 0f)
        assertEquals(40f, strip.frame.height, 0.001f)
        assertEquals(0.5f, strip.opacity, 0.001f)
        // Without a slider thickness, the selected preset width is used.
        assertEquals(20f, TapeOptions().item(2L, 0f, 0f, 100f, 0f).frame.height, 0.001f)
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
}
