package app.betternotes.core.gesture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationGestureTest {
    /** Two fingers [spread] px apart, on a line turned by [degrees] about (500, 500). */
    private fun fingers(degrees: Double, spread: Double = 200.0): FloatArray {
        val a = degrees * PI / 180.0
        val half = spread / 2
        return floatArrayOf(
            (500 - half * cos(a)).toFloat(),
            (500 - half * sin(a)).toFloat(),
            (500 + half * cos(a)).toFloat(),
            (500 + half * sin(a)).toFloat(),
        )
    }

    private fun RotationGesture.moveTo(degrees: Double, spread: Double = 200.0): RotationStep? {
        val f = fingers(degrees, spread)
        return move(f[0], f[1], f[2], f[3])
    }

    private fun RotationGesture.begin(degrees: Double = 0.0) {
        val f = fingers(degrees)
        start(f[0], f[1], f[2], f[3])
    }

    @Test
    fun aSmallWobbleDoesNotTurnThePage() {
        val gesture = RotationGesture()
        gesture.begin()
        for (d in listOf(2.0, -3.0, 12.0, 19.5, -15.0)) assertNull(gesture.moveTo(d))
        assertFalse(gesture.active)
    }

    @Test
    fun aTwistPastTheThresholdStartsFollowingWithoutAJump() {
        val gesture = RotationGesture(thresholdDegrees = 10f)
        gesture.begin()
        assertNull(gesture.moveTo(12.0))
        assertTrue(gesture.active)
        val step = gesture.moveTo(20.0)
        assertNotNull(step)
        assertEquals(8f, step!!.degrees, 0.01f)
        assertEquals(1f, step.scale, 0.001f)
    }

    @Test
    fun followingTurnsBackWhenTheFingersTurnBack() {
        val gesture = RotationGesture(thresholdDegrees = 10f)
        gesture.begin()
        gesture.moveTo(15.0)
        assertEquals(10f, gesture.moveTo(25.0)!!.degrees, 0.01f)
        assertEquals(-20f, gesture.moveTo(5.0)!!.degrees, 0.01f)
    }

    @Test
    fun scaleAndPanFollowTheFingersWhileTurning() {
        val gesture = RotationGesture(thresholdDegrees = 10f)
        gesture.begin()
        gesture.moveTo(15.0)
        val step = gesture.moveTo(15.0, spread = 300.0)!!
        assertEquals(1.5f, step.scale, 0.001f)
        assertEquals(0f, step.dx, 0.01f)
        assertEquals(500f, step.focusX, 0.01f)
    }

    @Test
    fun theShortWayRoundIsTaken() {
        val gesture = RotationGesture(thresholdDegrees = 10f)
        gesture.begin(170.0)
        // 170 to -175 is a turn of 15 degrees the short way, past the threshold. -175 to -165 is 10 more.
        assertNull(gesture.moveTo(-175.0))
        assertTrue(gesture.active)
        assertEquals(10f, gesture.moveTo(-165.0)!!.degrees, 0.01f)
    }

    @Test
    fun endForgetsTheGesture() {
        val gesture = RotationGesture()
        gesture.begin()
        gesture.moveTo(20.0)
        gesture.end()
        assertFalse(gesture.tracking)
        assertNull(gesture.moveTo(40.0))
    }

    @Test
    fun snapsToUprightAndRightAnglesOnlyWhenClose() {
        assertEquals(0f, snappedRotation(3f), 0f)
        assertEquals(0f, snappedRotation(-3.5f), 0f)
        assertEquals(90f, snappedRotation(92f), 0f)
        assertEquals(-90f, snappedRotation(-88f), 0f)
        assertEquals(180f, snappedRotation(177f), 0f)
        assertEquals(180f, snappedRotation(-178f), 0f)
        assertEquals(37f, snappedRotation(37f), 0f)
        assertEquals(10f, snappedRotation(370f), 0f)
    }
}
