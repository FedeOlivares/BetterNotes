package app.betternotes.core.model

import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame

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
enum class BrushKind { PENCIL, OIL, SPILL, WATERCOLOR, COMICS, SCRATCHES, DOTS, LEAVES, CROSS, GLOW }

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
)

/**
 * Anything placed on a page. Items are immutable, so an edit replaces an item and an undo puts the
 * old one back, and a background renderer can read them without locks.
 */
sealed interface PageItem {
    val id: Long

    /** A box containing everything the item paints, in the coordinates of its page. */
    val bounds: Bounds

    /** A copy moved and scaled by [t], which must not flip. */
    fun transformed(t: Affine): PageItem
}

data class StrokeItem(
    override val id: Long,
    val style: InkStyle,
    val points: StrokePoints,
) : PageItem {
    init {
        require(points.size > 0) { "A stroke needs a point" }
    }

    // Square tips reach further on the diagonal, and sqrt(2) / 2 of the width covers them too.
    override val bounds = points.bounds().outset(style.width * SQUARE_TIP_REACH)

    override fun transformed(t: Affine): StrokeItem {
        val factor = t.meanScale
        val scaledStyle = style.copy(width = style.width * factor, line = style.line.scaled(factor))
        return copy(style = scaledStyle, points = points.transformed(t))
    }

    private companion object {
        const val SQUARE_TIP_REACH = 0.7072f
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
) : PageItem {
    override val bounds get() = frame.bounds.outset(strokeWidth / 2f)

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
) : PageItem {
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
)

data class TextItem(
    override val id: Long,
    val frame: Frame,
    val text: String,
    val textSize: Float = 12f,
    val color: Int = 0xFF000000.toInt(),
    val style: TextStyle = TextStyle(),
) : PageItem {
    override val bounds get() = frame.bounds

    override fun transformed(t: Affine) =
        copy(frame = frame.transformed(t), textSize = textSize * t.meanScale)
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
) : PageItem {
    init {
        require(items.none { it is StickyNoteItem }) { "Sticky notes do not nest" }
    }

    /** The rectangle the note occupies in its current open or closed state. */
    val visibleFrame
        get() = if (isOpen) frame else frame.copy(width = closedWidth, height = closedHeight)

    override val bounds get() = visibleFrame.bounds

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
) : PageItem {
    init {
        require(columnWeights.isNotEmpty() && rowWeights.isNotEmpty()) { "A table needs cells" }
        require(cells.size == columnWeights.size * rowWeights.size) {
            "${cells.size} cells in a ${rowWeights.size} x ${columnWeights.size} table"
        }
    }

    override val bounds get() = frame.bounds.outset(lineWidth / 2f)

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
) : PageItem {
    override val bounds get() = frame.bounds

    override fun transformed(t: Affine) = copy(frame = frame.transformed(t))
}

/** A voice recording placed on the page as a marker. The audio file is stored under [assetId]. */
data class AudioItem(
    override val id: Long,
    val frame: Frame,
    val assetId: String,
    val durationMs: Long,
) : PageItem {
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
}
