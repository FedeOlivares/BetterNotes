package app.betternotes.canvas

import app.betternotes.core.edit.LayerOps
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import app.betternotes.pdf.PdfThumbnails
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.ink.authoring.InProgressStrokeId
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.InkTool
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.input.motionprediction.MotionEventPredictor
import app.betternotes.core.template.CoverDesign
import app.betternotes.core.template.toItems
import app.betternotes.core.edit.BatchEdit
import app.betternotes.core.template.PageBackground
import app.betternotes.core.edit.InsertPage
import app.betternotes.core.model.PageSource
import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.EditTarget
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.edit.StepKind
import app.betternotes.core.geometry.TurnedBox
import app.betternotes.core.edit.MovePage
import app.betternotes.core.edit.RemovePage
import app.betternotes.core.edit.SetCover
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.gesture.GestureListener
import app.betternotes.core.gesture.GestureRecognizer
import app.betternotes.core.gesture.HoldDetector
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.gesture.TwoFingerAction
import app.betternotes.core.gesture.snappedRotation
import app.betternotes.core.gesture.RotationStep
import app.betternotes.core.gesture.RotationGesture
import app.betternotes.core.gesture.PointerKind
import app.betternotes.core.layout.DocumentLayout
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.audio.Waveforms
import app.betternotes.core.model.AudioBar
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.FillItem
import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.withId
import app.betternotes.core.model.withGroup
import app.betternotes.core.edit.Grouping
import app.betternotes.core.model.withLocked
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
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.planner.PlannerAction
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.planner.DateStroke
import app.betternotes.core.planner.PlannerColors
import app.betternotes.core.planner.PlannerPage
import app.betternotes.core.planner.PlannerPages
import app.betternotes.core.planner.PlannerPlan
import app.betternotes.core.planner.PlannerSetup
import app.betternotes.core.planner.plannerActionAt
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import app.betternotes.core.model.SpanLook
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextSpan
import app.betternotes.core.model.TextSpans
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VideoItem
import app.betternotes.core.model.Document
import app.betternotes.core.search.TextMatch
import app.betternotes.search.MlKitRecognizer
import app.betternotes.search.WordStroke
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.settings.StylusAction
import app.betternotes.core.settings.StylusButtonListener
import app.betternotes.core.settings.StylusButtons
import app.betternotes.core.settings.StylusEraserEnd
import app.betternotes.core.tools.EraseMode
import app.betternotes.core.tools.FittedShape
import app.betternotes.core.tools.ShapeFit
import app.betternotes.core.tools.TextBlocks
import app.betternotes.core.tools.DEFAULT_ERASABLE
import app.betternotes.core.tools.EraseSession
import app.betternotes.core.tools.Erasing
import app.betternotes.core.tools.NoteEraseSession
import app.betternotes.core.tools.ALL_ITEM_KINDS
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.Selection
import app.betternotes.core.tools.isCoverBackground
import app.betternotes.core.tools.Stabilizer
import app.betternotes.core.tools.StrokeGestures
import app.betternotes.core.tools.StrokeSmoothing
import app.betternotes.core.tools.NoteFold
import app.betternotes.core.tools.NoteScope
import app.betternotes.core.tools.selectWithLasso
import app.betternotes.core.tools.clearEdit
import app.betternotes.core.tools.Ruler
import app.betternotes.core.tools.ImageOptions
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.core.tools.AngleGuide
import app.betternotes.core.tools.TapeLineMode
import app.betternotes.core.tools.TapeOptions
import app.betternotes.core.tools.cellAt
import app.betternotes.core.tools.typedText
import app.betternotes.core.tools.withCell
import app.betternotes.core.tools.withText
import app.betternotes.ui.theme.CanvasColors
import app.betternotes.ui.theme.DarkEditorColors
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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
        val stabilization: Int = 0,
    ) : CanvasTool
    data class Eraser(
        val mode: EraseMode,
        val radiusPt: Float,
        val kinds: Set<ItemKind> = DEFAULT_ERASABLE,
        val lockedToo: Boolean = false,
    ) : CanvasTool

    /** Shows where the pen is, with a fading tail, and leaves nothing on the page. */
    data object Laser : CanvasTool

    /** Selects items and moves or resizes the selection. */
    data class Lasso(
        val rectangle: Boolean,
        val kinds: Set<ItemKind>,
        val fullOnly: Boolean,
        val anyPortion: Boolean = false,
        val lockedToo: Boolean = false,
    ) : CanvasTool

    /** Drags out a new shape. */
    data class Shape(val options: ShapeOptions) : CanvasTool

    /** Lays a strip of tape between where the pen lands and lifts. */
    data class Tape(val options: TapeOptions) : CanvasTool

    /** The next touch hands its place on the screen to [onPick], which takes the color under it. */
    data class Pick(val onPick: (x: Float, y: Float) -> Unit) : CanvasTool

    /** Fills the closed region the pen lands in with a color, as one vector item that goes under everything else on the page. */
    data class Fill(val color: Int, val opacity: Float, val pattern: FillPattern = FillPattern.SOLID) : CanvasTool

    /** Puts a saved sticker, its items with the top left corner at the origin, where the pen lands. [assetFile] finds the files of its pictures. */
    data class Sticker(val items: List<PageItem>, val assetFile: (String) -> File?) : CanvasTool

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
    initial: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    textSizePx: Float,
    color: Int,
    style: TextStyle,
    sizePt: Float,
    spans: List<TextSpan>,
    private val commit: (String, TextStyle, Float, List<TextSpan>, Float, Int) -> Unit,
    /** How far the page is turned, in degrees clockwise, about ([pivotX], [pivotY]) on the screen. */
    turnDegrees: Float = 0f,
    pivotX: Float = 0f,
    pivotY: Float = 0f,
) {
    // The box follows the page as it is panned and zoomed, so these change while it is shown.
    var left by mutableFloatStateOf(left)
        private set
    var top by mutableFloatStateOf(top)
        private set
    var width by mutableFloatStateOf(width)
        private set
    var height by mutableFloatStateOf(height)
        private set
    var textSizePx by mutableFloatStateOf(textSizePx)
        private set

    /** The color of the text, which the toolbar can change while the box is open. */
    var color by mutableIntStateOf(color)
        private set

    /** The color of what is typed next, when it is not the color of the text so far; null when it is. */
    var typingColor: Int? = null

    /** The color of the first selected letter, or of the whole text when nothing is selected. */
    fun shownColor(): Int {
        if (!hasSelection) return typingColor ?: color
        return TextSpans.looks(spans, text.length)[selectionStart].color ?: color
    }

    /** Whether the whole text is selected, so that a change of the selection is a change of the text. */
    val selectionIsAll get() = hasSelection && selectionStart == 0 && selectionEnd == text.length

    /** Makes all of the text [value], and drops the colors that single letters had. */
    fun recolorAll(value: Int) {
        color = value
        typingColor = null
        spans = TextSpans.change(spans, text.length, 0, text.length) { it.copy(color = null) }
    }

    /** Gives the selected letters [value] as their color. */
    fun colorSelection(value: Int) = styleSelection { it.copy(color = value) }

    /** Gives the letters just typed, which [TextSpans.inserted] finds from the text before, the color of what is typed next. */
    fun colorTyped(before: String) {
        val chosen = typingColor ?: return
        val range = TextSpans.inserted(before, text) ?: return
        spans = TextSpans.change(spans, text.length, range.first, range.last + 1) { it.copy(color = chosen.takeIf { c -> c != color }) }
    }

    /** The look of the text, which the toolbar can change while the box is open. */
    var style by mutableStateOf(style)
        private set
    var sizePt = sizePt
        private set

    /** The parts of the text that look different from the rest, kept in step with what is typed. */
    var spans by mutableStateOf(spans)

    /** What is selected in the text, as the characters from [selectionStart] up to [selectionEnd]. */
    var selectionStart by mutableIntStateOf(0)
    var selectionEnd by mutableIntStateOf(0)
    val hasSelection get() = selectionEnd > selectionStart && selectionEnd <= text.length

    /** Whether every selected letter looks as [pick] says it does, with the text's own look where a span says nothing. */
    fun selectionAll(pick: (SpanLook, TextStyle) -> Boolean): Boolean {
        val looks = TextSpans.looks(spans, text.length)
        return hasSelection && (selectionStart until selectionEnd).all { pick(looks[it], style) }
    }

    /** The size of the first selected letter, or of the whole text when nothing is selected. */
    fun shownSize(): Float {
        if (!hasSelection) return sizePt
        return TextSpans.looks(spans, text.length)[selectionStart].size ?: sizePt
    }

    /** Gives the selected letters a new look. A look the same as the text's own is stored as no look at all. */
    fun styleSelection(change: (SpanLook) -> SpanLook) {
        if (!hasSelection) return
        spans = TextSpans.change(spans, text.length, selectionStart, selectionEnd) { look ->
            val next = change(look)
            next.copy(
                bold = next.bold.takeIf { it != style.bold },
                italic = next.italic.takeIf { it != style.italic },
                underline = next.underline.takeIf { it != style.underline },
                strikethrough = next.strikethrough.takeIf { it != style.strikethrough },
                size = next.size.takeIf { it != sizePt },
                font = next.font.takeIf { it != style.font },
                color = next.color.takeIf { it != this.color },
            )
        }
    }
    var turnDegrees by mutableFloatStateOf(turnDegrees)
        private set
    var pivotX by mutableFloatStateOf(pivotX)
        private set
    var pivotY by mutableFloatStateOf(pivotY)
        private set

    /** What is typed so far. Handwriting that has been read is added to it from outside. */
    var text by mutableStateOf(initial)

    /** Where the end of the text sits in the box, which is where the next word goes, in pixels from the box's corner. */
    var endX by mutableFloatStateOf(0f)
    var endY by mutableFloatStateOf(0f)
    var lineHeight by mutableFloatStateOf(textSizePx)

    var commitNow: () -> Unit = {}

    /** Set by the box on screen: where character [index] of the text lies, as left, top, right, bottom in pixels from the box's corner, or null. */
    var charBox: (Int) -> FloatArray? = { null }

    /** Takes the characters at [indices] out of the text. The box on screen keeps the parts that look different in step. */
    fun deleteChars(indices: Set<Int>) {
        text = buildString { text.forEachIndexed { i, c -> if (i !in indices) append(c) } }
    }

    /** How tall the text is as laid out in the box, in pixels, which is more than the box when the text runs past it. */
    var contentHeightPx by mutableFloatStateOf(0f)

    fun finish(text: String) = commit(text, style, sizePt, spans, contentPt(), color)

    private fun contentPt() = if (textSizePx > 0f) contentHeightPx * sizePt / textSizePx else 0f

    /** Called when a block of writing is done, with the box as it is: the box is put on the page as it is, as a step of undo, and stays open. */
    var block: ((String, TextStyle, Float, List<TextSpan>, Float, Int) -> Unit)? = null

    /** Ends the block being written: what was typed, handwritten or changed since the last block is one step of undo. */
    fun finishBlock() {
        block?.invoke(text, style, sizePt, spans, contentPt(), color)
    }

    fun restyle(style: TextStyle, sizePt: Float) {
        this.style = style
        this.sizePt = sizePt
    }

    /** Adds [word] after what is there, with a space between if needed. */
    fun append(word: String) {
        text = if (text.isEmpty() || text.last().isWhitespace()) text + word else "$text $word"
    }

    fun moveTo(
        left: Float, top: Float, width: Float, height: Float, textSizePx: Float,
        turnDegrees: Float, pivotX: Float, pivotY: Float,
    ) {
        this.left = left
        this.top = top
        this.width = width
        this.height = height
        this.textSizePx = textSizePx
        this.turnDegrees = turnDegrees
        this.pivotX = pivotX
        this.pivotY = pivotY
    }
}

