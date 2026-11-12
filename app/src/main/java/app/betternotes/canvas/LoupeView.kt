package app.betternotes.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.layout.DocumentLayout
import app.betternotes.ui.theme.CanvasColors

/**
 * The loupe's magnified window, which shows a small box of a page across a larger area of the screen.
 * The pen writes in it at that size, and the ink lands in the box. It only draws: touches reach it through
 * the canvas, which owns the pen.
 */
@SuppressLint("ViewConstructor") // Only ever built in code, by CanvasView.
internal class LoupeView(context: Context) : View(context) {
    /** Where the window is on screen, in this view's coordinates. Empty until the controls have been laid out. */
    val area = RectF()

    var pageIndex = 0

    /** The box on the page, in the page's own coordinates. */
    val box = RectF()

    var painter: PagePainter? = null
    var layoutOf: () -> DocumentLayout? = { null }

    private val density = resources.displayMetrics.density
    private val clip = Path()
    private val radii = FloatArray(8)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = EDGE_COLOR
        strokeWidth = density
    }

    /** Screen pixels per point in the window. */
    val scale get() = if (box.width() > 0f) area.width() / box.width() else 1f

    fun contains(x: Float, y: Float) = area.contains(x, y)

    /** The document point that shows under a screen point of the window. */
    fun worldX(screenX: Float) = box.left + (screenX - area.left) / scale

    fun worldY(screenY: Float) = box.top + (screenY - area.top) / scale

    /** Fills [matrix] with the mapping from the window's screen pixels to document coordinates. */
    fun screenToWorld(matrix: Matrix) {
        matrix.setTranslate(-area.left, -area.top)
        val inverse = 1f / scale
        matrix.postScale(inverse, inverse)
        matrix.postTranslate(box.left, box.top)
    }

    override fun onDraw(canvas: Canvas) {
        val paint = painter ?: return
        val layout = layoutOf() ?: return
        if (area.isEmpty || box.isEmpty || !canvas.isHardwareAccelerated) return
        val scale = scale
        // The box is in document coordinates, and whatever page lies there, turned or not, shows as it is.
        val x = box.left
        val y = box.top
        val region = Bounds(x, y, x + box.width(), y + area.height() / scale)
        clip.reset()
        val radius = RADIUS_DP * density
        radii.fill(radius, fromIndex = 4)
        clip.addRoundRect(area, radii, Path.Direction.CW)
        canvas.withClip(clip) {
            canvas.drawColor(BACKDROP)
            canvas.withTranslation(area.left, area.top) {
                paint.paint(this, layout, region, x.toDouble() * scale, y.toDouble() * scale, scale)
            }
        }
        canvas.drawPath(clip, edge)
    }

    private companion object {
        const val RADIUS_DP = 20f
        const val EDGE_COLOR = CanvasColors.LOUPE_EDGE
        const val BACKDROP = CanvasColors.LOUPE_BACKDROP
    }
}
