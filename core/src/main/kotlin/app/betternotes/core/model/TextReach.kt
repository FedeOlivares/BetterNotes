package app.betternotes.core.model

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Everything the text of a box can paint, which is more than the box: the page does not clip text to its frame, so a long text runs
 * below it, a bottom-aligned one above it, and the slant of an italic and the swing of a script letter reach past its sides.
 * A tile only draws the items whose paint box touches it, so this box decides whether such text shows or is cut at a tile's edge.
 */
object TextReach {
    /**
     * How tall [text] lays out at [width] points, in points. Core has no fonts, so the app installs this; without it a generous guess
     * is used, which only costs the tiles some extra drawing. Called from any thread.
     */
    @Volatile
    var layoutHeight: ((text: String, size: Float, style: TextStyle, spans: List<TextSpan>, width: Float) -> Float)? = null

    /** Sideways reach of a glyph past its advance, as a share of the largest font size: slanted and swashed letters. */
    private const val SIDE_OVERHANG = 0.5f

    /** Reach of a glyph above and below its line, as a share of the largest font size. Also covers underlines. */
    private const val LINE_OVERHANG = 0.35f

    /** The tallest a line can be as a share of the font size, for the guess. */
    private const val GUESS_LINE = 1.6f

    /**
     * The box around the text of [text], in the box's own coordinates (the frame's top-left corner is the origin, nothing is turned),
     * for a frame of [width] by [height]. Written as left, top, right, bottom.
     */
    fun localBox(text: String, size: Float, style: TextStyle, spans: List<TextSpan>, width: Float, height: Float): FloatArray {
        if (text.isEmpty() || width <= 0f) return floatArrayOf(0f, 0f, width.coerceAtLeast(0f), height.coerceAtLeast(0f))
        val biggest = spans.maxOfOrNull { it.look.size ?: size }?.coerceAtLeast(size) ?: size
        val laidOut = layoutHeight?.invoke(text, size, style, spans, width) ?: guessHeight(text, biggest, width)
        val top = when (style.verticalAlign) {
            VerticalAlign.TOP -> 0f
            VerticalAlign.MIDDLE -> (height - laidOut) / 2f
            VerticalAlign.BOTTOM -> height - laidOut
        }
        val side = biggest * SIDE_OVERHANG
        val line = biggest * LINE_OVERHANG
        return floatArrayOf(-side, minOf(0f, top) - line, width + side, maxOf(height, top + laidOut) + line)
    }

    /** The box around [local], given in a frame's own coordinates, once the frame is placed and turned. */
    fun inDocument(frame: Frame, local: FloatArray): Bounds {
        val radians = Math.toRadians(frame.rotation.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (k in 0 until 4) {
            val dx = (if (k % 2 == 0) local[0] else local[2]) - frame.width / 2f
            val dy = (if (k < 2) local[1] else local[3]) - frame.height / 2f
            val x = frame.centerX + dx * c - dy * s
            val y = frame.centerY + dx * s + dy * c
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        return Bounds(left, top, right, bottom)
    }

    /** Never less than the real height: a glyph is at most one em wide and a line at most [GUESS_LINE] ems tall. */
    private fun guessHeight(text: String, size: Float, width: Float): Float {
        val perLine = floor(width / size).coerceAtLeast(1f)
        var lines = 0
        for (paragraph in text.split('\n')) lines += ceil(paragraph.length / perLine).toInt().coerceAtLeast(1)
        return lines * size * GUESS_LINE
    }
}
