package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import androidx.ink.strokes.Stroke
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.TileGrid
import app.betternotes.core.layout.DocumentLayout
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.CoverDesign
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import app.betternotes.core.template.draw
import app.betternotes.core.template.isMirroredOn
import app.betternotes.ui.theme.CanvasColors

/** What a [PagePainter] paints from. */
internal interface PaintSource {
    val pages: List<Page>

    /** The page's items, or null if they are not available yet. */
    fun contentOrLoad(pageId: Long): PageContent?

    fun mesh(item: StrokeItem): Stroke

    /** The mesh of a no-overlap highlighter stroke, in an opaque brush. A source without one gives the plain mesh. */
    fun opaqueMesh(item: StrokeItem): Stroke = mesh(item)

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

/** Locked items first, then the rest, so the locked ones lie below. */

/**
 * Paints pages: the part of a document inside one tile, or a page for an export. Use from one thread,
 * which must be the one its [PaintSource] expects.
 */
internal class PagePainter(private val source: PaintSource, private val ink: InkRenderer = InkRenderer()) {
    private val paper = Paint()
    private val template = CanvasTemplateSink()
    private val strokeToTile = Matrix()
    private val items = ItemPainter(source::image) { canvas, stroke ->
        ink.drawOrStandIn(canvas, source.mesh(stroke), strokeToTile)
    }
    private val layerBounds = android.graphics.RectF()
    private val highlightPaint = Paint().apply { blendMode = BlendMode.MULTIPLY }

