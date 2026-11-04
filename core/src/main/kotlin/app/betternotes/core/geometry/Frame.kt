package app.betternotes.core.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A rectangle that may be rotated about its center, the placement of every item that is not ink.
 * [left] and [top] describe the rectangle before rotation.
 */
data class Frame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
) {
    val centerX get() = left + width / 2f
    val centerY get() = top + height / 2f
    val bounds get() = Bounds.ofSize(left, top, width, height).rotatedBounds(rotation)

    /** World position of a point given as a fraction of the frame's width and height. */
    fun worldX(u: Float, v: Float): Float {
        val r = Math.toRadians(rotation.toDouble())
        return centerX + (u - 0.5f) * width * cos(r).toFloat() - (v - 0.5f) * height * sin(r).toFloat()
    }

    fun worldY(u: Float, v: Float): Float {
        val r = Math.toRadians(rotation.toDouble())
        return centerY + (u - 0.5f) * width * sin(r).toFloat() + (v - 0.5f) * height * cos(r).toFloat()
    }

    fun contains(x: Float, y: Float, margin: Float = 0f): Boolean {
        val r = Math.toRadians(rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val dx = x - centerX
        val dy = y - centerY
        return abs(dx * c + dy * s) <= width / 2f + margin && abs(dy * c - dx * s) <= height / 2f + margin
    }

    /**
     * Applies [t], which must not flip. A non-uniform scale of a rotated frame would need a skew,
     * which a frame cannot hold, so the frame keeps its right angles and scales along its own axes.
     */
    fun transformed(t: Affine): Frame {
        val r = Math.toRadians(rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val xAxisX = t.a * c + t.c * s
        val xAxisY = t.b * c + t.d * s
        val yAxisX = t.c * c - t.a * s
        val yAxisY = t.d * c - t.b * s
        val w = width * hypot(xAxisX, xAxisY)
        val h = height * hypot(yAxisX, yAxisY)
        val cx = t.mapX(centerX, centerY)
        val cy = t.mapY(centerX, centerY)
        val degrees = Math.toDegrees(atan2(xAxisY, xAxisX).toDouble()).toFloat()
        return Frame(cx - w / 2f, cy - h / 2f, w, h, degrees)
    }
}
