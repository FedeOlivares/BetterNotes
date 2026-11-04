package app.betternotes.canvas

import android.graphics.Canvas
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ShapeItem
import kotlin.math.abs

/** Dragging out a new shape. Until the pen lifts it floats over the page, drawn by the same painter. */
internal class ShapeController(
    private val viewport: Viewport,
    private val pageOriginX: (pageIndex: Int) -> Float,
    private val pageOriginY: (pageIndex: Int) -> Float,
    private val redraw: () -> Unit,
    private val add: (pageIndex: Int, item: ShapeItem) -> Unit,
) {
    private val painter = ItemPainter()
    private var make: ((x0: Float, y0: Float, x1: Float, y1: Float) -> ShapeItem)? = null
    private var pageIndex = -1
    private var startX = 0f
    private var startY = 0f
    private var preview: ShapeItem? = null

    /** Starts a drag, which [make] turns into an item at each move. */
    fun start(pageIndex: Int, x: Float, y: Float, make: (x0: Float, y0: Float, x1: Float, y1: Float) -> ShapeItem) {
        this.make = make
        this.pageIndex = pageIndex
        startX = x
        startY = y
        preview = null
    }

    fun move(x: Float, y: Float) {
        val current = make ?: return
        preview = current(startX, startY, x, y)
        redraw()
    }

    fun end() {
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
        make = null
        preview = null
        redraw()
    }

    fun draw(canvas: Canvas) {
        val item = preview ?: return
        val scale = viewport.pxPerPt
        canvas.withTranslation(viewport.toScreenX(pageOriginX(pageIndex)), viewport.toScreenY(pageOriginY(pageIndex))) {
            withScale(scale, scale, 0f, 0f) { painter.paint(this, item) }
        }
    }

    private companion object {
        /** Smaller than this is a slip of the pen. */
        const val MIN_SIZE_PT = 2f
    }
}
