package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.FillItem
import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.tools.FloodFill
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The paint bucket. It draws everything on the page that can bound a region (ink, shapes, tape and the lines of tables) into a small
 * picture, finds the closed region around the tapped point in it, and turns that region into a vector [FillItem]. Gaps in the lines up to
 * [gapPt] across are bridged, and a region that leaks out of the page, or a tap on a line, fills nothing.
 *
 * Meant for a background thread: it owns its own ink meshes and touches no view.
 */
internal class FillBucket {
    private val strokes = StrokeCache()
    private val ink = InkRenderer()

    /**
     * The fill for the region around ([x], [y]) in [items], or null if there is none to fill. All in page points. [viewWidthPt] and
     * [viewHeightPt] are what the screen shows, and [pxPerPt] is how big that is: the picture of the lines is made at about the screen's
     * own resolution, only of the part of the page near the tap, and of more of it only if the shape does not close within that.
     */
    fun fill(
        items: List<PageItem>,
        pageSize: PageSize?,
        x: Float,
        y: Float,
        gapPt: Float,
        color: Int,
        opacity: Float,
        pattern: FillPattern,
        pxPerPt: Float,
        viewWidthPt: Float,
        viewHeightPt: Float,
        /** Fills only within this area, whose edge is a line too, as the edge of a note is: it holds a fill in, where a page's edge lets it leak. */
        edgeArea: Bounds? = null,
        /** Decodes a picture, which may be slow: its opaque parts are lines too, so a sticker's outline holds a fill. */
        image: (String) -> Bitmap? = { null },
    ): FillItem? {
        val whole = edgeArea ?: areaOf(items, pageSize, x, y)
        val edgeIsLine = edgeArea != null
        var halfWidth = viewWidthPt * FIRST_WINDOW
        var halfHeight = viewHeightPt * FIRST_WINDOW
        repeat(MAX_WINDOWS) { attempt ->
            val window = Bounds(
                max(whole.left, x - halfWidth),
                max(whole.top, y - halfHeight),
                min(whole.right, x + halfWidth),
                min(whole.bottom, y + halfHeight),
            )
            val covers = window.left <= whole.left && window.top <= whole.top && window.right >= whole.right && window.bottom >= whole.bottom
            when (val result = fillIn(items, image, window, whole, edgeIsLine, x, y, gapPt, color, opacity, pattern, pxPerPt)) {
                is Found -> return result.item
                NoShape -> return null
                // The shape reaches the edge of the part looked at: the whole page is the last place to look.
                OpenShape -> {
                    if (covers) return null
                    halfWidth *= WINDOW_GROWTH
                    halfHeight *= WINDOW_GROWTH
                }
            }
        }
        return null
    }

    private sealed interface Attempt
    private class Found(val item: FillItem) : Attempt
    private data object NoShape : Attempt
    private data object OpenShape : Attempt

    /** What can bound a region, highest first: the drawing on the page or the note, and under it the pictures such as stickers. */
    private enum class Layer { DRAWING, STICKER }

    private fun Layer.holds(item: PageItem) = when (this) {
        Layer.DRAWING -> item is StrokeItem || item is TapePathItem || item is ShapeItem || item is PathShapeItem || item is TableItem
        Layer.STICKER -> item is ImageItem
    }

