package app.betternotes.core.search

import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.stroke
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

/** A letter-sized stroke with its top-left at x, y. */
private fun letter(id: Long, x: Float, y: Float, height: Float = 20f) = stroke(id, x, y, x + 10f, y + height)

class InkLinesTest {
    @Test
    fun `strokes on two levels make two lines in reading order`() {
        val lines = groupInkLines(
            listOf(
                letter(1, 0f, 100f), letter(2, 15f, 100f),
                letter(3, 0f, 10f), letter(4, 15f, 12f), letter(5, 30f, 9f),
            ),
        )
        assertEquals(listOf(3, 2), lines.map { it.strokes.size })
        assertTrue(lines[0].bounds.top < lines[1].bounds.top)
    }

    @Test
    fun `a line keeps the order the strokes were drawn in`() {
        val first = letter(1, 30f, 10f)
        val second = letter(2, 0f, 10f)
        assertEquals(listOf(first.points, second.points), groupInkLines(listOf(first, second)).single().strokes)
    }

    @Test
    fun `an accent joins the line below it and does not start one`() {
        val accent = stroke(9, 18f, 2f, 22f, 6f)
        val lines = groupInkLines(listOf(letter(1, 0f, 10f), letter(2, 15f, 10f), accent))
        assertEquals(3, lines.single().strokes.size)
    }

    @Test
    fun `a far away mark is dropped as noise`() {
        val lines = groupInkLines(listOf(letter(1, 0f, 10f), letter(2, 15f, 10f), stroke(9, 400f, 500f, 402f, 501f)))
        assertEquals(2, lines.single().strokes.size)
    }

    @Test
    fun `a wide gap separates columns`() {
        val lines = groupInkLines(listOf(letter(1, 0f, 10f), letter(2, 400f, 10f)))
        assertEquals(2, lines.size)
    }

    @Test
    fun `highlighter is ignored`() {
        val marker = StrokeItem(9, InkStyle(tool = InkTool.HIGHLIGHTER), letter(9, 0f, 10f).points)
        assertTrue(groupInkLines(listOf(marker)).isEmpty())
    }

    @Test
    fun `a stroke far taller than the rest is left out as a drawing`() {
        val lines = groupInkLines(listOf(letter(1, 0f, 10f), letter(2, 15f, 10f), letter(3, 30f, 10f), letter(4, 45f, 0f, height = 400f)))
        assertEquals(3, lines.single().strokes.size)
    }

    @Test
    fun `the key follows the strokes and ignores where they are`() {
        val a = letter(1, 0f, 10f)
        val b = letter(2, 15f, 10f)
        val key = groupInkLines(listOf(a, b)).single().key
        assertEquals(key, groupInkLines(listOf(letter(1, 300f, 300f), letter(2, 315f, 300f))).single().key)
        assertFalse(key == groupInkLines(listOf(a, letter(3, 15f, 10f))).single().key)
    }
}

class SearchIndexTest {
    private val page = Page(1, PageSize.A4)

    private fun index(vararg lines: IndexedLine) = SearchIndex("test", mapOf(1L to IndexedPage(0, true, lines.toList())))

    private fun ink(vararg readings: String, top: Float = 0f) =
        IndexedLine(top.toLong(), TextSource.HANDWRITING, Bounds(0f, top, 10f, top + 10f), readings.toList())

    private fun typed(text: String) = IndexedLine(1, TextSource.TYPED, Bounds(0f, 0f, 1f, 1f), listOf(text))

    @Test
    fun `case and accents do not matter`() {
        val index = index(ink("El Año Nuevo"))
        assertEquals(1, index.search("año", listOf(page)).size)
        assertEquals(1, index.search("ANO", listOf(page)).size)
        assertEquals(1, index.search("nuévo", listOf(page)).size)
        assertTrue(index.search("ano", listOf(page))[0].exact)
    }

    @Test
    fun `any reading can match and the matching one is returned`() {
        val hit = index(ink("hala mundo", "hola mundo")).search("hola", listOf(page)).single()
        assertEquals("hola mundo", hit.text)
        assertTrue(hit.exact)
    }

