package app.betternotes.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.RenderParams
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.PageSource
import app.betternotes.core.search.SearchableText
import app.betternotes.core.search.TextMatch
import app.betternotes.ui.theme.CanvasColors
import java.io.IOException

/**
 * A piece of a PDF page as pixels: the [widthPx] by [heightPx] rectangle whose corner is at [leftPx],
 * [topPx] on the page drawn at [scale] pixels per point.
 */
internal data class PdfRaster(
    val source: PageSource.PdfPage,
    val scale: Float,
    val leftPx: Double,
    val topPx: Double,
    val widthPx: Int,
    val heightPx: Int,
    /** A small render of the whole page, as opposed to a tile. */
    val isPreview: Boolean,
)

/** A raster someone is waiting for. [worldX] and [worldY] say where it is, to do the nearest first. */
internal class PdfRequest(val raster: PdfRaster, val pageId: Long, val worldX: Float, val worldY: Float)

/**
 * The one thread that talks to PdfRenderer. The platform allows one open page per renderer and
 * serializes all PDF work under a global lock, so more threads would only wait. It keeps the last page
 * open, because a page's content is parsed on opening and the tiles of a page tend to come in a row.
 *
 * Whole-page previews go first, since one covers a page at once. Then come tiles, nearest to the
 * middle of the view first, so a tile the view has already left waits behind those still on screen.
 * A search runs one page at a time, only when nothing else needs drawing.
 *
 * Results arrive on the main thread.
 */
