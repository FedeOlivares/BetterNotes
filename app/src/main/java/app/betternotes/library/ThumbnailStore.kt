package app.betternotes.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import app.betternotes.canvas.BrushTextures
import app.betternotes.canvas.InkBrushes
import app.betternotes.canvas.CanvasTemplateSink
import app.betternotes.canvas.ItemPainter
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.canvas.toInkStroke
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.library.LibraryStore
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.FillItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.canvas.spannedText
import app.betternotes.core.model.TextItem
import app.betternotes.core.template.CoverDesign
import app.betternotes.ui.theme.templateLanguage
import app.betternotes.core.template.draw
import app.betternotes.pdf.PdfThumbnails
import app.betternotes.ui.theme.CanvasColors
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

    /** The last number is the picture's shape (2: square, 3: landscape covers zoomed, 4: post-its, tables and tape drawn; 5: locked items under the rest; 6: what open post-its hold), so pictures kept from the older shape are drawn again. */
    private fun key(id: Long, savedAtMs: Long) = java.lang.Long.toHexString(id) + "-" + savedAtMs + "-6"

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
    private const val LANDSCAPE_COVER_ZOOM = 1.15f

    /** The tile's picture is square, as in the reference. */
    const val HEIGHT_PX = 720

    /** How far a sparse canvas may zoom in, in pixels per point, so a single word does not fill the tile. */
    private const val MAX_CANVAS_SCALE = 2f
    private const val CANVAS_PADDING = 0.92f

    /** Below this a canvas's pattern would be a dense blur that costs thousands of draws. */
    private const val MIN_PATTERN_SCALE = 0.5f

    fun render(store: DocumentStore): Bitmap {
        val document = store.readDocument()
        val page = document.pages[0]
        val items = store.readShownItems(page.id)
        val isPdf = document.kind == DocumentKind.PDF
        // A cover is shown across its whole width and cut at the top and bottom around its middle.
        val heightPx = HEIGHT_PX
        val bitmap = createBitmap(WIDTH_PX, heightPx)
        bitmap.eraseColor(if (isPdf) CanvasColors.PAPER else page.background.color)
        val canvas = Canvas(bitmap)
        val view = if (page.size == null) canvasView(items) else pageView(page, heightPx)
        (page.source as? PageSource.PdfPage)?.let { drawPdf(canvas, store, it, view.top) }
        canvas.withTranslation(view.left, view.top) {
            scale(view.scale, view.scale)
            val region = Bounds(
                -view.left / view.scale,
                -view.top / view.scale,
                (WIDTH_PX - view.left) / view.scale,
                (heightPx - view.top) / view.scale,
            )
            if ((!isPdf || page.isCover) && view.scale >= MIN_PATTERN_SCALE) drawPattern(this, page, region, store.templateLanguage())
            drawItems(this, store, items, view.scale)
        }
        return bitmap
    }

    /** The whole first page, [widthPx] wide, as the New Notebook dialog shows a cover that was edited: its design and everything on it. */
    fun renderWhole(store: DocumentStore, widthPx: Int): Bitmap {
        val document = store.readDocument()
        val page = document.pages[0]
        val size = page.size ?: return createBitmap(widthPx, widthPx).also { it.eraseColor(page.background.color) }
        val items = store.readShownItems(page.id)
        val scale = widthPx / size.widthPt
        val bitmap = createBitmap(widthPx, (size.heightPt * scale).toInt().coerceAtLeast(1))
        bitmap.eraseColor(page.background.color)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        drawPattern(canvas, page, Bounds(0f, 0f, size.widthPt, size.heightPt), store.templateLanguage())
        drawItems(canvas, store, items, scale)
        return bitmap
    }

    private class View(val scale: Float, val left: Float, val top: Float)

    /** Fits the page's width and shows its top. A page too short to fill the tile sits in the middle. */
    private fun pageView(page: Page, heightPx: Int): View {
        val size = page.size!!
        if (page.isCover) {
            // The cover fills the width, and the picture shows its middle: the top and bottom are cut.
            // A landscape cover is zoomed in a little, so it fills the picture instead of sitting small in it.
            val fit = WIDTH_PX / size.widthPt * if (size.widthPt > size.heightPt) LANDSCAPE_COVER_ZOOM else 1f
            return View(fit, (WIDTH_PX - size.widthPt * fit) / 2f, (heightPx - size.heightPt * fit) / 2f)
        }
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

    private fun drawPattern(canvas: Canvas, page: Page, region: Bounds, language: String) {
        val sink = CanvasTemplateSink()
        sink.languageOverride = language
        sink.canvas = canvas
        val cover = CoverDesign.byId(page.cover)
        val size = page.size
        if (cover != null && size != null) cover.draw(sink, size, page.background.template)
        else page.background.template.draw(page.size, region, sink, paper = page.background.color)
        sink.flush()
    }

    /** Every item type: ink, pictures and text are drawn here, the rest by the canvas's own painter. */
    private fun drawItems(canvas: Canvas, store: DocumentStore, items: List<PageItem>, scale: Float) {
        val renderer = CanvasStrokeRenderer.create(BrushTextures)
        val strokeToBitmap = Matrix().apply { setScale(scale, scale) }
        // Shapes and paths, such as the items of a cover the owner has made editable, paint as they do on the page.
        val shapes = ItemPainter()
        // Locked items, such as a cover's design, are a layer below the rest whatever the order, as on the page.
        for (item in items.filter { it.locked } + items.filter { !it.locked }) {
            when (item) {
                is StrokeItem -> InkBrushes.draw(renderer, canvas, item.toInkStroke(), strokeToBitmap)
                is ImageItem -> drawImage(canvas, store, item, scale)
                is TextItem -> drawText(canvas, item)
                // A post-it shows as on the page: its paper, and open, what it holds, ink and pictures too, drawn whole past its edges.
                is StickyNoteItem -> {
                    shapes.paint(canvas, item.copy(items = emptyList()))
                    if (item.isOpen && item.items.isNotEmpty()) {
                        val frame = item.frame
                        canvas.withRotation(frame.rotation, frame.centerX, frame.centerY) {
                            withTranslation(frame.left, frame.top) { drawItems(this, store, item.items, scale) }
                        }
                    }
                }
                else -> shapes.paint(canvas, item)
            }
        }
    }

    /** A picture read at about the size it is shown, for items inside a post-it. */
    private fun assetBitmap(store: DocumentStore, assetId: String, scale: Float): Bitmap? {
        val file = store.assetFile(assetId)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val shownPx = (bounds.outWidth * scale).toInt().coerceAtLeast(1)
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = (bounds.outWidth / shownPx).coerceAtLeast(1) })
    }

    private fun drawText(canvas: Canvas, item: TextItem) {
        val frame = item.frame
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.color
            textSize = item.textSize
            if (item.spans.isEmpty()) app.betternotes.fonts.FontLibrary.face(item.style.font, item.style.bold, item.style.italic)?.let { typeface = it }
        }
        val wide = frame.width.toInt().coerceAtLeast(1)
        val plain = if (item.spans.isEmpty()) item.text else spannedText(item.text, item.textSize, item.style, item.spans)
        val shown = app.betternotes.canvas.withListMarks(plain, item.style, item.color, item.textSize, paint, wide)
        val layout = StaticLayout.Builder.obtain(shown, 0, item.text.length, paint, wide).build()
        canvas.withTranslation(frame.left, frame.top) { layout.draw(this) }
    }

    private fun drawImage(canvas: Canvas, store: DocumentStore, item: ImageItem, scale: Float) {
        val file = store.assetFile(item.assetId)
        val frame = item.frame
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        // A picture is far larger than a tile needs, and this reads only about as much of it as is shown.
        val shownPx = (frame.width * scale).toInt().coerceAtLeast(1)
        val options = BitmapFactory.Options().apply { inSampleSize = (bounds.outWidth / shownPx).coerceAtLeast(1) }
        val image = BitmapFactory.decodeFile(file.path, options) ?: return
        canvas.withRotation(frame.rotation, frame.centerX, frame.centerY) {
            val target = RectF(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height)
            drawBitmap(image, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        image.recycle()
    }
}
