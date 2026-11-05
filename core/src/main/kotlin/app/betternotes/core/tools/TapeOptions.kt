package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TapePattern
import app.betternotes.core.model.TapeShadow
import kotlin.math.atan2
import kotlin.math.hypot

/** Whether tape is always a straight strip, straightens when the pen is held, or never does and follows the pen. */
enum class TapeLineMode { ALWAYS, HOLD, NEVER }

/** What the tape tool lays down. [widths] are in points, as a strip of tape is a real size on the page. */
data class TapeOptions(
    val widths: List<Int> = listOf(12, 20, 32),
    val selectedWidth: Int = 1,
    val colors: List<Int> = PALETTE,
    val selectedColor: Int = 0,
    /** How see-through the whole strip is, on top of the color's own transparency. */
    val opacity: Float = 1f,
    val pattern: TapePattern = TapePattern.PLAIN,
    /** A tiny shadow, which the panel can change. Distance is in points and the angle is on the page. */
    /** The master switch of the shadow. Off, the sliders keep their values for when it is turned on again. */
    val shadowOn: Boolean = true,
    val shadowDistance: Float = DEFAULT_SHADOW_DISTANCE,
    val shadowAngle: Float = DEFAULT_SHADOW_ANGLE,
    val shadowOpacity: Float = DEFAULT_SHADOW_OPACITY,
    val lineMode: TapeLineMode = TapeLineMode.ALWAYS,
    /** How long the pen must stay still to straighten tape, in the HOLD mode. */
    val holdMs: Int = DEFAULT_HOLD_MS,
) {
    val shadow get() = if (shadowOn) TapeShadow(shadowDistance, shadowAngle, shadowOpacity) else TapeShadow.NONE

    val width get() = widths[selectedWidth]

    /** The width slot in use set to [points], as the pen's thickness slider changes its slot. */
    fun withWidth(points: Int): TapeOptions {
        val next = widths.toMutableList()
        next[selectedWidth] = points.coerceIn(MIN_THICKNESS, MAX_THICKNESS)
        return copy(widths = next)
    }

    /** The slot after the last color is the warning tape: yellow and red stripes, drawn by the painter. */
    val color get() = colors.getOrElse(selectedColor) { WARNING_FILL }

    /** Adds [argb] to the colors as see-through tape, as the other colors are, and selects it. */
    fun withColor(argb: Int): TapeOptions {
        val tape = (argb and 0xFFFFFF) or WASHI_ALPHA
        val at = colors.indexOf(tape)
        return if (at >= 0) copy(selectedColor = at) else copy(colors = colors + tape, selectedColor = colors.size)
    }

    /** A strip torn off between two points on the page. */
    fun item(id: Long, x0: Float, y0: Float, x1: Float, y1: Float): ShapeItem {
        val length = hypot(x1 - x0, y1 - y0)
        val degrees = Math.toDegrees(atan2(y1 - y0, x1 - x0).toDouble()).toFloat()
        val frame = Frame((x0 + x1 - length) / 2f, (y0 + y1 - width) / 2f, length, width.toFloat(), degrees)
        return ShapeItem(id = id, frame = frame, kind = ShapeKind.TAPE, strokeWidth = 0f, fillColor = color, opacity = opacity, pattern = pattern, shadow = shadow)
    }

    companion object {
        const val WASHI_ALPHA = 0xB3000000.toInt()
        const val DEFAULT_SHADOW_DISTANCE = 1.5f
        const val DEFAULT_SHADOW_ANGLE = 45f
        const val DEFAULT_SHADOW_OPACITY = 0.3f
        const val MAX_SHADOW_DISTANCE = 8f
        const val DEFAULT_HOLD_MS = 500
        const val MIN_HOLD_MS = 200
        const val MAX_HOLD_MS = 2000
        const val MIN_THICKNESS = 4
        const val MAX_THICKNESS = 80
        const val MIN_OPACITY = 0.1f

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

/** A strip of tape with a new thickness and/or opacity, keeping its middle, length and angle. Null leaves a value as it is. */
fun ShapeItem.withTape(thickness: Float?, opacity: Float?): ShapeItem {
    val newFrame = if (thickness == null) frame else Frame(frame.centerX - frame.width / 2f, frame.centerY - thickness / 2f, frame.width, thickness, frame.rotation)
    return copy(frame = newFrame, opacity = opacity ?: this.opacity)
}