    @Test
    fun `an exact reading beats an earlier close one`() {
        val hit = index(ink("casas", "casa")).search("casa", listOf(page)).single()
        assertTrue(hit.exact)
    }

    @Test
    fun `handwriting tolerates a misread letter and typed text does not`() {
        val hit = index(ink("mañana")).search("manama", listOf(page)).single()
        assertFalse(hit.exact)
        assertTrue(index(typed("mañana")).search("manama", listOf(page)).isEmpty())
    }

    @Test
    fun `short words need an exact match`() {
        assertTrue(index(ink("sol")).search("sal", listOf(page)).isEmpty())
    }

    @Test
    fun `every word of the query must be on the line`() {
        val index = index(ink("hello big world"))
        assertEquals(1, index.search("world hello", listOf(page)).size)
        assertTrue(index.search("hello mars", listOf(page)).isEmpty())
    }

    @Test
    fun `a word run together with the next is still found`() {
        assertEquals(1, index(ink("holamundo")).search("mundo", listOf(page)).size)
    }

    @Test
    fun `an empty query finds nothing`() {
        assertTrue(index(ink("hello")).search("  ¿? ", listOf(page)).isEmpty())
    }

    @Test
    fun `hits come in page order and then top to bottom`() {
        val a = Page(1, PageSize.A4)
        val b = Page(2, PageSize.A4)
        val index = SearchIndex(
            "test",
            mapOf(
                1L to IndexedPage(0, true, listOf(ink("word", top = 50f), ink("word", top = 5f))),
                2L to IndexedPage(0, true, listOf(ink("word", top = 1f))),
            ),
        )
        val hits = index.search("word", listOf(b, a))
        assertEquals(listOf(2L, 1L, 1L), hits.map { it.pageId })
        assertEquals(listOf(0, 1, 1), hits.map { it.pageIndex })
        assertEquals(listOf(1f, 5f, 50f), hits.map { it.bounds.top })
    }

    @Test
    fun `edit distance counts a swap as one and gives up over the limit`() {
        assertEquals(0, editDistance("casa", "casa", 2))
        assertEquals(1, editDistance("casa", "cosa", 2))
        assertEquals(1, editDistance("casa", "acsa", 2))
        assertEquals(1, editDistance("casa", "cas", 2))
        assertEquals(2, editDistance("casa", "perro", 1))
        assertEquals(2, editDistance("a", "abcdef", 1))
    }
}

class SearchIndexerTest {
    private val dir = Files.createTempDirectory("search").toFile()
    private val store = DocumentStore(dir)
    private val page = Page(1, PageSize.A4)
    private val pages = listOf(page)

    private class FakeRecognizer(var ready: Boolean = true) : HandwritingRecognizer {
        override val engineId = "fake"
        override val isReady get() = ready
        var calls = 0
        var fail = false

        override fun recognize(line: InkLine): List<String> {
            calls++
            if (fail) throw IOException("no")
            return listOf("hola ${line.strokes.size}")
        }
    }

    private val recognizer = FakeRecognizer()
    private val indexer = SearchIndexer(store, recognizer)

    private fun write(vararg items: PageItem) = store.writePage(page.id, items.toList())

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `ink and typed text both end up searchable`() {
        val cell = TableItem(3, Frame(0f, 0f, 10f, 10f), listOf(1f), listOf(1f), listOf("celda"))
        write(letter(1, 0f, 10f), TextItem(2, Frame(0f, 100f, 50f, 20f), "Tipeado"), cell)
        val index = indexer.load()
        val update = indexer.update(index, pages)
        assertEquals(IndexUpdate(1, 0, false), update)
        assertEquals(TextSource.HANDWRITING, index.search("hola", pages).single().source)
        assertEquals(TextSource.TYPED, index.search("tipeado", pages).single().source)
        assertEquals(1, index.search("celda", pages).size)
    }

