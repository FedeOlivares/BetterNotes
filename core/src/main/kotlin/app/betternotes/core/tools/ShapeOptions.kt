package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * What the shape tool is set to. [recentKinds] are the three shapes in the toolbar, and [kind] is the
 * one in use. [widths] are the three border widths, in the same unit as a pen's thickness, and
 * [colors] the three border colors. A [fillColor] with zero alpha means no fill.
 */
data class ShapeOptions(
    val kind: ShapeKind = ShapeKind.RECTANGLE,
    val recentKinds: List<ShapeKind> = listOf(ShapeKind.ELLIPSE, ShapeKind.TRIANGLE, ShapeKind.RECTANGLE),
    val widths: List<Int> = listOf(2, 6, 12),
    val selectedWidth: Int = 1,
    val colors: List<Int> = listOf(0xFF000000.toInt(), 0xFFD10101.toInt(), 0xFF04CEFF.toInt()),
    val selectedColor: Int = 1,
    val fillColor: Int = 0,
    val line: LineKind = LineKind.SOLID,
    val dashLength: Float = DEFAULT_DASH,
    val cornerRadius: Int = 1,
) {
    init {
        require(recentKinds.size == SLOTS && widths.size == SLOTS && colors.size == SLOTS) { "Three of each" }
        require(selectedWidth in widths.indices) { "Width slot $selectedWidth" }
        require(selectedColor in colors.indices) { "Color slot $selectedColor" }
    }

    val width get() = widths[selectedWidth]

    val color get() = colors[selectedColor]

    /** Uses a shape, which puts it in the toolbar in place of the oldest one if it is not there yet. */
    fun withKind(next: ShapeKind) = copy(
        kind = next,
        recentKinds = if (next in recentKinds) recentKinds else recentKinds.drop(1) + next,
    )

    fun withWidth(value: Int): ShapeOptions {
        val next = widths.toMutableList()
        next[selectedWidth] = value.coerceIn(MIN_WIDTH, MAX_WIDTH)
        return copy(widths = next)
    }

    fun withColor(value: Int): ShapeOptions {
        val next = colors.toMutableList()
        next[selectedColor] = value
        return copy(colors = next)
    }

    /** The shape dragged from one point to another, in page coordinates. */
    fun item(id: Long, x0: Float, y0: Float, x1: Float, y1: Float) = ShapeItem(
        id = id,
        frame = shapeFrame(kind, x0, y0, x1, y1),
        kind = kind,
        strokeColor = color,
        strokeWidth = width * InkPreset.POINTS_PER_THICKNESS_UNIT,
        line = LineStyle(kind = line, dashLength = dashLength, gapLength = dashLength * GAP_OF_DASH),
        fillColor = fillColor,
        cornerRadius = cornerRadius * POINTS_PER_RADIUS,
    )

    companion object {
        const val SLOTS = 3
        const val MIN_WIDTH = 1
        const val MAX_WIDTH = 100
        const val MAX_CORNER_RADIUS = 10
        const val MIN_DASH = 1f
        const val MAX_DASH = 30f
        private const val DEFAULT_DASH = 6f
        private const val GAP_OF_DASH = 0.7f
        private const val POINTS_PER_RADIUS = 2f
    }
}

/**
 * The frame for a shape dragged between two points. A line or arrow runs from one to the other, so it
 * is turned to their angle with no height. Anything else fills the box they span.
 */
fun shapeFrame(kind: ShapeKind, x0: Float, y0: Float, x1: Float, y1: Float): Frame {
    if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) {
        val length = hypot(x1 - x0, y1 - y0)
        val degrees = Math.toDegrees(atan2(y1 - y0, x1 - x0).toDouble()).toFloat()
        return Frame((x0 + x1 - length) / 2f, (y0 + y1) / 2f, length, 0f, degrees)
    }
    return Frame(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1) - minOf(x0, x1), maxOf(y0, y1) - minOf(y0, y1))
}
