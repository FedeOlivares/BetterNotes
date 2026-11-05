package app.betternotes.core.tools

import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.min

/** What a handle on a selected shape changes. */
enum class ShapeParam { STROKE_WIDTH, TIP_LENGTH, TIP_WIDTH }

/**
 * The adjustable parts of shapes, which the handles on a selected shape drag. A tip is the head of an
 * arrow, of an axis or of a block arrow; a cylinder's tip length is the height of its caps, and a cube's
 * is its depth. A [ShapeItem] stores 0 for a tip size that was never changed, which means the shape's own
 * proportions, so shapes saved before there were handles look the same.
 *
 * Positions are in the shape's own frame: the origin is the frame's top left and nothing is rotated.
 */
object ShapeTips {
    private const val MIN_TIP_PT = 3f
    private const val MAX_STROKE_PT = 60f
    private const val MIN_STROKE_PT = 0.5f

    fun params(kind: ShapeKind): Set<ShapeParam> = when (kind) {
        ShapeKind.ARROW, ShapeKind.BLOCK_ARROW, ShapeKind.AXES ->
            setOf(ShapeParam.STROKE_WIDTH, ShapeParam.TIP_LENGTH, ShapeParam.TIP_WIDTH)
        ShapeKind.CYLINDER, ShapeKind.CUBE, ShapeKind.PIE, ShapeKind.PRISM, ShapeKind.SPEECH_ROUND, ShapeKind.THOUGHT ->
            setOf(ShapeParam.STROKE_WIDTH, ShapeParam.TIP_LENGTH)
        ShapeKind.TAPE -> emptySet()
        else -> setOf(ShapeParam.STROKE_WIDTH)
    }

    /** The tip's length, along the shape. */
    fun length(kind: ShapeKind, w: Float, h: Float, tipLength: Float): Float {
        if (tipLength > 0f || tipLength != 0f && isBubble(kind)) return tipLength
        return when (kind) {
            ShapeKind.ARROW -> min(w * 0.25f, ARROW_HEAD_MAX)
            ShapeKind.AXES -> min(w, h) * 0.1f
            ShapeKind.BLOCK_ARROW -> w * 0.4f
            ShapeKind.CYLINDER -> h * 0.15f
            ShapeKind.CUBE -> min(w, h) * 0.25f
            // The pie's bite in degrees, the wedge's body height, and where the speech tail's tip is across.
            ShapeKind.PIE -> PIE_BITE_DEGREES
            ShapeKind.PRISM -> h * WEDGE_BODY
            ShapeKind.SPEECH_ROUND -> w * SPEECH_TAIL_X
            ShapeKind.THOUGHT -> w * THOUGHT_TAIL_X
            else -> 0f
        }
    }

    /** The tip's full width, across the shape. */
    fun width(kind: ShapeKind, w: Float, h: Float, tipLength: Float, tipWidth: Float): Float {
        if (tipWidth > 0f || tipWidth != 0f && isBubble(kind)) return tipWidth
        return when (kind) {
            ShapeKind.ARROW -> length(kind, w, h, tipLength) * ARROW_SPREAD * 2f
            ShapeKind.AXES -> length(kind, w, h, tipLength) * 2f
            ShapeKind.BLOCK_ARROW -> h
            ShapeKind.SPEECH_ROUND -> h
            ShapeKind.THOUGHT -> h * THOUGHT_TAIL_Y
            else -> 0f
        }
    }

    /** A speech or thought bubble keeps its tail's tip as a point of its frame, which may lie anywhere around it, outside the frame too. */
    fun isBubble(kind: ShapeKind) = kind == ShapeKind.SPEECH_ROUND || kind == ShapeKind.THOUGHT

    /** The point where a speech or thought bubble's tail ends, in its frame. */
    fun speechTip(item: ShapeItem): FloatArray {
        val w = item.frame.width
        val h = item.frame.height
        return floatArrayOf(length(item.kind, w, h, item.tipLength), width(item.kind, w, h, item.tipLength, item.tipWidth))
    }

    /** Where the handle for [param] sits, as x then y. */
    fun position(item: ShapeItem, param: ShapeParam): FloatArray {
        val w = item.frame.width
        val h = item.frame.height
        val length = length(item.kind, w, h, item.tipLength)
        val width = width(item.kind, w, h, item.tipLength, item.tipWidth)
        val axis = if (item.kind == ShapeKind.AXES) h * AXES_ORIGIN else h / 2f
        return when (param) {
            ShapeParam.STROKE_WIDTH -> floatArrayOf(w / 2f, -item.strokeWidth / 2f)
            ShapeParam.TIP_LENGTH -> when (item.kind) {
                ShapeKind.PIE -> {
                    val sweep = Math.toRadians((360f - length).toDouble())
                    floatArrayOf(w / 2f + w / 2f * cos(sweep).toFloat(), h / 2f + h / 2f * sin(sweep).toFloat())
                }
                ShapeKind.PRISM -> floatArrayOf(w / 2f, h)
                ShapeKind.SPEECH_ROUND, ShapeKind.THOUGHT -> speechTip(item)
                ShapeKind.CYLINDER -> floatArrayOf(w / 2f, length * 2f)
                ShapeKind.CUBE -> floatArrayOf(0f, length)
                else -> floatArrayOf(w - length, axis)
            }
            ShapeParam.TIP_WIDTH -> floatArrayOf(w - length, axis - width / 2f)
        }
    }

