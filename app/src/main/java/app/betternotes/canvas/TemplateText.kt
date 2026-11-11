package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import app.betternotes.fonts.FontLibrary

/** Draws the words on the built-in template pages. One paint is reused, so a page of labels allocates nothing. */
internal class TemplateText {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val heading = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val label = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    /** [font] is the name of a font of the font library that replaces the built-in face (null or one that is missing: the built-in one). */
    fun draw(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign, font: String? = null) {
        paint.color = color
        paint.textSize = size
        paint.typeface = font?.let { FontLibrary.face(it, bold = style != TextStyle.LABEL, italic = false) } ?: when (style) {
            TextStyle.TITLE -> title
            TextStyle.HEADING -> heading
            TextStyle.LABEL -> label
        }
        paint.letterSpacing = if (style == TextStyle.HEADING) HEADING_SPACING else 0f
        paint.textAlign = when (align) {
            TextAlign.CENTER -> Paint.Align.CENTER
            TextAlign.END -> Paint.Align.RIGHT
            TextAlign.START -> Paint.Align.LEFT
        }
        canvas.drawText(text, x, baseline, paint)
    }

    private companion object {
        /** Headings are set wide, as in the reference. In em. */
        const val HEADING_SPACING = 0.12f
    }
}
