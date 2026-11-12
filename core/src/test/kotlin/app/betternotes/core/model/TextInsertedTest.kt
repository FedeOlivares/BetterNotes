package app.betternotes.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextInsertedTest {
    @Test
    fun `letters typed at the end, the start and the middle are found`() {
        assertEquals(5 until 8, TextSpans.inserted("hello", "helloabc"))
        assertEquals(0 until 3, TextSpans.inserted("hello", "abchello"))
        assertEquals(2 until 4, TextSpans.inserted("held", "hexxld"))
    }

    @Test
    fun `a letter typed next to the same letter is found as one letter`() {
        assertEquals(1, TextSpans.inserted("hello", "helllo")?.count())
    }

    @Test
    fun `a text that was cut or left as it was adds nothing`() {
        assertNull(TextSpans.inserted("hello", "hello"))
        assertNull(TextSpans.inserted("hello", "hell"))
        assertNull(TextSpans.inserted("hello", ""))
    }

    @Test
    fun `typing into an empty text finds all of it`() {
        assertEquals(0 until 3, TextSpans.inserted("", "abc"))
    }
}
