package app.betternotes.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderNode
import android.view.View
import androidx.core.graphics.withTranslation
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.TileGrid
import app.betternotes.core.geometry.Viewport
import kotlin.math.max
import kotlin.math.roundToInt

/** Paints a square of the document. See [PagePainter.paint] for the arguments. */
internal fun interface TilePainter {
    fun paint(canvas: Canvas, region: Bounds, leftPx: Double, topPx: Double, scale: Float)
}

/**
 * Shows finished content as a grid of GPU layers, so moving the page costs a handful of textured
 * quads per frame however much ink it holds. A pan only rasterizes the tiles it uncovers, and those
 * in the ring past the screen's edge are drawn ahead of time.
 *
 * During a pinch the tiles are stretched, which blurs them for a moment. They are redrawn when the
 * stretch goes too far and when the fingers lift.
 */
@SuppressLint("ViewConstructor") // Only ever built in code, by CanvasView.
internal class TileLayerView(
    context: Context,
    private val viewport: Viewport,
    private val painter: TilePainter,
    var backdropColor: Int,
) : View(context) {
    private var grid: TileGrid? = null
    private var slots = emptyArray<Tile?>()

    /** Document pixels per point that the tiles are drawn at, or 0 if they are out of date. */
    private var tileScale = 0f
    private var generation = 0

    /** Called with the time each tile took to record, for the performance probe. */
    var onTileRecorded: ((nanos: Long) -> Unit)? = null

    /** Marks the tiles over a document region as out of date. */
    fun invalidateRegion(region: Bounds) {
        val scale = tileScale
        if (scale == 0f) return
        for (tile in slots) {
            if (tile == null) continue
            val left = tile.column * TILE_PX / scale - OVERLAP_PX / scale
            val top = tile.row * TILE_PX / scale - OVERLAP_PX / scale
            val extent = (TILE_PX + 2 * OVERLAP_PX) / scale
            if (left <= region.right && region.left <= left + extent && top <= region.bottom && region.top <= top + extent) {
                tile.dirty = true
            }
        }
        invalidate()
    }

    /** Marks every tile as out of date. */
    fun invalidateAll() {
        generation++
        invalidate()
    }

    /** The zoom has stopped changing, so the tiles should be redrawn sharply at it. */
    fun settle() {
        if (tileScale != viewport.pxPerPt) tileScale = 0f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val needed = TileGrid(TILE_PX, max(w, h))
        if (needed.dimension > (grid?.dimension ?: 0)) {
            discardTiles()
            grid = needed
            slots = arrayOfNulls(needed.slotCount)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        discardTiles()
    }

    override fun onDraw(canvas: Canvas) {
        val grid = grid ?: return
        // A software canvas, as a screenshot of the view hierarchy uses, cannot draw a RenderNode.
        if (!canvas.isHardwareAccelerated) return
        canvas.drawColor(backdropColor)

        val scale = viewport.pxPerPt
        if (tileScale == 0f || TileGrid.needsRescale(scale / tileScale)) {
            tileScale = scale
            generation++
        }
        val ratio = scale / tileScale
        val onScreen = TILE_PX * ratio
        // A whole-pixel position leaves the texture unfiltered, so ink stays exactly as sharp as drawn.
        val panX = if (ratio == 1f) viewport.panX.roundToInt().toFloat() else viewport.panX
        val panY = if (ratio == 1f) viewport.panY.roundToInt().toFloat() else viewport.panY

        val firstColumn = TileGrid.firstVisible(panX, onScreen)
        val lastColumn = TileGrid.lastVisible(panX, onScreen, width)
        val firstRow = TileGrid.firstVisible(panY, onScreen)
        val lastRow = TileGrid.lastVisible(panY, onScreen, height)

        var prefetchBudget = PREFETCH_TILES_PER_FRAME
        var prefetchPending = false
        for (row in firstRow - TileGrid.RING..lastRow + TileGrid.RING) {
            for (column in firstColumn - TileGrid.RING..lastColumn + TileGrid.RING) {
                val tile = tileAt(grid, column, row)
                if (!tile.isCurrent(column, row, generation)) {
                    val visible = column in firstColumn..lastColumn && row in firstRow..lastRow
                    if (!visible && prefetchBudget == 0) {
                        prefetchPending = true
                        continue
                    }
                    if (!visible) prefetchBudget--
                    record(tile, column, row)
                }
                val x = panX + (column * TILE_PX - OVERLAP_PX) * ratio
                val y = panY + (row * TILE_PX - OVERLAP_PX) * ratio
                canvas.withTranslation(x, y) {
                    if (ratio != 1f) scale(ratio, ratio)
                    drawRenderNode(tile.node)
                }
            }
        }
        if (prefetchPending) postInvalidateOnAnimation()
    }

    private fun tileAt(grid: TileGrid, column: Int, row: Int): Tile {
        val slot = grid.slot(column, row)
        return slots[slot] ?: Tile().also { slots[slot] = it }
    }

    private fun record(tile: Tile, column: Int, row: Int) {
        val start = System.nanoTime()
        val scale = tileScale
        val leftPx = column.toDouble() * TILE_PX - OVERLAP_PX
        val topPx = row.toDouble() * TILE_PX - OVERLAP_PX
        val region = Bounds(
            (leftPx / scale).toFloat(),
            (topPx / scale).toFloat(),
            ((leftPx + NODE_PX) / scale).toFloat(),
            ((topPx + NODE_PX) / scale).toFloat(),
        )
        val canvas = tile.node.beginRecording(NODE_PX, NODE_PX)
        try {
            canvas.drawColor(backdropColor)
            painter.paint(canvas, region, leftPx, topPx, scale)
        } finally {
            tile.node.endRecording()
        }
        tile.column = column
        tile.row = row
        tile.generation = generation
        tile.dirty = false
        onTileRecorded?.invoke(System.nanoTime() - start)
    }

    private fun discardTiles() {
        for (tile in slots) tile?.node?.discardDisplayList()
        slots.fill(null)
        tileScale = 0f
    }

    private class Tile {
        val node = RenderNode("tile").apply {
            setPosition(0, 0, NODE_PX, NODE_PX)
            setUseCompositingLayer(true, null)
        }
        var column = Int.MIN_VALUE
        var row = Int.MIN_VALUE
        var generation = -1
        var dirty = false

        /** A node that left the frame loses its display list, so that counts as out of date too. */
        fun isCurrent(column: Int, row: Int, generation: Int) =
            this.column == column && this.row == row && this.generation == generation &&
                !dirty && node.hasDisplayList()
    }

    private companion object {
        const val TILE_PX = 512

        /**
         * Tiles overlap their neighbours by this much. They are opaque, so a stretched tile blends
         * with the same content at its edge instead of showing a seam.
         */
        const val OVERLAP_PX = 1
        const val NODE_PX = TILE_PX + 2 * OVERLAP_PX

        /** Limits the cost of drawing ahead, so a frame stays cheap while the ring fills up. */
        const val PREFETCH_TILES_PER_FRAME = 2
    }
}
