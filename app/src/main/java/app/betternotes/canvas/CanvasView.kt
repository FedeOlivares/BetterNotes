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
import app.betternotes.core.tools.DEFAULT_ERASABLE
import app.betternotes.core.tools.EraseSession
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.clearEdit
import app.betternotes.core.tools.Ruler
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What the stylus does: lay down ink, erase, or point. */
sealed interface CanvasTool {
    /** Holding the pen still for [holdToLineMs] turns the stroke into a straight line. 0 turns that off. */
    data class Ink(val style: InkStyle, val holdToLineMs: Int = 0) : CanvasTool
    data class Eraser(val mode: EraseMode, val radiusPt: Float, val kinds: Set<ItemKind> = DEFAULT_ERASABLE) : CanvasTool

    /** Shows where the pen is, with a fading tail, and leaves nothing on the page. */
    data object Laser : CanvasTool

    /** Selects items and moves or resizes the selection. */
    data class Lasso(val rectangle: Boolean, val kinds: Set<ItemKind>, val fullOnly: Boolean) : CanvasTool
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

    /** Called whenever the page moves under the view, for the page and zoom indicator. */
    var onViewMoved: () -> Unit = {}

    /** The page at the middle of the view, counting from 0. */
    val currentPage: Int
        get() {
            val layout = layout ?: return 0
            if (width == 0) return 0
            val index = layout.pageIndexAt(viewport.toWorldX(width / 2f), viewport.toWorldY(height / 2f))
            return if (index >= 0) index else lastCurrentPage
        }

    val pageCount get() = editor?.document?.pages?.size ?: 0

    /** How thick the laser's tail is, relative to its middle setting. */
    var laserScale: Float
        get() = overlay.laserScale
        set(value) {
            overlay.laserScale = value
        }

    /** Brings a page to the view at the current zoom. */
    fun goToPage(index: Int) {
        val layout = layout ?: return
        if (index !in 0 until pageCount || width == 0) return
        stopFling()
        lastCurrentPage = index
        layout.moveToPage(viewport, index, width, height, pageMarginPx)
        clampPan()
        settlePan()
        onViewMoved()
    }

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
        set(value) {
            if (value !is CanvasTool.Lasso) lasso.clear()
            field = value
        }

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

