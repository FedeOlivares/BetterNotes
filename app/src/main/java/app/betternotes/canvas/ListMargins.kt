package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.LeadingMarginSpan
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListMarkers
import app.betternotes.core.model.ListStyle
import app.betternotes.core.model.TextStyle
import kotlin.math.max
import kotlin.math.min

/**
 * The text of a list with a marker in the margin of each paragraph. A long paragraph wraps under its text, since the margin is the
 * same for every line. The margin lies inside the width the text is laid out in, so a list never paints outside its box.
 *
 * [shown] is laid out with [paint] at [size] (in the layout's own units, which is also the unit of [width]); [style] tells whether it is
 * a list at all, and [color] is the text's color, which the text markers are drawn in.
 */
internal fun withListMarks(shown: CharSequence, style: TextStyle, color: Int, size: Float, paint: Paint, width: Int): CharSequence {
    val list = style.list ?: return shown
    val out = shown as? Spannable ?: SpannableString(shown)
    val margin = ListMarginSpan.marginFor(list, ListMarkers.paragraphs(out), size, paint).coerceAtMost(width / 2)
    out.setSpan(ListMarginSpan(list, color, size, margin), 0, out.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    return out
}

/** The margin, in pixels, that holds the widest marker a text of [paragraphs] paragraphs can have, at [size] in [face]. */
internal fun listMargin(list: ListStyle, paragraphs: Int, size: Float, face: Typeface?): Int =
    ListMarginSpan.marginFor(list, paragraphs, size, Paint().apply { typeface = face })

/**
 * Draws the marker of paragraph [index] on [c], in a margin of [margin] pixels whose left edge is [left] and whose text starts at
 * [left] plus [margin]. [baseline] is the baseline of the paragraph's first line.
 */
internal fun drawListMark(
    c: Canvas, ink: Paint, list: ListStyle, color: Int, size: Float, face: Typeface?, index: Int,
    left: Float, margin: Int, baseline: Float,
) {
    val gap = size * ListMarginSpan.GAP
    val right = left + margin - gap
    val label = ListMarkers.label(list.kind, index)
    if (label == null) {
        ink.color = list.dotArgb
        val radius = min(size * list.dotSize / 2f, (margin - gap) / 2f).coerceAtLeast(0f)
        c.drawCircle(right - radius, baseline - size * ListMarginSpan.DOT_RISE, radius, ink)
    } else {
        ink.color = color
        ink.textSize = size
        ink.typeface = face
        c.drawText(label, max(right - ink.measureText(label), left), baseline, ink)
    }
}

/** Draws the marker of a paragraph at its first line. The number of the paragraph is the line breaks before it. */
private class ListMarginSpan(
    private val list: ListStyle,
    private val color: Int,
    private val size: Float,
    private val margin: Int,
) : LeadingMarginSpan {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getLeadingMargin(first: Boolean) = margin

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) {
        if (!first || dir < 0) return
        var index = 0
        for (i in 0 until min(start, text.length)) if (text[i] == '\n') index++
        drawListMark(c, ink, list, color, size, p.typeface, index, x.toFloat(), margin, baseline.toFloat())
    }

    companion object {
        /** The space between a marker and its text, as a share of the text size. */
        const val GAP = 0.35f

        /** How far above the baseline the middle of a dot lies, as a share of the text size: about half the height of a lower-case letter. */
        const val DOT_RISE = 0.3f

        /** The margin that holds the widest marker a text of [paragraphs] paragraphs can have. */
        fun marginFor(list: ListStyle, paragraphs: Int, size: Float, paint: Paint): Int {
            val probe = Paint(paint).apply { textSize = size }
            val widest = if (list.kind == ListKind.DOT) {
                size * list.dotSize
            } else {
                var most = 0f
                for (i in 0 until paragraphs) most = max(most, probe.measureText(ListMarkers.label(list.kind, i).orEmpty()))
                most
            }
            return Math.ceil((widest + size * GAP * 2f).toDouble()).toInt()
        }
    }
}
