package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.FillItem
import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.FillPatternKind
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.ImageShape
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListStyle
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PenType
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.TapePattern
import app.betternotes.core.model.TapeShadow
import app.betternotes.core.model.SpanLook
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VideoItem
import app.betternotes.core.model.withGroup
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.VerticalAlign
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TipShape
import app.betternotes.core.model.Layer
import app.betternotes.core.model.PageLayers

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
private const val TAG_FILL = 12
private const val TAG_PATH_SHAPE = 13
private const val TAG_SPANNED_TEXT = 14
private const val TAG_FONT_TEXT = 15
private const val TAG_COLORED_TABLE = 16
private const val TAG_MIRRORED_SHAPE = 17

/** A paint bucket fill with a pattern over its color; a fill without one keeps [TAG_FILL]. Page format 15. */
private const val TAG_PATTERN_FILL = 18

/** A text box that is a list. A tag far from the others, so that tags added elsewhere cannot collide with it. */
private const val TAG_LIST_TEXT = 40

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
internal val FILL_PATTERN_KINDS = WireEnum(FillPatternKind.SOLID, FillPatternKind.DOTS, FillPatternKind.LINES, FillPatternKind.DIAGONAL, FillPatternKind.CROSSHATCH)
internal val TAPE_PATTERNS = WireEnum(TapePattern.PLAIN, TapePattern.DOTS, TapePattern.LINES, TapePattern.CROSSHATCH)
internal val IMAGE_SHAPES = WireEnum(ImageShape.RECTANGLE, ImageShape.ELLIPSE)
internal val HORIZONTAL_ALIGNS = WireEnum(HorizontalAlign.START, HorizontalAlign.CENTER, HorizontalAlign.END)
internal val LIST_KINDS = WireEnum(
    ListKind.DASH, ListKind.STAR, ListKind.BULLET, ListKind.NUMBER_DOT, ListKind.NUMBER_PAREN,
    ListKind.UPPER_PAREN, ListKind.LOWER_PAREN, ListKind.ROMAN_PAREN, ListKind.ARROW, ListKind.DOT,
)
internal val VERTICAL_ALIGNS = WireEnum(VerticalAlign.TOP, VerticalAlign.MIDDLE, VerticalAlign.BOTTOM)

/**
 * The file holding one page's items. Stroke samples are written as raw floats, which uses more
 * disk than delta compression would but reads back as a single block copy, because loading speed
 * is the priority. Readers refuse a newer version instead of skipping what they cannot read, since
 * saving would then destroy it.
 */
object PageCodec {
    /**
     * [version] is only for tests of older formats. A page that uses layers is the only one written at [PAGE_LAYERS_FORMAT_VERSION],
     * so every other page keeps the bytes it always had and older builds can still read it.
     */
    fun encode(items: List<PageItem>, version: Int = PAGE_FORMAT_VERSION, layers: PageLayers = PageLayers.Single): ByteArray {
        // Items of a layer the table lacks, such as copied ones with no table, go to the bottom layer. The table counts items layer by layer.
        val placed = items.map { it.onLayer(layers.idOrBottom(it.layer)) }.sortedBy { layers.rank(it.layer) }
        // What a post-it holds is written inside it, so it counts too: a sticker with a pattern fill put on a post-it needs the newer format.
        val all = placed + placed.flatMap { (it as? StickyNoteItem)?.items.orEmpty() }
        val usesTools = all.any { (it is TableItem && it.headerColor != 0) || (it is ShapeItem && it.mirrored) }
        val usesPatterns = all.any { it is FillItem && !it.pattern.isSolid }
        val written = when {
            usesPatterns -> maxOf(version, PAGE_PATTERN_FILL_FORMAT_VERSION)
            usesTools -> maxOf(version, PAGE_TOOLS_FORMAT_VERSION)
            layers.isDefault -> version
            else -> maxOf(version, PAGE_LAYERS_FORMAT_VERSION)
        }
        return encodeFile(PAGE_MAGIC, written) {
            items(placed)
            if (written >= PAGE_LAYERS_FORMAT_VERSION) layers(placed, layers)
        }
    }

    fun decode(bytes: ByteArray): List<PageItem> = decodePage(bytes).items

    fun decodePage(bytes: ByteArray): DecodedPage = decodeFile(bytes, PAGE_MAGIC, PAGE_PATTERN_FILL_FORMAT_VERSION) {
        val items = items()
        if (version >= PAGE_LAYERS_FORMAT_VERSION) layers(items) else DecodedPage(items, PageLayers.Single)
    }
}

/** What a page file holds: its items, sorted by the rank of their layer, and its layers. */
class DecodedPage(val items: List<PageItem>, val layers: PageLayers)

