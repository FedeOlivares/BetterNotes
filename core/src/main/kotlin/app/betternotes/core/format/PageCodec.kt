package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.ImageShape
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PenType
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.TapePattern
import app.betternotes.core.model.TapeShadow
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VideoItem
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.VerticalAlign
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TipShape

private const val PAGE_MAGIC = "BNPG"

private const val TAG_STROKE = 1
private const val TAG_SHAPE = 2
private const val TAG_IMAGE = 3
private const val TAG_TEXT = 4
private const val TAG_STICKY = 5
private const val TAG_TABLE = 6
private const val TAG_AUDIO = 7

// Added after format version 1 shipped, for items that use what it could not hold. An item that doesn't
// is written under its old tag, so plain items keep their bytes.
private const val TAG_STYLED_TEXT = 8
private const val TAG_STYLED_TABLE = 9
private const val TAG_VIDEO = 10
private const val TAG_TAPE_PATH = 11

/** An item takes at least a tag and an id. */
private const val MIN_ITEM_BYTES = 1 + Long.SIZE_BYTES

internal val INK_TOOLS = WireEnum(InkTool.PEN, InkTool.PENCIL, InkTool.HIGHLIGHTER)
internal val PEN_TYPES = WireEnum(PenType.NATURAL, PenType.BALL, PenType.CALLIGRAPHY)
internal val TIP_SHAPES = WireEnum(TipShape.ROUND, TipShape.SQUARE)
internal val BRUSH_KINDS = WireEnum(
    BrushKind.PENCIL,
    BrushKind.OIL,
    BrushKind.SPILL,
    BrushKind.WATERCOLOR,
    BrushKind.COMICS,
    BrushKind.STRIPES,
    BrushKind.DOTS,
    BrushKind.LEAVES,
    BrushKind.CROSS,
    BrushKind.GLOW,
    BrushKind.SINGLE_DOTS,
    BrushKind.SCRATCHES,
)
internal val LINE_KINDS = WireEnum(LineKind.SOLID, LineKind.DOTTED, LineKind.DASHED)
internal val SHAPE_KINDS =
    WireEnum(
        ShapeKind.LINE,
        ShapeKind.ARROW,
        ShapeKind.RECTANGLE,
        ShapeKind.ELLIPSE,
        ShapeKind.TRIANGLE,
        // Added after format version 1 shipped. New values go at the end, so old files read the same.
        ShapeKind.DIAMOND,
        ShapeKind.PARALLELOGRAM,
        ShapeKind.TRAPEZOID,
        ShapeKind.BLOCK_ARROW,
        ShapeKind.CURVE,
        ShapeKind.PENTAGON,
        ShapeKind.HEXAGON,
        ShapeKind.PIE,
        ShapeKind.ARC,
        ShapeKind.STAR,
        ShapeKind.SPHERE,
        ShapeKind.PYRAMID,
        ShapeKind.CYLINDER,
        ShapeKind.CUBE,
        ShapeKind.TETRAHEDRON,
        ShapeKind.PRISM,
        ShapeKind.DISCS,
        ShapeKind.SINE,
        ShapeKind.COSINE,
        ShapeKind.AXES,
        ShapeKind.PARABOLA,
        ShapeKind.CUBIC,
        ShapeKind.BRACE,
        ShapeKind.HEART,
        ShapeKind.SPEECH_BOX,
        ShapeKind.SPEECH_ROUND,
        ShapeKind.THOUGHT,
        ShapeKind.TAPE,
    )
internal val TAPE_PATTERNS = WireEnum(TapePattern.PLAIN, TapePattern.DOTS, TapePattern.LINES, TapePattern.CROSSHATCH)
internal val IMAGE_SHAPES = WireEnum(ImageShape.RECTANGLE, ImageShape.ELLIPSE)
internal val HORIZONTAL_ALIGNS = WireEnum(HorizontalAlign.START, HorizontalAlign.CENTER, HorizontalAlign.END)
internal val VERTICAL_ALIGNS = WireEnum(VerticalAlign.TOP, VerticalAlign.MIDDLE, VerticalAlign.BOTTOM)

/**
 * The file holding one page's items. Stroke samples are written as raw floats, which uses more
 * disk than delta compression would but reads back as a single block copy, because loading speed
 * is the priority. Readers refuse a newer version instead of skipping what they cannot read, since
 * saving would then destroy it.
 */
object PageCodec {
    /** [version] is only for tests of older formats. */
    fun encode(items: List<PageItem>, version: Int = PAGE_FORMAT_VERSION): ByteArray =
        encodeFile(PAGE_MAGIC, version) { items(items) }

