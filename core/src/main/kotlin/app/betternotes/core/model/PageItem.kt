package app.betternotes.core.model

import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import app.betternotes.core.tools.ShapeTips

/** Makes ids for items, pages and documents. Random ids need no counter that a crash could rewind. */
fun interface IdSource {
    fun next(): Long

    companion object {
        val Random = IdSource { kotlin.random.Random.nextLong() }
    }
}

enum class LineKind { SOLID, DOTTED, DASHED }

/** How a line is broken up. [dashLength] and [gapLength] apply to the dotted and dashed kinds. */
data class LineStyle(
    val kind: LineKind = LineKind.SOLID,
    val dashLength: Float = 6f,
    val gapLength: Float = 4f,
) {
    fun scaled(factor: Float) = copy(dashLength = dashLength * factor, gapLength = gapLength * factor)

    companion object {
        val Solid = LineStyle()
    }
}

enum class InkTool { PEN, PENCIL, HIGHLIGHTER }

enum class PenType { NATURAL, BALL, CALLIGRAPHY }

enum class TipShape { ROUND, SQUARE }

/** The kinds of the creative brush, which is what the pencil slot of the toolbar holds. */
enum class BrushKind { PENCIL, OIL, SPILL, WATERCOLOR, COMICS, SCRATCHES, STRIPES, DOTS, LEAVES, CROSS, GLOW, SINGLE_DOTS }

/** [color] is ARGB, and [opacity] multiplies its alpha. [width] is the widest the line can get, in points. */
data class InkStyle(
    val tool: InkTool = InkTool.PEN,
    val penType: PenType = PenType.NATURAL,
    val tip: TipShape = TipShape.ROUND,
    val color: Int = 0xFF000000.toInt(),
    val width: Float = 2f,
    val opacity: Float = 1f,
    val line: LineStyle = LineStyle.Solid,
    /** Which creative brush a pencil-slot stroke was drawn with. The pen and the highlighter ignore it. */
    val brush: BrushKind = BrushKind.PENCIL,
    /** Only for the highlighter: strokes of one color overlap without getting darker. */
    val noOverlap: Boolean = false,
    /** The line gets thinner where the pen presses lightly. The natural pen always does, so it ignores this. */
    val pressure: Boolean = false,
)

/**
 * Anything placed on a page. Items are immutable, so an edit replaces an item and an undo puts the
 * old one back, and a background renderer can read them without locks.
 */
sealed interface PageItem {
    val id: Long

    /** A locked item can be selected but not moved, resized or deleted, and the lasso and eraser skip it unless asked not to. */
    val locked: Boolean

    /** The group the item belongs to, shared by every item that was grouped together, or 0 for an item on its own. Items of a group are selected together. */
    val group: Long

    /** The id of the layer of its page that holds the item. 0 is the layer every page starts with. See [PageLayers]. */
    val layer: Long

    /** A copy on [layer]. */
    fun onLayer(layer: Long): PageItem

    /** A box containing everything the item paints, in the coordinates of its page. */
    val bounds: Bounds

    /** A box containing every pixel the item can paint, which for a stamped brush is wider than [bounds]. */
    val paintBounds: Bounds get() = bounds

    /** A copy moved and scaled by [t], which must not flip. */
    fun transformed(t: Affine): PageItem
}

data class StrokeItem(
    override val id: Long,
    val style: InkStyle,
    val points: StrokePoints,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): StrokeItem = if (layer == this.layer) this else copy(layer = layer)

    init {
        require(points.size > 0) { "A stroke needs a point" }
    }

    // Square tips reach further on the diagonal, and sqrt(2) / 2 of the width covers them too.
    override val bounds = points.bounds().outset(style.width * SQUARE_TIP_REACH)

    // The creative brushes throw stamps and particles well past the line. Selection boxes and hit tests keep to bounds.
    override val paintBounds = points.bounds().outset(style.width * if (style.tool == InkTool.PENCIL) CREATIVE_BRUSH_REACH else PAINT_REACH)

    override fun transformed(t: Affine): StrokeItem {
        val factor = t.meanScale
        val scaledStyle = style.copy(width = style.width * factor, line = style.line.scaled(factor))
        return copy(style = scaledStyle, points = points.transformed(t))
    }

    private companion object {
        const val SQUARE_TIP_REACH = 0.7072f
        const val CREATIVE_BRUSH_REACH = 2.5f

        // The smoothed line can swing past its samples and past the square-tip reach, so what tiles cut and redraw is wider than bounds.
        const val PAINT_REACH = 1.2f
    }
}

