package app.betternotes.core.format

import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageSource
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.LayoutElement
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate

private const val DOCUMENT_MAGIC = "BNDC"

private const val MIN_PAGE_BYTES = Long.SIZE_BYTES
private const val MIN_ELEMENT_BYTES = 1

private const val TEMPLATE_BLANK = 0
private const val TEMPLATE_LINED = 1
private const val TEMPLATE_DOTTED = 2
private const val TEMPLATE_GRID = 3
private const val TEMPLATE_HEXAGONAL = 4
private const val TEMPLATE_CORNELL = 5
private const val TEMPLATE_LEGAL = 6
private const val TEMPLATE_MUSIC = 7
private const val TEMPLATE_GUITAR = 8
private const val TEMPLATE_CUSTOM = 9

private const val ELEMENT_LINE = 0
private const val ELEMENT_RECTANGLE = 1
private const val ELEMENT_TABLE = 2
private const val ELEMENT_RULED = 3

private const val SOURCE_BLANK = 0
private const val SOURCE_PDF = 1

internal val DOCUMENT_KINDS = WireEnum(DocumentKind.NOTE, DocumentKind.CANVAS, DocumentKind.PDF)
internal val LENGTH_UNITS = WireEnum(LengthUnit.PT, LengthUnit.INCH, LengthUnit.CM, LengthUnit.MM, LengthUnit.PX)

/** The file describing a document and listing its pages in order. */
object DocumentCodec {
    fun encode(document: Document): ByteArray = encodeFile(DOCUMENT_MAGIC) {
        long(document.id)
        string(document.title)
        DOCUMENT_KINDS.write(this, document.kind)
        long(document.createdAtMs)
        long(document.modifiedAtMs)
        varint(document.pages.size)
        document.pages.forEach { page(it) }
    }

    fun decode(bytes: ByteArray): Document = decodeFile(bytes, DOCUMENT_MAGIC) {
        val id = long()
        val title = string()
        val kind = DOCUMENT_KINDS.read(this)
        val createdAtMs = long()
        val modifiedAtMs = long()
        val pageCount = count(MIN_PAGE_BYTES)
        Document(id, title, kind, createdAtMs, modifiedAtMs, List(pageCount) { page() })
    }
}

private fun ByteWriter.page(page: Page) {
    long(page.id)
    bool(page.size != null)
    page.size?.let { pageSize(it) }
    int(page.background.color)
    template(page.background.template)
    when (val source = page.source) {
        PageSource.Blank -> byte(SOURCE_BLANK)
        is PageSource.PdfPage -> {
            byte(SOURCE_PDF)
            string(source.assetId)
            varint(source.pageIndex)
        }
    }
}

private fun ByteReader.page(): Page {
    val id = long()
    val size = if (bool()) pageSize() else null
    val background = PageBackground(int(), template())
    val source = when (val tag = byte()) {
        SOURCE_BLANK -> PageSource.Blank
        SOURCE_PDF -> PageSource.PdfPage(string(), varint())
        else -> throw CorruptFileException("Unknown page source $tag")
    }
    return Page(id, size, background, source)
}

private fun ByteWriter.pageSize(size: PageSize) {
    float(size.widthPt)
    float(size.heightPt)
}

private fun ByteReader.pageSize() = PageSize(float(), float())

private fun ByteWriter.template(template: PageTemplate) {
    when (template) {
        PageTemplate.Blank -> byte(TEMPLATE_BLANK)
        is PageTemplate.Lined -> {
            byte(TEMPLATE_LINED)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Dotted -> {
            byte(TEMPLATE_DOTTED)
            float(template.spacing)
            float(template.dotDiameter)
            int(template.color)
        }
        is PageTemplate.Grid -> {
            byte(TEMPLATE_GRID)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Hexagonal -> {
            byte(TEMPLATE_HEXAGONAL)
            float(template.side)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Cornell -> {
            byte(TEMPLATE_CORNELL)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Legal -> {
            byte(TEMPLATE_LEGAL)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
            int(template.marginColor)
        }
        is PageTemplate.MusicScore -> {
            byte(TEMPLATE_MUSIC)
            float(template.lineGap)
            float(template.staffGap)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.GuitarTab -> {
            byte(TEMPLATE_GUITAR)
            float(template.lineGap)
            float(template.staffGap)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Custom -> {
            byte(TEMPLATE_CUSTOM)
            customLayout(template.layout)
        }
    }
}

private fun ByteReader.template(): PageTemplate = when (val tag = byte()) {
    TEMPLATE_BLANK -> PageTemplate.Blank
    TEMPLATE_LINED -> PageTemplate.Lined(float(), float(), int())
    TEMPLATE_DOTTED -> PageTemplate.Dotted(float(), float(), int())
    TEMPLATE_GRID -> PageTemplate.Grid(float(), float(), int())
    TEMPLATE_HEXAGONAL -> PageTemplate.Hexagonal(float(), float(), int())
    TEMPLATE_CORNELL -> PageTemplate.Cornell(float(), float(), int())
    TEMPLATE_LEGAL -> PageTemplate.Legal(float(), float(), int(), int())
    TEMPLATE_MUSIC -> PageTemplate.MusicScore(float(), float(), float(), int())
    TEMPLATE_GUITAR -> PageTemplate.GuitarTab(float(), float(), float(), int())
    TEMPLATE_CUSTOM -> PageTemplate.Custom(customLayout())
    else -> throw CorruptFileException("Unknown template $tag")
}

private fun ByteWriter.customLayout(layout: CustomLayout) {
    string(layout.name)
    pageSize(layout.size)
    LENGTH_UNITS.write(this, layout.displayUnit)
    varint(layout.elements.size)
    layout.elements.forEach { element(it) }
}

private fun ByteReader.customLayout(): CustomLayout {
    val name = string()
    val size = pageSize()
    val unit = LENGTH_UNITS.read(this)
    val count = count(MIN_ELEMENT_BYTES)
    return CustomLayout(name, size, unit, List(count) { element() })
}

private fun ByteWriter.element(element: LayoutElement) {
    when (element) {
        is LayoutElement.Line -> {
            byte(ELEMENT_LINE)
            float(element.x0)
            float(element.y0)
            float(element.x1)
            float(element.y1)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Rectangle -> {
            byte(ELEMENT_RECTANGLE)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Table -> {
            byte(ELEMENT_TABLE)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            varint(element.rows)
            varint(element.columns)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Ruled -> {
            byte(ELEMENT_RULED)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            float(element.spacing)
            float(element.lineWidth)
            int(element.color)
        }
    }
}

private fun ByteReader.element(): LayoutElement = when (val tag = byte()) {
    ELEMENT_LINE -> LayoutElement.Line(float(), float(), float(), float(), float(), int())
    ELEMENT_RECTANGLE -> LayoutElement.Rectangle(float(), float(), float(), float(), float(), int())
    ELEMENT_TABLE -> LayoutElement.Table(float(), float(), float(), float(), varint(), varint(), float(), int())
    ELEMENT_RULED -> LayoutElement.Ruled(float(), float(), float(), float(), float(), float(), int())
    else -> throw CorruptFileException("Unknown layout element $tag")
}
