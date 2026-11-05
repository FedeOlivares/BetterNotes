package app.betternotes.core.tools

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The reference angles for straight lines: 0, 45, 90 and on round to 315, counted anticlockwise from the right as on a
 * protractor. A line within [SNAP_DEGREES] (1.5) of one is turned onto it, and one within [SHOW_DEGREES] gets its centerline shown.
 */
object AngleGuide {
    const val SNAP_DEGREES = 1.5f
    const val SHOW_DEGREES = 10f
    private const val STEP = 45f

    /** A line shorter than this, in points, has no useful angle. */
    const val MIN_LENGTH_PT = 6f

    /** The angle of the line from (x0, y0) to (x1, y1), from 0 up to 360, with y growing downward as on a page. */
    fun angle(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val degrees = Math.toDegrees(atan2((y0 - y1).toDouble(), (x1 - x0).toDouble())).toFloat()
        return if (degrees < 0f) degrees + 360f else degrees
    }

    /** The reference angle nearest to [degrees]. */
    fun nearest(degrees: Float): Float = (degrees / STEP).roundToInt() * STEP % 360f

    /** How far [degrees] is from its nearest reference angle, never negative. */
    fun distance(degrees: Float): Float = abs(degrees - (degrees / STEP).roundToInt() * STEP)

    /** The end of the line, moved onto the reference angle if it is within [SNAP_DEGREES] of it, keeping the line's length. */
    fun snap(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val length = hypot(x1 - x0, y1 - y0)
        if (length < MIN_LENGTH_PT) return floatArrayOf(x1, y1)
        val angle = angle(x0, y0, x1, y1)
        if (distance(angle) > SNAP_DEGREES) return floatArrayOf(x1, y1)
        val radians = nearest(angle) * PI / 180.0
        return floatArrayOf(x0 + length * cos(radians).toFloat(), y0 - length * sin(radians).toFloat())
    }
}
