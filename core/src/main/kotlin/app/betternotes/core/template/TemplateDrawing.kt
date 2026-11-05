package app.betternotes.core.template

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Receives a template as vector primitives, so the canvas and the PDF export draw the same thing.
 * Primitives may run past the requested region, and the sink is expected to clip them.
 */
interface TemplateSink {
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int)
    fun dot(x: Float, y: Float, radius: Float, color: Int)
}

private const val CORNELL_TITLE_FRACTION = 0.08f
private const val CORNELL_CUE_FRACTION = 0.3f
private const val CORNELL_SUMMARY_FRACTION = 0.2f
private const val LEGAL_HEAD_PT = 72f
private const val LEGAL_MARGIN_PT = 90f
private const val LEGAL_MARGIN_GAP_PT = 3f
private const val STAFF_SIDE_MARGIN_PT = 36f
private const val STAFF_TOP_MARGIN_PT = 36f
private const val MUSIC_STAFF_LINES = 5
private const val GUITAR_STAFF_LINES = 6
private const val ENGINEERING_MARGIN_PT = 18f
private const val STRUCTURE_SHADE = 55
private const val STICKY_EDGE_PT = 0.75f

private fun shade(argb: Int): Int {
    fun part(shift: Int) = ((argb shr shift and 0xFF) * 3 / 4) shl shift
    return (argb and 0xFF000000.toInt()) or part(16) or part(8) or part(0)
}

/**
 * Emits the part of this template inside [region].
 *
 * [page] is null on an infinite canvas, where only templates with [PageTemplate.tiles] draw
 * anything. Those align to the world origin, so the pattern stays put while the canvas scrolls.
 */
fun PageTemplate.draw(page: PageSize?, region: Bounds, sink: TemplateSink) {
    val area = if (page == null) region else clipToPage(region, page)
    if (area.width < 0f || area.height < 0f) return
    when (this) {
        PageTemplate.Blank -> Unit
        is PageTemplate.Lined -> horizontalLines(area, page, spacing, lineWidth, color, sink)
        is PageTemplate.Dotted -> dots(area, page, this, sink)
        is PageTemplate.Grid -> {
            horizontalLines(area, page, spacing, lineWidth, color, sink)
            verticalLines(area, page, spacing, lineWidth, color, sink)
        }
        is PageTemplate.Hexagonal -> hexagons(area, this, sink)
        is PageTemplate.Cornell -> page?.let { cornell(it, spacing, lineWidth, color, ruledCue = false, sink) }
        is PageTemplate.CornellRuled -> page?.let { cornell(it, spacing, lineWidth, color, ruledCue = true, sink) }
        is PageTemplate.Engineering -> page?.let { engineering(it, this, sink) }
        is PageTemplate.Legal -> page?.let { legal(it, this, sink) }
        is PageTemplate.MusicScore ->
            page?.let { staves(it, MUSIC_STAFF_LINES, lineGap, staffGap, lineWidth, color, sink) }
        is PageTemplate.GuitarTab ->
            page?.let { staves(it, GUITAR_STAFF_LINES, lineGap, staffGap, lineWidth, color, sink) }
        is PageTemplate.GuitarScore -> page?.let { guitarScore(it, this, sink) }
        is PageTemplate.Custom -> if (page != null) {
            layout.base.draw(page, region, sink)
            layout.elements.forEach { it.draw(sink) }
        }
    }
}

private fun clipToPage(region: Bounds, page: PageSize) = Bounds(
    max(region.left, 0f),
    max(region.top, 0f),
    min(region.right, page.widthPt),
    min(region.bottom, page.heightPt),
)

/** First grid index to draw. On a page, index 0 would sit on the edge, so it is skipped. */
private fun firstIndex(start: Float, spacing: Float, page: PageSize?): Int {
    val first = ceil(start / spacing).toInt()
    return if (page != null) max(first, 1) else first
}

/** One past the last grid index. On a page, a line exactly on the far edge is skipped too. */
private fun endIndex(end: Float, spacing: Float, pageEnd: Float?): Int {
    val last = floor(end / spacing).toInt()
    val onFarEdge = pageEnd != null && last * spacing >= pageEnd
    return if (onFarEdge) last else last + 1
}

private fun horizontalLines(area: Bounds, page: PageSize?, spacing: Float, width: Float, color: Int, sink: TemplateSink) {
    for (i in firstIndex(area.top, spacing, page) until endIndex(area.bottom, spacing, page?.heightPt)) {
        val y = i * spacing
        sink.line(area.left, y, area.right, y, width, color)
    }
}

