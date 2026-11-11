package app.betternotes.core.planner

/** The colors a planner can change: its cover (three) and the accent of its pages. */
object PlannerColors {
    const val COUNT = 4
    const val COVER = 0
    const val SPINE = 1
    const val STRIPE = 2
    const val ACCENT = 3

    /** The cover's colors as `CoverDesign.PLANNER` draws them, and the pages' blue. */
    val DEFAULT: List<Int> = listOf(0xFF27324F.toInt(), 0xFF1A2240.toInt(), 0xFFE2C84A.toInt(), 0xFF5B6EC7.toInt())

    /** [colors] as the template keeps them (none when they are the defaults, so such a planner's file is unchanged). */
    fun normalized(colors: List<Int>): List<Int> = if (colors.size != COUNT || colors == DEFAULT) emptyList() else colors

    /** Color [index] of a template's [colors], falling back to the default. */
    fun of(colors: List<Int>, index: Int): Int = colors.getOrNull(index) ?: DEFAULT[index]

    /** The cover design's own colors mapped to [colors]'s, for `CoverDesign.draw`'s recolor. Empty when nothing changed. */
    fun coverRecolor(colors: List<Int>): Map<Int, Int> =
        if (colors.isEmpty()) emptyMap() else (COVER..STRIPE).filter { colors[it] != DEFAULT[it] }.associate { DEFAULT[it] to colors[it] }
}

/**
 * One handwritten stroke in a semester date blank. [slot] is which date (semester * 2, plus 1 for an end date). [points] are x, y pairs in the
 * planner's reference units, measured from the top-left of the blank, so the stroke lands the same way on every page that shows the date.
 */
data class DateStroke(val slot: Int, val color: Int, val width: Float, val points: List<Float>) {
    init {
        require(slot in 0 until PlannerPages.SEMESTER_DATES) { "Date slot $slot" }
        require(points.size >= 2 && points.size % 2 == 0) { "A stroke has x, y pairs" }
    }

    /** Whether a disc of [radius] at ([x], [y]), in the same units as the points, reaches the line. */
    fun touches(x: Float, y: Float, radius: Float): Boolean {
        val reach = radius + width / 2f
        var i = 0
        while (i + 3 < points.size) {
            if (distanceToSegment(x, y, points[i], points[i + 1], points[i + 2], points[i + 3]) <= reach) return true
            i += 2
        }
        return points.size == 2 && Math.hypot((x - points[0]).toDouble(), (y - points[1]).toDouble()) <= reach
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0f, 1f)
        return Math.hypot((px - (ax + t * dx)).toDouble(), (py - (ay + t * dy)).toDouble()).toFloat()
    }

    companion object {
        /** The most strokes one blank keeps, so a corrupt file or a scribble cannot grow a template without end. */
        const val MAX_PER_SLOT = 40
        const val MAX_POINTS = 400
        /** How far above its rule a blank reaches: the blank is this tall, and the rule is at its foot. */
        const val SLOT_HEIGHT = 40f
    }
}
