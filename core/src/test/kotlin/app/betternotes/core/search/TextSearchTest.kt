package app.betternotes.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    private fun find(text: String, query: String): List<String> =
        SearchableText(text).find(SearchableText.foldQuery(query)).map { text.substring(it) }

    @Test
    fun `case does not matter`() {
        assertEquals(listOf("HOLA", "hola"), find("HOLA y hola", "Hola"))
    }

    @Test
    fun `accents do not matter in either direction`() {
        assertEquals(listOf("Camión"), find("El Camión rojo", "camion"))
        assertEquals(listOf("camion"), find("El camion rojo", "CAMIÓN"))
        assertEquals(listOf("Niño"), find("Un Niño", "nino"))
    }

    @Test
    fun `a ligature is found by its letters and reported whole`() {
        assertEquals(listOf("oﬃce"), find("the oﬃce door", "office"))
        assertEquals(listOf("oﬃ"), find("the oﬃce door", "off"))
    }

    @Test
    fun `whitespace and line wraps match a single space`() {
        assertEquals(listOf("two\r\n  words"), find("say two\r\n  words now", "two words"))
    }

    @Test
    fun `a query's own spacing at the ends is ignored`() {
        assertEquals(listOf("word"), find("a word here", "  word "))
    }

    @Test
    fun `a hyphen that only breaks the line disappears`() {
        assertEquals(listOf("hyphen-\nated"), find("is hyphen-\nated here", "hyphenated"))
    }

    @Test
    fun `a real hyphen stays`() {
        assertEquals(listOf("well-known"), find("a well-known fact", "well-known"))
        assertTrue(find("a well-known fact", "wellknown").isEmpty())
    }

    @Test
    fun `soft hyphens disappear`() {
        assertEquals(listOf("pala­bra"), find("una pala­bra", "palabra"))
        assertEquals(listOf("pala￾bra"), find("una pala￾bra", "palabra"))
    }

    @Test
    fun `matches do not overlap`() {
        assertEquals(listOf("aa", "aa"), find("aaaaa", "aa"))
    }

    @Test
    fun `a match at the very end reaches the end`() {
        assertEquals(1, SearchableText("abc").find("bc").size)
        assertEquals(1..2, SearchableText("abc").find("bc").single())
    }

    @Test
    fun `an empty query finds nothing`() {
        assertTrue(find("abc", "   ").isEmpty())
    }

    @Test
    fun `no match returns nothing`() {
        assertTrue(find("abc", "xyz").isEmpty())
    }

    @Test
    fun `characters outside the basic plane keep their place`() {
        assertEquals(listOf("after"), find("😀 after", "after"))
    }
}