/** The layers, bottom first, each with how many items it holds. The items are sorted by layer, so the counts say which item is on which. */
private fun ByteWriter.layers(items: List<PageItem>, layers: PageLayers) {
    varint(layers.size)
    for (layer in layers.all) {
        long(layer.id)
        string(layer.name)
        bool(layer.visible)
        varint(items.count { it.layer == layer.id })
    }
}

private fun ByteReader.layers(items: List<PageItem>): DecodedPage {
    val count = count(MIN_LAYER_BYTES)
    val table = ArrayList<Layer>(count)
    val counts = ArrayList<Int>(count)
    repeat(count) {
        table += Layer(long(), string(), bool())
        counts += varint()
    }
    if (table.isEmpty() || table.map { it.id }.toSet().size != table.size || counts.sum() != items.size) {
        throw CorruptFileException("Layer table does not match ${items.size} items")
    }
    var next = 0
    val placed = ArrayList<PageItem>(items.size)
    for (k in table.indices) repeat(counts[k]) { placed += items[next++].onLayer(table[k].id) }
    return DecodedPage(placed, PageLayers(table))
}

private const val MIN_LAYER_BYTES = Long.SIZE_BYTES + 3

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
            locked(item)
            inkStyle(item.style)
            varint(item.points.size)
            floats(item.points.packed)
        }
        is ShapeItem -> {
            byte(if (item.mirrored) TAG_MIRRORED_SHAPE else TAG_SHAPE)
            long(item.id)
            locked(item)
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
            locked(item)
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
        is FillItem -> {
            require(version >= 6) { "A paint bucket fill needs page format 6" }
            val patterned = !item.pattern.isSolid
            if (patterned) require(version >= PAGE_PATTERN_FILL_FORMAT_VERSION) { "A pattern fill needs page format $PAGE_PATTERN_FILL_FORMAT_VERSION" }
            byte(if (patterned) TAG_PATTERN_FILL else TAG_FILL)
            long(item.id)
            locked(item)
            int(item.color)
            float(item.opacity)
            if (patterned) {
                FILL_PATTERN_KINDS.write(this, item.pattern.kind)
                int(item.pattern.color)
                float(item.pattern.spacing)
                float(item.pattern.weight)
            }
            varint(item.loops.size)
            for (loop in item.loops) {
                varint(loop.size / 2)
                for (value in loop) float(value)
            }
        }
        is PathShapeItem -> {
            require(version >= 6) { "A recognized shape needs page format 6" }
            byte(TAG_PATH_SHAPE)
            long(item.id)
            locked(item)
            bool(item.closed)
            bool(item.smooth)
            int(item.strokeColor)
            float(item.strokeWidth)
            lineStyle(item.line)
            int(item.fillColor)
            float(item.opacity)
            varint(item.pointCount)
            for (value in item.points) float(value)
        }
        is ImageItem -> {
            byte(TAG_IMAGE)
            long(item.id)
            locked(item)
            frame(item.frame)
            string(item.assetId)
            IMAGE_SHAPES.write(this, item.shape)
            float(item.cornerRadius)
            int(item.borderColor)
            float(item.borderWidth)
            if (version >= 5) float(item.opacity)
        }
        is TextItem -> {
            val listed = version >= 10 && item.style.list != null
            val fonted = listed || version >= 8 && (item.style.font.isNotEmpty() || item.spans.any { it.look.font != null })
            // Without a font to keep, the parts are written as before, minus any that only differed by font.
            val spans = if (fonted) item.spans else item.spans.map { it.copy(look = it.look.copy(font = null)) }.filter { !it.look.isPlain }
            val spanned = fonted || (spans.isNotEmpty() && version >= 7)
            val styled = spanned || item.style.copy(font = "", list = null) != TextStyle()
            byte(if (listed) TAG_LIST_TEXT else if (fonted) TAG_FONT_TEXT else if (spanned) TAG_SPANNED_TEXT else if (styled) TAG_STYLED_TEXT else TAG_TEXT)
            long(item.id)
            locked(item)
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
            if (spanned) {
                varint(spans.size)
                for (span in spans) {
                    varint(span.start)
                    varint(span.end)
                    byte(packLook(span.look))
                    bool(span.look.size != null)
                    span.look.size?.let { float(it) }
                    if (fonted) {
                        bool(span.look.font != null)
                        span.look.font?.let { string(it) }
                    }
                }
                if (fonted) string(item.style.font)
                item.style.list?.takeIf { listed }?.let { list ->
                    LIST_KINDS.write(this, list.kind)
                    int(list.dotColor)
                    float(list.dotSize)
                    float(list.dotOpacity)
                }
            }
        }
        is StickyNoteItem -> {
            byte(TAG_STICKY)
            long(item.id)
            locked(item)
            frame(item.frame)
            int(item.color)
            bool(item.isOpen)
            float(item.closedWidth)
            float(item.closedHeight)
            if (version >= 5) bool(item.keepOpen)
            items(item.items)
        }
        is TableItem -> {
            val colored = item.headerColor != 0
            val styled = colored || item.headerRow || item.headerColumn || item.fillColor != 0
            byte(if (colored) TAG_COLORED_TABLE else if (styled) TAG_STYLED_TABLE else TAG_TABLE)
            long(item.id)
            locked(item)
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
                if (colored) int(item.headerColor)
            }
        }
        is VideoItem -> {
            byte(TAG_VIDEO)
            long(item.id)
            locked(item)
            frame(item.frame)
            string(item.assetId)
            string(item.posterAssetId)
            long(item.durationMs)
        }
        is AudioItem -> {
            byte(TAG_AUDIO)
            long(item.id)
            locked(item)
            frame(item.frame)
            string(item.assetId)
            long(item.durationMs)
        }
    }
}

