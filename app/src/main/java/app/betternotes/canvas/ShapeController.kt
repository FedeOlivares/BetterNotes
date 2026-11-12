package app.betternotes.canvas

import android.graphics.Canvas
import androidx.core.graphics.withMatrix
import android.graphics.Matrix
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.withId
import app.betternotes.core.tools.TapeOptions
import app.betternotes.core.tools.smoothTapePath
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.tools.AngleGuide
import kotlin.math.abs
import kotlin.math.hypot

/** Dragging out a new shape. Until the pen lifts it floats over the page, drawn by the same painter. */
internal class ShapeController(
    /** Fills the matrix that takes a point of a page to the screen, which turns and scales it as the page is. */
    private val pageToScreenOf: (pageIndex: Int, into: Matrix) -> Unit,
    private val redraw: () -> Unit,
    private val add: (pageIndex: Int, item: PageItem) -> Unit,
    density: Float,
) {
    private val guidePainter = AngleGuidePainter(density)
    private val onScreen = Matrix()
    private var guideEndX = 0f
    private var guideEndY = 0f
    private var guideOn = false

    private val painter = ItemPainter()
    private var make: ((x0: Float, y0: Float, x1: Float, y1: Float) -> ShapeItem)? = null
    private var pageIndex = -1
    private var startX = 0f
    private var startY = 0f
    private var preview: ShapeItem? = null

    // Tape that follows the pen, which may straighten if the pen is held still.
    private var tape: TapeOptions? = null
    private val tapePoints = ArrayList<Float>()
    private var pathPreview: TapePathItem? = null
    private var straight = false
    private var anchorX = 0f
    private var anchorY = 0f

    /** Starts a drag, which [make] turns into an item at each move. */
    fun start(pageIndex: Int, x: Float, y: Float, make: (x0: Float, y0: Float, x1: Float, y1: Float) -> ShapeItem) {
        this.make = make
        this.pageIndex = pageIndex
        startX = x
        startY = y
        preview = null
    }

    /** Starts tape that follows the pen from (x, y). */
    fun startTape(pageIndex: Int, x: Float, y: Float, options: TapeOptions) {
        make = null
        this.pageIndex = pageIndex
        startX = x
        startY = y
        tape = options
        tapePoints.clear()
        tapePoints += x
        tapePoints += y
        straight = false
        anchorX = x
        anchorY = y
        preview = null
        pathPreview = null
    }

    /** Whether tape that follows the pen is being laid. */
    val isLayingTape get() = tape != null

    /**
     * The pen moved to (x, y). Returns true if it has left the spot where it was last held, by more than [slop],
     * so a hold timer should start over.
     */
    fun moveTape(x: Float, y: Float, slop: Float): Boolean {
        val options = tape ?: return false
        if (straight) {
            val end = AngleGuide.snap(startX, startY, x, y)
            preview = options.item(0L, startX, startY, end[0], end[1])
            guideEndX = end[0]
            guideEndY = end[1]
            guideOn = hypot(end[0] - startX, end[1] - startY) >= AngleGuide.MIN_LENGTH_PT
        } else {
            val last = tapePoints.size
            if (last < 2 || abs(x - tapePoints[last - 2]) + abs(y - tapePoints[last - 1]) >= TAPE_SAMPLE_PT) {
                tapePoints += x
                tapePoints += y
                pathPreview = options.pathItem(0L, smoothTapePath(tapePoints.toFloatArray()))
            }
        }
        redraw()
        val moved = abs(x - anchorX) + abs(y - anchorY) > slop
        if (moved) {
            anchorX = x
            anchorY = y
        }
        return moved
    }

    /** The pen was held still: the strip becomes straight, from where it began to where the pen is. */
    fun straightenTape() {
        val options = tape ?: return
        if (straight || tapePoints.size < 2) return
        straight = true
        val end = AngleGuide.snap(startX, startY, tapePoints[tapePoints.size - 2], tapePoints[tapePoints.size - 1])
        preview = options.item(0L, startX, startY, end[0], end[1])
        guideEndX = end[0]
        guideEndY = end[1]
        guideOn = hypot(end[0] - startX, end[1] - startY) >= AngleGuide.MIN_LENGTH_PT
        pathPreview = null
        redraw()
    }

    fun move(x: Float, y: Float) {
        val current = make ?: return
        var item = current(startX, startY, x, y)
        guideOn = false
        if (isStraight(item)) {
            // A straight line turns onto a reference angle that it is close to, and shows the angle while it is drawn.
            val end = AngleGuide.snap(startX, startY, x, y)
            item = current(startX, startY, end[0], end[1])
            guideEndX = end[0]
            guideEndY = end[1]
            guideOn = hypot(end[0] - startX, end[1] - startY) >= AngleGuide.MIN_LENGTH_PT
        }
        preview = item
        redraw()
    }

    private fun isStraight(item: ShapeItem) = item.kind == ShapeKind.LINE || item.kind == ShapeKind.ARROW || item.kind == ShapeKind.TAPE

    fun end() {
        val path = pathPreview
        if (tape != null && path != null && path.pointCount >= 2) {
            add(pageIndex, path.copy(id = IdSource.Random.next()))
            cancel()
            return
        }
        val item = preview
        if (item != null && (abs(item.frame.width) >= MIN_SIZE_PT || abs(item.frame.height) >= MIN_SIZE_PT)) {
            add(pageIndex, item.copy(id = IdSource.Random.next()))
        }
        cancel()
    }

    /** Ends the drag without adding anything, and returns what was dragged out, if the pen moved. */
    fun takePreview(): ShapeItem? {
        val item = preview
        cancel()
        return item
    }

    fun cancel() {
        guideOn = false
        make = null
        preview = null
        tape = null
        pathPreview = null
        straight = false
        redraw()
    }

    // A stroke the pen has drawn and held, shown as the shape it was closest to until the pen lifts.
    private var snap: PageItem? = null
    private var snapPage = -1

    val hasSnap get() = snap != null

    fun showSnap(pageIndex: Int, item: PageItem) {
        snap = item
        snapPage = pageIndex
        redraw()
    }

    /** The pen lifted: the shape goes onto the page. */
    fun commitSnap() {
        val item = snap ?: return
        val page = snapPage
        cancelSnap()
        add(page, item.withId(IdSource.Random.next()))
    }

    /** The pen lifted over something that takes the shape itself, such as an open note: hands it over without adding it to the page. */
    fun takeSnap(): PageItem? {
        val item = snap
        cancelSnap()
        return item
    }

    fun cancelSnap() {
        snap = null
        snapPage = -1
        redraw()
    }

    fun draw(canvas: Canvas) {
        val held = snap
        if (held != null) {
            pageToScreenOf(snapPage, onScreen)
            canvas.withMatrix(onScreen) { painter.paint(this, held) }
        }
        val item = preview
        val path = pathPreview
        if (item == null && path == null) return
        pageToScreenOf(pageIndex, onScreen)
        canvas.withMatrix(onScreen) {
            if (item != null) painter.paint(this, item)
            if (path != null) painter.paint(this, path)
        }
        if (guideOn) {
            val ends = floatArrayOf(startX, startY, guideEndX, guideEndY)
            onScreen.mapPoints(ends)
            guidePainter.draw(canvas, ends[0], ends[1], ends[2], ends[3], AngleGuide.angle(startX, startY, guideEndX, guideEndY))
        }
    }

    private companion object {
        /** Smaller than this is a slip of the pen. */
        const val MIN_SIZE_PT = 2f

        /** The pen must have moved this far, in points, for tape that follows it to take another sample. */
        const val TAPE_SAMPLE_PT = 1.2f
    }
}
