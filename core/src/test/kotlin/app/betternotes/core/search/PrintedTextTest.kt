package app.betternotes.core.search

import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class AssemblePrintedLinesTest {
    private fun piece(text: String, left: Float, top: Float, right: Float) =
        PrintedLine(text, Bounds(left, top, right, top + 10f))

    private fun texts(vararg pieces: PrintedLine) = assemblePrintedLines(pieces.toList()).map { it.text }

    @Test
    fun `lines at different heights stay apart`() {
        assertEquals(listOf("uno", "dos"), texts(piece("uno", 0f, 0f, 30f), piece("dos", 0f, 20f, 30f)))
    }

    @Test
    fun `letters reported one per line become one word`() {
        val letters = "marca".mapIndexed { i, c -> piece(c.toString(), i * 6f, 0f, i * 6f + 6f) }
        assertEquals(listOf("marca"), assemblePrintedLines(letters).map { it.text })
    }

    @Test
    fun `a real gap on one baseline becomes a space`() {
        assertEquals(listOf("uno dos"), texts(piece("uno", 0f, 0f, 30f), piece("dos", 50f, 0f, 80f)))
    }

    @Test
    fun `pieces on one baseline share one rectangle`() {
        val line = assemblePrintedLines(listOf(piece("uno", 0f, 0f, 30f), piece("dos", 50f, 1f, 80f))).single()
        assertEquals(Bounds(0f, 0f, 80f, 11f), line.bounds)
    }

    @Test
    fun `a column to the left starts a new line even at the same height`() {
        assertEquals(listOf("derecha", "izquierda"), texts(piece("derecha", 300f, 0f, 400f), piece("izquierda", 0f, 0f, 100f)))
    }

    @Test
    fun `a word broken by a hyphen goes whole on its first line`() {
        assertEquals(
            listOf("una palabra", "partida en dos"),
            texts(piece("una pala-", 0f, 0f, 90f), piece("bra partida en dos", 0f, 20f, 90f)),
        )
    }

    @Test
    fun `a next line that was only the rest of the word disappears`() {
        assertEquals(listOf("una palabra"), texts(piece("una pala-", 0f, 0f, 90f), piece("bra", 0f, 20f, 30f)))
    }

    @Test
    fun `a hyphen followed by a number or at the end of the text stays`() {
        assertEquals(listOf("tipo-", "2 fin"), texts(piece("tipo-", 0f, 0f, 50f), piece("2 fin", 0f, 20f, 50f)))
        assertEquals(listOf("fin-"), texts(piece("fin-", 0f, 0f, 40f)))
    }

    @Test
    fun `blank pieces are dropped`() {
        assertEquals(listOf("uno"), texts(piece("  ", 0f, 0f, 10f), piece("uno", 0f, 20f, 30f)))
    }
}

class PrintedTextIndexTest {
    private val dir = Files.createTempDirectory("printed").toFile()
    private val store = DocumentStore(dir)
    private val page = Page(1, PageSize.A4, source = PageSource.PdfPage("a.pdf", 0))
    private val pages = listOf(page)

    private class NoInk : HandwritingRecognizer {
        override val engineId = "none"
        override val isReady = true
        override fun recognize(line: InkLine) = emptyList<String>()
    }

    private class FakePrinted : PrintedTextSource {
        var calls = 0
        var fail = false

        override fun linesOf(page: Page): List<PrintedLine> {
            calls++
            if (fail) throw IOException("no")
            return listOf(PrintedLine("El Camión ﬁno", Bounds(10f, 20f, 100f, 30f)))
        }
    }

    private val printed = FakePrinted()
    private val indexer = SearchIndexer(store, NoInk(), printed)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `printed text is found where it is, ignoring case, accents and ligatures`() {
        val index = indexer.load()
        indexer.update(index, pages)
        val hit = index.search("camion", pages).single()
        assertEquals(TextSource.PRINTED, hit.source)
        assertEquals(Bounds(10f, 20f, 100f, 30f), hit.bounds)
        assertEquals(1, index.search("fino", pages).size)
    }

    @Test
    fun `printed text does not tolerate wrong letters as handwriting does`() {
        val index = indexer.load()
        indexer.update(index, pages)
        assertTrue(index.search("camiom", pages).isEmpty())
    }

    @Test
    fun `printed text survives a save and is not read again when the page changes`() {
        val index = indexer.load()
        indexer.update(index, pages)
        indexer.save(index)
        assertEquals(1, printed.calls)

        val reloaded = indexer.load()
        assertEquals(1, reloaded.search("camion", pages).size)
        store.writePage(page.id, emptyList())
        assertEquals(1, indexer.update(reloaded, pages).changedPages)
        assertEquals(1, printed.calls)
        assertEquals(1, reloaded.search("camion", pages).size)
    }

    @Test
    fun `a page that could not be read stays pending and is tried again`() {
        printed.fail = true
        val index = indexer.load()
        assertEquals(1, indexer.update(index, pages).pendingLines)
        assertFalse(index.page(page.id)!!.complete)
        printed.fail = false
        assertEquals(0, indexer.update(index, pages).pendingLines)
        assertEquals(1, index.search("camion", pages).size)
    }
}