    private fun fillIn(
        items: List<PageItem>,
        image: (String) -> Bitmap?,
        area: Bounds,
        whole: Bounds,
        edgeIsLine: Boolean,
        x: Float,
        y: Float,
        gapPt: Float,
        color: Int,
        opacity: Float,
        pattern: FillPattern,
        pxPerPt: Float,
    ): Attempt {
        val scale = min(pxPerPt, MAX_SIDE_PX / max(area.width, area.height))
        val width = max(1, ceil(area.width * scale).toInt())
        val height = max(1, ceil(area.height * scale).toInt())
        val startX = ((x - area.left) * scale).toInt()
        val startY = ((y - area.top) * scale).toInt()
        val covers = area.left <= whole.left && area.top <= whole.top && area.right >= whole.right && area.bottom >= whole.bottom
        val edge = BooleanArray(1)

        fun found(region: BooleanArray): Attempt {
            val loops = FloodFill.outlines(region, width, height, SIMPLIFY_PX).map { loop ->
                FloatArray(loop.size) { i -> if (i % 2 == 0) area.left + loop[i] / scale else area.top + loop[i] / scale }
            }
            return if (loops.isEmpty()) NoShape else Found(FillItem(IdSource.Random.next(), loops, color, opacity, pattern = pattern))
        }

        // The highest layer with a closed area around the tap is the one that is filled. If one is not closed within the part looked at,
        // a larger part is looked at before a lower layer is.
        for (layer in Layer.entries) {
            if (items.none { layer.holds(it) }) continue
            val walls = drawWalls(items.filter { layer.holds(it) }, image, area, scale, width, height)
            // The edge of a note is a line too, so a curve that runs from one side of it to another closes off a part of it.
            if (edgeIsLine) closeEdges(walls, width, height, area, whole)
            // A closed shape is filled as drawn, up to the middle of its lines, and a region a single pixel wide on the screen is filled.
            // The gap tolerance is for drawings and pictures that are not closed, since bridging gaps rounds off sharp corners.
            edge[0] = false
            var region = FloodFill.region(walls, width, height, startX, startY, 0, edge)
            if (region == null && edge[0]) {
                // Line art in a picture has more gaps than ink, and a fill that stays inside it is what a sticker's fill is for.
                val reach = if (layer == Layer.STICKER) STICKER_GAP_FACTOR else 1f
                val gapPx = ceil(gapPt * scale * reach / 2f).toInt().coerceIn(1, MAX_GAP_PX * reach.toInt())
                edge[0] = false
                region = FloodFill.region(walls, width, height, startX, startY, gapPx, edge)
            }
            if (region != null) return found(region)
            if (edge[0] && !covers) return OpenShape
        }
        if (edgeIsLine) {
            // Nothing closes around the tap: the note itself is the layer under them, bounded by its edge.
            val walls = BooleanArray(width * height)
            closeEdges(walls, width, height, area, whole)
            edge[0] = false
            val region = FloodFill.region(walls, width, height, startX, startY, 0, edge)
            if (region != null) return found(region)
            if (edge[0] && !covers) return OpenShape
        }
        return NoShape
    }

    /** Lines along the sides of the picture that are sides of [whole], so that a region cannot leave it there. */
    private fun closeEdges(walls: BooleanArray, width: Int, height: Int, area: Bounds, whole: Bounds) {
        if (area.left <= whole.left) for (y in 0 until height) walls[y * width] = true
        if (area.right >= whole.right) for (y in 0 until height) walls[y * width + width - 1] = true
        if (area.top <= whole.top) for (x in 0 until width) walls[x] = true
        if (area.bottom >= whole.bottom) for (x in 0 until width) walls[(height - 1) * width + x] = true
    }

    /** A page's own area, or for a canvas that has no edge, where the items are and the point is, with room around them. */
    private fun areaOf(items: List<PageItem>, pageSize: PageSize?, x: Float, y: Float): Bounds {
        if (pageSize != null) return Bounds(0f, 0f, pageSize.widthPt, pageSize.heightPt)
        var box = Bounds(x, y, x, y)
        for (item in items) box = box.union(item.bounds)
        return Bounds(box.left - CANVAS_MARGIN_PT, box.top - CANVAS_MARGIN_PT, box.right + CANVAS_MARGIN_PT, box.bottom + CANVAS_MARGIN_PT)
    }

    private fun drawWalls(items: List<PageItem>, image: (String) -> Bitmap?, area: Bounds, scale: Float, width: Int, height: Int): BooleanArray {
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val matrix = Matrix().apply { setScale(scale, scale) }
        val painter = ItemPainter(image = image, paintStroke = { c, stroke -> ink.draw(c, strokes.get(stroke), matrix) })
        canvas.withMatrix(Matrix().apply { setTranslate(-area.left * scale, -area.top * scale); preScale(scale, scale) }) {
            for (item in items) {
                val wall = when (item) {
                    is StrokeItem, is TapePathItem, is ImageItem -> item
                    // Only the outline of a shape or the lines of a table bound a region, not the colors inside them.
                    is ShapeItem -> if (item.kind == ShapeKind.TAPE) item else item.copy(fillColor = 0)
                    is PathShapeItem -> item.copy(fillColor = 0)
                    is TableItem -> item.copy(cells = item.cells.map { "" }, fillColor = 0, headerRow = false, headerColumn = false)
                    else -> null
                } ?: continue
                painter.paint(this, wall)
            }
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return BooleanArray(pixels.size) { (pixels[it] ushr ALPHA_SHIFT) >= WALL_ALPHA }
    }

    private companion object {
        const val FIRST_WINDOW = 0.5f
        const val WINDOW_GROWTH = 2.5f
        const val MAX_WINDOWS = 5
        const val MAX_SIDE_PX = 3400f
        const val MAX_GAP_PX = 24
        const val STICKER_GAP_FACTOR = 3f
        const val SIMPLIFY_PX = 0.7f
        const val CANVAS_MARGIN_PT = 60f
        const val ALPHA_SHIFT = 24
        const val WALL_ALPHA = 48
    }
}
