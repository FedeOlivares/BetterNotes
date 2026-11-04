package app.betternotes.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageSource
import app.betternotes.core.search.SearchableText
import app.betternotes.core.search.TextMatch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Puts PDF pages under a document's ink. PDFium is far too slow to call while recording a tile, so
 * painting never waits for it: it uses the pixels at hand and asks [PdfWorker] for what is missing,
 * and [onRegionReady] redraws the tile when they arrive.
 *
 * What a tile shows, best first:
 * 1. A raster made for this very tile at this zoom. It lines up with the tile's pixels, so text stays
 *    as sharp as PDFium drew it.
 * 2. Rasters from the zoom before, stretched, so that settling after a pinch does not flash blurry.
 * 3. The page's preview, a small render of the whole page. It is also all a far zoomed-out view uses.
 *
 * Main thread only, apart from what it hands to the worker.
 */
internal class PdfPages(store: DocumentStore, private val document: Document) {
    /** Called when new pixels for a point on a page are ready, in the page's own coordinates. */
    var onRegionReady: (pageId: Long, region: Bounds) -> Unit = { _, _ -> }

    private val previews = RasterCache(PREVIEW_CACHE_BYTES)
    private val tiles = RasterCache(TILE_CACHE_BYTES)
    private val pending = HashSet<PdfRaster>()
    private val worker = PdfWorker(store, ::onRendered, ::onDropped)
    private var closed = false

    private var currentScale = 0f
    private var fallbackScale = 0f
    private var currentHasTiles = false
    private var search: SearchJob? = null
    private var skippedWhilePinching = false

    private val paper = Paint().apply { color = Color.WHITE }
    private val sharp = Paint()
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    /** True during a pinch, when the zoom keeps changing and a sharp tile would be stale on arrival. */
    var pinching = false

    /**
     * Ends a pinch. Returns true if tiles were painted without asking for their sharp version, so they
     * need painting again.
     */
    fun endPinch(): Boolean {
        pinching = false
        return skippedWhilePinching.also { skippedWhilePinching = false }
    }

    fun setFocus(worldX: Float, worldY: Float) = worker.setFocus(worldX, worldY)

    /**
     * Paints a page into a tile. [x] and [y] are where the page's corner falls on the tile's canvas at
     * [scale], and [originX] and [originY] are where it sits in the document.
     */
    fun paint(canvas: Canvas, page: Page, source: PageSource.PdfPage, originX: Float, originY: Float, x: Double, y: Double, scale: Float) {
        val size = page.size ?: return
        val pageWidth = size.widthPt * scale.toDouble()
        val pageHeight = size.heightPt * scale.toDouble()
        val left = max(0.0, floor(x)).toInt()
        val top = max(0.0, floor(y)).toInt()
        val right = min(canvas.width.toDouble(), ceil(x + pageWidth)).toInt()
        val bottom = min(canvas.height.toDouble(), ceil(y + pageHeight)).toInt()
        if (left >= right || top >= bottom) return

        val raster = PdfRaster(source, scale, left - x, top - y, right - left, bottom - top, isPreview = false)
        val ready = tiles[raster]
        if (ready != null) {
            canvas.drawBitmap(ready, left.toFloat(), top.toFloat(), sharp)
            return
        }

        val pageLeft = x.toFloat()
        val pageTop = y.toFloat()
        destination.set(pageLeft, pageTop, pageLeft + pageWidth.toFloat(), pageTop + pageHeight.toFloat())
        canvas.drawRect(destination, paper)
        val previewScale = previewScale(size)
        val preview = previewRaster(source, size, previewScale)
        val previewBitmap = previews[preview]
        if (previewBitmap != null) {
            canvas.drawBitmap(previewBitmap, null, destination, smooth)
        } else {
            request(preview, page.id, originX + size.widthPt / 2f, originY + size.heightPt / 2f)
        }
        // At this size the preview has as many pixels as the screen can show.
        if (scale <= previewScale) return

        moveToScale(scale)
        paintFallback(canvas, source, x, y, scale, left, top, right, bottom)
        if (pinching) {
            skippedWhilePinching = true
        } else {
            val centerX = ((left + right) / 2.0 - x) / scale
            val centerY = ((top + bottom) / 2.0 - y) / scale
            request(raster, page.id, originX + centerX.toFloat(), originY + centerY.toFloat())
        }
    }

    /** Stretches whatever the zoom before left that overlaps this tile. */
    private fun paintFallback(canvas: Canvas, source: PageSource.PdfPage, x: Double, y: Double, scale: Float, left: Int, top: Int, right: Int, bottom: Int) {
        if (fallbackScale == 0f) return
        val ratio = (scale / fallbackScale).toDouble()
        tiles.forEach { old, bitmap ->
            if (old.scale != fallbackScale || old.source != source) return@forEach
            val oldLeft = old.leftPx * ratio + x
            val oldTop = old.topPx * ratio + y
            val oldRight = oldLeft + old.widthPx * ratio
            val oldBottom = oldTop + old.heightPx * ratio
            if (oldLeft < right && left < oldRight && oldTop < bottom && top < oldBottom) {
                destination.set(oldLeft.toFloat(), oldTop.toFloat(), oldRight.toFloat(), oldBottom.toFloat())
                canvas.drawBitmap(bitmap, null, destination, smooth)
            }
        }
    }

