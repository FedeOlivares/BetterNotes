package app.betternotes.core.tools

import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.PenType
import app.betternotes.core.model.TipShape

/**
 * What one of the pen, pencil and highlighter is set to: the row of [colors] and [widths] in the
 * toolbar, which slot of each is in use, and the rest of the expanded panel.
 *
 * [widths] are in tenths of a millimeter, the unit of the panel's thickness value. [colors] holds the
 * five defaults, plus one custom color once it has been added. [palette] is the longer list of swatches
 * in the panel, from which a slot can be changed.
 */
data class InkPreset(
    val tool: InkTool,
    val colors: List<Int>,
    val widths: List<Int>,
    val palette: List<Int>,
    val selectedColor: Int = 0,
    val selectedWidth: Int = 0,
    val penType: PenType = PenType.BALL,
    val tip: TipShape = TipShape.ROUND,
    val opacity: Float = 1f,
    val line: LineKind = LineKind.SOLID,
    val holdToLine: Boolean = true,
    val holdDelayMs: Int = DEFAULT_HOLD_MS,
    val scribbleToErase: Boolean = false,
    val circleToLasso: Boolean = false,
    val stabilization: Int = 1,
    /** For the pencil slot, which creative brush it draws with. */
    val brush: BrushKind = BrushKind.PENCIL,
) {
    init {
        require(colors.size == DEFAULT_COLOR_SLOTS || colors.size == DEFAULT_COLOR_SLOTS + 1) { "${colors.size} colors" }
        require(widths.size == WIDTH_SLOTS) { "${widths.size} widths" }
        require(selectedColor in colors.indices) { "Color slot $selectedColor" }
        require(selectedWidth in widths.indices) { "Width slot $selectedWidth" }
    }

    val color get() = colors[selectedColor]

    val thickness get() = widths[selectedWidth]

    /** How long the pen must stay still to turn a stroke into a straight line, or 0 if it never does. */
    val holdToLineMs get() = if (holdToLine) holdDelayMs else 0

    fun style() = InkStyle(
        tool = tool,
        penType = penType,
        tip = tip,
        color = color,
        width = thickness * POINTS_PER_THICKNESS_UNIT,
        opacity = opacity,
        line = LineStyle(kind = line),
        brush = brush,
    )

    /** Fine-tunes the width slot in use. */
    fun withThickness(value: Int): InkPreset {
        val next = widths.toMutableList()
        next[selectedWidth] = value.coerceIn(MIN_THICKNESS, MAX_THICKNESS)
        return copy(widths = next)
    }

    /** Selects [color] if a slot already has it. Otherwise puts it in the slot in use. */
    fun pickColor(color: Int): InkPreset {
        val slot = colors.indexOf(color)
        return if (slot >= 0) copy(selectedColor = slot) else withSlotColor(color)
    }

    fun withSlotColor(color: Int): InkPreset {
        val next = colors.toMutableList()
        next[selectedColor] = color
        return copy(colors = next)
    }

    /** Adds the one custom slot, or replaces it if there already is one, and selects it. */
    fun withCustomColor(color: Int): InkPreset {
        val next = colors.take(DEFAULT_COLOR_SLOTS) + color
        return copy(colors = next, selectedColor = DEFAULT_COLOR_SLOTS)
    }

    fun withPaletteColor(color: Int) = if (color in palette) this else copy(palette = palette + color)

    companion object {
        const val DEFAULT_COLOR_SLOTS = 5
        const val WIDTH_SLOTS = 3
        const val MIN_THICKNESS = 1
        const val MAX_THICKNESS = 100
        const val MIN_HOLD_MS = 200
        const val MAX_HOLD_MS = 2000
        const val DEFAULT_HOLD_MS = 500
        const val MAX_STABILIZATION = 5

        /** One tenth of a millimeter, in points. */
        const val POINTS_PER_THICKNESS_UNIT = 0.1f * 72f / 25.4f

        private const val BLACK = 0xFF000000.toInt()
        private const val RED = 0xFFD10101.toInt()
        private const val NAVY = 0xFF0107A3.toInt()
        private const val CYAN = 0xFF04CEFF.toInt()
        private const val GREEN = 0xFF03D102.toInt()
        private const val DARK_GREEN = 0xFF02A200.toInt()
        private const val AMBER = 0xFFFFC803.toInt()
        private const val BROWN = 0xFF7A4A21.toInt()
        private const val YELLOW = 0xFFFFEB3B.toInt()
        private const val PINK = 0xFFFF80AB.toInt()
        private const val ORANGE = 0xFFFFAB40.toInt()
        private const val HIGHLIGHTER_OPACITY = 0.4f

        private val INK_COLORS = listOf(BLACK, RED, NAVY, AMBER, GREEN)
        private val INK_PALETTE = listOf(BLACK, RED, NAVY, CYAN, GREEN, DARK_GREEN, AMBER, BROWN)

        fun defaults(tool: InkTool) = when (tool) {
            InkTool.PEN, InkTool.PENCIL -> InkPreset(tool, INK_COLORS, listOf(4, 8, 14), INK_PALETTE)
            InkTool.HIGHLIGHTER -> InkPreset(
                tool,
                colors = listOf(YELLOW, GREEN, CYAN, PINK, ORANGE),
                widths = listOf(30, 50, 80),
                palette = listOf(YELLOW, GREEN, CYAN, PINK, ORANGE, AMBER),
                opacity = HIGHLIGHTER_OPACITY,
            )
        }
    }
}
