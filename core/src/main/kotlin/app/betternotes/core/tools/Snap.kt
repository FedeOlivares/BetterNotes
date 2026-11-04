package app.betternotes.core.tools

import app.betternotes.core.geometry.Bounds
import kotlin.math.abs

/** How far to nudge something being moved, and where the lines it lined up with are, if anywhere. */
class SnapResult(val dx: Float, val dy: Float, val verticalLine: Float?, val horizontalLine: Float?)

/**
 * Lines up a moving box with other boxes and with the page. Its left, middle and right are each tried
 * against the same three of everything else, and likewise its top, middle and bottom, and the closest
 * pair within [reach] wins on each axis. [pageWidth] and [pageHeight] are null on an infinite canvas.
 */
fun snapToAlign(moving: Bounds, others: List<Bounds>, pageWidth: Float?, pageHeight: Float?, reach: Float): SnapResult {
    val xs = ArrayList<Float>(others.size * LINES_PER_BOX + LINES_PER_BOX)
    val ys = ArrayList<Float>(others.size * LINES_PER_BOX + LINES_PER_BOX)
    for (other in others) {
        xs += other.left
        xs += other.centerX
        xs += other.right
        ys += other.top
        ys += other.centerY
        ys += other.bottom
    }
    if (pageWidth != null) {
        xs += 0f
        xs += pageWidth / 2f
        xs += pageWidth
    }
    if (pageHeight != null) {
        ys += 0f
        ys += pageHeight / 2f
        ys += pageHeight
    }
    val snapX = closest(floatArrayOf(moving.left, moving.centerX, moving.right), xs, reach)
    val snapY = closest(floatArrayOf(moving.top, moving.centerY, moving.bottom), ys, reach)
    return SnapResult(snapX?.first ?: 0f, snapY?.first ?: 0f, snapX?.second, snapY?.second)
}

/** The smallest move that lines one of [own] up with one of [lines], with the line it reaches. */
private fun closest(own: FloatArray, lines: List<Float>, reach: Float): Pair<Float, Float>? {
    var best: Pair<Float, Float>? = null
    var bestDistance = reach
    for (mine in own) {
        for (line in lines) {
            val distance = abs(line - mine)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = (line - mine) to line
            }
        }
    }
    return best
}

private const val LINES_PER_BOX = 3