enum class ShapeKind {
    LINE,
    ARROW,
    RECTANGLE,
    ELLIPSE,
    TRIANGLE,
    DIAMOND,
    PARALLELOGRAM,
    TRAPEZOID,
    BLOCK_ARROW,
    CURVE,
    PENTAGON,
    HEXAGON,
    PIE,
    ARC,
    STAR,
    SPHERE,
    PYRAMID,
    CYLINDER,
    CUBE,
    TETRAHEDRON,
    PRISM,
    DISCS,
    SINE,
    COSINE,
    AXES,
    PARABOLA,
    CUBIC,
    BRACE,
    HEART,
    SPEECH_BOX,
    SPEECH_ROUND,
    THOUGHT,
    TAPE,
}

/** What is printed on a strip of tape, in white. */
enum class TapePattern { PLAIN, DOTS, LINES, CROSSHATCH }

/** A tape's drop shadow: how far it falls, in points, at which angle on the page, and how dark. */
data class TapeShadow(val distance: Float = 0f, val angleDegrees: Float = 45f, val opacity: Float = 0f) {
    val isVisible get() = distance > 0f && opacity > 0f

    companion object {
        val NONE = TapeShadow()
    }
}

/**
 * A vector shape. A line or arrow runs along the frame's horizontal center, from left to right,
 * so its direction comes from the frame's rotation and its height is zero. A [fillColor] with zero
 * alpha means no fill.
 */
data class ShapeItem(
    override val id: Long,
    val frame: Frame,
    val kind: ShapeKind,
    val strokeColor: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 2f,
    val line: LineStyle = LineStyle.Solid,
    val fillColor: Int = 0,
    val cornerRadius: Float = 0f,
    val opacity: Float = 1f,
    /** The size of the shape's tip, cap or depth that its handles change. 0 means the shape's own proportions. */
    val tipLength: Float = 0f,
    val tipWidth: Float = 0f,
    /** Only for tape: what is printed on it, and its shadow. */
    val pattern: TapePattern = TapePattern.PLAIN,
    val shadow: TapeShadow = TapeShadow.NONE,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
    /** Turned over from left to right inside its frame, as in a mirror. The frame and its handles stay where they are. */
    val mirrored: Boolean = false,
) : PageItem {
    override fun onLayer(layer: Long): ShapeItem = if (layer == this.layer) this else copy(layer = layer)

    /** The frame, and for a speech or thought bubble also the tail, which can reach out of it in any direction. */
    override val bounds: Bounds
        get() {
            val box = frame.bounds.outset(strokeWidth / 2f + if (shadow.isVisible) shadow.distance + SHADOW_SOFTNESS else 0f)
            if (!ShapeTips.isBubble(kind) || frame.width <= 0f || frame.height <= 0f) return box
            val tip = ShapeTips.speechTip(this)
            val x = frame.worldX(if (mirrored) 1f - tip[0] / frame.width else tip[0] / frame.width, tip[1] / frame.height)
            val y = frame.worldY(tip[0] / frame.width, tip[1] / frame.height)
            val pad = strokeWidth / 2f + 0.06f * maxOf(frame.width, frame.height)
            return Bounds(minOf(box.left, x - pad), minOf(box.top, y - pad), maxOf(box.right, x + pad), maxOf(box.bottom, y + pad))
        }

    /**
     * A miter join of the stroke reaches up to [MITER_REACH] times the line's width past a corner, and the tip of an arrow or axis
     * can lie past a frame that is as thin as its line.
     */
    override val paintBounds: Bounds
        get() {
            val tip = when (kind) {
                ShapeKind.ARROW, ShapeKind.AXES, ShapeKind.BLOCK_ARROW ->
                    maxOf(ShapeTips.width(kind, frame.width, frame.height, tipLength, tipWidth) / 2f, ShapeTips.length(kind, frame.width, frame.height, tipLength))
                else -> 0f
            }
            return bounds.outset(strokeWidth * MITER_REACH + tip)
        }

    val isFilled get() = fillColor ushr 24 != 0

    override fun transformed(t: Affine): ShapeItem {
        val factor = t.meanScale
        return copy(
            frame = frame.transformed(t),
            strokeWidth = strokeWidth * factor,
            line = line.scaled(factor),
            cornerRadius = cornerRadius * factor,
            tipLength = if (kind == ShapeKind.PIE) tipLength else tipLength * factor,
            tipWidth = tipWidth * factor,
        )
    }
}

