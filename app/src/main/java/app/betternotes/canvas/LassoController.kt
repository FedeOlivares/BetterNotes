package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withMatrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.ResizeHandle
import app.betternotes.core.tools.Selection
import app.betternotes.core.tools.resizeTransform
import app.betternotes.core.tools.selectWithLasso
import app.betternotes.core.tools.snapToAlign
import kotlin.math.abs

/**
 * Selecting items and moving or resizing them. While a selection is dragged its items leave the page
 * and are drawn over it, moved by a matrix, so a drag costs one composite however much is selected. They
 * go back to the page, as one edit, when the pen lifts. Coordinates are the page's own unless named
 * otherwise.
 */
internal class LassoController(
    private val viewport: Viewport,
    private val density: Float,
    private val pageOriginX: (pageIndex: Int) -> Float,
    private val pageOriginY: (pageIndex: Int) -> Float,
    private val redraw: () -> Unit,
) {
    private enum class Drag { NONE, FREEFORM, RECTANGLE, MOVE, RESIZE }

    private var editor: NoteEditor? = null
    private var drag = Drag.NONE
    private var pageIndex = -1
    private var kinds: Set<ItemKind> = emptySet()
    private var fullOnly = false

    private var selection: Selection? = null
    private var handle = ResizeHandle.BOTTOM_RIGHT
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var transform = Affine()
    private val polygon = ArrayList<Float>()

    private val renderer = CanvasStrokeRenderer.create()
    private val itemPainter = ItemPainter({ editor?.imageOrLoad(it) }) { canvas, stroke ->
        editor?.strokes?.get(stroke)?.let { renderer.draw(canvas, it, strokeToScreen) }
    }
    private val pageToScreen = Matrix()
    private val strokeToScreen = Matrix()
    private val itemMatrix = Matrix()
    private val outline = Path()
    private val marquee = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = MARQUEE_COLOR
        strokeWidth = MARQUEE_WIDTH_DP * density
        pathEffect = DashPathEffect(floatArrayOf(DASH_DP * density, DASH_DP * density), 0f)
    }
    private val selectionEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = MARQUEE_COLOR
        strokeWidth = SELECTION_WIDTH_DP * density
    }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GUIDE_COLOR
        strokeWidth = SELECTION_WIDTH_DP * density
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HANDLE_FILL }
    private val handleEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = MARQUEE_COLOR
        strokeWidth = HANDLE_EDGE_DP * density
    }

    val hasSelection get() = selection != null

    /** Whether something moved lines up with what is around it. */
    var snapEnabled = true

    private var snapTargets: List<Bounds> = emptyList()
    private var guideX: Float? = null
    private var guideY: Float? = null

    fun open(editor: NoteEditor?) {
        clear()
        this.editor = editor
    }

    /** Drops the selection, which holds positions that the next edit of the page may invalidate. */
    fun clear() {
        if (drag == Drag.MOVE || drag == Drag.RESIZE) cancel()
        selection = null
        drag = Drag.NONE
        polygon.clear()
        redraw()
    }

    fun start(pageIndex: Int, x: Float, y: Float, rectangle: Boolean, kinds: Set<ItemKind>, fullOnly: Boolean) {
        this.kinds = kinds
        this.fullOnly = fullOnly
        startX = x
        startY = y
        currentX = x
        currentY = y
        val current = selection
        val box = current?.bounds
        if (current != null && box != null && pageIndex == this.pageIndex) {
            val grabbed = handleAt(box, x, y)
            if (grabbed != null) {
                handle = grabbed
                beginTransform(current, Drag.RESIZE)
                return
            }
            if (box.contains(x, y)) {
                beginTransform(current, Drag.MOVE)
                return
            }
        }
        selection = null
        this.pageIndex = pageIndex
        drag = if (rectangle) Drag.RECTANGLE else Drag.FREEFORM
        polygon.clear()
        polygon += x
        polygon += y
        redraw()
    }

    fun move(x: Float, y: Float) {
        currentX = x
        currentY = y
        when (drag) {
            Drag.NONE -> return
            Drag.RECTANGLE -> Unit
            Drag.FREEFORM -> {
                polygon += x
                polygon += y
            }
            Drag.MOVE -> transform = moveTransform(x - startX, y - startY)
            Drag.RESIZE -> transform = selection?.bounds?.let { resizeTransform(it, handle, x, y, keepAspect = handle.isCorner) } ?: transform
        }
        redraw()
    }

    private fun moveTransform(dx: Float, dy: Float): Affine {
        guideX = null
        guideY = null
        val box = selection?.bounds
        if (!snapEnabled || box == null) return Affine.translate(dx, dy)
        val moved = Bounds(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
        val size = editor?.document?.pages?.getOrNull(pageIndex)?.size
        val snapped = snapToAlign(moved, snapTargets, size?.widthPt, size?.heightPt, SNAP_REACH_DP * density / viewport.pxPerPt)
        guideX = snapped.verticalLine
        guideY = snapped.horizontalLine
        return Affine.translate(dx + snapped.dx, dy + snapped.dy)
    }

    fun end() {
        guideX = null
        guideY = null
        when (drag) {
            Drag.NONE -> Unit
            Drag.RECTANGLE, Drag.FREEFORM -> finishMarquee()
            Drag.MOVE, Drag.RESIZE -> finishTransform()
        }
        drag = Drag.NONE
        redraw()
    }

    fun cancel() {
        val selected = selection
        val page = pageContent()
        if ((drag == Drag.MOVE || drag == Drag.RESIZE) && selected != null && page != null) {
            page.splice(IntArray(0), selected.indices, selected.items)
        }
        drag = Drag.NONE
        polygon.clear()
        redraw()
    }

    fun deleteSelection() {
        val selected = selection ?: return
        selection = null
        editor?.perform(selected.deleteEdit())
        redraw()
    }

    /** Draws in view pixels, over everything on the page. */
    fun draw(canvas: Canvas) {
        if (pageIndex < 0) return
        val scale = viewport.pxPerPt
        pageToScreen.setScale(scale, scale)
        pageToScreen.postTranslate(viewport.toScreenX(pageOriginX(pageIndex)), viewport.toScreenY(pageOriginY(pageIndex)))
        when (drag) {
            Drag.RECTANGLE, Drag.FREEFORM -> drawMarquee(canvas)
            Drag.MOVE, Drag.RESIZE -> {
                drawDraggedItems(canvas)
                selection?.bounds?.let { drawBox(canvas, it.mapped(transform)) }
                drawGuides(canvas)
            }
            Drag.NONE -> selection?.bounds?.let { box ->
                drawBox(canvas, box)
                drawHandles(canvas, box)
            }
        }
    }

    private fun beginTransform(current: Selection, gesture: Drag) {
        val page = pageContent() ?: return
        page.splice(current.indices, IntArray(0), emptyList())
        snapTargets = if (gesture == Drag.MOVE && snapEnabled) page.toList().map { it.bounds } else emptyList()
        transform = Affine()
        drag = gesture
        redraw()
    }

    private fun finishMarquee() {
        val page = pageContent()
        val points = if (drag == Drag.RECTANGLE) {
            floatArrayOf(startX, startY, currentX, startY, currentX, currentY, startX, currentY)
        } else {
            polygon.toFloatArray()
        }
        polygon.clear()
        val editor = editor
        if (page == null || editor == null) return
        val pageId = editor.document.pages[pageIndex].id
        val found = selectWithLasso(pageId, page, points, kinds, fullOnly)
        selection = if (found.isEmpty) null else found
    }

    private fun finishTransform() {
        val selected = selection ?: return
        val page = pageContent() ?: return
        val moved = abs(currentX - startX) + abs(currentY - startY) > MIN_DRAG_PT
        if (!moved) {
            page.splice(IntArray(0), selected.indices, selected.items)
            return
        }
        val changed = selected.items.map { it.transformed(transform) }
        page.splice(IntArray(0), selected.indices, changed)
        val edit = ItemsEdit(selected.pageId, selected.indices, selected.items, selected.indices, changed)
        val kept = selected.afterTransform(edit)
        // Recording fires the history callback, which would otherwise take this for someone else's edit.
        selection = null
        editor?.record(edit)
        selection = kept
    }

    private fun pageContent() = editor?.let { open ->
        open.document.pages.getOrNull(pageIndex)?.let { open.content(it.id) }
    }

    private fun drawMarquee(canvas: Canvas) {
        outline.reset()
        if (drag == Drag.RECTANGLE) {
            outline.addRect(
                minOf(startX, currentX),
                minOf(startY, currentY),
                maxOf(startX, currentX),
                maxOf(startY, currentY),
                Path.Direction.CW,
            )
        } else {
            for (i in polygon.indices step 2) {
                if (i == 0) outline.moveTo(polygon[i], polygon[i + 1]) else outline.lineTo(polygon[i], polygon[i + 1])
            }
        }
        outline.transform(pageToScreen)
        canvas.drawPath(outline, marquee)
    }

    private fun drawDraggedItems(canvas: Canvas) {
        val selected = selection ?: return
        val strokes = editor?.strokes ?: return
        itemMatrix.setValues(
            floatArrayOf(transform.a, transform.c, transform.e, transform.b, transform.d, transform.f, 0f, 0f, 1f),
        )
        strokeToScreen.set(itemMatrix)
        strokeToScreen.postConcat(pageToScreen)
        canvas.withMatrix(strokeToScreen) {
            for (item: PageItem in selected.items) {
                if (item is StrokeItem) {
                    renderer.draw(this, strokes.get(item), strokeToScreen)
                } else {
                    itemPainter.paint(this, item)
                }
            }
        }
    }

    private fun drawBox(canvas: Canvas, box: Bounds) {
        outline.reset()
        outline.addRect(box.left, box.top, box.right, box.bottom, Path.Direction.CW)
        outline.transform(pageToScreen)
        canvas.drawPath(outline, selectionEdge)
    }

    /** The lines that a moved selection has lined up with, across the whole view. */
    private fun drawGuides(canvas: Canvas) {
        val point = FloatArray(2)
        guideX?.let { x ->
            point[0] = x
            pageToScreen.mapPoints(point)
            canvas.drawLine(point[0], 0f, point[0], canvas.height.toFloat(), guide)
        }
        guideY?.let { y ->
            point[0] = 0f
            point[1] = y
            pageToScreen.mapPoints(point)
            canvas.drawLine(0f, point[1], canvas.width.toFloat(), point[1], guide)
        }
    }

    private fun drawHandles(canvas: Canvas, box: Bounds) {
        // Small squares on the corners and sides, as a word processor has.
        val half = HANDLE_HALF_DP * density
        val point = FloatArray(2)
        for (each in ResizeHandle.entries) {
            point[0] = box.handleX(each)
            point[1] = box.handleY(each)
            pageToScreen.mapPoints(point)
            canvas.drawRect(point[0] - half, point[1] - half, point[0] + half, point[1] + half, handleFill)
            canvas.drawRect(point[0] - half, point[1] - half, point[0] + half, point[1] + half, handleEdge)
        }
    }

    private fun handleAt(box: Bounds, x: Float, y: Float): ResizeHandle? {
        val reach = HANDLE_REACH_DP * density / viewport.pxPerPt
        return ResizeHandle.entries.firstOrNull { abs(box.handleX(it) - x) <= reach && abs(box.handleY(it) - y) <= reach }
    }

    private fun Bounds.handleX(handle: ResizeHandle) = centerX + handle.xSign * width / 2f

    private fun Bounds.handleY(handle: ResizeHandle) = centerY + handle.ySign * height / 2f

    private fun Bounds.mapped(t: Affine): Bounds {
        val x0 = t.mapX(left, top)
        val y0 = t.mapY(left, top)
        val x1 = t.mapX(right, bottom)
        val y1 = t.mapY(right, bottom)
        return Bounds(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
    }

    private companion object {
        const val MARQUEE_COLOR = 0xFF4A9BF5.toInt()
        const val HANDLE_FILL = 0xFFFFFFFF.toInt()
        const val MARQUEE_WIDTH_DP = 1.2f
        const val DASH_DP = 3f
        const val SELECTION_WIDTH_DP = 1f
        const val GUIDE_COLOR = 0xFFE5408F.toInt()
        const val SNAP_REACH_DP = 6f
        const val HANDLE_HALF_DP = 4f
        const val HANDLE_EDGE_DP = 1f
        const val HANDLE_REACH_DP = 18f

        /** Less than this is a tap on the selection, not a drag of it. */
        const val MIN_DRAG_PT = 0.5f
    }
}
