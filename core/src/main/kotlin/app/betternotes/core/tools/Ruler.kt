package app.betternotes.core.tools

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A straightedge lying over the canvas, in view pixels. It is a rectangle around ([centerX], [centerY]),
 * rotated by [angleRadians]. Two fingers carry it like a real ruler: it follows their midpoint and turns
 * with them, and spreading them does not stretch it.
 */
class Ruler(
    var centerX: Float,
    var centerY: Float,
    val length: Float,
    val thickness: Float,
    var angleRadians: Float = 0f,
) {
    private var grabX = 0f
    private var grabY = 0f
    private var grabAngle = 0f

    private val unitX get() = cos(angleRadians)
    private val unitY get() = sin(angleRadians)

    /** Whether a point lies on the ruler, with [margin] extra around it for a finger's width. */
    fun contains(x: Float, y: Float, margin: Float = 0f): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        val along = dx * unitX + dy * unitY
        val across = -dx * unitY + dy * unitX
        return abs(along) <= length / 2f + margin && abs(across) <= thickness / 2f + margin
    }

    /** Starts a two-finger move from these finger positions. */
    fun grab(x0: Float, y0: Float, x1: Float, y1: Float) {
        grabX = (x0 + x1) / 2f
        grabY = (y0 + y1) / 2f
        grabAngle = atan2(y1 - y0, x1 - x0)
    }

    /** Follows the fingers from where they were last seen, so the ruler moves as if held. */
    fun drag(x0: Float, y0: Float, x1: Float, y1: Float) {
        val midX = (x0 + x1) / 2f
        val midY = (y0 + y1) / 2f
        val angle = atan2(y1 - y0, x1 - x0)
        val turn = angle - grabAngle
        // Turns about the fingers, not its own center, as a held ruler does.
        val offsetX = centerX - grabX
        val offsetY = centerY - grabY
        val c = cos(turn)
        val s = sin(turn)
        centerX = midX + offsetX * c - offsetY * s
        centerY = midY + offsetX * s + offsetY * c
        angleRadians += turn
        grabX = midX
        grabY = midY
        grabAngle = angle
    }

    /**
     * Which long edge a point is close enough to draw along, or [NO_EDGE]. The result is 1 for the edge
     * on the positive side of the ruler and -1 for the other. A point past either end has no edge.
     */
    fun edgeNear(x: Float, y: Float, reach: Float): Int {
        val dx = x - centerX
        val dy = y - centerY
        val along = dx * unitX + dy * unitY
        if (abs(along) > length / 2f + reach) return NO_EDGE
        val across = -dx * unitY + dy * unitX
        val distance = abs(abs(across) - thickness / 2f)
        if (distance > reach) return NO_EDGE
        return if (across >= 0f) 1 else -1
    }

    /** The point on an edge's line closest to the given one. */
    fun projectX(x: Float, y: Float, edge: Int): Float = centerX + alongOf(x, y) * unitX - edge * thickness / 2f * unitY

    fun projectY(x: Float, y: Float, edge: Int): Float = centerY + alongOf(x, y) * unitY + edge * thickness / 2f * unitX

    /** The angle shown to the user, from 0 up to but not including 180 degrees. */
    fun displayDegrees(): Float {
        val degrees = Math.toDegrees(angleRadians.toDouble()).toFloat()
        return ((degrees % HALF_TURN) + HALF_TURN) % HALF_TURN
    }

    private fun alongOf(x: Float, y: Float) = (x - centerX) * unitX + (y - centerY) * unitY

    companion object {
        const val NO_EDGE = 0
        private const val HALF_TURN = 180f
    }
}
