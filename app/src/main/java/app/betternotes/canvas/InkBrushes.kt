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

    /** One tip per lane, from [from] to [to] in multiples of the brush size; the lane offset is passed in. */
    private fun lanes(from: Float, to: Float, step: Float, build: BrushTip.Builder.(BrushBehavior, Int) -> Unit): Array<BrushTip> {
        val count = Math.round((to - from) / step) + 1
        return Array(count) { i ->
            val offset = from + i * step
            // A constant input v maps to lerp(from, to, v), so (offset + 1) / 2 gives the offset on -1..1.
            val lane = vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -1f, 1f, ConstantNode((offset + 1f) / 2f))
            BrushTip.builder().apply { build(lane, i) }.build()
        }
    }

    private fun tip(build: BrushTip.Builder.() -> Unit) = BrushTip.builder().apply(build).build()

    private val creative = ConcurrentHashMap<BrushKind, BrushFamily>()

    private fun creativeFamily(kind: BrushKind): BrushFamily = creative.getOrPut(kind) {
        when (kind) {
            // Graphite: a faint soft body with a cloud of tiny grains over it.
            BrushKind.PENCIL -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.2f, 0.45f, noise(1, 0.3f)))
                },
                *Array(2) { c ->
                    tip {
                        setCornerRounding(1f)
                        setScaleX(0.04f)
                        setScaleY(0.04f)
                        setParticleGapDistanceScale(0.05f)
                        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_X_IN_MULTIPLES_OF_BRUSH_SIZE, -0.5f, 0.5f, noise(50 + c, 0.004f)))
                        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE, -0.5f, 0.5f, noise(52 + c, 0.004f)))
                        addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.2f, 0.9f, noise(54 + c, 0.01f)))
                    }
                },
            )
            // A layer of paint with darker bristle streaks drawn over it.
            BrushKind.OIL -> brushFamily(
                tip {
                    setCornerRounding(0.3f)
                    setScaleY(1.05f)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.35f, 0.65f, noise(3, 0.2f)))
                },
                *lanes(-0.45f, 0.45f, 0.15f) { lane, i ->
                    setCornerRounding(0.3f)
                    setScaleY(0.1f)
                    addBehavior(lane)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.1f, 1f, noise(30 + i, 0.12f)))
                },
            )
            BrushKind.SPILL -> brushFamily(
                *Array(2) { c ->
                    tip {
                        setCornerRounding(1f)
                        setScaleX(0.4f)
                        setScaleY(0.4f)
                        setParticleGapDistanceScale(0.2f)
                        addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.2f, 1.7f, noise(5 + c, 0.05f)))
                        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_X_IN_MULTIPLES_OF_BRUSH_SIZE, -1.5f, 1.5f, noise(6 + c, 0.04f)))
                        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE, -1.5f, 1.5f, noise(7 + c, 0.04f)))
                        addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.4f, 1f, noise(8 + c, 0.07f)))
                    }
                },
            )
            // Wet washes: big translucent blobs that overlap into darker patches.
            BrushKind.WATERCOLOR -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    setScaleX(0.9f)
                    setScaleY(0.9f)
                    setParticleGapDistanceScale(0.25f)
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.7f, 1.25f, noise(9, 0.15f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_X_IN_MULTIPLES_OF_BRUSH_SIZE, -0.25f, 0.25f, noise(60, 0.1f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE, -0.25f, 0.25f, noise(61, 0.1f)))
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.12f, 0.35f, noise(10, 0.2f)))
                },
            )
            // Sparse thin hatch marks, like shading lines in a comic.
            BrushKind.COMICS -> brushFamily(
                tip {
                    setScaleX(0.03f)
                    setScaleY(1.1f)
                    setCornerRounding(0.5f)
                    setParticleGapDistanceScale(0.35f)
                    addBehavior(aimedAtDirection())
                    addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, -0.15f, 0.15f, noise(20, 0.05f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.6f, 1.2f, noise(21, 0.08f)))
                },
            )
            // Beaded lines running exactly along the stroke, each a different weight.
            BrushKind.SCRATCHES -> brushFamily(
                *lanes(-0.9f, 0.9f, 0.3f) { lane, i ->
                    setCornerRounding(1f)
                    setScaleX(0.07f)
                    setScaleY(0.04f + 0.03f * ((i * 5) % 4))
                    setParticleGapDistanceScale(0.5f)
                    addBehavior(lane)
                    addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.6f, 1f, noise(80 + i, 0.2f)))
                },
            )
            // Parallel dotted lines of different weight.
            BrushKind.STRIPES -> brushFamily(
                *lanes(-0.5f, 0.5f, 0.2f) { lane, i ->
                    setScaleY(0.05f + 0.02f * (i % 3))
                    setScaleX(0.1f)
                    setCornerRounding(0.5f)
                    setParticleGapDistanceScale(0.4f)
                    addBehavior(lane)
                },
            )
            // A speckle cloud: tiny dots thrown wide of the line, two coats for density.
            BrushKind.DOTS -> brushFamily(
                *Array(2) { c ->
                    tip {
                        setCornerRounding(1f)
                        setScaleX(0.06f)
                        setScaleY(0.06f)
                        setParticleGapDistanceScale(0.12f)
                        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_X_IN_MULTIPLES_OF_BRUSH_SIZE, -1.5f, 1.5f, noise(40 + c, 0.01f)))
                        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE, -1.5f, 1.5f, noise(42 + c, 0.01f)))
                        addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.3f, 1.8f, noise(44 + c, 0.02f)))
                    }
                },
            )
            BrushKind.SINGLE_DOTS -> brushFamily(
                tip {
                    setCornerRounding(1f)
                    setScaleX(0.22f)
                    setScaleY(0.22f)
                    setParticleGapDistanceScale(1.3f)
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.7f, 1.2f, noise(13, 0.1f)))
                },
            )
            // Small leaves: pointed ovals at random angles.
            BrushKind.LEAVES -> brushFamily(
                tip {
                    setCornerRounding(0.5f)
                    setPinch(0.85f)
                    setScaleX(0.5f)
                    setScaleY(0.28f)
                    setParticleGapDistanceScale(0.9f)
                    addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, 0f, TWO_PI, noise(14, 0.03f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.7f, 1.3f, noise(15, 0.06f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_X_IN_MULTIPLES_OF_BRUSH_SIZE, -1f, 1f, noise(16, 0.05f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_Y_IN_MULTIPLES_OF_BRUSH_SIZE, -1f, 1f, noise(22, 0.05f)))
                },
            )
            BrushKind.CROSS -> brushFamily(
                tip {
                    setCornerRounding(0f)
                    setScaleX(0.9f)
                    setScaleY(0.06f)
                    setParticleGapDistanceScale(0.08f)
                    addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, 0f, TWO_PI, noise(17, 0.02f)))
                    addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -0.8f, 0.8f, noise(19, 0.03f)))
                    addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.5f, 1.4f, noise(18, 0.04f)))
                },
            )
            // Layers from a wide faint halo down to the core, so the colour fades into the glow.
            BrushKind.GLOW -> brushFamily(
                *Array(6) { i ->
                    tip {
                        setCornerRounding(1f)
                        val scale = 3f - 0.4f * i
                        setScaleX(scale)
                        setScaleY(scale)
                        addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0f, 0.16f, ConstantNode(1f)))
                    }
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
