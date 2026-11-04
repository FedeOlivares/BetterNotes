package app.betternotes.canvas

import android.graphics.Path
import android.graphics.RectF
import app.betternotes.core.model.ShapeKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The outline of each shape, in a box of the given size with its corner at the origin. */
internal object ShapePaths {
    private val box = RectF()

    fun build(path: Path, kind: ShapeKind, w: Float, h: Float, cornerRadius: Float) {
        path.reset()
        val midY = h / 2f
        when (kind) {
            ShapeKind.LINE -> {
                path.moveTo(0f, midY)
                path.lineTo(w, midY)
            }
            ShapeKind.ARROW -> {
                val head = min(w * HEAD_RATIO, HEAD_MAX)
                path.moveTo(0f, midY)
                path.lineTo(w, midY)
                path.moveTo(w - head, midY - head * HEAD_SPREAD)
                path.lineTo(w, midY)
                path.lineTo(w - head, midY + head * HEAD_SPREAD)
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
            ShapeKind.BLOCK_ARROW -> polygon(
                path,
                0f, h * 0.3f,
                w * 0.6f, h * 0.3f,
                w * 0.6f, 0f,
                w, midY,
                w * 0.6f, h,
                w * 0.6f, h * 0.7f,
                0f, h * 0.7f,
            )
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
                path.arcTo(box, 0f, PIE_SWEEP)
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
                polygon(path, w / 2f, 0f, w, h * 0.85f, w * 0.4f, h, 0f, h * 0.8f)
                path.moveTo(w / 2f, 0f)
                path.lineTo(w * 0.4f, h)
            }
            ShapeKind.CYLINDER -> {
                val cap = h * 0.15f
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
