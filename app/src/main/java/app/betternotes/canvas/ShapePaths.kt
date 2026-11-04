package app.betternotes.canvas

import android.graphics.Path
import android.graphics.RectF
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.tools.ShapeTips
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The outline of each shape, in a box of the given size with its corner at the origin. */
internal object ShapePaths {
    private val box = RectF()
    private val cloud = Path()

    fun build(path: Path, kind: ShapeKind, w: Float, h: Float, cornerRadius: Float, tipLength: Float = 0f, tipWidth: Float = 0f) {
        path.reset()
        val midY = h / 2f
        when (kind) {
            ShapeKind.LINE -> {
                path.moveTo(0f, midY)
                path.lineTo(w, midY)
            }
            ShapeKind.ARROW -> {
                val head = ShapeTips.length(kind, w, h, tipLength)
                val spread = ShapeTips.width(kind, w, h, tipLength, tipWidth) / 2f
                path.moveTo(0f, midY)
                path.lineTo(w, midY)
                path.moveTo(w - head, midY - spread)
                path.lineTo(w, midY)
                path.lineTo(w - head, midY + spread)
            }
            ShapeKind.RECTANGLE -> {
                box.set(0f, 0f, w, h)
                val radius = cornerRadius.coerceAtMost(min(w, h) / 2f)
                path.addRoundRect(box, radius, radius, Path.Direction.CW)
            }
            ShapeKind.ELLIPSE -> {
                box.set(0f, 0f, w, h)
                path.addOval(box, Path.Direction.CW)
            }
            ShapeKind.TRIANGLE -> polygon(path, 0.5f * w, 0f, w, h, 0f, h)
            ShapeKind.DIAMOND -> polygon(path, w / 2f, 0f, w, midY, w / 2f, h, 0f, midY)
            ShapeKind.PARALLELOGRAM -> polygon(path, w * SLANT, 0f, w, 0f, w * (1f - SLANT), h, 0f, h)
            ShapeKind.TRAPEZOID -> polygon(path, w * SLANT, 0f, w * (1f - SLANT), 0f, w, h, 0f, h)
            ShapeKind.BLOCK_ARROW -> {
                val neck = w - ShapeTips.length(kind, w, h, tipLength)
                val reach = (ShapeTips.width(kind, w, h, tipLength, tipWidth) / 2f).coerceAtLeast(h * 0.2f)
                polygon(
                    path,
                    0f, h * 0.3f,
                    neck, h * 0.3f,
                    neck, midY - reach,
                    w, midY,
                    neck, midY + reach,
                    neck, h * 0.7f,
                    0f, h * 0.7f,
                )
            }
            ShapeKind.CURVE -> {
                path.moveTo(0f, midY)
                path.cubicTo(w * 0.25f, -h * 0.3f, w * 0.25f, h * 1.1f, w / 2f, midY)
                path.cubicTo(w * 0.75f, -h * 0.1f, w * 0.75f, h * 1.3f, w, midY)
            }
            ShapeKind.PENTAGON -> regular(path, 5, w, h)
            ShapeKind.HEXAGON -> regular(path, 6, w, h)
            ShapeKind.PIE -> {
                box.set(0f, 0f, w, h)
                path.moveTo(w / 2f, midY)
                path.arcTo(box, 0f, 360f - ShapeTips.length(kind, w, h, tipLength))
                path.close()
            }
            ShapeKind.ARC -> {
                box.set(0f, 0f, w, h)
                path.addArc(box, ARC_START, PIE_SWEEP)
            }
            ShapeKind.STAR -> star(path, w, h)
            ShapeKind.SPHERE -> {
                box.set(0f, 0f, w, h)
                path.addOval(box, Path.Direction.CW)
                box.set(0f, h * 0.35f, w, h * 0.65f)
                path.addOval(box, Path.Direction.CW)
            }
            ShapeKind.PYRAMID -> {
                // A cone, as the screenshot's grid draws it.
                val cap = h * 0.12f
                path.moveTo(0f, h - cap)
                path.lineTo(w / 2f, 0f)
                path.lineTo(w, h - cap)
                box.set(0f, h - cap * 2f, w, h)
                path.arcTo(box, 0f, HALF_TURN)
            }
            ShapeKind.CUBE, ShapeKind.TETRAHEDRON, ShapeKind.PRISM, ShapeKind.DISCS, ShapeKind.SINE, ShapeKind.COSINE,
            ShapeKind.AXES, ShapeKind.PARABOLA, ShapeKind.CUBIC, ShapeKind.BRACE, ShapeKind.HEART, ShapeKind.SPEECH_BOX,
            ShapeKind.SPEECH_ROUND, ShapeKind.THOUGHT, ShapeKind.TAPE,
            -> buildSecondPage(path, kind, w, h, tipLength, tipWidth)
            ShapeKind.CYLINDER -> {
                val cap = ShapeTips.length(kind, w, h, tipLength)
                box.set(0f, 0f, w, cap * 2f)
                path.addOval(box, Path.Direction.CW)
                path.moveTo(0f, cap)
                path.lineTo(0f, h - cap)
                box.set(0f, h - cap * 2f, w, h)
                path.arcTo(box, HALF_TURN, -HALF_TURN)
                path.lineTo(w, cap)
            }
        }
    }

