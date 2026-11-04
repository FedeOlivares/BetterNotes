package app.betternotes.core.model

import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds

/**
 * The raw inputs of one stroke, packed into a single array so a page of ink costs one object per
 * stroke rather than one per sample. Each sample holds x, y, pressure, tilt, orientation and
 * elapsed milliseconds. The array is shared, so treat it as read-only.
 */
class StrokePoints(val packed: FloatArray) {
    init {
        require(packed.size % STRIDE == 0) { "Partial sample in ${packed.size} floats" }
    }

    val size get() = packed.size / STRIDE

    fun x(i: Int) = packed[i * STRIDE]
    fun y(i: Int) = packed[i * STRIDE + 1]
    fun pressure(i: Int) = packed[i * STRIDE + 2]
    fun tilt(i: Int) = packed[i * STRIDE + 3]
    fun orientation(i: Int) = packed[i * STRIDE + 4]
    fun timeMs(i: Int) = packed[i * STRIDE + 5]

    fun bounds(): Bounds {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < packed.size) {
            val x = packed[i]
            val y = packed[i + 1]
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
            i += STRIDE
        }
        return Bounds(left, top, right, bottom)
    }

    fun transformed(t: Affine): StrokePoints {
        val out = packed.copyOf()
        var i = 0
        while (i < out.size) {
            val x = packed[i]
            val y = packed[i + 1]
            out[i] = t.mapX(x, y)
            out[i + 1] = t.mapY(x, y)
            i += STRIDE
        }
        return StrokePoints(out)
    }

    override fun equals(other: Any?) = other is StrokePoints && packed.contentEquals(other.packed)
    override fun hashCode() = packed.contentHashCode()
    override fun toString() = "StrokePoints(size=$size)"

    companion object {
        const val STRIDE = 6

        /** Pressure, tilt or orientation the input device did not report. */
        const val UNKNOWN = -1f
    }
}

/** Accumulates samples for a [StrokePoints]. Reusable after [clear]. */
class StrokePointsBuilder(expectedPoints: Int = 64) {
    private var data = FloatArray(expectedPoints * StrokePoints.STRIDE)
    private var used = 0

    val size get() = used / StrokePoints.STRIDE

    fun add(
        x: Float,
        y: Float,
        pressure: Float = StrokePoints.UNKNOWN,
        tilt: Float = StrokePoints.UNKNOWN,
        orientation: Float = StrokePoints.UNKNOWN,
        timeMs: Float = 0f,
    ) {
        if (used + StrokePoints.STRIDE > data.size) {
            data = data.copyOf(maxOf(data.size * 2, StrokePoints.STRIDE * 16))
        }
        data[used] = x
        data[used + 1] = y
        data[used + 2] = pressure
        data[used + 3] = tilt
        data[used + 4] = orientation
        data[used + 5] = timeMs
        used += StrokePoints.STRIDE
    }

    fun add(from: StrokePoints, i: Int) {
        add(from.x(i), from.y(i), from.pressure(i), from.tilt(i), from.orientation(i), from.timeMs(i))
    }

    /**
     * Adds the point a fraction [t] of the way from sample [i] to the next. Orientation is taken
     * from the nearer sample because it wraps around, so a plain average could point anywhere.
     */
    fun addBetween(from: StrokePoints, i: Int, t: Float) {
        val j = i + 1
        add(
            lerp(from.x(i), from.x(j), t),
            lerp(from.y(i), from.y(j), t),
            lerp(from.pressure(i), from.pressure(j), t),
            lerp(from.tilt(i), from.tilt(j), t),
            if (t < 0.5f) from.orientation(i) else from.orientation(j),
            lerp(from.timeMs(i), from.timeMs(j), t),
        )
    }

    fun build() = StrokePoints(data.copyOf(used))

    fun clear() {
        used = 0
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