    /**
     * Keeps as the fallback the last zoom that has anything to show, which is not the last zoom asked
     * for: a pinch passes through several without rendering at any of them.
     */
    private fun moveToScale(scale: Float) {
        if (scale == currentScale) return
        if (currentHasTiles) fallbackScale = currentScale
        currentScale = scale
        currentHasTiles = false
        worker.clearTiles()
        pending.removeAll { !it.isPreview }
    }

    private fun request(raster: PdfRaster, pageId: Long, worldX: Float, worldY: Float) {
        if (pending.add(raster)) worker.enqueue(PdfRequest(raster, pageId, worldX, worldY))
    }

    private fun onRendered(request: PdfRequest, bitmap: Bitmap?) {
        val raster = request.raster
        pending.remove(raster)
        if (bitmap == null || closed) return
        if (raster.isPreview) {
            previews[raster] = bitmap
        } else {
            tiles[raster] = bitmap
            if (raster.scale != currentScale) return
            currentHasTiles = true
        }
        notifyReady(request)
    }

    /** The tile that asked may still be on screen, and painting it again asks again. */
    private fun onDropped(request: PdfRequest) {
        pending.remove(request.raster)
        if (!closed) notifyReady(request)
    }

    /** The middle of a raster identifies the one tile it was made for, which leaves its neighbours alone. */
    private fun notifyReady(request: PdfRequest) {
        val raster = request.raster
        val region = if (raster.isPreview) {
            Bounds(0f, 0f, raster.widthPx / raster.scale, raster.heightPx / raster.scale)
        } else {
            val x = ((raster.leftPx + raster.widthPx / 2.0) / raster.scale).toFloat()
            val y = ((raster.topPx + raster.heightPx / 2.0) / raster.scale).toFloat()
            Bounds(x, y, x, y)
        }
        onRegionReady(request.pageId, region)
    }

    private fun previewRaster(source: PageSource.PdfPage, size: PageSize, scale: Float) = PdfRaster(
        source,
        scale,
        0.0,
        0.0,
        (size.widthPt * scale).roundToInt().coerceAtLeast(1),
        (size.heightPt * scale).roundToInt().coerceAtLeast(1),
        isPreview = true,
    )

    private fun previewScale(size: PageSize) = PREVIEW_LONG_SIDE_PX / max(size.widthPt, size.heightPt)

    /**
     * Searches the PDF pages' text, in page order, and replaces any search under way. [onMatches] gets
     * each page's matches as they are found, and a page with no text never reports one.
     */
    fun search(query: String, onMatches: (List<TextMatch>) -> Unit, onFinished: () -> Unit) {
        search?.cancelled = true
        val entries = document.pages.mapNotNull { page ->
            (page.source as? PageSource.PdfPage)?.let { SearchJob.Entry(page.id, it) }
        }
        val job = SearchJob(SearchableText.foldQuery(query), entries, onMatches, onFinished)
        search = job
        worker.startSearch(job)
    }

    fun cancelSearch() {
        search?.cancelled = true
        search = null
        worker.startSearch(null)
    }

    fun close() {
        closed = true
        search?.cancelled = true
        worker.close()
    }

    private companion object {
        /** Enough for a screen of pages at the lowest zoom, at about 0.7 MB for an A4 preview. */
        const val PREVIEW_CACHE_BYTES = 96 * 1024 * 1024
        const val PREVIEW_LONG_SIDE_PX = 512

        /** About four Pad 7 screens: the view, the ring around it, and the zoom before. */
        const val TILE_CACHE_BYTES = 128 * 1024 * 1024
    }
}

/**
 * Least recently used bitmaps, up to a total size. An evicted bitmap is only let go, never recycled,
 * because a tile's display list may still be drawing it.
 */
private class RasterCache(private val maxBytes: Int) {
    private val entries = LinkedHashMap<PdfRaster, Bitmap>(64, 0.75f, true)
    private var bytes = 0

    operator fun get(raster: PdfRaster): Bitmap? = entries[raster]

    operator fun set(raster: PdfRaster, bitmap: Bitmap) {
        entries.put(raster, bitmap)?.let { bytes -= it.allocationByteCount }
        bytes += bitmap.allocationByteCount
        val oldest = entries.entries.iterator()
        while (bytes > maxBytes && oldest.hasNext()) {
            val entry = oldest.next()
            if (entry.key == raster) break
            bytes -= entry.value.allocationByteCount
            oldest.remove()
        }
    }

    inline fun forEach(action: (PdfRaster, Bitmap) -> Unit) {
        for ((raster, bitmap) in entries) action(raster, bitmap)
    }
}
