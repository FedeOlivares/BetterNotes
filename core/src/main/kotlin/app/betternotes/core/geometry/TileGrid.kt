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

        /** The tile holding the screen's first pixel, given the pan and a tile's size on screen. */
        fun firstVisible(pan: Float, tileOnScreen: Float) = floor(-pan / tileOnScreen).toInt()

        /** The tile holding the screen's last pixel. */
        fun lastVisible(pan: Float, tileOnScreen: Float, screenPx: Int) =
            ceil((screenPx - pan) / tileOnScreen).toInt() - 1
    }
}
