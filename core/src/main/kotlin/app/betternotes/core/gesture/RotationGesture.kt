package app.betternotes.core.gesture

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** How the page is to follow two fingers: turn by [degrees] and scale by [scale] about ([focusX], [focusY]), then pan by ([dx], [dy]). */
class RotationStep(val degrees: Float, val scale: Float, val dx: Float, val dy: Float, val focusX: Float, val focusY: Float)

/**
 * Tells a twist of two fingers from a pinch or a pan. The page turns only once the fingers' line has turned by
 * [thresholdDegrees] since they came down, so a pinch with a little wobble never turns it. After that, every move
 * gives a [RotationStep] that keeps the page under the fingers: it turns, scales and pans with them, until they lift.
 * Positions are the screen's own, before the page's turn is undone.
 */
class RotationGesture(private val thresholdDegrees: Float = DEFAULT_THRESHOLD_DEGREES) {
    var active = false
        private set

    /** Whether two fingers are being followed. */
    var tracking = false
        private set

    private var startAngle = 0f
    private var lastAngle = 0f
    private var lastDistance = 0f
    private var lastCx = 0f
    private var lastCy = 0f

    fun start(x0: Float, y0: Float, x1: Float, y1: Float) {
        tracking = true
        active = false
        startAngle = angle(x0, y0, x1, y1)
        lastAngle = startAngle
        lastDistance = hypot(x1 - x0, y1 - y0)
        lastCx = (x0 + x1) / 2f
        lastCy = (y0 + y1) / 2f
    }

    /** The fingers' new positions. Null until the twist is big enough, and then a step for every move. */
    fun move(x0: Float, y0: Float, x1: Float, y1: Float): RotationStep? {
        if (!tracking) return null
        val now = angle(x0, y0, x1, y1)
        val distance = hypot(x1 - x0, y1 - y0)
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        if (!active) {
            if (abs(wrap(now - startAngle)) < thresholdDegrees) return null
            active = true
            // The turn so far is not applied: the page starts following from here, so it does not jump.
            lastAngle = now
            lastDistance = distance
            lastCx = cx
            lastCy = cy
            return null
        }
        val step = RotationStep(
            degrees = wrap(now - lastAngle),
            scale = if (lastDistance > MIN_DISTANCE && distance > MIN_DISTANCE) distance / lastDistance else 1f,
            dx = cx - lastCx,
            dy = cy - lastCy,
            focusX = cx,
            focusY = cy,
        )
        lastAngle = now
        lastDistance = distance
        lastCx = cx
        lastCy = cy
        return step
    }

    fun end() {
        tracking = false
        active = false
    }

    private fun angle(x0: Float, y0: Float, x1: Float, y1: Float) = atan2(y1 - y0, x1 - x0) * RADIANS_TO_DEGREES

    /** An angle difference as a turn of the short way, from -180 to 180. */
    private fun wrap(degrees: Float): Float {
        var d = degrees % FULL_TURN
        if (d > HALF_TURN) d -= FULL_TURN
        if (d < -HALF_TURN) d += FULL_TURN
        return d
    }

    companion object {
        const val DEFAULT_THRESHOLD_DEGREES = 20f
        private const val MIN_DISTANCE = 1f
        private const val FULL_TURN = 360f
        private const val HALF_TURN = 180f
        private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()
    }
}

/** The angle from -180 (not included) to 180, snapped to the nearest multiple of 90 when it is within [reach] of one. */
fun snappedRotation(degrees: Float, reach: Float = 4f): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    val nearest = Math.round(d / 90f) * 90f
    val snapped = if (abs(d - nearest) <= reach) nearest else d
    return if (snapped == -180f) 180f else snapped
}
