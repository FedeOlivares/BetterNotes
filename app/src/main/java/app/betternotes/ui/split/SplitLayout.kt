package app.betternotes.ui.split

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ViewGroup
import app.betternotes.ui.theme.CanvasColors
import kotlin.math.roundToInt

/**
 * Two editor panes with a draggable bar between them. Side by side or one above the other, as [sideBySideChoice] says (by default
 * side by side on a screen wider than it is tall). With one pane, or none, it simply gives the room to the first.
 *
 * The bar is drawn here, not a child, so a touch on it never reaches a canvas. Dragging it moves the split; letting go
 * with a pane squeezed below 20% closes it ([onCollapse]); a double tap puts the bar back in the middle.
 */
class SplitLayout(context: Context) : ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private val barPx = (BAR_DP * density).roundToInt()
    private val grabPx = (GRAB_DP * density).roundToInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handle = android.graphics.RectF()

    /** The first pane's share of the room along the split, between [MIN_SHARE] and [MAX_SHARE]. */
    var share = 0.5f
        set(value) {
            field = value.coerceIn(DRAG_MIN, DRAG_MAX)
            requestLayout()
        }

    private val barColor = CanvasColors.SPLIT_BAR
    private val handleColor = CanvasColors.SPLIT_HANDLE

    /** True for panes side by side, false for one above the other, null to follow the screen's shape (wider than tall: side by side). */
    var sideBySideChoice: Boolean? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** Called when the bar is let go near an end: true if the first pane was squeezed out, false if the second. */
    var onCollapse: ((first: Boolean) -> Unit)? = null

    private val sideBySide get() = sideBySideChoice ?: (width >= height)
    private val split get() = childCount >= 2

    private var dragging = false
    private var lastTapMs = 0L

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        if (!split) {
            getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            return
        }
        val across = sideBySideChoice ?: (w >= h)
        val room = (if (across) w else h) - barPx
        val first = (room * share).roundToInt()
        val sizes = intArrayOf(first, room - first)
        for (i in 0..1) {
            val childW = if (across) sizes[i] else w
            val childH = if (across) h else sizes[i]
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        if (!split) {
            getChildAt(0)?.layout(0, 0, w, h)
            return
        }
        val first = getChildAt(0)
        val second = getChildAt(1)
        if (sideBySideChoice ?: (w >= h)) {
            first.layout(0, 0, first.measuredWidth, h)
            second.layout(first.measuredWidth + barPx, 0, w, h)
        } else {
            first.layout(0, 0, w, first.measuredHeight)
            second.layout(0, first.measuredHeight + barPx, w, h)
        }
    }

    /** The bar's bounds, in this view's pixels. */
    private fun bar(out: android.graphics.Rect) {
        val first = getChildAt(0)
        if (sideBySide) out.set(first.right, 0, first.right + barPx, height) else out.set(0, first.bottom, width, first.bottom + barPx)
    }

    override fun onDraw(canvas: Canvas) {
        if (!split) return
        val bar = android.graphics.Rect().also(::bar)
        paint.color = barColor
        canvas.drawRect(bar, paint)
        // A short pill in the middle of the bar says that it can be dragged.
        paint.color = handleColor
        val long = HANDLE_LENGTH_DP * density
        val thick = HANDLE_THICKNESS_DP * density
        if (sideBySide) {
            handle.set(bar.exactCenterX() - thick / 2, bar.exactCenterY() - long / 2, bar.exactCenterX() + thick / 2, bar.exactCenterY() + long / 2)
        } else {
            handle.set(bar.exactCenterX() - long / 2, bar.exactCenterY() - thick / 2, bar.exactCenterX() + long / 2, bar.exactCenterY() + thick / 2)
        }
        canvas.drawRoundRect(handle, thick / 2, thick / 2, paint)
    }

    private fun onBar(x: Float, y: Float): Boolean {
        if (!split) return false
        val bar = android.graphics.Rect().also(::bar)
        bar.inset(if (sideBySide) -(grabPx - barPx) / 2 else 0, if (sideBySide) 0 else -(grabPx - barPx) / 2)
        return bar.contains(x.toInt(), y.toInt())
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        event.actionMasked == MotionEvent.ACTION_DOWN && onBar(event.x, event.y)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!split) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = onBar(event.x, event.y)
                if (dragging) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    if (event.eventTime - lastTapMs < DOUBLE_TAP_MS) share = 0.5f
                    lastTapMs = event.eventTime
                }
                return dragging
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                val room = ((if (sideBySide) width else height) - barPx).coerceAtLeast(1)
                val along = if (sideBySide) event.x else event.y
                share = (along - barPx / 2f) / room
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = dragging
                dragging = false
                if (was && event.actionMasked == MotionEvent.ACTION_UP) {
                    if (share <= MIN_SHARE) onCollapse?.invoke(true) else if (share >= MAX_SHARE) onCollapse?.invoke(false)
                }
                return was
            }
        }
        return dragging
    }

    companion object {
        /** Letting go with the bar nearer an end than this closes the pane that was squeezed. */
        const val MIN_SHARE = 0.2f
        const val MAX_SHARE = 0.8f
        private const val DRAG_MIN = 0.06f
        private const val DRAG_MAX = 0.94f
        private const val BAR_DP = 14
        private const val GRAB_DP = 36
        private const val HANDLE_LENGTH_DP = 44
        private const val HANDLE_THICKNESS_DP = 4
        private const val DOUBLE_TAP_MS = 350L
    }
}
