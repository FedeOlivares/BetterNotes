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
        val origins = IntArray(folded.length)
        fold(original, origins)
        val found = ArrayList<IntRange>()
        while (at >= 0) {
            val end = at + foldedQuery.length
            // The last folded character gives the last original one, whatever it expanded to.
            val last = origins[end - 1]
            found += origins[at]..if (original[last].isHighSurrogate()) last + 1 else last
            at = folded.indexOf(foldedQuery, end)
        }
        return found
    }

    companion object {
        /** The form a query must be in. */
        fun foldQuery(query: String) = fold(query, null)

        /**
         * Lowercases, strips accents and expands ligatures. It also drops all whitespace, soft hyphens,
         * and any hyphen that ends a line. PDF text wraps anywhere, and PDFium can report letter-spaced
         * text as one letter per line, so spaces are not reliable. The cost is that "a si" also finds
         * "así", which a search can live with. If [origins] is given, it receives the index in [text]
         * that each output character came from.
         */
        private fun fold(text: String, origins: IntArray?): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c.code == SOFT_HYPHEN || c.code == PDF_SOFT_HYPHEN || c.isWhitespace() -> i++
                    c == '-' && endsLine(text, i) -> i++
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

        private const val SOFT_HYPHEN = 0x00AD

        /** What PDFium reports for a hyphen it decided is only there for the line break. */
        private const val PDF_SOFT_HYPHEN = 0xFFFE
    }
}