    @Test
    fun `an unchanged page is skipped`() {
        write(letter(1, 0f, 10f))
        val index = indexer.load()
        indexer.update(index, pages)
        assertEquals(IndexUpdate(0, 0, false), indexer.update(index, pages))
        assertEquals(1, recognizer.calls)
    }

    @Test
    fun `only the line that changed is recognized again`() {
        write(letter(1, 0f, 10f), letter(2, 0f, 100f))
        val index = indexer.load()
        indexer.update(index, pages)
        assertEquals(2, recognizer.calls)

        write(letter(1, 0f, 10f), letter(2, 0f, 100f), letter(3, 15f, 100f))
        assertEquals(1, indexer.update(index, pages).changedPages)
        assertEquals(3, recognizer.calls)
    }

    @Test
    fun `without models ink waits and typed text does not`() {
        recognizer.ready = false
        write(letter(1, 0f, 10f), TextItem(2, Frame(0f, 100f, 50f, 20f), "Tipeado"))
        val index = indexer.load()
        assertEquals(1, indexer.update(index, pages).pendingLines)
        assertEquals(0, recognizer.calls)
        assertEquals(1, index.search("tipeado", pages).size)
        assertTrue(index.search("hola", pages).isEmpty())

        recognizer.ready = true
        assertEquals(IndexUpdate(1, 0, false), indexer.update(index, pages))
        assertEquals(1, index.search("hola", pages).size)
    }

    @Test
    fun `a failed recognition is retried on the next update`() {
        write(letter(1, 0f, 10f))
        val index = indexer.load()
        recognizer.fail = true
        assertEquals(1, indexer.update(index, pages).pendingLines)
        recognizer.fail = false
        assertEquals(0, indexer.update(index, pages).pendingLines)
        assertEquals(1, index.search("hola", pages).size)
    }

    @Test
    fun `a removed page leaves the index`() {
        write(letter(1, 0f, 10f))
        val index = indexer.load()
        indexer.update(index, pages)
        indexer.update(index, emptyList())
        assertTrue(index.indexedPages.isEmpty())
    }

    @Test
    fun `cancelling stops before the next page and the next update carries on`() {
        write(letter(1, 0f, 10f))
        val index = indexer.load()
        assertTrue(indexer.update(index, pages) { true }.cancelled)
        assertEquals(0, recognizer.calls)
        assertFalse(indexer.update(index, pages).cancelled)
        assertEquals(1, recognizer.calls)
    }

    @Test
    fun `a sticky note's contents point at the note`() {
        val inside = TextItem(5, Frame(0f, 0f, 10f, 10f), "adentro")
        val note = StickyNoteItem(4, Frame(200f, 200f, 100f, 100f), 0, items = listOf(inside, letter(6, 1f, 1f)))
        write(note)
        val index = indexer.load()
        indexer.update(index, pages)
        assertEquals(note.bounds, index.search("adentro", pages).single().bounds)
        assertEquals(note.bounds, index.search("hola", pages).single().bounds)
    }

    @Test
    fun `the index survives a restart and is not recognized again`() {
        write(letter(1, 0f, 10f))
        val index = indexer.load()
        indexer.update(index, pages)
        indexer.save(index)

        val reloaded = indexer.load()
        assertEquals(1, reloaded.search("hola", pages).size)
        indexer.update(reloaded, pages)
        assertEquals(1, recognizer.calls)
    }

    @Test
    fun `an index from another engine or a damaged file starts over`() {
        write(letter(1, 0f, 10f))
        val index = indexer.load()
        indexer.update(index, pages)
        indexer.save(index)

        val other = SearchIndexer(store, object : HandwritingRecognizer {
            override val engineId = "other"
            override val isReady = true
            override fun recognize(line: InkLine) = listOf("otro")
        })
        assertTrue(other.load().indexedPages.isEmpty())

        dir.resolve("search.bni").writeBytes(ByteArray(30))
        assertTrue(indexer.load().indexedPages.isEmpty())
    }
}
