package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListMarkers
import app.betternotes.core.model.ListStyle
import app.betternotes.core.model.SpanLook
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListStyleTest {
    @Test
    fun `markers count as written lists do`() {
        assertEquals(listOf("A)", "B)", "Z)", "AA)", "AB)"), listOf(0, 1, 25, 26, 27).map { ListMarkers.label(ListKind.UPPER_PAREN, it) })
        assertEquals(listOf("a)", "ab)"), listOf(0, 27).map { ListMarkers.label(ListKind.LOWER_PAREN, it) })
        assertEquals(listOf("1)", "10)"), listOf(0, 9).map { ListMarkers.label(ListKind.NUMBER_PAREN, it) })
        assertEquals(listOf("i)", "iv)", "ix)", "xiv)", "mcmxciv)"), listOf(0, 3, 8, 13, 1993).map { ListMarkers.label(ListKind.ROMAN_PAREN, it) })
        assertEquals("3.", ListMarkers.label(ListKind.NUMBER_DOT, 2))
        assertEquals("-", ListMarkers.label(ListKind.DASH, 7))
        assertNull(ListMarkers.label(ListKind.DOT, 0))
    }

    @Test
    fun `an empty text is one paragraph`() {
        assertEquals(1, ListMarkers.paragraphs(""))
        assertEquals(3, ListMarkers.paragraphs("a\n\nb"))
    }

    @Test
    fun `a dot's opacity goes into its color and its size is kept inside its limits`() {
        val dot = ListStyle(ListKind.DOT, dotColor = 0xFF336699.toInt(), dotOpacity = 0.5f)
        assertEquals(0x80336699.toInt(), dot.dotArgb)
        assertEquals(ListStyle.MAX_DOT_SIZE, dot.copy(dotSize = 9f).clamped().dotSize, 0f)
        assertEquals(ListStyle.MIN_DOT_SIZE, dot.copy(dotSize = 0f).clamped().dotSize, 0f)
    }

    @Test
    fun `a list survives a save, and an older format shows a plain text`() {
        val list = ListStyle(ListKind.DOT, 0xFFD10101.toInt(), 0.6f, 0.25f)
        val item = TextItem(
            id = 3L,
            frame = Frame(0f, 0f, 100f, 40f),
            text = "one\ntwo",
            textSize = 14f,
            style = TextStyle(bold = true, font = "Mono", list = list),
            spans = listOf(TextSpan(0, 3, SpanLook(italic = true, font = "Lobster"))),
        )
        assertEquals(listOf(item), PageCodec.decode(PageCodec.encode(listOf(item))))
        val plain = item.copy(style = item.style.copy(list = null))
        assertEquals(listOf(plain), PageCodec.decode(PageCodec.encode(listOf(plain))))
        val old = PageCodec.decode(PageCodec.encode(listOf(item), version = 8)).single() as TextItem
        assertNull(old.style.list)
        assertEquals("Mono", old.style.font)
        val bare = item.copy(style = TextStyle(list = ListStyle(ListKind.DASH)), spans = emptyList())
        assertEquals(listOf(bare), PageCodec.decode(PageCodec.encode(listOf(bare))))
    }
}
