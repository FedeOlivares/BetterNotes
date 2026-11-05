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
) {
    constructor(layout: CustomLayout) : this(
        layout.name, layout.size, layout.displayUnit, layout.elements, layout.paper, layout.base, layout.bookStyle,
    )

    var base: PageTemplate = base
        set(value) {
            require(value !is PageTemplate.Custom) { "A layout's base cannot be a custom layout" }
            field = value
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

    fun build() = CustomLayout(name, size, unit, placed.toList(), paper, base, bookStyle)

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