private fun verticalLines(area: Bounds, page: PageSize?, spacing: Float, width: Float, color: Int, sink: TemplateSink) {
    for (i in firstIndex(area.left, spacing, page) until endIndex(area.right, spacing, page?.widthPt)) {
        val x = i * spacing
        sink.line(x, area.top, x, area.bottom, width, color)
    }
}

private fun dots(area: Bounds, page: PageSize?, t: PageTemplate.Dotted, sink: TemplateSink) {
    val radius = t.dotDiameter / 2f
    val rowEnd = endIndex(area.bottom, t.spacing, page?.heightPt)
    val columnEnd = endIndex(area.right, t.spacing, page?.widthPt)
    for (row in firstIndex(area.top, t.spacing, page) until rowEnd) {
        for (column in firstIndex(area.left, t.spacing, page) until columnEnd) {
            sink.dot(column * t.spacing, row * t.spacing, radius, t.color)
        }
    }
}

/**
 * Each hexagon draws its top, upper-right and lower-right edges. Its neighbours draw the other
 * three, so every edge in the tessellation is emitted exactly once.
 */
private fun hexagons(area: Bounds, t: PageTemplate.Hexagonal, sink: TemplateSink) {
    val side = t.side
    val halfHeight = side * sqrt(3f) / 2f
    val columnStep = side * 1.5f
    val rowStep = halfHeight * 2f
    val firstColumn = floor((area.left - side) / columnStep).toInt()
    val lastColumn = ceil((area.right + side) / columnStep).toInt()
    val firstRow = floor((area.top - halfHeight) / rowStep).toInt() - 1
    val lastRow = ceil((area.bottom + halfHeight) / rowStep).toInt()
    for (column in firstColumn..lastColumn) {
        val cx = column * columnStep
        val rowOffset = if (column.mod(2) == 1) halfHeight else 0f
        for (row in firstRow..lastRow) {
            val cy = row * rowStep + rowOffset
            val top = cy - halfHeight
            val bottom = cy + halfHeight
            sink.line(cx - side / 2f, top, cx + side / 2f, top, t.lineWidth, t.color)
            sink.line(cx + side / 2f, top, cx + side, cy, t.lineWidth, t.color)
            sink.line(cx + side, cy, cx + side / 2f, bottom, t.lineWidth, t.color)
        }
    }
}

private fun cornell(page: PageSize, spacing: Float, width: Float, color: Int, ruledCue: Boolean, sink: TemplateSink) {
    val titleBottom = page.heightPt * CORNELL_TITLE_FRACTION
    val summaryTop = page.heightPt * (1f - CORNELL_SUMMARY_FRACTION)
    val cueRight = page.widthPt * CORNELL_CUE_FRACTION
    val frame = darker(color)
    sink.line(0f, titleBottom, page.widthPt, titleBottom, width * 2f, frame)
    sink.line(0f, summaryTop, page.widthPt, summaryTop, width * 2f, frame)
    sink.line(cueRight, titleBottom, cueRight, summaryTop, width * 2f, frame)
    val ruleStart = if (ruledCue) 0f else cueRight
    var y = titleBottom + spacing
    while (y < summaryTop) {
        sink.line(ruleStart, y, page.widthPt, y, width, color)
        y += spacing
    }
}

/** The color for the lines that structure a layout, against the ruling inside it. */
private fun darker(color: Int): Int {
    val r = (color shr 16 and 0xFF) * STRUCTURE_SHADE / 100
    val g = (color shr 8 and 0xFF) * STRUCTURE_SHADE / 100
    val b = (color and 0xFF) * STRUCTURE_SHADE / 100
    return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
}

private fun engineering(page: PageSize, t: PageTemplate.Engineering, sink: TemplateSink) {
    val left = ENGINEERING_MARGIN_PT
    val top = ENGINEERING_MARGIN_PT
    val columns = ((page.widthPt - left * 2f) / t.spacing).toInt()
    val rows = ((page.heightPt - top * 2f) / t.spacing).toInt()
    val right = left + columns * t.spacing
    val bottom = top + rows * t.spacing
    for (column in 0..columns) {
        val major = column % PageTemplate.Engineering.MAJOR_EVERY == 0 || column == columns
        val x = left + column * t.spacing
        sink.line(x, top, x, bottom, t.lineWidth, if (major) darker(t.color) else t.color)
    }
    for (row in 0..rows) {
        val major = row % PageTemplate.Engineering.MAJOR_EVERY == 0 || row == rows
        val y = top + row * t.spacing
        sink.line(left, y, right, y, t.lineWidth, if (major) darker(t.color) else t.color)
    }
}

