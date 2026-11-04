package app.betternotes.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.input.motionprediction.MotionEventPredictor
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.gesture.GestureListener
import app.betternotes.core.gesture.GestureRecognizer
import app.betternotes.core.gesture.HoldDetector
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.gesture.PointerKind
import app.betternotes.core.layout.DocumentLayout
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.tools.EraseMode
import app.betternotes.core.tools.EraseSession
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the stylus does: lay down ink, or erase. */
sealed interface CanvasTool {
    /** Holding the pen still for [holdToLineMs] turns the stroke into a straight line. 0 turns that off. */
    data class Ink(val style: InkStyle, val holdToLineMs: Int = 0) : CanvasTool
    data class Eraser(val mode: EraseMode, val radiusPt: Float) : CanvasTool
}

/** Reports what happens in the view, for the performance probe. */
internal interface CanvasObserver {
    fun onTouch(event: MotionEvent)
    fun onTileRecorded(nanos: Long)
    fun onWetInkReady(view: InProgressStrokesView)
}

/**
 * The drawing surface. Finished content sits in a [TileLayerView], and the stroke being drawn goes
 * through an [InProgressStrokesView], which paints to the front buffer and so appears ahead of the
 * next frame. Touches go straight to a [GestureRecognizer], which decides what they mean.
 */
class CanvasView(context: Context) : FrameLayout(context), GestureListener, InProgressStrokesFinishedListener {
    val viewport = Viewport(physicalDpi(context))

    /** Called whenever the zoom changes. */
    var onZoomChanged: (zoom: Float) -> Unit = {}

    /** Called whenever [canUndo] or [canRedo] may have changed. */
    var onHistoryChanged: () -> Unit = {}

    val canUndo get() = editor?.canUndo == true
    val canRedo get() = editor?.canRedo == true

    /** The color around the pages. */
    var backdropColor = 0xFF0E1013.toInt()
        set(value) {
            field = value
            setBackgroundColor(value)
            tiles.backdropColor = value
            tiles.invalidateAll()
        }

    var tool: CanvasTool = CanvasTool.Ink(InkStyle())

    var inputMode: InputMode
        get() = recognizer.mode
        set(value) {
            recognizer.mode = value
            stopFling()
        }

    var scrollDirection = ScrollDirection.VERTICAL
        set(value) {
            field = value
            rebuildLayout()
        }

    internal var observer: CanvasObserver? = null
        set(value) {
            field = value
            value?.onWetInkReady(wet)
            tiles.onTileRecorded = value?.let { it::onTileRecorded }
        }

    private var editor: NoteEditor? = null
    private var painter: PagePainter? = null
    private var layout: DocumentLayout? = null
    private var layoutVersion = -1L
    private var hasPlacedPage = false

    private val tiles = TileLayerView(context, viewport, ::paintTile, backdropColor)
    private val wet = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val recognizer = GestureRecognizer(
        GestureConfig(touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()),
        this,
    )
    private val deadlineCheck = Runnable {
        recognizer.onDeadline(SystemClock.uptimeMillis())
        scheduleDeadline()
    }

