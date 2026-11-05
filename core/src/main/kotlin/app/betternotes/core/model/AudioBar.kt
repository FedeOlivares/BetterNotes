package app.betternotes.core.model

import app.betternotes.core.geometry.Frame

/**
 * How a recording looks and is touched when it sits on a page. A zero-size frame means it has not
 * been placed. Open, it is a play button and a waveform, wider than twice its height; collapsed, it is
 * the play button and a small arrow that opens it. All sizes are in points on the page.
 */
object AudioBar {
    const val HEIGHT = 32f
    const val OPEN_WIDTH = 200f
    const val ARROW_WIDTH = 14f
    const val COLLAPSED_WIDTH = HEIGHT + ARROW_WIDTH

    /** The corner of an open bar that collapses it. */
    const val CORNER = 16f

    enum class Zone { PLAY, COLLAPSE, EXPAND, WAVE }

    fun isPlaced(frame: Frame) = frame.width > 0f && frame.height > 0f

    fun isOpen(frame: Frame) = frame.width > 2f * frame.height

    /** A bar of the given state centred on ([x], [y]). */
    fun centeredAt(x: Float, y: Float, open: Boolean): Frame {
        val width = if (open) OPEN_WIDTH else COLLAPSED_WIDTH
        return Frame(x - width / 2f, y - HEIGHT / 2f, width, HEIGHT)
    }

    /** The same bar, opened or collapsed, keeping its top left corner. */
    fun withOpen(frame: Frame, open: Boolean): Frame =
        frame.copy(width = if (open) OPEN_WIDTH else COLLAPSED_WIDTH, height = HEIGHT)

    /** What a touch at ([x], [y]) on the page presses, or null outside the bar. */
    fun zoneAt(frame: Frame, x: Float, y: Float): Zone? {
        if (!frame.bounds.contains(x, y)) return null
        val dx = x - frame.left
        val dy = y - frame.top
        return when {
            dx < frame.height -> Zone.PLAY
            !isOpen(frame) -> Zone.EXPAND
            dx > frame.width - CORNER && dy < CORNER -> Zone.COLLAPSE
            else -> Zone.WAVE
        }
    }

    /** Where along the waveform of an open bar [x] falls, from 0 to 1. */
    fun fractionAt(frame: Frame, x: Float): Float {
        val start = frame.left + frame.height
        val end = frame.left + frame.width - WAVE_PADDING
        return ((x - start) / (end - start)).coerceIn(0f, 1f)
    }

    /** The space between the end of the waveform and the bar's edge. */
    const val WAVE_PADDING = 8f
}
