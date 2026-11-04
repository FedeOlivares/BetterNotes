package app.betternotes.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.model.PageSource
import kotlin.math.roundToInt

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
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
}