enum class ImageShape { RECTANGLE, ELLIPSE }

/** A picture stored as a file in the document's assets, under [assetId]. */
data class ImageItem(
    override val id: Long,
    val frame: Frame,
    val assetId: String,
    val shape: ImageShape = ImageShape.RECTANGLE,
    val cornerRadius: Float = 0f,
    val borderColor: Int = 0,
    val borderWidth: Float = 0f,
    /** 1 is solid; less lets what is under the picture show through. */
    val opacity: Float = 1f,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): ImageItem = if (layer == this.layer) this else copy(layer = layer)

    override val bounds get() = frame.bounds.outset(borderWidth / 2f)

    override fun transformed(t: Affine): ImageItem {
        val factor = t.meanScale
        return copy(
            frame = frame.transformed(t),
            cornerRadius = cornerRadius * factor,
            borderWidth = borderWidth * factor,
        )
    }
}

enum class HorizontalAlign { START, CENTER, END }

enum class VerticalAlign { TOP, MIDDLE, BOTTOM }

/** How typed text looks, beyond its size and color. */
data class TextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val align: HorizontalAlign = HorizontalAlign.START,
    val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    /** The added font the text is set in, by its name; empty is the app's own font. A font that is missing shows as the app's own. */
    val font: String = "",
    /** Null is a text that is not a list. */
    val list: ListStyle? = null,
    /** The fill behind the text box, as ARGB; an alpha of 0 is no fill. */
    val backgroundColor: Int = 0,
) {
    val hasBackground get() = backgroundColor ushr 24 != 0
}

/**
 * How a part of a text differs from the text as a whole: a field left null keeps what the text box itself says.
 * Only the look of the letters can differ; the alignment belongs to the whole box.
 */
data class SpanLook(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    val size: Float? = null,
    /** An added font by name, or empty for the app's own font; null keeps the text's. */
    val font: String? = null,
    /** The color of these letters, as ARGB; null keeps the color of the text box. */
    val color: Int? = null,
) {
    val isPlain get() = bold == null && italic == null && underline == null && strikethrough == null && size == null && font == null && color == null
}

/** The letters from [start] up to [end], counted in characters of the text, that look as [look] says. */
data class TextSpan(val start: Int, val end: Int, val look: SpanLook)

/**
 * A text box. [spans] hold the parts that differ from [style] and [textSize]; they never overlap, never touch a part
 * that looks the same, and stay inside the text. Use [TextSpans] to change them.
 */
data class TextItem(
    override val id: Long,
    val frame: Frame,
    val text: String,
    val textSize: Float = 12f,
    val color: Int = 0xFF000000.toInt(),
    val style: TextStyle = TextStyle(),
    override val locked: Boolean = false,
    val spans: List<TextSpan> = emptyList(),
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): TextItem = if (layer == this.layer) this else copy(layer = layer)

    override val bounds get() = frame.bounds

    /** The page does not clip text to its box, so what the text paints is read from its layout. See [TextReach]. */
    override val paintBounds: Bounds by lazy {
        bounds.union(TextReach.inDocument(frame, TextReach.localBox(text, textSize, style, spans, frame.width, frame.height)))
    }

    override fun transformed(t: Affine) = copy(
        frame = frame.transformed(t),
        textSize = textSize * t.meanScale,
        spans = spans.map { span -> span.look.size?.let { span.copy(look = span.look.copy(size = it * t.meanScale)) } ?: span },
    )
}

/** Changing the parts of a text that look different, one character at a time, then joining what looks alike. */
object TextSpans {
    /**
     * The characters of [new] that were typed in, found as what lies between the text [old] shares with it at the start and at the end, or
     * null when nothing was added. A text that was only cut or left as it was adds nothing.
     */
    fun inserted(old: String, new: String): IntRange? {
        if (new.length <= old.length) return null
        var head = 0
        while (head < old.length && old[head] == new[head]) head++
        var tail = 0
        while (tail < old.length - head && old[old.length - 1 - tail] == new[new.length - 1 - tail]) tail++
        val end = new.length - tail
        return if (end > head) head until end else null
    }