/** A request to play a video where it sits, in view pixels. */
class VideoPlayRequest(
    val file: File,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** How far the page is turned, in degrees clockwise, about ([pivotX], [pivotY]) on the screen. */
    val turnDegrees: Float = 0f,
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
)

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

    /** Told when a text box opens and handwriting cannot be read into it because the models are not on the device. */
    var onWritingModelsMissing: () -> Unit = {}

    /** Asks for the text tool, so that a text box tapped twice can be typed in. */
    var onTextToolRequested: () -> Unit = {}

    /** A touch away from the text being typed ends it, and the tool in hand before the text tool comes back. */
    var onTextToolLeft: () -> Unit = {}

    /** Asks for a text box. Null takes it away. */
    var onTextEdit: (TextEditRequest?) -> Unit = {}

    private var textEdit: TextEditRequest? = null
    private var textEditIndex = 0
    private var textEditBox = Frame(0f, 0f, 0f, 0f)
    private var textEditSize = 0f

    /** Gives the text box being typed a new look, as the toolbar's size and style buttons are used. */
    fun restyleTextEdit(style: TextStyle, sizePt: Float) {
        val request = textEdit ?: return
        textEditSize = sizePt
        request.restyle(style, sizePt)
        followTextEdit()
    }

    /** Puts the text box being typed in back over its place on the page, after the page was moved or zoomed. */
    private fun followTextEdit() {
        val request = textEdit ?: return
        val layout = layout ?: return
        val index = textEditIndex
        val scale = viewport.pxPerPt
        val box = textEditBox
        request.moveTo(
            viewport.toScreenX(layout.originX(index) + box.left),
            viewport.toScreenY(layout.originY(index) + box.top),
            box.width * scale,
            box.height * scale,
            textEditSize * scale,
            layout.turn(index),
            viewport.toScreenX(layout.centerX(index)),
            viewport.toScreenY(layout.centerY(index)),
        )
    }

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
        // The box keeps the window's proportions when the window is made taller or shorter.
        if (!loupe.box.isEmpty && loupe.area.width() > 0f && loupe.area.height() > 0f) {
            loupe.box.bottom = loupe.box.top + loupe.box.width() * loupe.area.height() / loupe.area.width()
        }
        loupe.invalidate()
    }

    /** Puts the loupe's box in the middle of the view. */
    fun recenterLoupe() {
        if (layout == null || width == 0 || loupe.area.isEmpty) return
        loupe.pageIndex = currentPage
        val world = viewport.visibleWorld(width, height)
        val boxWidth = world.width * LOUPE_BOX_SHARE
        val boxHeight = boxWidth * loupe.area.height() / loupe.area.width()
        val left = world.centerX - boxWidth / 2f
        // In the middle of what shows above the window, so the whole box can be seen and taken hold of.
        val centerY = viewport.toWorldY(loupe.area.top / 2f)
        val top = centerY - boxHeight / 2f
        loupe.box.set(left, top, left + boxWidth, top + boxHeight)
        overlay.invalidate()
        loupe.invalidate()
    }

    /** Moves the loupe's box along the line of writing, by its own width. At the page's edge it starts the next line. */
    fun stepLoupe(forward: Boolean) {
        val layout = layout ?: return
        val size = pages.getOrNull(loupe.pageIndex)?.size
        val step = loupe.box.width() * LOUPE_STEP_SHARE * if (forward) 1f else -1f
        loupe.box.offset(step, 0f)
        if (size != null) {
            val ox = layout.originX(loupe.pageIndex)
            val oy = layout.originY(loupe.pageIndex)
            if (forward && loupe.box.left - ox >= size.widthPt) {
                loupe.box.offsetTo(ox, loupe.box.top + loupe.box.height())
            } else if (!forward && loupe.box.right - ox <= 0f) {
                loupe.box.offsetTo(ox + (size.widthPt - loupe.box.width()).coerceAtLeast(0f), loupe.box.top - loupe.box.height())
            }
            if (oy.isNaN()) return
        }
        overlay.invalidate()
        loupe.invalidate()
    }

    private fun drawLoupeBox(canvas: Canvas) {
        if (!loupeOpen || layout == null || loupe.box.isEmpty) return
        canvas.drawRect(
            viewport.toScreenX(loupe.box.left),
            viewport.toScreenY(loupe.box.top),
            viewport.toScreenX(loupe.box.right),
            viewport.toScreenY(loupe.box.bottom),
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

    /** How far down the scrollable range the view is, 0 at the top and 1 at the bottom, or null if it cannot scroll. */
    val scrollFraction: Float?
        get() {
            val layout = layout ?: return null
            if (layout.isInfinite || width == 0) return null
            val scale = viewport.pxPerPt
            val max = layout.maxPanY(scale, height, pageMarginPx)
            val min = layout.minPanY(scale, height, pageMarginPx)
            return if (max - min < 1f) null else ((max - viewport.panY) / (max - min)).coerceIn(0f, 1f)
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

    /** Moves the view to [fraction] of the scrollable range, as a scrubber does. [last] lets the page settle sharp. */
    fun scrollToFraction(fraction: Float, last: Boolean) {
        val layout = layout ?: return
        if (layout.isInfinite || width == 0) return
        stopFling()
        val scale = viewport.pxPerPt
        val max = layout.maxPanY(scale, height, pageMarginPx)
        val min = layout.minPanY(scale, height, pageMarginPx)
        viewport.panY = max - fraction.coerceIn(0f, 1f) * (max - min)
        clampPan()
        if (last) settlePan()
        tiles.invalidate()
        viewMoved()
        overlay.invalidate()
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
        viewMoved()
    }

    /** Called whenever [canUndo] or [canRedo] may have changed. */
    var onHistoryChanged: () -> Unit = {}

    val canUndo get() = editor?.canUndo == true
    val canRedo get() = editor?.canRedo == true

    /** The color around the pages. */
    var backdropColor = DarkEditorColors.canvasBackdrop.toArgb()
        set(value) {
            field = value
            setBackgroundColor(value)
            tiles.backdropColor = value
            tiles.invalidateAll()
        }

    /** True while the selection was made without the lasso in hand, so that a change of tool does not drop it. */
    private var selectionKeepsTool = false

    /** The post-it tool's last settings, which typing into a selected note goes by while the lasso is in hand. */
    private var stickyOptions = NoteOptions()

    var tool: CanvasTool = CanvasTool.Ink(InkStyle())
        set(value) {
            if (value is CanvasTool.Lasso) selectionKeepsTool = false
            // A selection made while another tool was in hand stays with that tool, which the pen can still draw with elsewhere.
            if (value !is CanvasTool.Lasso && !(selectionKeepsTool && lasso.selection != null)) lasso.clear()
            if (value is CanvasTool.Sticky) stickyOptions = value.options
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

    /** Called with the selected items when the button that saves them as a sticker is pressed. */
    var onSaveSticker: (List<PageItem>) -> Unit = {}

    /** Called just before a shape, note, table or picture that was just made is selected, so that the tool in use can be given back once it is let go. */
    var onItemCreated: () -> Unit = {}

    /** Called when a circle drawn with the pen has been touched and the lasso should take over. */
    var onLassoRequested: () -> Unit = {}

    /** How a circle drawn for circle to lasso picks what it surrounds: the same as the lasso tool is set to. */
    var circleLasso = CanvasTool.Lasso(rectangle = false, kinds = ALL_ITEM_KINDS, fullOnly = false)

    /** Called when the selection is dropped: a touch outside it, a delete, or a change of tool. */
    /**
     * A touch away from a selection made without the lasso has let it go. True when the editor took that as the end of the tool in hand
     * (it gave back the tool before), so the touch does nothing more.
     */
    var onSelectionLeft: () -> Boolean = { false }

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
    private val flight = WordFlightView(context)
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
            // The selection's box and handles are drawn on the overlay in screen positions, so they follow the page as it flies.
            overlay.invalidate()
            followTextEdit()
            onViewMoved()
            postOnAnimation(this)
        }
    }

    // The stroke or eraser drag in progress.
    private var currentEvent: MotionEvent? = null
    private var strokeId: InProgressStrokeId? = null

    // ---- Turning a page: it turns about its middle, and the pages after it make room ----

    /** How far the page in view is turned, in degrees clockwise. */
    var rotationDegrees = 0f
        private set

    /** Called when the turn of the page in view changes, and again with `turning` false once the fingers lift. */
    var onRotationChanged: (degrees: Float, turning: Boolean) -> Unit = { _, _ -> }

    /** Called when a finger taps where the turn indicator sits while it is hidden. */
    var onRotationBadgeTapped: () -> Unit = {}

    private val twist = RotationGesture()
    private var twistId0 = -1
    private var twistId1 = -1

    /** The page that two fingers are turning, while they do, counted from 0. */
    private var turningPage = -1
    private var badgeFingerId = -1
    private var badgeDownX = 0f
    private var badgeDownY = 0f
    private var strokeWaitingForEvent = false
    private var strokeStartedInThisEvent = false
    private var erase: Erasing? = null
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

    // The angle tool for a pen stroke made straight, in page coordinates from the stroke's start.
    private val angleGuide = AngleGuidePainter(resources.displayMetrics.density)
    private var lineGuideOn = false
    private var lineGuideEndX = 0f
    private var lineGuideEndY = 0f
    private var drawScribble = false
    private var drawCircle = false
    private var holdFit: FittedShape? = null
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
    private var tapeHoldMs = 0L

    /** The pen has been held still while laying tape in the hold mode: the strip straightens. */
    private val tapeHold = Runnable { shapes.straightenTape() }

    private val shapes by lazy {
        ShapeController(
            { index, into -> pageToScreenMatrix(index, into) },
            { overlay.invalidate() },
            { index, item ->
                val open = editor
                if (open != null) {
                    val pageId = open.document.pages[index].id
                    open.perform(ItemsEdit.addOnTop(pageId, open.content(pageId), item))
                    syncChanges()
                    // A new shape is not selected: the tool in use before comes back. Tape stays on the tool, until its own delay has passed.
                    if (item !is TapePathItem && !(item is ShapeItem && item.kind == ShapeKind.TAPE)) onItemCreated() else onTapeLaid()
                }
            },
            resources.displayMetrics.density,
        )
    }

    private val lasso by lazy {
        LassoController(
            viewport,
            resources.displayMetrics.density,
            { index, into -> pageToScreenMatrix(index, into) },
            { index -> layout?.turn(index) ?: 0f },
            { overlay.invalidate() },
        ).also { controller ->
            controller.onSelectionChanged = { onSelectionChanged(it?.items) }
            controller.onCopied = { onItemsCopied(it) }
            controller.onMenu = { onSelectionMenu() }
            controller.onSaveSticker = { onSaveSticker(it) }
            controller.onNoItems = { index, points -> pickPlannerElements(index, points) }
            controller.onCleared = { plannerPick.clear() }
        }
    }

    private class PendingStroke(
        val editor: NoteEditor,
        val pageId: Long,
        val style: InkStyle,
        val stickyId: Long?,
        val scribbleToErase: Boolean = false,
        val circleToLasso: Boolean = false,
        /** Handwriting for a text box: kept as ink until it is read, and never added to the page. */
        val handwriting: Boolean = false,
        val toScreen: Matrix? = null,
    ) {
        /** Set when the pen is held at the end of the line: the line is then smoothed when it is finished. */
        var smoothOnFinish = false
    }

    /** The last circle-ish stroke, which a touch on its line turns into a selection. */
    /** [noteId] is the open post-it the circle was drawn on, which holds it; the circle's points are the page's. */
    private class LoopMark(val pageId: Long, val item: StrokeItem, val noteId: Long? = null)

    /** The open note that the stroke in progress belongs to, if it started in one. */
    private var strokeStickyId: Long? = null

    init {
        setBackgroundColor(backdropColor)
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(flight, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(loupe, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.extraDraw = { canvas ->
            drawAudioBars(canvas)
            drawLineGuide(canvas)
            drawPageGrid(canvas)
            lasso.draw(canvas)
            if (plannerPick.active) {
                pageToScreenMatrix(plannerPick.pageIndex, plannerMatrix)
                plannerPick.draw(canvas, plannerMatrix)
            }
            drawFillReveal(canvas)
            shapes.draw(canvas)
            drawLoupeBox(canvas)
        }
        wet.addFinishedStrokesListener(this)
    }

    fun open(editor: NoteEditor) {
        close()
        this.editor = editor
        lasso.open(editor)
        painter = PagePainter(editor.paintSource, editor.ink)
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
        val visible = viewport.visibleWorld(width, height)
        if (match.rects.isEmpty()) {
            layout.moveToPage(viewport, index, width, height, pageMarginPx)
        } else {
            val first = match.rects[0]
            val box = match.rects.fold(first) { all, rect -> all.union(rect) }
            layout.pageToWorld(index, box.centerX, box.centerY, mapped)
            val target = Bounds(mapped[0] - box.width / 2f, mapped[1] - box.height / 2f, mapped[0] + box.width / 2f, mapped[1] + box.height / 2f)
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

    /** The items and layers of the page in the middle of the view, or null with no document. */
    fun currentContent(): PageContent? {
        val open = editor ?: return null
        val page = open.document.pages.getOrNull(currentPage) ?: return null
        return open.content(page.id)
    }

    /** Makes [id] the layer the tools work on, on the page in view. The selection and any text being typed are let go first. */
    fun selectLayer(id: Long) {
        val content = currentContent() ?: return
        if (content.activeLayer == id) return
        endTextEdit()
        lasso.clear()
        // Working on a layer that is hidden would put ink where it cannot be seen, so choosing it shows it.
        if (content.layers[id]?.visible == false) editLayers { page, c -> LayerOps.setVisible(page, c, id, true) }
        content.activeLayer = id
        onHistoryChanged()
        overlay.invalidate()
    }

    /**
     * Hides or shows layer [id], any layer. Hiding the one being worked on moves the work to the nearest layer that is still shown (the one
     * above first), so ink is never drawn where it cannot be seen. If none is shown any more, it stays where it is.
     */
    fun setLayerVisible(id: Long, visible: Boolean) {
        val content = currentContent() ?: return
        editLayers { page, c -> LayerOps.setVisible(page, c, id, visible) }
        if (visible || content.activeLayer != id) return
        val all = content.layers.all
        val at = all.indexOfFirst { it.id == id }
        val next = (at + 1 until all.size).firstOrNull { all[it].visible } ?: (at - 1 downTo 0).firstOrNull { all[it].visible } ?: return
        content.activeLayer = all[next].id
        onHistoryChanged()
        overlay.invalidate()
    }

    /** Runs a layer change on the page in view as one edit that can be undone, and draws it. */
    fun editLayers(make: (pageId: Long, content: PageContent) -> Edit?) {
        val open = editor ?: return
        val page = open.document.pages.getOrNull(currentPage) ?: return
        val edit = make(page.id, open.content(page.id)) ?: return
        endTextEdit()
        lasso.clear()
        open.perform(edit)
        syncChanges()
    }

    /** A new empty layer just above the one being worked on, which becomes the one being worked on. */
    fun addLayer() {
        val content = currentContent() ?: return
        val before = content.layers.all.mapTo(HashSet()) { it.id }
        editLayers { page, c -> LayerOps.add(page, c) }
        content.layers.all.firstOrNull { it.id !in before }?.let { content.activeLayer = it.id }
        onHistoryChanged()
    }

    /** Puts what is selected on layer [id], which ends the selection, since it is then not on the layer being worked on. */
    fun moveSelectionToLayer(id: Long) {
        lasso.moveSelectionToLayer(id)
        syncChanges()
    }

    /** Removes what is selected, as one edit. */
    fun deleteSelection() {
        lasso.deleteSelection()
        syncChanges()
    }

    val hasSelection get() = lasso.hasSelection

    /** Whether the selection's options bar is open, which takes the place of the "..." button. */
    var selectionMenuOpen: Boolean
        get() = lasso.menuOpen
        set(value) {
            lasso.menuOpen = value
        }

    /** Where the options bar goes: its right edge and its top on the screen, or null with nothing selected. */
    fun selectionMenuAnchor(): FloatArray? {
        val anchor = lasso.menuAnchor() ?: return null
        return anchor
    }

    /** Called when the selection's "..." button is pressed. */
    var onSelectionMenu: () -> Unit = {}

    /** Called with the items the selection's copy button copied. */
    var onItemsCopied: (List<PageItem>) -> Unit = {}

    /** Called with the items of the lasso selection, or null when nothing is selected. */
    var onSelectionChanged: (List<PageItem>?) -> Unit = {}

    /** Changes the selected items as one edit that can be undone. */
    fun changeSelection(change: (PageItem) -> PageItem) {
        lasso.changeSelected(change)
        syncChanges()
    }

    /** Puts the selected items in one group, which is selected, moved and copied as one, or takes them out of it when [grouped] is false. One undoable edit. */
    fun groupSelection(grouped: Boolean) {
        val group = if (grouped) app.betternotes.core.model.IdSource.Random.next().takeIf { it != 0L } ?: 1L else 0L
        lasso.changeSelected { it.withGroup(group) }
        syncChanges()
    }

    /** Moves the selection to the front, middle or back of the page's stack. */
    fun arrangeSelection(place: app.betternotes.core.edit.ZPlace) {
        lasso.arrangeSelected(place)
        syncChanges()
    }

    /** True when the selection is on the cover page. */
    fun selectionOnCover() = lasso.selectionOnCover()

    /** Changes the selected items on the page as a slider moves; [finishAdjustSelection] records the whole change. */
    fun adjustSelection(change: (PageItem) -> PageItem) {
        lasso.adjustSelected(change)
        syncChanges()
    }

    fun finishAdjustSelection() {
        lasso.finishAdjust()
        syncChanges()
    }

    /** The look of the tape in the lasso selection, if there is any. */
    fun selectedTape(): TapeLook? = lasso.selectedTape()

    fun adjustSelectedTape(thickness: Float?, opacity: Float?) {
        lasso.adjustTapes(thickness, opacity)
        syncChanges()
    }

    fun finishAdjustSelectedTape() {
        lasso.finishAdjust()
        syncChanges()
    }

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
    /** How opaque a new post-it is when its color has no opacity of its own. */
    var stickyOpacity by mutableFloatStateOf(1f)
        private set

    fun applySettings(settings: AppSettings) {
        stickyOpacity = settings.canvas.stickyOpacity
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
        // What is being written is put on the page first, so that undo takes back its last block and not what was done before the box.
        endTextEdit()
        lasso.clear()
        val done = editor?.undo() ?: return
        if (done) syncChanges()
    }

    fun redo() {
        endTextEdit()
        lasso.clear()
        val done = editor?.redo() ?: return
        if (done) syncChanges()
    }

    private var resetAnimator: android.animation.ValueAnimator? = null

    /** Turns the page in view back upright, sliding the pages after it back up as it goes, and remembers that. */
    fun resetRotation() {
        val layout = layout ?: return
        val index = currentPageIndex
        if (index < 0) return
        resetAnimator?.cancel()
        val from = layout.turn(index)
        if (from == 0f) {
            finishTurn(index)
            return
        }
        resetAnimator = android.animation.ValueAnimator.ofFloat(from, 0f).apply {
            duration = RESET_TURN_MS
            addUpdateListener { animation ->
                val here = layout.centerY(index)
                val hereX = layout.centerX(index)
                turnPage(index, animation.animatedValue as Float)
                // The page keeps its place on the screen while the room it takes changes.
                viewport.panX += (hereX - layout.centerX(index)) * viewport.pxPerPt
                viewport.panY += (here - layout.centerY(index)) * viewport.pxPerPt
                tiles.invalidate()
                viewMoved()
                onRotationChanged(rotationDegrees, true)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    resetAnimator = null
                    finishTurn(index)
                }
            })
            start()
        }
    }

    private fun updatePageToScreen() {
        screenToPage.invert(pageToScreen)
    }

    /** Shows the turn of the page in view on the indicator. Each page keeps its own. */
    private fun syncBadgeToPage() {
        val layout = layout ?: return
        if (width == 0 || twist.active) return
        val index = currentPageIndex
        if (index !in 0 until layout.pageCount) return
        val turn = layout.turn(index)
        if (turn != rotationDegrees) {
            rotationDegrees = turn
            onRotationChanged(turn, false)
        }
    }

    private fun viewMoved() {
        followTextEdit()
        // Ink waiting to be read stays where it was drawn on the screen, so it is read before the page moves from under it.
        if (written.isNotEmpty() && !writingStroke) {
            removeCallbacks(wordEnd)
            convertWord()
        }
        syncBadgeToPage()
        overlay.invalidate()
        onViewMoved()
    }

    /** Draws page [index] turned by [degrees], and the pages after it slide to keep their gap from it. */
    private fun turnPage(index: Int, degrees: Float) {
        val layout = layout ?: return
        layout.setTurn(index, degrees)
        rotationDegrees = degrees
        tiles.invalidateAll()
        overlay.invalidate()
        loupe.invalidate()
    }

    /** The turn of page [index] is final: it is remembered, and the view settles. */
    private fun finishTurn(index: Int) {
        val editor = editor ?: return
        val layout = layout ?: return
        val page = editor.document.pages.getOrNull(index) ?: return
        editor.setPageRotation(page.id, layout.turn(index))
        clampPan()
        settlePan()
        tiles.invalidateAll()
        overlay.invalidate()
        loupe.invalidate()
        rotationDegrees = layout.turn(index)
        onRotationChanged(rotationDegrees, false)
    }

    // ---- Two fingers twisting turn the page ----

    private fun twoFingers(event: MotionEvent) =
        event.pointerCount == 2 &&
            event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER &&
            event.getToolType(1) == MotionEvent.TOOL_TYPE_FINGER

    private fun twistAllowed() =
        recognizer.config.twoFingers == TwoFingerAction.ZOOM_AND_PAN &&
            strokeId == null && !rulerVisible && editor != null

    /**
     * Follows two fingers on the glass and turns the page they are on once they twist. Until then every touch goes on as
     * usual, so a pinch or a pan is not changed. Returns true for touches the twist has taken.
     */
    private fun followTwist(event: MotionEvent): Boolean {
        if (!twistAllowed()) {
            if (twist.tracking) endTwist()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Fingers in the loupe's window write or move there, and do not turn the page.
                val inLoupe = loupeOpen && (loupe.contains(event.getX(0), event.getY(0)) || loupe.contains(event.getX(1), event.getY(1)))
                if (twoFingers(event) && !inLoupe) {
                    twistId0 = event.getPointerId(0)
                    twistId1 = event.getPointerId(1)
                    twist.start(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                } else if (twist.tracking) {
                    endTwist()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!twist.tracking) return false
                if (!twoFingers(event) || event.getPointerId(0) != twistId0 || event.getPointerId(1) != twistId1) {
                    endTwist()
                    return false
                }
                val wasActive = twist.active
                val step = twist.move(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                if (!wasActive && twist.active && !beginTwist(event)) return false
                if (step != null) applyTwist(step)
                return twist.active
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (twist.tracking) {
                    val took = twist.active
                    endTwist()
                    return took
                }
            }
        }
        return false
    }

    /**
     * The twist is past its threshold: the page under the fingers is the one that turns, and the recognizer lets go of them.
     * Returns false for a page that cannot turn, which leaves the touch to the recognizer.
     */
    private fun beginTwist(event: MotionEvent): Boolean {
        val layout = layout
        val pages = editor?.document?.pages
        val middleX = viewport.toWorldX((event.getX(0) + event.getX(1)) / 2f)
        val middleY = viewport.toWorldY((event.getY(0) + event.getY(1)) / 2f)
        val index = layout?.pageIndexAt(middleX, middleY)?.takeIf { it >= 0 } ?: currentPageIndex
        val page = pages?.getOrNull(index)
        // A page of a PDF, or an infinite canvas, is not turned.
        if (layout == null || page == null || page.size == null || page.source != PageSource.Blank) {
            twist.end()
            return false
        }
        turningPage = index
        removeCallbacks(deadlineCheck)
        recognizer.pointerCancel(twistId0, event.eventTime)
        recognizer.pointerCancel(twistId1, event.eventTime)
        pinchedThisGesture = true
        stopFling()
        onRotationChanged(layout.turn(index), true)
        return true
    }

    /** Turns, scales and pans so that the page keeps following the fingers. */
    private fun applyTwist(step: RotationStep) {
        val layout = layout ?: return
        val index = turningPage
        if (index < 0) return
        val here = FloatArray(2)
        // The point of the page that was under the fingers' middle before this move.
        layout.worldToPage(index, viewport.toWorldX(step.focusX - step.dx), viewport.toWorldY(step.focusY - step.dy), here)
        if (step.scale != 1f) {
            editor?.pdf?.pinching = true
            viewport.zoomTo(viewport.zoom * step.scale, step.focusX - step.dx, step.focusY - step.dy)
            onZoomChanged(viewport.zoom)
        }
        turnPage(index, normalizeDegrees(layout.turn(index) + step.degrees))
        // Where that point lies now, against where the fingers are: the pan makes up the difference.
        val world = FloatArray(2)
        layout.pageToWorld(index, here[0], here[1], world)
        viewport.panX += step.focusX - viewport.toScreenX(world[0])
        viewport.panY += step.focusY - viewport.toScreenY(world[1])
        tiles.invalidate()
        viewMoved()
        onRotationChanged(rotationDegrees, true)
    }

    private fun endTwist() {
        val wasActive = twist.active
        val index = turningPage
        twist.end()
        twistId0 = -1
        twistId1 = -1
        turningPage = -1
        if (!wasActive || index < 0) return
        val layout = layout ?: return
        // Once turned, the page keeps whatever angle it was left at: it does not snap to upright or to a right angle, so one degree
        // off stays one degree off. Turning it again has to get past the start threshold first.
        finishTurn(index)
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % FULL_TURN_DEGREES
        if (d > HALF_TURN_DEGREES) d -= FULL_TURN_DEGREES
        if (d <= -HALF_TURN_DEGREES) d += FULL_TURN_DEGREES
        return d
    }

    /**
     * A quick tap of one finger where the turn indicator sits shows it, while the page is turned. The touch is taken, so a
     * finger that draws leaves no dot there.
     */
    private fun watchBadge(raw: MotionEvent): Boolean {
        if (rotationDegrees == 0f || loupeOpen) {
            badgeFingerId = -1
            return false
        }
        when (raw.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val inside = raw.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && inBadgeZone(raw.x, raw.y)
                badgeFingerId = if (inside) raw.getPointerId(0) else -1
                badgeDownX = raw.x
                badgeDownY = raw.y
                return inside
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> return badgeFingerId >= 0
            MotionEvent.ACTION_UP -> {
                val mine = badgeFingerId >= 0
                badgeFingerId = -1
                if (mine) {
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    if (raw.eventTime - raw.downTime <= FINGER_TAP_MS && hypot(raw.x - badgeDownX, raw.y - badgeDownY) <= slop) {
                        onRotationBadgeTapped()
                    }
                }
                return mine
            }
            MotionEvent.ACTION_CANCEL -> {
                val mine = badgeFingerId >= 0
                badgeFingerId = -1
                return mine
            }
        }
        return false
    }

    private var loupeFingerId = -1

    /** The point of page [index] under a screen point, for tests. */
    internal fun pagePointForTest(screenX: Float, screenY: Float, index: Int) = floatArrayOf(pageX(screenX, screenY, index), pageY(screenX, screenY, index))

    /** How far page [index] is drawn turned, for tests. */
    internal fun pageTurnForTest(index: Int) = layout?.turn(index) ?: 0f

    /** Where page [index] starts in the document, for tests. */
    internal fun pageOriginYForTest(index: Int) = layout?.originY(index) ?: 0f

    /** The loupe's box on the page, for tests. */
    internal fun loupeBoxForTest() = android.graphics.RectF(loupe.box)

    /** A finger on the loupe's box on the page carries the box along, as the pen does. Returns true for the touches it takes. */
    private fun dragLoupeByFinger(raw: MotionEvent): Boolean {
        if (!loupeOpen) {
            loupeFingerId = -1
            return false
        }
        fun flat(index: Int) = floatArrayOf(raw.getX(index), raw.getY(index))
        when (raw.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (raw.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER || loupe.contains(raw.x, raw.y)) return false
                val at = flat(0)
                if (startDraggingLoupeBox(raw.getPointerId(0), at[0], at[1])) {
                    loupeFingerId = raw.getPointerId(0)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (loupeFingerId < 0) return false
                val index = raw.findPointerIndex(loupeFingerId)
                if (index >= 0) {
                    val at = flat(index)
                    onDrawMove(loupeFingerId, at[0], at[1])
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> return loupeFingerId >= 0
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val mine = loupeFingerId >= 0
                loupeFingerId = -1
                loupeBoxPointer = -1
                return mine
            }
        }
        return false
    }

    private fun inBadgeZone(x: Float, y: Float): Boolean {
        val density = resources.displayMetrics.density
        return abs(x - width / 2f) <= BADGE_HALF_WIDTH_DP * density && y >= height - BADGE_ZONE_HEIGHT_DP * density
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Sets up the front buffer now, rather than making the first stroke wait for it.
        wet.eagerInit()
    }

    override fun onDetachedFromWindow() {
        readerThread?.let { thread ->
            thread.execute {
                reader?.close()
                reader = null
            }
            thread.shutdown()
        }
        readerThread = null
        super.onDetachedFromWindow()
        removeCallbacks(deadlineCheck)
        removeCallbacks(holdCheck)
        removeCallbacks(stylusHoldCheck)
        stopFling()
        recognizer.reset()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lasso.viewWidthPx = w
        lasso.viewHeightPx = h
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
        if (dragLoupeByFinger(event) || watchBadge(event) || followTwist(event)) return true
        return handleTouch(event)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
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
        trackHoldSelect(event)
        trackFingerTap(event)
        trackTableTap(event)
        trackPlannerTap(event)
        strokeStartedInThisEvent = false
        currentEvent = null
        scheduleDeadline()
        return true
    }

    private var holdSelectPossible = false
    private var holdMoving = false
    private var holdSelectPointer = -1
    private var holdSelectX = 0f
    private var holdSelectY = 0f
    private val holdSelect = Runnable { selectHeld() }

    /**
     * A touch that stays still on an item for [HOLD_SELECT_MS] selects it, with its handles, and the tool in hand stays. This is for the
     * pen and a finger alike, and it takes the touch back from what it began, such as a dot of ink.
     */
    private fun trackHoldSelect(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(holdSelect)
                holdSelectPossible = event.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER && holdSelectsWith(tool)
                holdSelectPointer = event.getPointerId(0)
                holdSelectX = event.x
                holdSelectY = event.y
                if (holdSelectPossible) postDelayed(holdSelect, HOLD_SELECT_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (holdMoving) {
                    val moved = layout?.pageIndexAt(viewport.toWorldX(holdSelectX), viewport.toWorldY(holdSelectY)) ?: -1
                    if (moved >= 0) lasso.move(pageX(event.x, event.y, moved), pageY(event.x, event.y, moved))
                }
                if (holdSelectPossible && hypot(event.x - holdSelectX, event.y - holdSelectY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    holdSelectPossible = false
                    removeCallbacks(holdSelect)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                holdSelectPossible = false
                removeCallbacks(holdSelect)
                if (holdMoving && event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) {
                    holdMoving = false
                    if (event.actionMasked == MotionEvent.ACTION_UP) lasso.end() else lasso.cancel()
                    syncChanges()
                }
            }
        }
    }

    /** The tools whose first touch can be taken back without a trace. The others do their work as the touch lands. */
    private fun holdSelectsWith(tool: CanvasTool) =
        tool is CanvasTool.Ink || tool is CanvasTool.Lasso || tool is CanvasTool.Shape || tool is CanvasTool.Text || tool is CanvasTool.Table

    private fun selectHeld() {
        if (!holdSelectPossible) return
        holdSelectPossible = false
        val open = editor ?: return
        val layout = layout ?: return
        if (lassoPointer >= 0 || audioPointer >= 0 || loupeOpen || textEdit != null) return
        val index = layout.pageIndexAt(viewport.toWorldX(holdSelectX), viewport.toWorldY(holdSelectY))
        if (index < 0) return
        val pageId = open.document.pages[index].id
        val x = pageX(holdSelectX, holdSelectY, index)
        val y = pageY(holdSelectX, holdSelectY, index)
        if (lasso.hasSelection && lasso.grabs(index, x, y)) {
            // A hold on the selection itself picks it up: the rest of this touch carries it, whatever the tool.
            recognizer.pointerCancel(holdSelectPointer, SystemClock.uptimeMillis())
            lassoPointer = -1
            holdMoving = true
            lasso.start(index, x, y, rectangle = false, kinds = ALL_ITEM_KINDS, fullOnly = false)
            syncChanges()
            return
        }
        val at = heldItemAt(pageId, x, y)
        if (at < 0) return
        // The touch is over: the dot or stroke it began is dropped, and the rest of it does nothing.
        recognizer.pointerCancel(holdSelectPointer, SystemClock.uptimeMillis())
        val content = open.content(pageId)
        // A hold is the one way to pick up the background of a cover, and the other locked things that no loop takes.
        val cover = coverSizeOf(pageId)
        val onBackground = cover != null && content[at].isCoverBackground(cover)
        val chosen = if (onBackground) Selection(pageId, intArrayOf(at), listOf(content[at])) else Grouping.expand(Selection(pageId, intArrayOf(at), listOf(content[at])), content, coverSize = cover)
        lasso.clear()
        lasso.select(index, chosen, coverBackgroundToo = onBackground)
        selectionKeepsTool = tool !is CanvasTool.Lasso
        syncChanges()
    }

    /**
     * The top item that a hold at ([x], [y]) is on: any shown item of the working layer, locked ones too (they come last, as they lie
     * below the rest). A stroke counts only near its line, as its box can be as large as the page. -1 if there is none.
     */
    private fun heldItemAt(pageId: Long, x: Float, y: Float): Int {
        val content = editor?.content(pageId) ?: return -1
        val reach = HOLD_STROKE_REACH_DP * resources.displayMetrics.density / viewport.pxPerPt
        var lockedHit = -1
        var backgroundHit = -1
        val cover = coverSizeOf(pageId)
        for (i in content.size - 1 downTo 0) {
            val item = content[i]
            if (!content.isEditable(item) || !content.isShown(item) || !item.bounds.contains(x, y)) continue
            if (item is StrokeItem && !(item.points.size == 1 || StrokeGestures.isNearLine(item.points, x, y, max(reach, item.style.width / 2f)))) continue
            // The background of a cover comes last of all: a hold on it picks it up only where nothing else is.
            if (cover != null && item.isCoverBackground(cover)) {
                if (backgroundHit < 0) backgroundHit = i
                continue
            }
            if (!item.locked) return i
            if (lockedHit < 0) lockedHit = i
        }
        return if (lockedHit >= 0) lockedHit else backgroundHit
    }

    /** The size of the page [pageId] when it is a cover, else null. */
    private fun coverSizeOf(pageId: Long): app.betternotes.core.geometry.PageSize? =
        editor?.document?.pages?.firstOrNull { it.id == pageId }?.takeIf { it.isCover }?.size

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
                if (tapped) fingerTapped(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> fingerTapPossible = false
        }
    }

    private var tableTapPossible = false
    private var tableTapDownX = 0f
    private var tableTapDownY = 0f
    private var lastTableTapId = 0L
    private var lastTableTapMs = 0L
    private var lastTableTapX = 0f
    private var lastTableTapY = 0f

    /** Two quick, still taps of the pen or a finger on one table open its editing handles, whatever the tool is. */
    private fun trackTableTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tableTapPossible = event.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER
                tableTapDownX = event.x
                tableTapDownY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> tableTapPossible = false
            MotionEvent.ACTION_MOVE -> {
                if (tableTapPossible && hypot(event.x - tableTapDownX, event.y - tableTapDownY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    tableTapPossible = false
                }
            }
            MotionEvent.ACTION_UP -> {
                val tapped = tableTapPossible && event.eventTime - event.downTime <= TABLE_TAP_MS
                tableTapPossible = false
                if (tapped) tableTapped(event.x, event.y, event.eventTime, event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)
            }
            MotionEvent.ACTION_CANCEL -> tableTapPossible = false
        }
    }

    // ---- Planner ----

    /** Told when a tap lands on a planner part that needs the editor's own screen: the weekday menu or the cover's years. */
    var onPlannerAction: (PlannerAction, PlannerPage) -> Unit = { _, _ -> }

    private var plannerPlan: PlannerPlan? = null
    private var plannerTapPossible = false
    private var plannerTapDownX = 0f
    private var plannerTapDownY = 0f

    /** The dates of the open planner, or null when the notebook is not one. */
    val plannerSetup: PlannerSetup? get() = editor?.document?.let { PlannerPages.setupOf(it) }

    private fun planFor(setup: PlannerSetup): PlannerPlan = plannerPlan?.takeIf { it.setup == setup } ?: PlannerPlan(setup).also { plannerPlan = it }

    /** A quick, still tap of the pen or a finger on a planner's tab or link, whatever the tool is. */
    private fun trackPlannerTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                plannerTapPossible = event.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER && editor?.document?.kind == DocumentKind.PLANNER
                plannerTapDownX = event.x
                plannerTapDownY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> plannerTapPossible = false
            MotionEvent.ACTION_MOVE -> {
                if (plannerTapPossible && hypot(event.x - plannerTapDownX, event.y - plannerTapDownY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    plannerTapPossible = false
                }
            }
            MotionEvent.ACTION_UP -> {
                val tapped = plannerTapPossible && event.eventTime - event.downTime <= TABLE_TAP_MS
                plannerTapPossible = false
                if (tapped) plannerTapped(event.x, event.y, event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS)
            }
            MotionEvent.ACTION_CANCEL -> plannerTapPossible = false
        }
    }

    private fun plannerTapped(x: Float, y: Float, byPen: Boolean) {
        val open = editor ?: return
        val document = open.document
        val index = layout?.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y)) ?: return
        if (index < 0) return
        val page = document.pages[index]
        val template = page.background.template as? PageTemplate.Planner ?: return
        val size = page.size ?: return
        val action = plannerActionAt(template.page, template.setup, size, pageX(x, y, index), pageY(x, y, index)) ?: return
        // The pen draws a dot with each tap in an ink tool. It is taken back, so a tap on a tab leaves no mark.
        if (byPen && tool is CanvasTool.Ink) removeTapDot(page.id)
        when (action) {
            is PlannerAction.Go -> {
                val target = planFor(template.setup).indexOf(action.page)
                if (target >= 0) goToPage(target + if (document.coverId != null) 1 else 0)
            }
            else -> onPlannerAction(action, template.page)
        }
    }

    private fun removeTapDot(pageId: Long) {
        val open = editor ?: return
        postDelayed({
            val content = open.content(pageId)
            val last = content.insertionIndex(content.activeLayer) - 1
            val dot = (if (last >= 0) content[last] else null) as? StrokeItem ?: return@postDelayed
            if (dot.bounds.width < TAP_DOT_PT && dot.bounds.height < TAP_DOT_PT && open.canUndo) open.undo()
        }, TAP_DOT_WAIT_MS)
    }

    /**
     * Gives the planner the dates of [setup], as one undoable step, and goes to [stayOn] (the cover when null) in the new pages.
     * A page that is still there keeps what was drawn on it.
     */
    fun setPlannerSetup(setup: PlannerSetup, stayOn: PlannerPage?) {
        val open = editor ?: return
        val edit = PlannerPages.change(open.document, setup) ?: return
        lasso.clear()
        open.perform(edit)
        syncChanges()
        val target = if (stayOn == null) 0 else open.document.pages.indexOfFirst { (it.background.template as? PageTemplate.Planner)?.page == stayOn }
        goToPage(target.coerceAtLeast(0))
    }

    /** The semester dates of the open planner, as days since 1970 (0 for none): a start and an end for each of four semesters. */
    val plannerSemesters: List<Int> get() = editor?.document?.let { PlannerPages.semestersOf(it) }.orEmpty()

    /** Sets or clears ([epochDay] null) a semester date on every page that shows it, as one undoable step. */
    fun setSemesterDate(semester: Int, end: Boolean, epochDay: Int?) {
        val open = editor ?: return
        val edit = PlannerPages.setSemesterDate(open.document, semester, end, epochDay) ?: return
        open.perform(edit)
        syncChanges()
    }

    /** Takes the date handwriting the eraser touched away, as one undoable step. */
    private fun eraseDateInk() {
        if (dateEraseHits.isEmpty()) return
        val open = editor
        val edit = if (open == null) null else PlannerPages.removeDateStrokes(open.document, dateEraseHits)
        dateEraseHits.clear()
        if (open == null || edit == null) return
        open.perform(edit)
        syncChanges()
    }

    /**
     * A pen stroke that is drawn in a semester date blank of a planner becomes the handwriting of that date, which every page showing the
     * date draws. Says whether it was taken; a dot (a tap) and highlighter strokes are not.
     */
    private fun captureDateInk(editor: NoteEditor, pageId: Long, item: StrokeItem): Boolean {
        if (item.style.tool == InkTool.HIGHLIGHTER || (item.bounds.width < TAP_DOT_PT && item.bounds.height < TAP_DOT_PT)) return false
        val page = editor.document.pages.firstOrNull { it.id == pageId } ?: return false
        val template = page.background.template as? PageTemplate.Planner ?: return false
        if (!PlannerPages.showsDates(template.page.kind)) return false
        val size = page.size ?: return false
        val bounds = item.bounds
        val action = plannerActionAt(template.page, template.setup, size, bounds.centerX, bounds.centerY) as? PlannerAction.PickDate ?: return false
        val xy = FloatArray(item.points.size * 2) { if (it % 2 == 0) item.points.x(it / 2) else item.points.y(it / 2) }
        val edit = PlannerPages.addDateInk(editor.document, PlannerPages.dateStroke(action, size, xy, item.style.color, item.style.width)) ?: return false
        lasso.clear()
        editor.perform(edit)
        return true
    }

    /** The planner's four colors (cover, spine, stripe, page accent), defaults filled in. */
    val plannerColors: List<Int> get() = editor?.document?.let { doc -> PlannerPages.colorsOf(doc).ifEmpty { PlannerColors.DEFAULT } } ?: PlannerColors.DEFAULT

    /** Gives every page of the planner these colors as one undoable step; the cover and all pages are drawn again. */
    fun setPlannerColors(colors: List<Int>) {
        val open = editor ?: return
        val edit = PlannerPages.recolor(open.document, colors) ?: return
        lasso.clear()
        open.perform(edit)
        syncChanges()
    }

    // ---- Restyling planner elements ----

    private val plannerMatrix = Matrix()

    /** What the lasso picked on a planner page: its generated elements, which can only be restyled. */
    private val plannerPick = PlannerPick { overlay.invalidate() }

    /** Called with the picked planner elements (empty when none). */
    var onPlannerPick: (List<app.betternotes.core.planner.PlannerElement>) -> Unit
        get() = plannerPick.onChanged
        set(value) {
            plannerPick.onChanged = value
        }

    /** The generated elements of page [index] that the loop [polygon] surrounds, for the pick to be made when there are some. */
    private fun plannerElementsIn(index: Int, polygon: FloatArray): List<app.betternotes.core.planner.PlannerElement> {
        val page = editor?.document?.pages?.getOrNull(index) ?: return emptyList()
        return plannerPick.find(page, polygon)
    }

    private fun pickPlannerElements(index: Int, points: FloatArray): Boolean {
        val page = editor?.document?.pages?.getOrNull(index) ?: return false
        return plannerPick.pick(index, page, points)
    }

    /** The parts of an element the picked ones have, for the restyle bar. */
    fun plannerPickSlots() = plannerPick.slots()

    /** Where the restyle bar goes, in view pixels, or null when nothing is picked. */
    fun plannerPickAnchor(): FloatArray? {
        val at = plannerPick.anchor() ?: return null
        pageToScreenMatrix(plannerPick.pageIndex, plannerMatrix)
        plannerMatrix.mapPoints(at)
        return at
    }

    /** The color the first picked element has in [slot] now, or null when it is as drawn. */
    fun plannerPickColor(slot: app.betternotes.core.planner.RoleSlot): Int? {
        val template = editor?.document?.pages?.getOrNull(plannerPick.pageIndex)?.background?.template as? PageTemplate.Planner ?: return null
        val key = plannerPick.keys().firstOrNull() ?: return null
        return template.roles.styleOf(key.role, key.x, key.y)?.colorOf(slot)
    }

    /** The font the first picked element has now, or null. */
    fun plannerPickFont(): String? {
        val template = editor?.document?.pages?.getOrNull(plannerPick.pageIndex)?.background?.template as? PageTemplate.Planner ?: return null
        val key = plannerPick.keys().firstOrNull() ?: return null
        return template.roles.styleOf(key.role, key.x, key.y)?.font
    }

    /**
     * Gives the picked elements the [change] as one undoable step, or every element like them on every page when [similar]. The picked
     * elements stay picked, and the page is drawn again.
     */
    fun restylePlanner(change: PlannerPages.StyleChange, similar: Boolean) {
        val open = editor ?: return
        if (!plannerPick.active) return
        val edit = PlannerPages.restyle(open.document, plannerPick.pageId, plannerPick.keys(), similar, change) ?: return
        open.perform(edit)
        syncChanges()
        overlay.invalidate()
    }

    /** Gives every element like the picked ones the look they have now, as one undoable step ("similar", pressed after a change). */
    fun spreadPlanner() {
        val open = editor ?: return
        if (!plannerPick.active) return
        val edit = PlannerPages.spread(open.document, plannerPick.pageId, plannerPick.keys()) ?: return
        open.perform(edit)
        syncChanges()
        overlay.invalidate()
    }

    /** Drops the picked planner elements, and gives back the tool the lasso was lent from, as letting go of a selection does. */
    fun clearPlannerPick() {
        plannerPick.clear()
        lasso.onSelectionDropped()
    }

    /** The title on the planner's cover, shown when it is edited. */
    val plannerTitle: String get() = editor?.document?.let { PlannerPages.titleOf(it) }.orEmpty()

    /** Puts [title] on the planner's cover as one undoable step. An empty title brings the default back. */
    fun setPlannerTitle(title: String) {
        val open = editor ?: return
        val edit = PlannerPages.retitle(open.document, title) ?: return
        open.perform(edit)
        syncChanges()
    }

    private fun tableTapped(x: Float, y: Float, timeMs: Long, byPen: Boolean) {
        val editor = editor ?: return
        val index = layout?.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y)) ?: return
        if (index < 0) return
        val pageId = editor.document.pages[index].id
        val table = topItemOfType<TableItem>(pageId, pageX(x, y, index), pageY(x, y, index))?.second
        val text = if (table == null) topItemOfType<TextItem>(pageId, pageX(x, y, index), pageY(x, y, index))?.second else null
        if (table == null && text == null) {
            lastTableTapId = 0L
            return
        }
        val tapped = table?.id ?: text!!.id
        val near = hypot(x - lastTableTapX, y - lastTableTapY) <= TABLE_DOUBLE_TAP_REACH_DP * resources.displayMetrics.density
        if (lastTableTapId == tapped && timeMs - lastTableTapMs <= TABLE_DOUBLE_TAP_MS && near) {
            lastTableTapId = 0L
            // Two taps on a table open its handles, and on a text box they go back to typing in it.
            if (table != null) openTableEditor(index, pageId, table.id, cleanDots = byPen && tool is CanvasTool.Ink) else continueText(index, pageId, text!!)
        } else {
            lastTableTapId = tapped
            lastTableTapMs = timeMs
            lastTableTapX = x
            lastTableTapY = y
        }
    }

    /** Selects a text box, with its handles showing, so that it can be moved and resized and its look changed. */
    private fun selectText(index: Int, pageId: Long, item: TextItem) {
        val open = editor ?: return
        val at = indexOfItem(pageId, item.id)
        if (at < 0) return
        lasso.select(index, Selection(pageId, intArrayOf(at), listOf(open.content(pageId)[at])))
        selectionKeepsTool = true
        syncChanges()
    }

    /** Opens a text box for typing again, with the text tool in hand. */
    private fun continueText(index: Int, pageId: Long, item: TextItem) {
        onTextToolRequested()
        // Typing again is not working on the box: its handles go, so that the pen writes and scribbles on it and does not move it.
        selectionKeepsTool = false
        lasso.clear()
        val at = indexOfItem(pageId, item.id)
        val current = (if (at >= 0) editor?.content(pageId)?.get(at) as? TextItem else null) ?: item
        editText(index, pageId, current, isNew = false)
    }

    /** Selects the table and shows its editing handles, with the lasso to work them. */
    private fun openTableEditor(index: Int, pageId: Long, tableId: Long, cleanDots: Boolean = false) {
        val open = editor ?: return
        fun show(): Boolean {
            val at = indexOfItem(pageId, tableId)
            if (at < 0) return false
            lasso.editTable(index, Selection(pageId, intArrayOf(at), listOf(open.content(pageId)[at])))
            selectionKeepsTool = true
            syncChanges()
            return true
        }
        if (!show()) return
        if (cleanDots) {
            // The pen draws a dot with each tap in an ink tool. They are taken back once the second one has been saved.
            postDelayed({
                repeat(2) {
                    val content = open.content(pageId)
                    val last = content.insertionIndex(content.activeLayer) - 1
                    val dot = (if (last >= 0) content[last] else null) as? StrokeItem ?: return@repeat
                    if (dot.bounds.width < TAP_DOT_PT && dot.bounds.height < TAP_DOT_PT && open.canUndo) open.undo()
                }
                show()
            }, TAP_DOT_WAIT_MS)
        }
    }

    /**
     * A press on a button of the selection that lies outside every page, which happens when the selection runs to the page's edge, as a
     * cover's background does: the buttons sit just under it. True when one was pressed and did its action.
     */
    private fun pressSelectionButtonOffPage(x: Float, y: Float, reachDp: Float): Boolean {
        val index = lasso.selectionPageIndex()
        if (index < 0) return false
        return lasso.pressButton(index, pageX(x, y, index), pageY(x, y, index), reachDp)
    }

    /** What a finger tap does that only the pen could before: touch a circle drawn for circle to lasso, or a button of a lasso selection. */
    private fun fingerTapped(x: Float, y: Float) {
        val editor = editor ?: return
        if ((tool is CanvasTool.Lasso || lasso.hasSelection) && pressSelectionButtonOffPage(x, y, FINGER_BUTTON_REACH_DP)) {
            syncChanges()
            return
        }
        val index = layout?.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y)) ?: return
        if (index < 0) return
        val pageId = editor.document.pages[index].id
        // A finger tap away from a selection made with a tool other than the lasso lets it go, as the pen's touch does.
        if (tool !is CanvasTool.Lasso && lasso.hasSelection && !lasso.grabs(index, pageX(x, y, index), pageY(x, y, index))) {
            lasso.clear()
            syncChanges()
            onSelectionLeft()
            return
        }
        when (val current = tool) {
            // Picking a color works with a finger too: a quick touch takes the color under it.
            is CanvasTool.Pick -> current.onPick(x, y)
            is CanvasTool.Ink -> if (current.circleToLasso && lastLoop != null) {
                selectLoopAt(index, pageId, pageX(x, y, index), pageY(x, y, index), byFinger = true)
            }
            is CanvasTool.Lasso -> {
                val px = pageX(x, y, index)
                val py = pageY(x, y, index)
                if (lasso.pressButton(index, px, py, FINGER_BUTTON_REACH_DP) || lasso.pressTableButton(index, px, py, FINGER_BUTTON_REACH_DP)) syncChanges()
            }
            // A touch on a text box selects it, as the pen does, so that it can be moved and its look changed.
            is CanvasTool.Text -> {
                endTextEdit()
                topItemOfType<TextItem>(pageId, pageX(x, y, index), pageY(x, y, index))?.second?.let { selectText(index, pageId, it) }
            }
            else -> Unit
        }
    }

    /**
     * Whether a single finger at ([x], [y]) is on what is selected, or on one of its handles, which a finger then moves or resizes as the pen
     * does, and not the page. A selection with something locked in it is not grabbed: the finger moves the page.
     */
    private fun fingerGrabsText(x: Float, y: Float): Boolean {
        if ((tool !is CanvasTool.Lasso && !lasso.hasSelection) || recognizer.mode != InputMode.STYLUS_DRAW) return false
        val layout = layout ?: return false
        val index = layout.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y))
        return index >= 0 && lasso.grabs(index, pageX(x, y, index), pageY(x, y, index))
    }

    /**
     * The touch now down is a finger that counts as the pen only to move the text box it is on. A finger that does not draw, with the page in
     * scroll mode, never makes a line, a shape, tape, a loop or writing: all it may do is what it was let through for, work the selection.
     */
    private var fingerCarries = false

    private fun pointerDown(event: MotionEvent, index: Int) {
        var kind = kindOf(event.getToolType(index))
        fingerCarries = false
        // A finger that lands alone on the selected text box carries it, so it counts as the pen for this touch. It only carries it: see [fingerCarries].
        if (kind == PointerKind.FINGER && event.pointerCount == 1 && fingerGrabsText(event.getX(index), event.getY(index))) {
            kind = PointerKind.STYLUS
            fingerCarries = true
        }
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
        (tool as? CanvasTool.Pick)?.let { pick ->
            pick.onPick(x, y)
            return
        }
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
        // The selection's buttons are always on the screen, but not always on its page: pressing one is found by the selection's page.
        if ((tool is CanvasTool.Lasso || lasso.hasSelection) && !drawingInLoupe && kind != PointerKind.STYLUS_ERASER && !holdErasing && pressSelectionButtonOffPage(x, y, BUTTON_REACH_OFF_PAGE_DP)) {
            syncChanges()
            return
        }
        val index = if (drawingInLoupe) loupe.pageIndex else layout.pageIndexAt(viewport.toWorldX(x), viewport.toWorldY(y))
        if (index < 0) return
        val page = editor.document.pages[index]
        drawPointerId = pointerId
        drawPageIndex = index
        if (fingerCarries) {
            fingerCarries = false
            val px = pageX(x, y, index)
            val py = pageY(x, y, index)
            // Whatever the tool in hand, the finger works the selection it landed on, and draws nothing.
            if (lasso.hasSelection && lasso.grabs(index, px, py, body = true)) {
                endTextEdit()
                lassoPointer = pointerId
                lasso.start(index, px, py, rectangle = false, kinds = ALL_ITEM_KINDS, fullOnly = false)
                syncChanges()
            }
            return
        }
        if (tool !is CanvasTool.Lasso && lasso.hasSelection && kind != PointerKind.STYLUS_ERASER && !holdErasing && !drawingInLoupe) {
            val px = pageX(x, y, index)
            val py = pageY(x, y, index)
            // With ink in hand a touch on the selection's body writes, as it does on a note; its handles still work. A hold moves it.
            if (lasso.grabs(index, px, py, body = tool !is CanvasTool.Ink)) {
                // A selection made while another tool was in hand is worked with the pen as with the lasso, and that tool stays.
                endTextEdit()
                lassoPointer = pointerId
                lasso.start(index, px, py, rectangle = false, kinds = ALL_ITEM_KINDS, fullOnly = false)
                syncChanges()
                return
            }
            // On the body with ink in hand the touch goes on, and holding still turns it into a move of the selection. Elsewhere it lets go.
            if (!lasso.grabs(index, px, py)) {
                lasso.clear()
                syncChanges()
                // With a tool that makes items, a touch away from the selection only lets it go, and the tool before comes back.
                if (onSelectionLeft()) return
            }
        }
        if (kind != PointerKind.STYLUS_ERASER && !holdErasing && !drawingInLoupe && tool is CanvasTool.Text && textEdit != null && writingReady) {
            startHandwriting(x, y)
            return
        }
        val typing = tool is CanvasTool.Text && textEdit != null
        // A touch away from the box while words are being written does not throw them away: they finish turning into text, then the box closes.
        if (typing && kind != PointerKind.STYLUS_ERASER && topItemOfType<TextItem>(page.id, pageX(x, y, index), pageY(x, y, index)) == null &&
            afterWordsLand { endTextEdit(); onTextToolLeft() }
        ) {
            return
        }
        endTextEdit()
        strokeStickyId = null
        if (typing && kind != PointerKind.STYLUS_ERASER && topItemOfType<TextItem>(page.id, pageX(x, y, index), pageY(x, y, index)) == null) {
            onTextToolLeft()
            return
        }
        // A touch on the line of the circle just drawn is on the circle, whatever lies under it, a post-it or an item on one: it selects what the
        // circle surrounds, before any item takes the touch.
        val circle = tool as? CanvasTool.Ink
        if (kind != PointerKind.STYLUS_ERASER && circle != null && circle.circleToLasso && selectLoopAt(index, page.id, pageX(x, y, index), pageY(x, y, index))) return
        if (kind != PointerKind.STYLUS_ERASER && pressAudio(pointerId, index, page.id, pageX(x, y, index), pageY(x, y, index), x, y)) return
        if (kind != PointerKind.STYLUS_ERASER && handlePlacement(index, pageX(x, y, index), pageY(x, y, index))) return

        val tool = tool
        if (kind == PointerKind.STYLUS_ERASER || holdErasing || tool is CanvasTool.Eraser) {
            val eraser = tool as? CanvasTool.Eraser ?: DEFAULT_ERASER
            // On an open note the eraser works inside it, on what is in it. Under a closed note it works on the page as always.
            val onNote = topItemOfType<StickyNoteItem>(page.id, pageX(x, y, index), pageY(x, y, index))?.takeIf { it.second.isOpen }
            erase = if (onNote != null) {
                NoteEraseSession(page.id, editor.content(page.id), onNote.first, eraser.mode, eraser.radiusPt, eraser.kinds, eraser.lockedToo)
            } else {
                EraseSession(page.id, editor.content(page.id), eraser.mode, eraser.radiusPt, kinds = eraser.kinds, includeLocked = eraser.lockedToo)
            }
            eraseAt(x, y)
        } else if (tool is CanvasTool.Lasso) {
            lassoPointer = pointerId
            editor.content(page.id)
            if (lasso.pressButton(index, pageX(x, y, index), pageY(x, y, index)) || lasso.pressTableButton(index, pageX(x, y, index), pageY(x, y, index))) {
                syncChanges()
                return
            }
            lasso.start(index, pageX(x, y, index), pageY(x, y, index), tool.rectangle, tool.kinds, tool.fullOnly, tool.anyPortion, tool.lockedToo)
            syncChanges()
        } else if (tool is CanvasTool.Shape) {
            shapePointer = pointerId
            shapes.start(index, pageX(x, y, index), pageY(x, y, index)) { x0, y0, x1, y1 -> tool.options.item(0L, x0, y0, x1, y1) }
        } else if (tool is CanvasTool.Tape) {
            shapePointer = pointerId
            if (tool.options.lineMode == TapeLineMode.ALWAYS) {
                shapes.start(index, pageX(x, y, index), pageY(x, y, index)) { x0, y0, x1, y1 -> tool.options.item(0L, x0, y0, x1, y1) }
            } else {
                // Never straight, or straight once the pen is held: the tape follows the pen.
                shapes.startTape(index, pageX(x, y, index), pageY(x, y, index), tool.options)
                tapeHoldMs = if (tool.options.lineMode == TapeLineMode.HOLD) tool.options.holdMs.toLong() else 0L
                if (tapeHoldMs > 0L) postDelayed(tapeHold, tapeHoldMs)
            }
        } else if (tool is CanvasTool.Ink) {
            if (tool.circleToLasso && selectLoopAt(index, page.id, pageX(x, y, index), pageY(x, y, index))) return
            drawStyle = tool.style
            holdDelayMs = tool.holdToLineMs.toLong()
            drawScribble = tool.scribbleToErase
            drawCircle = tool.circleToLasso
            stabilizer = if (tool.stabilization > 0) Stabilizer(tool.stabilization) else null
            val reach = RULER_EDGE_REACH_DP * resources.displayMetrics.density
            strokeEdge = if (drawingInLoupe) Ruler.NO_EDGE else overlay.ruler?.edgeNear(x, y, reach) ?: Ruler.NO_EDGE
            // A stroke that starts when a tap times out has no event yet, and ink needs one.
            val event = currentEvent
            if (event != null) startStroke(event) else strokeWaitingForEvent = true
        }
    }

    override fun onDrawMove(pointerId: Int, x: Float, y: Float) {
        if (pointerId == loupeBoxPointer) {
            loupe.box.offsetTo(viewport.toWorldX(x) - loupeBoxGrabX, viewport.toWorldY(y) - loupeBoxGrabY)
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
            lasso.move(pageX(x, y, drawPageIndex), pageY(x, y, drawPageIndex))
            return
        }
        if (pointerId == shapePointer) {
            if (shapes.isLayingTape) {
                val slop = HOLD_SLOP_DP * resources.displayMetrics.density / viewport.pxPerPt
                if (shapes.moveTape(pageX(x, y, drawPageIndex), pageY(x, y, drawPageIndex), slop) && tapeHoldMs > 0L) {
                    removeCallbacks(tapeHold)
                    postDelayed(tapeHold, tapeHoldMs)
                }
            } else {
                if (areaKind != AreaKind.NONE && areaMoveMs == 0L) {
                    val slop = HOLD_SLOP_DP * resources.displayMetrics.density / viewport.pxPerPt
                    if (abs(pageX(x, y, drawPageIndex) - areaX) > slop || abs(pageY(x, y, drawPageIndex) - areaY) > slop) areaMoveMs = SystemClock.uptimeMillis()
                }
                shapes.move(pageX(x, y, drawPageIndex), pageY(x, y, drawPageIndex))
            }
            return
        }
        if (writingStroke && pointerId == drawPointerId) {
            writeReach = max(writeReach, hypot(x - writeDownX, y - writeDownY))
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
            removeCallbacks(tapeHold)
            if (areaKind != AreaKind.NONE) finishAreaDrag() else shapes.end()
            return
        }
        if (writingStroke && pointerId == drawPointerId) {
            endHandwritingStroke()
            return
        }
        strokeWaitingForEvent = false
        val session = erase
        if (session != null) {
            session.finish()?.let { editor?.record(it) }
            erase = null
            eraseDateInk()
            onEraseDone()
            return
        }
        if (shapes.hasSnap) {
            removeCallbacks(holdCheck)
            commitSnap()
            return
        }
        val id = strokeId ?: return
        val event = currentEvent ?: return
        removeCallbacks(holdCheck)
        // Smoothed only if the pen was still held when it lifted; moving on after the hold takes it back.
        pendingStrokes[id]?.let { if (it.smoothOnFinish && !hold.isHeld(event.eventTime, holdDelayMs)) it.smoothOnFinish = false }
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
            removeCallbacks(tapeHold)
            areaKind = AreaKind.NONE
            shapes.cancel()
            return
        }
        if (writingStroke && pointerId == drawPointerId) {
            writingStroke = false
            strokeWaitingForEvent = false
            strokeId?.let {
                wet.cancelStroke(it, currentEvent)
                pendingStrokes.remove(it)
            }
            strokeId = null
            return
        }
        strokeWaitingForEvent = false
        val session = erase
        if (session != null) {
            session.finish()?.let { editor?.discard(it) }
            erase = null
            dateEraseHits.clear()
            redrawChanges(drawPageIndex)
            return
        }
        if (shapes.hasSnap) {
            removeCallbacks(holdCheck)
            snapStickyId = null
            shapes.cancelSnap()
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
        val sticky = topItemOfType<StickyNoteItem>(page.id, x, y) ?: closedNoteNear(page.id, x, y)
        // A touch outside an open note closes it. Ink goes on to draw there, and the note tool is done with the touch.
        if (sticky == null && (tool is CanvasTool.Sticky || tool is CanvasTool.Ink) && closeOpenStickies(page.id)) {
            if (tool is CanvasTool.Sticky) return true
        }
        if (sticky != null && (tool is CanvasTool.Sticky || tool is CanvasTool.Ink)) {
            val (at, note) = sticky
            when {
                // Opening and closing a post-it is looking, not drawing: undo and redo pass over it.
                !note.isOpen -> setSticky(page.id, at, note.copy(isOpen = true), kind = StepKind.OPEN_CLOSE)
                note.isInFoldCorner(x, y) -> foldNote(page.id, at, note)
                tool is CanvasTool.Ink -> {
                    strokeStickyId = note.id
                    return false
                }
                // A touch on a post-it selects it, with its handles. Its text is typed from the button in the selection's bar.
                else -> {
                    onItemCreated()
                    selectItem(index, page.id, note.id)
                }
            }
            return true
        }
        when (val current = tool) {
            is CanvasTool.Sticker -> beginAreaDrag(AreaKind.STAMP, index, x, y, NoteOptions(), current)
            is CanvasTool.Fill -> fillAt(index, page.id, x, y, current)
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
                if (!addToOpenNote(page.id, x, y, listOf(item))) {
                    editor.perform(ItemsEdit.addOnTop(page.id, content, item))
                    syncChanges()
                }
            }
            is CanvasTool.Sticky -> beginAreaDrag(AreaKind.STICKY, index, x, y, current.options)
            is CanvasTool.Text -> {
                val found = topItemOfType<TextItem>(page.id, x, y)?.second
                if (found != null) selectText(index, page.id, found) else beginAreaDrag(AreaKind.TEXT, index, x, y, current.options)
            }
            is CanvasTool.Table -> {
                val found = topItemOfType<TableItem>(page.id, x, y)?.second
                if (found != null && lastTableTapId == found.id && SystemClock.uptimeMillis() - lastTableTapMs <= TABLE_DOUBLE_TAP_MS) {
                    lastTableTapId = 0L
                    openTableEditor(index, page.id, found.id)
                } else if (found != null) {
                    editCell(index, page.id, found, found.cellAt(x, y))
                } else {
                    val made = current.options.newTable(x, y)
                    editor.perform(ItemsEdit.addOnTop(page.id, content, made))
                    syncChanges()
                    onItemCreated()
                }
            }
            else -> return false
        }
        return true
    }

    private var gridCross = false
    private var gridThirds = false
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GRID_COLOR
    }
    private val gridMatrix = Matrix()

    /** Shows or hides the guide lines drawn over each page: a dotted cross through its middle, and its thirds. */
    fun setGrid(cross: Boolean, thirds: Boolean) {
        gridCross = cross
        gridThirds = thirds
        overlay.invalidate()
    }

    /** The guide lines belong to the page, so they move and turn with it. On a canvas with no pages they sit on the view. */
    private fun drawPageGrid(canvas: Canvas) {
        if (!gridCross && !gridThirds) return
        val layout = layout ?: return
        val density = resources.displayMetrics.density
        if (layout.isInfinite) {
            gridPaint.strokeWidth = GRID_WIDTH_DP * density
            gridPaint.pathEffect = DashPathEffect(floatArrayOf(GRID_DASH_DP * density, GRID_GAP_DP * density), 0f)
            drawGridLines(canvas, 0f, 0f, width.toFloat(), height.toFloat())
            return
        }
        val here = layout.pageIndexAt(viewport.toWorldX(width / 2f), viewport.toWorldY(height / 2f)).let { if (it < 0) currentPageIndex else it }
        val unit = density / viewport.pxPerPt
        gridPaint.strokeWidth = GRID_WIDTH_DP * unit
        gridPaint.pathEffect = DashPathEffect(floatArrayOf(GRID_DASH_DP * unit, GRID_GAP_DP * unit), 0f)
        for (index in (here - GRID_PAGES_AROUND).coerceAtLeast(0)..(here + GRID_PAGES_AROUND).coerceAtMost(layout.pageCount - 1)) {
            pageToScreenMatrix(index, gridMatrix)
            canvas.save()
            canvas.concat(gridMatrix)
            drawGridLines(canvas, 0f, 0f, layout.pageWidth(index), layout.pageHeight(index))
            canvas.restore()
        }
    }

    private fun drawGridLines(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val w = right - left
        val h = bottom - top
        if (gridCross) {
            canvas.drawLine(left + w / 2f, top, left + w / 2f, bottom, gridPaint)
            canvas.drawLine(left, top + h / 2f, right, top + h / 2f, gridPaint)
        }
        if (gridThirds) {
            for (k in 1..2) {
                canvas.drawLine(left + w * k / 3f, top, left + w * k / 3f, bottom, gridPaint)
                canvas.drawLine(left, top + h * k / 3f, right, top + h * k / 3f, gridPaint)
            }
        }
    }

    private val fillBucket = FillBucket()
    private val fillWork = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var fillRunning = false

    private var reveal: FillItem? = null
    private var revealIndex = 0
    private var revealX = 0f
    private var revealY = 0f
    private var revealStartMs = 0L
    private val revealMatrix = Matrix()
    private val revealPath = Path()
    private val revealPainter = ItemPainter()

    /** Draws the fill growing out of the tap as a circle until it covers every edge. */
    private fun drawFillReveal(canvas: Canvas) {
        val fill = reveal ?: return
        val progress = ((SystemClock.uptimeMillis() - revealStartMs).toFloat() / FILL_REVEAL_MS).coerceIn(0f, 1f)
        val box = fill.bounds
        val far = maxOf(
            hypot(box.left - revealX, box.top - revealY),
            hypot(box.right - revealX, box.top - revealY),
            hypot(box.left - revealX, box.bottom - revealY),
            hypot(box.right - revealX, box.bottom - revealY),
        )
        pageToScreenMatrix(revealIndex, revealMatrix)
        revealPath.rewind()
        revealPath.addCircle(revealX, revealY, far * progress, Path.Direction.CW)
        canvas.save()
        canvas.concat(revealMatrix)
        canvas.clipPath(revealPath)
        revealPainter.paint(canvas, fill)
        canvas.restore()
        if (progress < 1f) overlay.invalidate()
    }

    /** Called when the paint bucket found nothing to fill where it was used. */
    var onFillFailed: () -> Unit = {}

    /** Called when a fill has been put on the page. */
    var onFillDone: () -> Unit = {}

    /** Finds the closed region around a point off the main thread, then adds it under everything as one edit that can be undone. */
    private fun fillAt(index: Int, pageId: Long, x: Float, y: Float, bucket: CanvasTool.Fill) {
        val open = editor ?: return
        if (fillRunning) return
        // On an open note the bucket works on the note: what is in it, and what lies on it on the page, such as a sticker put there. All in
        // page coordinates, as on the page, with the edge of the note as the last line. The fill belongs to the note.
        val onNote = topItemOfType<StickyNoteItem>(pageId, x, y)?.takeIf { it.second.isOpen }
        val note = onNote?.second
        val pageContent = open.content(pageId)
        val pageItems = pageContent.toList().filter { pageContent.isShown(it) }
        val items = if (note == null) {
            pageItems
        } else {
            val move = Affine.translate(note.frame.left, note.frame.top)
            note.items.map { it.transformed(move) } +
                pageItems.drop(onNote.first + 1).filter { it !is StickyNoteItem && it.bounds.intersects(note.frame.bounds) }
        }
        val size = if (note != null) null else open.document.pages[index].size
        // One percent of the view's width, so a gap a pen could leave without lifting does not stop the fill.
        val gapPt = width * FILL_GAP_OF_WIDTH / viewport.pxPerPt
        val scale = viewport.pxPerPt
        val viewWidthPt = width / scale
        val viewHeightPt = height / scale
        fillRunning = true
        fillWork.execute {
            val fill = try {
                fillBucket.fill(items, size, x, y, gapPt, bucket.color, bucket.opacity, bucket.pattern, scale, viewWidthPt, viewHeightPt, note?.frame?.bounds, open::imageNow)
            } catch (e: OutOfMemoryError) {
                null
            }
            post {
                fillRunning = false
                val still = editor
                if (still !== open || fill == null) {
                    if (fill == null) onFillFailed()
                    return@post
                }
                // The color spreads from the tap to every edge in a moment, then becomes part of the page.
                reveal = fill
                revealIndex = index
                revealX = x
                revealY = y
                revealStartMs = SystemClock.uptimeMillis()
                overlay.invalidate()
                postDelayed({
                    reveal = null
                    if (editor === open) {
                        if (note != null) fillInNote(pageId, note, x, y, fill) else {
                            open.perform(fillEdit(pageId, open.content(pageId), x, y, fill))
                            syncChanges()
                        }
                        onFillDone()
                    }
                    overlay.invalidate()
                }, FILL_REVEAL_MS)
            }
        }
    }

    /** Like [fillEdit], for a note: the fill, in the note's coordinates, joins what is in it, and the note is changed as one edit. */
    private fun fillInNote(pageId: Long, was: StickyNoteItem, pageX: Float, pageY: Float, fill: FillItem) {
        val at = indexOfItem(pageId, was.id)
        val note = editor?.content(pageId)?.takeIf { at >= 0 }?.get(at) as? StickyNoteItem ?: return
        // The fill came in page coordinates and goes in the note's own.
        val x = pageX - note.frame.left
        val y = pageY - note.frame.top
        val local = fill.transformed(Affine.translate(-note.frame.left, -note.frame.top))
        val inside = note.items.toMutableList()
        val under = inside.indexOfLast { it is FillItem && it.contains(x, y) }
        if (under < 0) {
            inside.add(0, local)
        } else {
            val old = inside[under] as FillItem
            val same = old.area > 0f && abs(local.area - old.area) <= SAME_FILL_AREA * old.area
            if (same) inside[under] = local else inside.add(under + 1, local)
        }
        setSticky(pageId, at, note.copy(items = inside))
    }

    /**
     * How a new [fill] joins the page. Under everything when nothing is filled where it was used. Where something is, it takes the place
     * of that fill when it covers about the same area, which is the same region painted again, so the new color replaces the old. When it
     * covers less, say because lines were drawn in the old fill since, it goes just above it, still below whatever was drawn after.
     */
    private fun fillEdit(pageId: Long, content: PageContent, x: Float, y: Float, placedFill: FillItem): ItemsEdit {
        val fill = placedFill.onLayer(content.activeLayer)
        var under = -1
        for (i in content.size - 1 downTo 0) {
            val item = content[i]
            if (item is FillItem && content.isEditable(item) && item.contains(x, y)) {
                under = i
                break
            }
        }
        if (under < 0) return ItemsEdit(pageId, IntArray(0), emptyList(), intArrayOf(content.layerStart(content.activeLayer)), listOf(fill))
        val old = content[under] as FillItem
        val same = old.area > 0f && abs(fill.area - old.area) <= SAME_FILL_AREA * old.area
        return if (same) {
            ItemsEdit.replace(pageId, content, intArrayOf(under), listOf(fill))
        } else {
            ItemsEdit(pageId, IntArray(0), emptyList(), intArrayOf(under + 1), listOf(fill))
        }
    }

    /**
     * If an open note lies under ([x], [y]), puts [items] in it, in its own coordinates, as one edit that can be undone, and returns
     * true. They then fold away with the note and move with it. Notes do not nest, so a note among them keeps them on the page.
     */
    private fun addToOpenNote(pageId: Long, x: Float, y: Float, items: List<PageItem>): Boolean {
        if (items.any { it is StickyNoteItem }) return false
        val (at, note) = topItemOfType<StickyNoteItem>(pageId, x, y) ?: return false
        if (!note.isOpen) return false
        val inside = items.map { it.transformed(Affine.translate(-note.frame.left, -note.frame.top)) }
        setSticky(pageId, at, note.copy(items = note.items + inside))
        return true
    }

    /** Puts a copy of a sticker with its middle at the point, as one edit, and selects it so it can be moved or resized at once. */
    private fun placeSticker(
        index: Int,
        pageId: Long,
        x: Float,
        y: Float,
        sticker: CanvasTool.Sticker,
        /** If given, the sticker is scaled to fit in this frame, in the middle of it, instead of put at its own size at the point. */
        fit: Frame? = null,
        select: Boolean = true,
    ) {
        val open = editor ?: return
        val box = sticker.items.map { it.bounds }.reduce { a, b -> a.union(b) }
        val scaleBy = if (fit == null) 1f else minOf(fit.width / box.width.coerceAtLeast(1f), fit.height / box.height.coerceAtLeast(1f))
        val move = if (fit == null) {
            Affine.translate(x - box.width / 2f, y - box.height / 2f)
        } else {
            val left = fit.left + (fit.width - box.width * scaleBy) / 2f
            val top = fit.top + (fit.height - box.height * scaleBy) / 2f
            Affine(a = scaleBy, b = 0f, c = 0f, d = scaleBy, e = left - scaleBy * box.left, f = top - scaleBy * box.top)
        }
        val placed = Grouping.withFreshGroups(sticker.items.map { item ->
            // Its pictures are copied into this document, unless it already has them.
            if (item is ImageItem) {
                val target = open.assetFile(item.assetId)
                val source = sticker.assetFile(item.assetId)
                if (!target.exists() && source != null && source.exists()) source.copyTo(target)
            }
            item.transformed(move).withId(IdSource.Random.next()).withLocked(false)
        })
        // A sticker put on an open note belongs to it, and folds away with it.
        if (addToOpenNote(pageId, x, y, placed)) return
        val edit = ItemsEdit.addAllOnTop(pageId, open.content(pageId), placed)
        open.perform(edit)
        if (select) {
            lasso.select(index, Selection(pageId, edit.addedAt, edit.added))
            selectionKeepsTool = true
        }
        syncChanges()
    }

    /** Drops a sticker dragged from the list at a place on the screen, without selecting it. False if that is not on a page. */
    fun dropSticker(screenX: Float, screenY: Float, sticker: CanvasTool.Sticker): Boolean {
        val open = editor ?: return false
        val layout = layout ?: return false
        val index = layout.pageIndexAt(viewport.toWorldX(screenX), viewport.toWorldY(screenY))
        if (index < 0) return false
        val page = open.document.pages[index]
        placeSticker(index, page.id, pageX(screenX, screenY, index), pageY(screenX, screenY, index), sticker, select = false)
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
                layout.turn(index),
                viewport.toScreenX(layout.centerX(index)),
                viewport.toScreenY(layout.centerY(index)),
            ),
        )
        return true
    }

    private inline fun <reified T : PageItem> topItemOfType(pageId: Long, x: Float, y: Float): Pair<Int, T>? {
        val content = editor?.content(pageId) ?: return null
        var lockedHit: Pair<Int, T>? = null
        for (i in content.size - 1 downTo 0) {
            val item = content[i]
            if (item is T && content.isEditable(item) && item.bounds.contains(x, y)) {
                // Locked items are a layer below the rest, so an item that is not locked wins.
                if (!item.locked) return i to item
                if (lockedHit == null) lockedHit = i to item
            }
        }
        return lockedHit
    }

    private fun indexOfItem(pageId: Long, id: Long): Int {
        val content = editor?.content(pageId) ?: return -1
        for (i in 0 until content.size) if (content[i].id == id) return i
        return -1
    }

    private fun setSticky(pageId: Long, at: Int, note: StickyNoteItem, onTop: Boolean = false, kind: StepKind = StepKind.STEP) {
        val editor = editor ?: return
        val content = editor.content(pageId)
        editor.perform(
            if (onTop) ItemsEdit.replaceOnTop(pageId, content, intArrayOf(at), listOf(note)) else ItemsEdit.replace(pageId, content, intArrayOf(at), listOf(note)),
            kind,
        )
        syncChanges()
    }

    private fun addToSticky(editor: NoteEditor, pageId: Long, stickyId: Long, stroke: PageItem): Boolean {
        val at = indexOfItem(pageId, stickyId)
        val note = editor.content(pageId).takeIf { at >= 0 }?.get(at) as? StickyNoteItem ?: return false
        val relative = stroke.transformed(Affine.translate(-note.frame.left, -note.frame.top))
        setSticky(pageId, at, note.copy(items = note.items + relative))
        return true
    }

    private enum class AreaKind { NONE, STICKY, TEXT, STAMP }

    private var areaKind = AreaKind.NONE
    private var areaIndex = 0
    private var areaX = 0f
    private var areaY = 0f
    private var areaOptions = NoteOptions()
    private var areaDownMs = 0L
    private var areaMoveMs = 0L
    private var areaSticker: CanvasTool.Sticker? = null

    /** Asked for when a sticker made by holding and dragging is done: the sticker tool has been used. */
    var onReturnTool: () -> Unit = {}

    /** A stroke of ink was put on the page, and an erase was finished: the tool in hand has been used. */
    var onInkDrawn: () -> Unit = {}
    var onEraseDone: () -> Unit = {}

    /** A strip of tape was laid on the page. */
    var onTapeLaid: () -> Unit = {}

    /** A touch that places a note or text: a tap puts one at the default size, and a drag sizes it. */
    private fun beginAreaDrag(kind: AreaKind, index: Int, x: Float, y: Float, options: NoteOptions, sticker: CanvasTool.Sticker? = null) {
        areaKind = kind
        areaDownMs = SystemClock.uptimeMillis()
        areaMoveMs = 0L
        areaSticker = sticker
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
        // Sized by dragging, or held: the tool in use before comes back. A tap, which puts one down at its default size, does not.
        val held = (if (areaMoveMs == 0L) SystemClock.uptimeMillis() else areaMoveMs) - areaDownMs >= HOLD_TO_SIZE_MS
        when (kind) {
            AreaKind.STICKY -> {
                val note = areaOptions.newSticky(areaX, areaY, general = stickyOpacity).let { if (dragged != null) it.copy(frame = dragged) else it }
                open.perform(ItemsEdit.addOnTop(page.id, open.content(page.id), note))
                syncChanges()
                // The new note is not selected: the tool in use before comes back. It never opens a text box.
                onItemCreated()
            }
            AreaKind.STAMP -> {
                val sticker = areaSticker ?: return
                when {
                    dragged != null -> placeSticker(areaIndex, page.id, areaX, areaY, sticker, fit = dragged, select = false)
                    held -> placeSticker(areaIndex, page.id, areaX, areaY, sticker, select = false)
                    else -> {
                        onItemCreated()
                        placeSticker(areaIndex, page.id, areaX, areaY, sticker, select = false)
                    }
                }
                if (held || dragged != null) onReturnTool()
            }
            AreaKind.TEXT -> {
                val item = TextItem(
                    IdSource.Random.next(),
                    dragged ?: Frame(areaX, areaY, NEW_TEXT_WIDTH_PT, NEW_TEXT_HEIGHT_PT),
                    "",
                    areaOptions.textSize.toFloat(),
                    areaOptions.textColor,
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
        var closed = false
        var i = 0
        // Folding a note takes the items on it off the page, so the content is read again each time.
        while (i < open.content(pageId).size) {
            val note = open.content(pageId)[i] as? StickyNoteItem
            if (note != null && note.isOpen && !note.keepOpen) {
                foldNote(pageId, i, note)
                closed = true
            }
            i++
        }
        return closed
    }

    /**
     * Folds the open [note] at index [at]. Whatever lies on it on the page, above it, goes into it (see [NoteFold]: more than 80 percent
     * within its edges, tape more than 5 percent), so it folds away with the note and comes back whole when the note opens. One edit.
     */
    private fun foldNote(pageId: Long, at: Int, note: StickyNoteItem) {
        val open = editor ?: return
        val content = open.content(pageId)
        val on = (at + 1 until content.size).filter { i -> content.isEditable(content[i]) && NoteFold.collapses(content[i], note) }
        if (on.isEmpty()) return setSticky(pageId, at, note.copy(isOpen = false), kind = StepKind.OPEN_CLOSE)
        val inside = on.map { content[it].transformed(Affine.translate(-note.frame.left, -note.frame.top)) }
        val folded = note.copy(isOpen = false, items = note.items + inside)
        open.perform(ItemsEdit(pageId, intArrayOf(at) + on.toIntArray(), listOf<PageItem>(note) + on.map { content[it] }, intArrayOf(at), listOf(folded)), StepKind.FOLD)
        syncChanges()
    }

    /** The fold corner reaches [STICKY_CORNER_DP] grown by [STICKY_CLOSE_GROWTH] each way, so closing a post-it is easy to hit. */
    private fun StickyNoteItem.isInFoldCorner(x: Float, y: Float): Boolean {
        val reach = STICKY_CORNER_DP * STICKY_CLOSE_GROWTH * resources.displayMetrics.density / viewport.pxPerPt
        return x > frame.left + frame.width - reach && y > frame.top + frame.height - reach
    }

    /**
     * The top closed post-it whose tap target holds ([x], [y]): the folded square made half as wide and half as tall again about its
     * middle, so it opens without hitting it exactly. Null if there is none.
     */
    private fun closedNoteNear(pageId: Long, x: Float, y: Float): Pair<Int, StickyNoteItem>? {
        val content = editor?.content(pageId) ?: return null
        for (i in content.size - 1 downTo 0) {
            val note = content[i] as? StickyNoteItem ?: continue
            if (note.isOpen || note.locked || !content.isEditable(note) || !content.isShown(note)) continue
            val box = note.bounds
            val growX = box.width * (STICKY_TAP_GROWTH - 1f) / 2f
            val growY = box.height * (STICKY_TAP_GROWTH - 1f) / 2f
            if (x >= box.left - growX && x <= box.right + growX && y >= box.top - growY && y <= box.bottom + growY) return i to note
        }
        return null
    }

    private fun editStickyText(index: Int, pageId: Long, note: StickyNoteItem) {
        val options = (tool as? CanvasTool.Sticky)?.options ?: stickyOptions
        val inset = NoteOptions.STICKY_TEXT_INSET
        val size = options.textSize.toFloat()
        beginTextEdit(
            index,
            Frame(note.frame.left + inset, note.frame.top + inset, note.frame.width - 2 * inset, note.frame.height - 2 * inset),
            note.typedText,
            size,
            NoteOptions.TEXT_COLOR,
            options.style,
            emptyList(),
        ) { text, _, _, _, _, _ ->
            val at = indexOfItem(pageId, note.id)
            val latest = editor?.content(pageId)?.takeIf { at >= 0 }?.get(at) as? StickyNoteItem
            if (latest != null && text != latest.typedText) setSticky(pageId, at, latest.withText(text, size, options.style), onTop = true)
        }
    }

    /** Leaves the saved text of [id] off the page while its box is open over it, so it is not drawn twice. */
    private fun hideTextBehindBox(id: Long) {
        val paint = painter ?: return
        if (paint.hiddenItemId == id) return
        paint.hiddenItemId = id
        tiles.invalidateAll()
    }

    private fun editText(index: Int, pageId: Long, item: TextItem, isNew: Boolean) {
        if (!isNew) hideTextBehindBox(item.id)
        // Each block written is a step of undo of its own, put on the page as the block ends, while the box stays open. See [TextBlocks].
        val put = { text: String, style: TextStyle, size: Float, spans: List<TextSpan>, color: Int ->
            val editor = editor
            if (editor != null) {
                val content = editor.content(pageId)
                val at = indexOfItem(pageId, item.id)
                val onPage = if (at >= 0) content[at] as? TextItem else null
                // The box is as tall as its text, and the page paints a text box only where its frame is.
                // A box whose text, look and size are unchanged keeps its frame: refitting it would move its text for nothing.
                val base = onPage ?: item
                val changed = base.copy(text = text, style = style, textSize = size, spans = spans, color = color)
                val edited = if (changed == base) base else TextFit.fitted(changed)
                TextBlocks.between(pageId, content, onPage, edited)?.let { editor.perform(it) }
                syncChanges()
            }
        }
        beginTextEdit(index, item.frame, item.text, item.textSize, item.color, item.style, item.spans) { text, style, size, spans, _, color ->
            hideTextBehindBox(0L)
            put(text, style, size, spans, color)
        }
        // The box is still open over the text a block puts on the page, so that text is kept off the page too, or it is drawn twice.
        textEdit?.block = { text, style, size, spans, _, color ->
            hideTextBehindBox(item.id)
            put(text, style, size, spans, color)
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
        beginTextEdit(index, box, table.cells[cell], table.textSize, NoteOptions.TEXT_COLOR, TextStyle(), emptyList()) { text, _, _, _, _, _ ->
            val editor = editor ?: return@beginTextEdit
            val at = indexOfItem(pageId, table.id)
            val latest = editor.content(pageId).takeIf { at >= 0 }?.get(at) as? TableItem
            if (latest != null && text != latest.cells[cell]) {
                editor.perform(ItemsEdit.replaceOnTop(pageId, editor.content(pageId), intArrayOf(at), listOf(latest.withCell(cell, text))))
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
        spans: List<TextSpan>,
        commit: (String, TextStyle, Float, List<TextSpan>, Float, Int) -> Unit,
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
            size,
            spans,
            commit,
            layout.turn(index),
            viewport.toScreenX(layout.centerX(index)),
            viewport.toScreenY(layout.centerY(index)),
        )
        textEdit = request
        checkWritingReady()
        textEditIndex = index
        textEditBox = box
        textEditSize = size
        onTextEdit(request)
    }

    // ---- Pages ----

    val pages get() = editor?.document?.pages.orEmpty()

    val canAddPages get() = editor?.document?.kind != DocumentKind.CANVAS && editor != null

    /** Adds a page like the one at [after], right behind it. */
    fun addPage(after: Int) {
        val open = editor ?: return
        val pages = open.document.pages
        val tapped = pages.getOrNull(after) ?: return
        // A new page is never like the cover: it follows the first page behind it, or the paper of the notebook.
        val like = if (tapped.isCover) pages.getOrNull(after + 1) ?: tapped else tapped
        open.perform(InsertPage(after + 1, Page(IdSource.Random.next(), like.size, if (like.isCover) PageBackground() else like.background)))
        syncChanges()
    }

    /** Adds the pages of a PDF behind the last page, as one undoable step. */
    fun importPdfPages(uri: Uri, unsupported: () -> Unit, failed: () -> Unit) {
        val open = editor ?: return
        if (!canAddPages) return unsupported()
        open.importPdf(context.contentResolver, uri) { assetId, sizes ->
            if (sizes == null) {
                failed()
            } else if (editor === open) {
                val end = open.document.pages.size
                open.perform(
                    BatchEdit(
                        sizes.mapIndexed { i, size ->
                            InsertPage(end + i, Page(IdSource.Random.next(), size, source = PageSource.PdfPage(assetId, i)))
                        },
                    ),
                )
                syncChanges()
                goToPage(end)
            }
        }
    }

    /** Sets this notebook's own template language ("en" or "es"), draws its pages again, and keeps it with the notebook. */
    fun setTemplateLanguage(code: String) {
        val open = editor ?: return
        open.store.writeTemplateLanguage(code, touch = true)
        app.betternotes.ui.theme.TemplateText.documentLanguage = code
        tiles.invalidateAll()
        overlay.invalidate()
    }

    /** A notebook can take a cover unless it is a canvas or a typed notebook. */
    val canHaveCover get() = editor?.document?.let {
        it.kind != DocumentKind.PLANNER && (it.coverId != null || (it.canAddPages && it.pages.first().size != null))
    } == true

    /** Adds, changes (keeping what was drawn on it) or takes out the cover, as one undoable step. Null takes it out. */
    fun setCover(coverId: String?, landscape: Boolean? = null, editable: Boolean = false, recolor: Map<Int, Int> = emptyMap()) {
        val open = editor ?: return
        val pages = open.document.pages
        val before = pages.first().takeIf { it.isCover }
        // The cover may be turned to the other orientation, which swaps its width and height; null leaves it as it is.
        val base = pages.first().size ?: return
        val size = if (landscape != null && (base.widthPt > base.heightPt) != landscape) PageSize(base.heightPt, base.widthPt) else base
        // An editable cover holds its design as items of its own: the drawn design is turned off (the custom cover) and the
        // design's fills, lines and dots are put on the page beneath whatever is already drawn there.
        val design = if (editable || recolor.isNotEmpty()) CoverDesign.byId(coverId)?.takeIf { it.id != CoverDesign.CUSTOM_ID } else null
        val storedId = if (design != null) CoverDesign.CUSTOM_ID else coverId
        val after = when {
            coverId == null -> null
            before != null -> before.copy(cover = storedId, size = size)
            else -> Page(IdSource.Random.next(), size, cover = storedId)
        }
        if (before == after || (before == null && !open.document.canAddPages)) return
        lasso.clear()
        val edit: Edit = if (design != null && after != null) {
            val items = design.toItems(size.widthPt, size.heightPt, recolor = recolor)
            BatchEdit(listOf(SetCover(before, after), ItemsEdit(after.id, IntArray(0), emptyList(), IntArray(items.size) { it }, items)))
        } else {
            SetCover(before, after)
        }
        open.perform(edit)
        syncChanges()
        if (after != null) goToPage(0)
    }

    fun deletePage(index: Int) {
        val open = editor ?: return
        val page = open.document.pages.getOrNull(index) ?: return
        if (open.document.pages.size < 2 || page.isCover) return
        lasso.clear()
        open.perform(RemovePage(index, page))
        syncChanges()
    }

    fun movePage(from: Int, to: Int) {
        val open = editor ?: return
        if (from !in open.document.pages.indices || to !in open.document.pages.indices) return
        if (open.document.coverId != null && (from == 0 || to == 0)) return
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

    /** The PDF's own picture of a page, or null if it is not a PDF page. Rendered in the background and cached. */
    suspend fun pdfThumbnail(pageId: Long, widthPx: Int): Bitmap? {
        val open = editor ?: return null
        val source = open.document.pages.firstOrNull { it.id == pageId }?.source as? PageSource.PdfPage ?: return null
        return PdfThumbnails.cached(open.store, source, widthPx)
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
    fun addAudio(assetId: String, durationMs: Long): Long? {
        val open = editor ?: return null
        val page = open.document.pages.getOrNull(currentPage) ?: return null
        val item = AudioItem(IdSource.Random.next(), Frame(0f, 0f, 0f, 0f), assetId, durationMs)
        open.perform(ItemsEdit.addOnTop(page.id, open.content(page.id), item))
        return item.id
    }

    /** Copies a recording in. With [place], it also lands as a bar in the middle of the view. */
    fun importAudio(uri: Uri, place: Boolean = false, failed: () -> Unit) {
        val open = editor ?: return
        open.importAsset(context.contentResolver, uri) { assetId ->
            if (assetId == null) {
                failed()
            } else if (editor === open) {
                val id = addAudio(assetId, audioDurationMs(open.assetFile(assetId)))
                if (place && id != null) placeAudio(id, width / 2f, height / 2f)
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

    private fun drawLineGuide(canvas: Canvas) {
        if (!lineGuideOn || !straightening) return
        val layout = layout ?: return
        val start = screenOf(drawPageIndex, strokeStartX, strokeStartY)
        val end = screenOf(drawPageIndex, lineGuideEndX, lineGuideEndY)
        angleGuide.draw(canvas, start[0], start[1], end[0], end[1], AngleGuide.angle(strokeStartX, strokeStartY, lineGuideEndX, lineGuideEndY))
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
            // A turned page is searched whole: its bars sit anywhere in it.
            val region = if (layout.turn(index) == 0f) {
                Bounds(view.left - ox, view.top - oy, view.right - ox, view.bottom - oy)
            } else {
                Bounds(-Float.MAX_VALUE / 4f, -Float.MAX_VALUE / 4f, Float.MAX_VALUE / 4f, Float.MAX_VALUE / 4f)
            }
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
                        if (carried) audioPenX - item.frame.width * scale / 2f else screenOf(index, item.frame.left, item.frame.top)[0],
                        if (carried) audioPenY - item.frame.height * scale / 2f else screenOf(index, item.frame.left, item.frame.top)[1],
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
            AudioTouch.SEEKING -> audio?.seek(item, AudioBar.fractionAt(item.frame, pageX(x, y, audioPage)))
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
            AudioBar.Zone.WAVE -> playback.seek(item, AudioBar.fractionAt(item.frame, audioDownX.let { pageX(it, audioDownY, audioPage) }))
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
        val moved = found.copy(frame = AudioBar.centeredAt(pageX(screenX, screenY, target), pageY(screenX, screenY, target), open = !wasCollapsed))
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
                            onItemCreated()
                        }
                }
            }
        }
    }

    /** Opens the text of the one post-it that is selected for typing. */
    fun editSelectedNoteText() {
        val open = editor ?: return
        val chosen = lasso.selection?.takeIf { it.items.size == 1 }?.let { it.items[0] as? StickyNoteItem } ?: return
        val index = open.document.pages.indexOfFirst { it.id == lasso.selection?.pageId }
        if (index < 0) return
        editStickyText(index, lasso.selection!!.pageId, chosen)
    }

    /** Selects the item with [id] on page [pageId], showing its handles, with the lasso lent to work them. */
    private fun selectItem(index: Int, pageId: Long, id: Long) {
        val open = editor ?: return
        val at = indexOfItem(pageId, id)
        if (at < 0) return
        lasso.select(index, Selection(pageId, intArrayOf(at), listOf(open.content(pageId)[at])))
        selectionKeepsTool = true
        syncChanges()
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
                options.textColor,
                options.style,
            )
            open.perform(ItemsEdit.addOnTop(pageId, open.content(pageId), item))
        }
    }

    /**
     * Puts items copied earlier in the middle of the view, as new items. Pictures, videos and recordings whose file this
     * document does not have are left out.
     */
    fun pasteItems(items: List<PageItem>) {
        val open = editor ?: return
        val usable = items.filter { item ->
            when (item) {
                is ImageItem -> assetFile(item.assetId) != null
                is VideoItem -> assetFile(item.assetId) != null
                is AudioItem -> assetFile(item.assetId) != null
                else -> true
            }
        }
        if (usable.isEmpty()) return
        placeInView { pageId, x, y, _, _ ->
            val box = usable.map { it.bounds }.reduce(Bounds::union)
            val move = Affine.translate(x - box.centerX, y - box.centerY)
            val placed = Grouping.withFreshGroups(usable.map { it.transformed(move).withId(IdSource.Random.next()).withLocked(false) })
            open.perform(ItemsEdit.addAllOnTop(pageId, open.content(pageId), placed))
        }
    }

    /**
     * Puts copies of items from another open notebook in the middle of what is on screen, bringing their pictures, recordings
     * and videos along. [sourceAsset] finds an asset's file in the notebook they come from.
     */
    fun pasteFrom(items: List<PageItem>, sourceAsset: (String) -> File?) {
        val open = editor ?: return
        for (item in items) {
            val assetId = when (item) {
                is ImageItem -> item.assetId
                is VideoItem -> item.assetId
                is AudioItem -> item.assetId
                else -> continue
            }
            val target = open.assetFile(assetId)
            val source = sourceAsset(assetId)
            if (!target.exists() && source != null && source.exists()) source.copyTo(target)
        }
        pasteItems(items)
    }

    private inline fun placeInView(place: (pageId: Long, x: Float, y: Float, maxWidth: Float, maxHeight: Float) -> Unit) {
        val open = editor ?: return
        val layout = layout ?: return
        val index = currentPage
        val page = open.document.pages.getOrNull(index) ?: return
        val world = viewport.visibleWorld(width, height)
        layout.worldToPage(index, world.centerX, world.centerY, mapped)
        place(
            page.id,
            mapped[0],
            mapped[1],
            world.width * PLACED_SHARE_OF_VIEW,
            world.height * PLACED_SHARE_OF_VIEW,
        )
        syncChanges()
    }


    // ---- Writing by hand into a text box ----

    /** One stroke of handwriting waiting to be read: the pen's path in view pixels, and when, from the stroke's start. */
    private class WrittenStroke(
        val screen: FloatArray,
        val timeMs: LongArray,
        val widthPx: Float,
        val page: StrokePoints,
        val pageId: Long,
    )

    private class ReadWord(
        val request: TextEditRequest,
        val text: String,
        val strokes: List<FloatArray>,
        val widthPx: Float,
        val ids: List<InProgressStrokeId>,
    )

    /** How long the pen rests after a stroke for the word to count as finished. */
    var handwritingPauseMs = WORD_PAUSE_MS

    private val written = ArrayList<WrittenStroke>()
    private val writtenIds = ArrayList<InProgressStrokeId>()
    private val writingWet = HashSet<InProgressStrokeId>()
    private val landing = ArrayDeque<ReadWord>()
    private var recognizing = 0
    private var writingStroke = false
    private var writingReady = false
    private var writeDownX = 0f
    private var writeDownY = 0f
    private var writeDownMs = 0L
    private var writeReach = 0f
    private val wordEnd = Runnable { convertWord() }
    private var reader: MlKitRecognizer? = null
    private var readerThread: ExecutorService? = null

    private fun readerThread() = readerThread ?: Executors.newSingleThreadExecutor { Thread(it, "word-ink").apply { isDaemon = true } }
        .also { readerThread = it }

    private fun reader() = reader ?: MlKitRecognizer().also { reader = it }

    /** Handwriting is read only once the models are on the device, so until then the pen acts as it always did. */
    private fun checkWritingReady() {
        if (writingReady) return
        readerThread().execute {
            val ready = try {
                reader().isReady
            } catch (_: RuntimeException) {
                false
            }
            post {
                writingReady = ready
                if (!ready && textEdit != null) onWritingModelsMissing()
            }
        }
    }

    private fun startHandwriting(x: Float, y: Float) {
        writingStroke = true
        writeDownX = x
        writeDownY = y
        writeDownMs = SystemClock.uptimeMillis()
        writeReach = 0f
        removeCallbacks(wordEnd)
        strokeStickyId = null
        drawStyle = WRITING_STYLE
        holdDelayMs = 0L
        drawScribble = false
        drawCircle = false
        stabilizer = null
        strokeEdge = Ruler.NO_EDGE
        val event = currentEvent
        if (event != null) startStroke(event) else strokeWaitingForEvent = true
    }

    private fun inkPending() = written.isNotEmpty() || recognizing > 0 || landing.isNotEmpty() || flight.isFlying

    private var afterWriting: (() -> Unit)? = null

    /** Whether the screen point ([x], [y]) is within reach of a stroke written and still waiting to be read, as the dot of an "i" would be. */
    private fun nearWrittenInk(x: Float, y: Float): Boolean {
        val reach = WRITTEN_INK_REACH_DP * resources.displayMetrics.density
        return written.any { stroke ->
            var near = false
            var i = 0
            while (i < stroke.screen.size && !near) {
                near = hypot(stroke.screen[i] - x, stroke.screen[i + 1] - y) <= reach
                i += 2
            }
            near
        }
    }

    /**
     * Lets the handwriting that is waiting, being read or flying into the text become text, with its animation, and only then runs [then].
     * Says whether it had to wait; when it did not, [then] has not run and is the caller's to do.
     */
    private fun afterWordsLand(then: () -> Unit): Boolean {
        if (!inkPending()) return false
        afterWriting = then
        removeCallbacks(wordEnd)
        if (written.isNotEmpty()) convertWord()
        settleWriting()
        return true
    }

    /** Runs what waits for the handwriting once none of it is left to land. */
    private fun settleWriting() {
        val then = afterWriting ?: return
        removeCallbacks(settleCheck)
        if (inkPending()) {
            // The last word's flight is still ending when it reports that it is done, so look again shortly.
            postDelayed(settleCheck, SETTLE_POLL_MS)
            return
        }
        afterWriting = null
        then()
    }

    private val settleCheck = Runnable { settleWriting() }

    private fun endHandwritingStroke() {
        writingStroke = false
        strokeWaitingForEvent = false
        val id = strokeId
        strokeId = null
        val touch = TAP_REACH_DP * resources.displayMetrics.density
        val stationary = writeReach < touch && SystemClock.uptimeMillis() - writeDownMs < TAP_MAX_MS
        // A touch on the line of the circle just written is the lasso that circle was drawn for, not a dot after an "o".
        if (stationary && lassoFromWrittenLoop(id)) return
        // A dot belongs to words: it follows ink still waiting to be read or text just read. Alone, the touch is a tap.
        // On the box a lone touch is neither a dot nor a way out: it leaves the box as it is.
        if (stationary && !inkPending() && insideOpenBox(writeDownX, writeDownY)) {
            if (id != null) {
                wet.cancelStroke(id, currentEvent)
                pendingStrokes.remove(id)
            }
            return
        }
        // A lone touch away from the box and from the words waiting is a tap away from the text while words are still being written: they are
        // not thrown away, they finish turning into text, and then the typing ends as for any tap away.
        val away = stationary && inkPending() && !insideOpenBox(writeDownX, writeDownY) && !nearWrittenInk(writeDownX, writeDownY)
        val tapped = stationary && !inkPending()
        if (tapped || away) {
            if (id != null) {
                wet.cancelStroke(id, currentEvent)
                pendingStrokes.remove(id)
            }
            // A touch with nothing written is what it was before: it ends the typing and puts a box or an edit where it landed.
            val typing = textEdit != null
            val index = drawPageIndex
            val atX = pageX(writeDownX, writeDownY, index)
            val atY = pageY(writeDownX, writeDownY, index)
            val endTyping = {
                endTextEdit()
                // Away from any text, a touch that ends the typing also ends the text tool.
                val pageId = editor?.document?.pages?.get(index)?.id
                if (typing && pageId != null && topItemOfType<TextItem>(pageId, atX, atY) == null) onTextToolLeft() else handlePlacement(index, atX, atY)
                if (areaKind != AreaKind.NONE) {
                    shapePointer = -1
                    finishAreaDrag()
                }
            }
            if (!away || !afterWordsLand(endTyping)) endTyping()
            return
        }
        val event = currentEvent ?: return
        if (id != null) wet.finishStroke(event, drawPointerId, id)
    }

    /**
     * A circle was written and its line is touched now: the circle is not read as an "o" and the touch is not a dot.
     * The circle's ink goes, the typing ends, and what the circle surrounds is selected. [touchId] is the touch's own ink.
     */
    private fun lassoFromWrittenLoop(touchId: InProgressStrokeId?): Boolean {
        val last = written.lastOrNull() ?: return false
        if (!StrokeGestures.isClosedLoop(last.page)) return false
        val index = drawPageIndex
        val reach = LOOP_TAP_REACH_DP * resources.displayMetrics.density / viewport.pxPerPt
        if (!StrokeGestures.isNearLine(last.page, pageX(writeDownX, writeDownY, index), pageY(writeDownX, writeDownY, index), reach)) return false
        if (touchId != null) {
            wet.cancelStroke(touchId, currentEvent)
            pendingStrokes.remove(touchId)
        }
        val loopId = writtenIds.lastOrNull()
        val loop = last
        written.removeAt(written.size - 1)
        if (writtenIds.isNotEmpty()) writtenIds.removeAt(writtenIds.size - 1)
        if (loopId != null) dropWet(listOf(loopId))
        if (written.isEmpty()) removeCallbacks(wordEnd)
        endTextEdit()
        if (!selectInsideLoop(index, loop.pageId, loop.page, null)) onTextToolLeft()
        return true
    }

    private fun collectWritten(id: InProgressStrokeId, stroke: Stroke, pending: PendingStroke) {
        val points = stroke.inputs.toStrokePoints()
        val screen = FloatArray(points.size * 2)
        for (i in 0 until points.size) {
            screen[2 * i] = points.x(i)
            screen[2 * i + 1] = points.y(i)
        }
        pending.toScreen?.mapPoints(screen)
        // A scribble on typed letters rubs out the ones it touches, by the same gesture that rubs out ink; it is not read as handwriting.
        val open = textEdit
        if (open != null && StrokeGestures.isScribble(points) && scribbleOutLetters(open, screen, pending.style.width * viewport.pxPerPt)) {
            dropWet(listOf(id))
            return
        }
        written += WrittenStroke(screen, LongArray(points.size) { points.timeMs(it).toLong() }, pending.style.width * viewport.pxPerPt, points, pending.pageId)
        writtenIds += id
        writingWet += id
        removeCallbacks(wordEnd)
        if (!writingStroke) postDelayed(wordEnd, handwritingPauseMs)
    }

    /**
     * Takes out the letters of [request] that the scribble [screen] (x, y pairs in view pixels, [widthPx] wide) passes over, and says whether it
     * touched any. A scribble that touches none is left to be read as handwriting.
     */
    private fun scribbleOutLetters(request: TextEditRequest, screen: FloatArray, widthPx: Float): Boolean {
        // The scribble in the box's own axes: the page may be turned.
        val radians = Math.toRadians(-request.turnDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val xs = FloatArray(screen.size / 2)
        val ys = FloatArray(screen.size / 2)
        for (i in xs.indices) {
            val dx = screen[2 * i] - request.pivotX
            val dy = screen[2 * i + 1] - request.pivotY
            xs[i] = request.pivotX + dx * cos - dy * sin - request.left
            ys[i] = request.pivotY + dx * sin + dy * cos - request.top
        }
        val reach = widthPx / 2f
        val touched = HashSet<Int>()
        for (index in request.text.indices) {
            val box = request.charBox(index) ?: continue
            if (box[2] - box[0] <= 0f) continue
            if (passesOver(xs, ys, box, reach)) touched += index
        }
        if (touched.isEmpty()) return false
        request.deleteChars(touched)
        return true
    }

    /** Whether the path of [xs], [ys] comes within [reach] of [box] (left, top, right, bottom), looking at points along each of its sides. */
    private fun passesOver(xs: FloatArray, ys: FloatArray, box: FloatArray, reach: Float): Boolean {
        fun inside(x: Float, y: Float) = x >= box[0] - reach && x <= box[2] + reach && y >= box[1] - reach && y <= box[3] + reach
        for (i in xs.indices) {
            if (inside(xs[i], ys[i])) return true
            if (i == 0) continue
            val steps = (hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) / SCRIBBLE_SAMPLE_PX).toInt()
            for (s in 1..steps) {
                val t = s / (steps + 1f)
                if (inside(xs[i - 1] + (xs[i] - xs[i - 1]) * t, ys[i - 1] + (ys[i] - ys[i - 1]) * t)) return true
            }
        }
        return false
    }

    /** The pen has rested: what was written since is one word, which is read off the main thread. */
    private fun convertWord() {
        if (written.isEmpty()) return
        val strokes = written.toList()
        val ids = writtenIds.toList()
        written.clear()
        writtenIds.clear()
        val request = textEdit
        if (request == null || !writingReady) {
            dropWet(ids)
            return settleWriting()
        }
        recognizing++
        val ink = strokes.map { s ->
            WordStroke(
                FloatArray(s.timeMs.size) { s.screen[2 * it] },
                FloatArray(s.timeMs.size) { s.screen[2 * it + 1] },
                s.timeMs,
            )
        }
        val before = request.text.takeLast(PRE_CONTEXT_CHARS)
        val widthPx = strokes.maxOf { it.widthPx }
        val screens = strokes.map { it.screen }
        readerThread().execute {
            val text = try {
                reader().readWord(ink, before)
            } catch (_: IOException) {
                null
            } catch (_: RuntimeException) {
                null
            }
            post {
                recognizing--
                if (text == null || textEdit !== request) {
                    dropWet(ids)
                    settleWriting()
                } else {
                    landing += ReadWord(request, text, screens, widthPx, ids)
                    flyNextWord()
                }
            }
        }
    }

    /** Words land one at a time, so each finds the end of the text where the one before left it. */
    private fun flyNextWord() {
        if (flight.isFlying) return
        val next = landing.removeFirstOrNull() ?: return settleWriting()
        val request = next.request
        if (textEdit !== request) {
            dropWet(next.ids)
            return flyNextWord()
        }
        val target = WordTarget(
            left = request.left + request.endX,
            leadingSpace = request.text.isNotEmpty() && !request.text.last().isWhitespace(),
            lineTop = request.top + request.endY,
            lineHeight = request.lineHeight,
            textSizePx = request.textSizePx,
            color = request.color,
            bold = request.style.bold,
            italic = request.style.italic,
            turnDegrees = request.turnDegrees,
            pivotX = request.pivotX,
            pivotY = request.pivotY,
        )
        // The flight draws the ink from where it lay, so taking the pen's own ink away shows no change.
        flight.fly(next.strokes, next.widthPx, WRITING_STYLE.color, next.text, target) {
            if (textEdit === request) request.append(next.text)
            flyNextWord()
        }
        dropWet(next.ids)
    }

    private fun dropWet(ids: List<InProgressStrokeId>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        writingWet.removeAll(set)
        wet.removeFinishedStrokes(set)
    }

    /** Ends any word being written, without adding it. */
    private fun discardWriting() {
        removeCallbacks(wordEnd)
        if (!inkPending() && writingWet.isEmpty()) return
        dropWet(writtenIds.toList())
        written.clear()
        writtenIds.clear()
        for (word in landing) dropWet(word.ids)
        landing.clear()
        flight.clear()
    }

    /** Whether the open text box lies under the screen point ([x], [y]), or within [marginPx] of its edge, turned as the page is. */
    private fun insideOpenBox(x: Float, y: Float, marginPx: Float = 0f): Boolean {
        val request = textEdit ?: return false
        val height = maxOf(request.height, request.contentHeightPx)
        return TurnedBox.contains(x, y, request.left, request.top, request.width, height, request.turnDegrees, request.pivotX, request.pivotY, marginPx)
    }

    /** Whether a pen at the screen point writes into the open text box: the app reads handwriting there as it does around it. */
    fun penWritesAt(x: Float, y: Float): Boolean {
        if (tool !is CanvasTool.Text || !writingReady || textEdit == null) return false
        if (insideOpenBox(x, y)) return true
        // While a box is open the pen writes wherever it lands, near the box or far from it, except over a toolbar or a panel. Around the box the
        // text field keeps a strip of its own where it waits for the pen to start writing in it, and it would take a pen that lands there.
        return overlayBounds.none { it.contains(x, y) }
    }

    /** Finishes any text being typed. */
    fun endTextEdit() {
        discardWriting()
        val request = textEdit ?: return
        textEdit = null
        request.commitNow()
        onTextEdit(null)
    }

    private val mapped = FloatArray(2)

    /** The point of page [index] that lies under a point of the screen, or under the loupe's window when drawing in it. */
    private fun pageAt(screenX: Float, screenY: Float, index: Int, axis: Int): Float {
        val layout = layout ?: return 0f
        val worldX = if (drawingInLoupe) loupe.worldX(screenX) else viewport.toWorldX(screenX)
        val worldY = if (drawingInLoupe) loupe.worldY(screenY) else viewport.toWorldY(screenY)
        layout.worldToPage(index, worldX, worldY, mapped)
        return mapped[axis]
    }

    private fun pageX(screenX: Float, screenY: Float, index: Int) = pageAt(screenX, screenY, index, 0)

    private fun pageY(screenX: Float, screenY: Float, index: Int) = pageAt(screenX, screenY, index, 1)

    /** Where a point of page [index] shows on the screen, x then y. */
    private fun screenOf(index: Int, pageX: Float, pageY: Float): FloatArray {
        val layout = layout ?: return floatArrayOf(pageX, pageY)
        layout.pageToWorld(index, pageX, pageY, mapped)
        return floatArrayOf(viewport.toScreenX(mapped[0]), viewport.toScreenY(mapped[1]))
    }

    /** Fills [out] with the matrix that takes a point of page [index] to the screen: the page's turn, then the zoom and pan. */
    private fun pageToScreenMatrix(index: Int, out: Matrix) {
        val layout = layout
        val scale = viewport.pxPerPt
        if (layout == null) {
            out.setScale(scale, scale)
            out.postTranslate(viewport.panX, viewport.panY)
            return
        }
        out.setTranslate(-layout.pageWidth(index) / 2f, -layout.pageHeight(index) / 2f)
        out.postRotate(layout.turn(index))
        out.postTranslate(layout.centerX(index), layout.centerY(index))
        out.postScale(scale, scale)
        out.postTranslate(viewport.panX, viewport.panY)
    }

    /** The pen on the loupe's box, away from anything else, carries the box along. */
    private fun startDraggingLoupeBox(pointerId: Int, x: Float, y: Float): Boolean {
        val worldX = viewport.toWorldX(x)
        val worldY = viewport.toWorldY(y)
        // A little past the box's edge counts, so a finger need not land on the thin outline.
        val slop = LOUPE_GRAB_SLOP_DP * resources.displayMetrics.density / viewport.pxPerPt
        if (!RectF(loupe.box).apply { inset(-slop, -slop) }.contains(worldX, worldY)) return false
        loupeBoxPointer = pointerId
        loupeBoxGrabX = worldX - loupe.box.left
        loupeBoxGrabY = worldY - loupe.box.top
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
            // The window shows the document, and the page it lies on may be turned.
            loupe.screenToWorld(screenToPage)
            val worldToPage = Matrix()
            pageToScreenMatrix(drawPageIndex, worldToPage)
            val pageToWorld = Matrix().apply {
                setTranslate(-layout.pageWidth(drawPageIndex) / 2f, -layout.pageHeight(drawPageIndex) / 2f)
                postRotate(layout.turn(drawPageIndex))
                postTranslate(layout.centerX(drawPageIndex), layout.centerY(drawPageIndex))
            }
            pageToWorld.invert(worldToPage)
            screenToPage.postConcat(worldToPage)
        } else {
            pageToScreenMatrix(drawPageIndex, pageToScreen)
            pageToScreen.invert(screenToPage)
        }
        updateWetMask(layout, drawPageIndex)
        if (strokeEdge != Ruler.NO_EDGE) {
            startStrokeAlongRuler(event, editor, page.id)
            return
        }
        val steady = stabilizer
        val id = if (steady != null) {
            strokeStartMs = event.eventTime
            updatePageToScreen()
            val index = event.findPointerIndex(drawPointerId).coerceAtLeast(0)
            wet.startStroke(
                stabilizedInput(event.getX(index), event.getY(index), event.eventTime, event.getPressure(index)),
                InkBrushes.brush(drawStyle),
                pageToScreen,
            )
        } else {
            wet.startStroke(event, drawPointerId, InkBrushes.brush(drawStyle), screenToPage)
        }
        pendingStrokes[id] = PendingStroke(
            editor, page.id, drawStyle, strokeStickyId, drawScribble, drawCircle,
            writingStroke, if (writingStroke) Matrix(pageToScreen) else null,
        )
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
            if (index >= 0) {
                hold.move(event.getX(index), event.getY(index), event.eventTime)
                if (!straightening) {
                    for (h in 0 until event.historySize) {
                        traceAdd(event.getHistoricalX(index, h), event.getHistoricalY(index, h))
                        tracePressure(event.getHistoricalPressure(index, h))
                    }
                    traceAdd(event.getX(index), event.getY(index))
                    tracePressure(event.getPressure(index))
                }
            }
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
        updatePageToScreen()
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
        traceSize = 0
        tracePressureSum = 0f
        tracePressureCount = 0
        traceAdd(x, y)
        postDelayed(holdCheck, holdDelayMs)
    }

    /** Runs only once per delay, rather than rescheduling on each of the pen's 240 samples a second. */
    private fun checkHold() {
        if (strokeId == null || straightening) return
        val now = SystemClock.uptimeMillis()
        if (hold.isHeld(now, holdDelayMs)) {
            if (!snapToShape()) {
                val fit = holdFit
                // A line that is already straight is made so. Anything else, a curve or a shape that is not one of the usual ones, is smoothed.
                if (strokeEdge != Ruler.NO_EDGE || traceSize < 8 || (fit is FittedShape.Line && fit.headLength == 0f)) {
                    straighten()
                } else {
                    smoothHeldStroke()
                }
            }
        } else {
            postDelayed(holdCheck, (hold.heldAtMs(holdDelayMs) - now).coerceIn(MIN_HOLD_CHECK_MS, holdDelayMs))
        }
    }

    // The pen's path so far on the page, as x,y pairs, kept while a hold can still turn the stroke into a shape.
    private var trace = FloatArray(TRACE_START_FLOATS)
    private var traceSize = 0
    private val traceAt = FloatArray(2)
    private var tracePressureSum = 0f
    private var tracePressureCount = 0

    private fun tracePressure(pressure: Float) {
        tracePressureSum += pressure
        tracePressureCount++
    }

    private fun traceAdd(screenX: Float, screenY: Float) {
        traceAt[0] = screenX
        traceAt[1] = screenY
        screenToPage.mapPoints(traceAt)
        if (traceSize + 2 > trace.size) trace = trace.copyOf(trace.size * 2)
        trace[traceSize++] = traceAt[0]
        trace[traceSize++] = traceAt[1]
    }

    /**
     * The pen has been held still. If the stroke so far is closest to an ellipse, rectangle, triangle, other polygon,
     * arrow or smooth curve, it is taken off the wet layer and that shape is shown instead, to be put on the page when the
     * pen lifts, as one edit like any shape. A stroke that is only a straight line is left to [straighten].
     */
    private fun snapToShape(): Boolean {
        holdFit = null
        val old = strokeId ?: return false
        if (strokeEdge != Ruler.NO_EDGE || traceSize < 8) return false
        val pending = pendingStrokes[old] ?: return false
        val fit = ShapeFit.fit(trace.copyOf(traceSize), pending.style.tool == InkTool.HIGHLIGHTER) ?: return false
        holdFit = fit
        if (fit is FittedShape.Line && fit.headLength == 0f) return false
        // A simple curve, with one handle between its ends, gets that handle. One that would need more is too complex for handles: its own
        // line is smoothed instead.
        if (fit is FittedShape.Path && fit.smooth && !fit.closed && fit.points.size / 2 - 2 > 1) return false
        pendingStrokes.remove(old)
        wet.cancelStroke(old)
        stabilizer = null
        strokeId = null
        snapEditor = pending.editor
        snapPageId = pending.pageId
        snapStickyId = pending.stickyId
        // The shape's line is as wide as the pen's line was on average, which presses wider or thinner than the set width.
        val meanPressure = if (tracePressureCount > 0) tracePressureSum / tracePressureCount else 0.5f
        val style = pending.style.copy(width = pending.style.width * InkBrushes.meanWidthFactor(pending.style, meanPressure))
        shapes.showSnap(drawPageIndex, ShapeFit.toItem(fit, style))
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return true
    }

    // Where a shape being held will go when the pen lifts: the page, or the open note the stroke was drawn in.
    private var snapEditor: NoteEditor? = null
    private var snapPageId = 0L
    private var snapStickyId: Long? = null

    /** The pen lifted with a shape held: it goes onto the page, or into the open note, as one edit. */
    private fun commitSnap() {
        val sticky = snapStickyId
        snapStickyId = null
        val editor = snapEditor
        if (sticky == null || editor == null) {
            shapes.commitSnap()
            return
        }
        val item = shapes.takeSnap() ?: return
        addToSticky(editor, snapPageId, sticky, item.withId(IdSource.Random.next()))
        syncChanges()
    }

    /** Replaces the wet stroke with a line from where it began to where the pen is. */
    /** The pen is held at the end of a line that is not a plain straight one or a usual shape: when it lifts, the line is smoothed. */
    private fun smoothHeldStroke() {
        val id = strokeId ?: return
        pendingStrokes[id]?.smoothOnFinish = true
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun straighten() {
        val old = strokeId ?: return
        val pending = pendingStrokes.remove(old) ?: return
        wet.cancelStroke(old)
        stabilizer = null
        updatePageToScreen()
        val first = strokeInput(strokeStartX, strokeStartY, 0L)
        val id = wet.startStroke(first, InkBrushes.brush(drawStyle), pageToScreen)
        pendingStrokes[id] = pending
        strokeId = id
        straightening = true
        moveStraightEnd(id, SystemClock.uptimeMillis())
    }

    private fun moveStraightEnd(id: InProgressStrokeId, timeMs: Long) {
        val end = currentLineEnd(timeMs)
        overlay.invalidate()
        val prediction = MutableStrokeInputBatch().add(end)
        wet.addToStroke(ImmutableStrokeInputBatch.EMPTY, id, prediction)
    }

    private fun finishStraightStroke(event: MotionEvent, id: InProgressStrokeId) {
        val index = event.findPointerIndex(drawPointerId)
        if (index >= 0) hold.move(event.getX(index), event.getY(index), event.eventTime)
        val end = currentLineEnd(event.eventTime)
        straightening = false
        lineGuideOn = false
        overlay.invalidate()
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
        if (strokeEdge == Ruler.NO_EDGE || ruler == null) {
            // Within a few degrees of a reference angle the line turns onto it, and the angle is shown until the pen lifts.
            val snapped = AngleGuide.snap(strokeStartX, strokeStartY, end[0], end[1])
            end[0] = snapped[0]
            end[1] = snapped[1]
            lineGuideEndX = end[0]
            lineGuideEndY = end[1]
            lineGuideOn = hypot(end[0] - strokeStartX, end[1] - strokeStartY) >= AngleGuide.MIN_LENGTH_PT
        } else {
            lineGuideOn = false
        }
        return strokeInput(end[0], end[1], (timeMs - strokeStartMs).coerceAtLeast(1L))
    }

    /** A straight line has no pressure to speak of, so it comes out at an even width. */
    private fun strokeInput(x: Float, y: Float, elapsedMs: Long) = StrokeInput().apply {
        update(x, y, elapsedMs, InputToolType.STYLUS, STROKE_UNIT_CM, STRAIGHT_LINE_PRESSURE)
    }

    /** Strokes of a planner's date blanks the eraser has passed over in this drag; they go, on every page that shows them, when it lifts. */
    private val dateEraseHits = LinkedHashSet<DateStroke>()

    private fun eraseAt(x: Float, y: Float) {
        erase?.eraseTo(pageX(x, y, drawPageIndex), pageY(x, y, drawPageIndex))
        val page = editor?.document?.pages?.getOrNull(drawPageIndex)
        if (page != null && page.background.template is PageTemplate.Planner) {
            val radius = (tool as? CanvasTool.Eraser ?: DEFAULT_ERASER).radiusPt
            dateEraseHits += PlannerPages.dateStrokesNear(page, pageX(x, y, drawPageIndex), pageY(x, y, drawPageIndex), radius)
        }
        redrawChanges(drawPageIndex)
    }

    /**
     * Hides wet ink past the page's edge, where the finished stroke will be clipped anyway, and under
     * the views on top of this one.
     */
    private fun updateWetMask(layout: DocumentLayout, index: Int) {
        val size = editor?.document?.pages?.get(index)?.size
        val pageOutline = Path()
        val page = if (drawingInLoupe) {
            RectF(loupe.area)
        } else {
            size?.let {
                // The page as it lies on the screen, which is a turned rectangle when the page is turned.
                val onScreen = Matrix()
                pageToScreenMatrix(index, onScreen)
                pageOutline.addRect(0f, 0f, it.widthPt, it.heightPt, Path.Direction.CW)
                pageOutline.transform(onScreen)
                RectF().also { box -> pageOutline.computeBounds(box, true) }
            }
        }
        val turnedHere = !drawingInLoupe && layout.turn(index) != 0f
        if (maskIsCurrent && page == maskedPage && !turnedHere) return
        maskIsCurrent = !turnedHere
        maskedPage = page
        val mask = Path()
        if (page != null) {
            mask.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            val shown = if (drawingInLoupe || pageOutline.isEmpty) Path().apply { addRect(page, Path.Direction.CW) } else pageOutline
            mask.op(shown, Path.Op.DIFFERENCE)
        }
        for (overlay in overlayBounds) {
            mask.op(Path().apply { addRect(overlay, Path.Direction.CW) }, Path.Op.UNION)
        }
        wet.maskPath = if (mask.isEmpty) null else mask
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        var inked = false
        for ((id, stroke) in strokes) {
            val pending = pendingStrokes.remove(id) ?: continue
            if (pending.handwriting) {
                collectWritten(id, stroke, pending)
                continue
            }
            val editor = pending.editor
            val drawn = StrokeItem(IdSource.Random.next(), pending.style, stroke.inputs.toStrokePoints())
            // Held at its end, the line is what was drawn but with its wobble gone. The gestures below look at what was drawn.
            val item = if (pending.smoothOnFinish) drawn.copy(points = StrokeSmoothing.smoothBy(drawn.points, HOLD_SMOOTHING_SIGMA_PT)) else drawn
            val stickyId = pending.stickyId
            if (stickyId != null) {
                // A scribble on a post-it rubs out what the post-it holds under it, as one on the page does there.
                val note = noteScope(editor, pending.pageId, stickyId)
                if (pending.scribbleToErase && note != null && StrokeGestures.isScribble(drawn.points)) {
                    scribbleOut(editor, pending.pageId, drawn, note)
                    lastLoop = null
                    continue
                }
                if (addToSticky(editor, pending.pageId, stickyId, item)) {
                    lastLoop = if (pending.circleToLasso && StrokeGestures.isClosedLoop(drawn.points)) LoopMark(pending.pageId, item, stickyId) else null
                    continue
                }
            }
            lastLoop = null
            if (pending.scribbleToErase && StrokeGestures.isScribble(drawn.points)) {
                scribbleOut(editor, pending.pageId, drawn)
                continue
            }
            // A tap on a text box with the pen is a touch on the box, not a dot: it selects the box.
            if (item.bounds.width < TAP_DOT_PT && item.bounds.height < TAP_DOT_PT && selectTextUnderDot(editor, pending.pageId, item)) continue
            if (captureDateInk(editor, pending.pageId, item)) {
                holdWet += id
                continue
            }
            // The ink the wet layer made is of the line as drawn, so a smoothed line is built afresh.
            if (item === drawn) editor.strokes.put(item, stroke)
            editor.perform(ItemsEdit.addOnTop(pending.pageId, editor.content(pending.pageId), item))
            if (item.style.tool != InkTool.HIGHLIGHTER) holdWet += id
            if (pending.circleToLasso && StrokeGestures.isClosedLoop(drawn.points)) lastLoop = LoopMark(pending.pageId, item)
            inked = true
        }
        syncChanges()
        if (inked) onInkDrawn()
        // A finished stroke stays on the wet layer until the tiles have drawn it, or it would vanish for as long as a slow tile takes,
        // such as one with a large see-through picture. Marker strokes go at once: shown twice they would look darker for a moment.
        val now = strokes.keys.filter { it !in holdWet && it !in writingWet }.toSet()
        if (now.isNotEmpty()) wet.removeFinishedStrokes(now)
        val later = strokes.keys.filter { it in holdWet && it !in writingWet }.toSet()
        holdWet.removeAll(later)
        if (later.isNotEmpty()) tiles.afterPaints(WET_HOLD_PAINTS) { wet.removeFinishedStrokes(later) }
    }

    private val holdWet = HashSet<InProgressStrokeId>()

    /** Selects the text box under a tap that the pen made, and says whether there was one. */
    private fun selectTextUnderDot(editor: NoteEditor, pageId: Long, dot: StrokeItem): Boolean {
        val found = topItemOfType<TextItem>(pageId, dot.bounds.centerX, dot.bounds.centerY)?.second ?: return false
        // The second tap of a double tap opens the box for typing, and the pen's dot, which is read after it, must not select the box again.
        if (textEdit != null) return true
        val index = editor.document.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return false
        selectText(index, pageId, found)
        return true
    }

    /**
     * Erases whole strokes and shapes along a scribble, as one edit. The scribble itself is not kept. With [inside], the scribble was drawn
     * on that open post-it and erases what the post-it holds, by the same rules, as one change of the post-it.
     */
    private fun scribbleOut(editor: NoteEditor, pageId: Long, scribble: StrokeItem, inside: NoteScope? = null) {
        val content = inside?.content ?: editor.content(pageId)
        val points = scribble.points
        val session = EraseSession(
            pageId,
            content,
            EraseMode.WHOLE,
            scribble.style.width / 2f + SCRIBBLE_REACH_PT,
            kinds = DEFAULT_ERASABLE,
        )
        for (i in 0 until points.size) session.eraseTo(points.x(i), points.y(i))
        session.finish()?.let { recordOn(editor, pageId, inside, it) }
        redrawChanges(editor.document.pages.indexOfFirst { it.id == pageId })
    }

    /** The items of the open post-it [noteId] on page [pageId], for a gesture made on it, or null when it is gone or closed. */
    private fun noteScope(editor: NoteEditor, pageId: Long, noteId: Long): NoteScope? {
        val at = indexOfItem(pageId, noteId)
        return if (at < 0) null else NoteScope.open(pageId, editor.content(pageId), at)
    }

    /** Records [edit], already made on [inside]'s items or, without it, on the page, as one step of the history. */
    private fun recordOn(editor: NoteEditor, pageId: Long, inside: NoteScope?, edit: Edit) {
        if (inside == null) return editor.record(edit)
        inside.commit(editor.content(pageId))?.let { editor.perform(it) }
    }

    /** Performs [edit] on [inside]'s items or, without it, on the page, as one step of the history. */
    private fun performOn(editor: NoteEditor, pageId: Long, inside: NoteScope?, edit: Edit) {
        if (inside == null) return editor.perform(edit)
        edit.apply(object : EditTarget {
            override val document get() = editor.document
            override fun content(pageId: Long) = inside.content
        })
        recordOn(editor, pageId, inside, edit)
    }

    /** Touching the last circle's line selects what it surrounds, and hands the canvas over to the lasso. */
    private fun selectLoopAt(pageIndex: Int, pageId: Long, x: Float, y: Float, byFinger: Boolean = false): Boolean {
        val loop = lastLoop ?: return false
        val editor = editor ?: return false
        if (loop.pageId != pageId) return false
        // A finger is blunter than the pen, so it may also touch inside the circle.
        val reach = if (byFinger) {
            // Measured from the edge of the line, so half its width comes on top of the millimetres.
            FINGER_LOOP_REACH_MM * resources.displayMetrics.xdpi / MM_PER_INCH / viewport.pxPerPt + loop.item.style.width / 2f
        } else {
            LOOP_TAP_REACH_DP * resources.displayMetrics.density / viewport.pxPerPt
        }
        val touched = StrokeGestures.isNearLine(loop.item.points, x, y, reach) ||
            byFinger && StrokeGestures.isInside(loop.item.points, x, y)
        if (!touched) return false
        lastLoop = null
        return selectInsideLoop(pageIndex, pageId, loop.item.points, loop.item.id, loop.noteId)
    }

    /**
     * The circle itself is the lasso: its line, [ownId] when it was drawn as ink, is taken away, and what it surrounds is selected. A
     * circle written in a text box was never put on the page, so it has no id. A circle drawn on an open post-it ([noteId]) is in the
     * post-it: wholly inside its edges, it works on what the post-it holds, in place, by the same steps as on the page; leaving them,
     * it is taken out of the post-it and works on the page.
     */
    private fun selectInsideLoop(pageIndex: Int, pageId: Long, points: StrokePoints, ownId: Long?, noteId: Long? = null): Boolean {
        val editor = editor ?: return false
        val polygon = StrokeGestures.polygon(points)
        var inside: NoteScope? = null
        if (noteId != null) {
            val note = noteScope(editor, pageId, noteId) ?: return false
            // The loop keeps its id in the post-it, so it is found there as on the page.
            if (ownId != null && note.content.toList().none { it.id == ownId }) return false
            inside = NoteScope.forLoop(pageId, editor.content(pageId), polygon)?.takeIf { it.noteId == noteId }
            if (inside == null && ownId != null) {
                // It leaves the post-it: what it catches is on the page, and its line comes out of the post-it.
                if (!loopCatches(pageIndex, pageId, editor.content(pageId), polygon)) return false
                val own = note.content.toList().indexOfFirst { it.id == ownId }
                performOn(editor, pageId, note, ItemsEdit.remove(pageId, note.content, intArrayOf(own)))
                redrawChanges(pageIndex)
                return selectInsideLoop(pageIndex, pageId, points, null)
            }
        }
        val content = inside?.content ?: editor.content(pageId)
        val own = if (ownId == null) -1 else (0 until content.size).firstOrNull { content[it].id == ownId } ?: return false
        val found = loopSelection(pageIndex, pageId, content, polygon, inside)
        val picked = found.indices.indices.filter { found.indices[it] != own }
        if (picked.isEmpty()) {
            // Nothing of the owner's is inside: on a planner page the circle picks the page's generated elements, as the lasso does.
            if (inside != null || plannerElementsIn(pageIndex, polygon).isEmpty()) return false
            if (own >= 0) {
                editor.perform(ItemsEdit.remove(pageId, content, intArrayOf(own)))
                redrawChanges(pageIndex)
            }
            // The lasso is lent first: taking up a tool drops a pick, so the pick is made after.
            onLassoRequested()
            plannerPick.pick(pageIndex, editor.document.pages[pageIndex], polygon)
            syncChanges()
            return true
        }
        if (own >= 0) {
            performOn(editor, pageId, inside, ItemsEdit.remove(pageId, content, intArrayOf(own)))
            redrawChanges(pageIndex)
        }
        // Everything above the removed line moves down one place.
        val indices = picked.map { found.indices[it].let { index -> if (own in 0 until index) index - 1 else index } }
        val items = picked.map { found.items[it] }
        // The circle turns into marching ants before the selection takes over, on the page and in a post-it alike.
        val chosen = Selection(pageId, indices.toIntArray(), items)
        lasso.selectAfterAnts(pageIndex, polygon, chosen, inside) {
            onLassoRequested()
            syncChanges()
        }
        return true
    }

    /** What a circle's loop [polygon] selects in [content]: the page's, or [inside]'s items. On a cover page, never its background. */
    private fun loopSelection(pageIndex: Int, pageId: Long, content: PageContent, polygon: FloatArray, inside: NoteScope?): Selection {
        val cover = if (inside != null) null else editor?.document?.pages?.get(pageIndex)?.let { if (it.isCover) it.size else null }
        return Grouping.expand(
            selectWithLasso(pageId, content, polygon, circleLasso.kinds, circleLasso.fullOnly, circleLasso.anyPortion, circleLasso.lockedToo, cover),
            content,
            circleLasso.lockedToo,
            cover,
        )
    }

    /** Whether the loop catches anything on the page [content], or a planner page's elements. */
    private fun loopCatches(pageIndex: Int, pageId: Long, content: PageContent, polygon: FloatArray): Boolean =
        !loopSelection(pageIndex, pageId, content, polygon, null).isEmpty || plannerElementsIn(pageIndex, polygon).isNotEmpty()

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
        viewMoved()
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
        // Rounding the pan to whole pixels moves the page a little, and the selection with it.
        followTextEdit()
        overlay.invalidate()
    }

    private fun clampPan() {
        if (width > 0) layout?.clampPan(viewport, width, height, pageMarginPx)
    }

    /**
     * The middle of the empty room past the last page, on the screen, when there is room to render at least a share
     * of a page there, and null when there is not or no page can be added.
     */
    fun addPageSpot(): FloatArray? {
        val layout = layout ?: return null
        val pages = editor?.document?.pages ?: return null
        if (!canAddPages || layout.isInfinite || pages.isEmpty() || width == 0) return null
        val last = pages.lastIndex
        val gap = DocumentLayout.DEFAULT_GAP_PT
        val length = layout.lengthAlong(last)
        val end = layout.bounds
        val spot: FloatArray
        val free: Float
        when (scrollDirection) {
            ScrollDirection.VERTICAL -> {
                val start = end.bottom + gap
                free = viewport.toWorldY(height.toFloat()) - start
                // The middle of the empty space on the screen, and the middle of the screen across.
                val middle = start + free / 2f
                spot = floatArrayOf(width / 2f, viewport.toScreenY(middle))
            }
            ScrollDirection.LEFT_TO_RIGHT -> {
                val start = end.right + gap
                free = viewport.toWorldX(width.toFloat()) - start
                val middle = start + free / 2f
                spot = floatArrayOf(viewport.toScreenX(middle), height / 2f)
            }
            ScrollDirection.RIGHT_TO_LEFT -> {
                val start = end.left - gap
                free = start - viewport.toWorldX(0f)
                val middle = start - free / 2f
                spot = floatArrayOf(viewport.toScreenX(middle), height / 2f)
            }
        }
        if (free < length * ADD_PAGE_MIN_SHARE) return null
        val margin = pageMarginPx
        spot[0] = spot[0].coerceIn(margin, width - margin)
        spot[1] = spot[1].coerceIn(margin, height - margin)
        return spot
    }

    /**
     * The middle of the room before the first page, on the screen, once enough of it shows, and null when the notebook has a
     * cover or cannot take one.
     */
    fun addCoverSpot(): FloatArray? {
        val layout = layout ?: return null
        val document = editor?.document ?: return null
        if (layout.startRoomPt <= 0f || width == 0 || document.coverId != null || !document.canAddPages) return null
        val end = layout.bounds
        val room = layout.startRoomPt
        val spot: FloatArray
        val free: Float
        when (scrollDirection) {
            ScrollDirection.VERTICAL -> {
                free = end.top - viewport.toWorldY(0f)
                spot = floatArrayOf(viewport.toScreenX(end.centerX), viewport.toScreenY(end.top - minOf(free, room) / 2f))
            }
            ScrollDirection.LEFT_TO_RIGHT -> {
                free = end.left - viewport.toWorldX(0f)
                spot = floatArrayOf(viewport.toScreenX(end.left - minOf(free, room) / 2f), viewport.toScreenY(end.centerY))
            }
            ScrollDirection.RIGHT_TO_LEFT -> {
                free = viewport.toWorldX(width.toFloat()) - end.right
                spot = floatArrayOf(viewport.toScreenX(end.right + minOf(free, room) / 2f), viewport.toScreenY(end.centerY))
            }
        }
        if (free < room * ADD_COVER_MIN_SHARE) return null
        spot[0] = spot[0].coerceIn(pageMarginPx, width - pageMarginPx)
        spot[1] = spot[1].coerceIn(pageMarginPx, height - pageMarginPx)
        return spot
    }

    // ---- Document ----

    private fun rebuildLayout(keepView: Boolean = false) {
        val document = editor?.document ?: return
        layout = DocumentLayout(document.pages, scrollDirection).also { built ->
            // Where pages can be added, the view may go on past the last one, to the room for a new page.
            if (canAddPages && !built.isInfinite) built.endRoomPt = built.lengthAlong(document.pages.lastIndex) * ADD_PAGE_ROOM
            // The view may go up to half a page before the first, so every part of the page can be reached comfortably.
            // With no cover, "Add cover" sits in that room.
            if (!built.isInfinite) built.startRoomPt = built.lengthAlong(0) * ADD_COVER_ROOM
        }
        layoutVersion = document.version
        if (!keepView) placeFirstPage()
        clampPan()
        tiles.invalidateAll()
    }

    /** Set before a notebook opens to have it open on its cover. Cleared once the first page is placed. */
    var startOnCover = false

    private fun placeFirstPage() {
        if (hasPlacedPage || width == 0) return
        val layout = layout ?: return
        val saved = if (continueFromLastPage) editor?.lastPageIndex() ?: -1 else -1
        // A notebook with a cover opens on its first note page, page 1; the cover, page 0, is a scroll up away.
        val first = if (editor?.document?.coverId != null) 1 else 0
        // Asked for the cover, as after Edit in the New Notebook dialog: it opens on page 0.
        val index = if (startOnCover) 0 else saved.coerceAtLeast(first)
        startOnCover = false
        layout.moveToPage(viewport, index, width, height, pageMarginPx)
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
        tiles.invalidateRegion(layout.pageBoundsToWorld(pageIndex, changed))
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
                layout.roomBounds(index)
            },
        )
    }

    /** New PDF pixels for a point on a page have arrived, which its tile is still waiting for. */
    private fun redrawPageRegion(pageId: Long, region: Bounds) {
        val editor = editor ?: return
        val layout = layout ?: return
        val index = editor.document.indexOfPage(pageId)
        if (index < 0) return
        tiles.invalidateRegion(layout.pageBoundsToWorld(index, region))
    }

    private fun paintTile(canvas: Canvas, region: Bounds, leftPx: Double, topPx: Double, scale: Float) {
        val layout = layout ?: return
        editor?.pdf?.setFocus(viewport.toWorldX(width / 2f), viewport.toWorldY(height / 2f))
        painter?.paint(canvas, layout, region, leftPx, topPx, scale)
    }

    /**
     * For the benchmark: grabs everything on page 1 with the lasso and drags it about by script. [step]
     * counts from 0, and a negative step lets it go.
     */
    internal fun benchmarkDrag(step: Int) {
        if (step < 0) {
            lasso.cancel()
            lasso.clear()
            return
        }
        if (step == 0) {
            val editor = editor ?: return
            val pageId = editor.document.pages[0].id
            val content = editor.content(pageId)
            if (content.size == 0) return
            benchmarkBox = content.toList().map { it.bounds }.reduce(Bounds::union)
            layout?.moveToPage(viewport, 0, width, height, pageMarginPx)
            settlePan()
            lasso.select(0, Selection(pageId, IntArray(content.size) { it }, content.toList()))
            lasso.start(0, benchmarkBox.centerX, benchmarkBox.centerY, rectangle = false, kinds = ALL_ITEM_KINDS, fullOnly = false)
        }
        if (!lasso.hasSelection) return
        lasso.move(benchmarkBox.centerX + 60f * sin(step * 0.04f), benchmarkBox.centerY + 40f * sin(step * 0.03f))
    }

    private var benchmarkBox = Bounds(0f, 0f, 0f, 0f)

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
        const val HOLD_SELECT_MS = 500L
        const val HOLD_STROKE_REACH_DP = 10f
        const val AUDIO_HOLD_MS = 400L
        const val FINGER_TAP_MS = 300L
        const val FILL_GAP_OF_WIDTH = 0.01f
        const val SAME_FILL_AREA = 0.15f
        const val WET_HOLD_PAINTS = 3
        const val GRID_COLOR = CanvasColors.PAGE_GRID
        const val GRID_WIDTH_DP = 1.5f
        const val GRID_DASH_DP = 3f
        const val GRID_GAP_DP = 6f
        const val GRID_PAGES_AROUND = 2
        const val FILL_REVEAL_MS = 50L
        const val TABLE_TAP_MS = 300L
        const val TABLE_DOUBLE_TAP_MS = 450L
        const val TABLE_DOUBLE_TAP_REACH_DP = 40f
        const val TAP_DOT_PT = 6f
        const val TAP_DOT_WAIT_MS = 200L
        const val RESET_TURN_MS = 250L

        /** How far past the last page the view may go, as a share of that page, and how much of a page must show to offer a new one. */
        const val ADD_PAGE_ROOM = 0.6f

        /** How far before the first page the view may go, as a share of the page's length, and how much of it shows the button. */
        const val ADD_COVER_ROOM = 0.5f
        const val ADD_COVER_MIN_SHARE = 0.3f
        const val ADD_PAGE_MIN_SHARE = 0.3f
        const val LOUPE_GRAB_SLOP_DP = 24f
        const val BADGE_HALF_WIDTH_DP = 44f
        const val BADGE_ZONE_HEIGHT_DP = 96f
        const val FULL_TURN_DEGREES = 360f
        const val HALF_TURN_DEGREES = 180f
        const val MIN_HOLD_CHECK_MS = 8L
        const val TRACE_START_FLOATS = 1024
        const val PLACED_SHARE_OF_VIEW = 0.6f
        const val EMOJI_BOX_OF_SIZE = 1.5f
        const val VIDEO_BUTTON_REACH_PT = 24f
        const val LOUPE_BOX_SHARE = 0.45f
        const val LOUPE_STEP_SHARE = 0.8f
        const val LOUPE_BOX_COLOR = CanvasColors.SELECTION
        const val LOUPE_BOX_WIDTH_DP = 1.2f
        /** The pen used for handwriting that becomes text. It is only ever shown, never saved. */
        val WRITING_STYLE = InkStyle(width = 1.5f)

        /** A pause this long after a stroke ends a written word. */
        const val WORD_PAUSE_MS = 700L

        /** How much typed text before a word helps to read it. */
        const val PRE_CONTEXT_CHARS = 20

        /** A touch that stays within this and ends within [TAP_MAX_MS] is a tap, not writing. */
        const val TAP_REACH_DP = 6f
        const val TAP_MAX_MS = 300L

        const val NEW_TEXT_WIDTH_PT = 200f
        const val NEW_TEXT_HEIGHT_PT = 60f
        const val CELL_INSET_PT = 3f
        const val STICKY_CORNER_DP = 40f

        /** How much larger than drawn a post-it's open and close tap targets are, each way. */
        const val STICKY_TAP_GROWTH = 1.5f

        /** The fold corner that closes a post-it reaches 60% further than it did at [STICKY_TAP_GROWTH]. */
        const val STICKY_CLOSE_GROWTH = STICKY_TAP_GROWTH * 1.6f
        const val MIN_AREA_PT = 30f

        /** How strongly a line held at its end is smoothed: very (see [StrokeSmoothing]), so that only the shape of the line is left. */
        val HOLD_SMOOTHING_SIGMA_PT = 7 * StrokeSmoothing.SIGMA_PT_PER_LEVEL * 1.2f

        /** How long a touch must be held before it moves for it to size a note or sticker and then give back the tool. */
        const val HOLD_TO_SIZE_MS = 350L
        const val AREA_EDGE_COLOR = CanvasColors.SELECTION
        const val AREA_FILL_COLOR = CanvasColors.SELECTION_FILL

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

        /** How far from a written stroke a lone touch still counts as a dot of that word, in dp. */
        const val WRITTEN_INK_REACH_DP = 60f

        /** How often words still landing are looked at, once a tap away is waiting for them, in milliseconds. */
        const val SETTLE_POLL_MS = 50L

        /** How far apart the points are that a scribble is checked at along its way over typed letters. */
        const val SCRIBBLE_SAMPLE_PX = 4f
        const val LOOP_TAP_REACH_DP = 14f
        /** How far from the edge of a circle's line a finger may land and still touch it, in millimetres on the glass. */
        const val FINGER_LOOP_REACH_MM = 5f
        private const val MM_PER_INCH = 25.4f
        const val FINGER_BUTTON_REACH_DP = 30f
        const val BUTTON_REACH_OFF_PAGE_DP = 18f
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
