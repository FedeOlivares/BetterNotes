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
import app.betternotes.core.edit.ZPlace
import app.betternotes.core.edit.rearrange
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.withId
import app.betternotes.core.model.withLocked
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.tools.withTape
import app.betternotes.core.model.ShapeKind
import app.betternotes.stickers.canBeSticker
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.tools.TableHandle
import app.betternotes.core.tools.columnDividerX
import app.betternotes.core.tools.handles
import app.betternotes.core.tools.rowDividerY
import app.betternotes.core.tools.withColumnAdded
import app.betternotes.core.tools.withColumnRemoved
import app.betternotes.core.tools.withDividerAt
import app.betternotes.core.tools.withRowAdded
import app.betternotes.core.tools.withRowRemoved
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.TapePathItem
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
import kotlin.math.atan2
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
    /** Fills the matrix that takes a point of a page to the screen, which turns and scales it as the page is. */
    private val pageToScreenOf: (pageIndex: Int, into: Matrix) -> Unit,
    /** How far a page is turned, in degrees. */
    private val pageTurn: (pageIndex: Int) -> Float,
    private val redraw: () -> Unit,
) {
    private enum class Drag { NONE, FREEFORM, RECTANGLE, MOVE, RESIZE, PARAM, TABLE, ROTATE }

    private var editor: NoteEditor? = null
    private var drag = Drag.NONE
    private var pageIndex = -1
    private var kinds: Set<ItemKind> = emptySet()
    private var fullOnly = false
    private var anyPortion = false
    private var includeLocked = false

    private var selection: Selection? = null
        set(value) {
            field = value
            // Editing ends when something other than the table being edited is selected. A pause with nothing selected, which
            // an edit makes while it swaps the selection, does not end it.
            val single = value?.items?.singleOrNull()
            if (value != null && !(single is TableItem && single.id == tableEditingId)) tableEditingId = null
            onSelectionChanged(value)
        }

    /** The table whose editing handles show, opened by a double tap on it. */
    private var tableEditingId: Long? = null

    /** While a divider of the table is dragged: the table as it is now, and which divider it is. */
    private var tableDraft: TableItem? = null
    private var tableHandle: TableHandle? = null

    /** Selects [selected], a table, and shows its editing handles. */
    fun editTable(pageIndex: Int, selected: Selection) {
        select(pageIndex, selected)
        tableEditingId = (selected.items.singleOrNull() as? TableItem)?.id
        redraw()
    }

    val isEditingTable get() = tableEditingId != null

    /** Called when the "..." button next to delete and copy is pressed. */
    var onMenu: () -> Unit = {}

    /** Called with the items the copy button copied, which stay as they were. */
    var onCopied: (List<PageItem>) -> Unit = {}

    /** Called with the selected items when the sticker button is pressed. */
    var onSaveSticker: (List<PageItem>) -> Unit = {}

    /** Called with the new selection, or null, whenever it changes, so the toolbars can offer what suits it. */
    var onSelectionChanged: (Selection?) -> Unit = {}
    private var handle = ResizeHandle.BOTTOM_RIGHT
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var transform = Affine()

    /** While a shape's tip or stroke handle is dragged: the shape as it is now, and which handle it is. */
    private var paramItem: PageItem? = null
    private var param: ShapeParam? = null

    /** While a corner or control point of a recognized shape is dragged: which one. */
    private var vertex = -1
    private val polygon = ArrayList<Float>()

    private val itemPainter = ItemPainter({ editor?.imageOrLoad(it) }) { canvas, stroke ->
        editor?.let { it.ink.draw(canvas, it.strokes.get(stroke), strokeToScreen) }
    }
    /** The size of the view in pixels, which keeps the selection's buttons on the screen. Set by the view. */
    var viewWidthPx = 0
    var viewHeightPx = 0
    private val clampMatrix = Matrix()
    private val clampInverse = Matrix()

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
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
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
        if (drag == Drag.MOVE || drag == Drag.RESIZE || drag == Drag.PARAM || drag == Drag.ROTATE) cancel()
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

    fun start(
        pageIndex: Int,
        x: Float,
        y: Float,
        rectangle: Boolean,
        kinds: Set<ItemKind>,
        fullOnly: Boolean,
        anyPortion: Boolean = false,
        includeLocked: Boolean = false,
    ) {
        this.kinds = kinds
        this.fullOnly = fullOnly
        this.anyPortion = anyPortion
        this.includeLocked = includeLocked
        startX = x
        startY = y
        currentX = x
        currentY = y
        val current = selection
        val box = current?.bounds
        if (current != null && box != null && pageIndex == this.pageIndex && current.items.any { it.locked }) {
            // A locked selection stays where it is: a touch on it does nothing, and one elsewhere starts a new selection.
            if (box.contains(x, y) || handleAt(box, x, y) != null) {
                drag = Drag.NONE
                return
            }
        } else if (current != null && box != null && pageIndex == this.pageIndex) {
            val table = (current.items.singleOrNull() as? TableItem)?.takeIf { it.id == tableEditingId }
            val dividerHandle = table?.let { dividerAt(it, x, y) }
            if (table != null && dividerHandle != null) {
                tableHandle = dividerHandle
                tableDraft = table
                beginTransform(current, Drag.TABLE)
                return
            }
            val shape = current.items.singleOrNull() as? ShapeItem
            val tipHandle = shape?.let { paramHandleAt(it, x, y) }
            if (shape != null && tipHandle != null) {
                param = tipHandle
                paramItem = shape
                beginTransform(current, Drag.PARAM)
                return
            }
            val path = current.items.singleOrNull() as? PathShapeItem
            val corner = path?.let { vertexAt(it, x, y) } ?: -1
            if (path != null && corner >= 0) {
                vertex = corner
                param = null
                paramItem = path
                beginTransform(current, Drag.PARAM)
                return
            }
            if (onRotateKnob(box, x, y)) {
                beginTransform(current, Drag.ROTATE)
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
        tableEditingId = null
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
                val item = paramItem
                val which = param
                if (item is ShapeItem && which != null) {
                    val local = toLocal(item, x, y)
                    paramItem = ShapeTips.dragged(item, which, local[0], local[1])
                } else if (item is PathShapeItem && vertex >= 0) {
                    paramItem = item.copy(points = item.points.copyOf().also { it[vertex * 2] = x; it[vertex * 2 + 1] = y })
                }
            }
            Drag.TABLE -> {
                val table = selection?.items?.singleOrNull() as? TableItem
                val which = tableHandle
                if (table != null && which != null) tableDraft = table.withDividerAt(which, x, y)
            }
            Drag.MOVE -> transform = moveTransform(x - startX, y - startY)
            Drag.ROTATE -> transform = rotateTransform(x, y)
            Drag.RESIZE -> transform = selection?.bounds?.let { resizeTransform(it, handle, x, y, keepAspect = handle.isCorner && !isTextBox) }
                ?.let { if (isTextBox) Affine(a = it.a, e = it.e) else it } ?: transform
        }
        redraw()
    }

    /** The knob above the selection's top edge that turns it. */
    private fun knobCenter(box: Bounds) = floatArrayOf(box.centerX, box.top - ROTATE_KNOB_DP * density / viewport.pxPerPt)

    private fun onRotateKnob(box: Bounds, x: Float, y: Float): Boolean {
        val reach = HANDLE_REACH_DP * density / viewport.pxPerPt
        val knob = knobCenter(box)
        return abs(knob[0] - x) <= reach && abs(knob[1] - y) <= reach
    }

    /**
     * Turns the selection about its center by as much as the pen has swept round it. Near a multiple of 15 degrees it settles
     * on it, counted from the page for a single shape, so a tilted shape can be put back to exactly upright.
     */
    private fun rotateTransform(x: Float, y: Float): Affine {
        val box = selection?.bounds ?: return transform
        val cx = box.centerX
        val cy = box.centerY
        var degrees = Math.toDegrees((atan2(y - cy, x - cx) - atan2(startY - cy, startX - cx)).toDouble()).toFloat()
        val base = (selection?.items?.singleOrNull() as? ShapeItem)?.frame?.rotation ?: 0f
        val target = base + degrees
        val settled = Math.round(target / ROTATE_STEP_DEGREES) * ROTATE_STEP_DEGREES
        if (abs(target - settled) <= ROTATE_SETTLE_DEGREES) degrees = settled - base
        return Affine.rotateAbout(Math.toRadians(degrees.toDouble()), cx, cy)
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
            Drag.MOVE, Drag.RESIZE, Drag.ROTATE -> finishTransform()
            Drag.PARAM -> finishParam()
            Drag.TABLE -> finishTable()
        }
        drag = Drag.NONE
        redraw()
    }

    fun cancel() {
        releaseDragNode()
        val selected = selection
        val page = pageContent()
        if ((drag == Drag.MOVE || drag == Drag.RESIZE || drag == Drag.PARAM || drag == Drag.TABLE || drag == Drag.ROTATE) && selected != null && page != null) {
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
        val kinds = buttonKinds()
        val hit = kinds.withIndex().firstOrNull { (slot, _) ->
            val center = buttonCenter(box, slot)
            kotlin.math.hypot(x - center[0], y - center[1]) <= reach
        }?.value ?: return false
        when (hit) {
            ButtonKind.MORE -> if (menuOpen) return false else onMenu()
            ButtonKind.DELETE -> deleteSelection()
            ButtonKind.COPY -> duplicateSelection()
            ButtonKind.STICKER -> selection?.items?.let(onSaveSticker)
        }
        return true
    }

    /** Whether the options bar is open where the "..." button sits, which hides that button. */
    var menuOpen = false
        set(value) {
            field = value
            redraw()
        }

    /**
     * Where the "..." button sits, as the screen's own x and y of its right edge and its top, in the view's unturned
     * coordinates, or null when nothing is selected.
     */
    fun menuAnchor(): FloatArray? {
        val box = selection?.bounds ?: return null
        if (pageIndex < 0) return null
        val center = buttonCenter(box, buttonKinds().indexOf(ButtonKind.MORE))
        val reach = BUTTON_RADIUS_DP * density
        val at = Matrix()
        pageToScreenOf(pageIndex, at)
        at.mapPoints(center)
        return floatArrayOf(center[0] + reach, center[1] - reach)
    }

    private enum class ButtonKind { DELETE, COPY, STICKER, MORE }

    /** The buttons under the selection, from the corner: delete, copy, save as a sticker when the selection can be one, and "...". */
    private fun buttonKinds(): List<ButtonKind> {
        val canStick = selection?.items?.let(::canBeSticker) == true
        return listOfNotNull(ButtonKind.DELETE, ButtonKind.COPY, ButtonKind.STICKER.takeIf { canStick }, ButtonKind.MORE)
    }

    /** The button's center in page units: slot 0 is the one at the corner, slot 1 is next to it. */
    private fun buttonCenter(box: Bounds, slot: Int): FloatArray {
        val unit = density / viewport.pxPerPt
        val gap = BUTTON_RADIUS_DP * unit
        val step = (2 * BUTTON_RADIUS_DP + BUTTON_GAP_DP) * unit
        // The corner button's place: just under the selection's lower right corner.
        var x = box.right - gap
        var y = box.bottom + gap + BUTTON_GAP_DP * unit
        // A selection that runs to the page's edge, such as a cover's background, would put its buttons outside the page, where a
        // touch is not on any page. They stay inside it, over the selection's lower right corner.
        val size = editor?.document?.pages?.getOrNull(pageIndex)?.size
        if (size != null) {
            x = x.coerceAtMost(size.widthPt - gap)
            y = y.coerceAtMost(size.heightPt - gap)
        }
        // And they are always on the screen: when the corner is out of view they are drawn at the nearest edge of it.
        if (viewWidthPx > 0 && viewHeightPx > 0) {
            val corner = floatArrayOf(x, y)
            pageToScreenOf(pageIndex, clampMatrix)
            clampMatrix.mapPoints(corner)
            val radius = BUTTON_RADIUS_DP * density
            val margin = BUTTON_VIEW_MARGIN_DP * density
            val row = (buttonKinds().size - 1) * (2 * BUTTON_RADIUS_DP + BUTTON_GAP_DP) * density
            val minX = radius + margin + row
            val maxX = (viewWidthPx - radius - margin).coerceAtLeast(minX)
            val minY = radius + margin
            val maxY = (viewHeightPx - radius - margin).coerceAtLeast(minY)
            corner[0] = corner[0].coerceIn(minX, maxX)
            corner[1] = corner[1].coerceIn(minY, maxY)
            if (clampMatrix.invert(clampInverse)) {
                clampInverse.mapPoints(corner)
                x = corner[0]
                y = corner[1]
            }
        }
        return floatArrayOf(x - slot * step, y)
    }

    /** Puts a copy of the selection a little down and right of it, and selects the copy. */
    fun duplicateSelection() {
        val selected = selection ?: return
        val page = pageContent() ?: return
        val shift = Affine.translate(DUPLICATE_OFFSET_PT, DUPLICATE_OFFSET_PT)
        onCopied(selected.items)
        val copies = selected.items.map { it.transformed(shift).withId(IdSource.Random.next()).withLocked(false) }
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
        tableEditingId = null
        selection = null
        editor?.perform(selected.deleteEdit())
        redraw()
        onSelectionDropped()
    }

    /** Draws in view pixels, over everything on the page. */
    fun draw(canvas: Canvas) {
        if (pageIndex < 0) return
        pageToScreenOf(pageIndex, pageToScreen)
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
            Drag.MOVE, Drag.RESIZE, Drag.ROTATE -> {
                drawDraggedItems(canvas)
                val textBox = selection?.items?.singleOrNull() as? TextItem
                if (drag == Drag.RESIZE && textBox != null) drawBox(canvas, resizedText(textBox).bounds) else selection?.bounds?.let { drawBox(canvas, it.mapped(transform)) }
                drawGuides(canvas)
            }
            Drag.PARAM -> {
                drawParamItem(canvas)
                (paramItem as? ShapeItem)?.let { drawParamHandles(canvas, it) }
                (paramItem as? PathShapeItem)?.let { drawVertexHandles(canvas, it) }
            }
            Drag.TABLE -> tableDraft?.let { draft ->
                canvas.withMatrix(pageToScreen) { itemPainter.paint(this, draft) }
                drawBox(canvas, draft.bounds)
                drawTableHandles(canvas, draft)
            }
            Drag.NONE -> selection?.bounds?.let { box ->
                drawBox(canvas, box)
                drawHandles(canvas, box)
                (selection?.items?.singleOrNull() as? ShapeItem)?.let { drawParamHandles(canvas, it) }
                (selection?.items?.singleOrNull() as? PathShapeItem)?.let { drawVertexHandles(canvas, it) }
                (selection?.items?.singleOrNull() as? TableItem)?.takeIf { it.id == tableEditingId }?.let { drawTableHandles(canvas, it) }
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
        val found = selectWithLasso(pageId, page, points, kinds, fullOnly, anyPortion, includeLocked)
        selection = if (found.isEmpty) null else found
        // A touch outside the old selection that picks up nothing leaves no selection at all.
        if (found.isEmpty) onSelectionDropped()
    }

    private var adjustFrom: List<PageItem>? = null

    /** What the selection holds, or null if nothing is selected. */
    fun selectedItems(): List<PageItem>? = selection?.items

    /** Changes every selected item as one edit that can be undone, and keeps them selected. */
    fun changeSelected(change: (PageItem) -> PageItem) {
        val selected = selection ?: return
        val changed = selected.items.map(change)
        if (changed == selected.items) return
        val edit = ItemsEdit(selected.pageId, selected.indices, selected.items, selected.indices, changed)
        // Performing it fires the history callback, which would otherwise take this for someone else's edit.
        selection = null
        editor?.perform(edit)
        selection = Selection(selected.pageId, selected.indices, changed)
        redraw()
    }

    /**
     * Moves the selected items to the front, the middle or the back of the page's stack, as one edit that can be undone, and keeps
     * them selected. Post-its stay at the bottom, so they take no part, except on the cover (the document's first page, when it is one).
     */
    fun arrangeSelected(place: ZPlace) {
        val selected = selection ?: return
        val content = pageContent() ?: return
        val onCover = selectionOnCover()
        val movable = selected.indices.filterIndexed { i, _ -> onCover || selected.items[i] !is StickyNoteItem }
        if (movable.isEmpty()) return
        val moved = rearrange(selected.pageId, content, movable.toIntArray(), place) ?: return
        val items = movable.map { content[it] }
        selection = null
        editor?.perform(moved.edit)
        // Whatever of the selection did not move keeps its place only if it was below the moved items; the rest are found again by id.
        val ids = selected.items.mapTo(HashSet()) { it.id }
        val now = (0 until content.size).filter { content[it].id in ids }
        selection = Selection(selected.pageId, now.toIntArray(), now.map { content[it] }.ifEmpty { items })
        redraw()
    }

    /** True when the selection is one text box and ([x], [y]) on page [pageIndex] is on it or on one of its handles. */
    fun grabsText(pageIndex: Int, x: Float, y: Float): Boolean {
        val current = selection ?: return false
        val box = current.bounds ?: return false
        if (pageIndex != this.pageIndex || current.items.singleOrNull() !is TextItem || current.items[0].locked) return false
        return box.contains(x, y) || handleAt(box, x, y) != null
    }

    /** The index of the page the selection is on, or -1 when nothing is selected. */
    fun selectionPageIndex() = if (selection != null) pageIndex else -1

    /** True when the selection is on the cover page, where post-its may be raised above other items. */
    fun selectionOnCover(): Boolean {
        val selected = selection ?: return false
        val first = editor?.document?.pages?.firstOrNull() ?: return false
        return first.isCover && first.id == selected.pageId
    }

    /** Like [changeSelected], but straight on the page while a slider moves. [finishAdjust] then records one step. */
    fun adjustSelected(change: (PageItem) -> PageItem) {
        val selected = selection ?: return
        val page = pageContent() ?: return
        if (adjustFrom == null) adjustFrom = selected.items
        val changed = selected.items.map(change)
        page.splice(selected.indices, selected.indices, changed)
        selection = Selection(selected.pageId, selected.indices, changed)
        redraw()
    }

    /** The thickness and opacity of the first strip of tape in the selection, or null if it holds none. */
    fun selectedTape(): TapeLook? {
        for (item in selection?.items ?: return null) {
            if (item is ShapeItem && item.kind == ShapeKind.TAPE) return TapeLook(item.frame.height, item.opacity)
            if (item is TapePathItem) return TapeLook(item.width, item.opacity)
        }
        return null
    }

    /**
     * Changes the tape in the selection as a slider moves, straight on the page. [finishAdjust] then records
     * the whole change as one step, so a drag of the slider is one undo.
     */
    fun adjustTapes(thickness: Float?, opacity: Float?) {
        val selected = selection ?: return
        val page = pageContent() ?: return
        if (adjustFrom == null) adjustFrom = selected.items
        val changed = selected.items.map { item ->
            when {
                item is ShapeItem && item.kind == ShapeKind.TAPE -> item.withTape(thickness, opacity)
                item is TapePathItem -> item.withTape(thickness, opacity)
                else -> item
            }
        }
        page.splice(selected.indices, selected.indices, changed)
        selection = Selection(selected.pageId, selected.indices, changed)
        redraw()
    }

    fun finishAdjust() {
        val original = adjustFrom ?: return
        adjustFrom = null
        val selected = selection ?: return
        if (original == selected.items) return
        val edit = ItemsEdit(selected.pageId, selected.indices, original, selected.indices, selected.items)
        // Recording fires the history callback, which would otherwise take this for someone else's edit.
        selection = null
        editor?.record(edit)
        selection = selected
    }

    private fun finishParam() {
        val selected = selection ?: return
        val page = pageContent() ?: return
        val changed = paramItem
        paramItem = null
        param = null
        vertex = -1
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

    /** The corner or control point under (x, y), if any. */
    private fun vertexAt(item: PathShapeItem, x: Float, y: Float): Int {
        val reach = HANDLE_REACH_DP * density / viewport.pxPerPt
        for (k in 0 until item.pointCount) {
            if (abs(item.points[k * 2] - x) <= reach && abs(item.points[k * 2 + 1] - y) <= reach) return k
        }
        return -1
    }

    /** Round handles on a recognized shape's corners, or on the points a curve runs through. */
    private fun drawVertexHandles(canvas: Canvas, item: PathShapeItem) {
        val radius = PARAM_HANDLE_DP * density
        val point = FloatArray(2)
        for (k in 0 until item.pointCount) {
            point[0] = item.points[k * 2]
            point[1] = item.points[k * 2 + 1]
            pageToScreen.mapPoints(point)
            canvas.drawCircle(point[0], point[1], radius, paramFill)
            canvas.drawCircle(point[0], point[1], radius, paramEdge)
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

    private fun finishTable() {
        val selected = selection ?: return
        val page = pageContent() ?: return
        val changed = tableDraft
        tableDraft = null
        tableHandle = null
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

    /** The divider of [table] at a page point, or null. They sit on the table's top edge for columns and its left edge for rows. */
    private fun dividerAt(table: TableItem, x: Float, y: Float): TableHandle? {
        val reach = HANDLE_REACH_DP * density / viewport.pxPerPt
        return table.handles().firstOrNull { handle ->
            val at = dividerHandleCenter(table, handle)
            abs(at[0] - x) <= reach && abs(at[1] - y) <= reach
        }
    }

    private fun dividerHandleCenter(table: TableItem, handle: TableHandle): FloatArray {
        val out = DIVIDER_OUT_DP * density / viewport.pxPerPt
        return when (handle) {
            is TableHandle.ColumnDivider -> floatArrayOf(table.columnDividerX(handle.index), table.frame.top - out)
            is TableHandle.RowDivider -> floatArrayOf(table.frame.left - out, table.rowDividerY(handle.index))
        }
    }

    /** The four buttons that add or take away a column or a row, in the order: add column, remove column, add row, remove row. */
    private fun tableButtonCenter(table: TableItem, slot: Int): FloatArray {
        val unit = density / viewport.pxPerPt
        val step = (2 * BUTTON_RADIUS_DP + BUTTON_GAP_DP) * unit
        val edge = (BUTTON_RADIUS_DP + BUTTON_GAP_DP) * unit
        val f = table.frame
        return when (slot) {
            0 -> floatArrayOf(f.left + f.width + edge, f.top - edge)
            1 -> floatArrayOf(f.left + f.width + edge, f.top - edge + step)
            2 -> floatArrayOf(f.left - edge, f.top + f.height + edge)
            else -> floatArrayOf(f.left - edge + step, f.top + f.height + edge)
        }
    }

    /**
     * A press on one of a table's editing buttons, in page coordinates. Adds or takes away a column or a row as one edit that can be
     * undone, and returns true; false if the press was not on one.
     */
    fun pressTableButton(pageIndex: Int, x: Float, y: Float, reachDp: Float = BUTTON_RADIUS_DP): Boolean {
        val selected = selection ?: return false
        val table = (selected.items.singleOrNull() as? TableItem)?.takeIf { it.id == tableEditingId } ?: return false
        if (pageIndex != this.pageIndex) return false
        val reach = reachDp * density / viewport.pxPerPt
        val slot = (0..3).firstOrNull { kotlin.math.hypot(x - tableButtonCenter(table, it)[0], y - tableButtonCenter(table, it)[1]) <= reach } ?: return false
        changeSelected {
            when (slot) {
                0 -> table.withColumnAdded()
                1 -> table.withColumnRemoved()
                2 -> table.withRowAdded()
                else -> table.withRowRemoved()
            }
        }
        return true
    }

    private val tableGlyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = BUTTON_GLYPH_COLOR
    }

    /** Round orange handles on the table's edges for its dividers, and four small buttons to add or remove a column or a row. */
    private fun drawTableHandles(canvas: Canvas, table: TableItem) {
        val radius = PARAM_HANDLE_DP * density
        val point = FloatArray(2)
        for (handle in table.handles()) {
            val at = dividerHandleCenter(table, handle)
            point[0] = at[0]
            point[1] = at[1]
            pageToScreen.mapPoints(point)
            canvas.drawCircle(point[0], point[1], radius, paramFill)
            canvas.drawCircle(point[0], point[1], radius, paramEdge)
        }
        val buttonRadius = BUTTON_RADIUS_DP * density
        tableGlyph.strokeWidth = 2f * density
        for (slot in 0..3) {
            val at = tableButtonCenter(table, slot)
            point[0] = at[0]
            point[1] = at[1]
            pageToScreen.mapPoints(point)
            canvas.drawCircle(point[0], point[1], buttonRadius, paramFill)
            canvas.drawCircle(point[0], point[1], buttonRadius, paramEdge)
            val arm = buttonRadius * 0.5f
            canvas.drawLine(point[0] - arm, point[1], point[0] + arm, point[1], tableGlyph)
            if (slot % 2 == 0) canvas.drawLine(point[0], point[1] - arm, point[0], point[1] + arm, tableGlyph)
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
        val changed = if (drag == Drag.RESIZE && isTextBox) listOf(resizedText(selected.items.single() as TextItem)) else selected.items.map { it.transformed(transform) }
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
        if (pageTurn(pageIndex) != 0f) return false
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

    // Only read for a page that is not turned, where the page's corner is all the matrix adds.
    private val pageToScreenX get() = cornerOnScreen()[0]
    private val pageToScreenY get() = cornerOnScreen()[1]

    private fun cornerOnScreen(): FloatArray {
        val corner = floatArrayOf(0f, 0f)
        pageToScreen.mapPoints(corner)
        return corner
    }

    private fun recordDragNode(selected: Selection, scale: Float): Boolean {
        val editor = editor ?: return false
        val box = selected.items.map { it.paintBounds }.reduceOrNull(Bounds::union) ?: return false
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

    /** True when the selection is one text box, whose handles change the box and leave the text at its size. */
    private val isTextBox get() = selection?.items?.singleOrNull() is TextItem

    /** The box as the handles have made it: as wide as dragged, and as tall as the text wrapped to that width needs. */
    private fun resizedText(item: TextItem): TextItem = TextFit.fitted(item.copy(frame = item.frame.transformed(transform)))

    private fun drawDraggedItems(canvas: Canvas) {
        val selected = selection ?: return
        if (drag == Drag.RESIZE && isTextBox) {
            canvas.withMatrix(pageToScreen) { itemPainter.paint(this, resizedText(selected.items.single() as TextItem)) }
            return
        }
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
        for ((slot, kind) in buttonKinds().withIndex()) {
            if (kind == ButtonKind.MORE && menuOpen) continue
            val center = buttonCenter(box, slot)
            point[0] = center[0]
            point[1] = center[1]
            pageToScreen.mapPoints(point)
            buttonFill.color = when (kind) {
                ButtonKind.DELETE -> DELETE_COLOR
                ButtonKind.COPY -> COPY_COLOR
                ButtonKind.STICKER -> STICKER_COLOR
                ButtonKind.MORE -> MORE_COLOR
            }
            canvas.drawCircle(point[0], point[1], radius, buttonFill)
            when (kind) {
                ButtonKind.DELETE -> drawTrash(canvas, point[0], point[1], radius)
                ButtonKind.COPY -> drawCopy(canvas, point[0], point[1], radius)
                ButtonKind.STICKER -> drawSticker(canvas, point[0], point[1], radius)
                ButtonKind.MORE -> drawMore(canvas, point[0], point[1], radius)
            }
        }
    }

    /** A note with a folded corner and a smile. */
    private fun drawSticker(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val u = radius / 10f * GLYPH_SCALE
        canvas.drawRoundRect(cx - 6 * u, cy - 6 * u, cx + 6 * u, cy + 6 * u, 2 * u, 2 * u, glyph)
        canvas.drawCircle(cx - 2.2f * u, cy - 1.5f * u, 0.9f * u, dotFill)
        canvas.drawCircle(cx + 2.2f * u, cy - 1.5f * u, 0.9f * u, dotFill)
        canvas.drawArc(cx - 3 * u, cy - 1 * u, cx + 3 * u, cy + 4 * u, 20f, 140f, false, glyph)
    }

    private fun drawTrash(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val u = radius / 10f * GLYPH_SCALE
        canvas.drawLine(cx - 5 * u, cy - 4 * u, cx + 5 * u, cy - 4 * u, glyph)
        canvas.drawLine(cx - 1.5f * u, cy - 6 * u, cx + 1.5f * u, cy - 6 * u, glyph)
        canvas.drawRect(cx - 4 * u, cy - 4 * u, cx + 4 * u, cy + 6 * u, glyph)
        canvas.drawLine(cx - 1.5f * u, cy - 1.5f * u, cx - 1.5f * u, cy + 3.5f * u, glyph)
        canvas.drawLine(cx + 1.5f * u, cy - 1.5f * u, cx + 1.5f * u, cy + 3.5f * u, glyph)
    }

    private fun drawMore(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val u = radius / 10f
        for (k in -1..1) canvas.drawCircle(cx + k * 4.5f * u, cy, 1.4f * u, dotFill)
    }

    private fun drawCopy(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val u = radius / 10f * GLYPH_SCALE
        canvas.drawRoundRect(cx - 2 * u, cy - 6 * u, cx + 6 * u, cy + 3 * u, u, u, glyph)
        canvas.drawRoundRect(cx - 6 * u, cy - 2 * u, cx + 2 * u, cy + 7 * u, u, u, glyph)
    }

    private fun drawHandles(canvas: Canvas, box: Bounds) {
        drawButtons(canvas, box)
        // A round knob on a stem above the top edge turns the selection.
        val knob = knobCenter(box)
        val top = floatArrayOf(box.centerX, box.top, knob[0], knob[1])
        pageToScreen.mapPoints(top)
        canvas.drawLine(top[0], top[1], top[2], top[3], selectionEdge)
        canvas.drawCircle(top[2], top[3], PARAM_HANDLE_DP * density, handleFill)
        canvas.drawCircle(top[2], top[3], PARAM_HANDLE_DP * density, handleEdge)
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
        val xs = floatArrayOf(t.mapX(left, top), t.mapX(right, top), t.mapX(right, bottom), t.mapX(left, bottom))
        val ys = floatArrayOf(t.mapY(left, top), t.mapY(right, top), t.mapY(right, bottom), t.mapY(left, bottom))
        return Bounds(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private companion object {
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
        const val ROTATE_KNOB_DP = 28f
        const val ROTATE_STEP_DEGREES = 15f
        const val ROTATE_SETTLE_DEGREES = 3f
        const val DIVIDER_OUT_DP = 14f
        const val BUTTON_GLYPH_COLOR = 0xFFFFFFFF.toInt()
        const val PARAM_COLOR = 0xFFF28B30.toInt()
        const val BUTTON_RADIUS_DP = 18f

        /** How far in from the screen's edge the buttons keep when they have to be moved onto it. */
        const val BUTTON_VIEW_MARGIN_DP = 8f

        /** The symbols in the buttons are this share of their drawn size, so they do not crowd the circle's edge. */
        const val GLYPH_SCALE = 0.8f
        const val BUTTON_GAP_DP = 8f
        const val DELETE_COLOR = 0xFFE5483F.toInt()
        const val COPY_COLOR = 0xFF3F7FE5.toInt()
        const val MORE_COLOR = 0xFF5B6270.toInt()
        const val STICKER_COLOR = 0xFF8E63D9.toInt()
        const val DUPLICATE_OFFSET_PT = 18f

        /** Less than this is a tap on the selection, not a drag of it. */
        const val MIN_DRAG_PT = 0.5f
    }
}

/** How a strip of tape looks: its thickness in points and its opacity from 0 to 1. */
data class TapeLook(val thickness: Float, val opacity: Float)