    /** The look of each of the [length] characters. */
    fun looks(spans: List<TextSpan>, length: Int): Array<SpanLook> {
        val looks = Array(length) { SpanLook() }
        for (span in spans) for (i in span.start.coerceAtLeast(0) until span.end.coerceAtMost(length)) looks[i] = span.look
        return looks
    }

    /** The spans that give back [looks]: a run of the same look is one span, and a plain run is none. */
    fun spansOf(looks: Array<SpanLook>): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        var i = 0
        while (i < looks.size) {
            var j = i + 1
            while (j < looks.size && looks[j] == looks[i]) j++
            if (!looks[i].isPlain) out += TextSpan(i, j, looks[i])
            i = j
        }
        return out
    }

    /** [spans] with [change] made to the look of the characters from [start] up to [end]. */
    fun change(spans: List<TextSpan>, length: Int, start: Int, end: Int, change: (SpanLook) -> SpanLook): List<TextSpan> {
        val looks = looks(spans, length)
        for (i in start.coerceAtLeast(0) until end.coerceAtMost(length)) looks[i] = change(looks[i])
        return spansOf(looks)
    }

    /**
     * [spans] after the text went from [old] to [new] by typing, pasting or deleting in one place. What is typed
     * looks like the letter before it, or like the first letter when it is typed at the start.
     */
    fun retext(spans: List<TextSpan>, old: String, new: String): List<TextSpan> {
        if (spans.isEmpty() || old == new) return spans
        var prefix = 0
        val shorter = minOf(old.length, new.length)
        while (prefix < shorter && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < shorter - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val before = looks(spans, old.length)
        val looks = Array(new.length) { SpanLook() }
        for (i in 0 until prefix) looks[i] = before[i]
        val inherited = when {
            prefix > 0 -> before[prefix - 1]
            old.isNotEmpty() -> before[0]
            else -> SpanLook()
        }
        for (i in prefix until new.length - suffix) looks[i] = inherited
        for (k in 1..suffix) looks[new.length - k] = before[old.length - k]
        return spansOf(looks)
    }
}

/**
 * A sticky note, smaller when closed. [frame] is its open size, and a closed note shows only
 * [closedWidth] by [closedHeight] at the same top-left corner.
 *
 * Whatever is typed or drawn in the note lives in [items], in coordinates relative to the note's
 * top-left corner, so it moves with the note and cannot mix with the page. Notes do not nest.
 */
data class StickyNoteItem(
    override val id: Long,
    val frame: Frame,
    val color: Int,
    val isOpen: Boolean = false,
    val closedWidth: Float = DEFAULT_CLOSED_SIZE,
    val closedHeight: Float = DEFAULT_CLOSED_SIZE,
    val items: List<PageItem> = emptyList(),
    /** An open note with this stays open when the page is tapped elsewhere. */
    val keepOpen: Boolean = false,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): StickyNoteItem = if (layer == this.layer) this else copy(layer = layer)

    init {
        require(items.none { it is StickyNoteItem }) { "Sticky notes do not nest" }
    }

    /** The rectangle the note occupies in its current open or closed state. */
    val visibleFrame
        get() = if (isOpen) frame else frame.copy(width = closedWidth, height = closedHeight)

    override val bounds get() = visibleFrame.bounds

    /**
     * An open note does not clip what is in it, so a line or tape that runs past its edge is drawn whole. The box takes in everything the
     * items paint, so every tile they touch repaints. Closed, nothing in the note is drawn.
     */
    override val paintBounds: Bounds by lazy {
        val shown = bounds
        if (!isOpen || items.isEmpty()) return@lazy shown
        var inside = items[0].paintBounds
        for (i in 1 until items.size) inside = inside.union(items[i].paintBounds)
        val turn = Math.toRadians(frame.rotation.toDouble())
        val c = kotlin.math.cos(turn).toFloat()
        val s = kotlin.math.sin(turn).toFloat()
        var box = shown
        // The items are in the note's own coordinates, which the page turns about the note's middle.
        for (corner in 0 until 4) {
            val dx = frame.left + (if (corner and 1 == 0) inside.left else inside.right) - frame.centerX
            val dy = frame.top + (if (corner and 2 == 0) inside.top else inside.bottom) - frame.centerY
            val x = frame.centerX + dx * c - dy * s
            val y = frame.centerY + dx * s + dy * c
            box = box.union(Bounds(x, y, x, y))
        }
        box
    }

    override fun transformed(t: Affine): StickyNoteItem {
        val moved = frame.transformed(t)
        val sx = if (frame.width > 0f) moved.width / frame.width else 1f
        val sy = if (frame.height > 0f) moved.height / frame.height else 1f
        val contents = if (sx == 1f && sy == 1f) items else items.map { it.transformed(Affine(a = sx, d = sy)) }
        return copy(frame = moved, items = contents)
    }

    companion object {
        const val DEFAULT_CLOSED_SIZE = 28f
    }
}

