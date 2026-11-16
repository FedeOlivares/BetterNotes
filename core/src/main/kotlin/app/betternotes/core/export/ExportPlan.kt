package app.betternotes.core.export

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
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

/** How far the export sheet's page scale slider goes. 100% is the page's own size. */
val PAGE_SCALE_RANGE = 0.5f..2.5f

data class PdfExportOptions(
    val scale: Float = 1f,
    val invert: Boolean = false,
    /**
     * Turns each page into one picture. The PDF is then exactly what the screen shows, but its ink can no
     * longer be zoomed without blurring, and nothing in it can be selected.
     */
    val rasterize: Boolean = false,
) {
    init {
        require(scale in PAGE_SCALE_RANGE) { "Bad scale $scale" }
    }
}

data class ImageExportOptions(
    val format: ImageFormat = ImageFormat.PNG,
    val dpi: Float = DEFAULT_DPI,
    /** JPEG only, from 0 to 100. */
    val quality: Int = DEFAULT_JPEG_QUALITY,
    /** Multiplies [dpi], as the export sheet's page scale does. */
    val scale: Float = 1f,
    /** Swaps every color for its opposite, for reading a light page on a dark screen. */
    val invert: Boolean = false,
) {
    init {
        require(dpi > 0f) { "Bad resolution $dpi" }
        require(quality in 0..100) { "Bad quality $quality" }
        require(scale in PAGE_SCALE_RANGE) { "Bad scale $scale" }
    }

    /** The resolution the pages are drawn at. */
    val effectiveDpi get() = dpi * scale

    companion object {
        const val DEFAULT_DPI = 300f
        const val DEFAULT_JPEG_QUALITY = 92
    }
}

/**
 * What one exported page shows and at what size: [area], in the page's own points, is drawn at
 * [scale] onto a page or image of [outputWidth] by [outputHeight], turned [turn] degrees clockwise about its
 * middle. Those are points in a PDF and pixels in an image. A turned page comes out as the box around it.
 */
data class ExportFrame(val area: Bounds, val scale: Float, val outputWidth: Int, val outputHeight: Int, val turn: Float = 0f) {
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

        /** How far [page] is turned in an export. A canvas has no edge to turn about, so it is never turned. */
        fun turnOf(page: Page): Float = if (page.size == null) 0f else page.rotation

        /** The width and height of the box around a [width] by [height] rectangle turned [turn] degrees. */
        fun turnedSize(width: Float, height: Float, turn: Float): Pair<Float, Float> {
            val radians = Math.toRadians(turn.toDouble())
            val cos = abs(cos(radians)).toFloat()
            val sin = abs(sin(radians)).toFloat()
            // A quarter turn is exact, not a hair over from the float sine.
            return when (((turn % 180f) + 180f) % 180f) {
                0f -> width to height
                90f -> height to width
                else -> (width * cos + height * sin) to (width * sin + height * cos)
            }
        }

        /** One point of the document is one point of the PDF, unless the area is too large for a PDF page. */
        fun forPdf(area: Bounds, scale: Float = 1f, turn: Float = 0f): ExportFrame = fitted(area, scale, MAX_PDF_SIDE_PT, turn)

        fun forImage(area: Bounds, dpi: Float, turn: Float = 0f): ExportFrame {
            val wanted = dpi / POINTS_PER_INCH
            val (width, height) = turnedSize(area.width, area.height, turn)
            val pixels = width * wanted * height * wanted
            val limited = if (pixels > MAX_IMAGE_PIXELS) wanted * sqrt(MAX_IMAGE_PIXELS / pixels) else wanted
            return fitted(area, limited, MAX_IMAGE_SIDE_PX, turn)
        }

        private fun fitted(area: Bounds, wantedScale: Float, maxSide: Float, turn: Float): ExportFrame {
            val (width, height) = turnedSize(area.width, area.height, turn)
            val scale = min(wantedScale, min(maxSide / width, maxSide / height))
            return ExportFrame(area, scale, ceil(width * scale).toInt(), ceil(height * scale).toInt(), turn)
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
