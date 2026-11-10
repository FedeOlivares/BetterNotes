package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.SpanLook
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextSpans
import app.betternotes.core.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TextSpansTest {
    private val bold = SpanLook(bold = true)

    @Test
    fun `changing a range makes one span, and changing it back leaves none`() {
        val spans = TextSpans.change(emptyList(), 11, 6, 11) { it.copy(bold = true) }
        assertEquals(listOf(TextSpan(6, 11, bold)), spans)
        assertEquals(emptyList<TextSpan>(), TextSpans.change(spans, 11, 6, 11) { it.copy(bold = null) })
    }

    @Test
    fun `a range inside a span splits it`() {
        val spans = TextSpans.change(listOf(TextSpan(0, 10, bold)), 10, 3, 5) { it.copy(italic = true) }
        assertEquals(
            listOf(TextSpan(0, 3, bold), TextSpan(3, 5, SpanLook(bold = true, italic = true)), TextSpan(5, 10, bold)),
            spans,
        )
    }

    @Test
    fun `typed text looks like the letter before it`() {
        val spans = listOf(TextSpan(0, 5, bold))
        assertEquals(listOf(TextSpan(0, 8, bold)), TextSpans.retext(spans, "hello", "hello!!!"))
        assertEquals(listOf(TextSpan(0, 6, bold)), TextSpans.retext(spans, "hello", "heyllo"))
    }

    @Test
    fun `text after a deletion keeps its look`() {
        val spans = listOf(TextSpan(9, 14, bold))
        assertEquals(listOf(TextSpan(5, 10, bold)), TextSpans.retext(spans, "abc defg world", "abc  world"))
    }

    @Test
    fun `spans survive a save, and an older format drops them`() {
        val item = TextItem(
            id = 1L,
            frame = Frame(0f, 0f, 100f, 40f),
            text = "hello world",
            textSize = 14f,
            spans = listOf(TextSpan(0, 5, bold), TextSpan(6, 11, SpanLook(italic = true, strikethrough = false, size = 20f))),
        )
        assertEquals(listOf(item), PageCodec.decode(PageCodec.encode(listOf(item))))
        assertEquals(listOf(item.copy(spans = emptyList())), PageCodec.decode(PageCodec.encode(listOf(item), version = 6)))
    }

    @Test
    fun `fonts of the text and of its parts survive a save, and older formats drop them`() {
        val item = TextItem(
            id = 2L,
            frame = Frame(0f, 0f, 100f, 40f),
            text = "hello world",
            textSize = 14f,
            style = TextStyle(bold = true, font = "Lobster-Regular"),
            spans = listOf(TextSpan(0, 5, SpanLook(font = "")), TextSpan(6, 11, SpanLook(italic = true, font = "Mono"))),
        )
        assertEquals(listOf(item), PageCodec.decode(PageCodec.encode(listOf(item))))
        val v7 = PageCodec.decode(PageCodec.encode(listOf(item), version = 7)).single() as TextItem
        assertEquals("", v7.style.font)
        assertEquals(listOf(TextSpan(6, 11, SpanLook(italic = true))), v7.spans)
        val v6 = PageCodec.decode(PageCodec.encode(listOf(item), version = 6)).single() as TextItem
        assertEquals(item.style.copy(font = ""), v6.style)
        assertEquals(emptyList<TextSpan>(), v6.spans)
    }

    @Test
    fun `a font alone is a look, and the same font as the text is no look`() {
        val spans = TextSpans.change(emptyList(), 5, 0, 5) { it.copy(font = "Mono") }
        assertEquals(listOf(TextSpan(0, 5, SpanLook(font = "Mono"))), spans)
        assertEquals(emptyList<TextSpan>(), TextSpans.change(spans, 5, 0, 5) { it.copy(font = null) })
    }
}
