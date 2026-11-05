package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.os.SystemClock
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderNode
import androidx.core.graphics.withMatrix
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.withId
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.ResizeHandle
import app.betternotes.core.tools.ShapeParam
import app.betternotes.core.tools.ShapeTips
import app.betternotes.core.tools.Selection
import app.betternotes.core.tools.resizeTransform
import app.betternotes.core.tools.selectWithLasso
import app.betternotes.core.tools.snapToAlign
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    private enum class Drag { NONE, FREEFORM, RECTANGLE, MOVE, RESIZE, PARAM }

    private var editor: NoteEditor? = null
    private var drag = Drag.NONE
    private var pageIndex = -1
    private var kinds: Set<ItemKind> = emptySet()
    private var fullOnly = false
    private var anyPortion = false

    private var selection: Selection? = null
    private var handle = ResizeHandle.BOTTOM_RIGHT
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var transform = Affine()

    /** While a shape's tip or stroke handle is dragged: the shape as it is now, and which handle it is. */
    private var paramItem: ShapeItem? = null
    private var param: ShapeParam? = null
    private val polygon = ArrayList<Float>()

    private val itemPainter = ItemPainter({ editor?.imageOrLoad(it) }) { canvas, stroke ->
        editor?.let { it.ink.draw(canvas, it.strokes.get(stroke), strokeToScreen) }
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
    private val buttonFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val paramFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PARAM_COLOR }
    private val paramEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = HANDLE_FILL
        strokeWidth = 1.5f * density
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

    /** Called when a selection that existed is dropped, whether by a touch elsewhere or a change of tool. */
    var onSelectionDropped: () -> Unit = {}

    /** Drops the selection, which holds positions that the next edit of the page may invalidate. */
    fun clear() {
        if (drag == Drag.MOVE || drag == Drag.RESIZE || drag == Drag.PARAM) cancel()
        val hadSelection = selection != null
        selection = null
        ants = null
        antsSelection = null
        antsDone = null
        drag = Drag.NONE
        polygon.clear()
        redraw()
        if (hadSelection) onSelectionDropped()
    }

    private var ants: FloatArray? = null
    private var antsStartMs = 0L
    private var antsSelection: Selection? = null
    private var antsDone: (() -> Unit)? = null

    /**
     * Shows the circle that was drawn turning into a marching-ants outline, then selects [selected]
     * and calls [done]. [polygon] is the circle in page points.
     */
    fun selectAfterAnts(pageIndex: Int, polygon: FloatArray, selected: Selection, done: () -> Unit) {
        this.pageIndex = pageIndex
        ants = polygon
        antsStartMs = SystemClock.uptimeMillis()
        antsSelection = selected
        antsDone = done
        drag = Drag.NONE
        redraw()
    }

    private fun drawAnts(canvas: Canvas, polygon: FloatArray, elapsedMs: Long) {
        outline.reset()
        for (i in polygon.indices step 2) {
            if (i == 0) outline.moveTo(polygon[i], polygon[i + 1]) else outline.lineTo(polygon[i], polygon[i + 1])
        }
        outline.close()
        outline.transform(pageToScreen)
        // The line gives way to dashes that run along it.
        val dash = DASH_DP * density
        marquee.pathEffect = DashPathEffect(floatArrayOf(dash, dash), elapsedMs * ANT_SPEED_DP_PER_MS * density)
        marquee.alpha = (255 * (0.4f + 0.6f * (elapsedMs.toFloat() / ANTS_MS).coerceIn(0f, 1f))).toInt()
        canvas.drawPath(outline, marquee)
        marquee.alpha = 255
        marquee.pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
    }

    /** Shows [selected], found elsewhere, with its handles. */
    fun select(pageIndex: Int, selected: Selection) {
        this.pageIndex = pageIndex
        selection = selected
        drag = Drag.NONE
        redraw()
    }

    fun start(pageIndex: Int, x: Float, y: Float, rectangle: Boolean, kinds: Set<ItemKind>, fullOnly: Boolean, anyPortion: Boolean = false) {
        this.kinds = kinds
        this.fullOnly = fullOnly
        this.anyPortion = anyPortion
        startX = x
        startY = y
        currentX = x
        currentY = y
        val current = selection
        val box = current?.bounds
        if (current != null && box != null && pageIndex == this.pageIndex) {
            val shape = current.items.singleOrNull() as? ShapeItem
            val tipHandle = shape?.let { paramHandleAt(it, x, y) }
            if (shape != null && tipHandle != null) {
                param = tipHandle
                paramItem = shape
                beginTransform(current, Drag.PARAM)
                return
            }
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
            Drag.PARAM -> {
                val shape = paramItem
                val which = param
                if (shape != null && which != null) {
                    val local = toLocal(shape, x, y)
                    paramItem = ShapeTips.dragged(shape, which, local[0], local[1])
                }
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
        releaseDragNode()
        guideX = null
        guideY = null
        when (drag) {
            Drag.NONE -> Unit
            Drag.RECTANGLE, Drag.FREEFORM -> finishMarquee()
            Drag.MOVE, Drag.RESIZE -> finishTransform()
            Drag.PARAM -> finishParam()
        }
        drag = Drag.NONE
        redraw()
    }

    fun cancel() {
        releaseDragNode()
        val selected = selection
        val page = pageContent()
        if ((drag == Drag.MOVE || drag == Drag.RESIZE || drag == Drag.PARAM) && selected != null && page != null) {
            page.splice(IntArray(0), selected.indices, selected.items)
        }
        drag = Drag.NONE
        polygon.clear()
        redraw()
    }

    /**
     * A press on the selection's delete or copy button, in page coordinates. Returns true if it was one,
     * and the button's action is done.
     */
    fun pressButton(pageIndex: Int, x: Float, y: Float, reachDp: Float = BUTTON_RADIUS_DP): Boolean {
        val box = selection?.bounds ?: return false
        if (pageIndex != this.pageIndex) return false
        val reach = reachDp * density / viewport.pxPerPt
        val delete = buttonCenter(box, 0)
        val copy = buttonCenter(box, 1)
        when {
            kotlin.math.hypot(x - delete[0], y - delete[1]) <= reach -> deleteSelection()
            kotlin.math.hypot(x - copy[0], y - copy[1]) <= reach -> duplicateSelection()
            else -> return false
        }
        return true
    }

    /** The button's center in page units: slot 0 is the one at the corner, slot 1 is next to it. */
    private fun buttonCenter(box: Bounds, slot: Int): FloatArray {
        val unit = density / viewport.pxPerPt
        val gap = BUTTON_RADIUS_DP * unit
        val x = box.right - gap - slot * (2 * BUTTON_RADIUS_DP + BUTTON_GAP_DP) * unit
        val y = box.bottom + gap + BUTTON_GAP_DP * unit
        return floatArrayOf(x, y)
    }

    /** Puts a copy of the selection a little down and right of it, and selects the copy. */
    fun duplicateSelection() {
        val selected = selection ?: return
        val page = pageContent() ?: return
        val shift = Affine.translate(DUPLICATE_OFFSET_PT, DUPLICATE_OFFSET_PT)
        val copies = selected.items.map { it.transformed(shift).withId(IdSource.Random.next()) }
        val at = IntArray(copies.size) { page.size + it }
        val edit = ItemsEdit(selected.pageId, IntArray(0), emptyList(), at, copies)
        // Performing it fires the history callback, which would otherwise take this for someone else's edit.
        selection = null
        editor?.perform(edit)
        selection = Selection(selected.pageId, at, copies)
        redraw()
    }

    fun deleteSelection() {
        val selected = selection ?: return
        selection = null
        editor?.perform(selected.deleteEdit())
        redraw()
        onSelectionDropped()
    }

    /** Draws in view pixels, over everything on the page. */
    fun draw(canvas: Canvas) {
        if (pageIndex < 0) return
        val scale = viewport.pxPerPt
        pageToScreen.setScale(scale, scale)
        pageToScreen.postTranslate(viewport.toScreenX(pageOriginX(pageIndex)), viewport.toScreenY(pageOriginY(pageIndex)))
        val walking = ants
        if (walking != null) {
            val elapsed = SystemClock.uptimeMillis() - antsStartMs
            if (elapsed < ANTS_MS) {
                drawAnts(canvas, walking, elapsed)
                redraw()
                return
            }
            val chosen = antsSelection
            val done = antsDone
            ants = null
            antsSelection = null
            antsDone = null
            if (chosen != null) {
                selection = chosen
                done?.invoke()
            }
        }
        when (drag) {
            Drag.RECTANGLE, Drag.FREEFORM -> drawMarquee(canvas)
            Drag.MOVE, Drag.RESIZE -> {
                drawDraggedItems(canvas)
                selection?.bounds?.let { drawBox(canvas, it.mapped(transform)) }
                drawGuides(canvas)
            }
            Drag.PARAM -> {
                drawParamItem(canvas)
                paramItem?.let { drawParamHandles(canvas, it) }
            }
            Drag.NONE -> selection?.bounds?.let { box ->
                drawBox(canvas, box)
                drawHandles(canvas, box)
                (selection?.items?.singleOrNull() as? ShapeItem)?.let { drawParamHandles(canvas, it) }
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
        val found = selectWithLasso(pageId, page, points, kinds, fullOnly, anyPortion)
        selection = if (found.isEmpty) null else found
        // A touch outside the old selection that picks up nothing leaves no selection at all.
        if (found.isEmpty) onSelectionDropped()
    }

    private fun finishParam() {
        val selected = selection ?: return
        val page = pageContent() ?: return
        val changed = paramItem
        paramItem = null
        param = null
        if (changed == null || changed == selected.items.single()) {
            page.splice(IntArray(0), selected.indices, selected.items)
            return
        }
        page.splice(IntArray(0), selected.indices, listOf(changed))
        val edit = ItemsEdit(selected.pageId, selected.indices, selected.items, selected.indices, listOf(changed))
        val kept = selected.afterTransform(edit)
        selection = null
        editor?.record(edit)
        selection = kept
    }

    /** A page point in the shape's own frame: moved to its corner and turned back by its rotation. */
    private fun toLocal(shape: ShapeItem, x: Float, y: Float): FloatArray {
        val frame = shape.frame
        val radians = Math.toRadians(-frame.rotation.toDouble())
        val dx = x - frame.centerX
        val dy = y - frame.centerY
        val c = Math.cos(radians).toFloat()
        val s = Math.sin(radians).toFloat()
        return floatArrayOf(dx * c - dy * s + frame.width / 2f, dx * s + dy * c + frame.height / 2f)
    }

    /** The inverse of [toLocal]: a point in the shape's frame as a page point. */
    private fun toPage(shape: ShapeItem, x: Float, y: Float): FloatArray {
        val frame = shape.frame
        val radians = Math.toRadians(frame.rotation.toDouble())
        val dx = x - frame.width / 2f
        val dy = y - frame.height / 2f
        val c = Math.cos(radians).toFloat()
        val s = Math.sin(radians).toFloat()
        return floatArrayOf(dx * c - dy * s + frame.centerX, dx * s + dy * c + frame.centerY)
    }

    private fun paramHandleAt(shape: ShapeItem, x: Float, y: Float): ShapeParam? {
        val reach = HANDLE_REACH_DP * density / viewport.pxPerPt
        return ShapeTips.params(shape.kind).firstOrNull { which ->
            val at = ShapeTips.position(shape, which)
            val page = toPage(shape, at[0], at[1])
            abs(page[0] - x) <= reach && abs(page[1] - y) <= reach
        }
    }

    private fun drawParamItem(canvas: Canvas) {
        val shape = paramItem ?: return
        canvas.withMatrix(pageToScreen) { itemPainter.paint(this, shape) }
    }

    /** Round orange handles, unlike the white squares that resize the box. */
    private fun drawParamHandles(canvas: Canvas, shape: ShapeItem) {
        val radius = PARAM_HANDLE_DP * density
        val point = FloatArray(2)
        for (which in ShapeTips.params(shape.kind)) {
            val at = ShapeTips.position(shape, which)
            val page = toPage(shape, at[0], at[1])
            point[0] = page[0]
            point[1] = page[1]
            pageToScreen.mapPoints(point)
            canvas.drawCircle(point[0], point[1], radius, paramFill)
            canvas.drawCircle(point[0], point[1], radius, paramEdge)
        }
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

    /** The dragged items drawn once at the current zoom, so a move only has to slide this picture. */
    private var dragNode: RenderNode? = null
    private var dragNodeSelection: Selection? = null
    private var dragNodeScale = 0f
    private var dragNodeBox = Bounds(0f, 0f, 0f, 0f)

    private fun releaseDragNode() {
        dragNode?.discardDisplayList()
        dragNode = null
        dragNodeSelection = null
    }

    /**
     * Draws a move as a picture of the selection slid into place, rounded to whole pixels. Drawing every
     * stroke again each frame costs as much as the selection is large. Returns false when the selection
     * is too big to hold as a picture, and the caller draws it item by item.
     */
    private fun drawMoved(canvas: Canvas, selected: Selection): Boolean {
        if (!canvas.isHardwareAccelerated || transform.a != 1f || transform.d != 1f || transform.b != 0f || transform.c != 0f) return false
        val scale = viewport.pxPerPt
        if (dragNode == null || dragNodeSelection !== selected || dragNodeScale != scale) {
            if (!recordDragNode(selected, scale)) return false
        }
        val node = dragNode ?: return false
        val x = (dragNodeBox.left + transform.e) * scale + pageToScreenX
        val y = (dragNodeBox.top + transform.f) * scale + pageToScreenY
        canvas.save()
        canvas.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())
        canvas.drawRenderNode(node)
        canvas.restore()
        return true
    }

    private val pageToScreenX get() = viewport.toScreenX(pageOriginX(pageIndex))
    private val pageToScreenY get() = viewport.toScreenY(pageOriginY(pageIndex))

    private fun recordDragNode(selected: Selection, scale: Float): Boolean {
        val editor = editor ?: return false
        val bounds = selected.bounds ?: return false
        // Stamped brushes reach past a stroke's bounds, by up to a line width.
        val margin = selected.items.maxOf { (it as? StrokeItem)?.style?.width ?: 0f } * DRAG_MARGIN_OF_WIDTH + 2f
        val box = Bounds(bounds.left - margin, bounds.top - margin, bounds.right + margin, bounds.bottom + margin)
        val width = ceil(box.width * scale).toInt().coerceAtLeast(1)
        val height = ceil(box.height * scale).toInt().coerceAtLeast(1)
        if (width > MAX_DRAG_NODE_PX || height > MAX_DRAG_NODE_PX || width.toLong() * height > MAX_DRAG_NODE_AREA) return false
        releaseDragNode()
        val node = RenderNode("lasso-drag")
        node.setPosition(0, 0, width, height)
        node.setClipToBounds(false)
        node.setUseCompositingLayer(true, null)
        strokeToScreen.setScale(scale, scale)
        strokeToScreen.postTranslate(-box.left * scale, -box.top * scale)
        val recording = node.beginRecording(width, height)
        try {
            recording.withMatrix(strokeToScreen) {
                for (item: PageItem in selected.items) {
                    if (item is StrokeItem) editor.ink.draw(this, editor.strokes.get(item), strokeToScreen) else itemPainter.paint(this, item)
                }
            }
        } finally {
            node.endRecording()
        }
        dragNode = node
        dragNodeSelection = selected
        dragNodeScale = scale
        dragNodeBox = box
        return true
    }

    private fun drawDraggedItems(canvas: Canvas) {
        val selected = selection ?: return
        if (drag == Drag.MOVE && drawMoved(canvas, selected)) return
        val strokes = editor?.strokes ?: return
        itemMatrix.setValues(
            floatArrayOf(transform.a, transform.c, transform.e, transform.b, transform.d, transform.f, 0f, 0f, 1f),
        )
        strokeToScreen.set(itemMatrix)
        strokeToScreen.postConcat(pageToScreen)
        canvas.withMatrix(strokeToScreen) {
            for (item: PageItem in selected.items) {
                if (item is StrokeItem) {
                    editor?.ink?.draw(this, strokes.get(item), strokeToScreen)
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

    private fun drawButtons(canvas: Canvas, box: Bounds) {
        val radius = BUTTON_RADIUS_DP * density
        val point = FloatArray(2)
        for (slot in 0..1) {
            val center = buttonCenter(box, slot)
            point[0] = center[0]
            point[1] = center[1]
            pageToScreen.mapPoints(point)
            buttonFill.color = if (slot == 0) DELETE_COLOR else COPY_COLOR
            canvas.drawCircle(point[0], point[1], radius, buttonFill)
            if (slot == 0) drawTrash(canvas, point[0], point[1], radius) else drawCopy(canvas, point[0], point[1], radius)
        }
    }

    private fun drawTrash(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val u = radius / 10f
        canvas.drawLine(cx - 5 * u, cy - 4 * u, cx + 5 * u, cy - 4 * u, glyph)
        canvas.drawLine(cx - 1.5f * u, cy - 6 * u, cx + 1.5f * u, cy - 6 * u, glyph)
        canvas.drawRect(cx - 4 * u, cy - 4 * u, cx + 4 * u, cy + 6 * u, glyph)
        canvas.drawLine(cx - 1.5f * u, cy - 1.5f * u, cx - 1.5f * u, cy + 3.5f * u, glyph)
        canvas.drawLine(cx + 1.5f * u, cy - 1.5f * u, cx + 1.5f * u, cy + 3.5f * u, glyph)
    }

    private fun drawCopy(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val u = radius / 10f
        canvas.drawRoundRect(cx - 2 * u, cy - 6 * u, cx + 6 * u, cy + 3 * u, u, u, glyph)
        canvas.drawRoundRect(cx - 6 * u, cy - 2 * u, cx + 2 * u, cy + 7 * u, u, u, glyph)
    }

    private fun drawHandles(canvas: Canvas, box: Bounds) {
        drawButtons(canvas, box)
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
        /** A stamp can reach this many line widths past the stroke's bounds. */
        const val DRAG_MARGIN_OF_WIDTH = 2f
        const val MAX_DRAG_NODE_PX = 8192
        const val MAX_DRAG_NODE_AREA = 24_000_000L
        const val MARQUEE_COLOR = 0xFF4A9BF5.toInt()
        const val HANDLE_FILL = 0xFFFFFFFF.toInt()
        const val MARQUEE_WIDTH_DP = 1.2f
        const val DASH_DP = 3f
        const val ANTS_MS = 540L
        const val ANT_SPEED_DP_PER_MS = 0.032f
        const val SELECTION_WIDTH_DP = 1f
        const val GUIDE_COLOR = 0xFFE5408F.toInt()
        const val SNAP_REACH_DP = 6f
        const val HANDLE_HALF_DP = 4f
        const val HANDLE_EDGE_DP = 1f
        const val HANDLE_REACH_DP = 18f
        const val PARAM_HANDLE_DP = 6f
        const val PARAM_COLOR = 0xFFF28B30.toInt()
        const val BUTTON_RADIUS_DP = 18f
        const val BUTTON_GAP_DP = 8f
        const val DELETE_COLOR = 0xFFE5483F.toInt()
        const val COPY_COLOR = 0xFF3F7FE5.toInt()
        const val DUPLICATE_OFFSET_PT = 18f

        /** Less than this is a tap on the selection, not a drag of it. */
        const val MIN_DRAG_PT = 0.5f
    }
}
