package app.betternotes.canvas

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushTip
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.StockBrushes.DashedLineVersion
import androidx.ink.brush.StockBrushes.HighlighterVersion
import androidx.ink.brush.StockBrushes.MarkerVersion
import androidx.ink.brush.StockBrushes.PressurePenVersion
import androidx.ink.brush.behavior.ConstantNode
import androidx.ink.brush.behavior.NoiseNode
import androidx.ink.brush.behavior.ProgressDomain
import androidx.ink.brush.behavior.SourceNode
import androidx.ink.brush.behavior.TargetNode
import androidx.ink.brush.behavior.ValueNode
import app.betternotes.core.model.BrushKind
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

    private const val TWO_PI = 6.2831855f
    private const val CALLIGRAPHY_NIB_RATIO = 0.25f
    private const val CALLIGRAPHY_NIB_DEGREES = 45f

    private val pressurePen by lazy { StockBrushes.pressurePen(PressurePenVersion.V1) }
    private val marker by lazy { StockBrushes.marker(MarkerVersion.V1) }
    private val highlighter by lazy { StockBrushes.highlighter(version = HighlighterVersion.V1) }
    /** The stock highlighter, but ending in a half circle. Its tip has square corners rounded off. */
    private val roundHighlighter by lazy {
        val stock = highlighter.coats.first()
        val tip = stock.tip.toBuilder().setCornerRounding(1f).build()
        BrushFamily.builder().setCoat(stock.copy(tip = tip)).setInputModel(highlighter.inputModel).build()
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

    // ---- The creative brushes ----
    //
    // None of these uses a texture. Scattered brushes are particles: a small tip stamped along the line at
    // a gap, with noise moving and resizing each stamp. The rest vary size and opacity with noise.

    private fun noise(seed: Int, period: Float) = NoiseNode(seed, ProgressDomain.DISTANCE_IN_MULTIPLES_OF_BRUSH_SIZE, period)

    private fun vary(target: TargetNode.Target, from: Float, to: Float, input: ValueNode) =
        BrushBehavior(TargetNode(target, from, to, input))

    private fun aimedAtDirection() = vary(
        TargetNode.Target.ROTATION_OFFSET_IN_RADIANS,
        0f,
        TWO_PI,
        SourceNode(SourceNode.Source.DIRECTION_IN_RADIANS, 0f, TWO_PI),
    )

    private fun brushFamily(vararg tips: BrushTip) = BrushFamily.builder()
        .setCoats(tips.map { BrushCoat(it) })
        .setInputModel(BrushFamily.InputModel.SlidingWindowModel())
        .build()

    private fun tip(build: BrushTip.Builder.() -> Unit) = BrushTip.builder().apply(build).build()

    private val creative = ConcurrentHashMap<BrushKind, BrushFamily>()

    private fun creativeFamily(kind: BrushKind): BrushFamily = creative.getOrPut(kind) {
        when (kind) {
            BrushKind.PENCIL -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.55f, 0.95f, noise(1, 0.35f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.8f, 1.05f, noise(2, 0.5f)))
                },
            )
            BrushKind.OIL -> brushFamily(
                tip {
                    setCornerRounding(0.3f)
                    setScaleY(0.6f)
                    setRotationDegrees(20f)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.7f, 1f, noise(3, 0.12f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -0.08f, 0.08f, noise(4, 0.2f)))
                },
            )
            BrushKind.SPILL -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    setScaleX(0.3f)
                    setScaleY(0.3f)
                    setParticleGapDistanceScale(0.5f)
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.3f, 1.6f, noise(5, 0.05f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_X_IN_MULTIPLES_OF_BRUSH_SIZE, -1.2f, 1.2f, noise(6, 0.05f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE, -1.2f, 1.2f, noise(7, 0.05f)))
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.4f, 1f, noise(8, 0.07f)))
                },
            )
            BrushKind.WATERCOLOR -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.85f, 1.15f, noise(9, 0.6f)))
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.6f, 1f, noise(10, 0.3f)))
                },
            )
            BrushKind.COMICS -> brushFamily(
                tip {
                    setScaleX(0.1f)
                    setCornerRounding(0f)
                    setParticleGapDistanceScale(0.12f)
                    addBehavior(aimedAtDirection())
                },
            )
            BrushKind.SCRATCHES -> brushFamily(
                tip {
                    setScaleY(0.06f)
                    setCornerRounding(0.5f)
                    setParticleGapDistanceScale(0.35f)
                    addBehavior(aimedAtDirection())
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -0.7f, 0.7f, noise(11, 0.06f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.3f, 1.2f, noise(12, 0.08f)))
                },
            )
            BrushKind.DOTS -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    setScaleX(0.22f)
                    setScaleY(0.22f)
                    setParticleGapDistanceScale(1.3f)
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.7f, 1.2f, noise(13, 0.1f)))
                },
            )
            BrushKind.LEAVES -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    setPinch(0.7f)
                    setScaleX(0.55f)
                    setScaleY(0.3f)
                    setParticleGapDistanceScale(0.8f)
                    addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, 0f, TWO_PI, noise(14, 0.04f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.6f, 1.4f, noise(15, 0.06f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -0.5f, 0.5f, noise(16, 0.07f)))
                },
            )
            BrushKind.CROSS -> brushFamily(
                tip {
                    setCornerRounding(0f)
                    setScaleX(0.12f)
                    setScaleY(0.12f)
                    setRotationDegrees(45f)
                    setParticleGapDistanceScale(0.9f)
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -0.6f, 0.6f, noise(17, 0.05f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.6f, 1.4f, noise(18, 0.06f)))
                },
            )
            // A wide, faint stroke under a normal one.
            BrushKind.GLOW -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    setScaleX(2.6f)
                    setScaleY(2.6f)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.3f, 0.3f, ConstantNode(1f)))
                },
                tip { setCornerRounding(1f) },
            )
        }
    }

    private val brushes = ConcurrentHashMap<InkStyle, Brush>()

    fun brush(style: InkStyle): Brush = brushes.getOrPut(style) {
        Brush.createWithColorIntArgb(family(style), colorOf(style), style.width, EPSILON_PT)
    }

    private fun family(style: InkStyle): BrushFamily = when {
        style.line.kind != LineKind.SOLID -> dashed
        style.tool == InkTool.HIGHLIGHTER -> if (style.tip == TipShape.SQUARE) highlighter else roundHighlighter
        style.tip == TipShape.SQUARE && style.tool != InkTool.PENCIL -> squareNib
        style.tool == InkTool.PENCIL -> creativeFamily(style.brush)
        else -> when (style.penType) {
            PenType.NATURAL -> pressurePen
            PenType.BALL -> marker
            PenType.CALLIGRAPHY -> calligraphyNib
        }
    }

    /**
     * Without an input model the mesh joins the pen's samples with straight segments, which shows as
     * corners when zoomed in. The sliding window smooths and upsamples them, as the stock brushes do.
     */
    private fun familyOf(tip: BrushTip) = BrushFamily.builder()
        .setCoat(BrushCoat(tip))
        .setInputModel(BrushFamily.InputModel.SlidingWindowModel())
        .build()

    private fun colorOf(style: InkStyle): Int {
        val alpha = ((style.color ushr 24) * style.opacity + 0.5f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (style.color and 0xFFFFFF)
    }
}
