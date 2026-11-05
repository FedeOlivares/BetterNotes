package app.betternotes.canvas

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import androidx.ink.brush.BrushTip
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.StockBrushes.DashedLineVersion
import androidx.ink.brush.StockBrushes.HighlighterVersion
import androidx.ink.brush.StockBrushes.MarkerVersion
import androidx.ink.brush.StockBrushes.PressurePenVersion
import androidx.ink.brush.behavior.ConstantNode
import androidx.ink.brush.behavior.EasingFunction
import androidx.ink.brush.behavior.NoiseNode
import androidx.ink.brush.behavior.OutOfRange
import androidx.ink.brush.behavior.ProgressDomain
import androidx.ink.brush.behavior.ResponseNode
import androidx.ink.brush.behavior.SourceNode
import androidx.ink.brush.behavior.TargetNode
import androidx.ink.brush.behavior.ValueNode
import androidx.ink.geometry.ImmutableVec
import androidx.ink.strokes.Stroke
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
@OptIn(ExperimentalInkCustomBrushApi::class, androidx.ink.brush.ExperimentalInkAnimationApi::class)
internal object InkBrushes {
    /**
     * The smallest distance the mesh resolves, in points: about 0.2 px at 1500% on the Pad 7. At 0.01 pt
     * curves showed flat segments at that zoom. This costs about 30% more meshing time than 0.01 pt.
     */
    private const val EPSILON_PT = 0.003f

    private const val TWO_PI = 6.2831855f
    private const val STAMP_LOOP_MS = 1000L
    private const val OIL_RUN = 1.8f
    private const val OIL_RUN_STAGGER = 0.2f
    private const val CALLIGRAPHY_DEPTH = 0.12f
    private const val CALLIGRAPHY_MIN_WIDTH = 0.1f
    private const val CALLIGRAPHY_POINT = 0.04f
    private const val CALLIGRAPHY_TAPER = 1.6f