    /** The shapes of the screenshot's second page, and tape. Their proportions come from that grid. */
    private fun buildSecondPage(path: Path, kind: ShapeKind, w: Float, h: Float, tipLength: Float, tipWidth: Float) {
        val midY = h / 2f
        when (kind) {
            ShapeKind.CUBE -> {
                // A cube drawn before it had a handle keeps its look, since its depth is then not set.
                val side = if (tipLength > 0f) (tipLength / min(w, h)).coerceAtMost(0.9f) else 0.25f
                polygon(path, 0f, h * side, w * (1f - side), h * side, w * (1f - side), h, 0f, h)
                polyline(path, 0f, h * side, w * side, 0f, w, 0f, w, h * (1f - side), w * (1f - side), h)
                polyline(path, w * (1f - side), h * side, w, 0f)
            }
            ShapeKind.TETRAHEDRON -> {
                polygon(path, w / 2f, 0f, w, h * 0.72f, w / 2f, h, 0f, h * 0.72f)
                polyline(path, w / 2f, 0f, w / 2f, h)
            }
            ShapeKind.PRISM -> {
                // The top triangle is as tall as the frame minus the body, so growing the body leaves it alone.
                val top = h - ShapeTips.length(kind, w, h, tipLength)
                polygon(path, 0f, top, w * 0.65f, 0f, w, top * 0.25f / 0.3f, w, h, 0f, h)
                polyline(path, 0f, top, w, top * 0.25f / 0.3f)
            }
            ShapeKind.DISCS -> {
                box.set(0f, 0f, w, h * 0.45f)
                path.addOval(box, Path.Direction.CW)
                box.set(0f, h * 0.55f, w, h)
                path.addOval(box, Path.Direction.CW)
            }
            ShapeKind.SINE -> wave(path, w, h, 0.0)
            ShapeKind.COSINE -> wave(path, w, h, PI / 2.0)
            ShapeKind.AXES -> {
                val originX = w * 0.3f
                val originY = h * 0.7f
                val head = ShapeTips.length(kind, w, h, tipLength)
                val spread = ShapeTips.width(kind, w, h, tipLength, tipWidth) / 2f
                polyline(path, originX, originY, originX, 0f)
                polyline(path, originX - spread, head, originX, 0f, originX + spread, head)
                polyline(path, originX, originY, w, originY)
                polyline(path, w - head, originY - spread, w, originY, w - head, originY + spread)
                polyline(path, originX, originY, 0f, h)
            }
            ShapeKind.PARABOLA -> curve(path, w, h) { x -> x * x }
            ShapeKind.CUBIC -> curve(path, w, h) { x -> x * x * x }
            ShapeKind.BRACE -> {
                path.moveTo(w * 0.8f, 0f)
                path.cubicTo(w * 0.2f, 0f, w * 0.7f, midY * 0.85f, w * 0.15f, midY)
                path.cubicTo(w * 0.7f, midY * 1.15f, w * 0.2f, h, w * 0.8f, h)
            }
            ShapeKind.HEART -> {
                path.moveTo(w / 2f, h)
                path.cubicTo(-w * 0.15f, h * 0.55f, w * 0.05f, -h * 0.1f, w / 2f, h * 0.25f)
                path.cubicTo(w * 0.95f, -h * 0.1f, w * 1.15f, h * 0.55f, w / 2f, h)
                path.close()
            }
            ShapeKind.SPEECH_BOX -> polygon(
                path,
                0f, 0f, w, 0f, w, h * 0.75f,
                w * 0.7f, h * 0.75f, w * 0.72f, h, w * 0.45f, h * 0.75f, 0f, h * 0.75f,
            )
            ShapeKind.SPEECH_ROUND -> speechBubble(path, w, h, tipLength, tipWidth)
            ShapeKind.THOUGHT -> {
                // Circles that touch merge into one outline, so the cloud has no lines inside it.
                for ((i, bump) in CLOUD_BUMPS.withIndex()) {
                    cloud.rewind()
                    // Ovals sized by the frame, not circles sized by its shorter side, which come apart when it is stretched.
                    box.set((bump[0] - bump[2]) * w, (bump[1] - bump[2]) * h, (bump[0] + bump[2]) * w, (bump[1] + bump[2]) * h)
                    cloud.addOval(box, Path.Direction.CW)
                    if (i == 0) path.set(cloud) else path.op(cloud, Path.Op.UNION)
                }
                thoughtTail(path, w, h, tipLength, tipWidth)
            }
            ShapeKind.TAPE -> tape(path, w, h)
            else -> Unit
        }
    }

