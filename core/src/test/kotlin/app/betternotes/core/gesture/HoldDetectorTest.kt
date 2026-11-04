package app.betternotes.core.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldDetectorTest {
    private val detector = HoldDetector(slop = 5f).apply { start(0f, 0f, 0L) }

    @Test
    fun restingBeforeMovingIsNotAHold() {
        assertFalse(detector.isHeld(10_000L, 500L))
    }

    @Test
    fun stoppingAfterMovingIsAHoldOnceTheDelayPasses() {
        detector.move(50f, 0f, 100L)
        assertFalse(detector.isHeld(599L, 500L))
        assertTrue(detector.isHeld(600L, 500L))
        assertEquals(600L, detector.heldAtMs(500L))
    }

    @Test
    fun jitterWithinTheSlopDoesNotRestartTheWait() {
        detector.move(50f, 0f, 100L)
        detector.move(53f, 2f, 400L)
        assertTrue(detector.isHeld(600L, 500L))
        assertEquals(53f, detector.x, 0f)
    }

    @Test
    fun movingOnRestartsTheWait() {
        detector.move(50f, 0f, 100L)
        detector.move(70f, 0f, 500L)
        assertFalse(detector.isHeld(600L, 500L))
        assertTrue(detector.isHeld(1000L, 500L))
    }
}
