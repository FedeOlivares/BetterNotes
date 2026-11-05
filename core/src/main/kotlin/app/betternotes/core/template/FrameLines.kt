package app.betternotes.core.template

import app.betternotes.core.geometry.PageSize

/**
 * One line of a frame that can be moved. [fraction] is how far across the page it sits: of the width for a
 * [vertical] line, of the height for a horizontal one. A fraction, not points, so it follows a page resize.
 */
data class FrameLine(val vertical: Boolean, val fraction: Float) {
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

/**
 * The lines of a Cornell or legal frame that the owner can move, in a fixed order: Cornell has the title band's
 * bottom, the summary band's top and the cue column's right edge, legal has the head's bottom and the margin, and a
 * ruled page has its first two rules, which set where the ruling starts and how far apart it is.
 * [moved] is the layout's own positions, used when it has one for each line; any other template has no such lines.
 */
fun PageTemplate.frameLines(page: PageSize, moved: List<Float> = emptyList()): List<FrameLine> {
    val defaults = when (this) {
        is PageTemplate.Cornell, is PageTemplate.CornellRuled ->
            listOf(FrameLine(false, CORNELL_TITLE), FrameLine(false, CORNELL_SUMMARY), FrameLine(true, CORNELL_CUE))
        is PageTemplate.Legal ->
            listOf(FrameLine(false, LEGAL_HEAD_PT / page.heightPt), FrameLine(true, LEGAL_MARGIN_PT / page.widthPt))
        // The first rule and the second: the gap between them is the spacing of all the rest.
        is PageTemplate.Lined -> listOf(FrameLine(false, spacing / page.heightPt), FrameLine(false, 2f * spacing / page.heightPt))
        else -> return emptyList()
    }
    return if (moved.size == defaults.size) defaults.mapIndexed { i, line -> line.copy(fraction = moved[i]) } else defaults
}

/** Where line [index] may go, given the others: bands and rules never cross, and nothing leaves the page. */
fun PageTemplate.clampFrameLine(lines: List<FrameLine>, index: Int, fraction: Float, page: PageSize): Float {
    if (this is PageTemplate.Lined) {
        // Rules never closer than the densest spacing the pickers allow.
        val gap = DENSITY_RANGE_PT.start / page.heightPt
        return if (index == 0) fraction.coerceIn(EDGE, lines[1].fraction - gap) else fraction.coerceIn(lines[0].fraction + gap, 1f - EDGE)
    }
    val isCornell = this is PageTemplate.Cornell || this is PageTemplate.CornellRuled
    val low = if (isCornell && index == 1) lines[0].fraction + MIN_GAP else EDGE
    val high = if (isCornell && index == 0) lines[1].fraction - MIN_GAP else 1f - EDGE
    return fraction.coerceIn(low, high)
}