/**
 * A table. Row and column sizes are relative weights, so resizing the frame keeps the proportions.
 * [cells] is in row order and holds one string per cell.
 */
data class TableItem(
    override val id: Long,
    val frame: Frame,
    val columnWeights: List<Float>,
    val rowWeights: List<Float>,
    val cells: List<String>,
    val textSize: Float = 12f,
    val lineColor: Int = 0xFF000000.toInt(),
    val lineWidth: Float = 1f,
    val headerRow: Boolean = false,
    val headerColumn: Boolean = false,
    val fillColor: Int = 0,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
    /** The color of the header row and column. 0 is the usual translucent gray. */
    val headerColor: Int = 0,
) : PageItem {
    override fun onLayer(layer: Long): TableItem = if (layer == this.layer) this else copy(layer = layer)

    init {
        require(columnWeights.isNotEmpty() && rowWeights.isNotEmpty()) { "A table needs cells" }
        require(cells.size == columnWeights.size * rowWeights.size) {
            "${cells.size} cells in a ${rowWeights.size} x ${columnWeights.size} table"
        }
    }

    override val bounds get() = frame.bounds.outset(lineWidth / 2f)

    /** A cell's text is not clipped to its cell, so the box takes in what every cell's text paints. */
    override val paintBounds: Bounds by lazy {
        val columnTotal = columnWeights.sum()
        val rowTotal = rowWeights.sum()
        var box = bounds.outset(lineWidth * MITER_REACH)
        if (columnTotal <= 0f || rowTotal <= 0f) return@lazy box
        var y = 0f
        for ((row, rowWeight) in rowWeights.withIndex()) {
            val rowHeight = rowWeight / rowTotal * frame.height
            var x = 0f
            for ((column, columnWeight) in columnWeights.withIndex()) {
                val columnWidth = columnWeight / columnTotal * frame.width
                val cell = cells[row * columnWeights.size + column]
                if (cell.isNotEmpty() && columnWidth - 2 * CELL_PADDING > 0f) {
                    val local = TextReach.localBox(cell, textSize, TextStyle(), emptyList(), columnWidth - 2 * CELL_PADDING, rowHeight - 2 * CELL_PADDING)
                    // The cell's text starts a padding in from the cell's corner.
                    val inTable = floatArrayOf(local[0] + x + CELL_PADDING, local[1] + y + CELL_PADDING, local[2] + x + CELL_PADDING, local[3] + y + CELL_PADDING)
                    box = box.union(TextReach.inDocument(frame, inTable))
                }
                x += columnWidth
            }
            y += rowHeight
        }
        box
    }

    companion object {
        /** Space between a cell's lines and its text, in points. */
        const val CELL_PADDING = 3f
    }

    override fun transformed(t: Affine): TableItem {
        val factor = t.meanScale
        return copy(frame = frame.transformed(t), textSize = textSize * factor, lineWidth = lineWidth * factor)
    }
}

/** A video stored in the document's assets under [assetId], showing the picture under [posterAssetId] until played. */
data class VideoItem(
    override val id: Long,
    val frame: Frame,
    val assetId: String,
    val posterAssetId: String,
    val durationMs: Long,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): VideoItem = if (layer == this.layer) this else copy(layer = layer)

    override val bounds get() = frame.bounds

    override fun transformed(t: Affine) = copy(frame = frame.transformed(t))
}