private fun ByteReader.table(): TableItem {
    val id = long()
    val locked = locked()
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
        locked = locked,
    )
}

/** Reads one item and puts it in the group its flags byte named, if it had one. */
private fun ByteReader.item(): PageItem {
    val item = itemBody()
    val group = groups.removeLast()
    return if (group != 0L) item.withGroup(group) else item
}

private fun ByteReader.itemBody(): PageItem = when (val tag = byte()) {
    TAG_STROKE -> {
        val id = long()
        val locked = locked()
        val style = inkStyle()
        val pointCount = count(StrokePoints.STRIDE * Float.SIZE_BYTES)
        StrokeItem(id, style, StrokePoints(floats(pointCount * StrokePoints.STRIDE)), locked)
    }
    TAG_SHAPE, TAG_MIRRORED_SHAPE -> ShapeItem(
        id = long(),
        locked = locked(),
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
        mirrored = tag == TAG_MIRRORED_SHAPE,
    )
    TAG_TAPE_PATH -> {
        val id = long()
        val locked = locked()
        val width = float()
        val color = int()
        val opacity = float()
        val pattern = TAPE_PATTERNS.read(this)
        val shadow = TapeShadow(float(), float(), float())
        val count = count(2 * Float.SIZE_BYTES)
        TapePathItem(id, floats(count * 2), width, color, opacity, pattern, shadow, locked)
    }
    TAG_FILL, TAG_PATTERN_FILL -> {
        val id = long()
        val locked = locked()
        val color = int()
        val opacity = float()
        val pattern = if (tag == TAG_PATTERN_FILL) {
            if (version < PAGE_PATTERN_FILL_FORMAT_VERSION) throw CorruptFileException("A pattern fill in page format $version")
            FillPattern(FILL_PATTERN_KINDS.read(this), int(), float(), float())
        } else {
            FillPattern.SOLID
        }
        val loops = List(count(1)) { floats(count(2 * Float.SIZE_BYTES) * 2) }
        FillItem(id, loops, color, opacity, locked, pattern = pattern)
    }
    TAG_PATH_SHAPE -> {
        val id = long()
        val locked = locked()
        val closed = bool()
        val smooth = bool()
        val color = int()
        val width = float()
        val line = lineStyle()
        val fill = int()
        val opacity = float()
        val count = count(2 * Float.SIZE_BYTES)
        PathShapeItem(id, floats(count * 2), closed, smooth, color, width, line, fill, opacity, locked)
    }
    TAG_IMAGE -> ImageItem(
        id = long(),
        locked = locked(),
        frame = frame(),
        assetId = string(),
        shape = IMAGE_SHAPES.read(this),
        cornerRadius = float(),
        borderColor = int(),
        borderWidth = float(),
        opacity = if (version >= 5) float() else 1f,
    )
    TAG_STYLED_TEXT -> TextItem(
        id = long(),
        locked = locked(),
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
    TAG_SPANNED_TEXT, TAG_FONT_TEXT, TAG_LIST_TEXT -> {
        val listed = tag.toInt() == TAG_LIST_TEXT
        val fonted = listed || tag.toInt() == TAG_FONT_TEXT
        val id = long()
        val locked = locked()
        val frame = frame()
        val text = string()
        val size = float()
        val color = int()
        val style = TextStyle(
            bold = bool(),
            italic = bool(),
            underline = bool(),
            strikethrough = bool(),
            align = HORIZONTAL_ALIGNS.read(this),
            verticalAlign = VERTICAL_ALIGNS.read(this),
        )
        val spans = List(varint()) {
            val start = varint()
            val end = varint()
            val packed = byte().toInt()
            val sized = if (bool()) float() else null
            val font = if (fonted && bool()) string() else null
            TextSpan(start, end, unpackLook(packed, sized).copy(font = font))
        }
        val withFont = if (fonted) style.copy(font = string()) else style
        val withList = if (listed) {
            withFont.copy(list = ListStyle(LIST_KINDS.read(this), dotColor = int(), dotSize = float(), dotOpacity = float()).clamped())
        } else {
            withFont
        }
        TextItem(id, frame, text, size, color, withList, locked, spans)
    }
    TAG_TEXT -> TextItem(
        id = long(),
        locked = locked(),
        frame = frame(),
        text = string(),
        textSize = float(),
        color = int(),
    )
    TAG_STICKY -> StickyNoteItem(
        id = long(),
        locked = locked(),
        frame = frame(),
        color = int(),
        isOpen = bool(),
        closedWidth = float(),
        closedHeight = float(),
        keepOpen = if (version >= 5) bool() else false,
        items = items(),
    )
    TAG_TABLE -> table()
    TAG_STYLED_TABLE -> table().let { plain ->
        plain.copy(headerRow = bool(), headerColumn = bool(), fillColor = int())
    }
    TAG_COLORED_TABLE -> table().let { plain ->
        plain.copy(headerRow = bool(), headerColumn = bool(), fillColor = int(), headerColor = int())
    }
    TAG_VIDEO -> VideoItem(
        id = long(),
        locked = locked(),
        frame = frame(),
        assetId = string(),
        posterAssetId = string(),
        durationMs = long(),
    )
    TAG_AUDIO -> AudioItem(
        id = long(),
        locked = locked(),
        frame = frame(),
        assetId = string(),
        durationMs = long(),
    )
    else -> throw CorruptFileException("Unknown item tag $tag")
}

private fun ByteWriter.inkStyle(style: InkStyle) {
    INK_TOOLS.write(this, style.tool)
    PEN_TYPES.write(this, style.penType)
    // The tip's byte has room: the top bit says the line follows the pen's pressure. Older files never set it.
    byte(TIP_SHAPES.coded.indexOf(style.tip) or if (style.pressure) PRESSURE_BIT else 0)
    int(style.color)
    float(style.width)
    float(style.opacity)
    lineStyle(style.line)
    if (version >= 3) BRUSH_KINDS.write(this, style.brush)
    if (version >= 5) bool(style.noOverlap)
}

private const val PRESSURE_BIT = 0x80

private fun ByteReader.inkStyle(): InkStyle {
    val tool = INK_TOOLS.read(this)
    val penType = PEN_TYPES.read(this)
    val tipByte = byte()
    val tip = TIP_SHAPES.coded.getOrNull(tipByte and PRESSURE_BIT.inv()) ?: throw CorruptFileException("Unknown tip $tipByte")
    return InkStyle(
        tool = tool,
        penType = penType,
        tip = tip,
        color = int(),
        width = float(),
        opacity = float(),
        line = lineStyle(),
        brush = if (version >= 3) BRUSH_KINDS.read(this) else BrushKind.PENCIL,
        noOverlap = if (version >= 5) bool() else false,
        pressure = tipByte and PRESSURE_BIT != 0,
    )
}

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

/**
 * What every item says after its id: since version 5 a flags byte, whose first bit is locked. Since version 9 its second bit says a group
 * id follows, so an item that is in no group takes the byte it always did.
 */
private fun ByteWriter.locked(item: PageItem) {
    if (version < 5) return
    val grouped = version >= 9 && item.group != 0L
    byte((if (item.locked) FLAG_LOCKED else 0) or (if (grouped) FLAG_GROUPED else 0))
    if (grouped) long(item.group)
}

/** Reads the flags and returns locked. The group, if any, is queued for [item], which puts it on the item it finishes. */
private fun ByteReader.locked(): Boolean {
    var locked = false
    var group = 0L
    if (version >= 5) {
        val flags = byte()
        val known = if (version >= 9) FLAG_LOCKED or FLAG_GROUPED else FLAG_LOCKED
        if (flags and known.inv() != 0) throw CorruptFileException("Bad item flags $flags")
        locked = flags and FLAG_LOCKED != 0
        if (flags and FLAG_GROUPED != 0) group = long()
    }
    groups.addLast(group)
    return locked
}

private const val FLAG_LOCKED = 1
private const val FLAG_GROUPED = 2

/** Four on, off or unset choices, two bits each: 0 unset, 1 off, 2 on. */
private fun packLook(look: SpanLook): Int {
    fun bits(v: Boolean?) = when (v) {
        null -> 0
        false -> 1
        true -> 2
    }
    return bits(look.bold) or (bits(look.italic) shl 2) or (bits(look.underline) shl 4) or (bits(look.strikethrough) shl 6)
}

private fun unpackLook(packed: Int, size: Float?): SpanLook {
    fun flag(shift: Int) = when ((packed shr shift) and 3) {
        1 -> false
        2 -> true
        else -> null
    }
    return SpanLook(flag(0), flag(2), flag(4), flag(6), size)
}