    /** A ruler over the page, which two fingers on it can move and which strokes near its edges follow. */
    var rulerVisible: Boolean
        get() = overlay.ruler != null
        set(value) {
            if (value == rulerVisible) return
            overlay.ruler = if (value) newRuler() else null
            draggingRuler = false
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
    private var lastCurrentPage = 0

    private val tiles = TileLayerView(context, viewport, ::paintTile, backdropColor)
    private val wet = InProgressStrokesView(context)
    private val overlay = CanvasOverlayView(context, viewport)
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
            onViewMoved()
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

    // The ruler's two-finger move, and the edge a stroke is following.
    private var draggingRuler = false
    private var waitingForAllUp = false
    private var rulerFinger0 = -1
    private var rulerFinger1 = -1
    private var strokeEdge = Ruler.NO_EDGE

    private var laserPointer = -1
    private var lassoPointer = -1

    private val lasso by lazy {
        LassoController(
            viewport,
            resources.displayMetrics.density,
            { index -> layout?.originX(index) ?: 0f },
            { index -> layout?.originY(index) ?: 0f },
            { overlay.invalidate() },
        )
    }

    private class PendingStroke(val editor: NoteEditor, val pageId: Long, val style: InkStyle)

    init {
        setBackgroundColor(backdropColor)
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.extraDraw = { canvas -> lasso.draw(canvas) }
        wet.addFinishedStrokesListener(this)
    }

    fun open(editor: NoteEditor) {
        close()
        this.editor = editor
        lasso.open(editor)
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
        lasso.open(null)
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

    /** Removes what is selected, as one edit. */
    fun deleteSelection() {
        lasso.deleteSelection()
        syncChanges()
    }

    val hasSelection get() = lasso.hasSelection

    /**
     * Clears the page at the middle of the view. On an infinite canvas it clears only what is showing,
     * since there is no end to the rest.
     */
    fun clearPage() {
        val editor = editor ?: return
        val layout = layout ?: return
        val page = editor.document.pages.getOrNull(currentPage) ?: return
        val region = if (page.size != null) {
            null
        } else {
            val world = viewport.visibleWorld(width, height)
            val x = layout.originX(currentPage)
            val y = layout.originY(currentPage)
            Bounds(world.left - x, world.top - y, world.right - x, world.bottom - y)
        }
        lasso.clear()
        clearEdit(page.id, editor.content(page.id), region)?.let { editor.perform(it) }
        syncChanges()
    }

    fun undo() {
        lasso.clear()
        val done = editor?.undo() ?: return
        if (done) syncChanges()
    }

    fun redo() {
        lasso.clear()
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
        if (handleRulerTouch(event)) return true
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

    // ---- The ruler ----

    private fun newRuler(): Ruler {
        val density = resources.displayMetrics.density
        return Ruler(
            centerX = width / 2f,
            centerY = height / 2f,
            length = hypot(width.toFloat(), height.toFloat()) * RULER_LENGTH_OF_DIAGONAL,
            thickness = RULER_THICKNESS_DP * density,
        )
    }

    /**
     * Two fingers landing on the ruler pick it up, and the page never hears of them again: what the
     * recognizer took for a pan or a tap so far is cancelled. Returns true if the ruler used the event.
     */
    private fun handleRulerTouch(event: MotionEvent): Boolean {
        val ruler = overlay.ruler
        val action = event.actionMasked
        if (waitingForAllUp) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) waitingForAllUp = false
            return true
        }
        if (ruler == null) return false
        if (!draggingRuler) {
            if (action != MotionEvent.ACTION_POINTER_DOWN || event.pointerCount != 2 || !bothFingersOn(ruler, event)) {
                return false
            }
            for (i in 0 until event.pointerCount) recognizer.pointerCancel(event.getPointerId(i), event.eventTime)
            rulerFinger0 = event.getPointerId(0)
            rulerFinger1 = event.getPointerId(1)
            ruler.grab(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
            draggingRuler = true
            return true
        }
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                val i0 = event.findPointerIndex(rulerFinger0)
                val i1 = event.findPointerIndex(rulerFinger1)
                if (i0 >= 0 && i1 >= 0) {
                    ruler.drag(event.getX(i0), event.getY(i0), event.getX(i1), event.getY(i1))
                    overlay.invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                draggingRuler = false
                waitingForAllUp = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> draggingRuler = false
        }
        return true
    }

    private fun bothFingersOn(ruler: Ruler, event: MotionEvent): Boolean {
        val margin = RULER_GRAB_MARGIN_DP * resources.displayMetrics.density
        for (i in 0 until event.pointerCount) {
            if (event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) return false
            if (!ruler.contains(event.getX(i), event.getY(i), margin)) return false
        }
        return true
    }

    // ---- Drawing ----

    override fun onDrawStart(pointerId: Int, kind: PointerKind, x: Float, y: Float) {
        if (tool is CanvasTool.Laser && kind != PointerKind.STYLUS_ERASER) {
            laserPointer = pointerId
            overlay.laserStart(x, y)
            return
        }
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
            erase = EraseSession(page.id, editor.content(page.id), eraser.mode, eraser.radiusPt, kinds = eraser.kinds)
            eraseAt(x, y)
        } else if (tool is CanvasTool.Lasso) {
            lassoPointer = pointerId
            editor.content(page.id)
            lasso.start(index, pageX(x, index), pageY(y, index), tool.rectangle, tool.kinds, tool.fullOnly)
            syncChanges()
        } else if (tool is CanvasTool.Ink) {
            drawStyle = tool.style
            holdDelayMs = tool.holdToLineMs.toLong()
            val reach = RULER_EDGE_REACH_DP * resources.displayMetrics.density
            strokeEdge = overlay.ruler?.edgeNear(x, y, reach) ?: Ruler.NO_EDGE
            // A stroke that starts when a tap times out has no event yet, and ink needs one.
            val event = currentEvent
            if (event != null) startStroke(event) else strokeWaitingForEvent = true
        }
    }

    override fun onDrawMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId == laserPointer) {
            overlay.laserMove(x, y)
            return
        }
        if (pointerId == lassoPointer) {
            lasso.move(pageX(x, drawPageIndex), pageY(y, drawPageIndex))
            return
        }
        if (erase != null) eraseAt(x, y)
    }

    override fun onDrawEnd(pointerId: Int) {
        if (pointerId == laserPointer) {
            laserPointer = -1
            return
        }
        if (pointerId == lassoPointer) {
            lassoPointer = -1
            lasso.end()
            syncChanges()
            return
        }
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
        if (pointerId == laserPointer) {
            laserPointer = -1
            return
        }
        if (pointerId == lassoPointer) {
            lassoPointer = -1
            lasso.cancel()
            syncChanges()
            return
        }
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
        strokeEdge = Ruler.NO_EDGE
        wet.cancelStroke(id, currentEvent)
        pendingStrokes.remove(id)
        strokeId = null
    }

    private fun pageX(screenX: Float, index: Int) = viewport.toWorldX(screenX) - (layout?.originX(index) ?: 0f)

    private fun pageY(screenY: Float, index: Int) = viewport.toWorldY(screenY) - (layout?.originY(index) ?: 0f)

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
        if (strokeEdge != Ruler.NO_EDGE) {
            startStrokeAlongRuler(event, editor, page.id)
            return
        }
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
        if (holdDelayMs > 0L || straightening) {
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

    /** A stroke that starts by a ruler's edge runs along the edge, wherever the pen wanders. */
    private fun startStrokeAlongRuler(event: MotionEvent, editor: NoteEditor, pageId: Long) {
        val index = event.findPointerIndex(drawPointerId)
        val ruler = overlay.ruler
        if (index < 0 || ruler == null) return
        val x = event.getX(index)
        val y = event.getY(index)
        hold.start(x, y, event.eventTime)
        strokeStartMs = event.eventTime
        val start = floatArrayOf(ruler.projectX(x, y, strokeEdge), ruler.projectY(x, y, strokeEdge))
        screenToPage.mapPoints(start)
        strokeStartX = start[0]
        strokeStartY = start[1]
        screenToPage.invert(pageToScreen)
        val id = wet.startStroke(strokeInput(strokeStartX, strokeStartY, 0L), InkBrushes.brush(drawStyle), pageToScreen)
        pendingStrokes[id] = PendingStroke(editor, pageId, drawStyle)
        strokeId = id
        straightening = true
        strokeStartedInThisEvent = true
    }

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
        val end = currentLineEnd(event.eventTime)
        straightening = false
        strokeEdge = Ruler.NO_EDGE
        wet.finishStroke(end, id)
    }

    private fun currentLineEnd(timeMs: Long): StrokeInput {
        val ruler = overlay.ruler
        val end = if (strokeEdge != Ruler.NO_EDGE && ruler != null) {
            floatArrayOf(ruler.projectX(hold.x, hold.y, strokeEdge), ruler.projectY(hold.x, hold.y, strokeEdge))
        } else {
            floatArrayOf(hold.x, hold.y)
        }
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
        onViewMoved()
        if (lasso.hasSelection) overlay.invalidate()
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

        /** Long enough to cross the screen at any angle, wherever two fingers carry it. */
        const val RULER_LENGTH_OF_DIAGONAL = 3f
        const val RULER_THICKNESS_DP = 140f
        const val RULER_GRAB_MARGIN_DP = 12f

        /** How close to a ruler's edge a stroke must start to run along it. */
        const val RULER_EDGE_REACH_DP = 14f
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
