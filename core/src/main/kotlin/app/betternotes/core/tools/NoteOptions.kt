package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextStyle

/** What the sticky note and text tools write with. [colors] are the three note colors in the toolbar. */
data class NoteOptions(
    val colors: List<Int> = STICKY_PALETTE.subList(3, 3 + COLOR_SLOTS),
    val selectedColor: Int = 1,
    val textSize: Int = 9,
    val style: TextStyle = TextStyle(),
    /** The color new text is written in, as ARGB. */
    val textColor: Int = TEXT_COLOR,
    /** New sticky notes stay open when the page is tapped elsewhere. */
    val keepOpen: Boolean = false,
    /**
     * Colors that have an opacity of their own, by their color without alpha. Every other color follows the general opacity, which is a
     * setting and not part of the tool.
     */
    val colorOpacity: Map<Int, Float> = emptyMap(),
) {
    val color get() = colors[selectedColor]

    /** The opacity a note of [rgb] gets: its own, or else [general]. */
    fun opacityOf(rgb: Int, general: Float): Float = colorOpacity[rgb and RGB_MASK] ?: general

    /** [rgb] with an opacity of its own, or following the general opacity when [opacity] is null. */
    fun withOpacity(rgb: Int, opacity: Float?): NoteOptions {
        val key = rgb and RGB_MASK
        return copy(colorOpacity = if (opacity == null) colorOpacity - key else colorOpacity + (key to opacity.coerceIn(MIN_OPACITY, 1f)))
    }

    fun withColor(value: Int): NoteOptions {
        val next = colors.toMutableList()
        next[selectedColor] = value
        return copy(colors = next)
    }

    /** A new, open note with its corner at the given point. */
    fun newSticky(x: Float, y: Float, ids: IdSource = IdSource.Random, general: Float = 1f) = StickyNoteItem(
        id = ids.next(),
        frame = Frame(x, y, STICKY_SIZE, STICKY_SIZE),
        color = argb(color, opacityOf(color, general)),
        isOpen = true,
        keepOpen = keepOpen,
    )

    companion object {
        const val COLOR_SLOTS = 3
        const val RGB_MASK = 0xFFFFFF

        /** The least a note shows of its color: a note that cannot be seen cannot be found to be changed. */
        const val MIN_OPACITY = 0.05f

        /** [color] with its alpha set to [opacity]. */
        fun argb(color: Int, opacity: Float): Int = ((opacity.coerceIn(MIN_OPACITY, 1f) * 255f + 0.5f).toInt() shl 24) or (color and RGB_MASK)

        /** How opaque [color] is, from 0 to 1. */
        fun alphaOf(color: Int): Float = (color ushr 24) / 255f
        const val MIN_TEXT = 4
        const val MAX_TEXT = 72
        const val STICKY_SIZE = 200f
        const val STICKY_TEXT_INSET = 8f
        const val TEXT_COLOR = 0xFF1E1F24.toInt()

        val STICKY_PALETTE: List<Int> = listOf(
            0xFFEDEDED.toInt(),
            0xFFF6A9A0.toInt(),
            0xFFF8C9A0.toInt(),
            0xFFFBDD8C.toInt(),
            0xFFA9E5B5.toInt(),
            0xFF9FE6E0.toInt(),
            0xFFA8D8FF.toInt(),
            0xFFCFC0F5.toInt(),
            0xFFFFA6DC.toInt(),
        )
    }
}

/** What the table tool makes. [widths] and [colors] follow the shape tool's unit and layout. */
data class TableOptions(
    val columns: Int = 3,
    val rows: Int = 3,
    val headerColumn: Boolean = true,
    val headerRow: Boolean = true,
    val borderWidth: Int = 3,
    val colors: List<Int> = listOf(0xFF000000.toInt(), 0xFFD10101.toInt(), 0xFF04CEFF.toInt()),
    val selectedColor: Int = 0,
    val fillColor: Int = 0,
    /** 0 is the usual translucent gray. */
    val headerColor: Int = 0,
) {
    val color get() = colors[selectedColor]

    fun withColor(value: Int): TableOptions {
        val next = colors.toMutableList()
        next[selectedColor] = value
        return copy(colors = next)
    }

    /** A new, empty table with its corner at the given point. */
    fun newTable(x: Float, y: Float, ids: IdSource = IdSource.Random) = TableItem(
        id = ids.next(),
        frame = Frame(x, y, columns * CELL_WIDTH, rows * CELL_HEIGHT),
        columnWeights = List(columns) { 1f },
        rowWeights = List(rows) { 1f },
        cells = List(columns * rows) { "" },
        lineColor = color,
        lineWidth = borderWidth * InkPreset.POINTS_PER_THICKNESS_UNIT,
        headerRow = headerRow,
        headerColumn = headerColumn,
        fillColor = fillColor,
        headerColor = headerColor,
    )

    companion object {
        const val MAX_CELLS_PER_SIDE = 12
        const val MIN_BORDER = 1
        const val MAX_BORDER = 30
        const val CELL_WIDTH = 80f
        const val CELL_HEIGHT = 28f
    }
}

/** Which cell of a table a point falls in, by its row-order index, or -1 if it is outside. */
fun TableItem.cellAt(x: Float, y: Float): Int {
    val u = x - frame.left
    val v = y - frame.top
    if (u < 0f || v < 0f || u > frame.width || v > frame.height) return -1
    val column = slotAt(columnWeights, u / frame.width)
    val row = slotAt(rowWeights, v / frame.height)
    return row * columnWeights.size + column
}

private fun slotAt(weights: List<Float>, fraction: Float): Int {
    val total = weights.sum()
    var reached = 0f
    for ((index, weight) in weights.withIndex()) {
        reached += weight / total
        if (fraction <= reached) return index
    }
    return weights.lastIndex
}

/** The table with one cell's text changed. */
fun TableItem.withCell(index: Int, text: String) = copy(cells = cells.toMutableList().also { it[index] = text })

/** The note with its typed text changed. The text is the note's first text item, if it has one. */
fun StickyNoteItem.withText(value: String, size: Float, style: TextStyle, ids: IdSource = IdSource.Random): StickyNoteItem {
    val inset = NoteOptions.STICKY_TEXT_INSET
    val existing = items.firstOrNull { it is TextItem } as? TextItem
    val updated = TextItem(
        id = existing?.id ?: ids.next(),
        frame = Frame(inset, inset, frame.width - 2 * inset, frame.height - 2 * inset),
        text = value,
        textSize = size,
        color = NoteOptions.TEXT_COLOR,
        // The background belongs to the text tool; a post-it keeps its text plain.
        style = style.copy(backgroundColor = 0),
    )
    return copy(items = listOf(updated) + items.filter { it !is TextItem })
}

val StickyNoteItem.typedText get() = (items.firstOrNull { it is TextItem } as? TextItem)?.text.orEmpty()