    /** Two small circles leading from the cloud toward the tip, which can be dragged anywhere around it. */
    private fun thoughtTail(path: Path, w: Float, h: Float, tipLength: Float, tipWidth: Float) {
        val tipX = ShapeTips.length(ShapeKind.THOUGHT, w, h, tipLength)
        val tipY = ShapeTips.width(ShapeKind.THOUGHT, w, h, tipLength, tipWidth)
        val fromX = w * 0.52f
        val fromY = h * 0.36f
        for ((along, size) in THOUGHT_TAIL) {
            val cx = fromX + (tipX - fromX) * along
            val cy = fromY + (tipY - fromY) * along
            box.set(cx - w * size, cy - h * size, cx + w * size, cy + h * size)
            path.addOval(box, Path.Direction.CW)
        }
    }

    /** One closed outline: the ellipse is left open where the tail joins it, and the tail runs to its tip. */
    private fun speechBubble(path: Path, w: Float, h: Float, tipLength: Float, tipWidth: Float) {
        val rx = w / 2f
        val ry = h * ShapeTips.SPEECH_ELLIPSE / 2f
        val cx = rx
        val cy = ry
        val tipX = ShapeTips.length(ShapeKind.SPEECH_ROUND, w, h, tipLength)
        val tipY = ShapeTips.width(ShapeKind.SPEECH_ROUND, w, h, tipLength, tipWidth)
        // The angle along the ellipse that points at the tip, and a mouth to either side of it.
        val toward = Math.toDegrees(atan2(((tipY - cy) / ry).toDouble(), ((tipX - cx) / rx).toDouble())).toFloat()
        val start = toward + SPEECH_MOUTH_DEGREES
        box.set(0f, 0f, w, ry * 2f)
        val begin = Math.toRadians(start.toDouble())
        path.moveTo(cx + rx * cos(begin).toFloat(), cy + ry * sin(begin).toFloat())
        path.arcTo(box, start, 360f - 2f * SPEECH_MOUTH_DEGREES)
        path.lineTo(tipX, tipY)
        path.close()
    }

    private fun polyline(path: Path, vararg xy: Float) {
        path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i + 1])
    }

    private fun wave(path: Path, w: Float, h: Float, phase: Double) {
        for (i in 0..CURVE_STEPS) {
            val t = i.toDouble() / CURVE_STEPS
            val x = w * t.toFloat()
            val y = h / 2f - h / 2f * sin(2.0 * PI * t + phase).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    /** A function drawn from -1 to 1 across the box, with its largest value at the top. */
    private inline fun curve(path: Path, w: Float, h: Float, function: (Float) -> Float) {
        val values = FloatArray(CURVE_STEPS + 1) { function(-1f + 2f * it / CURVE_STEPS) }
        val low = values.min()
        val high = values.max()
        for (i in 0..CURVE_STEPS) {
            val x = w * i / CURVE_STEPS
            val y = h * (1f - (values[i] - low) / (high - low))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    /** A strip with both ends torn in a zigzag. */
    private fun tape(path: Path, w: Float, h: Float) {
        val teeth = TAPE_TEETH
        val bite = min(h / teeth, w / 4f)
        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        for (i in 1..teeth * 2) path.lineTo(if (i % 2 == 1) w - bite else w, h * i / (teeth * 2))
        path.lineTo(0f, h)
        for (i in 1..teeth * 2) path.lineTo(if (i % 2 == 1) bite else 0f, h - h * i / (teeth * 2))
        path.close()
    }

    private fun polygon(path: Path, vararg xy: Float) {
        path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i + 1])
        path.close()
    }

    private fun regular(path: Path, sides: Int, w: Float, h: Float) {
        for (i in 0 until sides) {
            val a = (2.0 * PI * i / sides - PI / 2.0)
            val x = (w / 2f + w / 2f * cos(a).toFloat())
            val y = (h / 2f + h / 2f * sin(a).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun star(path: Path, w: Float, h: Float) {
        for (i in 0 until STAR_POINTS * 2) {
            val reach = if (i % 2 == 0) 1f else STAR_INNER
            val a = PI * i / STAR_POINTS - PI / 2.0
            val x = w / 2f + w / 2f * reach * cos(a).toFloat()
            val y = h / 2f + h / 2f * reach * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private const val SPEECH_MOUTH_DEGREES = 14f

    /** How far along from the cloud to the tip each tail circle sits, and its size as a part of the frame. */
    private val THOUGHT_TAIL = arrayOf(0.72f to 0.045f, 0.92f to 0.03f)
    private const val CURVE_STEPS = 40
    private const val TAPE_TEETH = 5
    private val CLOUD_BUMPS = arrayOf(
        floatArrayOf(0.3f, 0.3f, 0.2f),
        floatArrayOf(0.55f, 0.22f, 0.22f),
        floatArrayOf(0.75f, 0.38f, 0.2f),
        floatArrayOf(0.45f, 0.5f, 0.22f),
    )
    private const val HEAD_RATIO = 0.25f
    private const val HEAD_MAX = 24f
    private const val HEAD_SPREAD = 0.6f
    private const val SLANT = 0.22f
    private const val PIE_SWEEP = 270f
    private const val ARC_START = 180f
    private const val HALF_TURN = 180f
    private const val STAR_POINTS = 5
    private const val STAR_INNER = 0.42f
}
