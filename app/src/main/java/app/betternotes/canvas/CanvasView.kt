package app.betternotes.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.input.motionprediction.MotionEventPredictor
import app.betternotes.core.edit.InsertPage
import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.edit.MovePage
import app.betternotes.core.edit.RemovePage
import app.betternotes.core.geometry.Affine
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
import app.betternotes.audio.Waveforms
import app.betternotes.core.model.AudioBar
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.Page
import app.betternotes.core.model.InkStyle
import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VideoItem
import app.betternotes.core.model.Document
import app.betternotes.core.search.TextMatch
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.settings.StylusAction
import app.betternotes.core.settings.StylusButtonListener
import app.betternotes.core.settings.StylusButtons
import app.betternotes.core.settings.StylusEraserEnd
import app.betternotes.core.tools.EraseMode
import app.betternotes.core.tools.DEFAULT_ERASABLE
import app.betternotes.core.tools.EraseSession
import app.betternotes.core.tools.ALL_ITEM_KINDS
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.Selection
import app.betternotes.core.tools.Stabilizer
import app.betternotes.core.tools.StrokeGestures
import app.betternotes.core.tools.selectWithLasso
import app.betternotes.core.tools.clearEdit
import app.betternotes.core.tools.Ruler
import app.betternotes.core.tools.ImageOptions
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.core.tools.TapeOptions
import app.betternotes.core.tools.cellAt
import app.betternotes.core.tools.typedText
import app.betternotes.core.tools.withCell
import app.betternotes.core.tools.withText
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** What the stylus does: lay down ink, erase, or point. */
sealed interface CanvasTool {
    /** Holding the pen still for [holdToLineMs] turns the stroke into a straight line. 0 turns that off. */
    data class Ink(
        val style: InkStyle,
        val holdToLineMs: Int = 0,
        /** A zigzag erases what it crosses instead of staying as ink. */
        val scribbleToErase: Boolean = false,
        /** After a circle-ish stroke, touching its line selects what it surrounds. */
        val circleToLasso: Boolean = false,
        /** 1 is the pen itself; each level above follows it more slowly. */
        val stabilization: Int = 1,
    ) : CanvasTool
    data class Eraser(val mode: EraseMode, val radiusPt: Float, val kinds: Set<ItemKind> = DEFAULT_ERASABLE) : CanvasTool

    /** Shows where the pen is, with a fading tail, and leaves nothing on the page. */
    data object Laser : CanvasTool

    /** Selects items and moves or resizes the selection. */
    data class Lasso(val rectangle: Boolean, val kinds: Set<ItemKind>, val fullOnly: Boolean) : CanvasTool

    /** Drags out a new shape. */
    data class Shape(val options: ShapeOptions) : CanvasTool

    /** Lays a strip of tape between where the pen lands and lifts. */
    data class Tape(val options: TapeOptions) : CanvasTool

    /** Puts an emoji where the pen lands, at a size in points. */
    data class Emoji(val text: String, val sizePt: Int) : CanvasTool

    /** Places a sticky note, or opens, closes or types in the one under the pen. */
    data class Sticky(val options: NoteOptions) : CanvasTool

    /** Places text, or edits the text under the pen. */
    data class Text(val options: NoteOptions) : CanvasTool

    /** Places a table, or edits the cell under the pen. */
    data class Table(val options: TableOptions) : CanvasTool
}

/**
 * A request to show a text box over the canvas, in view pixels. [commitNow] is filled in by whoever shows
 * it, so that the canvas can end the edit when the pen comes down somewhere else.
 */
class TextEditRequest(
    val initial: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val textSizePx: Float,
    val color: Int,
    val style: TextStyle,
    private val commit: (String) -> Unit,
) {
    var commitNow: () -> Unit = {}

    fun finish(text: String) = commit(text)
}