/** A voice recording placed on the page as a marker. The audio file is stored under [assetId]. */
data class AudioItem(
    override val id: Long,
    val frame: Frame,
    val assetId: String,
    val durationMs: Long,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): AudioItem = if (layer == this.layer) this else copy(layer = layer)

    override val bounds get() = frame.bounds

    override fun transformed(t: Affine) = copy(frame = frame.transformed(t))
}

/** A copy under a new [id], for duplicating an item. */
fun PageItem.withId(id: Long): PageItem = when (this) {
    is StrokeItem -> copy(id = id)
    is ShapeItem -> copy(id = id)
    is ImageItem -> copy(id = id)
    is TextItem -> copy(id = id)
    is StickyNoteItem -> copy(id = id)
    is TableItem -> copy(id = id)
    is VideoItem -> copy(id = id)
    is AudioItem -> copy(id = id)
    is TapePathItem -> copy(id = id)
    is FillItem -> copy(id = id)
    is PathShapeItem -> copy(id = id)
}

/** A copy in group [group], or out of any group when it is 0. */
fun PageItem.withGroup(group: Long): PageItem = when (this) {
    is StrokeItem -> copy(group = group)
    is ShapeItem -> copy(group = group)
    is ImageItem -> copy(group = group)
    is TextItem -> copy(group = group)
    is StickyNoteItem -> copy(group = group)
    is TableItem -> copy(group = group)
    is VideoItem -> copy(group = group)
    is AudioItem -> copy(group = group)
    is TapePathItem -> copy(group = group)
    is FillItem -> copy(group = group)
    is PathShapeItem -> copy(group = group)
}

/** A copy locked or unlocked. */
fun PageItem.withLocked(locked: Boolean): PageItem = when (this) {
    is StrokeItem -> copy(locked = locked)
    is ShapeItem -> copy(locked = locked)
    is ImageItem -> copy(locked = locked)
    is TextItem -> copy(locked = locked)
    is StickyNoteItem -> copy(locked = locked)
    is TableItem -> copy(locked = locked)
    is VideoItem -> copy(locked = locked)
    is AudioItem -> copy(locked = locked)
    is TapePathItem -> copy(locked = locked)
    is FillItem -> copy(locked = locked)
    is PathShapeItem -> copy(locked = locked)
}

/**
 * A strip of tape laid along the pen's path, as wide as [width] and following every bend. [points] are packed
 * x,y pairs along its middle. A pattern and the warning stripes run along the strip, turning with it.
 */
class TapePathItem(
    override val id: Long,
    val points: FloatArray,
    val width: Float,
    val fillColor: Int,
    val opacity: Float = 1f,
    val pattern: TapePattern = TapePattern.PLAIN,
    val shadow: TapeShadow = TapeShadow.NONE,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): TapePathItem = if (layer == this.layer) this else copy(layer = layer)

    val pointCount get() = points.size / 2

    override val bounds: Bounds
        get() {
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (i in points.indices step 2) {
                left = minOf(left, points[i])
                right = maxOf(right, points[i])
                top = minOf(top, points[i + 1])
                bottom = maxOf(bottom, points[i + 1])
            }
            if (left > right) return Bounds(0f, 0f, 0f, 0f)
            val reach = width / 2f + if (shadow.isVisible) shadow.distance + SHADOW_SOFTNESS else SHADOW_SOFTNESS
            return Bounds(left - reach, top - reach, right + reach, bottom + reach)
        }

    override fun transformed(t: Affine): TapePathItem {
        val moved = FloatArray(points.size)
        for (i in points.indices step 2) {
            moved[i] = t.mapX(points[i], points[i + 1])
            moved[i + 1] = t.mapY(points[i], points[i + 1])
        }
        return copy(points = moved, width = width * t.meanScale)
    }

    fun copy(
        id: Long = this.id,
        points: FloatArray = this.points,
        width: Float = this.width,
        fillColor: Int = this.fillColor,
        opacity: Float = this.opacity,
        pattern: TapePattern = this.pattern,
        shadow: TapeShadow = this.shadow,
        locked: Boolean = this.locked,
        group: Long = this.group,
        layer: Long = this.layer,
    ) = TapePathItem(id, points, width, fillColor, opacity, pattern, shadow, locked, group, layer)

    override fun equals(other: Any?) = other is TapePathItem && id == other.id && width == other.width &&
        fillColor == other.fillColor && opacity == other.opacity && pattern == other.pattern && shadow == other.shadow && locked == other.locked && group == other.group && layer == other.layer && layer == other.layer &&
        points.contentEquals(other.points)

    override fun hashCode() = 31 * id.hashCode() + points.contentHashCode()
}

