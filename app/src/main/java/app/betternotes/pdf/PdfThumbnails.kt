package app.betternotes.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.model.PageSource
import kotlin.math.roundToInt
import android.util.LruCache
import app.betternotes.ui.theme.CanvasColors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** One-off renders of a whole PDF page, for the library's tiles and the page switcher. */
object PdfThumbnails {
    /**
     * Renders a page [widthPx] wide, keeping its proportions, on white paper. Opens the file each time,
     * so cache the result. Blocks, so call it off the main thread.
     */
    fun render(store: DocumentStore, source: PageSource.PdfPage, widthPx: Int): Bitmap {
        val descriptor = ParcelFileDescriptor.open(store.assetFile(source.assetId), ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(source.pageIndex).use { page ->
                val scale = widthPx.toFloat() / page.width
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = createBitmap(widthPx, height)
                bitmap.eraseColor(CanvasColors.PAPER)
                page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** One at a time and off the main thread, so a long document's thumbnails never compete with the canvas. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** A cached [render], produced in the background. The bitmap is shared, so never recycle it. */
    suspend fun cached(store: DocumentStore, source: PageSource.PdfPage, widthPx: Int): Bitmap {
        val key = "${source.assetId}:${source.pageIndex}:$widthPx"
        cache.get(key)?.let { return it }
        return withContext(worker) {
            cache.get(key) ?: render(store, source, widthPx).also { cache.put(key, it) }
        }
    }

    private const val CACHE_BYTES = 24 * 1024 * 1024
}
