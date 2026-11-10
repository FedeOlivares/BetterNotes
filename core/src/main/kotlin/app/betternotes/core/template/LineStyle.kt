package app.betternotes.core.template

/** The colors offered for a layout's lines. Opaque; a translucent line such as a highlighter keeps its own alpha. */
val LINE_COLORS = listOf(
    0xFF55585F.toInt(),
    0xFF000000.toInt(),
    0xFFB9BDC4.toInt(),
    0xFFD9534F.toInt(),
    0xFFE8923B.toInt(),
    0xFF3E9B5B.toInt(),
    0xFF3E78EE.toInt(),
    0xFF8E5BD0.toInt(),
)

/** The color of the ruling or lines of a template, or null if it has none. A legal page's margin color is separate. */
val PageTemplate.lineColor: Int?
    get() = when (this) {
        is PageTemplate.Lined -> color
        is PageTemplate.Dotted -> color
        is PageTemplate.Grid -> color
        is PageTemplate.Hexagonal -> color
        is PageTemplate.Cornell -> color
        is PageTemplate.CornellRuled -> color
        is PageTemplate.Engineering -> color
        is PageTemplate.GuitarScore -> color
        is PageTemplate.Legal -> color
        is PageTemplate.MusicScore -> color
        is PageTemplate.GuitarTab -> color
        PageTemplate.Blank, PageTemplate.Semester, PageTemplate.MindMap, PageTemplate.WeekPlanner, is PageTemplate.Planner, is PageTemplate.Custom -> null
    }

/** Sets [lineColor]. Templates with none come back unchanged. */
fun PageTemplate.withLineColor(value: Int): PageTemplate = when (this) {
    is PageTemplate.Lined -> copy(color = value)
    is PageTemplate.Dotted -> copy(color = value)
    is PageTemplate.Grid -> copy(color = value)
    is PageTemplate.Hexagonal -> copy(color = value)
    is PageTemplate.Cornell -> copy(color = value)
    is PageTemplate.CornellRuled -> copy(color = value)
    is PageTemplate.Engineering -> copy(color = value)
    is PageTemplate.GuitarScore -> copy(color = value)
    is PageTemplate.Legal -> copy(color = value)
    is PageTemplate.MusicScore -> copy(color = value)
    is PageTemplate.GuitarTab -> copy(color = value)
    PageTemplate.Blank, PageTemplate.Semester, PageTemplate.MindMap, PageTemplate.WeekPlanner, is PageTemplate.Planner, is PageTemplate.Custom -> this
}

/** The stroke width of an element's lines, or null for a sticky note, which has none. */
val LayoutElement.strokeWidth: Float?
    get() = when (this) {
        is LayoutElement.Line -> lineWidth
        is LayoutElement.Rectangle -> lineWidth
        is LayoutElement.Table -> lineWidth
        is LayoutElement.Ruled -> lineWidth
        is LayoutElement.Sticky -> null
    }

/** The color of an element's lines, or of a sticky note's fill. */
val LayoutElement.strokeColor: Int
    get() = when (this) {
        is LayoutElement.Line -> color
        is LayoutElement.Rectangle -> color
        is LayoutElement.Table -> color
        is LayoutElement.Ruled -> color
        is LayoutElement.Sticky -> color
    }

/** Sets the stroke width, kept inside [LINE_WEIGHT_RANGE_PT]. A sticky note comes back unchanged. */
fun LayoutElement.withStrokeWidth(value: Float): LayoutElement {
    val v = value.coerceIn(LINE_WEIGHT_RANGE_PT)
    return when (this) {
        is LayoutElement.Line -> copy(lineWidth = v)
        is LayoutElement.Rectangle -> copy(lineWidth = v)
        is LayoutElement.Table -> copy(lineWidth = v)
        is LayoutElement.Ruled -> copy(lineWidth = v)
        is LayoutElement.Sticky -> this
    }
}

/** Sets the color of the lines (or the sticky note's fill) to [rgb], keeping the element's own transparency. */
fun LayoutElement.withStrokeColor(rgb: Int): LayoutElement {
    val value = (strokeColor.toLong() and 0xFF000000L).toInt() or (rgb and 0x00FFFFFF)
    return when (this) {
        is LayoutElement.Line -> copy(color = value)
        is LayoutElement.Rectangle -> copy(color = value)
        is LayoutElement.Table -> copy(color = value)
        is LayoutElement.Ruled -> copy(color = value)
        is LayoutElement.Sticky -> copy(color = value)
    }
}
