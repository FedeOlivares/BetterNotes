package app.betternotes.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.LayoutElement
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.bounds
import app.betternotes.core.template.frameLines
import kotlin.math.abs
import kotlin.math.min

private const val FIT_FRACTION = 0.9f
private const val MIN_ZOOM = 0.4f
private const val MAX_ZOOM = 8f
private const val HANDLE_RADIUS_DP = 12
private const val HANDLE_GRAB_DP = 32

/** Where a horizontal frame line's handle sits across the page, so it stays clear of the vertical lines. */
private const val HORIZONTAL_HANDLE_X = 0.85f

/** Space between the two pages of a book-style spread, as a fraction of a page's width. */
private const val SPREAD_GAP = 0.04f

/** How faint the mirrored page is drawn: it is a look at the other side of the spread, not something to edit. */
private const val MIRROR_ALPHA = 0.55f

/** Where the page sits on screen: [scale] pixels to the point, with the editing page's top-left at ([x], [y]). */
private class PageView(val scale: Float, val x: Float, val y: Float, val fit: Float)

/**
 * The page of the layout on its canvas, drawn with the same template code as a note and moved the way a note is:
 * two fingers pinch to zoom and pan. One finger (or the stylus) picks and moves an element, or with [drawing] on,
 * drags out a new one. With book style on, the mirrored left-hand page shows beside it.
 */
