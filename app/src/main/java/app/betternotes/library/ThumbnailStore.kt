package app.betternotes.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import app.betternotes.canvas.CanvasTemplateSink
import app.betternotes.canvas.toInkStroke
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.library.LibraryStore
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.template.draw
import app.betternotes.pdf.PdfThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The tiles' pictures of first pages. A picture's file name carries the time its document was saved,
 * so a changed document simply asks for a file that does not exist yet. The files are in the cache
 * folder because any of them can be drawn again.
 */
class ThumbnailStore(context: Context, private val library: LibraryStore) {
    private val folder = File(context.cacheDir, "thumbnails")
    private val workers = Dispatchers.IO.limitedParallelism(MAX_RENDERS)
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** The picture if it is in memory, so a tile scrolled back into view needs no placeholder. */
    fun cached(id: Long, savedAtMs: Long): Bitmap? = memory[key(id, savedAtMs)]

    /** Returns the picture, drawing it first if need be, or null if the document cannot be read. */
    suspend fun load(id: Long, savedAtMs: Long): Bitmap? {
        val key = key(id, savedAtMs)
        memory[key]?.let { return it }
        return withContext(workers) {
            val bitmap = decode(file(key)) ?: renderAndSave(id, key)
            bitmap?.also { memory.put(key, it) }
        }
    }

    /** Deletes the pictures of a document that is gone. Blocks on the disk. */
    fun forget(id: Long) = deleteFiles(id, keep = null)

    private fun decode(file: File): Bitmap? {
        if (!file.exists()) return null
        // A hardware bitmap needs no upload when it is first drawn, so it cannot cost a scrolling frame.
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun renderAndSave(id: Long, key: String): Bitmap? = try {
        val bitmap = ThumbnailRenderer.render(library.documentStore(id))
        save(bitmap, key, id)
        bitmap.copy(Bitmap.Config.HARDWARE, false).also { bitmap.recycle() }
    } catch (e: IOException) {
        Log.e(TAG, "Could not draw the thumbnail of $id", e)
        null
    }

    private fun save(bitmap: Bitmap, key: String, id: Long) {
        folder.mkdirs()
        val target = file(key)
        val temp = File(folder, target.name + ".tmp")
        FileOutputStream(temp).use { out -> bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out) }
        if (!temp.renameTo(target)) temp.delete()
        deleteFiles(id, keep = target)
    }

    private fun deleteFiles(id: Long, keep: File?) {
        val prefix = java.lang.Long.toHexString(id) + "-"
        folder.listFiles { file -> file.name.startsWith(prefix) && file != keep }?.forEach { it.delete() }
    }

    private fun file(key: String) = File(folder, "$key.webp")

    private fun key(id: Long, savedAtMs: Long) = java.lang.Long.toHexString(id) + "-" + savedAtMs

    private companion object {
        const val TAG = "Thumbnails"
        const val MAX_RENDERS = 2
        const val QUALITY = 85
        const val MEMORY_BYTES = 64 * 1024 * 1024
    }
}

/** Draws a document's first page into a picture a tile can crop. Safe from any thread. */
internal object ThumbnailRenderer {
    /** A little wider than the widest tile on the Pad 7, with about the same proportions. */
    const val WIDTH_PX = 720
    const val HEIGHT_PX = 540

    /** How far a sparse canvas may zoom in, in pixels per point, so a single word does not fill the tile. */
    private const val MAX_CANVAS_SCALE = 2f
    private const val CANVAS_PADDING = 0.92f

    /** Below this a canvas's pattern would be a dense blur that costs thousands of draws. */
    private const val MIN_PATTERN_SCALE = 0.5f

    private const val WHITE = 0xFFFFFFFF.toInt()

    fun render(store: DocumentStore): Bitmap {
        val document = store.readDocument()
        val page = document.pages[0]
        val items = store.readPage(page.id)
        val isPdf = document.kind == DocumentKind.PDF
        val bitmap = createBitmap(WIDTH_PX, HEIGHT_PX)
        bitmap.eraseColor(if (isPdf) WHITE else page.background.color)
        val canvas = Canvas(bitmap)
        val view = if (page.size == null) canvasView(items) else pageView(page)
        (page.source as? PageSource.PdfPage)?.let { drawPdf(canvas, store, it, view.top) }
        canvas.withTranslation(view.left, view.top) {
            scale(view.scale, view.scale)
            val region = Bounds(
                -view.left / view.scale,
                -view.top / view.scale,
                (WIDTH_PX - view.left) / view.scale,
                (HEIGHT_PX - view.top) / view.scale,
            )
            if (!isPdf && view.scale >= MIN_PATTERN_SCALE) drawPattern(this, page, region)
            drawItems(this, items, view.scale)
        }
        return bitmap
    }

    private class View(val scale: Float, val left: Float, val top: Float)

    /** Fits the page's width and shows its top. A page too short to fill the tile sits in the middle. */
    private fun pageView(page: Page): View {
        val size = page.size!!
        val scale = WIDTH_PX / size.widthPt
        val height = size.heightPt * scale
        return View(scale, 0f, if (height < HEIGHT_PX) (HEIGHT_PX - height) / 2f else 0f)
    }

    /** A canvas has no first page to crop, so it shows all of its content. */
    private fun canvasView(items: List<PageItem>): View {
        val bounds = items.map { it.bounds }.reduceOrNull { a, b -> a.union(b) }
            ?: return View(1f, WIDTH_PX / 2f, HEIGHT_PX / 2f)
        val width = (bounds.right - bounds.left).coerceAtLeast(1f)
        val height = (bounds.bottom - bounds.top).coerceAtLeast(1f)
        val scale = minOf(WIDTH_PX / width, HEIGHT_PX / height, MAX_CANVAS_SCALE) * CANVAS_PADDING
        return View(
            scale,
            (WIDTH_PX - width * scale) / 2f - bounds.left * scale,
            (HEIGHT_PX - height * scale) / 2f - bounds.top * scale,
        )
    }

    private fun drawPdf(canvas: Canvas, store: DocumentStore, source: PageSource.PdfPage, top: Float) {
        val page = PdfThumbnails.render(store, source, WIDTH_PX)
        canvas.drawBitmap(page, 0f, top, Paint(Paint.FILTER_BITMAP_FLAG))
        page.recycle()
    }

    private fun drawPattern(canvas: Canvas, page: Page, region: Bounds) {
        val sink = CanvasTemplateSink()
        sink.canvas = canvas
        page.background.template.draw(page.size, region, sink)
        sink.flush()
    }

    /** Only ink for now, like the canvas's painter. New item types need adding in both. */
    private fun drawItems(canvas: Canvas, items: List<PageItem>, scale: Float) {
        val renderer = CanvasStrokeRenderer.create()
        val strokeToBitmap = Matrix().apply { setScale(scale, scale) }
        for (item in items) {
            if (item is StrokeItem) renderer.draw(canvas, item.toInkStroke(), strokeToBitmap)
        }
    }
}
