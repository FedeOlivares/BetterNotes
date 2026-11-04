package app.betternotes.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
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
import app.betternotes.core.model.StrokeItem
import java.io.IOException
import java.io.OutputStream

/**
 * Writes pages to a PDF or to images, from the files on disk, so the document can stay open and keep
 * changing. Only one page's ink is in memory at a time, so a long document exports in the memory of
 * its largest page. Everything here blocks, so run it on a background thread.
 *
 * Ink goes into a PDF as vector paths, and so do templates. An exported PDF stays sharp at any zoom.
 */
class DocumentExporter(private val store: DocumentStore, private val title: String, pages: List<Page>) {
    private val pages = pages.toList()
    private val source = ExportSource()
    private val painter = PagePainter(source)

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
                paint(page.canvas, pages[index], frame)
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
            val bitmap = Bitmap.createBitmap(frame.outputWidth, frame.outputHeight, Bitmap.Config.ARGB_8888)
            try {
                paint(Canvas(bitmap), pages[index], frame)
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

    private fun paint(canvas: Canvas, page: Page, frame: ExportFrame) {
        canvas.withSave {
            scale(frame.scale, frame.scale)
            translate(-frame.area.left, -frame.area.top)
            painter.paintPage(this, page, frame.area, frame.scale)
        }
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
}

/**
 * Saves first, so that the export includes the latest edits, and snapshots the page list. Call this
 * on the main thread, then run the exporter on a background thread.
 */
fun NoteEditor.exporter(): DocumentExporter {
    saveNow()
    return DocumentExporter(store, document.title, document.pages)
}