/** How far a miter join reaches past the geometry of a line, as a share of the line's width: the paint's default miter limit of 4, over two. */
const val MITER_REACH = 2f

/** How far the soft edge of a drop shadow reaches beyond its offset, in points. */
const val SHADOW_SOFTNESS = 2f

/**
 * A region filled with one color by the paint bucket: closed [loops] of packed x,y pairs in page points, filled with the even-odd rule, so
 * a loop inside another is a hole. It is a vector, so it stays sharp at any zoom, and an item like any other for undo, the lasso and the eraser.
 */
class FillItem(
    override val id: Long,
    val loops: List<FloatArray>,
    val color: Int,
    val opacity: Float = 1f,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
    /** What is drawn over the color, clipped to the loops. Solid, the default, is the color alone. */
    val pattern: FillPattern = FillPattern.SOLID,
) : PageItem {
    override fun onLayer(layer: Long): FillItem = if (layer == this.layer) this else copy(layer = layer)

    override val bounds: Bounds
        get() {
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (loop in loops) {
                for (i in loop.indices step 2) {
                    left = minOf(left, loop[i])
                    right = maxOf(right, loop[i])
                    top = minOf(top, loop[i + 1])
                    bottom = maxOf(bottom, loop[i + 1])
                }
            }
            return if (left > right) Bounds(0f, 0f, 0f, 0f) else Bounds(left, top, right, bottom)
        }

    override fun transformed(t: Affine) = copy(
        loops = loops.map { loop ->
            FloatArray(loop.size).also { moved ->
                for (i in loop.indices step 2) {
                    moved[i] = t.mapX(loop[i], loop[i + 1])
                    moved[i + 1] = t.mapY(loop[i], loop[i + 1])
                }
            }
        },
    )

    /** The area the fill covers in square points: the loops' areas, the outer ones adding and the holes taking away. */
    val area: Float
        get() {
            var total = 0.0
            for (loop in loops) {
                var twice = 0.0
                var j = loop.size - 2
                for (i in loop.indices step 2) {
                    twice += (loop[j].toDouble() * loop[i + 1] - loop[i].toDouble() * loop[j + 1])
                    j = i
                }
                total += twice / 2.0
            }
            return Math.abs(total).toFloat()
        }

    /** True if the page point is inside the fill: inside an odd number of its loops. */
    fun contains(x: Float, y: Float): Boolean = loops.count { loopContains(it, x, y) } % 2 == 1

    fun copy(
        id: Long = this.id,
        loops: List<FloatArray> = this.loops,
        color: Int = this.color,
        opacity: Float = this.opacity,
        locked: Boolean = this.locked,
        group: Long = this.group,
        layer: Long = this.layer,
        pattern: FillPattern = this.pattern,
    ) = FillItem(id, loops, color, opacity, locked, group, layer, pattern)

    override fun equals(other: Any?) = other is FillItem && id == other.id && color == other.color && opacity == other.opacity &&
        locked == other.locked && group == other.group && layer == other.layer && pattern == other.pattern && loops.size == other.loops.size && loops.indices.all { loops[it].contentEquals(other.loops[it]) }

    override fun hashCode() = 31 * id.hashCode() + loops.sumOf { it.contentHashCode() }

    private fun loopContains(loop: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        var j = loop.size - 2
        for (i in loop.indices step 2) {
            val xi = loop[i]
            val yi = loop[i + 1]
            val xj = loop[j]
            val yj = loop[j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/**
 * A shape made of points that can each be moved: the corners of a polygon such as a triangle, or the control points a
 * smooth curve runs through. [points] are packed x,y pairs. A [closed] shape joins its last point to its first, and a
 * [smooth] one is drawn through its points as a curve instead of with straight sides. A [fillColor] with zero alpha
 * means no fill, and only a closed shape fills.
 */
class PathShapeItem(
    override val id: Long,
    val points: FloatArray,
    val closed: Boolean,
    val smooth: Boolean,
    val strokeColor: Int = 0xFF000000.toInt(),
    val strokeWidth: Float = 2f,
    val line: LineStyle = LineStyle.Solid,
    val fillColor: Int = 0,
    val opacity: Float = 1f,
    override val locked: Boolean = false,
    override val group: Long = 0L,
    override val layer: Long = 0L,
) : PageItem {
    override fun onLayer(layer: Long): PathShapeItem = if (layer == this.layer) this else copy(layer = layer)

    init {
        require(points.size >= 4 && points.size % 2 == 0) { "A path needs two points" }
    }

    val pointCount get() = points.size / 2

    val isFilled get() = closed && fillColor ushr 24 != 0

    /** The curve's bulges can reach past its control points, so the box takes in the drawn outline. */
    override val bounds: Bounds
        get() {
            val outline = outline()
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (i in outline.indices step 2) {
                left = minOf(left, outline[i])
                right = maxOf(right, outline[i])
                top = minOf(top, outline[i + 1])
                bottom = maxOf(bottom, outline[i + 1])
            }
            return Bounds(left, top, right, bottom).outset(strokeWidth / 2f)
        }

    override val paintBounds: Bounds get() = bounds.outset(strokeWidth * (MITER_REACH - 0.5f))

    /** The line as packed x,y pairs: the points themselves, or for a smooth shape a polyline close enough to the curve. */
    fun outline(): FloatArray = if (smooth && pointCount > 2) smoothOutline(points, closed) else points

    override fun transformed(t: Affine): PathShapeItem {
        val moved = FloatArray(points.size)
        for (i in points.indices step 2) {
            moved[i] = t.mapX(points[i], points[i + 1])
            moved[i + 1] = t.mapY(points[i], points[i + 1])
        }
        val factor = t.meanScale
        return copy(points = moved, strokeWidth = strokeWidth * factor, line = line.scaled(factor))
    }

    fun copy(
        id: Long = this.id,
        points: FloatArray = this.points,
        closed: Boolean = this.closed,
        smooth: Boolean = this.smooth,
        strokeColor: Int = this.strokeColor,
        strokeWidth: Float = this.strokeWidth,
        line: LineStyle = this.line,
        fillColor: Int = this.fillColor,
        opacity: Float = this.opacity,
        locked: Boolean = this.locked,
        group: Long = this.group,
        layer: Long = this.layer,
    ) = PathShapeItem(id, points, closed, smooth, strokeColor, strokeWidth, line, fillColor, opacity, locked, group, layer)

    override fun equals(other: Any?) = other is PathShapeItem && id == other.id && closed == other.closed && smooth == other.smooth &&
        strokeColor == other.strokeColor && strokeWidth == other.strokeWidth && line == other.line && fillColor == other.fillColor &&
        opacity == other.opacity && locked == other.locked && group == other.group && layer == other.layer && points.contentEquals(other.points)

    override fun hashCode() = 31 * id.hashCode() + points.contentHashCode()
}

private const val CURVE_STEPS = 12

/**
 * A Catmull-Rom curve through [points] (packed x,y pairs) as a polyline of [CURVE_STEPS] pieces per span. An open curve
 * runs from the first point to the last; a closed one comes round to the first again.
 */
fun smoothOutline(points: FloatArray, closed: Boolean): FloatArray {
    val n = points.size / 2
    val spans = if (closed) n else n - 1
    val out = FloatArray((spans * CURVE_STEPS + 1) * 2)
    fun px(k: Int) = points[(if (closed) Math.floorMod(k, n) else k.coerceIn(0, n - 1)) * 2]
    fun py(k: Int) = points[(if (closed) Math.floorMod(k, n) else k.coerceIn(0, n - 1)) * 2 + 1]
    var o = 0
    for (s in 0 until spans) {
        for (step in 0 until CURVE_STEPS) {
            val t = step / CURVE_STEPS.toFloat()
            out[o++] = catmull(px(s - 1), px(s), px(s + 1), px(s + 2), t)
            out[o++] = catmull(py(s - 1), py(s), py(s + 1), py(s + 2), t)
        }
    }
    out[o++] = px(spans)
    out[o] = py(spans)
    return out
}

private fun catmull(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (2f * p1 + (p2 - p0) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (3f * p1 - p0 - 3f * p2 + p3) * t3)
}
