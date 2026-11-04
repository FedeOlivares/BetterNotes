package app.betternotes.core.template

import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize

private const val DEFAULT_LINE_COLOR = 0xFFB9BDC4.toInt()
private const val DEFAULT_MARGIN_COLOR = 0xFFD9534F.toInt()

/** Paper color plus the pattern printed on it. Colors are ARGB throughout the model. */
data class PageBackground(
    val color: Int = DEFAULT_PAPER_COLOR,
    val template: PageTemplate = PageTemplate.Blank,
) {
    companion object {
        /** A by-eye match of the cream page in the reference screenshots, not a measured value. */
        const val DEFAULT_PAPER_COLOR = 0xFFFAF6EA.toInt()
    }
}

/**
 * What is printed on a page. Lengths are in points. Density is the spacing between lines, dots
 * or staves, so a smaller value is denser.
 */
sealed interface PageTemplate {
    /** True when the pattern repeats without reference to a page edge, as an infinite canvas needs. */
    val tiles: Boolean get() = false

    data object Blank : PageTemplate {
        override val tiles get() = true
    }

    data class Lined(
        val spacing: Float = 24f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(spacing, lineWidth) }

        override val tiles get() = true
    }

    data class Dotted(
        val spacing: Float = 14f,
        val dotDiameter: Float = 1f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(spacing, dotDiameter) }

        override val tiles get() = true
    }

    data class Grid(
        val spacing: Float = 14f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(spacing, lineWidth) }

        override val tiles get() = true
    }

    /** Flat-topped hexagons; [side] is the length of one edge. */
    data class Hexagonal(
        val side: Float = 12f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(side, lineWidth) }

        override val tiles get() = true
    }

    /** Title band, cue column on the left, note area ruled at [spacing], summary band at the bottom. */
    data class Cornell(
        val spacing: Float = 24f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(spacing, lineWidth) }
    }

    /** A legal pad: a blank head, ruled lines and a double margin line down the left. */
    data class Legal(
        val spacing: Float = 24f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
        val marginColor: Int = DEFAULT_MARGIN_COLOR,
    ) : PageTemplate {
        init { requireLength(spacing, lineWidth) }
    }

    /** Five-line staves. [lineGap] separates lines in a staff, [staffGap] separates staves. */
    data class MusicScore(
        val lineGap: Float = 7f,
        val staffGap: Float = 42f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(lineGap, staffGap, lineWidth) }
    }

    /** Six-line guitar tablature staves. */
    data class GuitarTab(
        val lineGap: Float = 7f,
        val staffGap: Float = 42f,
        val lineWidth: Float = 0.5f,
        val color: Int = DEFAULT_LINE_COLOR,
    ) : PageTemplate {
        init { requireLength(lineGap, staffGap, lineWidth) }
    }

    data class Custom(val layout: CustomLayout) : PageTemplate

    companion object {
        /** The most density a pattern may have. It also stops a corrupt file from hanging the renderer. */
        const val MIN_SPACING = 1f
    }
}

private fun requireLength(spacing: Float, vararg others: Float) {
    require(spacing >= PageTemplate.MIN_SPACING) { "Spacing too small: $spacing" }
    others.forEach { require(it > 0f) { "Length must be positive: $it" } }
}

/**
 * A layout the owner builds. [displayUnit] only remembers which unit the builder was showing,
 * since all lengths are stored in points.
 */
data class CustomLayout(
    val name: String,
    val size: PageSize,
    val displayUnit: LengthUnit,
    val elements: List<LayoutElement>,
)

sealed interface LayoutElement {
    data class Line(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val lineWidth: Float,
        val color: Int,
    ) : LayoutElement

    data class Rectangle(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val lineWidth: Float,
        val color: Int,
    ) : LayoutElement

    data class Table(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rows: Int,
        val columns: Int,
        val lineWidth: Float,
        val color: Int,
    ) : LayoutElement {
        init { require(rows > 0 && columns > 0) { "Table needs cells: $rows x $columns" } }
    }

    /** A block of evenly spaced horizontal lines. */
    data class Ruled(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val spacing: Float,
        val lineWidth: Float,
        val color: Int,
    ) : LayoutElement {
        init { require(spacing >= PageTemplate.MIN_SPACING) { "Spacing too small: $spacing" } }
    }
}
