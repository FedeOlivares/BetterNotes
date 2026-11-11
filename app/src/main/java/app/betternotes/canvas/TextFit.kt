package app.betternotes.canvas

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextReach
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextStyle
import app.betternotes.fonts.FontLibrary
import kotlin.math.roundToInt

/** How tall a text box must be to hold its text at its width, laid out as the page paints it. Safe to call from any thread. */
internal object TextFit {
    /** Tells the core how tall a text lays out, so that the area a text box paints is known. Call once at start. */
    fun install() {
        TextReach.layoutHeight = { text, size, style, spans, width -> height(text, size, style, spans, width) }
    }

    /** The height in points that the text of [item] takes when wrapped at the width of its frame, at least one line. */
    fun height(item: TextItem): Float = height(item.text.ifEmpty { " " }, item.textSize, item.style, item.spans, item.frame.width)

    fun height(text: String, size: Float, style: TextStyle, spans: List<TextSpan>, width: Float): Float {
        val scale = ItemPainter.TEXT_LAYOUT_SCALE
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val spanned = spans.isNotEmpty()
        paint.textSize = size * scale
        paint.typeface = when {
            spanned -> Typeface.DEFAULT
            else -> FontLibrary.face(style.font, style.bold, style.italic) ?: when {
                style.bold && style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                style.bold -> Typeface.DEFAULT_BOLD
                style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }
        val wide = (width * scale).roundToInt().coerceAtLeast(1)
        val plain = if (spanned) spannedText(text, size * scale, style, spans.map { it.scaled(scale) }) else text
        val shown = withListMarks(plain, style, 0, size * scale, paint, wide)
        val layout = StaticLayout.Builder
            .obtain(shown, 0, text.length, paint, wide)
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
