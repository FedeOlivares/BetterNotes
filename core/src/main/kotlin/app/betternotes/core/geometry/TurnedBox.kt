package app.betternotes.core.geometry

import kotlin.math.cos
import kotlin.math.sin

/** A box turned about a pivot, as a text box being typed in is when the page is turned. */
object TurnedBox {
    /**
     * Whether the point ([x], [y]) is in the box at [left], [top], [width] by [height] that is turned [turnDegrees] clockwise about
     * ([pivotX], [pivotY]), or within [margin] of its edge on every side. The box, the point and the margin are in the same units.
     */
    fun contains(
        x: Float, y: Float, left: Float, top: Float, width: Float, height: Float,
        turnDegrees: Float, pivotX: Float, pivotY: Float, margin: Float = 0f,
    ): Boolean {
        val radians = Math.toRadians(-turnDegrees.toDouble())
        val dx = x - pivotX
        val dy = y - pivotY
        val px = pivotX + dx * cos(radians).toFloat() - dy * sin(radians).toFloat()
        val py = pivotY + dx * sin(radians).toFloat() + dy * cos(radians).toFloat()
        return px >= left - margin && px <= left + width + margin && py >= top - margin && py <= top + height + margin
    }
}
