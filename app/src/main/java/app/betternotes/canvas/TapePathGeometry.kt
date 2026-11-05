package app.betternotes.canvas

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A strip of tape's path, resampled at an even spacing, with the direction of travel at each sample. A place on the
 * strip is given as a distance [s] along it and a sideways offset [u] from its middle, so stripes and marks can be
 * laid out in the strip's own frame and then follow every bend.
 */
internal class TapePathGeometry {
    var count = 0
        private set
    var length = 0f
        private set
    private var step = 1f
    private var cx = FloatArray(0)
    private var cy = FloatArray(0)
    private var tx = FloatArray(0)
    private var ty = FloatArray(0)

    /** Resamples [points], packed x,y pairs. */
    fun build(points: FloatArray) {
        val n = points.size / 2
        count = 0
        length = 0f
        if (n < 2) return
        val along = FloatArray(n)
        for (i in 1 until n) {
            along[i] = along[i - 1] + hypot(points[i * 2] - points[i * 2 - 2], points[i * 2 + 1] - points[i * 2 - 1])
        }
        length = along[n - 1]
        if (length <= 0f) return
        val samples = min(MAX_SAMPLES, max(2, ceil(length / TARGET_STEP).toInt() + 1))
        step = length / (samples - 1)
        if (cx.size < samples) {
            cx = FloatArray(samples)
            cy = FloatArray(samples)
            tx = FloatArray(samples)
            ty = FloatArray(samples)
        }
        var segment = 0
        for (i in 0 until samples) {
            val s = i * step
            while (segment < n - 2 && along[segment + 1] < s) segment++
            val span = along[segment + 1] - along[segment]
            val t = if (span > 0f) ((s - along[segment]) / span).coerceIn(0f, 1f) else 0f
            cx[i] = points[segment * 2] + (points[segment * 2 + 2] - points[segment * 2]) * t
            cy[i] = points[segment * 2 + 1] + (points[segment * 2 + 3] - points[segment * 2 + 1]) * t
        }
        // The direction at a sample is taken over a few neighbours, so a corner turns the strip gradually.
        for (i in 0 until samples) {
            val a = max(0, i - DIRECTION_REACH)
            val b = min(samples - 1, i + DIRECTION_REACH)
            val dx = cx[b] - cx[a]
            val dy = cy[b] - cy[a]
            val size = hypot(dx, dy)
            tx[i] = if (size > 0f) dx / size else 1f
            ty[i] = if (size > 0f) dy / size else 0f
        }
        count = samples
    }

    /** The sample, as a position along the strip. Past either end the strip is carried on straight. */
    private fun locate(s: Float, out: FloatArray) {
        val last = count - 1
        val index = (s / step)
        when {
            s <= 0f -> {
                out[0] = cx[0] + tx[0] * s
                out[1] = cy[0] + ty[0] * s
                out[2] = tx[0]
                out[3] = ty[0]
            }
            s >= length -> {
                out[0] = cx[last] + tx[last] * (s - length)
                out[1] = cy[last] + ty[last] * (s - length)
                out[2] = tx[last]
                out[3] = ty[last]
            }
            else -> {
                val i = min(index.toInt(), last - 1)
                val f = index - i
                out[0] = cx[i] + (cx[i + 1] - cx[i]) * f
                out[1] = cy[i] + (cy[i + 1] - cy[i]) * f
                val dx = tx[i] + (tx[i + 1] - tx[i]) * f
                val dy = ty[i] + (ty[i + 1] - ty[i]) * f
                val size = hypot(dx, dy)
                out[2] = if (size > 0f) dx / size else tx[i]
                out[3] = if (size > 0f) dy / size else ty[i]
            }
        }
    }

    private val here = FloatArray(4)

    /** The page position of distance [s] along the strip and [u] to the side of its middle, written to [out] as x then y. */
    fun place(s: Float, u: Float, out: FloatArray) {
        locate(s, here)
        // To the side is a quarter turn from the direction of travel.
        out[0] = here[0] - here[3] * u
        out[1] = here[1] + here[2] * u
    }

    private companion object {
        const val TARGET_STEP = 1.5f
        const val MAX_SAMPLES = 6000
        const val DIRECTION_REACH = 3
    }
}
