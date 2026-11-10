package app.betternotes.canvas

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max

/** Where a written word lands, in view pixels: the left of the word, the top of its line, and how the page is turned. */
internal class WordTarget(
    val left: Float,
    /** True when a space goes before the word. */
    val leadingSpace: Boolean,
    val lineTop: Float,
    val lineHeight: Float,
    val textSizePx: Float,
    val color: Int,
    val bold: Boolean,
    val italic: Boolean,
    val turnDegrees: Float,
    val pivotX: Float,
    val pivotY: Float,
)

/**
 * Plays the hand-written ink turning into the typed word and flying to its place in the text box. It only draws, over the
 * canvas, and ignores touches. One word flies at a time.
 */
internal class WordFlightView(context: Context) : View(context) {
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var strokes: List<FloatArray> = emptyList()
    private var word = ""
    private var progress = 0f
    private var inkLeft = 0f
    private var inkTop = 0f
    private var inkWidth = 1f
    private var inkHeight = 1f
    private var wordWidth = 1f
    private var wordHeight = 1f
    private var baseline = 0f
    private var spaceWidth = 0f
    private var target: WordTarget? = null
    private var animator: ValueAnimator? = null

    val isFlying get() = animator?.isRunning == true

    /** [strokes] are x, y pairs in view pixels. [onLanded] runs when the word reaches its place. */
    fun fly(strokes: List<FloatArray>, widthPx: Float, inkColor: Int, word: String, target: WordTarget, onLanded: () -> Unit) {
        this.strokes = strokes
        this.word = word
        this.target = target
        inkPaint.strokeWidth = widthPx
        inkPaint.color = inkColor
        textPaint.textSize = target.textSizePx
        textPaint.color = target.color
        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            when {
                target.bold && target.italic -> Typeface.BOLD_ITALIC
                target.bold -> Typeface.BOLD
                target.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (stroke in strokes) {
            for (i in stroke.indices step 2) {
                left = minOf(left, stroke[i])
                right = maxOf(right, stroke[i])
                top = minOf(top, stroke[i + 1])
                bottom = maxOf(bottom, stroke[i + 1])
            }
        }
        inkLeft = left
        inkTop = top
        inkWidth = max(right - left, 1f)
        inkHeight = max(bottom - top, 1f)
        wordWidth = max(textPaint.measureText(word), 1f)
        spaceWidth = if (target.leadingSpace) textPaint.measureText(" ") else 0f
        val metrics = textPaint.fontMetrics
        wordHeight = metrics.descent - metrics.ascent
        baseline = (target.lineHeight - wordHeight) / 2f - metrics.ascent
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIGHT_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator !== animation) return
                    onLanded()
                    // The text box draws the word on its next frame or two; until then the flown word covers its place.
                    postOnAnimation {
                        postOnAnimation {
                            if (animator === animation) {
                                animator = null
                                this@WordFlightView.strokes = emptyList()
                                invalidate()
                            }
                        }
                    }
                }
            })
            start()
        }
    }

    fun clear() {
        val running = animator
        animator = null
        running?.cancel()
        strokes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val target = target ?: return
        if (animator == null) return
        val e = progress
        val inkCx = inkLeft + inkWidth / 2f
        val inkCy = inkTop + inkHeight / 2f
        // The word's middle, with the page's turn applied to it.
        val local = floatArrayOf(target.left + spaceWidth + wordWidth / 2f, target.lineTop + target.lineHeight / 2f)
        val turn = android.graphics.Matrix().apply { setRotate(target.turnDegrees, target.pivotX, target.pivotY) }
        turn.mapPoints(local)
        val cx = inkCx + (local[0] - inkCx) * e
        val cy = inkCy + (local[1] - inkCy) * e
        val inkToWord = (wordWidth / inkWidth).coerceIn(MIN_SHRINK, MAX_GROW)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(target.turnDegrees * e)
        val inkScale = 1f + (inkToWord - 1f) * e
        canvas.scale(inkScale, inkScale)
        canvas.translate(-inkCx, -inkCy)
        val inkAlpha = 1f - smooth(INK_FADE_FROM, INK_FADE_TO, e)
        if (inkAlpha > 0f) {
            inkPaint.alpha = (255 * inkAlpha).toInt()
            for (stroke in strokes) {
                path.rewind()
                path.moveTo(stroke[0], stroke[1])
                for (i in 2 until stroke.size step 2) path.lineTo(stroke[i], stroke[i + 1])
                if (stroke.size == 2) path.lineTo(stroke[0] + 0.1f, stroke[1])
                canvas.drawPath(path, inkPaint)
            }
        }
        canvas.restore()
        val wordAlpha = smooth(WORD_FADE_FROM, WORD_FADE_TO, e)
        if (wordAlpha > 0f) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(target.turnDegrees * e)
            val wordScale = 1f / inkToWord + (1f - 1f / inkToWord) * e
            canvas.scale(wordScale, wordScale)
            textPaint.alpha = (255 * wordAlpha).toInt()
            canvas.drawText(word, -wordWidth / 2f, baseline - target.lineHeight / 2f, textPaint)
            canvas.restore()
        }
    }

    private fun smooth(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FLIGHT_MS = 420L
        const val MIN_SHRINK = 0.15f
        const val MAX_GROW = 2f
        const val INK_FADE_FROM = 0.35f
        const val INK_FADE_TO = 0.8f
        const val WORD_FADE_FROM = 0.25f
        const val WORD_FADE_TO = 0.7f
    }
}
