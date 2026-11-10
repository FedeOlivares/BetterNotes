package app.betternotes.canvas

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.TextItem
import app.betternotes.fonts.FontLibrary
import kotlin.math.roundToInt

/** How tall a text box must be to hold its text at its width, laid out as the page paints it. Used on the main thread only. */
internal object TextFit {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** The height in points that the text of [item] takes when wrapped at the width of its frame, at least one line. */
    fun height(item: TextItem): Float {
        val scale = ItemPainter.TEXT_LAYOUT_SCALE
        val style = item.style
        val spanned = item.spans.isNotEmpty()
        paint.textSize = item.textSize * scale
        paint.isUnderlineText = false
        paint.isStrikeThruText = false
        paint.typeface = when {
            spanned -> Typeface.DEFAULT
            else -> FontLibrary.face(style.font, style.bold, style.italic) ?: when {
                style.bold && style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                style.bold -> Typeface.DEFAULT_BOLD
                style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }
        val value = item.text.ifEmpty { " " }
        val shown = if (spanned) spannedText(value, item.textSize * scale, style, item.spans.map { it.scaled(scale) }) else value
        val layout = StaticLayout.Builder
            .obtain(shown, 0, value.length, paint, (item.frame.width * scale).roundToInt().coerceAtLeast(1))
            .setAlignment(
                when (style.align) {
                    HorizontalAlign.START -> Layout.Alignment.ALIGN_NORMAL
                    HorizontalAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    HorizontalAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
                },
            )
            .build()
        return layout.height / scale
    }

    /** [item] with its frame as tall as its text. */
    fun fitted(item: TextItem): TextItem = item.copy(frame = item.frame.copy(height = height(item)))
}
