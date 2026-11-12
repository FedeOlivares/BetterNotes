package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import app.betternotes.core.tools.AngleGuide
import app.betternotes.ui.theme.CanvasColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The angle tool shown while a straight line is drawn: a small red arc and the angle in degrees near the line's start,
 * and, when the line is close to a reference angle, a thin faint dashed centerline along that angle. It is drawn in
 * screen pixels, and goes when the pen lifts.
 */
internal class AngleGuidePainter(private val density: Float) {
    private val red = CanvasColors.ANGLE_GUIDE
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = red
        strokeWidth = 1.2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = red
        alpha = CENTERLINE_ALPHA
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = red
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val oval = RectF()

    /** [degrees] is the line's angle, anticlockwise from the right, from (startX, startY) to (endX, endY) in screen pixels. */
    fun draw(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, degrees: Float) {
        val length = hypot(endX - startX, endY - startY)
        if (AngleGuide.distance(degrees) <= AngleGuide.SHOW_DEGREES) {
            val reference = Math.toRadians(AngleGuide.nearest(degrees).toDouble())
            val dx = cos(reference).toFloat()
            val dy = -sin(reference).toFloat()
            val before = CENTERLINE_BEFORE_DP * density
            val after = length + CENTERLINE_AFTER_DP * density
            canvas.drawLine(startX - dx * before, startY - dy * before, startX + dx * after, startY + dy * after, center)
        }
        val radius = ARC_RADIUS_DP * density
        oval.set(startX - radius, startY - radius, startX + radius, startY + radius)
        // Canvas angles run clockwise, so the sweep is the negative of the line's anticlockwise angle. Just short of a full turn is a full circle.
        canvas.drawArc(oval, 0f, -degrees, false, arc)
        canvas.drawLine(startX, startY, startX + radius * 1.4f, startY, arc)
        val middle = Math.toRadians((degrees / 2f).toDouble())
        val labelRadius = radius + LABEL_GAP_DP * density
        canvas.drawText(
            "${degrees.roundToInt() % 360}\u00B0",
            startX + cos(middle).toFloat() * labelRadius,
            startY - sin(middle).toFloat() * labelRadius + text.textSize / 3f,
            text,
        )
    }

    private companion object {
        const val ARC_RADIUS_DP = 10f
        const val LABEL_GAP_DP = 11f
        const val CENTERLINE_ALPHA = 90
        const val CENTERLINE_BEFORE_DP = 24f
        const val CENTERLINE_AFTER_DP = 70f
    }
}
