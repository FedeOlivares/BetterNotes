package app.betternotes.core.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Axis-aligned rectangle in world units. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun intersects(o: Bounds) = left <= o.right && o.left <= right && top <= o.bottom && o.top <= bottom
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun contains(o: Bounds) = o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom
    fun outset(d: Float) = Bounds(left - d, top - d, right + d, bottom + d)
    fun union(o: Bounds) =
        Bounds(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    /** The rectangle's bounding box after rotating about its own center. */
    fun rotatedBounds(degrees: Float): Bounds {
        if (degrees == 0f) return this
        val r = Math.toRadians(degrees.toDouble())
        val c = abs(cos(r)).toFloat()
        val s = abs(sin(r)).toFloat()
        val hw = (width * c + height * s) / 2f
        val hh = (width * s + height * c) / 2f
        return Bounds(centerX - hw, centerY - hh, centerX + hw, centerY + hh)
    }

    companion object {
        fun ofSize(x: Float, y: Float, w: Float, h: Float) = Bounds(x, y, x + w, y + h)
    }
}

/** 2D affine transform: x' = a*x + c*y + e, y' = b*x + d*y + f. */
data class Affine(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f,
) {
    fun mapX(x: Float, y: Float) = a * x + c * y + e
    fun mapY(x: Float, y: Float) = b * x + d * y + f

    val scaleX get() = hypot(a, b)
    val scaleY get() = hypot(c, d)
    val rotationDegrees get() = Math.toDegrees(atan2(b, a).toDouble()).toFloat()

    /** How much a stroke width grows under this transform. */
    val meanScale get() = sqrt(abs(a * d - b * c))

    companion object {
        val Identity = Affine()

        fun translate(dx: Float, dy: Float) = Affine(e = dx, f = dy)

        /** Scales about a fixed point, as when dragging a selection handle. */
        fun scaleAbout(sx: Float, sy: Float, px: Float, py: Float) =
            Affine(a = sx, d = sy, e = px - sx * px, f = py - sy * py)

        fun rotateAbout(degrees: Float, px: Float, py: Float): Affine {
            val r = Math.toRadians(degrees.toDouble())
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            return Affine(c, s, -s, c, px - c * px + s * py, py - s * px - c * py)
        }
    }
}
