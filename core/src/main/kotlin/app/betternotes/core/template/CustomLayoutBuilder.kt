package app.betternotes.core.template

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A box around the element, ignoring its line width. */
val LayoutElement.bounds: Bounds
    get() = when (this) {
        is LayoutElement.Line -> Bounds(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))
        is LayoutElement.Rectangle -> Bounds.ofSize(x, y, width, height)
        is LayoutElement.Table -> Bounds.ofSize(x, y, width, height)
        is LayoutElement.Ruled -> Bounds.ofSize(x, y, width, height)
        is LayoutElement.Sticky -> Bounds.ofSize(x, y, width, height)
    }

fun LayoutElement.translated(dx: Float, dy: Float): LayoutElement = when (this) {
    is LayoutElement.Line -> copy(x0 = x0 + dx, y0 = y0 + dy, x1 = x1 + dx, y1 = y1 + dy)
    is LayoutElement.Rectangle -> copy(x = x + dx, y = y + dy)
    is LayoutElement.Table -> copy(x = x + dx, y = y + dy)
    is LayoutElement.Ruled -> copy(x = x + dx, y = y + dy)
    is LayoutElement.Sticky -> copy(x = x + dx, y = y + dy)
}

/** The smallest a box element can be dragged to, in points, so it never collapses to nothing. */
const val MIN_BOX_PT = 12f

/** Corners of a box element by index: top-left, top-right, bottom-right, bottom-left. */
const val CORNER_COUNT = 4

/** The corner points of a box element in the order of [withCorner], or none for a line. */
val LayoutElement.corners: List<Pair<Float, Float>>
    get() {
        if (this is LayoutElement.Line) return emptyList()
        val b = bounds
        return listOf(b.left to b.top, b.right to b.top, b.right to b.bottom, b.left to b.bottom)
    }

/**
 * A box element with its [corner] dragged to ([x], [y]) and the opposite corner kept where it is. The box keeps at
 * least [MIN_BOX_PT] each way. A line comes back unchanged: it is resized by its ends.
 */
fun LayoutElement.withCorner(corner: Int, x: Float, y: Float): LayoutElement {
    if (this is LayoutElement.Line) return this
    val b = bounds
    val left = if (corner == 0 || corner == 3) min(x, b.right - MIN_BOX_PT) else b.left
    val right = if (corner == 1 || corner == 2) max(x, b.left + MIN_BOX_PT) else b.right
    val top = if (corner == 0 || corner == 1) min(y, b.bottom - MIN_BOX_PT) else b.top
    val bottom = if (corner == 2 || corner == 3) max(y, b.top + MIN_BOX_PT) else b.bottom
    val w = right - left
    val h = bottom - top
    return when (this) {
        is LayoutElement.Line -> this
        is LayoutElement.Rectangle -> copy(x = left, y = top, width = w, height = h)
        is LayoutElement.Table -> copy(x = left, y = top, width = w, height = h)
        is LayoutElement.Ruled -> copy(x = left, y = top, width = w, height = h)
        is LayoutElement.Sticky -> copy(x = left, y = top, width = w, height = h)
    }
}

/**
 * The state behind the custom layout builder: a size shown in px, cm or inches, and the lines, boxes,
 * tables and ruled blocks placed on it. Lengths are in points unless a call says otherwise.
 *
 * Not thread safe. [build] gives an immutable layout to save, or to paint as a preview.
 */