private fun guitarScore(page: PageSize, t: PageTemplate.GuitarScore, sink: TemplateSink) {
    val left = STAFF_SIDE_MARGIN_PT
    val right = page.widthPt - STAFF_SIDE_MARGIN_PT
    val musicHeight = t.lineGap * (MUSIC_STAFF_LINES - 1)
    val tabHeight = t.lineGap * (GUITAR_STAFF_LINES - 1)
    val inner = t.lineGap * 2f
    var top = STAFF_TOP_MARGIN_PT
    while (top + musicHeight + inner + tabHeight <= page.heightPt - STAFF_TOP_MARGIN_PT) {
        for (line in 0 until MUSIC_STAFF_LINES) {
            sink.line(left, top + line * t.lineGap, right, top + line * t.lineGap, t.lineWidth, t.color)
        }
        val tabTop = top + musicHeight + inner
        for (line in 0 until GUITAR_STAFF_LINES) {
            sink.line(left, tabTop + line * t.lineGap, right, tabTop + line * t.lineGap, t.lineWidth, t.color)
        }
        sink.line(left, top, left, tabTop + tabHeight, t.lineWidth, t.color)
        top = tabTop + tabHeight + t.staffGap
    }
}

private fun legal(page: PageSize, t: PageTemplate.Legal, sink: TemplateSink) {
    var y = LEGAL_HEAD_PT
    while (y < page.heightPt) {
        sink.line(0f, y, page.widthPt, y, t.lineWidth, t.color)
        y += t.spacing
    }
    for (x in floatArrayOf(LEGAL_MARGIN_PT, LEGAL_MARGIN_PT + LEGAL_MARGIN_GAP_PT)) {
        sink.line(x, 0f, x, page.heightPt, t.lineWidth, t.marginColor)
    }
}

private fun staves(
    page: PageSize,
    lineCount: Int,
    lineGap: Float,
    staffGap: Float,
    width: Float,
    color: Int,
    sink: TemplateSink,
) {
    val staffHeight = lineGap * (lineCount - 1)
    val left = STAFF_SIDE_MARGIN_PT
    val right = page.widthPt - STAFF_SIDE_MARGIN_PT
    var top = STAFF_TOP_MARGIN_PT
    while (top + staffHeight <= page.heightPt - STAFF_TOP_MARGIN_PT) {
        for (line in 0 until lineCount) {
            val y = top + line * lineGap
            sink.line(left, y, right, y, width, color)
        }
        top += staffHeight + staffGap
    }
}

private fun LayoutElement.draw(sink: TemplateSink) {
    when (this) {
        is LayoutElement.Line -> sink.line(x0, y0, x1, y1, lineWidth, color)
        is LayoutElement.Rectangle -> rectangle(x, y, width, height, lineWidth, color, sink)
        is LayoutElement.Table -> {
            rectangle(x, y, width, height, lineWidth, color, sink)
            for (row in 1 until rows) {
                val lineY = y + height * row / rows
                sink.line(x, lineY, x + width, lineY, lineWidth, color)
            }
            for (column in 1 until columns) {
                val lineX = x + width * column / columns
                sink.line(lineX, y, lineX, y + height, lineWidth, color)
            }
        }
        is LayoutElement.Ruled -> {
            var lineY = y + spacing
            while (lineY < y + height) {
                sink.line(x, lineY, x + width, lineY, lineWidth, color)
                lineY += spacing
            }
        }
        is LayoutElement.Sticky -> {
            // The sink has no fill, so a line as thick as the note, with butt ends, is the fill.
            sink.line(x, y + height / 2, x + width, y + height / 2, height, color)
            rectangle(x, y, width, height, STICKY_EDGE_PT, shade(color), sink)
        }
    }
}

private fun rectangle(x: Float, y: Float, w: Float, h: Float, width: Float, color: Int, sink: TemplateSink) {
    sink.line(x, y, x + w, y, width, color)
    sink.line(x + w, y, x + w, y + h, width, color)
    sink.line(x + w, y + h, x, y + h, width, color)
    sink.line(x, y + h, x, y, width, color)
}
