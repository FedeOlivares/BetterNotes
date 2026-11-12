package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageLayers
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.SpanLook
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextColorsTest {
    private val frame = Frame(1f, 2f, 300f, 40f)

    @Test
    fun `a background survives a round trip, with a list and parts`() {
        val style = TextStyle(bold = true, font = "Mono", list = ListStyle(ListKind.DOT, 0xFFD10101.toInt(), 0.6f, 0.25f), backgroundColor = 0x80F5C400.toInt())
        val item = TextItem(7, frame, "colored", 14f, style = style, spans = listOf(TextSpan(0, 3, SpanLook(italic = true))))
        val plainColors = item.copy(style = TextStyle(backgroundColor = 0xFFFFFFFF.toInt()))
        val items = listOf<PageItem>(item, plainColors)
        assertEquals(items, PageCodec.decode(PageCodec.encode(items)))
    }

    @Test
    fun `only a page with a background is written at format 16`() {
        val plain = TextItem(1, frame, "plain", 12f, style = TextStyle(bold = true, font = "Mono"))
        val colored = plain.copy(id = 2, style = TextStyle(backgroundColor = 0xFF000000.toInt()))
        assertEquals(10, PageCodec.encode(listOf(plain))[4].toInt())
        assertEquals(16, PageCodec.encode(listOf(plain, colored))[4].toInt())
        // Asked for an older format, the colors are still kept rather than lost.
        assertEquals(listOf<PageItem>(colored), PageCodec.decode(PageCodec.encode(listOf(colored), version = 8)))
    }

    @Test
    fun `an older page opens with no background`() {
        val old = TextItem(1, frame, "old", 12f, style = TextStyle(italic = true))
        val read = PageCodec.decode(PageCodec.encode(listOf(old), version = 8)).single() as TextItem
        assertTrue(!read.style.hasBackground)
        assertEquals(old, read)
    }

    @Test
    fun `a layer table matches the items of a page with a text background, on one layer, on several, and inside a post-it`() {
        val colored = TextItem(1, frame, "a", 12f, style = TextStyle(backgroundColor = 0xFFFFFF00.toInt()))
        val plain = TextItem(2, frame, "b", 12f)
        val note = StickyNoteItem(3, frame, 0xFFFBDD8C.toInt(), true, 100f, 100f, items = listOf(colored.copy(id = 4)))
        val two = PageLayers.Single.withNewLayerAbove(PageLayers.Single.all[0].id)
        val pages = listOf(
            listOf<PageItem>(colored) to PageLayers.Single,
            listOf<PageItem>(plain, colored) to PageLayers.Single,
            listOf<PageItem>(note) to PageLayers.Single,
            listOf<PageItem>(colored.onLayer(two.all[1].id), plain) to two,
            listOf<PageItem>(colored.onLayer(777L)) to PageLayers.Single,
        )
        for ((items, layers) in pages) {
            val bytes = PageCodec.encode(items, layers = layers)
            val back = PageCodec.decodePage(bytes)
            assertEquals(items.size, back.items.size)
            assertEquals(layers.size, back.layers.size)
        }
    }
}
