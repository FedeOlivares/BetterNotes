package app.betternotes.core.tools

import app.betternotes.core.model.StrokePoints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Tells what a finished pen stroke was meant as, for "scribble to erase" and "circle to lasso". The
 * stroke's points are in points on the page, so the thresholds are in points as well.
 */
object StrokeGestures {
    private const val STEP_PT = 4f
    private const val MIN_SCRIBBLE_TURNS = 4
    private const val SCRIBBLE_TURN_COS = -0.35f
    private const val MIN_SCRIBBLE_LENGTH_RATIO = 3f
    private const val MIN_LOOP_LENGTH_PT = 40f
    private const val MAX_LOOP_GAP_RATIO = 0.22f
    private const val MIN_LOOP_TURN = 1.6f * PI.toFloat()
    private const val TWO_PI = 2f * PI.toFloat()

    /** A zigzag: the pen reverses at least four times in a small area. */
    fun isScribble(points: StrokePoints): Boolean {
        val path = sample(points)
        if (path.size < 10) return false
        var turns = 0
        var length = 0f
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var i = 0
        while (i < path.size) {
            left = minOf(left, path[i])
            right = maxOf(right, path[i])
            top = minOf(top, path[i + 1])
            bottom = maxOf(bottom, path[i + 1])
            i += 2
        }
        i = 2
        while (i < path.size) {
            length += hypot(path[i] - path[i - 2], path[i + 1] - path[i - 1])
            if (i >= 4) {
                val ax = path[i - 2] - path[i - 4]
                val ay = path[i - 1] - path[i - 3]
                val bx = path[i] - path[i - 2]
                val by = path[i + 1] - path[i - 1]
                val norm = hypot(ax, ay) * hypot(bx, by)
                if (norm > 0f && (ax * bx + ay * by) / norm < SCRIBBLE_TURN_COS) turns++
            }
            i += 2
        }
        val diagonal = hypot(right - left, bottom - top)
        return turns >= MIN_SCRIBBLE_TURNS && length >= MIN_SCRIBBLE_LENGTH_RATIO * max(diagonal, STEP_PT)
    }

    /** A circle-ish stroke: it winds about once and ends close to where it began. */
    fun isClosedLoop(points: StrokePoints): Boolean {
        val path = sample(points)
        if (path.size < 12) return false
        var length = 0f
        var turn = 0f
        var i = 2
        while (i < path.size) {
            length += hypot(path[i] - path[i - 2], path[i + 1] - path[i - 1])
            if (i >= 4) {
                val a = atan2(path[i - 1] - path[i - 3], path[i - 2] - path[i - 4])
                val b = atan2(path[i + 1] - path[i - 1], path[i] - path[i - 2])
                var d = b - a
                while (d > PI) d -= TWO_PI
                while (d < -PI) d += TWO_PI
                turn += d
            }
            i += 2
        }
        val gap = hypot(path[path.size - 2] - path[0], path[path.size - 1] - path[1])
        return length >= MIN_LOOP_LENGTH_PT && gap <= MAX_LOOP_GAP_RATIO * length && abs(turn) >= MIN_LOOP_TURN
    }

    /** The stroke as packed x,y pairs, for [selectWithLasso]. */
    fun polygon(points: StrokePoints): FloatArray = sample(points)

    /** Whether (x, y) is within [tolerance] of the stroke's line. */
    fun isNearLine(points: StrokePoints, x: Float, y: Float, tolerance: Float): Boolean {
        for (i in 0 until points.size - 1) {
            if (distanceToSegment(x, y, points.x(i), points.y(i), points.x(i + 1), points.y(i + 1)) <= tolerance) return true
        }
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Points at least [STEP_PT] apart, as x,y pairs, always ending on the last point. */
    private fun sample(points: StrokePoints): FloatArray {
        if (points.size == 0) return FloatArray(0)
        val out = ArrayList<Float>()
        var lastX = points.x(0)
        var lastY = points.y(0)
        out += lastX
        out += lastY
        for (i in 1 until points.size) {
            val x = points.x(i)
            val y = points.y(i)
            if (hypot(x - lastX, y - lastY) >= STEP_PT) {
                out += x
                out += y
                lastX = x
                lastY = y
            }
        }
        val endX = points.x(points.size - 1)
        val endY = points.y(points.size - 1)
        if (lastX != endX || lastY != endY) {
            out += endX
            out += endY
        }
        return out.toFloatArray()
    }
}

/**
 * Follows the pen a little behind it, so a shaky hand draws a steadier line. Level 1 is the pen
 * itself, and each level above it follows more slowly.
 */
class Stabilizer(level: Int) {
    private val follow = 1f / level.coerceAtLeast(1)
    private var x = 0f
    private var y = 0f
    private var started = false

    /** Moves toward the pen and leaves where the line now is in [out], as x then y. */
    fun next(penX: Float, penY: Float, out: FloatArray) {
        if (!started) {
            x = penX
            y = penY
            started = true
        } else {
            x += (penX - x) * follow
            y += (penY - y) * follow
        }
        out[0] = x
        out[1] = y
    }
}
