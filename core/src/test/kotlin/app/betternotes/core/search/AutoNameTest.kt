package app.betternotes.core.search

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoNameTest {
    private val pages = listOf(Page(1, PageSize.A4), Page(2, PageSize.A4))

    private fun line(top: Float, text: String, source: TextSource = TextSource.HANDWRITING, left: Float = 0f) =
        IndexedLine(top.toLong() * 1000 + left.toLong(), source, Bounds(left, top, left + 50f, top + 10f), listOf(text, "other guess"))

    private fun index(first: List<IndexedLine>, second: List<IndexedLine> = emptyList()) = SearchIndex(
        "t",
        mapOf(1L to IndexedPage(0, true, first), 2L to IndexedPage(0, true, second)),
    )

    @Test
    fun `the name is the topmost line and uses the best guess`() {
        val name = suggestTitle(index(listOf(line(200f, "lower line"), line(20f, "medicina local"))), pages)
        assertEquals("Medicina local", name)
    }

    @Test
    fun `it keeps only the first words and cuts at a word`() {
        val long = "uno dos tres cuatro cinco seis siete ocho"
        assertEquals("Uno dos tres cuatro cinco seis", suggestTitle(index(listOf(line(1f, long))), pages))
        val wide = "internacionalizacion extraordinaria enciclopedica desproporcionada"
        val name = suggestTitle(index(listOf(line(1f, wide))), pages)!!
        assertTrue(name.length <= 40)
        assertFalse(name.endsWith(" "))
    }

    @Test
    fun `punctuation at the ends goes and short scribbles are skipped`() {
        assertEquals("Hola mundo", suggestTitle(index(listOf(line(1f, "- hola mundo."))), pages))
        assertEquals("Segunda linea", suggestTitle(index(listOf(line(1f, "x"), line(30f, "segunda linea"))), pages))
    }

    @Test
    fun `it falls to a later page when the first has no text`() {
        assertEquals("Pagina dos", suggestTitle(index(emptyList(), listOf(line(5f, "pagina dos"))), pages))
        assertNull(suggestTitle(index(emptyList()), pages))
    }

    @Test
    fun `typed text counts and printed pdf text does not`() {
        assertEquals("Tipeado", suggestTitle(index(listOf(line(5f, "tipeado", TextSource.TYPED))), pages))
        assertNull(suggestTitle(index(listOf(line(5f, "texto impreso del pdf", TextSource.PRINTED))), pages))
    }

    @Test
    fun `only a title the app gave may be replaced`() {
        val defaults = listOf("New note 2026-11-04", "Nueva nota 2026-11-04")
        assertTrue(isPlaceholderTitle("New note 2026-11-04", defaults))
        assertTrue(isPlaceholderTitle("  nueva nota 2026-11-04 ", defaults))
        assertTrue(isPlaceholderTitle("", defaults))
        assertFalse(isPlaceholderTitle("Mi clase de anatomia", defaults))
    }
}
