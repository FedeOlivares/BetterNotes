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
    pages: List<Page>,
    val direction: ScrollDirection = ScrollDirection.VERTICAL,
    private val gapPt: Float = DEFAULT_GAP_PT,
) {
    val isInfinite = pages.any { it.size == null }

    val pageCount = pages.size

    /** left, top, right, bottom of each page. */
    @PublishedApi
    internal val boxes = FloatArray(pages.size * BOX_STRIDE)

    /** The area pages occupy. On an infinite canvas, the area the view may reach. */
    val bounds: Bounds = if (isInfinite) placeInfinite() else place(pages)

    fun originX(index: Int) = if (isInfinite) 0f else boxes[index * BOX_STRIDE]
    fun originY(index: Int) = if (isInfinite) 0f else boxes[index * BOX_STRIDE + 1]

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
        minPan(bounds.left, bounds.right, pxPerPt, screenPx, marginPx)

    fun maxPanX(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        maxPan(bounds.left, bounds.right, pxPerPt, screenPx, marginPx)

    fun minPanY(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        minPan(bounds.top, bounds.bottom, pxPerPt, screenPx, marginPx)

    fun maxPanY(pxPerPt: Float, screenPx: Int, marginPx: Float) =
        maxPan(bounds.top, bounds.bottom, pxPerPt, screenPx, marginPx)

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
        return area
    }

    private fun place(pages: List<Page>): Bounds {
        val vertical = direction == ScrollDirection.VERTICAL
        val across = pages.maxOf { if (vertical) it.size!!.widthPt else it.size!!.heightPt }
        var along = 0f
        // Right to left runs backwards from the origin, so the first page has the highest x.
        val ordered = if (direction == ScrollDirection.RIGHT_TO_LEFT) pages.indices.reversed() else pages.indices
        for (i in ordered) {
            val size = pages[i].size!!
            val offset = i * BOX_STRIDE
            if (vertical) {
                boxes[offset] = (across - size.widthPt) / 2f
                boxes[offset + 1] = along
                along += size.heightPt + gapPt
            } else {
                boxes[offset] = along
                boxes[offset + 1] = (across - size.heightPt) / 2f
                along += size.widthPt + gapPt
            }
            boxes[offset + 2] = boxes[offset] + size.widthPt
            boxes[offset + 3] = boxes[offset + 1] + size.heightPt
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
