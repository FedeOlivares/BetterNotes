package app.betternotes.core.tools

import app.betternotes.core.model.StrokePoints
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGesturesTest {
    private fun stroke(xy: FloatArray): StrokePoints {
        val stride = StrokePoints.STRIDE
        val packed = FloatArray(xy.size / 2 * stride)
        for (i in 0 until xy.size / 2) {
            packed[i * stride] = xy[i * 2]
            packed[i * stride + 1] = xy[i * 2 + 1]
        }
        return StrokePoints(packed)
    }

    private fun circle(turns: Float): StrokePoints {
        val n = 80
        val xy = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = (turns * 2 * Math.PI * i / (n - 1)).toFloat()
            xy[i * 2] = 100f + 40f * cos(a)
            xy[i * 2 + 1] = 100f + 40f * sin(a)
        }
        return stroke(xy)
    }

    private fun zigzag(reversals: Int): StrokePoints {
        val xy = ArrayList<Float>()
        for (r in 0..reversals) {
            val left = r % 2 == 0
            xy += if (left) 0f else 40f
            xy += r * 6f
            xy += if (left) 40f else 0f
            xy += r * 6f + 3f
        }
        return stroke(xy.toFloatArray())
    }

    private fun line(length: Float): StrokePoints {
        val xy = FloatArray(16)
        for (i in 0 until 8) {
            xy[i * 2] = length * i / 7f
        }
        return stroke(xy)
    }

    @Test fun `a zigzag is a scribble`() = assertTrue(StrokeGestures.isScribble(zigzag(8)))

    @Test fun `a straight line is not a scribble`() = assertFalse(StrokeGestures.isScribble(line(250f)))

    @Test fun `a circle is not a scribble`() = assertFalse(StrokeGestures.isScribble(circle(1f)))

    @Test fun `a full circle is a closed loop`() = assertTrue(StrokeGestures.isClosedLoop(circle(1.05f)))

    @Test fun `half a circle is not a closed loop`() = assertFalse(StrokeGestures.isClosedLoop(circle(0.5f)))

    @Test fun `a line is not a closed loop`() = assertFalse(StrokeGestures.isClosedLoop(line(180f)))

    @Test fun `a point on the line is near it and the middle is not`() {
        val loop = circle(1f)
        assertTrue(StrokeGestures.isNearLine(loop, 140f, 100f, 5f))
        assertFalse(StrokeGestures.isNearLine(loop, 100f, 100f, 5f))
    }

    @Test fun `stabilizer level one is the pen and higher levels lag`() {
        val out = FloatArray(2)
        val pen = Stabilizer(1)
        pen.next(0f, 0f, 0L, out)
        pen.next(10f, 0f, 10L, out)
        assertEquals(10f, out[0], 0f)
        val steady = Stabilizer(4)
        steady.next(0f, 0f, 0L, out)
        steady.next(10f, 0f, 10L, out)
        assertEquals(10f * (1f - kotlin.math.exp(-10f / 75f)), out[0], 1e-4f)
    }

    @Test fun `stabilizer lag does not depend on the sample rate`() {
        fun after(stepMs: Long): Float {
            val out = FloatArray(2)
            val steady = Stabilizer(3)
            var t = 0L
            while (t <= 200L) {
                steady.next(if (t == 0L) 0f else 100f, 0f, t, out)
                t += stepMs
            }
            return out[0]
        }
        assertEquals(after(2L), after(8L), 1f)
    }

    @Test fun `a point is inside a circle only within its line`() {
        val loop = circle(1.05f)
        assertTrue(StrokeGestures.isInside(loop, 100f, 100f))
        assertFalse(StrokeGestures.isInside(loop, 200f, 100f))
    }
}
