package app.betternotes.core.tools

import app.betternotes.core.model.StrokePoints
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Smooths a finished stroke, as a very strong stabilizer would if it were run over the whole line afterwards: the wobble of the hand goes
 * and the shape stays. It has no lag, since it sees the line from both sides, and the two ends stay where the pen touched and lifted.
 */
object StrokeSmoothing {
    /** How far the smoothing reaches, in points, for each level of the setting. */
    const val SIGMA_PT_PER_LEVEL = 0.9f

    /** The distance between the samples the line is cut into, as a share of how far the smoothing reaches. */
    private const val STEP_OF_SIGMA = 0.25f
    private const val MIN_STEP_PT = 0.2f
    private const val KERNEL_SIGMAS = 3f

    /** [points] smoothed at [level] of the setting, or the same points when the level is 0 or the line is too short to have a shape. */
    fun smooth(points: StrokePoints, level: Int): StrokePoints = smoothBy(points, level * SIGMA_PT_PER_LEVEL)

    fun smoothBy(points: StrokePoints, sigmaPt: Float): StrokePoints {
        val count = points.size
        if (count < 3 || sigmaPt <= 0f) return points
        val along = FloatArray(count)
        for (i in 1 until count) {
            along[i] = along[i - 1] + hypot(points.x(i) - points.x(i - 1), points.y(i) - points.y(i - 1))
        }
        val length = along[count - 1]
        val step = max(MIN_STEP_PT, sigmaPt * STEP_OF_SIGMA)
        // A line shorter than a couple of steps is a dot or a tick, with no wobble to take out.
        if (length < step * 2f) return points
        val samples = ceil(length / step).toInt() + 1
        val gap = length / (samples - 1)

        // The line cut into evenly spaced samples, every channel following the distance along it.
        val channels = Array(StrokePoints.STRIDE) { FloatArray(samples) }
        var at = 0
        for (k in 0 until samples) {
            val d = k * gap
            while (at < count - 2 && along[at + 1] < d) at++
            val span = along[at + 1] - along[at]
            val t = if (span <= 0f) 0f else ((d - along[at]) / span).coerceIn(0f, 1f)
            for (c in 0 until StrokePoints.STRIDE) {
                val a = points.packed[at * StrokePoints.STRIDE + c]
                val b = points.packed[(at + 1) * StrokePoints.STRIDE + c]
                channels[c][k] = a + (b - a) * t
            }
        }

        val sigma = sigmaPt / gap
        val radius = ceil(KERNEL_SIGMAS * sigma).toInt().coerceAtLeast(1)
        val weights = FloatArray(radius + 1) { exp(-(it * it) / (2f * sigma * sigma)) }
        var total = weights[0]
        for (j in 1..radius) total += 2f * weights[j]
        for (j in 0..radius) weights[j] /= total

        val x = blur(channels[0], weights, odd = true)
        val y = blur(channels[1], weights, odd = true)
        val out = FloatArray(samples * StrokePoints.STRIDE)
        for (k in 0 until samples) {
            val base = k * StrokePoints.STRIDE
            for (c in 0 until StrokePoints.STRIDE) out[base + c] = channels[c][k]
            out[base] = x[k]
            out[base + 1] = y[k]
        }
        return StrokePoints(out)
    }

    /**
     * [values] smoothed by [weights] (the middle one first, then each step out to either side). Past an end the line goes on as its
     * mirror image turned about the end point when [odd], which keeps the end where it is and the line heading the way it was.
     */
    private fun blur(values: FloatArray, weights: FloatArray, odd: Boolean): FloatArray {
        val n = values.size
        val last = n - 1
        fun at(index: Int): Float {
            var i = index
            var sign = 1f
            var shift = 0f
            // Mirror back into range, however far out the index is.
            while (i < 0 || i > last) {
                if (i < 0) {
                    if (odd) {
                        shift += sign * 2f * values[0]
                        sign = -sign
                    }
                    i = -i
                } else {
                    if (odd) {
                        shift += sign * 2f * values[last]
                        sign = -sign
                    }
                    i = 2 * last - i
                }
            }
            return if (odd) shift + sign * values[i] else values[i]
        }
        val out = FloatArray(n)
        for (k in 0 until n) {
            var sum = weights[0] * values[k]
            for (j in 1 until weights.size) sum += weights[j] * (at(k - j) + at(k + j))
            out[k] = sum
        }
        return out
    }
}
