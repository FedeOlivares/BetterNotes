package app.betternotes.core.tools

import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import kotlin.math.abs
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
        ShapeKind.CYLINDER, ShapeKind.CUBE -> setOf(ShapeParam.STROKE_WIDTH, ShapeParam.TIP_LENGTH)
        ShapeKind.TAPE -> emptySet()
        else -> setOf(ShapeParam.STROKE_WIDTH)
    }

    /** The tip's length, along the shape. */
    fun length(kind: ShapeKind, w: Float, h: Float, tipLength: Float): Float {
        if (tipLength > 0f) return tipLength
        return when (kind) {
            ShapeKind.ARROW -> min(w * 0.25f, ARROW_HEAD_MAX)
            ShapeKind.AXES -> min(w, h) * 0.1f
            ShapeKind.BLOCK_ARROW -> w * 0.4f
            ShapeKind.CYLINDER -> h * 0.15f
            ShapeKind.CUBE -> min(w, h) * 0.25f
            else -> 0f
        }
    }

    /** The tip's full width, across the shape. */
    fun width(kind: ShapeKind, w: Float, h: Float, tipLength: Float, tipWidth: Float): Float {
        if (tipWidth > 0f) return tipWidth
        return when (kind) {
            ShapeKind.ARROW -> length(kind, w, h, tipLength) * ARROW_SPREAD * 2f
            ShapeKind.AXES -> length(kind, w, h, tipLength) * 2f
            ShapeKind.BLOCK_ARROW -> h
            else -> 0f
        }
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
                ShapeKind.CYLINDER -> item.copy(tipLength = (y / 2f).coerceIn(MIN_TIP_PT, h * 0.4f))
                ShapeKind.CUBE -> item.copy(tipLength = y.coerceIn(MIN_TIP_PT, min(w, h) * 0.8f))
                else -> item.copy(tipLength = (w - x).coerceIn(MIN_TIP_PT, w * 0.9f))
            }
            ShapeParam.TIP_WIDTH -> item.copy(tipWidth = (2f * abs(y - axis)).coerceIn(MIN_TIP_PT, maxOf(h, w * 0.5f)))
        }
    }

    const val ARROW_HEAD_MAX = 24f
    const val ARROW_SPREAD = 0.6f
    const val AXES_ORIGIN = 0.7f
}
