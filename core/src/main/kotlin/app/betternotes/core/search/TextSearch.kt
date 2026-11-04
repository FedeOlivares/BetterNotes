package app.betternotes.core.search

import app.betternotes.core.geometry.Bounds
import java.text.Normalizer

/** One place a search found its text. A match that wraps across lines has several rectangles. */
class TextMatch(val pageId: Long, val rects: List<Bounds>)

/**
 * Text prepared for a search that ignores case, accents, ligatures and how a line happens to wrap, so
 * "camion" finds "Camión" and "ofﬁce" is found by "office". Holding only the folded form keeps a long
 * document's text cheap, and the map back to the original is built only for a page that has a hit.
 */
class SearchableText(val original: String) {
    private val folded = fold(original, null)

    /**
     * The ranges of the original text holding [foldedQuery], which must come from [foldQuery]. They run
     * from the first character of a match to just past its last, and never overlap.
     */
    fun find(foldedQuery: String): List<IntRange> {
        if (foldedQuery.isEmpty()) return emptyList()
        var at = folded.indexOf(foldedQuery)
        if (at < 0) return emptyList()
        val origins = IntArray(folded.length + 1)
        fold(original, origins)
        origins[folded.length] = original.length
        val found = ArrayList<IntRange>()
        while (at >= 0) {
            val end = at + foldedQuery.length
            // Several folded characters can share one original, as "f" and "i" share "ﬁ".
            var next = end
            while (next < folded.length && origins[next] == origins[end - 1]) next++
            found += origins[at] until origins[next]
            at = folded.indexOf(foldedQuery, end)
        }
        return found
    }

    companion object {
        /** The form a query must be in. Whitespace at its ends is dropped, as a user rarely means it. */
        fun foldQuery(query: String) = fold(query.trim(), null)

        /**
         * Lowercases, strips accents, expands ligatures and turns each run of whitespace into one space.
         * It also drops soft hyphens and a hyphen that ends a line, which PDF text is full of. If
         * [origins] is given, it receives the index in [text] that each output character came from.
         */
        private fun fold(text: String, origins: IntArray?): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == SOFT_HYPHEN || c == PDF_SOFT_HYPHEN -> i++
                    c == '-' && endsLine(text, i) -> i = skipWhitespace(text, i + 1)
                    c.isWhitespace() -> {
                        origins?.set(out.length, i)
                        out.append(' ')
                        i = skipWhitespace(text, i)
                    }
                    c.code < 0x80 -> {
                        origins?.set(out.length, i)
                        out.append(c.lowercaseChar())
                        i++
                    }
                    else -> {
                        val units = if (c.isHighSurrogate() && i + 1 < text.length) 2 else 1
                        appendFolded(text.substring(i, i + units), i, out, origins)
                        i += units
                    }
                }
            }
            return out.toString()
        }

        private fun appendFolded(piece: String, index: Int, out: StringBuilder, origins: IntArray?) {
            val decomposed = Normalizer.normalize(piece, Normalizer.Form.NFKD)
            for (d in decomposed) {
                if (Character.getType(d) == Character.NON_SPACING_MARK.toInt()) continue
                for (lower in d.lowercase()) {
                    origins?.set(out.length, index)
                    out.append(lower)
                }
            }
        }

        private fun endsLine(text: String, hyphen: Int): Boolean {
            if (hyphen == 0 || !text[hyphen - 1].isLetter()) return false
            var i = hyphen + 1
            var sawBreak = false
            while (i < text.length && text[i].isWhitespace()) {
                if (text[i] == '\n' || text[i] == '\r') sawBreak = true
                i++
            }
            return sawBreak && i < text.length && text[i].isLetter()
        }

        private fun skipWhitespace(text: String, from: Int): Int {
            var i = from
            while (i < text.length && text[i].isWhitespace()) i++
            return i
        }

        private const val SOFT_HYPHEN = '­'

        /** What PDFium reports for a hyphen it decided is only there for the line break. */
        private const val PDF_SOFT_HYPHEN = '￾'
    }
}
