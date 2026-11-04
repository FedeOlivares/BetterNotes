package app.betternotes.core.search

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import java.io.IOException

/** A line of text printed on a page, in the page's own points. */
class PrintedLine(val text: String, val bounds: Bounds)

/** Reads what a page has printed on it, such as a PDF's text, for the search index. */
fun interface PrintedTextSource {
    /** Blocks. A page with no printed text has no lines. */
    @Throws(IOException::class)
    fun linesOf(page: Page): List<PrintedLine>

    companion object {
        val None = PrintedTextSource { emptyList() }
    }
}

/**
 * Turns the pieces a PDF engine reports into lines a search can use, given in reading order.
 *
 * Pieces on the same baseline become one line, because a PDF can report letter-spaced text as one letter
 * per line. And a word broken by a hyphen at the end of a line goes whole on the line where it starts.
 */
fun assemblePrintedLines(pieces: List<PrintedLine>): List<PrintedLine> {
    val lines = ArrayList<PrintedLine>()
    for (piece in pieces) {
        if (piece.text.isBlank()) continue
        val last = lines.lastOrNull()
        if (last != null && sameBaseline(last.bounds, piece.bounds)) {
            val gap = piece.bounds.left - last.bounds.right
            val joiner = if (gap > piece.bounds.height * WORD_GAP_OF_HEIGHT) " " else ""
            lines[lines.lastIndex] = PrintedLine(last.text + joiner + piece.text, last.bounds.union(piece.bounds))
        } else {
            lines += piece
        }
    }
    return joinHyphenatedWords(lines)
}

private fun sameBaseline(a: Bounds, b: Bounds): Boolean {
    val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    return b.left >= a.left && overlap > minOf(a.height, b.height) * SAME_LINE_OVERLAP
}

private fun joinHyphenatedWords(lines: List<PrintedLine>): List<PrintedLine> {
    val joined = ArrayList<PrintedLine>(lines.size)
    var carried: String? = null
    lines.forEachIndexed { index, line ->
        var text = line.text.trim()
        if (carried != null) {
            // What is left of this line once its first word moved up. Its rectangle stays the same.
            text = text.substringAfter(' ', "")
            carried = null
        }
        val next = lines.getOrNull(index + 1)?.text?.trim()
        if (next != null && text.length > 1 && text.last() == '-' && text[text.lastIndex - 1].isLetter() && next.first().isLetter()) {
            text = text.dropLast(1) + next.substringBefore(' ')
            carried = next
        }
        if (text.isNotEmpty()) joined += PrintedLine(text, line.bounds)
    }
    return joined
}

/** How much of the shorter piece must overlap the other, for two pieces to share a baseline. */
private const val SAME_LINE_OVERLAP = 0.5f

/** A gap wider than this share of the text's height is taken for a space. */
private const val WORD_GAP_OF_HEIGHT = 0.15f
