package app.betternotes.canvas

import android.graphics.Canvas
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import app.betternotes.core.geometry.Viewport
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.tools.ShapeOptions
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
    private var options: ShapeOptions? = null
    private var pageIndex = -1
    private var startX = 0f
    private var startY = 0f
    private var preview: ShapeItem? = null

    fun start(pageIndex: Int, x: Float, y: Float, options: ShapeOptions) {
        this.options = options
        this.pageIndex = pageIndex
        startX = x
        startY = y
        preview = null
    }

    fun move(x: Float, y: Float) {
        val current = options ?: return
        preview = current.item(PREVIEW_ID, startX, startY, x, y)
        redraw()
    }

    fun end() {
        val item = preview
        if (item != null && (abs(item.frame.width) >= MIN_SIZE_PT || abs(item.frame.height) >= MIN_SIZE_PT)) {
            add(pageIndex, item.copy(id = IdSource.Random.next()))
        }
        cancel()
    }

    fun cancel() {
        options = null
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
        const val PREVIEW_ID = 0L

        /** Smaller than this is a slip of the pen. */
        const val MIN_SIZE_PT = 2f
    }
}