/** A request to play a video where it sits, in view pixels. */
class VideoPlayRequest(val file: File, val left: Float, val top: Float, val width: Float, val height: Float)

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
    /** The density the device claims, which calibration can correct. */
    val reportedDpi = physicalDpi(context)

    val viewport = Viewport(reportedDpi)

    /** Called whenever the zoom changes. */
    var onZoomChanged: (zoom: Float) -> Unit = {}

    /** Asks for a video to be played. */
    var onPlayVideo: (VideoPlayRequest) -> Unit = {}

    /** Whether something moved with the lasso lines up with what is around it. */
    var snapToAlign: Boolean
        get() = lasso.snapEnabled
        set(value) {
            lasso.snapEnabled = value
        }

    /** Asks for a text box. Null takes it away. */
    var onTextEdit: (TextEditRequest?) -> Unit = {}

    private var textEdit: TextEditRequest? = null

    /** Whether the loupe is showing. */
    var loupeOpen: Boolean
        get() = loupe.isVisible
        set(value) {
            if (value == loupeOpen) return
            loupe.isVisible = value
            if (value) {
                loupe.pageIndex = currentPage
                recenterLoupe()
            } else {
                maskIsCurrent = false
            }
            overlay.invalidate()
        }

    /** Where the loupe's window goes on screen, from whoever lays out its controls. */
    fun setLoupeArea(left: Float, top: Float, right: Float, bottom: Float) {
        loupe.area.set(left, top, right, bottom)
        if (loupeOpen && loupe.box.isEmpty) recenterLoupe()
        loupe.invalidate()
    }

    /** Puts the loupe's box in the middle of the view. */
    fun recenterLoupe() {
        val layout = layout ?: return
        if (width == 0 || loupe.area.isEmpty) return
        loupe.pageIndex = currentPage
        val world = viewport.visibleWorld(width, height)
        val boxWidth = world.width * LOUPE_BOX_SHARE
        val boxHeight = boxWidth * loupe.area.height() / loupe.area.width()
        val left = world.centerX - boxWidth / 2f - layout.originX(loupe.pageIndex)
        val top = world.centerY - boxHeight / 2f - layout.originY(loupe.pageIndex)
        loupe.box.set(left, top, left + boxWidth, top + boxHeight)
        overlay.invalidate()
        loupe.invalidate()
    }

    /** Moves the loupe's box along the line of writing, by its own width. At the page's edge it starts the next line. */
    fun stepLoupe(forward: Boolean) {
        val size = pages.getOrNull(loupe.pageIndex)?.size
        val step = loupe.box.width() * LOUPE_STEP_SHARE * if (forward) 1f else -1f
        loupe.box.offset(step, 0f)
        if (size != null) {
            if (forward && loupe.box.left >= size.widthPt) {
                loupe.box.offsetTo(0f, loupe.box.top + loupe.box.height())
            } else if (!forward && loupe.box.right <= 0f) {
                loupe.box.offsetTo((size.widthPt - loupe.box.width()).coerceAtLeast(0f), loupe.box.top - loupe.box.height())
            }
        }
        overlay.invalidate()
        loupe.invalidate()
    }

    private fun drawLoupeBox(canvas: Canvas) {
        val layout = layout
        if (!loupeOpen || layout == null || loupe.box.isEmpty) return
        val x = layout.originX(loupe.pageIndex)
        val y = layout.originY(loupe.pageIndex)
        canvas.drawRect(
            viewport.toScreenX(x + loupe.box.left),
            viewport.toScreenY(y + loupe.box.top),
            viewport.toScreenX(x + loupe.box.right),
            viewport.toScreenY(y + loupe.box.bottom),
            loupeBoxPaint,
        )
    }

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

    var laserMode: LaserMode
        get() = overlay.laserMode
        set(value) {
            overlay.laserMode = value
        }

    var laserLength: Int
        get() = overlay.laserLength
        set(value) {
            overlay.laserLength = value
        }

    var laserColor: Int
        get() = overlay.laserColor
        set(value) {
            overlay.laserColor = value
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

    /**
     * A map of an infinite page for the overlay: where its items are, and the part of it in view, in the
     * page's own points. Null on any other kind of page.
     */
    class Minimap(val items: FloatArray, val viewLeft: Float, val viewTop: Float, val viewRight: Float, val viewBottom: Float)

    fun minimap(): Minimap? {
        val open = editor ?: return null
        val layout = layout ?: return null
        if (!layout.isInfinite || width == 0) return null
        val content = open.contentOrLoad(open.document.pages[0].id) ?: return null
        val all = content.toList()
        // It is only of use when something is out of sight, and still would be if the view were zoomed out by a fifth.
        val left = viewport.toWorldX(0f)
        val top = viewport.toWorldY(0f)
        val right = viewport.toWorldX(width.toFloat())
        val bottom = viewport.toWorldY(height.toFloat())
        val growX = (right - left) * MINIMAP_ZOOM_OUT / 2f
        val growY = (bottom - top) * MINIMAP_ZOOM_OUT / 2f
        val wider = Bounds(left - growX, top - growY, right + growX, bottom + growY)
        if (all.none { !it.bounds.intersects(wider) }) return null
        // A map has no use for every item of a huge page, so it shows an even sample of them.
        val step = (all.size / MINIMAP_MAX_ITEMS).coerceAtLeast(1)
        val boxes = ArrayList<Float>()
        for (i in all.indices step step) {
            val b = all[i].bounds
            boxes += b.left
            boxes += b.top
            boxes += b.right
            boxes += b.bottom
        }
        return Minimap(boxes.toFloatArray(), left, top, right, bottom)
    }

    /** The open document, for changes made from outside the canvas. */
    val openDocument: Document? get() = editor?.document

    /** The open editor, for what works on the saved files, such as export and search. */
    val openEditor: NoteEditor? get() = editor

    /** Called for a stylus button or shortcut's action that isn't the canvas's, as for choosing a tool. */
    var onToolAction: (EditorAction) -> Boolean = { false }

    /** Called when a circle drawn with the pen has been touched and the lasso should take over. */
    var onLassoRequested: () -> Unit = {}

    /** Called when the selection is dropped: a touch outside it, a delete, or a change of tool. */
    var onSelectionDropped: () -> Unit
        get() = lasso.onSelectionDropped
        set(value) {
            lasso.onSelectionDropped = value
        }

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
    private var modeBeforeViewOnly = InputMode.STYLUS_DRAW
    private var continueFromLastPage = false
    private var predictInk = true
    private var eraserEnd = StylusEraserEnd.ERASER
    private var holdErasing = false
    private var stylusButtonState = 0

    private val tiles = TileLayerView(context, viewport, ::paintTile, backdropColor)
    private val wet = InProgressStrokesView(context).apply { textureBitmapStore = BrushTextures }
    private val overlay = CanvasOverlayView(context, viewport)
    private val loupe = LoupeView(context).apply {
        visibility = GONE
        layoutOf = { layout }
    }
    private val predictor = MotionEventPredictor.newInstance(this)
    private val recognizer = GestureRecognizer(
        GestureConfig(touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()),
        this,
    )
    private val deadlineCheck = Runnable {
        recognizer.onDeadline(SystemClock.uptimeMillis())
        scheduleDeadline()
    }
    private val stylusButtons = StylusButtons(
        object : StylusButtonListener {
            override fun onStylusAction(action: StylusAction) {
                val editorAction = when (action) {
                    StylusAction.NONE -> return
                    StylusAction.PEN -> EditorAction.PEN
                    StylusAction.HIGHLIGHTER -> EditorAction.HIGHLIGHTER
                    StylusAction.ERASER -> EditorAction.ERASER
                    StylusAction.LASSO -> EditorAction.LASSO
                    StylusAction.UNDO -> EditorAction.UNDO
                    StylusAction.REDO -> EditorAction.REDO
                }
                if (!perform(editorAction)) onToolAction(editorAction)
            }

            override fun onHoldStart() {
                holdErasing = true
            }

            override fun onHoldEnd() {
                holdErasing = false
            }
        },
    )
    private val stylusHoldCheck = Runnable { stylusButtons.onDeadline(SystemClock.uptimeMillis()) }

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
    private var drawScribble = false
    private var drawCircle = false
    private var lastLoop: LoopMark? = null
    private var stabilizer: Stabilizer? = null
    private val stabilized = FloatArray(2)
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

    // The stroke in progress is in the loupe's window, or the pen is dragging its box on the page.
    private var drawingInLoupe = false
    private var loupeBoxPointer = -1
    private var loupeBoxGrabX = 0f
    private var loupeBoxGrabY = 0f
    private val loupeBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = LOUPE_BOX_COLOR
        strokeWidth = LOUPE_BOX_WIDTH_DP * resources.displayMetrics.density
    }
    private var shapePointer = -1

    private val shapes by lazy {
        ShapeController(
            viewport,
            { index -> layout?.originX(index) ?: 0f },
            { index -> layout?.originY(index) ?: 0f },
            { overlay.invalidate() },
            { index, item ->
                val open = editor
                if (open != null) {
                    val pageId = open.document.pages[index].id
                    open.perform(ItemsEdit.addOnTop(pageId, open.content(pageId), item))
                    syncChanges()
                    // A new shape is selected, with its handles showing. Tape has none and stays on the tool.
                    if (item.kind != ShapeKind.TAPE) selectLast(pageId)
                }
            },
        )
    }

    private val lasso by lazy {
        LassoController(
            viewport,
            resources.displayMetrics.density,
            { index -> layout?.originX(index) ?: 0f },
            { index -> layout?.originY(index) ?: 0f },
            { overlay.invalidate() },
        )
    }

    private class PendingStroke(
        val editor: NoteEditor,
        val pageId: Long,
        val style: InkStyle,
        val stickyId: Long?,
        val scribbleToErase: Boolean = false,
        val circleToLasso: Boolean = false,
    )

    /** The last circle-ish stroke, which a touch on its line turns into a selection. */
    private class LoopMark(val pageId: Long, val item: StrokeItem)

    /** The open note that the stroke in progress belongs to, if it started in one. */
    private var strokeStickyId: Long? = null

    init {
        setBackgroundColor(backdropColor)
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(loupe, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.extraDraw = { canvas ->
            drawAudioBars(canvas)
            lasso.draw(canvas)
            shapes.draw(canvas)
            drawLoupeBox(canvas)
        }
        wet.addFinishedStrokesListener(this)
    }

    fun open(editor: NoteEditor) {
        close()
        this.editor = editor
        lasso.open(editor)
        painter = PagePainter(editor.paintSource)
        loupe.painter = painter
        editor.onPageLoaded = ::redrawPage
        editor.pdf?.onRegionReady = ::redrawPageRegion
        editor.onHistoryChanged = { onHistoryChanged() }
        editor.onImageLoaded = { tiles.invalidateAll() }
        onHistoryChanged()
        hasPlacedPage = false
        rebuildLayout()
    }

    /**
     * Finishes with the open document. A stroke on its way out of the wet layer still lands on the
     * page it was drawn on, because it remembers its editor.
     */
    fun close() {
        rememberPage()
        editor?.onPageLoaded = {}
        editor?.onHistoryChanged = {}
        editor?.onImageLoaded = {}
        lasso.open(null)
        editor?.pdf?.onRegionReady = { _, _ -> }
        editor = null
        // A frame can still be on its way, and it must not reach for a document that has closed.
        painter = null
        onHistoryChanged()
    }

    /**
     * Marks text on the pages, such as search results, in place of whatever was marked before. Pass
     * [current] to make one match stand out, and an empty list to clear.
     */
    fun showMatches(matches: List<TextMatch>, current: TextMatch? = null) {
        val byPage = HashMap<Long, ArrayList<PageHighlight>>()
        for (match in matches) {
            val highlights = byPage.getOrPut(match.pageId) { ArrayList() }
            for (rect in match.rects) highlights += PageHighlight(rect, match === current)
        }
        painter?.highlights = byPage
        tiles.invalidateAll()
    }

    /**
     * Brings a match into view, at the current zoom. A match already on screen stays where it is, so
     * stepping through nearby ones does not make the page jump.
     */
    fun reveal(match: TextMatch) {
        val editor = editor ?: return
        val layout = layout ?: return
        val index = editor.document.indexOfPage(match.pageId)
        if (index < 0 || width == 0) return
        stopFling()
        val originX = layout.originX(index)
        val originY = layout.originY(index)
        val visible = viewport.visibleWorld(width, height)
        if (match.rects.isEmpty()) {
            layout.moveToPage(viewport, index, width, height, pageMarginPx)
        } else {
            val first = match.rects[0]
            val box = match.rects.fold(first) { all, rect -> all.union(rect) }
            val target = Bounds(box.left + originX, box.top + originY, box.right + originX, box.bottom + originY)
            if (visible.contains(target)) return
            viewport.panX = width / 2f - target.centerX * viewport.pxPerPt
            viewport.panY = height / 2f - target.centerY * viewport.pxPerPt
            clampPan()
        }
        settlePan()
    }

    /** Saves which page is on screen, for the next time this document opens. */
    fun rememberPage() {
        val editor = editor ?: return
        if (!continueFromLastPage || !hasPlacedPage || width == 0) return
        val index = currentPageIndex
        if (index in editor.document.pages.indices) editor.rememberLastPage(editor.document.pages[index].id)
    }

    /** Sets the zoom about the center of the view. Zoom 1 is real size. */
    fun zoomTo(zoom: Float) {
        viewport.zoomTo(zoom, width / 2f, height / 2f)
        clampPan()
        settlePan()
        onZoomChanged(viewport.zoom)
        if (overlay.ruler != null) overlay.invalidate()
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
        endTextEdit()
        lasso.clear()
        clearEdit(page.id, editor.content(page.id), region)?.let { editor.perform(it) }
        syncChanges()
    }

    /** Applies the settings this view follows. Safe to call whenever they change. */
    fun applySettings(settings: AppSettings) {
        recognizer.config = settings.gestures.applyTo(recognizer.config)
        scrollDirection = settings.scrollDirection
        stylusButtons.settings = settings.stylus
        predictInk = settings.stylus.predictInk
        eraserEnd = settings.stylus.eraserEnd
        continueFromLastPage = settings.canvas.continueFromLastPage
        if (inputMode != InputMode.VIEW_ONLY) {
            inputMode = if (settings.gestures.oneFinger == OneFingerSetting.DRAW) InputMode.FINGER_DRAW else InputMode.STYLUS_DRAW
        }
        val dpi = settings.calibration?.dpi ?: reportedDpi
        if (dpi != viewport.screenDpi) {
            viewport.changeDpi(dpi, width / 2f, height / 2f)
            clampPan()
            settlePan()
        }
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
        removeCallbacks(stylusHoldCheck)
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

    /** A stylus hovering reports its buttons here, and one touching reports them in [onTouchEvent]. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        trackStylusButtons(event)
        return super.onGenericMotionEvent(event)
    }

    private fun trackStylusButtons(event: MotionEvent) {
        val tool = event.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return
        val state = event.buttonState
        val changed = state xor stylusButtonState
        if (changed == 0) return
        stylusButtonState = state
        if (changed and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
            if (state and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) {
                stylusButtons.primaryDown(event.eventTime)
            } else {
                stylusButtons.primaryUp(event.eventTime)
            }
        }
        if (changed and state and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) stylusButtons.secondaryDown()
        removeCallbacks(stylusHoldCheck)
        val deadline = stylusButtons.nextDeadlineMs
        if (deadline != StylusButtons.NO_DEADLINE) {
            postDelayed(stylusHoldCheck, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        }
    }

    // Drawing has no click to perform: a tap only ever means something to the recognizer.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        observer?.onTouch(event)
        if (handleRulerTouch(event)) return true
        trackStylusButtons(event)
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
        trackFingerTap(event)
        strokeStartedInThisEvent = false
        currentEvent = null
        scheduleDeadline()
        return true
    }

    private var fingerTapPossible = false
    private var fingerTapDownX = 0f
    private var fingerTapDownY = 0f

    /** A quick, still touch of one finger. It is what touches a circle drawn for circle to lasso, as the pen does. */
    private fun trackFingerTap(event: MotionEvent) {
        val finger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingerTapPossible = finger
                fingerTapDownX = event.x
                fingerTapDownY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> fingerTapPossible = false
            MotionEvent.ACTION_MOVE -> {
                if (fingerTapPossible && hypot(event.x - fingerTapDownX, event.y - fingerTapDownY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    fingerTapPossible = false
                }
            }
            MotionEvent.ACTION_UP -> {
                val tapped = fingerTapPossible && event.eventTime - event.downTime <= FINGER_TAP_MS
                fingerTapPossible = false
                if (tapped) selectCircleAt(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> fingerTapPossible = false
        }
    }

    private fun selectCircleAt(x: Float, y: Float) {
        val current = tool as? CanvasTool.Ink ?: return
        if (!current.circleToLasso || lastLoop == null) return
        val editor = editor ?: return
        val index = layout?.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y)) ?: return
        if (index < 0) return
        selectLoopAt(index, editor.document.pages[index].id, pageX(x, index), pageY(y, index), byFinger = true)
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
        if (kind == PointerKind.STYLUS_ERASER && eraserEnd == StylusEraserEnd.IGNORED) return
        val editor = editor ?: return
        val layout = layout ?: return
        drawingInLoupe = loupeOpen && loupe.contains(x, y)
        if (loupeOpen && !drawingInLoupe && startDraggingLoupeBox(pointerId, x, y)) return
        val index = if (drawingInLoupe) loupe.pageIndex else layout.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y))
        if (index < 0) return
        val page = editor.document.pages[index]
        drawPointerId = pointerId
        drawPageIndex = index
        endTextEdit()
        strokeStickyId = null
        if (kind != PointerKind.STYLUS_ERASER && pressAudio(pointerId, index, page.id, pageX(x, index), pageY(y, index), x, y)) return
        if (kind != PointerKind.STYLUS_ERASER && handlePlacement(index, pageX(x, index), pageY(y, index))) return

        val tool = tool
        if (kind == PointerKind.STYLUS_ERASER || holdErasing || tool is CanvasTool.Eraser) {
            val eraser = tool as? CanvasTool.Eraser ?: DEFAULT_ERASER
            erase = EraseSession(page.id, editor.content(page.id), eraser.mode, eraser.radiusPt, kinds = eraser.kinds)
            eraseAt(x, y)
        } else if (tool is CanvasTool.Lasso) {
            lassoPointer = pointerId
            editor.content(page.id)
            if (lasso.pressButton(index, pageX(x, index), pageY(y, index))) {
                syncChanges()
                return
            }
            lasso.start(index, pageX(x, index), pageY(y, index), tool.rectangle, tool.kinds, tool.fullOnly)
            syncChanges()
        } else if (tool is CanvasTool.Shape) {
            shapePointer = pointerId
            shapes.start(index, pageX(x, index), pageY(y, index)) { x0, y0, x1, y1 -> tool.options.item(0L, x0, y0, x1, y1) }
        } else if (tool is CanvasTool.Tape) {
            shapePointer = pointerId
            shapes.start(index, pageX(x, index), pageY(y, index)) { x0, y0, x1, y1 -> tool.options.item(0L, x0, y0, x1, y1) }
        } else if (tool is CanvasTool.Ink) {
            if (tool.circleToLasso && selectLoopAt(index, page.id, pageX(x, index), pageY(y, index))) return
            drawStyle = tool.style
            holdDelayMs = tool.holdToLineMs.toLong()
            drawScribble = tool.scribbleToErase
            drawCircle = tool.circleToLasso
            stabilizer = if (tool.stabilization > 1) Stabilizer(tool.stabilization) else null
            val reach = RULER_EDGE_REACH_DP * resources.displayMetrics.density
            strokeEdge = if (drawingInLoupe) Ruler.NO_EDGE else overlay.ruler?.edgeNear(x, y, reach) ?: Ruler.NO_EDGE
            // A stroke that starts when a tap times out has no event yet, and ink needs one.
            val event = currentEvent
            if (event != null) startStroke(event) else strokeWaitingForEvent = true
        }
    }

    override fun onDrawMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId == loupeBoxPointer) {
            val layout = layout ?: return
            val index = loupe.pageIndex
            loupe.box.offsetTo(
                viewport.toWorldX(x) - layout.originX(index) - loupeBoxGrabX,
                viewport.toWorldY(y) - layout.originY(index) - loupeBoxGrabY,
            )
            overlay.invalidate()
            loupe.invalidate()
            return
        }
        if (pointerId == laserPointer) {
            overlay.laserMove(x, y)
            return
        }
        if (pointerId == audioPointer) {
            moveAudio(x, y)
            return
        }
        if (pointerId == lassoPointer) {
            lasso.move(pageX(x, drawPageIndex), pageY(y, drawPageIndex))
            return
        }
        if (pointerId == shapePointer) {
            shapes.move(pageX(x, drawPageIndex), pageY(y, drawPageIndex))
            return
        }
        if (erase != null) eraseAt(x, y)
    }

    override fun onDrawEnd(pointerId: Int) {
        if (pointerId == audioPointer) {
            endAudio(cancelled = false)
            return
        }
        if (pointerId == loupeBoxPointer) {
            loupeBoxPointer = -1
            return
        }
        if (pointerId == laserPointer) {
            laserPointer = -1
            overlay.laserEnd()
            return
        }
        if (pointerId == lassoPointer) {
            lassoPointer = -1
            lasso.end()
            syncChanges()
            return
        }
        if (pointerId == shapePointer) {
            shapePointer = -1
            if (areaKind != AreaKind.NONE) finishAreaDrag() else shapes.end()
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
        } else if (stabilizer != null) {
            wet.finishStroke(rawInput(event, event.findPointerIndex(pointerId).coerceAtLeast(0), event.eventTime), id)
        } else {
            wet.finishStroke(event, pointerId, id)
        }
        stabilizer = null
        strokeId = null
    }

    override fun onDrawCancel(pointerId: Int) {
        if (pointerId == audioPointer) {
            endAudio(cancelled = true)
            return
        }
        if (pointerId == loupeBoxPointer) {
            loupeBoxPointer = -1
            return
        }
        if (pointerId == laserPointer) {
            laserPointer = -1
            overlay.laserEnd()
            return
        }
        if (pointerId == lassoPointer) {
            lassoPointer = -1
            lasso.cancel()
            syncChanges()
            return
        }
        if (pointerId == shapePointer) {
            shapePointer = -1
            areaKind = AreaKind.NONE
            shapes.cancel()
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
        stabilizer = null
        strokeId = null
    }

    // ---- Placing notes, text and tables ----

    /**
     * What the pen does when it comes down on or for one of these. Returns true if that was all it did.
     * Ink on a closed note opens it, and ink in an open one will stay in it.
     */
    private fun handlePlacement(index: Int, x: Float, y: Float): Boolean {
        val editor = editor ?: return false
        val page = editor.document.pages[index]
        val content = editor.content(page.id)
        if (playVideoAt(index, page.id, x, y)) return true
        val sticky = topItemOfType<StickyNoteItem>(page.id, x, y)
        // A touch outside an open note closes it. Ink goes on to draw there, and the note tool is done with the touch.
        if (sticky == null && (tool is CanvasTool.Sticky || tool is CanvasTool.Ink) && closeOpenStickies(page.id)) {
            if (tool is CanvasTool.Sticky) return true
        }
        if (sticky != null && (tool is CanvasTool.Sticky || tool is CanvasTool.Ink)) {
            val (at, note) = sticky
            when {
                !note.isOpen -> setSticky(page.id, at, note.copy(isOpen = true))
                note.isInFoldCorner(x, y) -> setSticky(page.id, at, note.copy(isOpen = false))
                tool is CanvasTool.Ink -> {
                    strokeStickyId = note.id
                    return false
                }
                else -> editStickyText(index, page.id, note)
            }
            return true
        }
        when (val current = tool) {
            is CanvasTool.Emoji -> {
                val size = current.sizePt.toFloat()
                val box = size * EMOJI_BOX_OF_SIZE
                val item = TextItem(
                    IdSource.Random.next(),
                    Frame(x - box / 2f, y - box / 2f, box, box),
                    current.text,
                    size,
                    NoteOptions.TEXT_COLOR,
                )
                editor.perform(ItemsEdit.addOnTop(page.id, content, item))
                syncChanges()
            }
            is CanvasTool.Sticky -> beginAreaDrag(AreaKind.STICKY, index, x, y, current.options)
            is CanvasTool.Text -> {
                val found = topItemOfType<TextItem>(page.id, x, y)?.second
                if (found != null) editText(index, page.id, found, isNew = false) else beginAreaDrag(AreaKind.TEXT, index, x, y, current.options)
            }
            is CanvasTool.Table -> {
                val found = topItemOfType<TableItem>(page.id, x, y)?.second
                if (found != null) {
                    editCell(index, page.id, found, found.cellAt(x, y))
                } else {
                    editor.perform(ItemsEdit.addOnTop(page.id, content, current.options.newTable(x, y)))
                    syncChanges()
                }
            }
            else -> return false
        }
        return true
    }

    /** The pen on a video's play button starts it. Anywhere else on the video, the pen does as it would. */
    private fun playVideoAt(index: Int, pageId: Long, x: Float, y: Float): Boolean {
        val open = editor ?: return false
        val layout = layout ?: return false
        val video = topItemOfType<VideoItem>(pageId, x, y)?.second ?: return false
        val reach = VIDEO_BUTTON_REACH_PT
        if (abs(x - video.frame.centerX) > reach || abs(y - video.frame.centerY) > reach) return false
        val scale = viewport.pxPerPt
        onPlayVideo(
            VideoPlayRequest(
                open.assetFile(video.assetId),
                viewport.toScreenX(layout.originX(index) + video.frame.left),
                viewport.toScreenY(layout.originY(index) + video.frame.top),
                video.frame.width * scale,
                video.frame.height * scale,
            ),
        )
        return true
    }

    private inline fun <reified T : PageItem> topItemOfType(pageId: Long, x: Float, y: Float): Pair<Int, T>? {
        val content = editor?.content(pageId) ?: return null
        for (i in content.size - 1 downTo 0) {
            val item = content[i]
            if (item is T && item.bounds.contains(x, y)) return i to item
        }
        return null
    }

    private fun indexOfItem(pageId: Long, id: Long): Int {
        val content = editor?.content(pageId) ?: return -1
        for (i in 0 until content.size) if (content[i].id == id) return i
        return -1
    }

    private fun setSticky(pageId: Long, at: Int, note: StickyNoteItem) {
        val editor = editor ?: return
        editor.perform(ItemsEdit.replace(pageId, editor.content(pageId), intArrayOf(at), listOf(note)))
        syncChanges()
    }

    private fun addToSticky(editor: NoteEditor, pageId: Long, stickyId: Long, stroke: StrokeItem): Boolean {
        val at = indexOfItem(pageId, stickyId)
        val note = editor.content(pageId).takeIf { at >= 0 }?.get(at) as? StickyNoteItem ?: return false
        val relative = stroke.transformed(Affine.translate(-note.frame.left, -note.frame.top))
        setSticky(pageId, at, note.copy(items = note.items + relative))
        return true
    }

    private enum class AreaKind { NONE, STICKY, TEXT }

    private var areaKind = AreaKind.NONE
    private var areaIndex = 0
    private var areaX = 0f
    private var areaY = 0f
    private var areaOptions = NoteOptions()

    /** A touch that places a note or text: a tap puts one at the default size, and a drag sizes it. */
    private fun beginAreaDrag(kind: AreaKind, index: Int, x: Float, y: Float, options: NoteOptions) {
        areaKind = kind
        areaIndex = index
        areaX = x
        areaY = y
        areaOptions = options
        shapePointer = drawPointerId
        shapes.start(index, x, y) { x0, y0, x1, y1 ->
            ShapeItem(
                0L,
                Frame(minOf(x0, x1), minOf(y0, y1), abs(x1 - x0), abs(y1 - y0)),
                ShapeKind.RECTANGLE,
                strokeColor = AREA_EDGE_COLOR,
                strokeWidth = 1.5f,
                fillColor = AREA_FILL_COLOR,
            )
        }
    }

    private fun finishAreaDrag() {
        val kind = areaKind
        areaKind = AreaKind.NONE
        val open = editor ?: return
        val page = open.document.pages.getOrNull(areaIndex) ?: return
        val dragged = shapes.takePreview()?.frame?.takeIf { it.width >= MIN_AREA_PT && it.height >= MIN_AREA_PT }
        when (kind) {
            AreaKind.STICKY -> {
                val note = areaOptions.newSticky(areaX, areaY).let { if (dragged != null) it.copy(frame = dragged) else it }
                open.perform(ItemsEdit.addOnTop(page.id, open.content(page.id), note))
                syncChanges()
                editStickyText(areaIndex, page.id, note)
            }
            AreaKind.TEXT -> {
                val item = TextItem(
                    IdSource.Random.next(),
                    dragged ?: Frame(areaX, areaY, NEW_TEXT_WIDTH_PT, NEW_TEXT_HEIGHT_PT),
                    "",
                    areaOptions.textSize.toFloat(),
                    NoteOptions.TEXT_COLOR,
                    areaOptions.style,
                )
                editText(areaIndex, page.id, item, isNew = true)
            }
            AreaKind.NONE -> Unit
        }
    }

    /** Folds every open sticky note on the page. Returns true if there was one. */
    private fun closeOpenStickies(pageId: Long): Boolean {
        val open = editor ?: return false
        val content = open.content(pageId)
        var closed = false
        for (i in 0 until content.size) {
            val note = content[i] as? StickyNoteItem ?: continue
            if (!note.isOpen) continue
            setSticky(pageId, i, note.copy(isOpen = false))
            closed = true
        }
        return closed
    }

    private fun StickyNoteItem.isInFoldCorner(x: Float, y: Float): Boolean {
        val reach = STICKY_CORNER_DP * resources.displayMetrics.density / viewport.pxPerPt
        return x > frame.left + frame.width - reach && y > frame.top + frame.height - reach
    }

    private fun editStickyText(index: Int, pageId: Long, note: StickyNoteItem) {
        val options = (tool as? CanvasTool.Sticky)?.options ?: NoteOptions()
        val inset = NoteOptions.STICKY_TEXT_INSET
        val size = options.textSize.toFloat()
        beginTextEdit(
            index,
            Frame(note.frame.left + inset, note.frame.top + inset, note.frame.width - 2 * inset, note.frame.height - 2 * inset),
            note.typedText,
            size,
            NoteOptions.TEXT_COLOR,
            options.style,
        ) { text ->
            val at = indexOfItem(pageId, note.id)
            val latest = editor?.content(pageId)?.takeIf { at >= 0 }?.get(at) as? StickyNoteItem
            if (latest != null && text != latest.typedText) setSticky(pageId, at, latest.withText(text, size, options.style))
        }
    }

    private fun editText(index: Int, pageId: Long, item: TextItem, isNew: Boolean) {
        beginTextEdit(index, item.frame, item.text, item.textSize, item.color, item.style) { text ->
            val editor = editor ?: return@beginTextEdit
            val content = editor.content(pageId)
            val at = indexOfItem(pageId, item.id)
            when {
                isNew && text.isNotEmpty() -> editor.perform(ItemsEdit.addOnTop(pageId, content, item.copy(text = text)))
                at >= 0 && text.isEmpty() -> editor.perform(ItemsEdit.remove(pageId, content, intArrayOf(at)))
                at >= 0 && text != item.text ->
                    editor.perform(ItemsEdit.replace(pageId, content, intArrayOf(at), listOf(item.copy(text = text))))
            }
            syncChanges()
        }
    }

    private fun editCell(index: Int, pageId: Long, table: TableItem, cell: Int) {
        if (cell < 0) return
        val columns = table.columnWeights.size
        val column = cell % columns
        val row = cell / columns
        val columnTotal = table.columnWeights.sum()
        val rowTotal = table.rowWeights.sum()
        val left = table.frame.left + table.columnWeights.take(column).sum() / columnTotal * table.frame.width
        val top = table.frame.top + table.rowWeights.take(row).sum() / rowTotal * table.frame.height
        val box = Frame(
            left + CELL_INSET_PT,
            top + CELL_INSET_PT,
            table.columnWeights[column] / columnTotal * table.frame.width - 2 * CELL_INSET_PT,
            table.rowWeights[row] / rowTotal * table.frame.height - 2 * CELL_INSET_PT,
        )
        beginTextEdit(index, box, table.cells[cell], table.textSize, NoteOptions.TEXT_COLOR, TextStyle()) { text ->
            val editor = editor ?: return@beginTextEdit
            val at = indexOfItem(pageId, table.id)
            val latest = editor.content(pageId).takeIf { at >= 0 }?.get(at) as? TableItem
            if (latest != null && text != latest.cells[cell]) {
                editor.perform(ItemsEdit.replace(pageId, editor.content(pageId), intArrayOf(at), listOf(latest.withCell(cell, text))))
                syncChanges()
            }
        }
    }

    private fun beginTextEdit(
        index: Int,
        box: Frame,
        initial: String,
        size: Float,
        color: Int,
        style: TextStyle,
        commit: (String) -> Unit,
    ) {
        val layout = layout ?: return
        val scale = viewport.pxPerPt
        val request = TextEditRequest(
            initial,
            viewport.toScreenX(layout.originX(index) + box.left),
            viewport.toScreenY(layout.originY(index) + box.top),
            box.width * scale,
            box.height * scale,
            size * scale,
            color,
            style,
            commit,
        )
        textEdit = request
        onTextEdit(request)
    }

    // ---- Pages ----

    val pages get() = editor?.document?.pages.orEmpty()

    val canAddPages get() = editor?.document?.kind != DocumentKind.CANVAS && editor != null

    /** Adds a page like the one at [after], right behind it. */
    fun addPage(after: Int) {
        val open = editor ?: return
        val like = open.document.pages.getOrNull(after) ?: return
        open.perform(InsertPage(after + 1, Page(IdSource.Random.next(), like.size, like.background)))
        syncChanges()
    }

    fun deletePage(index: Int) {
        val open = editor ?: return
        val page = open.document.pages.getOrNull(index) ?: return
        if (open.document.pages.size < 2) return
        lasso.clear()
        open.perform(RemovePage(index, page))
        syncChanges()
    }

    fun movePage(from: Int, to: Int) {
        val open = editor ?: return
        if (from !in open.document.pages.indices || to !in open.document.pages.indices) return
        lasso.clear()
        open.perform(MovePage(from, to))
        syncChanges()
    }

    /** A picture of a page, at most [maxPx] wide or tall. Null if the page has no edges to fit. */
    fun thumbnail(pageId: Long, maxPx: Int): Bitmap? {
        val open = editor ?: return null
        val paint = painter ?: return null
        val page = open.document.pages.firstOrNull { it.id == pageId } ?: return null
        val size = page.size ?: return null
        open.content(page.id)
        val scale = maxPx / maxOf(size.widthPt, size.heightPt)
        val bitmap = createBitmap((size.widthPt * scale).roundToInt().coerceAtLeast(1), (size.heightPt * scale).roundToInt().coerceAtLeast(1))
        val drawing = Canvas(bitmap)
        drawing.scale(scale, scale)
        paint.paintPage(drawing, page, Bounds(0f, 0f, size.widthPt, size.heightPt), scale)
        return bitmap
    }

    // ---- Recordings ----

    /** The file an asset lives in, or null with nothing open. */
    fun assetFile(assetId: String): File? = editor?.assetFile(assetId)

    /** Every recording in the document, loading each page to look. */
    fun recordings(): List<AudioItem> {
        val open = editor ?: return emptyList()
        val found = ArrayList<AudioItem>()
        for (page in open.document.pages) {
            val content = open.content(page.id)
            for (i in 0 until content.size) (content[i] as? AudioItem)?.let { found += it }
        }
        return found
    }

    /** A recording goes on the page in view, but has no place on it, as it shows only in the recorder's menu. */
    fun addAudio(assetId: String, durationMs: Long) {
        val open = editor ?: return
        val page = open.document.pages.getOrNull(currentPage) ?: return
        val item = AudioItem(IdSource.Random.next(), Frame(0f, 0f, 0f, 0f), assetId, durationMs)
        open.perform(ItemsEdit.addOnTop(page.id, open.content(page.id), item))
    }

    fun importAudio(uri: Uri, failed: () -> Unit) {
        val open = editor ?: return
        open.importAsset(context.contentResolver, uri) { assetId ->
            if (assetId == null) {
                failed()
            } else if (editor === open) {
                addAudio(assetId, audioDurationMs(open.assetFile(assetId)))
            }
        }
    }

    private fun audioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }

    // ---- Recordings on the page ----

    /** Plays and stops the recordings on the page; set by the editor. */
    internal var audio: AudioPlayback? = null

    private val audioBars = AudioBarPainter()
    private enum class AudioTouch { NONE, PENDING, SEEKING, MOVING }

    private var audioPointer = -1
    private var audioPage = 0
    private var audioItem: AudioItem? = null
    private var audioZone = AudioBar.Zone.PLAY
    private var audioTouch = AudioTouch.NONE
    private var audioDownX = 0f
    private var audioDownY = 0f
    private var audioPenX = 0f
    private var audioPenY = 0f
    private val audioHold = Runnable {
        if (audioTouch == AudioTouch.PENDING) {
            audioTouch = AudioTouch.MOVING
            overlay.invalidate()
        }
    }

    private fun drawAudioBars(canvas: Canvas) {
        val open = editor ?: return
        val layout = layout ?: return
        val playback = audio
        val scale = viewport.pxPerPt
        val view = viewport.visibleWorld(width, height)
        var playing = false
        layout.forEachPageIn(view) { index ->
            val page = open.document.pages.getOrNull(index) ?: return@forEachPageIn
            val content = open.loadedPageOrNull(page.id) ?: return@forEachPageIn
            val ox = layout.originX(index)
            val oy = layout.originY(index)
            val region = Bounds(view.left - ox, view.top - oy, view.right - ox, view.bottom - oy)
            content.forEachIntersecting(region) { _, item ->
                if (item is AudioItem && AudioBar.isPlaced(item.frame)) {
                    val isPlaying = playback?.isPlaying(item.id) == true
                    playing = playing || isPlaying
                    val wave = Waveforms.get(item.assetId)
                    if (wave == null && AudioBar.isOpen(item.frame)) {
                        open.assetFile(item.assetId)?.let { file -> Waveforms.request(item.assetId, file) { overlay.postInvalidate() } }
                    }
                    val carried = audioTouch == AudioTouch.MOVING && audioItem?.id == item.id
                    audioBars.draw(
                        canvas,
                        item,
                        if (carried) audioPenX - item.frame.width * scale / 2f else viewport.toScreenX(ox + item.frame.left),
                        if (carried) audioPenY - item.frame.height * scale / 2f else viewport.toScreenY(oy + item.frame.top),
                        scale,
                        isPlaying,
                        if (isPlaying) playback?.fraction(item.id) ?: 0f else 0f,
                        wave,
                        android.graphics.Color.luminance(backdropColor) < 0.5f,
                    )
                }
            }
        }
        if (playing) overlay.postInvalidateOnAnimation()
    }

    /**
     * A pen touch on a recording's bar. Returns true if it was on one. What it means waits for the pen: a
     * quick lift presses the control under it, holding still picks the bar up to move it, and dragging along
     * the waveform seeks.
     */
    private fun pressAudio(pointerId: Int, pageIndex: Int, pageId: Long, x: Float, y: Float, screenX: Float, screenY: Float): Boolean {
        if (tool is CanvasTool.Eraser || tool is CanvasTool.Lasso) return false
        val (_, item) = topItemOfType<AudioItem>(pageId, x, y) ?: return false
        if (!AudioBar.isPlaced(item.frame) || audio == null) return false
        audioZone = AudioBar.zoneAt(item.frame, x, y) ?: return false
        audioPointer = pointerId
        audioPage = pageIndex
        audioItem = item
        audioTouch = AudioTouch.PENDING
        audioDownX = screenX
        audioDownY = screenY
        audioPenX = screenX
        audioPenY = screenY
        postDelayed(audioHold, AUDIO_HOLD_MS)
        return true
    }

    private fun moveAudio(x: Float, y: Float) {
        val item = audioItem ?: return
        audioPenX = x
        audioPenY = y
        when (audioTouch) {
            AudioTouch.PENDING -> {
                val slop = HOLD_SLOP_DP * resources.displayMetrics.density
                if (hypot(x - audioDownX, y - audioDownY) > slop) {
                    removeCallbacks(audioHold)
                    if (audioZone == AudioBar.Zone.WAVE) {
                        audioTouch = AudioTouch.SEEKING
                    } else {
                        audioTouch = AudioTouch.NONE
                    }
                }
            }
            AudioTouch.SEEKING -> audio?.seek(item, AudioBar.fractionAt(item.frame, pageX(x, audioPage)))
            AudioTouch.MOVING -> Unit
            AudioTouch.NONE -> Unit
        }
        overlay.invalidate()
    }

    private fun endAudio(cancelled: Boolean) {
        removeCallbacks(audioHold)
        val item = audioItem
        val touch = audioTouch
        audioPointer = -1
        audioTouch = AudioTouch.NONE
        audioItem = null
        if (item != null && !cancelled) {
            when (touch) {
                AudioTouch.PENDING -> tapAudio(item)
                AudioTouch.MOVING -> placeAudio(item.id, audioPenX, audioPenY)
                else -> Unit
            }
        }
        overlay.invalidate()
    }

    private fun tapAudio(item: AudioItem) {
        val playback = audio ?: return
        val pageId = editor?.document?.pages?.getOrNull(audioPage)?.id ?: return
        val at = indexOfItem(pageId, item.id)
        if (at < 0) return
        when (audioZone) {
            AudioBar.Zone.PLAY -> {
                // Pressing play on a collapsed bar opens it.
                if (!AudioBar.isOpen(item.frame)) replaceAudio(pageId, at, item.copy(frame = AudioBar.withOpen(item.frame, true)))
                playback.toggle(item)
            }
            AudioBar.Zone.COLLAPSE -> replaceAudio(pageId, at, item.copy(frame = AudioBar.withOpen(item.frame, false)))
            AudioBar.Zone.EXPAND -> replaceAudio(pageId, at, item.copy(frame = AudioBar.withOpen(item.frame, true)))
            AudioBar.Zone.WAVE -> playback.seek(item, AudioBar.fractionAt(item.frame, audioDownX.let { pageX(it, audioPage) }))
        }
    }

    private fun replaceAudio(pageId: Long, index: Int, item: AudioItem) {
        val open = editor ?: return
        open.perform(ItemsEdit.replace(pageId, open.content(pageId), intArrayOf(index), listOf(item)))
        syncChanges()
    }

    /**
     * Puts a recording on the page under a point of the view, open and centred there. It moves if it was
     * already placed. Returns false if the point is not over a page.
     */
    fun placeAudio(id: Long, screenX: Float, screenY: Float): Boolean {
        val open = editor ?: return false
        val layout = layout ?: return false
        val target = layout.pageIndexAt(viewport.toWorldX(screenX), viewport.toWorldY(screenY))
        if (target < 0) return false
        val targetPage = open.document.pages[target]
        var fromPage = 0L
        var at = -1
        var item: AudioItem? = null
        for (page in open.document.pages) {
            val content = open.content(page.id)
            for (i in 0 until content.size) {
                val candidate = content[i]
                if (candidate.id == id && candidate is AudioItem) {
                    fromPage = page.id
                    at = i
                    item = candidate
                }
            }
        }
        val found = item ?: return false
        val wasCollapsed = AudioBar.isPlaced(found.frame) && !AudioBar.isOpen(found.frame)
        val moved = found.copy(frame = AudioBar.centeredAt(pageX(screenX, target), pageY(screenY, target), open = !wasCollapsed))
        if (fromPage == targetPage.id) {
            replaceAudio(fromPage, at, moved)
        } else {
            open.perform(ItemsEdit.remove(fromPage, open.content(fromPage), intArrayOf(at)))
            open.perform(ItemsEdit.addOnTop(targetPage.id, open.content(targetPage.id), moved))
            syncChanges()
        }
        return true
    }

    fun deleteAudio(item: AudioItem) {
        val open = editor ?: return
        for (page in open.document.pages) {
            val content = open.content(page.id)
            for (i in 0 until content.size) {
                if (content[i].id == item.id) {
                    open.perform(ItemsEdit.remove(page.id, content, intArrayOf(i)))
                    return
                }
            }
        }
    }

    /** Copies a picture into the document and puts it in the middle of the view. */
    fun importImage(uri: Uri, options: ImageOptions, failed: () -> Unit) {
        val open = editor ?: return
        open.importImage(context.contentResolver, uri) { imported ->
            if (imported == null) {
                failed()
            } else if (editor === open) {
                placeInView { pageId, x, y, maxWidth, maxHeight ->
                    options.newImage(imported.assetId, imported.width, imported.height, x, y, maxWidth, maxHeight)
                        .also {
                            open.perform(ItemsEdit.addOnTop(pageId, open.content(pageId), it))
                            selectLast(pageId)
                        }
                }
            }
        }
    }

    /** Selects the item just put on top of a page, so that it can be moved or resized at once. */
    private fun selectLast(pageId: Long) {
        val open = editor ?: return
        val index = open.document.pages.indexOfFirst { it.id == pageId }
        val content = open.content(pageId)
        if (index < 0 || content.size == 0) return
        val at = content.size - 1
        lasso.select(index, Selection(pageId, intArrayOf(at), listOf(content[at])))
        onLassoRequested()
    }

    /** Copies a video into the document and puts it in the middle of the view. */
    fun importVideo(uri: Uri, failed: () -> Unit) {
        val open = editor ?: return
        open.importVideo(context.contentResolver, uri) { imported ->
            if (imported == null) {
                failed()
            } else if (editor === open) {
                placeInView { pageId, x, y, maxWidth, maxHeight ->
                    val fit = minOf(maxWidth / imported.width, maxHeight / imported.height)
                    val width = imported.width * fit
                    val height = imported.height * fit
                    val item = VideoItem(
                        IdSource.Random.next(),
                        Frame(x - width / 2f, y - height / 2f, width, height),
                        imported.assetId,
                        imported.posterAssetId,
                        imported.durationMs,
                    )
                    open.perform(ItemsEdit.addOnTop(pageId, open.content(pageId), item))
                }
            }
        }
    }

    /** Puts pasted text in the middle of the view. */
    fun pasteText(text: String, options: NoteOptions) {
        val open = editor ?: return
        placeInView { pageId, x, y, maxWidth, _ ->
            val width = minOf(maxWidth, NEW_TEXT_WIDTH_PT)
            val item = TextItem(
                IdSource.Random.next(),
                Frame(x - width / 2f, y, width, NEW_TEXT_HEIGHT_PT),
                text,
                options.textSize.toFloat(),
                NoteOptions.TEXT_COLOR,
                options.style,
            )
            open.perform(ItemsEdit.addOnTop(pageId, open.content(pageId), item))
        }
    }

    private inline fun placeInView(place: (pageId: Long, x: Float, y: Float, maxWidth: Float, maxHeight: Float) -> Unit) {
        val open = editor ?: return
        val layout = layout ?: return
        val index = currentPage
        val page = open.document.pages.getOrNull(index) ?: return
        val world = viewport.visibleWorld(width, height)
        place(
            page.id,
            world.centerX - layout.originX(index),
            world.centerY - layout.originY(index),
            world.width * PLACED_SHARE_OF_VIEW,
            world.height * PLACED_SHARE_OF_VIEW,
        )
        syncChanges()
    }

    /** Finishes any text being typed. */
    fun endTextEdit() {
        val request = textEdit ?: return
        textEdit = null
        request.commitNow()
        onTextEdit(null)
    }

    private fun pageX(screenX: Float, index: Int) =
        if (drawingInLoupe) loupe.pageX(screenX) else viewport.toWorldX(screenX) - (layout?.originX(index) ?: 0f)

    private fun pageY(screenY: Float, index: Int) =
        if (drawingInLoupe) loupe.pageY(screenY) else viewport.toWorldY(screenY) - (layout?.originY(index) ?: 0f)

    /** The pen on the loupe's box, away from anything else, carries the box along. */
    private fun startDraggingLoupeBox(pointerId: Int, x: Float, y: Float): Boolean {
        val layout = layout ?: return false
        val pageX = viewport.toWorldX(x) - layout.originX(loupe.pageIndex)
        val pageY = viewport.toWorldY(y) - layout.originY(loupe.pageIndex)
        if (!loupe.box.contains(pageX, pageY)) return false
        loupeBoxPointer = pointerId
        loupeBoxGrabX = pageX - loupe.box.left
        loupeBoxGrabY = pageY - loupe.box.top
        return true
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
        if (drawingInLoupe) {
            loupe.screenToPage(screenToPage)
        } else {
            screenToPage.setTranslate(-viewport.panX, -viewport.panY)
            screenToPage.postScale(1f / scale, 1f / scale)
            screenToPage.postTranslate(-layout.originX(drawPageIndex), -layout.originY(drawPageIndex))
        }
        updateWetMask(layout, drawPageIndex)
        if (strokeEdge != Ruler.NO_EDGE) {
            startStrokeAlongRuler(event, editor, page.id)
            return
        }
        val steady = stabilizer
        val id = if (steady != null) {
            strokeStartMs = event.eventTime
            screenToPage.invert(pageToScreen)
            val index = event.findPointerIndex(drawPointerId).coerceAtLeast(0)
            wet.startStroke(
                stabilizedInput(event.getX(index), event.getY(index), event.eventTime, event.getPressure(index)),
                InkBrushes.brush(drawStyle),
                pageToScreen,
            )
        } else {
            wet.startStroke(event, drawPointerId, InkBrushes.brush(drawStyle), screenToPage)
        }
        pendingStrokes[id] = PendingStroke(editor, page.id, drawStyle, strokeStickyId, drawScribble, drawCircle)
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
        if (stabilizer != null) {
            addStabilized(event, id)
            return
        }
        val predicted = if (predictInk) predictor.predict() else null
        wet.addToStroke(event, drawPointerId, id, predicted)
        predicted?.recycle()
    }

    // ---- Stabilization ----

    /** The pen's position, run through the stabilizer and mapped onto the page. */
    private fun stabilizedInput(x: Float, y: Float, timeMs: Long, pressure: Float): StrokeInput {
        val at = floatArrayOf(x, y)
        screenToPage.mapPoints(at)
        stabilizer?.next(at[0], at[1], timeMs, stabilized)
        return StrokeInput().apply {
            update(stabilized[0], stabilized[1], (timeMs - strokeStartMs).coerceAtLeast(0L), InputToolType.STYLUS, STROKE_UNIT_CM, pressure)
        }
    }

    /** The pen where it really is, to end a stabilized stroke exactly where the pen lifted. */
    private fun rawInput(event: MotionEvent, index: Int, timeMs: Long): StrokeInput {
        val at = floatArrayOf(event.getX(index), event.getY(index))
        screenToPage.mapPoints(at)
        return StrokeInput().apply {
            update(at[0], at[1], (timeMs - strokeStartMs).coerceAtLeast(1L), InputToolType.STYLUS, STROKE_UNIT_CM, event.getPressure(index))
        }
    }

    private fun addStabilized(event: MotionEvent, id: InProgressStrokeId) {
        val index = event.findPointerIndex(drawPointerId)
        if (index < 0) return
        val batch = MutableStrokeInputBatch()
        for (h in 0 until event.historySize) {
            batch.add(
                stabilizedInput(
                    event.getHistoricalX(index, h),
                    event.getHistoricalY(index, h),
                    event.getHistoricalEventTime(h),
                    event.getHistoricalPressure(index, h),
                ),
            )
        }
        batch.add(stabilizedInput(event.getX(index), event.getY(index), event.eventTime, event.getPressure(index)))
        wet.addToStroke(batch, id, ImmutableStrokeInputBatch.EMPTY)
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
        pendingStrokes[id] = PendingStroke(editor, pageId, drawStyle, strokeStickyId, drawScribble, drawCircle)
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
        stabilizer = null
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
        erase?.eraseTo(pageX(x, drawPageIndex), pageY(y, drawPageIndex))
        redrawChanges(drawPageIndex)
    }

    /**
     * Hides wet ink past the page's edge, where the finished stroke will be clipped anyway, and under
     * the views on top of this one.
     */
    private fun updateWetMask(layout: DocumentLayout, index: Int) {
        val size = editor?.document?.pages?.get(index)?.size
        val page = if (drawingInLoupe) RectF(loupe.area) else size?.let {
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
            val stickyId = pending.stickyId
            if (stickyId != null && addToSticky(editor, pending.pageId, stickyId, item)) continue
            lastLoop = null
            if (pending.scribbleToErase && StrokeGestures.isScribble(item.points)) {
                scribbleOut(editor, pending.pageId, item)
                continue
            }
            editor.strokes.put(item, stroke)
            editor.perform(ItemsEdit.addOnTop(pending.pageId, editor.content(pending.pageId), item))
            if (pending.circleToLasso && StrokeGestures.isClosedLoop(item.points)) lastLoop = LoopMark(pending.pageId, item)
        }
        syncChanges()
        wet.removeFinishedStrokes(strokes.keys)
    }

    /** Erases whole strokes and shapes along a scribble. The scribble itself is not kept. */
    private fun scribbleOut(editor: NoteEditor, pageId: Long, scribble: StrokeItem) {
        val content = editor.content(pageId)
        val points = scribble.points
        val session = EraseSession(
            pageId,
            content,
            EraseMode.WHOLE,
            scribble.style.width / 2f + SCRIBBLE_REACH_PT,
            kinds = DEFAULT_ERASABLE,
        )
        for (i in 0 until points.size) session.eraseTo(points.x(i), points.y(i))
        session.finish()?.let { editor.record(it) }
        redrawChanges(editor.document.pages.indexOfFirst { it.id == pageId })
    }

    /** Touching the last circle's line selects what it surrounds, and hands the canvas over to the lasso. */
    private fun selectLoopAt(pageIndex: Int, pageId: Long, x: Float, y: Float, byFinger: Boolean = false): Boolean {
        val loop = lastLoop ?: return false
        val editor = editor ?: return false
        if (loop.pageId != pageId) return false
        // A finger is blunter than the pen, so it may also touch inside the circle.
        val reachDp = if (byFinger) FINGER_LOOP_REACH_DP else LOOP_TAP_REACH_DP
        val reach = reachDp * resources.displayMetrics.density / viewport.pxPerPt
        val touched = StrokeGestures.isNearLine(loop.item.points, x, y, reach) ||
            byFinger && StrokeGestures.isInside(loop.item.points, x, y)
        if (!touched) return false
        lastLoop = null
        // The circle itself is the lasso: its line is taken off the page and what it surrounds is selected.
        val content = editor.content(pageId)
        val own = (0 until content.size).firstOrNull { content[it].id == loop.item.id } ?: return false
        val inside = selectWithLasso(pageId, content, StrokeGestures.polygon(loop.item.points), ALL_ITEM_KINDS, false)
        val picked = inside.indices.indices.filter { inside.indices[it] != own }
        if (picked.isEmpty()) return false
        editor.perform(ItemsEdit.remove(pageId, content, intArrayOf(own)))
        redrawChanges(pageIndex)
        // Everything above the removed line moves down one place.
        val indices = picked.map { inside.indices[it].let { index -> if (index > own) index - 1 else index } }
        val items = picked.map { inside.items[it] }
        // The circle turns into marching ants before the selection takes over.
        val chosen = Selection(pageId, indices.toIntArray(), items)
        lasso.selectAfterAnts(pageIndex, StrokeGestures.polygon(loop.item.points), chosen) {
            onLassoRequested()
            syncChanges()
        }
        return true
    }

    // ---- Navigation ----

    override fun onTransform(dx: Float, dy: Float, scale: Float, focusX: Float, focusY: Float) {
        viewport.panX += dx
        viewport.panY += dy
        if (scale != 1f) {
            pinchedThisGesture = true
            editor?.pdf?.pinching = true
            viewport.zoomTo(viewport.zoom * scale, focusX, focusY)
            onZoomChanged(viewport.zoom)
        }
        clampPan()
        tiles.invalidate()
        onViewMoved()
        overlay.invalidate()
    }

    override fun onTransformEnd() {
        if (!pinchedThisGesture && fling()) return
        settlePan()
    }

    override fun onUndo() = undo()

    override fun onRedo() = redo()

    override fun onZoomTap(x: Float, y: Float) {
        val target = if (abs(viewport.zoom - 1f) > ZOOM_TAP_NEAR_REAL_SIZE) 1f else ZOOM_TAP_LEVEL
        viewport.zoomTo(target, x, y)
        clampPan()
        settlePan()
        onZoomChanged(viewport.zoom)
    }

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
        if (editor?.pdf?.endPinch() == true) tiles.invalidateAll()
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
        val saved = if (continueFromLastPage) editor?.lastPageIndex() ?: -1 else -1
        layout.moveToPage(viewport, saved.coerceAtLeast(0), width, height, pageMarginPx)
        settlePan()
        hasPlacedPage = true
    }

    /** Brings the screen up to date with whatever has changed on the pages. */
    private fun syncChanges() {
        val editor = editor ?: return
        overlay.invalidate()
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
        if (loupeOpen) loupe.invalidate()
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

    /** New PDF pixels for a point on a page have arrived, which its tile is still waiting for. */
    private fun redrawPageRegion(pageId: Long, region: Bounds) {
        val editor = editor ?: return
        val layout = layout ?: return
        val index = editor.document.indexOfPage(pageId)
        if (index < 0) return
        val x = layout.originX(index)
        val y = layout.originY(index)
        tiles.invalidateRegion(Bounds(region.left + x, region.top + y, region.right + x, region.bottom + y))
    }

    private fun paintTile(canvas: Canvas, region: Bounds, leftPx: Double, topPx: Double, scale: Float) {
        val layout = layout ?: return
        editor?.pdf?.setFocus(viewport.toWorldX(width / 2f), viewport.toWorldY(height / 2f))
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
        const val AUDIO_HOLD_MS = 400L
        const val FINGER_TAP_MS = 300L
        const val MIN_HOLD_CHECK_MS = 8L
        const val PLACED_SHARE_OF_VIEW = 0.6f
        const val EMOJI_BOX_OF_SIZE = 1.5f
        const val VIDEO_BUTTON_REACH_PT = 24f
        const val LOUPE_BOX_SHARE = 0.45f
        const val LOUPE_STEP_SHARE = 0.8f
        const val LOUPE_BOX_COLOR = 0xFF4A9BF5.toInt()
        const val LOUPE_BOX_WIDTH_DP = 1.2f
        const val NEW_TEXT_WIDTH_PT = 200f
        const val NEW_TEXT_HEIGHT_PT = 60f
        const val CELL_INSET_PT = 3f
        const val STICKY_CORNER_DP = 40f
        const val MIN_AREA_PT = 30f
        const val AREA_EDGE_COLOR = 0xFF4A9BF5.toInt()
        const val AREA_FILL_COLOR = 0x224A9BF5

        /** Long enough to cross the screen at any angle, wherever two fingers carry it. */
        const val RULER_LENGTH_OF_DIAGONAL = 3f
        const val RULER_THICKNESS_DP = 140f
        const val RULER_GRAB_MARGIN_DP = 12f

        /** How close to a ruler's edge a stroke must start to run along it. */
        const val RULER_EDGE_REACH_DP = 14f
        const val STRAIGHT_LINE_PRESSURE = 0.5f
        const val MINIMAP_MAX_ITEMS = 400

        /** A fifth of zooming out shows a quarter more of the page, which is that much growth over the view's size. */
        const val MINIMAP_ZOOM_OUT = 0.25f
        const val SCRIBBLE_REACH_PT = 2f
        const val LOOP_TAP_REACH_DP = 14f
        const val FINGER_LOOP_REACH_DP = 28f
        /** What one press of a zoom shortcut multiplies the zoom by. */
        const val ZOOM_STEP = 1.25f

        /** What a double tap zooms to from real size. From anything else it goes back to real size. */
        const val ZOOM_TAP_LEVEL = 2f
        const val ZOOM_TAP_NEAR_REAL_SIZE = 0.01f

        /** Pages kept in memory on each side of the view, so that turning back finds them ready. */
        const val NEARBY_PAGES = 2

        /** What the eraser end of a stylus does when the tool in hand is not the eraser. */
        val DEFAULT_ERASER = CanvasTool.Eraser(EraseMode.PARTIAL, radiusPt = 6f)
    }
}

/**
 * The screen's real density, which zoom 1 relies on to show true size. It reads the panel's values
 * rather than densityDpi, which is a rounded size for the UI. Some devices report these wrongly, so
 * [AppSettings.calibration] can replace it.
 */
internal fun physicalDpi(context: Context): Float {
    val metrics = context.resources.displayMetrics
    return (metrics.xdpi + metrics.ydpi) / 2f
}
