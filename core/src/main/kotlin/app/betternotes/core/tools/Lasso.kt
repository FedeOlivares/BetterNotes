package app.betternotes.core.tools

import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.polygonContains
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import kotlin.math.max

/** Share of a stroke that must fall inside the lasso for it to count as selected. */
private const val STROKE_SELECT_FRACTION = 0.5f

/** Testing every sample of a long stroke adds nothing to the answer, so tests are capped per stroke. */
private const val MAX_STROKE_SAMPLES = 32

/**
 * Items caught by a lasso, with their positions on the page. The positions hold until the page's
 * next edit that changes its order, so the UI should drop the selection on undo, redo or delete.
 */
class Selection(
    val pageId: Long,
    val indices: IntArray,
    val items: List<PageItem>,
) {
    val isEmpty get() = items.isEmpty()

    /** The box the selection handles are drawn around. Null when nothing is selected. */
    val bounds: Bounds? = items.map { it.bounds }.reduceOrNull(Bounds::union)

    /** The edit that applies [transform], such as one from [resizeTransform], to every item in place. */
    fun transformEdit(transform: Affine) = ItemsEdit(
        pageId,
        indices,
        items,
        indices,
        items.map { it.transformed(transform) },
    )

    /** The selection once [edit] from [transformEdit] is applied, which keeps the handles up. */
    fun afterTransform(edit: ItemsEdit) = Selection(pageId, indices, edit.added)

    fun deleteEdit() = ItemsEdit(pageId, indices, items, IntArray(0), emptyList())
}

/**
 * Selects what a closed lasso surrounds. [polygon] holds packed x,y pairs. A stroke is selected if
 * at least half of it is inside, so a slightly sloppy lasso still works. Anything else is selected
 * if its center is inside.
 */
fun selectWithLasso(pageId: Long, content: PageContent, polygon: FloatArray): Selection {
    val indices = ArrayList<Int>()
    val items = ArrayList<PageItem>()
    if (polygon.size >= MIN_POLYGON_FLOATS) {
        content.forEachIntersecting(polygonBounds(polygon)) { index, item ->
            if (item.isCaughtBy(polygon)) {
                indices += index
                items += item
            }
        }
    }
    return Selection(pageId, indices.toIntArray(), items)
}

private const val MIN_POLYGON_FLOATS = 6

private fun polygonBounds(polygon: FloatArray): Bounds {
    var left = polygon[0]
    var top = polygon[1]
    var right = left
    var bottom = top
    for (i in polygon.indices step 2) {
        left = minOf(left, polygon[i])
        right = maxOf(right, polygon[i])
        top = minOf(top, polygon[i + 1])
        bottom = maxOf(bottom, polygon[i + 1])
    }
    return Bounds(left, top, right, bottom)
}

private fun PageItem.isCaughtBy(polygon: FloatArray): Boolean {
    if (this !is StrokeItem) {
        return polygonContains(polygon, bounds.centerX, bounds.centerY)
    }
    val step = max(1, points.size / MAX_STROKE_SAMPLES)
    var tested = 0
    var inside = 0
    for (i in 0 until points.size step step) {
        tested++
        if (polygonContains(polygon, points.x(i), points.y(i))) inside++
    }
    return inside >= tested * STROKE_SELECT_FRACTION
}

/** A selection handle. The signs say which edges it drags: -1 left or top, 1 right or bottom, 0 neither. */
enum class ResizeHandle(val xSign: Int, val ySign: Int) {
    TOP_LEFT(-1, -1),
    TOP(0, -1),
    TOP_RIGHT(1, -1),
    RIGHT(1, 0),
    BOTTOM_RIGHT(1, 1),
    BOTTOM(0, 1),
    BOTTOM_LEFT(-1, 1),
    LEFT(-1, 0),
    ;

    val isCorner get() = xSign != 0 && ySign != 0
}

/**
 * The transform for dragging [handle] of a selection in [bounds] to the world point ([x], [y]). The
 * opposite side stays fixed. Dragging past it stops at [minSize] rather than flipping the selection,
 * since a frame cannot hold a flip. Corners keep the aspect ratio when [keepAspect] is set.
 *
 * Always compute from the bounds at the start of the drag: the result maps those bounds directly,
 * so repeated moves do not pile up rounding errors.
 */
fun resizeTransform(
    bounds: Bounds,
    handle: ResizeHandle,
    x: Float,
    y: Float,
    keepAspect: Boolean = false,
    minSize: Float = 4f,
): Affine {
    val anchorX = if (handle.xSign < 0) bounds.right else bounds.left
    val anchorY = if (handle.ySign < 0) bounds.bottom else bounds.top
    var sx = axisScale(handle.xSign, bounds.width, x - anchorX, minSize)
    var sy = axisScale(handle.ySign, bounds.height, y - anchorY, minSize)
    if (keepAspect && handle.isCorner) {
        sx = max(sx, sy)
        sy = sx
    }
    return Affine.scaleAbout(sx, sy, anchorX, anchorY)
}

private fun axisScale(sign: Int, size: Float, dragged: Float, minSize: Float): Float {
    if (sign == 0 || size <= 0f) return 1f
    return max(dragged * sign, minSize) / size
}