    fun decode(bytes: ByteArray): List<PageItem> = decodeFile(bytes, PAGE_MAGIC, PAGE_FORMAT_VERSION) { items() }
}

private fun ByteWriter.items(items: List<PageItem>) {
    varint(items.size)
    items.forEach { item(it) }
}

private fun ByteReader.items(): List<PageItem> {
    val count = count(MIN_ITEM_BYTES)
    val items = ArrayList<PageItem>(count)
    repeat(count) { items += item() }
    return items
}

private fun ByteWriter.item(item: PageItem) {
    when (item) {
        is StrokeItem -> {
            byte(TAG_STROKE)
            long(item.id)
            inkStyle(item.style)
            varint(item.points.size)
            floats(item.points.packed)
        }
        is ShapeItem -> {
            byte(TAG_SHAPE)
            long(item.id)
            frame(item.frame)
            SHAPE_KINDS.write(this, item.kind)
            int(item.strokeColor)
            float(item.strokeWidth)
            lineStyle(item.line)
            int(item.fillColor)
            float(item.cornerRadius)
            float(item.opacity)
            if (version >= 2) {
                float(item.tipLength)
                float(item.tipWidth)
            }
            if (version >= 4) {
                TAPE_PATTERNS.write(this, item.pattern)
                float(item.shadow.distance)
                float(item.shadow.angleDegrees)
                float(item.shadow.opacity)
            }
        }
        is TapePathItem -> {
            require(version >= 4) { "Tape that follows the pen needs page format 4" }
            byte(TAG_TAPE_PATH)
            long(item.id)
            float(item.width)
            int(item.fillColor)
            float(item.opacity)
            TAPE_PATTERNS.write(this, item.pattern)
            float(item.shadow.distance)
            float(item.shadow.angleDegrees)
            float(item.shadow.opacity)
            varint(item.pointCount)
            for (value in item.points) float(value)
        }
        is ImageItem -> {
            byte(TAG_IMAGE)
            long(item.id)
            frame(item.frame)
            string(item.assetId)
            IMAGE_SHAPES.write(this, item.shape)
            float(item.cornerRadius)
            int(item.borderColor)
            float(item.borderWidth)
        }
        is TextItem -> {
            val styled = item.style != TextStyle()
            byte(if (styled) TAG_STYLED_TEXT else TAG_TEXT)
            long(item.id)
            frame(item.frame)
            string(item.text)
            float(item.textSize)
            int(item.color)
            if (styled) {
                bool(item.style.bold)
                bool(item.style.italic)
                bool(item.style.underline)
                bool(item.style.strikethrough)
                HORIZONTAL_ALIGNS.write(this, item.style.align)
                VERTICAL_ALIGNS.write(this, item.style.verticalAlign)
            }
        }
        is StickyNoteItem -> {
            byte(TAG_STICKY)
            long(item.id)
            frame(item.frame)
            int(item.color)
            bool(item.isOpen)
            float(item.closedWidth)
            float(item.closedHeight)
            items(item.items)
        }
        is TableItem -> {
            val styled = item.headerRow || item.headerColumn || item.fillColor != 0
            byte(if (styled) TAG_STYLED_TABLE else TAG_TABLE)
            long(item.id)
            frame(item.frame)
            floatList(item.columnWeights)
            floatList(item.rowWeights)
            item.cells.forEach { string(it) }
            float(item.textSize)
            int(item.lineColor)
            float(item.lineWidth)
            if (styled) {
                bool(item.headerRow)
                bool(item.headerColumn)
                int(item.fillColor)
            }
        }
        is VideoItem -> {
            byte(TAG_VIDEO)
            long(item.id)
            frame(item.frame)
            string(item.assetId)
            string(item.posterAssetId)
            long(item.durationMs)
        }
        is AudioItem -> {
            byte(TAG_AUDIO)
            long(item.id)
            frame(item.frame)
            string(item.assetId)
            long(item.durationMs)
        }
    }
}

private fun ByteReader.table(): TableItem {
    val id = long()
    val frame = frame()
    val columns = floatList()
    val rows = floatList()
    val cellCount = columns.size.toLong() * rows.size
    if (cellCount > remaining) throw CorruptFileException("$cellCount cells, $remaining bytes left")
    val cells = List(cellCount.toInt()) { string() }
    return TableItem(
        id = id,
        frame = frame,
        columnWeights = columns,
        rowWeights = rows,
        cells = cells,
        textSize = float(),
        lineColor = int(),
        lineWidth = float(),
    )
}