@Composable
internal fun LayoutCanvas(
    layout: CustomLayout,
    selected: Int,
    drawing: Boolean,
    /** True when the tool in hand makes a line, so the drag and the selection show as a line, not a box. */
    drawsLine: Boolean,
    onTap: (Float, Float) -> Unit,
    onPress: (Float, Float) -> Unit,
    onDraw: (Float, Float, Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onFrameLine: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    val slop = LocalViewConfiguration.current.touchSlop
    var area by remember { mutableStateOf(IntSize.Zero) }
    var view by remember { mutableStateOf<PageView?>(null) }
    var start by remember { mutableStateOf(Offset.Zero) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val layoutNow by rememberUpdatedState(layout)
    val drawingNow by rememberUpdatedState(drawing)
    val drawsLineNow by rememberUpdatedState(drawsLine)
    val onTapNow by rememberUpdatedState(onTap)
    val onPressNow by rememberUpdatedState(onPress)
    val onDrawNow by rememberUpdatedState(onDraw)
    val onDragNow by rememberUpdatedState(onDrag)
    val onFrameLineNow by rememberUpdatedState(onFrameLine)
    val shadow = colors.screen

    // Fits the page again when its size or the spread changes, or the screen does.
    LaunchedEffect(area, layout.size, layout.bookStyle) {
        if (area.width == 0) return@LaunchedEffect
        val width = layout.size.widthPt * (if (layout.bookStyle) 2f + SPREAD_GAP else 1f)
        val scale = min(area.width / width, area.height / layout.size.heightPt) * FIT_FRACTION
        val left = (area.width - width * scale) / 2f + if (layout.bookStyle) layout.size.widthPt * (1f + SPREAD_GAP) * scale else 0f
        view = PageView(scale, left, (area.height - layout.size.heightPt * scale) / 2f, scale)
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val first = view ?: return@awaitEachGesture
                    fun toPage(position: Offset) = Offset((position.x - first.x) / first.scale, (position.y - first.y) / first.scale)
                    val begin = toPage(down.position)
                    // A grab on a frame line's handle moves that line and nothing else.
                    val grabbed = layoutNow.frameHandles().indexOfFirst { handle ->
                        (Offset(first.x + handle.x * first.scale, first.y + handle.y * first.scale) - down.position).getDistance() <= HANDLE_GRAB_DP.dp.toPx()
                    }
                    if (grabbed >= 0) {
                        val line = layoutNow.base.frameLines(layoutNow.size, layoutNow.frameLines)[grabbed]
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val at = toPage(change.position)
                            onFrameLineNow(grabbed, if (line.vertical) at.x / layoutNow.size.widthPt else at.y / layoutNow.size.heightPt)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    if (!drawingNow) onPressNow(begin.x, begin.y)
                    var multi = false
                    var moved = false
                    var last = down.position
                    var event = awaitPointerEvent()
                    while (true) {
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (!multi) end = null
                            multi = true
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()
                            view = view?.zoomedAbout(centroid, zoom, event.calculatePan())
                            event.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val change = pressed[0]
                            if (!moved && (change.position - down.position).getDistance() > slop) {
                                moved = true
                                if (drawingNow) start = down.position
                            }
                            if (moved) {
                                val factor = view?.scale ?: first.scale
                                if (drawingNow) {
                                    end = change.position
                                } else {
                                    val delta = change.position - last
                                    onDragNow(delta.x / factor, delta.y / factor)
                                }
                                change.consume()
                            }
                            last = change.position
                        }
                        event = awaitPointerEvent()
                    }
                    val now = view
                    if (!multi && now != null) {
                        val from = Offset((down.position.x - now.x) / now.scale, (down.position.y - now.y) / now.scale)
                        val to = end?.let { Offset((it.x - now.x) / now.scale, (it.y - now.y) / now.scale) }
                        when {
                            !moved -> onTapNow(from.x, from.y)
                            drawingNow && to != null -> onDrawNow(from.x, from.y, to.x, to.y)
                        }
                    }
                    end = null
                }
            },
    ) {
        if (area != IntSize(size.width.toInt(), size.height.toInt())) area = IntSize(size.width.toInt(), size.height.toInt())
        val now = view ?: return@Canvas
        val page = layout.size
        val width = page.widthPt * now.scale
        val height = page.heightPt * now.scale
        if (layout.bookStyle) {
            val gap = page.widthPt * (1f + SPREAD_GAP) * now.scale
            translate(now.x - gap, now.y) { paintPage(layout, width, height, now.scale, mirrored = true, shadow) }
        }
        translate(now.x, now.y) { paintPage(layout, width, height, now.scale, mirrored = false, shadow) }
        for (handle in layout.frameHandles()) {
            val center = Offset(now.x + handle.x * now.scale, now.y + handle.y * now.scale)
            drawCircle(Color.White, HANDLE_RADIUS_DP.dp.toPx() + 2.dp.toPx(), center)
            drawCircle(colors.accent, HANDLE_RADIUS_DP.dp.toPx(), center)
        }
        val index = selected
        if (index in layout.elements.indices) {
            val element = layout.elements[index]
            if (element is LayoutElement.Line) {
                drawLine(
                    colors.accent,
                    Offset(now.x + element.x0 * now.scale, now.y + element.y0 * now.scale),
                    Offset(now.x + element.x1 * now.scale, now.y + element.y1 * now.scale),
                    strokeWidth = maxOf(element.lineWidth * now.scale, 2.dp.toPx()),
                )
            } else {
                val box = element.bounds
                drawRect(
                    colors.accent,
                    Offset(now.x + box.left * now.scale, now.y + box.top * now.scale),
                    Size(box.width * now.scale, box.height * now.scale),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        end?.takeIf { drawingNow }?.let {
            if (drawsLineNow) {
                drawLine(colors.accent, start, it, strokeWidth = 2.dp.toPx())
            } else {
                drawRect(
                    colors.accent,
                    Offset(min(start.x, it.x), min(start.y, it.y)),
                    Size(abs(it.x - start.x), abs(it.y - start.y)),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }
}

private fun PageView.zoomedAbout(centroid: Offset, zoom: Float, pan: Offset): PageView {
    val next = (scale * zoom).coerceIn(fit * MIN_ZOOM, fit * MAX_ZOOM)
    val factor = next / scale
    return PageView(next, centroid.x - (centroid.x - x) * factor + pan.x, centroid.y - (centroid.y - y) * factor + pan.y, fit)
}

/** One page of paper with its template, at [factor] pixels to the point, shadowed as a note's page is. */
private fun DrawScope.paintPage(layout: CustomLayout, width: Float, height: Float, factor: Float, mirrored: Boolean, shadow: Color) {
    drawRect(shadow.copy(alpha = 0.35f), Offset(3.dp.toPx(), 3.dp.toPx()), Size(width, height))
    clipRect(0f, 0f, width, height) {
        if (mirrored) scale(-1f, 1f, Offset(width / 2f, 0f)) { paintPaper(layout, width, height, factor) } else paintPaper(layout, width, height, factor)
    }
    if (mirrored) drawRect(Color(layout.paper).copy(alpha = 1f - MIRROR_ALPHA), Offset.Zero, Size(width, height))
}

private fun DrawScope.paintPaper(layout: CustomLayout, width: Float, height: Float, factor: Float) {
    drawRect(Color(layout.paper), Offset.Zero, Size(width, height))
    scale(factor, factor, Offset.Zero) {
        TemplatePainter.paint(this, PageTemplate.Custom(layout), layout.size, minLine = 1f / factor)
    }
}

/** Where each movable line of the layout's frame has its handle, in points, in the order of [frameLines]. */
private fun CustomLayout.frameHandles(): List<Offset> = base.frameLines(size, frameLines).map { line ->
    if (line.vertical) Offset(line.position(size), size.heightPt / 2f) else Offset(size.widthPt * HORIZONTAL_HANDLE_X, line.position(size))
}