    /** The item whose text is being typed over the page, which the page leaves out while the box shows it. */
    var hiddenItemId: Long
        get() = items.hiddenItemId
        set(value) {
            items.hiddenItemId = value
        }

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
            val turn = layout.turn(index)
            canvas.withTranslation(x.toFloat(), y.toFloat()) {
                scale(scale, scale)
                // A turned page turns about its middle. What of it the tile shows is the box around the tile's corners as they lie on the page.
                if (turn != 0f) rotate(turn, layout.pageWidth(index) / 2f, layout.pageHeight(index) / 2f)
                val local = if (turn == 0f) {
                    Bounds(region.left - originX, region.top - originY, region.right - originX, region.bottom - originY)
                } else {
                    localBounds(layout, index, region)
                }
                paintPage(this, page, local, scale)
            }
        }
    }

    private val corner = FloatArray(2)

    /** The box, in the coordinates of the turned page, around a region of the document. */
    private fun localBounds(layout: DocumentLayout, index: Int, region: Bounds): Bounds {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (k in 0 until 4) {
            layout.worldToPage(index, if (k % 2 == 0) region.left else region.right, if (k < 2) region.top else region.bottom, corner)
            left = minOf(left, corner[0])
            right = maxOf(right, corner[0])
            top = minOf(top, corner[1])
            bottom = maxOf(bottom, corner[1])
        }
        return Bounds(left, top, right, bottom)
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
        val cover = CoverDesign.byId(page.cover)
        if (cover != null && size != null) {
            template.canvas = canvas
            cover.draw(template, size, page.background.template)
            template.flush()
            return
        }
        val pattern = page.background.template
        if (!pattern.isTooDense(scale)) {
            template.canvas = canvas
            val mirrored = size != null && pattern.isMirrored(page)
            if (mirrored) {
                canvas.save()
                canvas.scale(-1f, 1f, size!!.widthPt / 2f, 0f)
            }
            pattern.draw(size, region, template, paper = page.background.color)
            template.flush()
            if (mirrored) canvas.restore()
        }
    }

    /** True for the left-hand pages of a book-style custom layout. Looks the page up only when the layout asks for it. */
    private fun PageTemplate.isMirrored(page: Page): Boolean {
        val layout = (this as? PageTemplate.Custom)?.layout ?: return false
        return layout.bookStyle && layout.isMirroredOn(source.pages.indexOfFirst { it.id == page.id })
    }

    /** Multiplying keeps the text under a highlight readable, as a marker on paper does. */
    private fun paintHighlights(canvas: Canvas, rects: List<PageHighlight>) {
        for (highlight in rects) {
            highlightPaint.color = if (highlight.isCurrent) CURRENT_HIGHLIGHT_COLOR else HIGHLIGHT_COLOR
            val r = highlight.rect
            canvas.drawRect(r.left, r.top, r.right, r.bottom, highlightPaint)
        }
    }

    /** Items are painted in their order on the page, which a layer's rank comes first in, and locked ones below the rest of their kind. */
    private fun paintItems(canvas: Canvas, content: PageContent, tileRegion: Bounds, scale: Float) {
        // Ink is picked by its box, and a pixel or two of the line can lie past it, so a tile looks a little beyond its own edge.
        val region = TileGrid.cullRegion(tileRegion, scale)
        strokeToTile.setScale(scale, scale)
        val passes: List<(PageItem) -> Boolean> = listOf({ it.locked }, { !it.locked })
        // Highlighter strokes that must not darken where they cross are drawn opaque into one layer per see-through level,
        // at the place in the stack of the first of them, so ink drawn in between does not split them into separate layers.
        val painted = HashSet<Int>()
        // A hidden layer is not drawn. Hiding it marks the whole page changed, so every tile that held something of it is redrawn.
        val hidden = content.layers.hiddenIds
        // Locked items are a layer below the rest: painted first, so that whatever is not locked lies over them whatever the order.
        for (pass in passes) {
        painted.clear()
        content.forEachPaintingIn(region) { _, item ->
            if (!pass(item) || item.layer in hidden) return@forEachPaintingIn
            val alpha = if (item is StrokeItem) InkBrushes.layerAlpha(item.style) else NO_LAYER
            when {
                item is StrokeItem && alpha == NO_LAYER -> ink.draw(canvas, source.mesh(item), strokeToTile)
                item is StrokeItem -> if (painted.add(alpha)) {
                    // A layer reaches past the tile on every side: sampled at its own border it would come out pale, and the tiles
                    // overlap there, so the pale line would show between them.
                    val reach = LAYER_REACH_PX / scale
                    layerBounds.set(region.left - reach, region.top - reach, region.right + reach, region.bottom + reach)
                    canvas.saveLayerAlpha(layerBounds, alpha)
                    content.forEachPaintingIn(region) { _, other ->
                        if (other is StrokeItem && pass(other) && other.layer !in hidden && InkBrushes.layerAlpha(other.style) == alpha) {
                            ink.draw(canvas, source.opaqueMesh(other), strokeToTile)
                        }
                    }
                    canvas.restore()
                }
                else -> items.paint(canvas, item)
            }
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
        const val NO_LAYER = -1
        const val LAYER_REACH_PX = 8f
        const val HIGHLIGHT_COLOR = CanvasColors.SEARCH_HIGHLIGHT
        const val CURRENT_HIGHLIGHT_COLOR = CanvasColors.SEARCH_HIGHLIGHT_CURRENT
    }
}

/** A rectangle to mark on a page, in the page's own points. */
internal class PageHighlight(val rect: Bounds, val isCurrent: Boolean)

/** Draws template lines directly, and batches dots, of which a dotted page has thousands. */
internal class CanvasTemplateSink : TemplateSink {
    lateinit var canvas: Canvas

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var dots = FloatArray(INITIAL_DOT_FLOATS)
    private var dotFloats = 0
    private val words = TemplateText()

    /** A notebook's own language, for pictures drawn off the editor; null follows the open notebook's or the app's. */
    var languageOverride: String? = null

    override val language: String get() = languageOverride ?: app.betternotes.ui.theme.TemplateText.code()

    override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) {
        flush()
        words.draw(canvas, text, x, baseline, size, color, style, align)
    }

    override fun turnedText(text: String, cx: Float, cy: Float, size: Float, degrees: Float, color: Int, style: TextStyle) =
        styledTurnedText(text, cx, cy, size, degrees, color, style, null)

    override fun styledText(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign, font: String?) {
        flush()
        words.draw(canvas, text, x, baseline, size, color, style, align, font)
    }

    override fun styledTurnedText(text: String, cx: Float, cy: Float, size: Float, degrees: Float, color: Int, style: TextStyle, font: String?) {
        flush()
        canvas.save()
        canvas.rotate(degrees, cx, cy)
        words.draw(canvas, text, cx, cy + size * CENTER_TO_BASELINE, size, color, style, TextAlign.CENTER, font)
        canvas.restore()
    }

    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) {
        flush()
        fillPaint.color = color
        canvas.drawRect(x, y, x + width, y + height, fillPaint)
    }

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

        /** How far below a line's middle its baseline is, as a share of the font size. */
        const val CENTER_TO_BASELINE = 0.36f
    }
}