class CustomLayoutBuilder(
    var name: String,
    size: PageSize = PageSize.A4,
    /** The unit the builder is showing. Stored values stay in points, so switching loses nothing. */
    var unit: LengthUnit = LengthUnit.CM,
    elements: List<LayoutElement> = emptyList(),
    /** The paper color of the layout. */
    var paper: Int = PageBackground.DEFAULT_PAPER_COLOR,
    /** The pattern or frame under the elements. */
    base: PageTemplate = PageTemplate.Blank,
    /** Whether every other page is mirrored. */
    var bookStyle: Boolean = false,
    /** Fractions for the movable lines of the base; empty for the defaults. */
    frameLines: List<Float> = emptyList(),
) {
    constructor(layout: CustomLayout) : this(
        layout.name, layout.size, layout.displayUnit, layout.elements, layout.paper, layout.base, layout.bookStyle, layout.frameLines,
    )

    var base: PageTemplate = base
        set(value) {
            require(value !is PageTemplate.Custom) { "A layout's base cannot be a custom layout" }
            // Lines moved on one frame mean nothing on another.
            // Changing the spacing puts the ruling back where the spacing says, and leaves the frame lines where they are.
            if (value.kind != field.kind) {
                frameLines = emptyList()
            } else if (value.density != field.density) {
                frameLines = frameLines.take(value.fixedFrameLines)
            }
            field = value
        }

    /** The owner's positions for the movable lines of [base], or empty for the defaults. */
    var frameLines: List<Float> = frameLines
        private set

    /** Moves line [index] of the frame to [fraction] of the page, kept clear of the page's edge and the other lines. */
    fun moveFrameLine(index: Int, fraction: Float) {
        if (index !in base.frameLines(size, frameLines).indices) return
        frameLines = base.movedFrameLines(size, frameLines, index, fraction)
    }

    var size = coerced(size.widthPt, size.heightPt)
        private set

    private val placed = elements.toMutableList()

    val elements: List<LayoutElement> get() = placed

    /** The page's width in [unit]. */
    val width get() = unit.fromPt(size.widthPt)

    /** The page's height in [unit]. */
    val height get() = unit.fromPt(size.heightPt)

    /** Sets the page size, in [unit]. Elements stay where they are, and any past the edge are clipped. */
    fun setSize(width: Float, height: Float) {
        size = coerced(unit.toPt(width), unit.toPt(height))
    }

    /** Places [element] on top and returns its index. */
    fun add(element: LayoutElement): Int {
        placed += element
        return placed.lastIndex
    }

    fun update(index: Int, element: LayoutElement) {
        placed[index] = element
    }

    fun remove(index: Int) {
        placed.removeAt(index)
    }

    /** Moves an element, stopping at the page's edges. One already past an edge can still move back. */
    fun moveBy(index: Int, dx: Float, dy: Float) {
        val box = placed[index].bounds
        val allowedDx = dx.coerceIn(min(-box.left, 0f), max(size.widthPt - box.right, 0f))
        val allowedDy = dy.coerceIn(min(-box.top, 0f), max(size.heightPt - box.bottom, 0f))
        placed[index] = placed[index].translated(allowedDx, allowedDy)
    }

    /** Drags one [corner] of the box at [index] to ([x], [y]), kept on the page. A line is resized by its ends instead. */
    fun resizeCorner(index: Int, corner: Int, x: Float, y: Float) {
        placed[index] = placed[index].withCorner(corner, x.coerceIn(0f, size.widthPt), y.coerceIn(0f, size.heightPt))
    }

    /** The topmost element within [tolerance] of a point, or -1. A line counts along its length, a box anywhere inside. */
    fun elementAt(x: Float, y: Float, tolerance: Float): Int {
        for (i in placed.indices.reversed()) {
            val element = placed[i]
            val hit = if (element is LayoutElement.Line) {
                distanceToSegment(x, y, element) <= tolerance
            } else {
                element.bounds.outset(tolerance).contains(x, y)
            }
            if (hit) return i
        }
        return -1
    }

    fun build() = CustomLayout(name, size, unit, placed.toList(), paper, base, bookStyle, frameLines)

    private fun distanceToSegment(x: Float, y: Float, line: LayoutElement.Line): Float {
        val dx = line.x1 - line.x0
        val dy = line.y1 - line.y0
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(x - line.x0, y - line.y0)
        val t = (((x - line.x0) * dx + (y - line.y0) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(x - (line.x0 + t * dx), y - (line.y0 + t * dy))
    }

    private fun coerced(widthPt: Float, heightPt: Float) =
        PageSize(widthPt.coerceIn(PAGE_SIZE_RANGE_PT), heightPt.coerceIn(PAGE_SIZE_RANGE_PT))

    companion object {
        /** From half an inch up to the largest page a PDF may have. */
        val PAGE_SIZE_RANGE_PT = 36f..14400f
    }
}
