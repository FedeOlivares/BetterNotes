package app.betternotes.core.template

import app.betternotes.core.geometry.PageSize
import kotlin.math.max
import kotlin.math.min

/**
 * One line of a frame or of its ruling that can be moved. [fraction] is how far across the page it sits: of the width
 * for a [vertical] line, of the height for a horizontal one. A fraction, not points, so it follows a page resize.
 * A [ruling] line is one of the first two lines of the ruling, which set where it starts and how far apart it is.
 */
data class FrameLine(val vertical: Boolean, val fraction: Float, val ruling: Boolean = false) {
    /** Where the line is on a page of [page], in points. */
    fun position(page: PageSize) = fraction * if (vertical) page.widthPt else page.heightPt
}

private const val CORNELL_TITLE = 0.08f
private const val CORNELL_SUMMARY = 0.8f
private const val CORNELL_CUE = 0.3f
private const val LEGAL_HEAD_PT = 72f
private const val LEGAL_MARGIN_PT = 90f
private const val EDGE = 0.02f
private const val MIN_GAP = 0.04f

/** The spacing of a template's ruling in points, or null when it has none the owner can move. */
private val PageTemplate.rulingSpacing: Float?
    get() = when (this) {
        is PageTemplate.Lined -> spacing
        is PageTemplate.Cornell -> spacing
        is PageTemplate.CornellRuled -> spacing
        is PageTemplate.Legal -> spacing
        else -> null
    }

/**
 * The lines of a frame, and of its ruling, that the owner can move, in a fixed order.
 * - Cornell: the title band's bottom, the summary band's top, the cue column's right edge, then the first two rules.
 * - Legal: the head's bottom (which is also the first rule), the margin, then the second rule.
 * - Ruled: the first two rules.
 *
 * [moved] is the layout's own positions. Any the layout does not have yet (a file saved before the ruling could be
 * moved) are filled in from the defaults, and a template with no such lines gives none.
 */
fun PageTemplate.frameLines(page: PageSize, moved: List<Float> = emptyList()): List<FrameLine> {
    val spacing = (rulingSpacing ?: return emptyList()) / page.heightPt
    fun at(i: Int, default: Float) = moved.getOrNull(i) ?: default
    return when (this) {
        is PageTemplate.Cornell, is PageTemplate.CornellRuled -> {
            val title = at(0, CORNELL_TITLE)
            val first = at(3, title + spacing)
            listOf(
                FrameLine(false, title),
                FrameLine(false, at(1, CORNELL_SUMMARY)),
                FrameLine(true, at(2, CORNELL_CUE)),
                FrameLine(false, first, ruling = true),
                FrameLine(false, at(4, first + spacing), ruling = true),
            )
        }
        is PageTemplate.Legal -> {
            val head = at(0, LEGAL_HEAD_PT / page.heightPt)
            listOf(
                FrameLine(false, head, ruling = true),
                FrameLine(true, at(1, LEGAL_MARGIN_PT / page.widthPt)),
                FrameLine(false, at(2, head + spacing), ruling = true),
            )
        }
        is PageTemplate.Lined -> {
            val first = at(0, spacing)
            listOf(FrameLine(false, first, ruling = true), FrameLine(false, at(1, first + spacing), ruling = true))
        }
        else -> emptyList()
    }
}

/** How many of [frameLines] stay put when the spacing changes: everything before the ruling that follows the spacing. */
val PageTemplate.fixedFrameLines: Int
    get() = when (this) {
        is PageTemplate.Cornell, is PageTemplate.CornellRuled -> 3
        is PageTemplate.Legal -> 2
        else -> 0
    }

private fun Float.within(low: Float, high: Float) = coerceIn(low, max(low, high))

/**
 * All positions after dragging line [index] to [fraction]. The bands and rules never cross, nothing leaves the page,
 * rules stay at least the densest spacing apart, and the Cornell title band takes its ruling with it.
 */
fun PageTemplate.movedFrameLines(page: PageSize, current: List<Float>, index: Int, fraction: Float): List<Float> {
    val lines = frameLines(page, current).map { it.fraction }.toMutableList()
    if (index !in lines.indices) return lines
    val gap = DENSITY_RANGE_PT.start / page.heightPt
    when (this) {
        is PageTemplate.Lined ->
            lines[index] = if (index == 0) fraction.within(EDGE, lines[1] - gap) else fraction.within(lines[0] + gap, 1f - EDGE)
        is PageTemplate.Legal -> lines[index] = when (index) {
            0 -> fraction.within(EDGE, lines[2] - gap)
            1 -> fraction.within(EDGE, 1f - EDGE)
            else -> fraction.within(lines[0] + gap, 1f - EDGE)
        }
        is PageTemplate.Cornell, is PageTemplate.CornellRuled -> when (index) {
            0 -> {
                val moved = fraction.within(EDGE, lines[1] - MIN_GAP - (lines[4] - lines[0]))
                val shift = moved - lines[0]
                lines[0] = moved
                lines[3] += shift
                lines[4] += shift
            }
            1 -> lines[1] = fraction.within(max(lines[0] + MIN_GAP, lines[4] + gap), 1f - EDGE)
            2 -> lines[2] = fraction.within(EDGE, 1f - EDGE)
            3 -> lines[3] = fraction.within(lines[0] + gap, lines[4] - gap)
            else -> lines[4] = fraction.within(lines[3] + gap, min(lines[1] - gap, 1f - EDGE))
        }
        else -> Unit
    }
    return lines
}
