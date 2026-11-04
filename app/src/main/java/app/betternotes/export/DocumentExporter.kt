package app.betternotes.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.ink.strokes.Stroke
import app.betternotes.canvas.NoteEditor
import app.betternotes.canvas.PagePainter
import app.betternotes.canvas.PaintSource
import app.betternotes.canvas.toInkStroke
import app.betternotes.core.export.ExportFrame
import app.betternotes.core.export.ImageExportOptions
import app.betternotes.core.export.ImageFormat
import app.betternotes.core.export.PageSelection
import app.betternotes.core.export.exportFileName
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.StrokeItem
import app.betternotes.pdf.PdfThumbnails
import java.io.IOException
import java.io.OutputStream
import kotlin.math.ceil

/**
 * Writes pages to a PDF or to images, from the files on disk, so the document can stay open and keep
 * changing. Only one page's ink is in memory at a time, so a long document exports in the memory of
 * its largest page. Everything here blocks, so run it on a background thread.
 *
 * Ink goes into a PDF as vector paths, and so do templates, so they stay sharp at any zoom. The pages
 * of an imported PDF do not: they go in as pixels at [PDF_PAGE_DPI], and lose their selectable text.
 */
class DocumentExporter(private val store: DocumentStore, private val title: String, pages: List<Page>) {
    private val pages = pages.toList()
    private val source = ExportSource()
    private val painter = PagePainter(source)
    private val rasterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Writes one PDF holding every selected page. An empty canvas page is left out. Returns the pages written. */
    @Throws(IOException::class)
    fun writePdf(selection: PageSelection, out: OutputStream, cancelled: () -> Boolean = { false }): Int {
        val pdf = PdfDocument()
        try {
            var written = 0
            for (index in selection.resolve(pages.size)) {
                if (cancelled()) return written
                val area = loadPage(index) ?: continue
                val frame = ExportFrame.forPdf(area)
                val info = PdfDocument.PageInfo.Builder(frame.outputWidth, frame.outputHeight, written + 1).create()
                val page = pdf.startPage(info)
                paint(page.canvas, pages[index], frame, PDF_PAGE_DPI / POINTS_PER_INCH)
                pdf.finishPage(page)
                written++
            }
            source.clear()
            if (written > 0) pdf.writeTo(out)
            return written
        } finally {
            pdf.close()
        }
    }

    /**
     * Writes one image per selected page, asking [open] for where each goes, by file name. The stream
     * is closed here. An empty canvas page is left out. Returns the images written.
     */
    @Throws(IOException::class)
    fun writeImages(
        selection: PageSelection,
        options: ImageExportOptions,
        open: (fileName: String) -> OutputStream,
        cancelled: () -> Boolean = { false },
    ): Int {
        val indices = selection.resolve(pages.size)
        val format = when (options.format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }
        var written = 0
        for (index in indices) {
            if (cancelled()) break
            val area = loadPage(index) ?: continue
            val frame = ExportFrame.forImage(area, options.dpi)
            val bitmap = createBitmap(frame.outputWidth, frame.outputHeight)
            try {
                paint(Canvas(bitmap), pages[index], frame, frame.scale)
                val name = exportFileName(title, options.format.extension, index + 1, pages.size)
                open(name).use { out ->
                    if (!bitmap.compress(format, options.quality, out)) throw IOException("Could not encode $name")
                }
            } finally {
                bitmap.recycle()
            }
            written++
        }
        source.clear()
        return written
    }

    private fun loadPage(index: Int): Bounds? {
        val page = pages[index]
        val items = store.readPage(page.id)
        source.show(page.id, PageContent(items))
        return ExportFrame.areaOf(page, items)
    }

    private fun paint(canvas: Canvas, page: Page, frame: ExportFrame, pdfPxPerPt: Float) {
        canvas.withSave {
            scale(frame.scale, frame.scale)
            translate(-frame.area.left, -frame.area.top)
            (page.source as? PageSource.PdfPage)?.let { paintPdf(this, page, it, pdfPxPerPt) }
            painter.paintPage(this, page, frame.area, frame.scale)
        }
    }

    /**
     * Draws a PDF page as pixels, since the platform offers no way to copy a page's vectors into another
     * PDF. An image gets one pixel per output pixel, and a PDF gets [PDF_PAGE_DPI].
     */
    private fun paintPdf(canvas: Canvas, page: Page, pdf: PageSource.PdfPage, pxPerPt: Float) {
        val size = page.size ?: return
        val raster = PdfThumbnails.render(store, pdf, ceil(size.widthPt * pxPerPt).toInt())
        canvas.drawBitmap(raster, null, RectF(0f, 0f, size.widthPt, size.heightPt), rasterPaint)
        raster.recycle()
    }

    /** Hands the painter the one page being exported, and meshes its ink with no cache to outlive it. */
    private inner class ExportSource : PaintSource {
        private var pageId = 0L
        private var content: PageContent? = null

        override val pages get() = this@DocumentExporter.pages

        fun show(pageId: Long, content: PageContent) {
            this.pageId = pageId
            this.content = content
        }

        fun clear() {
            content = null
        }

        override fun contentOrLoad(pageId: Long) = content.takeIf { pageId == this.pageId }

        override fun mesh(item: StrokeItem): Stroke = item.toInkStroke()
    }

    private companion object {
        const val PDF_PAGE_DPI = 300f
        const val POINTS_PER_INCH = 72f
    }
}

/**
 * Saves first, so that the export includes the latest edits, and snapshots the page list. Call this
 * on the main thread, then run the exporter on a background thread.
 */
fun NoteEditor.exporter(): DocumentExporter {
    saveNow()
    return DocumentExporter(store, document.title, document.pages)
}
