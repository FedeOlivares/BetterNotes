package app.betternotes.core.tools

import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.geometry.polygonContains
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
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
 * if its center is inside. With [fullOnly], all of the item must be inside. With [anyPortion], an item is selected
 * if any part of it is inside, even when the lasso only crosses it. Only items of [kinds] count, and locked ones only with [includeLocked].
 * On a cover page, pass its size as [coverSize]: its background (see [isCoverBackground]) is never selected, locked or not,
 * so the large color shape does not get in the way while the cover is designed.
 */
fun selectWithLasso(
    pageId: Long,
    content: PageContent,
    polygon: FloatArray,
    kinds: Set<ItemKind> = ALL_ITEM_KINDS,
    fullOnly: Boolean = false,
    anyPortion: Boolean = false,
    includeLocked: Boolean = false,
    coverSize: PageSize? = null,
): Selection {
    val indices = ArrayList<Int>()
    val items = ArrayList<PageItem>()
    if (polygon.size >= MIN_POLYGON_FLOATS) {
        content.forEachIntersecting(polygonBounds(polygon)) { index, item ->
            if ((includeLocked || !item.locked) && !(coverSize != null && item.isCoverBackground(coverSize)) && content.isEditable(item) && item.kind in kinds && item.isCaughtBy(polygon, fullOnly, anyPortion)) {
                indices += index
                items += item
            }
        }
    }
    return Selection(pageId, indices.toIntArray(), items)
}

/** The large shape that colors a whole cover page: it reaches every edge of the page. */
fun PageItem.isCoverBackground(size: PageSize): Boolean {
    val box = bounds
    return box.left <= 0f && box.top <= 0f && box.right >= size.widthPt && box.bottom >= size.heightPt
}

/** Whether every point of the loop [polygon] lies inside [box], so that the loop never leaves it. */
fun polygonWithin(polygon: FloatArray, box: Bounds): Boolean {
    if (polygon.isEmpty()) return false
    var i = 0
    while (i + 1 < polygon.size) {
        if (polygon[i] <= box.left || polygon[i] >= box.right || polygon[i + 1] <= box.top || polygon[i + 1] >= box.bottom) return false
        i += 2
    }
    return true
}

/**
 * Whether every point of the loop [polygon] lies inside the drawn edges of [frame], turn included, so that the loop never crosses them.
 * For a frame that is not turned this is [polygonWithin] of its box.
 */
fun polygonWithin(polygon: FloatArray, frame: Frame): Boolean {
    if (frame.rotation == 0f) return polygonWithin(polygon, frame.bounds)
    if (polygon.isEmpty()) return false
    val turn = Math.toRadians(-frame.rotation.toDouble())
    val c = kotlin.math.cos(turn).toFloat()
    val s = kotlin.math.sin(turn).toFloat()
    var i = 0
    while (i + 1 < polygon.size) {
        val dx = polygon[i] - frame.centerX
        val dy = polygon[i + 1] - frame.centerY
        val x = dx * c - dy * s
        val y = dx * s + dy * c
        if (x <= -frame.width / 2f || x >= frame.width / 2f || y <= -frame.height / 2f || y >= frame.height / 2f) return false
        i += 2
    }
    return true
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

private fun PageItem.isCaughtBy(polygon: FloatArray, fullOnly: Boolean, anyPortion: Boolean): Boolean {
    // A loop wholly inside an open post-it is on what the post-it holds (see [NoteScope]): only a loop that leaves it takes the post-it.
    if (this is StickyNoteItem && isOpen && polygonWithin(polygon, frame)) return false
    if (anyPortion && !fullOnly) return touches(polygon)
    if (this !is StrokeItem) {
        if (!fullOnly) return polygonContains(polygon, bounds.centerX, bounds.centerY)
        return polygonContains(polygon, bounds.left, bounds.top) && polygonContains(polygon, bounds.right, bounds.top) &&
            polygonContains(polygon, bounds.right, bounds.bottom) && polygonContains(polygon, bounds.left, bounds.bottom)
    }
    val step = max(1, points.size / MAX_STROKE_SAMPLES)
    var tested = 0
    var inside = 0
    for (i in 0 until points.size step step) {
        tested++
        if (polygonContains(polygon, points.x(i), points.y(i))) inside++
    }
    return inside >= tested * (if (fullOnly) 1f else STROKE_SELECT_FRACTION)
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

/** Whether any part of the item is inside the polygon: a stroke's line or an item's box crossing or lying in it. */
private fun PageItem.touches(polygon: FloatArray): Boolean {
    if (this !is StrokeItem) return polygonTouchesBox(polygon, bounds)
    if (points.size == 0) return false
    val step = max(1, points.size / MAX_STROKE_SAMPLES)
    var previous = 0
    if (polygonContains(polygon, points.x(0), points.y(0))) return true
    var i = step
    while (true) {
        val index = minOf(i, points.size - 1)
        if (polygonContains(polygon, points.x(index), points.y(index))) return true
        if (polygonEdgeCrosses(polygon, points.x(previous), points.y(previous), points.x(index), points.y(index))) return true
        if (index == points.size - 1) return false
        previous = index
        i += step
    }
}

private fun polygonTouchesBox(polygon: FloatArray, box: Bounds): Boolean {
    val corners = floatArrayOf(box.left, box.top, box.right, box.top, box.right, box.bottom, box.left, box.bottom)
    for (i in corners.indices step 2) if (polygonContains(polygon, corners[i], corners[i + 1])) return true
    for (i in polygon.indices step 2) {
        if (polygon[i] in box.left..box.right && polygon[i + 1] in box.top..box.bottom) return true
    }
    for (i in corners.indices step 2) {
        val next = (i + 2) % corners.size
        if (polygonEdgeCrosses(polygon, corners[i], corners[i + 1], corners[next], corners[next + 1])) return true
    }
    return false
}

/** Whether the segment from (ax, ay) to (bx, by) crosses any edge of the closed polygon. */
private fun polygonEdgeCrosses(polygon: FloatArray, ax: Float, ay: Float, bx: Float, by: Float): Boolean {
    var j = polygon.size - 2
    var i = 0
    while (i < polygon.size) {
        if (segmentsCross(ax, ay, bx, by, polygon[j], polygon[j + 1], polygon[i], polygon[i + 1])) return true
        j = i
        i += 2
    }
    return false
}

private fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
    fun side(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
    val d1 = side(cx, cy, dx, dy, ax, ay)
    val d2 = side(cx, cy, dx, dy, bx, by)
    val d3 = side(ax, ay, bx, by, cx, cy)
    val d4 = side(ax, ay, bx, by, dx, dy)
    return (d1 > 0f) != (d2 > 0f) && (d3 > 0f) != (d4 > 0f)
}
