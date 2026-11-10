package app.betternotes.core.layout

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.Page
import kotlin.math.max
import kotlin.math.min

enum class ScrollDirection { VERTICAL, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

/**
 * Where each page sits in document space, the space a [Viewport] looks at. Items stay in the
 * coordinates of their own page, so moving or resizing a page never touches its ink.
 *
 * Pages line up along the scroll direction and are centered across it. An infinite canvas is one
 * page with its origin at the document's origin.
 */
class DocumentLayout(
    private val pages: List<Page>,
    val direction: ScrollDirection = ScrollDirection.VERTICAL,
    private val gapPt: Float = DEFAULT_GAP_PT,
) {
    val isInfinite = pages.any { it.size == null }

    /** How far the view may go past the last page, in points, along the way the pages run: room for a new page. */
    var endRoomPt = 0f

    /** How far the view may go before the first page, in points: the room where "Add cover" sits. */
    var startRoomPt = 0f

    val pageCount = pages.size

    /**
     * left, top, right, bottom of the room each page takes. A page that is turned takes the box around it, so the pages after it
     * move aside; an upright page takes exactly its own rectangle.
     */
    @PublishedApi
    internal val boxes = FloatArray(pages.size * BOX_STRIDE)

    /** left, top, right, bottom of each page's own upright rectangle, which sits in the middle of its room. */
    private val rects = FloatArray(pages.size * BOX_STRIDE)

    /** How far each page is turned, in degrees clockwise about its middle. */
    private val turns = FloatArray(pages.size) { if (pages[it].size == null) 0f else pages[it].rotation }

    /** The area pages occupy. On an infinite canvas, the area the view may reach. */
    var bounds: Bounds = if (isInfinite) placeInfinite() else place(pages)
        private set

    /** Where the page's own upright rectangle starts. A turned page turns about this rectangle's middle. */
    fun originX(index: Int) = if (isInfinite) 0f else rects[index * BOX_STRIDE]
    fun originY(index: Int) = if (isInfinite) 0f else rects[index * BOX_STRIDE + 1]

    fun pageWidth(index: Int) = if (isInfinite) 0f else rects[index * BOX_STRIDE + 2] - rects[index * BOX_STRIDE]
    fun pageHeight(index: Int) = if (isInfinite) 0f else rects[index * BOX_STRIDE + 3] - rects[index * BOX_STRIDE + 1]

    fun centerX(index: Int) = originX(index) + pageWidth(index) / 2f
    fun centerY(index: Int) = originY(index) + pageHeight(index) / 2f

    /** The room the page takes, which is the box around it once it is turned. */
    fun roomBounds(index: Int): Bounds {
        val offset = index * BOX_STRIDE
        return Bounds(boxes[offset], boxes[offset + 1], boxes[offset + 2], boxes[offset + 3])
    }

    /** How far the page is drawn turned, in degrees clockwise. */
    fun turn(index: Int) = turns[index]

    /**
     * Turns the page as drawn, and lays the pages out again, so the pages after it slide to keep the same gap from the box
     * around it. Cheap enough to call on every move of two fingers.
     */
    fun setTurn(index: Int, degrees: Float) {
        if (isInfinite || turns[index] == degrees) return
        turns[index] = degrees
        bounds = place(pages)
    }

    /** The point of the page that lies under a document point, written to [out] as x then y. Upright, it is the point less the origin. */
    fun worldToPage(index: Int, x: Float, y: Float, out: FloatArray) {
        val w = pageWidth(index)
        val h = pageHeight(index)
        val dx = x - centerX(index)
        val dy = y - centerY(index)
        val radians = Math.toRadians(-turns[index].toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        out[0] = w / 2f + dx * cos - dy * sin
        out[1] = h / 2f + dx * sin + dy * cos
    }

    /** The box in the document around [box], given in the coordinates of a page, which can be turned. */
    fun pageBoundsToWorld(index: Int, box: Bounds): Bounds {
        if (turns[index] == 0f) {
            val x = originX(index)
            val y = originY(index)
            return Bounds(box.left + x, box.top + y, box.right + x, box.bottom + y)
        }
        val corner = FloatArray(2)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (k in 0 until 4) {
            pageToWorld(index, if (k % 2 == 0) box.left else box.right, if (k < 2) box.top else box.bottom, corner)
            left = minOf(left, corner[0])
            right = maxOf(right, corner[0])
            top = minOf(top, corner[1])
            bottom = maxOf(bottom, corner[1])
        }
        return Bounds(left, top, right, bottom)
    }

    /** The document point where a point of the page lies, written to [out] as x then y. */
    fun pageToWorld(index: Int, x: Float, y: Float, out: FloatArray) {
        val dx = x - pageWidth(index) / 2f
        val dy = y - pageHeight(index) / 2f
        val radians = Math.toRadians(turns[index].toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        out[0] = centerX(index) + dx * cos - dy * sin
        out[1] = centerY(index) + dx * sin + dy * cos
    }

    /** The page under a document point, or -1 if the point is off every page. */
    fun pageIndexAt(x: Float, y: Float): Int {
        for (i in 0 until pageCount) {
            val offset = i * BOX_STRIDE
            if (x >= boxes[offset] && x <= boxes[offset + 2] && y >= boxes[offset + 1] && y <= boxes[offset + 3]) {
                return i
            }
        }
        return -1
    }

    /** Calls [action] for each page touching [region], in page order. */
    inline fun forEachPageIn(region: Bounds, action: (index: Int) -> Unit) {
        for (i in 0 until pageCount) {
            val offset = i * 4
            if (boxes[offset] <= region.right && region.left <= boxes[offset + 2] &&
                boxes[offset + 1] <= region.bottom && region.top <= boxes[offset + 3]
            ) {
                action(i)
            }
        }
    }

    /**
     * The lowest pan a viewport may have along x. Content narrower than the screen stays centered, so
     * the lowest and highest are the same. Otherwise an edge can pull in from the screen's edge by
     * [marginPx].
     */
    fun minPanX(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        minPan(bounds.left, bounds.right, pxPerPt, screenPx, marginPx) -
            (if (direction == ScrollDirection.LEFT_TO_RIGHT) endRoomPt * pxPerPt else 0f) -
            if (direction == ScrollDirection.RIGHT_TO_LEFT) startRoomPt * pxPerPt else 0f

    fun maxPanX(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        maxPan(bounds.left, bounds.right, pxPerPt, screenPx, marginPx) +
            (if (direction == ScrollDirection.RIGHT_TO_LEFT) endRoomPt * pxPerPt else 0f) +
            if (direction == ScrollDirection.LEFT_TO_RIGHT) startRoomPt * pxPerPt else 0f

    fun minPanY(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        minPan(bounds.top, bounds.bottom, pxPerPt, screenPx, marginPx) -
            if (direction == ScrollDirection.VERTICAL) endRoomPt * pxPerPt else 0f

    fun maxPanY(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        maxPan(bounds.top, bounds.bottom, pxPerPt, screenPx, marginPx) +
            if (direction == ScrollDirection.VERTICAL) startRoomPt * pxPerPt else 0f

    /** Pulls the viewport back inside the range it may reach. */
    fun clampPan(viewport: Viewport, widthPx: Int, heightPx: Int, marginPx: Float) {
        val scale = viewport.pxPerPt
        viewport.panX = viewport.panX.coerceIn(
            minPanX(scale, widthPx, marginPx),
            maxPanX(scale, widthPx, marginPx),
        )
        viewport.panY = viewport.panY.coerceIn(
            minPanY(scale, heightPx, marginPx),
            maxPanY(scale, heightPx, marginPx),
        )
    }

    /** The length of the room the page at [index] takes along the way the pages run, in points. */
    fun lengthAlong(index: Int): Float {
        if (isInfinite) return 0f
        val offset = index * BOX_STRIDE
        return if (direction == ScrollDirection.VERTICAL) boxes[offset + 3] - boxes[offset + 1] else boxes[offset + 2] - boxes[offset]
    }

    /**
     * Shows the start of page [index]: its top-left corner when it is wider than the screen, since
     * writing starts there, and centered across the screen when it fits.
     */
    fun moveToPage(viewport: Viewport, index: Int, widthPx: Int, heightPx: Int, marginPx: Float) {
        val scale = viewport.pxPerPt
        viewport.panX = marginPx - originX(index) * scale
        viewport.panY = marginPx - originY(index) * scale
        clampPan(viewport, widthPx, heightPx, marginPx)
    }

    private fun minPan(start: Float, end: Float, pxPerPt: Float, screenPx: Int, marginPx: Float) =
        min(centeredPan(start, end, pxPerPt, screenPx), screenPx - end * pxPerPt - marginPx)

    private fun maxPan(start: Float, end: Float, pxPerPt: Float, screenPx: Int, marginPx: Float) =
        max(centeredPan(start, end, pxPerPt, screenPx), marginPx - start * pxPerPt)

    private fun centeredPan(start: Float, end: Float, pxPerPt: Float, screenPx: Int) =
        (screenPx - (end - start) * pxPerPt) / 2f - start * pxPerPt

    private fun placeInfinite(): Bounds {
        val area = Bounds(-CANVAS_LIMIT_PT, -CANVAS_LIMIT_PT, CANVAS_LIMIT_PT, CANVAS_LIMIT_PT)
        boxes[0] = area.left
        boxes[1] = area.top
        boxes[2] = area.right
        boxes[3] = area.bottom
        rects[0] = area.left
        rects[1] = area.top
        rects[2] = area.right
        rects[3] = area.bottom
        return area
    }

    private fun place(pages: List<Page>): Bounds {
        val vertical = direction == ScrollDirection.VERTICAL
        // Each page takes the box around it as turned, so a turned page has room.
        val roomWidth = FloatArray(pages.size)
        val roomHeight = FloatArray(pages.size)
        for (i in pages.indices) {
            val size = pages[i].size!!
            val radians = Math.toRadians(turns[i].toDouble())
            val cos = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
            val sin = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
            roomWidth[i] = size.widthPt * cos + size.heightPt * sin
            roomHeight[i] = size.widthPt * sin + size.heightPt * cos
        }
        val across = pages.indices.maxOf { if (vertical) roomWidth[it] else roomHeight[it] }
        var along = 0f
        // Right to left runs backwards from the origin, so the first page has the highest x.
        val ordered = if (direction == ScrollDirection.RIGHT_TO_LEFT) pages.indices.reversed() else pages.indices
        for (i in ordered) {
            val size = pages[i].size!!
            val offset = i * BOX_STRIDE
            if (vertical) {
                boxes[offset] = (across - roomWidth[i]) / 2f
                boxes[offset + 1] = along
                along += roomHeight[i] + gapPt
            } else {
                boxes[offset] = along
                boxes[offset + 1] = (across - roomHeight[i]) / 2f
                along += roomWidth[i] + gapPt
            }
            boxes[offset + 2] = boxes[offset] + roomWidth[i]
            boxes[offset + 3] = boxes[offset + 1] + roomHeight[i]
            rects[offset] = boxes[offset] + (roomWidth[i] - size.widthPt) / 2f
            rects[offset + 1] = boxes[offset + 1] + (roomHeight[i] - size.heightPt) / 2f
            rects[offset + 2] = rects[offset] + size.widthPt
            rects[offset + 3] = rects[offset + 1] + size.heightPt
        }
        val length = along - gapPt
        return if (vertical) Bounds(0f, 0f, across, length) else Bounds(0f, 0f, length, across)
    }

    companion object {
        const val DEFAULT_GAP_PT = 12f

        /**
         * About 18 m each way. Far beyond this, float coordinates at 1500% zoom could no longer place
         * a point to the nearest pixel.
         */
        const val CANVAS_LIMIT_PT = 50_000f

        @PublishedApi
        internal const val BOX_STRIDE = 4
    }
}
