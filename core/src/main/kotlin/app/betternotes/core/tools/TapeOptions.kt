package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import kotlin.math.atan2
import kotlin.math.hypot

/** What the tape tool lays down. [widths] are in points, as a strip of tape is a real size on the page. */
data class TapeOptions(
    val widths: List<Int> = listOf(12, 20, 32),
    val selectedWidth: Int = 1,
    val colors: List<Int> = PALETTE,
    val selectedColor: Int = 0,
) {
    val width get() = widths[selectedWidth]

    /** The slot after the last color is the warning tape: yellow and red stripes, drawn by the painter. */
    val color get() = colors.getOrElse(selectedColor) { WARNING_FILL }

    /** A strip torn off between two points on the page. */
    fun item(id: Long, x0: Float, y0: Float, x1: Float, y1: Float): ShapeItem {
        val length = hypot(x1 - x0, y1 - y0)
        val degrees = Math.toDegrees(atan2(y1 - y0, x1 - x0).toDouble()).toFloat()
        val frame = Frame((x0 + x1 - length) / 2f, (y0 + y1 - width) / 2f, length, width.toFloat(), degrees)
        return ShapeItem(id = id, frame = frame, kind = ShapeKind.TAPE, strokeWidth = 0f, fillColor = color)
    }

    companion object {
        /** Marks a strip of tape that is painted as warning tape, whatever its other colors. */
        const val WARNING_FILL = 0xFFFFC400.toInt()
        const val WARNING_STRIPE = 0xFF1B1B1B.toInt()

        /** See-through, as washi tape is. */
        val PALETTE: List<Int> = listOf(
            0xB3FBDD8C.toInt(),
            0xB3F6A9A0.toInt(),
            0xB3A9E5B5.toInt(),
            0xB3A8D8FF.toInt(),
            0xB3CFC0F5.toInt(),
        )
    }
}
