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
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.gesture.GestureListener
import app.betternotes.core.gesture.GestureRecognizer
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.gesture.PointerKind
import app.betternotes.core.layout.DocumentLayout
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.tools.EraseMode
import app.betternotes.core.tools.EraseSession
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the stylus does: lay down ink, or erase. */
sealed interface CanvasTool {
    data class Ink(val style: InkStyle) : CanvasTool
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
    private val reportedDpi = physicalDpi(context)

    val viewport = Viewport(reportedDpi)

    /** Called whenever the zoom changes. */
    var onZoomChanged: (zoom: Float) -> Unit = {}

    var tool: CanvasTool = CanvasTool.Ink(InkStyle())

    /** Called whenever the input mode changes, so that the toolbars can follow a shortcut. */
    var onInputModeChanged: (InputMode) -> Unit = {}

    var inputMode: InputMode
        get() = recognizer.mode
        set(value) {
            if (recognizer.mode == value) return
            if (value == InputMode.VIEW_ONLY) modeBeforeViewOnly = recognizer.mode
            recognizer.mode = value
            stopFling()
            onInputModeChanged(value)
        }

    /** Changing direction keeps the current page in view. */
    var scrollDirection = ScrollDirection.VERTICAL
        set(value) {
            if (field == value) return
            val page = currentPageIndex
            field = value
            rebuildLayout()
            if (page >= 0) goToPage(page)
        }

    /** The page under the middle of the view, or the first page on screen if the middle is in a gap. */
    val currentPageIndex: Int
        get() {
            val layout = layout ?: return -1
            if (width == 0) return 0
            val middle = layout.pageIndexAt(viewport.toWorldX(width / 2f), viewport.toWorldY(height / 2f))
            if (middle >= 0) return middle
            layout.forEachPageIn(viewport.visibleWorld(width, height)) { return it }
            return 0
        }

    internal var observer: CanvasObserver? = null
        set(value) {
            field = value
            value?.onWetInkReady(wet)
            tiles.onTileRecorded = value?.let { it::onTileRecorded }
        }

