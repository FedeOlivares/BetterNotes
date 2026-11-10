package app.betternotes.core.geometry

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The math of a grid of square tiles anchored at the document's origin. Tiles live in a fixed square
 * of slots, each in the slot given by its column and row modulo [dimension]. So the tiles around the
 * view never collide, a frame needs no hash lookups, and a tile far away is simply overwritten.
 */
class TileGrid(val tilePx: Int, maxScreenPx: Int) {
    /** Enough slots along each side for the most tiles the view can span, plus the [RING] around it. */
    val dimension = ceil(maxScreenPx / (tilePx * MIN_RATIO)).toInt() + 1 + 2 * RING

    val slotCount get() = dimension * dimension

    fun slot(column: Int, row: Int) = column.mod(dimension) + row.mod(dimension) * dimension

    companion object {
        /** Tiles kept drawn past each edge of the view, so a pan finds them ready. */
        const val RING = 1

        /** The most a tile is shrunk on screen before it is redrawn at the current zoom. */
        const val MIN_RATIO = 0.6f

        /** The most a tile is enlarged on screen before it is redrawn at the current zoom. */
        const val MAX_RATIO = 1.6f

        fun needsRescale(ratio: Float) = ratio < MIN_RATIO || ratio > MAX_RATIO

        /**
         * How far past a tile's own pixels, in screen pixels, a change still redraws it, and an item still counts as painting in it. Antialiasing
         * and filtering put a pixel or two of a line past its geometry; this is the margin for that, and it applies to both sides of the rule:
         * what a tile paints and what makes a tile redraw.
         */
        const val MARGIN_PX = 4f

        /**
         * The part of the document that the tile in [column] and [row] draws, in points, at [scale] pixels per point. A tile draws
         * [tilePx] pixels and [overlapPx] more on each side, which is why neighbours share their edges.
         */
        fun tileBounds(column: Int, row: Int, tilePx: Int, overlapPx: Int, scale: Float): Bounds {
            val leftPx = column.toDouble() * tilePx - overlapPx
            val topPx = row.toDouble() * tilePx - overlapPx
            val nodePx = tilePx + 2 * overlapPx
            return Bounds(
                (leftPx / scale).toFloat(),
                (topPx / scale).toFloat(),
                ((leftPx + nodePx) / scale).toFloat(),
                ((topPx + nodePx) / scale).toFloat(),
            )
        }

        /**
         * The part of the document that an item must reach to be drawn into [tile]: the tile, widened by [MARGIN_PX]. The painter keeps
         * an item whose paint box touches this, so an item that colours even one pixel of the tile is always drawn there.
         */
        fun cullRegion(tile: Bounds, scale: Float) = tile.outset(MARGIN_PX / scale)

        /**
         * Whether a change inside [changed] can alter a pixel of [tile], so that the tile must be redrawn. [changed] must hold everything
         * the old and the new item paint. Edges that touch count.
         */
        fun isAffectedBy(tile: Bounds, changed: Bounds, scale: Float) = cullRegion(tile, scale).intersects(changed)

        /** The tile holding the screen's first pixel, given the pan and a tile's size on screen. */
        fun firstVisible(pan: Float, tileOnScreen: Float) = floor(-pan / tileOnScreen).toInt()

        /** The tile holding the screen's last pixel. */
        fun lastVisible(pan: Float, tileOnScreen: Float, screenPx: Int) =
            ceil((screenPx - pan) / tileOnScreen).toInt() - 1
    }
}