    // Navigation.
    private val pageMarginPx = PAGE_MARGIN_DP * resources.displayMetrics.density
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private var pinchedThisGesture = false
    private var liftedPointerId = -1
    private val flingStep = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) {
                settlePan()
                return
            }
            viewport.panX = scroller.currX.toFloat()
            viewport.panY = scroller.currY.toFloat()
            tiles.invalidate()
            postOnAnimation(this)
        }
    }

    // The stroke or eraser drag in progress.
    private var currentEvent: MotionEvent? = null
    private var strokeId: InProgressStrokeId? = null
    private var strokeWaitingForEvent = false
    private var strokeStartedInThisEvent = false
    private var erase: EraseSession? = null
    private var drawPointerId = -1
    private var drawPageIndex = -1
    private var drawStyle = InkStyle()
    private val screenToPage = Matrix()
    private val pendingStrokes = HashMap<InProgressStrokeId, PendingStroke>()
    private var overlayBounds: List<RectF> = emptyList()
    private var maskedPage: RectF? = null
    private var maskIsCurrent = false

    // A stroke the pen has straightened. Its far end is ink's prediction, so that it can keep moving.
    private val hold = HoldDetector(HOLD_SLOP_DP * resources.displayMetrics.density)
    private var holdDelayMs = 0L
    private var straightening = false
    private var strokeStartMs = 0L
    private var strokeStartX = 0f
    private var strokeStartY = 0f
    private val pageToScreen = Matrix()
    private val holdCheck = Runnable { checkHold() }

    private class PendingStroke(val editor: NoteEditor, val pageId: Long, val style: InkStyle)

    init {
        setBackgroundColor(backdropColor)
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        wet.addFinishedStrokesListener(this)
    }

    fun open(editor: NoteEditor) {
        close()
        this.editor = editor
        painter = PagePainter(editor)
        editor.onPageLoaded = ::redrawPage
        editor.onHistoryChanged = { onHistoryChanged() }
        onHistoryChanged()
        hasPlacedPage = false
        rebuildLayout()
    }

    /**
     * Finishes with the open document. A stroke on its way out of the wet layer still lands on the
     * page it was drawn on, because it remembers its editor.
     */
    fun close() {
        editor?.onPageLoaded = {}
        editor?.onHistoryChanged = {}
        editor = null
        onHistoryChanged()
    }

    /** Sets the zoom about the center of the view. Zoom 1 is real size. */
    fun zoomTo(zoom: Float) {
        viewport.zoomTo(zoom, width / 2f, height / 2f)
        clampPan()
        settlePan()
        onZoomChanged(viewport.zoom)
    }

    /**
     * Where other views sit over this one, in its coordinates. Wet ink is drawn on top of everything in
     * the window, so it has to be told to stay out from under them.
     */
    fun setOverlayBounds(bounds: List<RectF>) {
        overlayBounds = bounds
        maskIsCurrent = false
    }

    fun undo() {
        val done = editor?.undo() ?: return
        if (done) syncChanges()
    }

    fun redo() {
        val done = editor?.redo() ?: return
        if (done) syncChanges()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Sets up the front buffer now, rather than making the first stroke wait for it.
        wet.eagerInit()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(deadlineCheck)
        removeCallbacks(holdCheck)
        stopFling()
        recognizer.reset()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maskIsCurrent = false
        placeFirstPage()
        clampPan()
        settlePan()
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    // Drawing has no click to perform: a tap only ever means something to the recognizer.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        observer?.onTouch(event)
        currentEvent = event
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            stopFling()
            pinchedThisGesture = false
            velocityTracker?.clear()
        }
        predictor.record(event)
        trackVelocity(event)
        if (strokeWaitingForEvent) startWaitingStroke(event)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> pointerDown(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> pointersMoved(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pointerUp(event, event.actionIndex)
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) recognizer.pointerCancel(event.getPointerId(i), event.eventTime)
            }
        }
        if (action == MotionEvent.ACTION_MOVE) addToStroke(event)
        strokeStartedInThisEvent = false
        currentEvent = null
        scheduleDeadline()
        return true
    }

    private fun pointerDown(event: MotionEvent, index: Int) {
        val kind = kindOf(event.getToolType(index))
        val draws = recognizer.mode != InputMode.VIEW_ONLY &&
            (kind != PointerKind.FINGER || recognizer.mode == InputMode.FINGER_DRAW)
        // Without this, Android holds moves back and delivers them once a frame.
        if (draws) requestUnbufferedDispatch(event)
        recognizer.pointerDown(event.getPointerId(index), kind, event.getX(index), event.getY(index), event.eventTime)
    }

    private fun pointersMoved(event: MotionEvent) {
        for (h in 0 until event.historySize) {
            val timeMs = event.getHistoricalEventTime(h)
            for (i in 0 until event.pointerCount) {
                recognizer.pointerMove(event.getPointerId(i), event.getHistoricalX(i, h), event.getHistoricalY(i, h), timeMs)
            }
        }
        for (i in 0 until event.pointerCount) {
            recognizer.pointerMove(event.getPointerId(i), event.getX(i), event.getY(i), event.eventTime)
        }
    }

    private fun pointerUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
            recognizer.pointerCancel(id, event.eventTime)
            return
        }
        liftedPointerId = id
        recognizer.pointerUp(id, event.eventTime)
    }

    private fun kindOf(toolType: Int) = when (toolType) {
        MotionEvent.TOOL_TYPE_ERASER -> PointerKind.STYLUS_ERASER
        // A mouse draws like a stylus, so the app can be tried with one, as on an emulator.
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_MOUSE -> PointerKind.STYLUS
        else -> PointerKind.FINGER
    }

    private fun scheduleDeadline() {
        removeCallbacks(deadlineCheck)
        val deadline = recognizer.nextDeadlineMs
        if (deadline != GestureRecognizer.NO_DEADLINE) {
            postDelayed(deadlineCheck, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        }
    }

    // ---- Drawing ----

    override fun onDrawStart(pointerId: Int, kind: PointerKind, x: Float, y: Float) {
        val editor = editor ?: return
        val layout = layout ?: return
        val index = layout.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y))
        if (index < 0) return
        val page = editor.document.pages[index]
        drawPointerId = pointerId
        drawPageIndex = index

        val tool = tool
        if (kind == PointerKind.STYLUS_ERASER || tool is CanvasTool.Eraser) {
            val eraser = tool as? CanvasTool.Eraser ?: DEFAULT_ERASER
            erase = EraseSession(page.id, editor.content(page.id), eraser.mode, eraser.radiusPt)
            eraseAt(x, y)
        } else if (tool is CanvasTool.Ink) {
            drawStyle = tool.style
            holdDelayMs = tool.holdToLineMs.toLong()
            // A stroke that starts when a tap times out has no event yet, and ink needs one.
            val event = currentEvent
            if (event != null) startStroke(event) else strokeWaitingForEvent = true
        }
    }

    override fun onDrawMove(pointerId: Int, x: Float, y: Float) {
        if (erase != null) eraseAt(x, y)
    }

    override fun onDrawEnd(pointerId: Int) {
        strokeWaitingForEvent = false
        val session = erase
        if (session != null) {
            session.finish()?.let { editor?.record(it) }
            erase = null
            return
        }
        val id = strokeId ?: return
        val event = currentEvent ?: return
        removeCallbacks(holdCheck)
        if (straightening) {
            finishStraightStroke(event, id)
        } else {
            wet.finishStroke(event, pointerId, id)
        }
        strokeId = null
    }

    override fun onDrawCancel(pointerId: Int) {
        strokeWaitingForEvent = false
        val session = erase
        if (session != null) {
            session.finish()?.let { editor?.discard(it) }
            erase = null
            redrawChanges(drawPageIndex)
            return
        }
        val id = strokeId ?: return
        removeCallbacks(holdCheck)
        straightening = false
        wet.cancelStroke(id, currentEvent)
        pendingStrokes.remove(id)
        strokeId = null
    }

    private fun startWaitingStroke(event: MotionEvent) {
        strokeWaitingForEvent = false
        if (event.findPointerIndex(drawPointerId) >= 0) startStroke(event)
    }

    private fun startStroke(event: MotionEvent) {
        val editor = editor ?: return
        val layout = layout ?: return
        val page = editor.document.pages[drawPageIndex]
        val scale = viewport.pxPerPt
        // Stroke coordinates are the page's own, so a page can move without touching its ink.
        screenToPage.setTranslate(-viewport.panX, -viewport.panY)
        screenToPage.postScale(1f / scale, 1f / scale)
        screenToPage.postTranslate(-layout.originX(drawPageIndex), -layout.originY(drawPageIndex))
        updateWetMask(layout, drawPageIndex)
        val id = wet.startStroke(event, drawPointerId, InkBrushes.brush(drawStyle), screenToPage)
        pendingStrokes[id] = PendingStroke(editor, page.id, drawStyle)
        strokeId = id
        strokeStartedInThisEvent = true
        if (holdDelayMs > 0L) watchForHold(event)
    }

    /** Passes a whole motion event to ink, along with a guess at what comes next. */
    private fun addToStroke(event: MotionEvent) {
        val id = strokeId ?: return
        // A stroke that started partway through this event already has its latest sample.
        if (strokeStartedInThisEvent) return
        if (holdDelayMs > 0L) {
            val index = event.findPointerIndex(drawPointerId)
            if (index >= 0) hold.move(event.getX(index), event.getY(index), event.eventTime)
        }
        if (straightening) {
            moveStraightEnd(id, event.eventTime)
            return
        }
        val predicted = predictor.predict()
        wet.addToStroke(event, drawPointerId, id, predicted)
        predicted?.recycle()
    }

    // ---- Holding the pen still to draw a straight line ----

    private fun watchForHold(event: MotionEvent) {
        val index = event.findPointerIndex(drawPointerId)
        if (index < 0) return
        val x = event.getX(index)
        val y = event.getY(index)
        hold.start(x, y, event.eventTime)
        strokeStartMs = event.eventTime
        val start = floatArrayOf(x, y)
        screenToPage.mapPoints(start)
        strokeStartX = start[0]
        strokeStartY = start[1]
        postDelayed(holdCheck, holdDelayMs)
    }

    /** Runs only once per delay, rather than rescheduling on each of the pen's 240 samples a second. */
    private fun checkHold() {
        if (strokeId == null || straightening) return
        val now = SystemClock.uptimeMillis()
        if (hold.isHeld(now, holdDelayMs)) {
            straighten()
        } else {
            postDelayed(holdCheck, (hold.heldAtMs(holdDelayMs) - now).coerceIn(MIN_HOLD_CHECK_MS, holdDelayMs))
        }
    }

    /** Replaces the wet stroke with a line from where it began to where the pen is. */
    private fun straighten() {
        val old = strokeId ?: return
        val pending = pendingStrokes.remove(old) ?: return
        wet.cancelStroke(old)
        screenToPage.invert(pageToScreen)
        val first = strokeInput(strokeStartX, strokeStartY, 0L)
        val id = wet.startStroke(first, InkBrushes.brush(drawStyle), pageToScreen)
        pendingStrokes[id] = pending
        strokeId = id
        straightening = true
        moveStraightEnd(id, SystemClock.uptimeMillis())
    }

    private fun moveStraightEnd(id: InProgressStrokeId, timeMs: Long) {
        val end = currentLineEnd(timeMs)
        val prediction = MutableStrokeInputBatch().add(end)
        wet.addToStroke(ImmutableStrokeInputBatch.EMPTY, id, prediction)
    }

    private fun finishStraightStroke(event: MotionEvent, id: InProgressStrokeId) {
        val index = event.findPointerIndex(drawPointerId)
        if (index >= 0) hold.move(event.getX(index), event.getY(index), event.eventTime)
        straightening = false
        wet.finishStroke(currentLineEnd(event.eventTime), id)
    }

    private fun currentLineEnd(timeMs: Long): StrokeInput {
        val end = floatArrayOf(hold.x, hold.y)
        screenToPage.mapPoints(end)
        return strokeInput(end[0], end[1], (timeMs - strokeStartMs).coerceAtLeast(1L))
    }

    /** A straight line has no pressure to speak of, so it comes out at an even width. */
    private fun strokeInput(x: Float, y: Float, elapsedMs: Long) = StrokeInput().apply {
        update(x, y, elapsedMs, InputToolType.STYLUS, STROKE_UNIT_CM, STRAIGHT_LINE_PRESSURE)
    }

    private fun eraseAt(x: Float, y: Float) {
        val layout = layout ?: return
        erase?.eraseTo(viewport.toWorldX(x) - layout.originX(drawPageIndex), viewport.toWorldY(y) - layout.originY(drawPageIndex))
        redrawChanges(drawPageIndex)
    }

    /**
     * Hides wet ink past the page's edge, where the finished stroke will be clipped anyway, and under
     * the views on top of this one.
     */
    private fun updateWetMask(layout: DocumentLayout, index: Int) {
        val size = editor?.document?.pages?.get(index)?.size
        val page = size?.let {
            val originX = layout.originX(index)
            val originY = layout.originY(index)
            RectF(
                viewport.toScreenX(originX),
                viewport.toScreenY(originY),
                viewport.toScreenX(originX + it.widthPt),
                viewport.toScreenY(originY + it.heightPt),
            )
        }
        if (maskIsCurrent && page == maskedPage) return
        maskIsCurrent = true
        maskedPage = page
        val mask = Path()
        if (page != null) {
            mask.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            mask.op(Path().apply { addRect(page, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        for (overlay in overlayBounds) {
            mask.op(Path().apply { addRect(overlay, Path.Direction.CW) }, Path.Op.UNION)
        }
        wet.maskPath = if (mask.isEmpty) null else mask
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        for ((id, stroke) in strokes) {
            val pending = pendingStrokes.remove(id) ?: continue
            val editor = pending.editor
            val item = StrokeItem(IdSource.Random.next(), pending.style, stroke.inputs.toStrokePoints())
            editor.strokes.put(item, stroke)
            editor.perform(ItemsEdit.addOnTop(pending.pageId, editor.content(pending.pageId), item))
        }
        syncChanges()
        wet.removeFinishedStrokes(strokes.keys)
    }

    // ---- Navigation ----

    override fun onTransform(dx: Float, dy: Float, scale: Float, focusX: Float, focusY: Float) {
        viewport.panX += dx
        viewport.panY += dy
        if (scale != 1f) {
            pinchedThisGesture = true
            viewport.zoomTo(viewport.zoom * scale, focusX, focusY)
            onZoomChanged(viewport.zoom)
        }
        clampPan()
        tiles.invalidate()
    }

    override fun onTransformEnd() {
        if (!pinchedThisGesture && fling()) return
        settlePan()
    }

    override fun onUndo() = undo()

    override fun onRedo() = redo()

    private fun trackVelocity(event: MotionEvent) {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER && recognizer.mode != InputMode.VIEW_ONLY) return
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(event)
    }

    private fun fling(): Boolean {
        val layout = layout ?: return false
        val tracker = velocityTracker ?: return false
        tracker.computeCurrentVelocity(1000, maxFlingVelocity)
        val vx = tracker.getXVelocity(liftedPointerId)
        val vy = tracker.getYVelocity(liftedPointerId)
        if (abs(vx) < minFlingVelocity && abs(vy) < minFlingVelocity) return false
        val scale = viewport.pxPerPt
        scroller.fling(
            viewport.panX.roundToInt(),
            viewport.panY.roundToInt(),
            vx.roundToInt(),
            vy.roundToInt(),
            layout.minPanX(scale, width, pageMarginPx).roundToInt(),
            layout.maxPanX(scale, width, pageMarginPx).roundToInt(),
            layout.minPanY(scale, height, pageMarginPx).roundToInt(),
            layout.maxPanY(scale, height, pageMarginPx).roundToInt(),
        )
        postOnAnimation(flingStep)
        return true
    }

    private fun stopFling() {
        if (scroller.isFinished) return
        scroller.forceFinished(true)
        removeCallbacks(flingStep)
        settlePan()
    }

    /** Puts the page on whole pixels, where tiles and new ink line up exactly, and redraws it sharply. */
    private fun settlePan() {
        viewport.panX = viewport.panX.roundToInt().toFloat()
        viewport.panY = viewport.panY.roundToInt().toFloat()
        tiles.settle()
        retainNearbyPages()
    }

    private fun clampPan() {
        if (width > 0) layout?.clampPan(viewport, width, height, pageMarginPx)
    }

    // ---- Document ----

    private fun rebuildLayout() {
        val document = editor?.document ?: return
        layout = DocumentLayout(document.pages, scrollDirection)
        layoutVersion = document.version
        placeFirstPage()
        clampPan()
        tiles.invalidateAll()
    }

    private fun placeFirstPage() {
        if (hasPlacedPage || width == 0) return
        val layout = layout ?: return
        layout.moveToPage(viewport, 0, width, height, pageMarginPx)
        settlePan()
        hasPlacedPage = true
    }

    /** Brings the screen up to date with whatever has changed on the pages. */
    private fun syncChanges() {
        val editor = editor ?: return
        if (editor.document.version != layoutVersion) {
            rebuildLayout()
            return
        }
        for (index in editor.document.pages.indices) redrawChanges(index)
    }

    private fun redrawChanges(pageIndex: Int) {
        val editor = editor ?: return
        val layout = layout ?: return
        val changed = editor.loadedPageOrNull(editor.document.pages[pageIndex].id)?.takeDirtyBounds() ?: return
        val x = layout.originX(pageIndex)
        val y = layout.originY(pageIndex)
        tiles.invalidateRegion(Bounds(changed.left + x, changed.top + y, changed.right + x, changed.bottom + y))
    }

    /** A page's ink arrived from the disk, which an empty tile is still waiting for. */
    private fun redrawPage(pageId: Long) {
        val editor = editor ?: return
        val layout = layout ?: return
        val index = editor.document.indexOfPage(pageId)
        if (index < 0) return
        val size = editor.document.pages[index].size
        tiles.invalidateRegion(
            if (size == null) {
                layout.bounds
            } else {
                Bounds.ofSize(layout.originX(index), layout.originY(index), size.widthPt, size.heightPt)
            },
        )
    }

    private fun paintTile(canvas: Canvas, region: Bounds, leftPx: Double, topPx: Double, scale: Float) {
        val layout = layout ?: return
        painter?.paint(canvas, layout, region, leftPx, topPx, scale)
    }

    /** Keeps the pages near the view in memory and lets the others go. */
    private fun retainNearbyPages() {
        val editor = editor ?: return
        val layout = layout ?: return
        if (width == 0) return
        val pages = editor.document.pages
        var first = Int.MAX_VALUE
        var last = -1
        layout.forEachPageIn(viewport.visibleWorld(width, height)) { index ->
            if (index < first) first = index
            if (index > last) last = index
        }
        if (last < 0) return
        val keep = HashSet<Long>()
        for (index in maxOf(0, first - NEARBY_PAGES)..minOf(pages.lastIndex, last + NEARBY_PAGES)) keep += pages[index].id
        editor.retainPages(keep)
    }

    private companion object {
        const val PAGE_MARGIN_DP = 24f

        /** How far a held pen can wander and still count as still. */
        const val HOLD_SLOP_DP = 3f
        const val MIN_HOLD_CHECK_MS = 8L
        const val STRAIGHT_LINE_PRESSURE = 0.5f

        /** Pages kept in memory on each side of the view, so that turning back finds them ready. */
        const val NEARBY_PAGES = 2

        /** What the eraser end of a stylus does when the tool in hand is not the eraser. */
        val DEFAULT_ERASER = CanvasTool.Eraser(EraseMode.PARTIAL, radiusPt = 6f)

        /**
         * The screen's real density, which zoom 1 relies on to show true size. It reads the panel's
         * values rather than densityDpi, which is a rounded size for the UI. A calibration setting
         * can replace it through [Viewport.screenDpi], since some devices report these wrongly.
         */
        fun physicalDpi(context: Context): Float {
            val metrics = context.resources.displayMetrics
            return (metrics.xdpi + metrics.ydpi) / 2f
        }
    }
}
