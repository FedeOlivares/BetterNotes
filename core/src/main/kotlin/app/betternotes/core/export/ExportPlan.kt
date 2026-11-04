package app.betternotes.core.export

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** Which pages to export. */
sealed interface PageSelection {
    data object All : PageSelection

    /** Indices counted from 0. Repeats and pages out of range are dropped, and the order is kept. */
    data class Pages(val indices: List<Int>) : PageSelection

    /** Pages [first] to [last], counted from 0 and both included. */
    data class Range(val first: Int, val last: Int) : PageSelection

    fun resolve(pageCount: Int): List<Int> = when (this) {
        All -> (0 until pageCount).toList()
        is Pages -> indices.distinct().filter { it in 0 until pageCount }
        is Range -> (first.coerceAtLeast(0)..last.coerceAtMost(pageCount - 1)).toList()
    }
}

enum class ImageFormat(val extension: String) { PNG("png"), JPEG("jpg") }

data class ImageExportOptions(
    val format: ImageFormat = ImageFormat.PNG,
    val dpi: Float = DEFAULT_DPI,
    /** JPEG only, from 0 to 100. */
    val quality: Int = DEFAULT_JPEG_QUALITY,
) {
    init {
        require(dpi > 0f) { "Bad resolution $dpi" }
        require(quality in 0..100) { "Bad quality $quality" }
    }

    companion object {
        const val DEFAULT_DPI = 300f
        const val DEFAULT_JPEG_QUALITY = 92
    }
}

/**
 * What one exported page shows and at what size: [area], in the page's own points, is drawn at
 * [scale] onto a page or image of [outputWidth] by [outputHeight]. Those are points in a PDF and
 * pixels in an image.
 */
data class ExportFrame(val area: Bounds, val scale: Float, val outputWidth: Int, val outputHeight: Int) {
    companion object {
        /** The largest page side the PDF format allows readers to reject beyond. */
        const val MAX_PDF_SIDE_PT = 14400f

        const val MAX_IMAGE_SIDE_PX = 16384f

        /** 64 million pixels take 256 MB as a bitmap. A larger canvas comes out less sharp rather than failing. */
        const val MAX_IMAGE_PIXELS = 64_000_000f

        /** Room left around the ink of an exported canvas, which has no edge of its own. */
        const val CANVAS_MARGIN_PT = 24f

        private const val POINTS_PER_INCH = 72f

        /** What an export of [page] shows. A canvas shows its items, and an empty canvas shows nothing. */
        fun areaOf(page: Page, items: List<PageItem>): Bounds? {
            page.size?.let { return Bounds(0f, 0f, it.widthPt, it.heightPt) }
            if (items.isEmpty()) return null
            return items.map { it.bounds }.reduce(Bounds::union).outset(CANVAS_MARGIN_PT)
        }

        /** One point of the document is one point of the PDF, unless the area is too large for a PDF page. */
        fun forPdf(area: Bounds): ExportFrame = fitted(area, 1f, MAX_PDF_SIDE_PT)

        fun forImage(area: Bounds, dpi: Float): ExportFrame {
            val wanted = dpi / POINTS_PER_INCH
            val pixels = area.width * wanted * area.height * wanted
            val limited = if (pixels > MAX_IMAGE_PIXELS) wanted * sqrt(MAX_IMAGE_PIXELS / pixels) else wanted
            return fitted(area, limited, MAX_IMAGE_SIDE_PX)
        }

        private fun fitted(area: Bounds, wantedScale: Float, maxSide: Float): ExportFrame {
            val scale = min(wantedScale, min(maxSide / area.width, maxSide / area.height))
            return ExportFrame(area, scale, ceil(area.width * scale).toInt(), ceil(area.height * scale).toInt())
        }
    }
}

private val UNSAFE_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private const val MAX_TITLE_CHARS = 120

/**
 * A file name for an exported document or page: the title with characters no file system accepts
 * taken out. [pageNumber] counts from 1 and is padded so that the files sort in page order.
 */
fun exportFileName(title: String, extension: String, pageNumber: Int? = null, pageCount: Int = 0): String {
    val clean = title.replace(UNSAFE_FILE_CHARS, "_").trim().trim('.').take(MAX_TITLE_CHARS).ifEmpty { "Better Notes" }
    if (pageNumber == null) return "$clean.$extension"
    val digits = maxOf(pageCount, pageNumber).toString().length
    return "$clean - ${pageNumber.toString().padStart(digits, '0')}.$extension"
}
