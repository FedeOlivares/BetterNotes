package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.ImageShape
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.PenType
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.TapePattern
import app.betternotes.core.model.TapeShadow
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TipShape
import app.betternotes.core.stroke
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.LayoutElement
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CodecTest {
    private val frame = Frame(1f, 2f, 300f, 400f, rotation = 15f)

    private val everyItem: List<PageItem> = listOf(
        StrokeItem(
            -1234567890123L,
            InkStyle(
                InkTool.HIGHLIGHTER,
                PenType.CALLIGRAPHY,
                TipShape.SQUARE,
                color = 0x80FFEE00.toInt(),
                width = 12.5f,
                opacity = 0.4f,
                line = LineStyle(LineKind.DASHED, 3f, 2f),
            ),
            StrokePointsBuilder().apply {
                add(1f, 2f, 0.5f, 0.1f, 3.2f, 0f)
                add(3f, 4f, timeMs = 4.2f)
            }.build(),
        ),
        ShapeItem(
            2,
            frame,
            ShapeKind.ELLIPSE,
            strokeColor = 0xFF112233.toInt(),
            strokeWidth = 3f,
            line = LineStyle(LineKind.DOTTED, 1f, 5f),
            fillColor = 0x40445566,
            cornerRadius = 7f,
            opacity = 0.75f,
        ),
        ShapeItem(
            22,
            frame,
            ShapeKind.TAPE,
            strokeWidth = 0f,
            fillColor = 0xB3A8D8FF.toInt(),
            pattern = TapePattern.CROSSHATCH,
            shadow = TapeShadow(2.5f, 120f, 0.4f),
        ),
        TapePathItem(
            23,
            floatArrayOf(10f, 10f, 40f, 30f, 80f, 25f, 120f, 60f),
            width = 18f,
            fillColor = 0xB3F6A9A0.toInt(),
            opacity = 0.8f,
            pattern = TapePattern.LINES,
            shadow = TapeShadow(1.5f, 45f, 0.3f),
        ),
        ImageItem(3, frame, "photo-ñ.jpg", ImageShape.ELLIPSE, 8f, 0xFF00FF00.toInt(), 2f),
        TextItem(4, frame, "Hola, ¿qué tal? 日本語 🙂", 14f, 0xFF0000FF.toInt()),
        StickyNoteItem(
            5,
            frame,
            0xFFFFFF88.toInt(),
            isOpen = true,
            closedWidth = 20f,
            closedHeight = 22f,
            items = listOf(stroke(51, 0f, 0f, 5f, 5f), TextItem(52, frame, "inside")),
        ),
        TableItem(
            6,
            frame,
            columnWeights = listOf(1f, 2f, 1f),
            rowWeights = listOf(1f, 3f),
            cells = listOf("a", "", "c", "d", "e", "f"),
            textSize = 11f,
            lineColor = 0xFF999999.toInt(),
            lineWidth = 0.5f,
        ),
        AudioItem(7, frame, "rec.m4a", 123_456_789_012L),
    )

    private val everyTemplate: List<PageTemplate> = listOf(
        PageTemplate.Blank,
        PageTemplate.Lined(20f, 0.3f, 1),
        PageTemplate.Dotted(21f, 0.4f, 2),
        PageTemplate.Grid(22f, 0.5f, 3),
        PageTemplate.Hexagonal(23f, 0.6f, 4),
        PageTemplate.Cornell(24f, 0.7f, 5),
        PageTemplate.Legal(25f, 0.8f, 6, 7),
        PageTemplate.MusicScore(8f, 40f, 0.9f, 8),
        PageTemplate.GuitarTab(9f, 41f, 1f, 9),
        PageTemplate.Custom(
            CustomLayout(
                "Mi plantilla",
                PageSize.of(10f, 20f, LengthUnit.CM),
                LengthUnit.CM,
                listOf(
                    LayoutElement.Line(1f, 2f, 3f, 4f, 5f, 6),
                    LayoutElement.Rectangle(1f, 2f, 3f, 4f, 5f, 6),
                    LayoutElement.Table(1f, 2f, 3f, 4f, 2, 3, 5f, 6),
                    LayoutElement.Ruled(1f, 2f, 3f, 4f, 5f, 6f, 7),
                ),
            ),
        ),
    )

    @Test
    fun `a sticky note in a custom layout survives a round trip`() {
        val layout = CustomLayout("S", PageSize(100f, 100f), LengthUnit.CM, listOf(LayoutElement.Sticky(1f, 2f, 3f, 4f, 5)))
        val document = Document(
            id = 1, title = "t", kind = DocumentKind.NOTE, createdAtMs = 0, modifiedAtMs = 0,
            pages = listOf(Page(1, PageSize(100f, 200f), PageBackground(0, PageTemplate.Custom(layout)))),
        )
        val back = DocumentCodec.decode(DocumentCodec.encode(document))
        assertEquals(layout, (back.pages[0].background.template as PageTemplate.Custom).layout)
    }

    private fun everyTemplateDocument() = Document(
        id = 99,
        title = "Apuntes ñ",
        kind = DocumentKind.NOTE,
        createdAtMs = 111,
        modifiedAtMs = 222,
        pages = everyTemplate.mapIndexed { i, template ->
            Page(i.toLong() + 1, PageSize(100f + i, 200f + i), PageBackground(i, template))
        },
    )

    private fun assertSameDocument(a: Document, b: Document) {
        assertEquals(a.id, b.id)
        assertEquals(a.title, b.title)
        assertEquals(a.kind, b.kind)
        assertEquals(a.createdAtMs, b.createdAtMs)
        assertEquals(a.modifiedAtMs, b.modifiedAtMs)
        assertEquals(a.pages, b.pages)
    }

    @Test
    fun `every item survives a round trip`() {
        assertEquals(everyItem, PageCodec.decode(PageCodec.encode(everyItem)))
    }

    @Test
    fun `an empty page survives a round trip`() {
        assertEquals(emptyList<PageItem>(), PageCodec.decode(PageCodec.encode(emptyList())))
    }

    @Test
    fun `every template survives a round trip`() {
        val document = everyTemplateDocument()
        assertSameDocument(document, DocumentCodec.decode(DocumentCodec.encode(document)))
    }

    @Test
    fun `the planning pages survive a round trip`() {
        val planning = listOf(PageTemplate.Semester, PageTemplate.MindMap, PageTemplate.WeekPlanner)
        val document = Document(1, "p", DocumentKind.NOTE, 1, 2, planning.mapIndexed { i, t -> Page(i + 1L, PageSize(100f, 200f), PageBackground(0, t)) })
        assertSameDocument(document, DocumentCodec.decode(DocumentCodec.encode(document)))
    }

    @Test
    fun `canvas and pdf documents survive a round trip`() {
        val canvas = Document(1, "c", DocumentKind.CANVAS, 1, 2, listOf(Page(5, null)))
        assertSameDocument(canvas, DocumentCodec.decode(DocumentCodec.encode(canvas)))

        val pdf = Document(
            2,
            "p",
            DocumentKind.PDF,
            1,
            2,
            listOf(Page(5, PageSize.A4, source = PageSource.PdfPage("book.pdf", 300)), Page(6, PageSize.A5)),
        )
        assertSameDocument(pdf, DocumentCodec.decode(DocumentCodec.encode(pdf)))
    }

    @Test
    fun `a page too big for the starting buffer still round trips`() {
        val long = stroke(1, *FloatArray(40_000) { it.toFloat() })
        val decoded = PageCodec.decode(PageCodec.encode(List(5) { long }))
        assertEquals(List(5) { long }, decoded)
    }

    @Test
    fun `any flipped bit is caught`() {
        val bytes = PageCodec.encode(everyItem)
        for (i in bytes.indices step 7) {
            val damaged = bytes.copyOf()
            damaged[i] = (damaged[i].toInt() xor 0x10).toByte()
            val thrown = runCatching { PageCodec.decode(damaged) }.exceptionOrNull()
            assertTrue(
                "byte $i was accepted: $thrown",
                thrown is CorruptFileException || thrown is UnsupportedVersionException,
            )
        }
    }

    @Test
    fun `any truncation is caught`() {
        val bytes = PageCodec.encode(everyItem)
        for (length in 0 until bytes.size step 11) {
            assertThrows(CorruptFileException::class.java) { PageCodec.decode(bytes.copyOf(length)) }
        }
    }

    @Test
    fun `a page file is not a document file`() {
        assertThrows(CorruptFileException::class.java) {
            DocumentCodec.decode(PageCodec.encode(everyItem))
        }
    }

    @Test
    fun `a newer version is refused, not skipped`() {
        val newer = encodeWithVersion("BNPG", 7)
        val thrown = assertThrows(UnsupportedVersionException::class.java) { PageCodec.decode(newer) }
        assertEquals(7, thrown.version)
    }

    /** Builds a well-formed file with a bad body, to check values the checksum cannot catch. */
    private fun fileWithBody(magic: String, body: ByteWriter.() -> Unit) = encodeFile(magic, body)

    private fun encodeWithVersion(magic: String, version: Int): ByteArray {
        val writer = ByteWriter()
        writer.bytes(magic.toByteArray())
        writer.varint(version)
        writer.int(0)
        return writer.toByteArray()
    }

    @Test
    fun `a huge count in a valid file fails cleanly rather than allocating`() {
        val bad = fileWithBody("BNPG") { varint(Int.MAX_VALUE) }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `a huge stroke point count fails cleanly`() {
        val bad = fileWithBody("BNPG") {
            varint(1)
            byte(1)
            long(1)
            repeat(3) { byte(0) }
            int(0)
            float(1f)
            float(1f)
            byte(0)
            float(1f)
            float(1f)
            varint(Int.MAX_VALUE)
        }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `a huge table in a valid file fails cleanly`() {
        val bad = fileWithBody("BNPG") {
            varint(1)
            byte(6)
            long(1)
            repeat(5) { float(1f) }
            repeat(2) {
                varint(50_000)
                repeat(50_000) { float(1f) }
            }
        }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `an unknown item tag in a valid file fails cleanly`() {
        val bad = fileWithBody("BNPG") {
            varint(1)
            byte(200)
            long(1)
        }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `an out of range enum in a valid file fails cleanly`() {
        val bad = fileWithBody("BNPG") {
            varint(1)
            byte(1)
            long(1)
            byte(77)
        }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `a template spacing that would hang the renderer fails cleanly`() {
        val bad = fileWithBody("BNDC") {
            long(1)
            string("t")
            byte(0)
            long(0)
            long(0)
            varint(1)
            long(1)
            bool(true)
            float(100f)
            float(100f)
            int(0)
            byte(1)
            float(0f)
            float(1f)
            int(0)
            byte(0)
        }
        assertThrows(CorruptFileException::class.java) { DocumentCodec.decode(bad) }
    }

    @Test
    fun `sticky notes nested by a damaged file fail cleanly`() {
        val innerFrame: ByteWriter.() -> Unit = { repeat(5) { float(1f) } }
        val bad = fileWithBody("BNPG") {
            varint(1)
            byte(5)
            long(1)
            innerFrame()
            int(0)
            bool(false)
            float(1f)
            float(1f)
            varint(1)
            byte(5)
            long(2)
            innerFrame()
            int(0)
            bool(false)
            float(1f)
            float(1f)
            varint(0)
        }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `leftover bytes fail cleanly`() {
        val bad = fileWithBody("BNPG") {
            varint(0)
            byte(0)
        }
        assertThrows(CorruptFileException::class.java) { PageCodec.decode(bad) }
    }

    @Test
    fun `every enum value has a wire code`() {
        assertEquals(InkTool.entries.toSet(), INK_TOOLS.coded.toSet())
        assertEquals(PenType.entries.toSet(), PEN_TYPES.coded.toSet())
        assertEquals(TipShape.entries.toSet(), TIP_SHAPES.coded.toSet())
        assertEquals(LineKind.entries.toSet(), LINE_KINDS.coded.toSet())
        assertEquals(ShapeKind.entries.toSet(), SHAPE_KINDS.coded.toSet())
        assertEquals(ImageShape.entries.toSet(), IMAGE_SHAPES.coded.toSet())
        assertEquals(DocumentKind.entries.toSet(), DOCUMENT_KINDS.coded.toSet())
        assertEquals(LengthUnit.entries.toSet(), LENGTH_UNITS.coded.toSet())
    }

    /**
     * Pins the bytes of format version 1. If this fails, saved notes would no longer open the same
     * way: bump [FORMAT_VERSION] and add a migration instead of changing the expected value.
     */
    @Test
    fun `format version 1 bytes do not change`() {
        val items = listOf(everyItem[0], everyItem[1])
        assertArrayEquals(GOLDEN_PAGE.decodeBase64(), PageCodec.encode(items, version = 1))
        assertEquals(items, PageCodec.decode(GOLDEN_PAGE.decodeBase64()))
    }

    @Test
    fun `a version 1 shape opens with the default tips and a version 2 one keeps its own`() {
        val plain = ShapeItem(9L, Frame(0f, 0f, 100f, 0f), ShapeKind.ARROW)
        val tipped = plain.copy(tipLength = 30f, tipWidth = 18f)
        assertEquals(listOf(plain), PageCodec.decode(PageCodec.encode(listOf(tipped), version = 1)).map {
            (it as ShapeItem).copy(tipLength = 0f, tipWidth = 0f)
        })
        assertEquals(listOf(tipped), PageCodec.decode(PageCodec.encode(listOf(tipped))))
    }

    @Test
    fun `document format version 1 bytes do not change`() {
        val document = everyTemplateDocument()
        assertArrayEquals(GOLDEN_DOCUMENT.decodeBase64(), DocumentCodec.encode(document, version = 1))
    }

    @Test
    fun `a version 1 layout opens with plain paper and a version 2 one keeps its look`() {
        val plain = CustomLayout("L", PageSize(100f, 100f), LengthUnit.CM, listOf(LayoutElement.Sticky(1f, 2f, 3f, 4f, 5)))
        val styled = plain.copy(paper = 0x12345678, base = PageTemplate.Legal(), bookStyle = true)
        fun roundTrip(layout: CustomLayout, version: Int): CustomLayout {
            val page = Page(1, PageSize(100f, 200f), PageBackground(0, PageTemplate.Custom(layout)))
            val back = DocumentCodec.decode(DocumentCodec.encode(Document(1, "t", DocumentKind.NOTE, 0, 0, listOf(page)), version))
            return (back.pages[0].background.template as PageTemplate.Custom).layout
        }
        assertEquals(plain, roundTrip(styled, 1).copy(elements = plain.elements, name = "L", size = plain.size))
        assertEquals(styled, roundTrip(styled, 2))
        val moved = styled.copy(base = PageTemplate.Cornell(), frameLines = listOf(0.1f, 0.7f, 0.4f))
        assertEquals(moved, roundTrip(moved, 3))
        assertEquals(emptyList<Float>(), roundTrip(moved, 2).frameLines)
    }

    @Test
    fun `page format 5 keeps the lock, keep open, opacity and no overlap, and older pages open without them`() {
        val stroke = StrokeItem(
            1L,
            InkStyle(tool = InkTool.HIGHLIGHTER, noOverlap = true),
            StrokePointsBuilder().apply {
                add(1f, 2f, 0.5f, 0.1f, 3.2f, 0f)
                add(3f, 4f, timeMs = 4.2f)
            }.build(),
            locked = true,
        )
        val image = ImageItem(2L, Frame(0f, 0f, 50f, 40f), "a.jpg", opacity = 0.4f, locked = true)
        val note = StickyNoteItem(3L, Frame(5f, 6f, 200f, 200f), 0xFFFBDD8C.toInt(), isOpen = true, keepOpen = true, locked = true)
        val text = TextItem(4L, Frame(0f, 0f, 10f, 10f), "hi", locked = true)
        val tape = TapePathItem(5L, floatArrayOf(0f, 0f, 9f, 9f), 6f, 0xFF123456.toInt(), locked = true)
        val items = listOf(stroke, image, note, text, tape)
        val back = PageCodec.decode(PageCodec.encode(items))
        assertEquals(items, back)
        assertTrue(back.all { it.locked })

        val older = PageCodec.decode(PageCodec.encode(items, version = 4))
        assertTrue(older.none { it.locked })
        assertEquals(1f, (older[1] as ImageItem).opacity, 0f)
        assertEquals(false, (older[2] as StickyNoteItem).keepOpen)
        assertEquals(false, (older[0] as StrokeItem).style.noOverlap)
        assertEquals(image.copy(opacity = 1f, locked = false), older[1])
    }

    @Test
    fun `document format 4 keeps the angle a page is shown at, and older documents open upright`() {
        val page = Page(1, PageSize(100f, 200f), rotation = 37.5f)
        val document = Document(1, "t", DocumentKind.NOTE, 0, 0, listOf(page))
        assertEquals(37.5f, DocumentCodec.decode(DocumentCodec.encode(document)).pages[0].rotation, 0f)
        assertEquals(0f, DocumentCodec.decode(DocumentCodec.encode(document, version = 3)).pages[0].rotation, 0f)
    }

    @Test
    fun `document format 5 keeps the cover design, and older documents have no cover`() {
        val size = PageSize(100f, 200f)
        val document = Document(1, "t", DocumentKind.NOTE, 0, 0, listOf(Page(7, size, cover = "simple-red"), Page(8, size)))
        val back = DocumentCodec.decode(DocumentCodec.encode(document))
        assertEquals("simple-red", back.coverId)
        assertEquals(null, back.pages[1].cover)
        val older = DocumentCodec.decode(DocumentCodec.encode(document, version = 4))
        assertEquals(null, older.coverId)
    }

    @Test
    fun `the cover page is locked in place`() {
        val size = PageSize(100f, 200f)
        val cover = Document.withCover(
            Document(1, "t", DocumentKind.NOTE, 0, 0, listOf(Page(8, size))),
            "solid-blue",
        )
        assertEquals(2, cover.pages.size)
        assertTrue(cover.pages[0].isCover)
        assertTrue(cover.isLocked(cover.pages[0].id))
        assertThrows(IllegalArgumentException::class.java) { cover.insertPage(0, Page(9, size)) }
        assertThrows(IllegalArgumentException::class.java) { cover.removePage(0) }
        assertThrows(IllegalArgumentException::class.java) { cover.movePage(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { cover.updatePage(cover.pages[0].copy(cover = null)) }
        assertEquals(cover, Document.withCover(cover, "solid-red"))
    }

    @Test
    fun `a cover is added, changed and taken out by one edit that undoes`() {
        val size = PageSize(100f, 200f)
        val document = Document(1, "t", DocumentKind.NOTE, 0, 0, listOf(Page(8, size)))
        val target = object : app.betternotes.core.edit.EditTarget {
            override val document = document
            override fun content(pageId: Long) = throw UnsupportedOperationException()
        }
        val cover = Page(9, size, cover = "simple")
        val add = app.betternotes.core.edit.SetCover(null, cover)
        add.apply(target)
        assertEquals(listOf(9L, 8L), document.pages.map { it.id })
        val change = app.betternotes.core.edit.SetCover(cover, cover.copy(cover = "koi"))
        change.apply(target)
        assertEquals("koi", document.coverId)
        change.revert(target)
        assertEquals("simple", document.coverId)
        val remove = app.betternotes.core.edit.SetCover(cover, null)
        remove.apply(target)
        assertEquals(null, document.coverId)
        remove.revert(target)
        add.revert(target)
        assertEquals(listOf(8L), document.pages.map { it.id })
    }

    private fun String.decodeBase64() = Base64.getDecoder().decode(this)

    private companion object {
        val GOLDEN_PAGE =
            "Qk5QRwECATX7BI7g/v//AgIBAO7/gAAASEHNzMw+AgAAQEAAAABAAgAAgD8AAABAAAAAP83MzD3NzExAAAAAAAAAQE" +
            "AAAIBAAACAvwAAgL8AAIC/ZmaGQAICAAAAAAAAAAAAgD8AAABAAACWQwAAyEMAAHBBAzMiEf8AAEBAAQAAgD8AAKBA" +
            "ZlVEQAAA4EAAAEA/6y/2Ow=="
        val GOLDEN_DOCUMENT =
            "Qk5EQwFjAAAAAAAAAApBcHVudGVzIMOxAG8AAAAAAAAA3gAAAAAAAAAKAQAAAAAAAAABAADIQgAASEMAAAAAAAACAA" +
            "AAAAAAAAEAAMpCAABJQwEAAAABAACgQZqZmT4BAAAAAAMAAAAAAAAAAQAAzEIAAEpDAgAAAAIAAKhBzczMPgIAAAAA" +
            "BAAAAAAAAAABAADOQgAAS0MDAAAAAwAAsEEAAAA/AwAAAAAFAAAAAAAAAAEAANBCAABMQwQAAAAEAAC4QZqZGT8EAA" +
            "AAAAYAAAAAAAAAAQAA0kIAAE1DBQAAAAUAAMBBMzMzPwUAAAAABwAAAAAAAAABAADUQgAATkMGAAAABgAAyEHNzEw/" +
            "BgAAAAcAAAAACAAAAAAAAAABAADWQgAAT0MHAAAABwAAAEEAACBCZmZmPwgAAAAACQAAAAAAAAABAADYQgAAUEMIAA" +
            "AACAAAEEEAACRCAACAPwkAAAAACgAAAAAAAAABAADaQgAAUUMJAAAACQxNaSBwbGFudGlsbGF3u41Dd7sNRAIEAAAA" +
            "gD8AAABAAABAQAAAgEAAAKBABgAAAAEAAIA/AAAAQAAAQEAAAIBAAACgQAYAAAACAACAPwAAAEAAAEBAAACAQAIDAA" +
            "CgQAYAAAADAACAPwAAAEAAAEBAAACAQAAAoEAAAMBABwAAAAD2kgiV"
    }
}
