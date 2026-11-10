package app.betternotes.canvas

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextStyle
import app.betternotes.fonts.FontLibrary

/**
 * The text of a text box with the look of each part marked on it, for Android's layouts. The whole text is marked
 * run by run, so the paint it is drawn with must have none of the look itself: plain size, regular, no line.
 */
internal fun spannedText(value: String, size: Float, style: TextStyle, spans: List<TextSpan>): CharSequence {
    val out = SpannableString(value)
    val edges = sortedSetOf(0, value.length)
    for (span in spans) {
        edges += span.start.coerceIn(0, value.length)
        edges += span.end.coerceIn(0, value.length)
    }
    val points = edges.toList()
    for (k in 0 until points.size - 1) {
        val from = points[k]
        val to = points[k + 1]
        val look = spans.firstOrNull { it.start <= from && to <= it.end }?.look
        val bold = look?.bold ?: style.bold
        val italic = look?.italic ?: style.italic
        val underline = look?.underline ?: style.underline
        val strike = look?.strikethrough ?: style.strikethrough
        val runSize = look?.size ?: size
        val face = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        // An added font is given its bold and italic face whole; a font that is missing leaves the app's own.
        val added = FontLibrary.face(look?.font ?: style.font, bold, italic)
        if (added != null) {
            out.setSpan(TypefaceSpan(added), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (face != Typeface.NORMAL) {
            out.setSpan(StyleSpan(face), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (underline) out.setSpan(UnderlineSpan(), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (strike) out.setSpan(StrikethroughSpan(), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (runSize != size && size > 0f) out.setSpan(RelativeSizeSpan(runSize / size), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return out
}