    /** The shape with the handle for [param] dragged to (x, y) in its frame. */
    fun dragged(item: ShapeItem, param: ShapeParam, x: Float, y: Float): ShapeItem {
        val w = item.frame.width
        val h = item.frame.height
        val axis = if (item.kind == ShapeKind.AXES) h * AXES_ORIGIN else h / 2f
        return when (param) {
            ShapeParam.STROKE_WIDTH -> item.copy(strokeWidth = (-2f * y).coerceIn(MIN_STROKE_PT, MAX_STROKE_PT))
            ShapeParam.TIP_LENGTH -> when (item.kind) {
                ShapeKind.PIE -> {
                    val around = Math.toDegrees(atan2((y - h / 2f) / (h / 2f), (x - w / 2f) / (w / 2f)).toDouble()).toFloat()
                    val sweep = if (around < 0f) around + 360f else around
                    item.copy(tipLength = (360f - sweep).coerceIn(MIN_BITE_DEGREES, 360f - MIN_BITE_DEGREES))
                }
                ShapeKind.PRISM -> {
                    // Only the body grows: the frame gets taller by as much, so the top triangle stays as it was.
                    val top = h - length(item.kind, w, h, item.tipLength)
                    val height = y.coerceAtLeast(top + MIN_TIP_PT)
                    item.copy(frame = item.frame.copy(height = height), tipLength = height - top)
                }
                ShapeKind.SPEECH_ROUND -> {
                    val tip = outsideEllipse(w / 2f, h * SPEECH_ELLIPSE / 2f, w / 2f, h * SPEECH_ELLIPSE / 2f, x.coerceIn(-TAIL_REACH * w, (1f + TAIL_REACH) * w), y.coerceIn(-TAIL_REACH * h, (1f + TAIL_REACH) * h))
                    item.copy(tipLength = tip[0], tipWidth = tip[1])
                }
                ShapeKind.THOUGHT -> {
                    val tip = outsideEllipse(w * 0.52f, h * 0.36f, w * 0.48f, h * 0.36f, x.coerceIn(-TAIL_REACH * w, (1f + TAIL_REACH) * w), y.coerceIn(-TAIL_REACH * h, (1f + TAIL_REACH) * h))
                    item.copy(tipLength = tip[0], tipWidth = tip[1])
                }
                ShapeKind.CYLINDER -> item.copy(tipLength = (y / 2f).coerceIn(MIN_TIP_PT, h * 0.4f))
                ShapeKind.CUBE -> item.copy(tipLength = y.coerceIn(MIN_TIP_PT, min(w, h) * 0.8f))
                else -> item.copy(tipLength = (w - x).coerceIn(MIN_TIP_PT, w * 0.9f))
            }
            ShapeParam.TIP_WIDTH -> item.copy(tipWidth = (2f * abs(y - axis)).coerceIn(MIN_TIP_PT, maxOf(h, w * 0.5f)))
        }
    }

    /** A point inside the bubble's ellipse is pushed out to its edge, so the tail always sticks out. */
    private fun outsideEllipse(cx: Float, cy: Float, rx: Float, ry: Float, x: Float, y: Float): FloatArray {
        val nx = (x - cx) / rx
        val ny = (y - cy) / ry
        val reach = hypot(nx, ny)
        if (reach >= 1.05f) return floatArrayOf(x, y)
        val scale = if (reach == 0f) 1.05f else 1.05f / reach
        return floatArrayOf(cx + nx * scale * rx, cy + ny * scale * ry)
    }

    /** How far outside its frame, in frames, a bubble's tail may be dragged. */
    const val TAIL_REACH = 2f
    const val PIE_BITE_DEGREES = 90f
    const val MIN_BITE_DEGREES = 15f
    const val WEDGE_BODY = 0.7f
    const val SPEECH_TAIL_X = 0.38f
    const val THOUGHT_TAIL_X = 0.82f
    const val THOUGHT_TAIL_Y = 0.95f
    const val SPEECH_ELLIPSE = 0.75f
    const val ARROW_HEAD_MAX = 24f
    const val ARROW_SPREAD = 0.6f
    const val AXES_ORIGIN = 0.7f
}
