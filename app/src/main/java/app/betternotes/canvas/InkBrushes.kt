package app.betternotes.canvas

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushTip
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.StockBrushes.DashedLineVersion
import androidx.ink.brush.StockBrushes.HighlighterVersion
import androidx.ink.brush.StockBrushes.MarkerVersion
import androidx.ink.brush.StockBrushes.PressurePenVersion
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.PenType
import app.betternotes.core.model.TipShape
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns the style saved with a stroke into the brush that draws it. Stock brushes are pinned to a
 * version, because saved ink is meshed again on load and must not change when the library updates.
 *
 * Safe from any thread, since pages are meshed in the background.
 */
@OptIn(ExperimentalInkCustomBrushApi::class)
internal object InkBrushes {
    /**
     * The smallest distance the mesh resolves, in points: about 0.2 px at 1500% on the Pad 7. At 0.01 pt
     * curves showed flat segments at that zoom. This costs about 30% more meshing time than 0.01 pt.
     */
    private const val EPSILON_PT = 0.003f

    private const val CALLIGRAPHY_NIB_RATIO = 0.25f
    private const val CALLIGRAPHY_NIB_DEGREES = 45f

    private val pressurePen by lazy { StockBrushes.pressurePen(PressurePenVersion.V1) }
    private val marker by lazy { StockBrushes.marker(MarkerVersion.V1) }
    private val highlighter by lazy { StockBrushes.highlighter(version = HighlighterVersion.V1) }
    /** The stock highlighter, but ending in a half circle. Its tip has square corners rounded off. */
    private val roundHighlighter by lazy {
        val stock = highlighter.coats.first()
        val tip = stock.tip.toBuilder().setCornerRounding(1f).build()
        BrushFamily.builder().setCoat(stock.copy(tip = tip)).build()
    }
    private val dashed by lazy { StockBrushes.dashedLine(DashedLineVersion.V1) }
    private val squareNib by lazy { familyOf(BrushTip.builder().setCornerRounding(0f).build()) }
    private val calligraphyNib by lazy {
        familyOf(
            BrushTip.builder()
                .setScaleY(CALLIGRAPHY_NIB_RATIO)
                .setRotationDegrees(CALLIGRAPHY_NIB_DEGREES)
                .setCornerRounding(0f)
                .build(),
        )
    }

    private val brushes = ConcurrentHashMap<InkStyle, Brush>()

    fun brush(style: InkStyle): Brush = brushes.getOrPut(style) {
        Brush.createWithColorIntArgb(family(style), colorOf(style), style.width, EPSILON_PT)
    }

    private fun family(style: InkStyle): BrushFamily = when {
        style.line.kind != LineKind.SOLID -> dashed
        style.tool == InkTool.HIGHLIGHTER -> if (style.tip == TipShape.SQUARE) highlighter else roundHighlighter
        style.tip == TipShape.SQUARE -> squareNib
        style.tool == InkTool.PENCIL -> marker
        else -> when (style.penType) {
            PenType.NATURAL -> pressurePen
            PenType.BALL -> marker
            PenType.CALLIGRAPHY -> calligraphyNib
        }
    }

    private fun familyOf(tip: BrushTip) = BrushFamily.builder().setCoat(BrushCoat(tip)).build()

    private fun colorOf(style: InkStyle): Int {
        val alpha = ((style.color ushr 24) * style.opacity + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (style.color and 0xFFFFFF)
    }
}