    private val backdropColor = 0xFF0E1013.toInt()
    private var editor: NoteEditor? = null
    private var painter: PagePainter? = null
    private var layout: DocumentLayout? = null
    private var layoutVersion = -1L
    private var hasPlacedPage = false
    private var modeBeforeViewOnly = InputMode.STYLUS_DRAW

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
    private val maskRect = RectF()

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
        painter = PagePainter(editor.paintSource)
        editor.onPageLoaded = ::redrawPage
        hasPlacedPage = false
        rebuildLayout()
    }

    /**
     * Finishes with the open document. A stroke on its way out of the wet layer still lands on the
     * page it was drawn on, because it remembers its editor.
     */
    fun close() {
        editor?.onPageLoaded = {}
        editor = null
        // A frame can still be on its way, and it must not reach for a document that has closed.
        painter = null
    }

    /** Sets the zoom about the center of the view. Zoom 1 is real size. */
    fun zoomTo(zoom: Float) {
        viewport.zoomTo(zoom, width / 2f, height / 2f)
        clampPan()
        settlePan()
        onZoomChanged(viewport.zoom)
    }

    /** Applies the settings this view follows. Safe to call whenever they change. */
    fun applySettings(settings: AppSettings) {
        recognizer.config = settings.gestures.applyTo(recognizer.config)
        scrollDirection = settings.scrollDirection
        val dpi = settings.calibration?.dpi ?: reportedDpi
        if (dpi != viewport.screenDpi) {
            viewport.changeDpi(dpi, width / 2f, height / 2f)
            clampPan()
            settlePan()
        }
    }

    /** Shows the start of page [index]. */
    fun goToPage(index: Int) {
        val layout = layout ?: return
        if (width == 0 || index !in 0 until layout.pageCount) return
        stopFling()
        layout.moveToPage(viewport, index, width, height, pageMarginPx)
        settlePan()
    }

    /** Applies an undoable change to the document, such as a new background, and redraws what it touched. */
    fun perform(edit: Edit) {
        editor?.perform(edit) ?: return
        syncChanges()
    }

    /**
     * Does [action] if it belongs to the canvas, as a keyboard shortcut asks. Returns false if it is
     * for someone else, such as the toolbars. Nothing that edits runs in view-only mode.
     */
    fun perform(action: EditorAction): Boolean {
        val pageCount = layout?.pageCount ?: return false
        val viewOnly = inputMode == InputMode.VIEW_ONLY
        when (action) {
            EditorAction.UNDO -> if (!viewOnly) undo()
            EditorAction.REDO -> if (!viewOnly) redo()
            EditorAction.TOGGLE_VIEW_ONLY -> inputMode = if (viewOnly) modeBeforeViewOnly else InputMode.VIEW_ONLY
            EditorAction.TOGGLE_FINGER_DRAW -> when (inputMode) {
                InputMode.STYLUS_DRAW -> inputMode = InputMode.FINGER_DRAW
                InputMode.FINGER_DRAW -> inputMode = InputMode.STYLUS_DRAW
                InputMode.VIEW_ONLY -> Unit
            }
            EditorAction.NEXT_PAGE -> goToPage(minOf(currentPageIndex + 1, pageCount - 1))
            EditorAction.PREVIOUS_PAGE -> goToPage(maxOf(currentPageIndex - 1, 0))
            EditorAction.FIRST_PAGE -> goToPage(0)
            EditorAction.LAST_PAGE -> goToPage(pageCount - 1)
            EditorAction.ZOOM_IN -> zoomTo(viewport.zoom * ZOOM_STEP)
            EditorAction.ZOOM_OUT -> zoomTo(viewport.zoom / ZOOM_STEP)
            EditorAction.ZOOM_REAL_SIZE -> zoomTo(1f)
            else -> return false
        }
        return true
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
        stopFling()
        recognizer.reset()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
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
        wet.finishStroke(event, pointerId, id)
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
        maskOutsidePage(layout, drawPageIndex)
        val id = wet.startStroke(event, drawPointerId, InkBrushes.brush(drawStyle), screenToPage)
        pendingStrokes[id] = PendingStroke(editor, page.id, drawStyle)
        strokeId = id
        strokeStartedInThisEvent = true
    }

    /** Passes a whole motion event to ink, along with a guess at what comes next. */
    private fun addToStroke(event: MotionEvent) {
        val id = strokeId ?: return
        // A stroke that started partway through this event already has its latest sample.
        if (strokeStartedInThisEvent) return
        val predicted = predictor.predict()
        wet.addToStroke(event, drawPointerId, id, predicted)
        predicted?.recycle()
    }

    private fun eraseAt(x: Float, y: Float) {
        val layout = layout ?: return
        erase?.eraseTo(viewport.toWorldX(x) - layout.originX(drawPageIndex), viewport.toWorldY(y) - layout.originY(drawPageIndex))
        redrawChanges(drawPageIndex)
    }

    /** Hides wet ink past the page's edge, where the finished stroke will be clipped anyway. */
    private fun maskOutsidePage(layout: DocumentLayout, index: Int) {
        val size = editor?.document?.pages?.get(index)?.size
        if (size == null) {
            if (wet.maskPath != null) wet.maskPath = null
            return
        }
        val originX = layout.originX(index)
        val originY = layout.originY(index)
        val rect = RectF(
            viewport.toScreenX(originX),
            viewport.toScreenY(originY),
            viewport.toScreenX(originX + size.widthPt),
            viewport.toScreenY(originY + size.heightPt),
        )
        if (rect == maskRect && wet.maskPath != null) return
        maskRect.set(rect)
        wet.maskPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRect(rect, Path.Direction.CW)
        }
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

        /** What one press of a zoom shortcut multiplies the zoom by. */
        const val ZOOM_STEP = 1.25f

        /** Pages kept in memory on each side of the view, so that turning back finds them ready. */
        const val NEARBY_PAGES = 2

        /** What the eraser end of a stylus does when the tool in hand is not the eraser. */
        val DEFAULT_ERASER = CanvasTool.Eraser(EraseMode.PARTIAL, radiusPt = 6f)

        /**
         * The screen's real density, which zoom 1 relies on to show true size. It reads the panel's
         * values rather than densityDpi, which is a rounded size for the UI. Some devices report these
         * wrongly, so [AppSettings.calibration] can replace it.
         */
        fun physicalDpi(context: Context): Float {
            val metrics = context.resources.displayMetrics
            return (metrics.xdpi + metrics.ydpi) / 2f
        }
    }
}
