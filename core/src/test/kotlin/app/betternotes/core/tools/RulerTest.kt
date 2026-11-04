package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RulerTest {
    private val ruler = Ruler(centerX = 500f, centerY = 500f, length = 800f, thickness = 100f)

    @Test
    fun containsFollowsTheRotation() {
        assertTrue(ruler.contains(850f, 500f))
        assertFalse(ruler.contains(500f, 600f))
        ruler.angleRadians = (PI / 2).toFloat()
        assertFalse(ruler.contains(850f, 500f))
        assertTrue(ruler.contains(500f, 850f))
    }

    @Test
    fun twoFingersCarryItWithoutStretchingIt() {
        ruler.grab(400f, 500f, 600f, 500f)
        ruler.drag(500f, 600f, 700f, 600f)
        assertEquals(600f, ruler.centerX, 0.01f)
        assertEquals(600f, ruler.centerY, 0.01f)
        assertEquals(0f, ruler.angleRadians, 0.0001f)

        ruler.drag(450f, 600f, 750f, 600f)
        assertEquals(800f, ruler.length, 0f)
        assertEquals(600f, ruler.centerX, 0.01f)
    }

    @Test
    fun turningTheFingersTurnsItAboutThem() {
        // Fingers to the right of center, so the center swings around them.
        ruler.grab(600f, 500f, 800f, 500f)
        ruler.drag(700f, 400f, 700f, 600f)
        assertEquals((PI / 2).toFloat(), ruler.angleRadians, 0.0001f)
        assertEquals(700f, ruler.centerX, 0.01f)
        assertEquals(300f, ruler.centerY, 0.01f)
    }

    @Test
    fun aPointNearALongEdgeSnapsToIt() {
        assertEquals(1, ruler.edgeNear(500f, 560f, reach = 20f))
        assertEquals(-1, ruler.edgeNear(500f, 440f, reach = 20f))
        assertEquals(Ruler.NO_EDGE, ruler.edgeNear(500f, 500f, reach = 20f))
        assertEquals(Ruler.NO_EDGE, ruler.edgeNear(500f, 700f, reach = 20f))
        assertEquals(Ruler.NO_EDGE, ruler.edgeNear(1000f, 550f, reach = 20f))

        assertEquals(620f, ruler.projectX(620f, 565f, 1), 0.01f)
        assertEquals(550f, ruler.projectY(620f, 565f, 1), 0.01f)
    }

    @Test
    fun projectingFollowsTheRotation() {
        ruler.angleRadians = (PI / 2).toFloat()
        val edge = ruler.edgeNear(445f, 600f, reach = 20f)
        assertEquals(1, edge)
        assertEquals(450f, ruler.projectX(445f, 600f, edge), 0.01f)
        assertEquals(600f, ruler.projectY(445f, 600f, edge), 0.01f)
    }

    @Test
    fun theAngleShownStaysUnderAHalfTurn() {
        ruler.angleRadians = (-PI / 4).toFloat()
        assertEquals(135f, ruler.displayDegrees(), 0.01f)
    }
}