internal class PdfWorker(
    private val store: DocumentStore,
    private val onRendered: (PdfRequest, Bitmap?) -> Unit,
    private val onDropped: (PdfRequest) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()
    private val previews = ArrayList<PdfRequest>()
    private val tiles = ArrayList<PdfRequest>()
    private var search: SearchJob? = null
    private var focusX = 0f
    private var focusY = 0f
    private var closed = false

    // Only touched on the worker thread.
    private val renderers = HashMap<String, PdfRenderer>()
    private var openPage: PdfRenderer.Page? = null
    private var openSource: PageSource.PdfPage? = null
    private val pageTexts = HashMap<PageSource.PdfPage, SearchableText>()
    private val matrix = Matrix()
    private val params = RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY)
        .setRenderFlags(RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS or RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS)
        .build()

    private val thread = Thread(::run, "pdf-worker").apply { start() }

    /** If this pushes out the request farthest from the view, that one is reported to [onDropped]. */
    fun enqueue(request: PdfRequest) {
        val dropped = synchronized(lock) {
            val queue = if (request.raster.isPreview) previews else tiles
            queue += request
            lock.notifyAll()
            if (queue.size > MAX_QUEUED) queue.removeAt(queue.indices.maxBy { queue[it].distanceToFocus() }) else null
        }
        if (dropped != null) main.post { onDropped(dropped) }
    }

    /** Forgets every tile not started yet, because the zoom they were meant for has gone. */
    fun clearTiles() = synchronized(lock) { tiles.clear() }

    /** Says what the view is looking at, in document points. */
    fun setFocus(worldX: Float, worldY: Float) = synchronized(lock) {
        focusX = worldX
        focusY = worldY
    }

    /** Replaces any search running. Its results arrive through [SearchJob.onMatches]. */
    fun startSearch(job: SearchJob?) = synchronized(lock) {
        search = job
        lock.notifyAll()
    }

    /** Stops the thread and frees the renderers once the work in hand is finished. */
    fun close() = synchronized(lock) {
        closed = true
        lock.notifyAll()
    }

    private fun PdfRequest.distanceToFocus(): Float {
        val dx = worldX - focusX
        val dy = worldY - focusY
        return dx * dx + dy * dy
    }

    private fun run() {
        while (true) {
            val request = synchronized(lock) {
                while (!closed && previews.isEmpty() && tiles.isEmpty() && search == null) lock.wait()
                if (closed) null else takeNearest(previews) ?: takeNearest(tiles) ?: NO_REQUEST
            } ?: break
            if (request === NO_REQUEST) searchNextPage() else render(request)
        }
        closeEverything()
    }

    private fun takeNearest(queue: ArrayList<PdfRequest>): PdfRequest? {
        if (queue.isEmpty()) return null
        return queue.removeAt(queue.indices.minBy { queue[it].distanceToFocus() })
    }

    private fun render(request: PdfRequest) {
        val raster = request.raster
        val bitmap = try {
            val page = open(raster.source)
            val bitmap = createBitmap(raster.widthPx, raster.heightPx)
            // Most PDFs paint no background of their own and expect white paper.
            bitmap.eraseColor(CanvasColors.PAPER)
            matrix.setScale(raster.scale, raster.scale)
            matrix.postTranslate(-raster.leftPx.toFloat(), -raster.topPx.toFloat())
            page.render(bitmap, null, matrix, params)
            bitmap.setHasAlpha(false)
            // Starts the upload to the GPU now, which would otherwise hold up the frame that draws it.
            bitmap.prepareToDraw()
            bitmap
        } catch (e: IOException) {
            Log.e(TAG, "Could not read ${raster.source}", e)
            null
        } catch (e: IllegalArgumentException) {
            // A page index the file does not have.
            Log.e(TAG, "Could not render ${raster.source}", e)
            null
        }
        main.post { onRendered(request, bitmap) }
    }

    private fun searchNextPage() {
        val job = synchronized(lock) { search } ?: return
        val entry = job.pages.getOrNull(job.next)
        if (entry == null) {
            synchronized(lock) { if (search === job) search = null }
            main.post { if (!job.cancelled) job.onFinished() }
            return
        }
        job.next++
        val matches = try {
            matchesOn(entry, job.foldedQuery)
        } catch (e: IOException) {
            Log.e(TAG, "Could not search ${entry.source}", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not search ${entry.source}", e)
            emptyList()
        }
        if (matches.isNotEmpty()) main.post { if (!job.cancelled) job.onMatches(matches) }
    }

    /**
     * The text is read once per page and kept, so that a page with no hit costs nothing the next time
     * and typing another letter only asks the renderer about pages that have one.
     */
    private fun matchesOn(entry: SearchJob.Entry, foldedQuery: String): List<TextMatch> {
        val text = pageTexts.getOrPut(entry.source) {
            SearchableText(open(entry.source).textContents.joinToString("\n") { it.text.orEmpty() })
        }
        val ranges = text.find(foldedQuery)
        if (ranges.isEmpty()) return emptyList()
        val page = open(entry.source)
        return ranges.map { range ->
            // Selecting by position works for any match, unlike the platform's own search, which knows
            // nothing of accents and ligatures and stops at a line break.
            val selection = page.selectContent(SelectionBoundary(range.first), SelectionBoundary(range.last + 1))
            val rects = selection?.selectedTextContents.orEmpty().flatMap { it.bounds }
            TextMatch(entry.pageId, rects.map { Bounds(it.left, it.top, it.right, it.bottom) })
        }
    }

    private fun open(source: PageSource.PdfPage): PdfRenderer.Page {
        openPage?.let { if (openSource == source) return it }
        openPage?.close()
        openPage = null
        val renderer = renderers.getOrPut(source.assetId) {
            val file = store.assetFile(source.assetId)
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
        return renderer.openPage(source.pageIndex).also {
            openPage = it
            openSource = source
        }
    }

    private fun closeEverything() {
        openPage?.close()
        renderers.values.forEach(PdfRenderer::close)
    }

    private companion object {
        const val TAG = "PdfWorker"

        /** About what two Pad 7 screens need, so a fling cannot pile up work nobody will see. */
        const val MAX_QUEUED = 160

        val NO_REQUEST = PdfRequest(
            PdfRaster(PageSource.PdfPage("", 0), 0f, 0.0, 0.0, 0, 0, isPreview = false),
            pageId = 0L,
            worldX = 0f,
            worldY = 0f,
        )
    }
}

/** A running search over [pages], in order. */
internal class SearchJob(
    val foldedQuery: String,
    val pages: List<Entry>,
    val onMatches: (List<TextMatch>) -> Unit,
    val onFinished: () -> Unit,
) {
    class Entry(val pageId: Long, val source: PageSource.PdfPage)

    /** Worker thread only. */
    var next = 0

    /** Main thread only, so results already on their way there are dropped when a search is replaced. */
    var cancelled = false
}
