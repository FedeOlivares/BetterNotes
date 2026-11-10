package app.betternotes.core.tools

import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.StrokePointsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot

class StrokeSmoothingTest {
    private fun line(count: Int, wobble: Float, seed: Long = 1L): StrokePoints {
        val random = Random(seed)
        val b = StrokePointsBuilder()
        for (i in 0 until count) {
            // A straight line along x at y = 50, with the hand's wobble across it.
            b.add(i * 1f, 50f + (random.nextFloat() - 0.5f) * 2f * wobble, 0.5f, -1f, -1f, i * 4f)
        }
        return b.build()
    }

    @Test
    fun levelZeroOrAShortLineIsLeftAlone() {
        val points = line(100, 2f)
        assertSame(points, StrokeSmoothing.smooth(points, 0))
        val dot = StrokePointsBuilder().apply { add(1f, 1f, 0.5f, -1f, -1f, 0f); add(1.1f, 1f, 0.5f, -1f, -1f, 4f) }.build()
        assertSame(dot, StrokeSmoothing.smooth(dot, 5))
    }

    @Test
    fun wobbleGoesAndTheEndsStay() {
        val points = line(200, 2f)
        val smooth = StrokeSmoothing.smooth(points, 6)
        // The two ends are where the pen touched and lifted.
        assertEquals(points.x(0), smooth.x(0), 1e-3f)
        assertEquals(points.y(0), smooth.y(0), 1e-3f)
        assertEquals(points.x(points.size - 1), smooth.x(smooth.size - 1), 1e-3f)
        assertEquals(points.y(points.size - 1), smooth.y(smooth.size - 1), 1e-3f)
        // In the middle the line is within a fraction of the wobble from the straight line it was meant to be.
        var worst = 0f
        for (i in smooth.size / 4 until smooth.size * 3 / 4) worst = maxOf(worst, abs(smooth.y(i) - 50f))
        assertTrue("worst $worst", worst < 1f)
    }

    @Test
    fun theShapeStaysAndNothingLags() {
        // A circle of radius 40 traced with a wobble.
        val random = Random(3)
        val b = StrokePointsBuilder()
        for (i in 0..360) {
            val a = Math.toRadians(i.toDouble())
            val r = 40f + (random.nextFloat() - 0.5f) * 2f
            b.add(100f + r * Math.cos(a).toFloat(), 100f + r * Math.sin(a).toFloat(), 0.5f, -1f, -1f, i * 4f)
        }
        val smooth = StrokeSmoothing.smooth(b.build(), 4)
        var worst = 0f
        for (i in 0 until smooth.size) worst = maxOf(worst, abs(hypot(smooth.x(i) - 100f, smooth.y(i) - 100f) - 40f))
        // A gentle curve loses a little radius to the smoothing, but nothing like the wobble, and it does not slide along the line.
        assertTrue("worst $worst", worst < 1.5f)
    }

    @Test
    fun pressureAndTimeFollowTheLine() {
        val smooth = StrokeSmoothing.smooth(line(100, 1f), 3)
        assertEquals(0.5f, smooth.pressure(smooth.size / 2), 1e-3f)
        assertEquals(0f, smooth.timeMs(0), 1e-3f)
        assertEquals(99f * 4f, smooth.timeMs(smooth.size - 1), 1e-2f)
        for (i in 1 until smooth.size) assertTrue(smooth.timeMs(i) >= smooth.timeMs(i - 1))
    }
}