    private val pressurePen by lazy { StockBrushes.pressurePen(PressurePenVersion.V1) }
    /** The pressure pen, with its width and opacity wavering by a few percent along the line, as ink from a real pen does. */
    private val naturalPen by lazy {
        val stock = pressurePen.coats.first()
        val tip = stock.tip.toBuilder()
            .addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.95f, 1f, noise(110, 0.35f)))
            .addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.95f, 1f, noise(111, 0.5f)))
            .build()
        BrushFamily.builder().setCoat(stock.copy(tip = tip)).setInputModel(pressurePen.inputModel).build()
    }
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
    /**
     * A broad nib held level: its width across the line follows the line's direction, widest going straight up or
     * down and thinnest going across, in between in proportion (half way at 45 degrees).
     */
    private val calligraphyNib by lazy {
        BrushFamily.builder()
            .setCoat(
                BrushCoat(
                    BrushTip.builder()
                        .setScaleX(CALLIGRAPHY_DEPTH)
                        .setScaleY(1f)
                        .setCornerRounding(0.3f)
                        .addBehavior(aimedAtDirection())
                        .addBehavior(BrushBehavior(TargetNode(TargetNode.Target.HEIGHT_MULTIPLIER, CALLIGRAPHY_MIN_WIDTH, 1f, ResponseNode(calligraphyWidth, SourceNode(SourceNode.Source.DIRECTION_IN_RADIANS, 0f, TWO_PI)))))
                        // The stroke starts and ends in a point: it grows over its first line widths and shrinks over its last.
                        .addBehavior(BrushBehavior(TargetNode(TargetNode.Target.HEIGHT_MULTIPLIER, CALLIGRAPHY_POINT, 1f, SourceNode(SourceNode.Source.DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE, 0f, CALLIGRAPHY_TAPER))))
                        .addBehavior(BrushBehavior(TargetNode(TargetNode.Target.HEIGHT_MULTIPLIER, CALLIGRAPHY_POINT, 1f, SourceNode(SourceNode.Source.DISTANCE_REMAINING_IN_MULTIPLES_OF_BRUSH_SIZE, 0f, CALLIGRAPHY_TAPER))))
                        .build(),
                ),
            )
            .setInputModel(BrushFamily.InputModel.SlidingWindowModel())
            .build()
    }

    /** From the direction of travel, over a whole turn, to how wide the nib is: thin at 0 and 180 degrees, full at 90 and 270. */
    private val calligraphyWidth = EasingFunction.Linear(
        listOf(ImmutableVec(0f, 0f), ImmutableVec(0.25f, 1f), ImmutableVec(0.5f, 0f), ImmutableVec(0.75f, 1f), ImmutableVec(1f, 0f)),
    )

    // ---- The creative brushes ----
    //
    // None of these uses a texture. Scattered brushes are particles: a small tip stamped along the line at
    // a gap, with noise moving and resizing each stamp. The rest vary size and opacity with noise.

    private fun noise(seed: Int, period: Float) = NoiseNode(seed, ProgressDomain.DISTANCE_IN_MULTIPLES_OF_BRUSH_SIZE, period)

    private fun vary(target: TargetNode.Target, from: Float, to: Float, input: ValueNode) =
        BrushBehavior(TargetNode(target, from, to, input))

    /** Near 0 for most of its input and rising to 1 at the top, so only now and then does anything happen. */
    private val seldom = EasingFunction.Linear(
        listOf(ImmutableVec(0f, 0f), ImmutableVec(0.72f, 0f), ImmutableVec(1f, 1f)),
    )

    private val spindle = EasingFunction.Linear(
        listOf(ImmutableVec(0f, 0f), ImmutableVec(0.3f, 1f), ImmutableVec(0.7f, 1f), ImmutableVec(1f, 0f)),
    )

    private fun oilBleed() = faint(
        tip {
            setCornerRounding(0.6f)
            setScaleY(1.05f)
            addBehavior(aimedAtDirection())
        },
        0.22f,
    )

    /** The run of bristle [i]: between 1.6 and 1.8 line widths long, so they do not all end together. */
    private fun oilRunLength(i: Int) = OIL_RUN - 0.05f * ((i * 3) % 5)

    private fun oilRun(i: Int) = SourceNode(
        SourceNode.Source.DISTANCE_TRAVELED_IN_MULTIPLES_OF_BRUSH_SIZE,
        -OIL_RUN_STAGGER * i,
        oilRunLength(i) - OIL_RUN_STAGGER * i,
        OutOfRange.REPEAT,
    )

    private fun oilBristles() = lanes(-0.46f, 0.46f, 0.115f) { lane, i ->
        setCornerRounding(0.6f)
        // 8% of the line width at most. A bristle starts at 1%, grows, wobbles between 4% and 8%, and thins out.
        setScaleY(0.08f)
        addBehavior(aimedAtDirection())
        addBehavior(lane)
        addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -0.05f, 0.05f, noise(30 + i, 0.3f + 0.04f * i)))
        addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.7f, 0.8f, noise(50 + i, 0.2f)))
        addBehavior(vary(TargetNode.Target.HEIGHT_MULTIPLIER, 0.5f, 1f, noise(70 + i, 0.15f + 0.02f * i)))
        addBehavior(BrushBehavior(TargetNode(TargetNode.Target.HEIGHT_MULTIPLIER, 0.125f, 1f, ResponseNode(spindle, oilRun(i)))))
    }

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

    private fun painted(tip: BrushTip, layer: BrushPaint.TextureLayer) = BrushCoat(tip, BrushPaint(listOf(layer)))

    private fun paintedFamily(vararg coats: BrushCoat) = BrushFamily.builder()
        .setCoats(coats.toList())
        .setInputModel(BrushFamily.InputModel.SlidingWindowModel())
        .build()

    /**
     * A picture stamped once for each particle, turned with the particle. A sheet of several pictures shows one at
     * random for each stamp, chosen by a PAINT_ANIMATION_PROGRESS_OFFSET behaviour on the tip.
     */
    // Lint marks the stamping constructor as restricted, though ink's own docs use it and it is the only way to build one.
    @android.annotation.SuppressLint("RestrictedApi")
    private fun stamped(id: String, frames: Int = 1, columns: Int = 1, rows: Int = 1) = BrushPaint.StampingTexture(
        id, BrushPaint.TextureLayer.BlendMode.MODULATE, frames, rows, columns, STAMP_LOOP_MS, BrushPaint.AnimationRepeatMode.RESTART,
    )

    /** A coat drawn at a fixed fraction of the stroke's opacity. The paint does this, so no behaviour is involved. */
    private fun faint(tip: BrushTip, opacity: Float) =
        BrushCoat(tip, BrushPaint(emptyList(), listOf(BrushPaint.ColorFunction.OpacityMultiplier(opacity))))

    private fun tip(build: BrushTip.Builder.() -> Unit) = BrushTip.builder().apply(build).build()

    private val creative = ConcurrentHashMap<BrushKind, BrushFamily>()

    /** Brushes that stamp pictures, and so can only be drawn on a hardware canvas, with the plain brush that stands in on a software one. */
    private val standIns = ConcurrentHashMap<BrushFamily, BrushFamily>()

    private fun stampedFamily(family: BrushFamily, standIn: BrushFamily): BrushFamily {
        standIns[family] = standIn
        return family
    }

    /**
     * Draws [stroke]. Canvases that are not hardware accelerated, such as a bitmap or a PDF page, cannot draw
     * the stamped brushes, so a plain look-alike is drawn there: for thumbnails and exports.
     */
    fun draw(renderer: androidx.ink.rendering.android.canvas.CanvasStrokeRenderer, canvas: android.graphics.Canvas, stroke: Stroke, matrix: android.graphics.Matrix) {
        val standIn = if (canvas.isHardwareAccelerated) null else standIns[stroke.brush.family]
        if (standIn == null) {
            renderer.draw(canvas, stroke, matrix)
        } else {
            renderer.draw(canvas, Stroke(stroke.brush.copy(family = standIn), stroke.inputs), matrix)
        }
    }

    private fun creativeFamily(kind: BrushKind): BrushFamily = creative.getOrPut(kind) {
        when (kind) {
            // Graphite: a dense run of round patches of grain, each turned along the line.
            BrushKind.PENCIL -> stampedFamily(
                paintedFamily(
                    painted(
                        tip {
                            setCornerRounding(1f)
                            setScaleX(1.1f)
                            setScaleY(1.1f)
                            setParticleGapDistanceScale(0.22f)
                            addBehavior(aimedAtDirection())
                            addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, 0f, TWO_PI, noise(2, 0.07f)))
                            addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.45f, 0.8f, noise(1, 0.15f)))
                        },
                        stamped(BrushTextures.GRAIN),
                    ),
),
                brushFamily(tip { setCornerRounding(1f); addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.45f, 0.7f, noise(1, 0.3f))) }),
            )
            // Oil: slices of the reference's bristle streaks, stamped end to end along the line and turned with the pen.
            // On a software canvas, where stamps cannot be drawn, the bristles are drawn as plain lines instead.
            BrushKind.OIL -> stampedFamily(
                paintedFamily(
                    painted(
                        tip {
                            setCornerRounding(0f)
                            setScaleX(2.24f)
                            setScaleY(2.4f)
                            setParticleGapDistanceScale(0.35f)
                            addBehavior(aimedAtDirection())
                            addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.75f, 0.95f, noise(3, 0.25f)))
                        },
                        stamped(BrushTextures.OIL),
                    ),
                ),
                paintedFamily(oilBleed(), *oilBristles().map { BrushCoat(it) }.toTypedArray()),
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
            // Wet washes: ragged blots of pigment stamped along the line, turned by the pen and overlapping into darker patches.
            BrushKind.WATERCOLOR -> stampedFamily(
                paintedFamily(
                    painted(
                        tip {
                            setCornerRounding(1f)
                            setScaleX(1.9f)
                            setScaleY(1.9f)
                            setParticleGapDistanceScale(0.343f)
                            addBehavior(aimedAtDirection())
                            addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, -0.5f, 0.5f, noise(62, 0.2f)))
                            addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.8f, 1.15f, noise(9, 0.15f)))
                            addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.2f, 0.4f, noise(10, 0.2f)))
                        },
                        stamped(BrushTextures.BLOT),
                    ),
),
                brushFamily(tip { setCornerRounding(1f); setScaleX(1.8f); setScaleY(1.8f); addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.3f, 0.5f, noise(10, 0.4f))) }),
            )
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
            // Tree leaves cut from the reference stroke, one picked at random for each stamp and turned with the pen.
            BrushKind.LEAVES -> stampedFamily(
                paintedFamily(
                    painted(
                        tip {
                            setCornerRounding(1f)
                            setScaleX(1.54f)
                            setScaleY(1.54f)
                            setParticleGapDistanceScale(0.9f)
                            addBehavior(vary(TargetNode.Target.PAINT_ANIMATION_PROGRESS_OFFSET, 0f, 1f, noise(80, 0.002f)))
                            addBehavior(aimedAtDirection())
                            addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, -0.9f, 0.9f, noise(14, 0.05f)))
                        addBehavior(vary(TargetNode.Target.OPACITY_MULTIPLIER, 0.9f, 1f, noise(92, 0.1f)))
                        // Now and then a leaf is caught by the wind: turned further and seen narrower, as if edge on.
                        addBehavior(BrushBehavior(TargetNode(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, 0f, 1.3f, ResponseNode(seldom, noise(90, 0.1f)))))
                        addBehavior(BrushBehavior(TargetNode(TargetNode.Target.WIDTH_MULTIPLIER, 1f, 0.45f, ResponseNode(seldom, noise(90, 0.1f)))))
                            addBehavior(vary(TargetNode.Target.SIZE_MULTIPLIER, 0.65f, 1.2f, noise(15, 0.06f)))
                            addBehavior(vary(TargetNode.Target.POSITION_OFFSET_LATERAL_IN_MULTIPLES_OF_BRUSH_SIZE, -1f, 1f, noise(16, 0.06f)))
                        },
                        stamped(BrushTextures.LEAVES, BrushTextures.LEAF_COUNT, BrushTextures.LEAF_COLUMNS, BrushTextures.LEAF_ROWS),
                    ),
),
                brushFamily(tip { setCornerRounding(0.5f); setPinch(0.85f); setScaleX(1.4f); setScaleY(0.8f); setParticleGapDistanceScale(0.9f); addBehavior(vary(TargetNode.Target.ROTATION_OFFSET_IN_RADIANS, 0f, TWO_PI, noise(14, 0.03f))) }),
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
            BrushKind.GLOW -> paintedFamily(
                *Array(9) { i ->
                    val scale = 3f - 0.27f * i
                    faint(tip { setCornerRounding(1f); setScaleX(scale); setScaleY(scale) }, 0.1f)
                },
                BrushCoat(tip { setCornerRounding(1f) }),
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
            PenType.NATURAL -> naturalPen
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
