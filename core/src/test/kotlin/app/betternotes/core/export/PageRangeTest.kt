package app.betternotes.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRangeTest {
    @Test
    fun `ranges and single pages read as indices from zero`() {
        assertEquals(listOf(1, 2, 3, 4, 7), parsePageRange("2-5, 8", 10))
        assertEquals(listOf(0), parsePageRange("1", 3))
        // A notebook whose cover is page 0: the cover is 0, the next page 1, and the last of 3 pages is 2.
        assertEquals(listOf(0, 1), parsePageRange("0-1", 3, firstNumber = 0))
        assertEquals(null, parsePageRange("3", 3, firstNumber = 0))
        assertEquals(null, parsePageRange("0", 3))
        assertEquals(listOf(0, 1, 2), parsePageRange(" 1 - 2 , 2-3 ", 3))
        assertEquals(listOf(4), parsePageRange("5; ", 5))
    }

    @Test
    fun `blank text selects nothing`() {
        assertEquals(emptyList<Int>(), parsePageRange("", 5))
        assertEquals(emptyList<Int>(), parsePageRange("   ", 5))
    }

    @Test
    fun `text that is not a range is refused, and so is a page that does not exist`() {
        assertNull(parsePageRange("abc", 5))
        assertNull(parsePageRange("2-", 5))
        assertNull(parsePageRange("5-2", 5))
        assertNull(parsePageRange("0", 5))
        assertNull(parsePageRange("1-9", 5))
        assertNull(parsePageRange("1-2-3", 5))
    }

    @Test
    fun `formatting gives the shortest text that reads back the same`() {
        assertEquals("1-3, 5, 7-8", formatPageRange(listOf(0, 1, 2, 4, 6, 7)))
        assertEquals("", formatPageRange(emptyList()))
        for (pages in listOf(listOf(0), listOf(2, 3, 9), (0..11).toList(), listOf(1, 3, 5))) {
            assertEquals(pages, parsePageRange(formatPageRange(pages), 12))
        }
    }
}