private fun ByteReader.item(): PageItem = when (val tag = byte()) {
    TAG_STROKE -> {
        val id = long()
        val style = inkStyle()
        val pointCount = count(StrokePoints.STRIDE * Float.SIZE_BYTES)
        StrokeItem(id, style, StrokePoints(floats(pointCount * StrokePoints.STRIDE)))
    }
    TAG_SHAPE -> ShapeItem(
        id = long(),
        frame = frame(),
        kind = SHAPE_KINDS.read(this),
        strokeColor = int(),
        strokeWidth = float(),
        line = lineStyle(),
        fillColor = int(),
        cornerRadius = float(),
        opacity = float(),
        tipLength = if (version >= 2) float() else 0f,
        tipWidth = if (version >= 2) float() else 0f,
        pattern = if (version >= 4) TAPE_PATTERNS.read(this) else TapePattern.PLAIN,
        shadow = if (version >= 4) TapeShadow(float(), float(), float()) else TapeShadow.NONE,
    )
    TAG_TAPE_PATH -> {
        val id = long()
        val width = float()
        val color = int()
        val opacity = float()
        val pattern = TAPE_PATTERNS.read(this)
        val shadow = TapeShadow(float(), float(), float())
        val count = count(2 * Float.SIZE_BYTES)
        TapePathItem(id, floats(count * 2), width, color, opacity, pattern, shadow)
    }
    TAG_IMAGE -> ImageItem(
        id = long(),
        frame = frame(),
        assetId = string(),
        shape = IMAGE_SHAPES.read(this),
        cornerRadius = float(),
        borderColor = int(),
        borderWidth = float(),
    )
    TAG_STYLED_TEXT -> TextItem(
        id = long(),
        frame = frame(),
        text = string(),
        textSize = float(),
        color = int(),
        style = TextStyle(
            bold = bool(),
            italic = bool(),
            underline = bool(),
            strikethrough = bool(),
            align = HORIZONTAL_ALIGNS.read(this),
            verticalAlign = VERTICAL_ALIGNS.read(this),
        ),
    )
    TAG_TEXT -> TextItem(
        id = long(),
        frame = frame(),
        text = string(),
        textSize = float(),
        color = int(),
    )
    TAG_STICKY -> StickyNoteItem(
        id = long(),
        frame = frame(),
        color = int(),
        isOpen = bool(),
        closedWidth = float(),
        closedHeight = float(),
        items = items(),
    )
    TAG_TABLE -> table()
    TAG_STYLED_TABLE -> table().let { plain ->
        plain.copy(headerRow = bool(), headerColumn = bool(), fillColor = int())
    }
    TAG_VIDEO -> VideoItem(
        id = long(),
        frame = frame(),
        assetId = string(),
        posterAssetId = string(),
        durationMs = long(),
    )
    TAG_AUDIO -> AudioItem(
        id = long(),
        frame = frame(),
        assetId = string(),
        durationMs = long(),
    )
    else -> throw CorruptFileException("Unknown item tag $tag")
}

private fun ByteWriter.inkStyle(style: InkStyle) {
    INK_TOOLS.write(this, style.tool)
    PEN_TYPES.write(this, style.penType)
    TIP_SHAPES.write(this, style.tip)
    int(style.color)
    float(style.width)
    float(style.opacity)
    lineStyle(style.line)
    if (version >= 3) BRUSH_KINDS.write(this, style.brush)
}

private fun ByteReader.inkStyle() = InkStyle(
    tool = INK_TOOLS.read(this),
    penType = PEN_TYPES.read(this),
    tip = TIP_SHAPES.read(this),
    color = int(),
    width = float(),
    opacity = float(),
    line = lineStyle(),
    brush = if (version >= 3) BRUSH_KINDS.read(this) else BrushKind.PENCIL,
)

private fun ByteWriter.lineStyle(style: LineStyle) {
    LINE_KINDS.write(this, style.kind)
    float(style.dashLength)
    float(style.gapLength)
}

private fun ByteReader.lineStyle() = LineStyle(LINE_KINDS.read(this), float(), float())

private fun ByteWriter.frame(frame: Frame) {
    float(frame.left)
    float(frame.top)
    float(frame.width)
    float(frame.height)
    float(frame.rotation)
}

private fun ByteReader.frame() = Frame(float(), float(), float(), float(), float())

private fun ByteWriter.floatList(values: List<Float>) {
    varint(values.size)
    values.forEach { float(it) }
}

private fun ByteReader.floatList(): List<Float> {
    val count = count(Float.SIZE_BYTES)
    return List(count) { float() }
}
