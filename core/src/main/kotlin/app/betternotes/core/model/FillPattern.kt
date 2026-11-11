package app.betternotes.core.model

/** The marks a paint bucket fill can carry on top of its color, in the style of a comic: dots, or lines in one or two directions. */
enum class FillPatternKind { SOLID, DOTS, LINES, DIAGONAL, CROSSHATCH }

/**
 * What is drawn over a [FillItem]'s color: [kind] marks in [color], [spacing] points apart, each [weight] of that spacing thick (a dot's
 * radius, a line's width). The marks are generated, not stored, on a grid that belongs to the page, so they are the same in every tile,
 * thumbnail and export.
 */
data class FillPattern(
    val kind: FillPatternKind = FillPatternKind.SOLID,
    val color: Int = 0xFF000000.toInt(),
    val spacing: Float = DEFAULT_SPACING,
    val weight: Float = DEFAULT_WEIGHT,
) {
    val isSolid get() = kind == FillPatternKind.SOLID

    companion object {
        val SOLID = FillPattern()
        const val MIN_SPACING = 3f
        const val MAX_SPACING = 24f
        const val DEFAULT_SPACING = 8f
        const val MIN_WEIGHT = 0.08f
        const val MAX_WEIGHT = 0.34f
        const val DEFAULT_WEIGHT = 0.2f
    }
}
