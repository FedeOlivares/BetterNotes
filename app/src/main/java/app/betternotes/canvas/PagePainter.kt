package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import app.betternotes.canvas.BrushTextures
import androidx.ink.strokes.Stroke
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.layout.DocumentLayout
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.draw

/** What a [PagePainter] paints from. */
internal interface PaintSource {
    val pages: List<Page>

    /** The page's items, or null if they are not available yet. */
    fun contentOrLoad(pageId: Long): PageContent?

    fun mesh(item: StrokeItem): Stroke

    /** A decoded picture, or null if it is not ready yet or this source has none. */
    fun image(assetId: String): Bitmap? = null

    /**
     * Paints a PDF page's pixels into a tile, ahead of the ink. [x] and [y] are where the page's corner
     * falls on the tile's canvas at [scale], and [originX] and [originY] are where it sits in the
     * document. Only a source that paints tiles needs this: an export paints whole pages through
     * [PagePainter.paintPage], and draws the PDF under them itself.
     */
    fun paintPdfTile(
        canvas: Canvas,
        page: Page,
        pdf: PageSource.PdfPage,
        originX: Float,
        originY: Float,
        x: Double,
        y: Double,
        scale: Float,
    ) = Unit
}

/**
 * Paints pages: the part of a document inside one tile, or a page for an export. Use from one thread,
 * which must be the one its [PaintSource] expects.
 */
internal class PagePainter(private val source: PaintSource) {
    private val renderer = CanvasStrokeRenderer.create(BrushTextures)
    private val paper = Paint()
    private val template = CanvasTemplateSink()
    private val strokeToTile = Matrix()
    private val items = ItemPainter(source::image) { canvas, stroke ->
        InkBrushes.draw(renderer, canvas, source.mesh(stroke), strokeToTile)
    }
    private val highlightPaint = Paint().apply { blendMode = BlendMode.MULTIPLY }

    /** What to mark on each page, by page id, such as search results. */
    var highlights: Map<Long, List<PageHighlight>> = emptyMap()

    /**
     * Paints [region], given in document points. [leftPx] and [topPx] are where the canvas's origin
     * sits in document pixels at [scale]. They are doubles because far from the origin at high zoom a
     * float could not hold a whole pixel.
     */
    fun paint(canvas: Canvas, layout: DocumentLayout, region: Bounds, leftPx: Double, topPx: Double, scale: Float) {
        val pages = source.pages
        layout.forEachPageIn(region) { index ->
            val originX = layout.originX(index)
            val originY = layout.originY(index)
            val x = originX * scale.toDouble() - leftPx
            val y = originY * scale.toDouble() - topPx
            val page = pages[index]
            val pdfSource = page.source as? PageSource.PdfPage
            // Before the translation below, because PDF rasters sit on the tile's own pixels.
            if (pdfSource != null) source.paintPdfTile(canvas, page, pdfSource, originX, originY, x, y, scale)
            canvas.withTranslation(x.toFloat(), y.toFloat()) {
                scale(scale, scale)
                val local = Bounds(region.left - originX, region.top - originY, region.right - originX, region.bottom - originY)
                paintPage(this, page, local, scale)
            }
        }
    }

    /**
     * Paints [page] into a canvas already scaled by [scale], with its origin at the page's origin. A
     * PDF page gets no paper here, because its pixels are already on the canvas.
     */
    fun paintPage(canvas: Canvas, page: Page, region: Bounds, scale: Float) {
        val size = page.size
        if (size != null) canvas.clipRect(0f, 0f, size.widthPt, size.heightPt)
        if (page.source == PageSource.Blank) paintPaper(canvas, page, region, scale)
        highlights[page.id]?.let { paintHighlights(canvas, it) }
        source.contentOrLoad(page.id)?.let { paintItems(canvas, it, region, scale) }
    }

    private fun paintPaper(canvas: Canvas, page: Page, region: Bounds, scale: Float) {
        val size = page.size
        paper.color = page.background.color
        if (size != null) {
            canvas.drawRect(0f, 0f, size.widthPt, size.heightPt, paper)
        } else {
            canvas.drawRect(region.left, region.top, region.right, region.bottom, paper)
        }
        val pattern = page.background.template
        if (!pattern.isTooDense(scale)) {
            template.canvas = canvas
            pattern.draw(size, region, template)
            template.flush()
        }
    }

    /** Multiplying keeps the text under a highlight readable, as a marker on paper does. */
    private fun paintHighlights(canvas: Canvas, rects: List<PageHighlight>) {
        for (highlight in rects) {
            highlightPaint.color = if (highlight.isCurrent) CURRENT_HIGHLIGHT_COLOR else HIGHLIGHT_COLOR
            val r = highlight.rect
            canvas.drawRect(r.left, r.top, r.right, r.bottom, highlightPaint)
        }
    }

    private fun paintItems(canvas: Canvas, content: PageContent, region: Bounds, scale: Float) {
        strokeToTile.setScale(scale, scale)
        content.forEachIntersecting(region) { _, item ->
            if (item is StrokeItem) {
                renderer.draw(canvas, source.mesh(item), strokeToTile)
            } else {
                items.paint(canvas, item)
            }
        }
    }

    /** A dot or hexagon pattern this fine reads as a flat tint, and would cost thousands of draws per tile. */
    private fun PageTemplate.isTooDense(scale: Float) = when (this) {
        is PageTemplate.Dotted -> spacing * scale < MIN_PATTERN_SPACING_PX
        is PageTemplate.Hexagonal -> side * scale < MIN_PATTERN_SPACING_PX
        else -> false
    }

    private companion object {
        const val MIN_PATTERN_SPACING_PX = 4f
        const val HIGHLIGHT_COLOR = 0xFFFFE680.toInt()
        const val CURRENT_HIGHLIGHT_COLOR = 0xFFFFA726.toInt()
    }
}

/** A rectangle to mark on a page, in the page's own points. */
internal class PageHighlight(val rect: Bounds, val isCurrent: Boolean)

/** Draws template lines directly, and batches dots, of which a dotted page has thousands. */
internal class CanvasTemplateSink : TemplateSink {
    lateinit var canvas: Canvas

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var dots = FloatArray(INITIAL_DOT_FLOATS)
    private var dotFloats = 0

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        flush()
        linePaint.color = color
        linePaint.strokeWidth = width
        canvas.drawLine(x0, y0, x1, y1, linePaint)
    }

    override fun dot(x: Float, y: Float, radius: Float, color: Int) {
        if (dotPaint.color != color || dotPaint.strokeWidth != radius * 2f) {
            flush()
            dotPaint.color = color
            dotPaint.strokeWidth = radius * 2f
        }
        if (dotFloats == dots.size) dots = dots.copyOf(dots.size * 2)
        dots[dotFloats++] = x
        dots[dotFloats++] = y
    }

    fun flush() {
        if (dotFloats == 0) return
        canvas.drawPoints(dots, 0, dotFloats, dotPaint)
        dotFloats = 0
    }

    private companion object {
        const val INITIAL_DOT_FLOATS = 2048
    }
}
