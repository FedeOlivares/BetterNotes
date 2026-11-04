package app.betternotes.pdf

import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.ParcelFileDescriptor
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageSource
import app.betternotes.core.search.PrintedLine
import app.betternotes.core.search.PrintedTextSource
import app.betternotes.core.search.assemblePrintedLines
import java.io.IOException

/**
 * Reads a PDF page's text for the search index, line by line with where each line is. Use it from one
 * thread, which is free to block: the platform serializes all PDF work across its renderers.
 */
class PdfPrintedText(private val store: DocumentStore) : PrintedTextSource, AutoCloseable {
    private val renderers = HashMap<String, PdfRenderer>()

    override fun linesOf(page: Page): List<PrintedLine> {
        val source = page.source as? PageSource.PdfPage ?: return emptyList()
        try {
            return renderer(source.assetId).openPage(source.pageIndex).use { pdfPage ->
                val text = pdfPage.textContents.joinToString("\n") { it.text.orEmpty() }
                assemblePrintedLines(pieces(pdfPage, text))
            }
        } catch (e: IllegalArgumentException) {
            throw IOException("Could not read the text of $source", e)
        }
    }

    /** Asks where each stretch of text between line breaks sits. Indices are those of the page's text. */
    private fun pieces(page: PdfRenderer.Page, text: String): List<PrintedLine> {
        val pieces = ArrayList<PrintedLine>()
        var start = 0
        while (start < text.length) {
            var end = start
            while (end < text.length && !isLineBreak(text[end])) end++
            if (end > start) {
                val selected = page.selectContent(SelectionBoundary(start), SelectionBoundary(end))
                val rects = selected?.selectedTextContents.orEmpty().flatMap { it.bounds }
                if (rects.isNotEmpty()) {
                    val box = rects
                        .map { Bounds(it.left, it.top, it.right, it.bottom) }
                        .reduce(Bounds::union)
                    pieces += PrintedLine(text.substring(start, end), box)
                }
            }
            start = end + 1
        }
        return pieces
    }

    private fun isLineBreak(c: Char) = c.code == LINE_FEED || c.code == CARRIAGE_RETURN

    private fun renderer(assetId: String) = renderers.getOrPut(assetId) {
        val file = store.assetFile(assetId)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    }

    override fun close() {
        renderers.values.forEach(PdfRenderer::close)
        renderers.clear()
    }

    private companion object {
        const val LINE_FEED = 0x0A
        const val CARRIAGE_RETURN = 0x0D
    }
}
