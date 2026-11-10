package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle

/** Draws the words on the built-in template pages. One paint is reused, so a page of labels allocates nothing. */
internal class TemplateText {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val heading = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val label = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    fun draw(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) {
        paint.color = color
        paint.textSize = size
        paint.typeface = when (style) {
            TextStyle.TITLE -> title
            TextStyle.HEADING -> heading
            TextStyle.LABEL -> label
        }
        paint.letterSpacing = if (style == TextStyle.HEADING) HEADING_SPACING else 0f
        paint.textAlign = if (align == TextAlign.CENTER) Paint.Align.CENTER else Paint.Align.LEFT
        canvas.drawText(text, x, baseline, paint)
    }

    private companion object {
        /** Headings are set wide, as in the reference. In em. */
        const val HEADING_SPACING = 0.12f
    }
}
