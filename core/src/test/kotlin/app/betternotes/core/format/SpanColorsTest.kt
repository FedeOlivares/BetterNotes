package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageLayers
import app.betternotes.core.model.SpanLook
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SpanColorsTest {
    private val frame = Frame(1f, 2f, 300f, 40f)
    private val red = 0xFFD10101.toInt()

    @Test
    fun `the color of single letters survives a round trip, with a background, a list and other looks`() {
        val spans = listOf(TextSpan(0, 3, SpanLook(color = red)), TextSpan(4, 6, SpanLook(bold = true, size = 20f, font = "Mono", color = 0xFF2F5BEA.toInt())))
        val style = TextStyle(font = "Mono", backgroundColor = 0x80F5C400.toInt(), list = ListStyle(ListKind.DOT, 0xFFD10101.toInt(), 0.6f, 0.25f))
        for (item in listOf(
            TextItem(1, frame, "red and blue", 14f, style = style, spans = spans),
            TextItem(2, frame, "red", 14f, spans = listOf(TextSpan(0, 3, SpanLook(color = red)))),
        )) {
            assertEquals(listOf<PageItem>(item), PageCodec.decode(PageCodec.encode(listOf(item))))
        }
    }

    @Test
    fun `only a page with colored letters is written at format 17, and older text boxes keep their bytes`() {
        val plain = TextItem(1, frame, "plain", 12f, spans = listOf(TextSpan(0, 2, SpanLook(bold = true))))
        val backed = plain.copy(id = 2, style = TextStyle(backgroundColor = 0xFF000000.toInt()))
        val lettered = plain.copy(id = 3, spans = listOf(TextSpan(0, 2, SpanLook(color = red))))
        assertEquals(10, PageCodec.encode(listOf(plain))[4].toInt())
        assertEquals(16, PageCodec.encode(listOf(plain, backed))[4].toInt())
        assertEquals(17, PageCodec.encode(listOf(plain, backed, lettered))[4].toInt())
    }

    @Test
    fun `a layer table matches the items of a page with colored letters, also inside a post-it`() {
        val lettered = TextItem(1, frame, "ab", 12f, spans = listOf(TextSpan(0, 1, SpanLook(color = red))))
        val note = StickyNoteItem(2, frame, 0xFFFBDD8C.toInt(), true, 100f, 100f, items = listOf(lettered.copy(id = 3)))
        val two = PageLayers.Single.withNewLayerAbove(PageLayers.Single.all[0].id)
        for ((items, layers) in listOf(listOf<PageItem>(lettered) to PageLayers.Single, listOf<PageItem>(note) to PageLayers.Single, listOf<PageItem>(lettered.onLayer(two.all[1].id)) to two)) {
            val back = PageCodec.decodePage(PageCodec.encode(items, layers = layers))
            assertEquals(items.size, back.items.size)
            assertEquals(layers.size, back.layers.size)
        }
    }

    @Test
    fun `a letter color asked to be written in an older format is kept by writing the newer one`() {
        val item = TextItem(1, frame, "abc", 12f, spans = listOf(TextSpan(0, 2, SpanLook(color = red))))
        assertEquals(listOf<PageItem>(item), PageCodec.decode(PageCodec.encode(listOf(item), version = 8)))
    }
}
