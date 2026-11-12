package app.betternotes.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.withRotation
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.tools.FadingTrail
import app.betternotes.core.tools.Ruler
import app.betternotes.ui.theme.CanvasColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

enum class LaserMode { DOT, LINE, DELAY }

/**
 * What floats over the page without being part of it: the laser pointer's tail and the ruler. It only
 * draws, and it is invalidated only while one of them has something to show.
 */
@SuppressLint("ViewConstructor") // Only ever built in code, by CanvasView.
internal class CanvasOverlayView(context: Context, private val viewport: Viewport) : View(context) {
    var ruler: Ruler? = null
        set(value) {
            field = value
            invalidate()
        }

    /** How thick the laser's tail is, relative to its middle setting. */
    var laserScale = 1f

    /** What the laser shows: a dot only, a tail that fades as it goes, or a tail that waits for the pen to lift. */
    var laserMode = LaserMode.LINE
        set(value) {
            field = value
            rebuildTrail()
        }

    /** How long the tail lasts, from 1 up. */
    var laserLength = DEFAULT_LASER_LENGTH
        set(value) {
            field = value
            rebuildTrail()
        }

    var laserColor = LASER_COLOR
        set(value) {
            field = value
            glowPaint.color = value
            tipPaint.color = value
        }

    private val density = resources.displayMetrics.density
    private var trail = FadingTrail(LASER_LIFETIME_MS)
    private var laserHeld = false
    private var laserStartedAt = 0L
    private var laserReleasedAt = 0L
    // Square ends, because overlapping round ones show as beads along the see-through tail.
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = LASER_COLOR
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LASER_COLOR }
    private val coreFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CanvasColors.LASER_CORE }
    private val coreLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CanvasColors.LASER_CORE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rulerBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULER_BODY }
    private val rulerMajorTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RULER_MARK
        strokeWidth = density
    }
    private val rulerMinorTick = Paint(rulerMajorTick).apply { strokeWidth = density * MINOR_TICK_WEIGHT }
    private val rulerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RULER_MARK
        textAlign = Paint.Align.CENTER
        textSize = RULER_TEXT_DP * density
    }

    fun laserStart(x: Float, y: Float) {
        laserStartedAt = SystemClock.uptimeMillis()
        laserHeld = true
        trail.start(x, y, laserStartedAt)
        invalidate()
    }

    fun laserEnd() {
        laserHeld = false
        laserReleasedAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun rebuildTrail() {
        trail = FadingTrail(if (laserMode == LaserMode.DOT) DOT_LIFETIME_MS else LASER_LIFETIME_MS / DEFAULT_LASER_LENGTH * laserLength)
        laserHeld = false
        invalidate()
    }

    fun laserMove(x: Float, y: Float) {
        trail.add(x, y, SystemClock.uptimeMillis())
        invalidate()
    }

    /** Draws anything else that floats over the page, in view pixels. */
    var extraDraw: (Canvas) -> Unit = {}

    override fun onDraw(canvas: Canvas) {
        extraDraw(canvas)
        ruler?.let { drawRuler(canvas, it) }
        drawLaser(canvas)
    }

    private fun drawLaser(canvas: Canvas) {
        val real = SystemClock.uptimeMillis()
        // The delayed tail keeps its strength while the pen is down, then fades as a whole.
        val now = when {
            laserMode != LaserMode.DELAY -> real
            laserHeld -> laserStartedAt
            else -> laserStartedAt + (real - laserReleasedAt)
        }
        // A held pen keeps its tip however still it is.
        if (!laserHeld && !trail.prune(now) || laserHeld && trail.tipIndex < 0) return
        val width = LASER_WIDTH_DP * density * laserScale
        if (laserMode != LaserMode.DOT) {
            trail.forEachSegment(now) { x0, y0, x1, y1, strength ->
                glowPaint.alpha = (strength * LASER_GLOW_ALPHA).roundToInt()
                glowPaint.strokeWidth = width * LASER_GLOW_RATIO * strength
                canvas.drawLine(x0, y0, x1, y1, glowPaint)
                glowPaint.alpha = (strength * LASER_ALPHA).roundToInt()
                glowPaint.strokeWidth = width * strength
                canvas.drawLine(x0, y0, x1, y1, glowPaint)
                coreLine.alpha = (strength * OPAQUE).roundToInt()
                coreLine.strokeWidth = width * LASER_CORE_RATIO * strength
                canvas.drawLine(x0, y0, x1, y1, coreLine)
            }
        }
        val tip = trail.tipIndex
        val strength = if (laserHeld) 1f else trail.strengthAt(tip, now)
        val tipX = trail.xAt(tip)
        val tipY = trail.yAt(tip)
        // A soft red halo, a red ring, and a white middle.
        tipPaint.alpha = (strength * LASER_GLOW_ALPHA).roundToInt()
        canvas.drawCircle(tipX, tipY, width * LASER_GLOW_RATIO, tipPaint)
        tipPaint.alpha = (strength * OPAQUE).roundToInt()
        canvas.drawCircle(tipX, tipY, width * LASER_TIP_RATIO, tipPaint)
        coreFill.alpha = (strength * OPAQUE).roundToInt()
        canvas.drawCircle(tipX, tipY, width * LASER_CORE_RATIO, coreFill)
        // The tail goes on fading with no touches to prompt a redraw.
        postInvalidateOnAnimation()
    }

    /**
     * A band with marks on both long edges, counted out from zero at its center, and a dial in the middle
     * that gives its angle. Marks fall on the page's own centimeters, so the scale follows the zoom.
     */
    private fun drawRuler(canvas: Canvas, ruler: Ruler) {
        val degrees = Math.toDegrees(ruler.angleRadians.toDouble()).toFloat()
        val cx = ruler.centerX
        val cy = ruler.centerY
        val half = ruler.thickness / 2f
        canvas.withRotation(degrees, cx, cy) {
            drawRect(cx - ruler.length / 2f, cy - half, cx + ruler.length / 2f, cy + half, rulerBody)
            drawEdgeMarks(this, ruler)
        }
        val outer = DIAL_RADIUS_DP * density
        for (i in 0 until DIAL_TICKS) {
            val turn = 2.0 * PI * i / DIAL_TICKS + ruler.angleRadians
            val inner = outer - density * if (i % (DIAL_TICKS / 4) == 0) DIAL_LONG_TICK_DP else DIAL_TICK_DP
            val c = cos(turn).toFloat()
            val s = sin(turn).toFloat()
            canvas.drawLine(cx + inner * c, cy + inner * s, cx + outer * c, cy + outer * s, rulerMinorTick)
        }
        val text = "${ruler.displayDegrees().roundToInt() % HALF_TURN_DEGREES}°"
        canvas.drawText(text, cx, cy + rulerText.textSize * TEXT_CENTER_SHIFT, rulerText)
    }

    private fun drawEdgeMarks(canvas: Canvas, ruler: Ruler) {
        val cx = ruler.centerX
        val cy = ruler.centerY
        val half = ruler.thickness / 2f
        val pxPerMm = viewport.pxPerPt * POINTS_PER_MM
        val pxPerCm = pxPerMm * MM_PER_CM
        val minorStepMm = if (pxPerMm >= MIN_MM_TICK_DP * density) 1 else if (pxPerCm >= MIN_MM_TICK_DP * density * 2) MM_PER_CM / 2 else MM_PER_CM
        val labelStepCm = if (pxPerCm >= MIN_LABEL_GAP_DP * density) 1 else if (pxPerCm * 5 >= MIN_LABEL_GAP_DP * density) 5 else 10
        val majorTick = TICK_MAJOR_DP * density
        val minorTick = TICK_MINOR_DP * density
        val labelGap = LABEL_GAP_DP * density
        val reachMm = (ruler.length / 2f / pxPerMm).toInt()
        var mm = -reachMm / minorStepMm * minorStepMm
        while (mm <= reachMm) {
            val x = cx + mm * pxPerMm
            val isCm = mm % MM_PER_CM == 0
            val paint = if (isCm) rulerMajorTick else rulerMinorTick
            val tick = if (isCm) majorTick else minorTick
            canvas.drawLine(x, cy - half, x, cy - half + tick, paint)
            canvas.drawLine(x, cy + half, x, cy + half - tick, paint)
            if (isCm && (mm / MM_PER_CM) % labelStepCm == 0) {
                val label = abs(mm / MM_PER_CM).toString()
                canvas.drawText(label, x, cy - half + majorTick + labelGap + rulerText.textSize, rulerText)
                // The far edge reads the right way up from the other side of the ruler.
                canvas.withRotation(180f, x, cy + half - majorTick - labelGap - rulerText.textSize / 2f) {
                    drawText(label, x, cy + half - majorTick - labelGap, rulerText)
                }
            }
            mm += minorStepMm
        }
    }

    private companion object {
        const val LASER_LIFETIME_MS = 700L
        const val DOT_LIFETIME_MS = 250L
        const val DEFAULT_LASER_LENGTH = 2
        const val LASER_COLOR = CanvasColors.LASER
        const val LASER_WIDTH_DP = 7f
        const val LASER_ALPHA = 200f
        const val LASER_GLOW_ALPHA = 90f
        const val LASER_GLOW_RATIO = 2.4f
        const val LASER_CORE_RATIO = 0.35f
        const val LASER_TIP_RATIO = 0.6f
        const val OPAQUE = 255f

        const val RULER_BODY = CanvasColors.RULER_BODY
        const val RULER_MARK = CanvasColors.RULER_MARK
        const val RULER_TEXT_DP = 13f
        const val POINTS_PER_MM = 72f / 25.4f
        const val MM_PER_CM = 10
        const val MIN_MM_TICK_DP = 3f
        const val TICK_MAJOR_DP = 22f
        const val TICK_MINOR_DP = 11f
        const val MINOR_TICK_WEIGHT = 0.7f
        const val LABEL_GAP_DP = 3f
        const val MIN_LABEL_GAP_DP = 26f
        const val HALF_TURN_DEGREES = 180
        const val TEXT_CENTER_SHIFT = 0.35f
        const val DIAL_RADIUS_DP = 19f
        const val DIAL_TICK_DP = 3f
        const val DIAL_LONG_TICK_DP = 6f
        const val DIAL_TICKS = 24
    }
}
