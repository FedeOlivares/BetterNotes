package app.betternotes.core.template

import app.betternotes.core.edit.BatchEdit
import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.UpdatePage
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageSource

/** The templates to pick from. A custom layout is picked by its own name instead. */
enum class TemplateKind {
    BLANK,
    LINED,
    GRID,
    DOTTED,
    HEXAGONAL,
    CORNELL_RULED,
    CORNELL,
    LEGAL,
    ENGINEERING,
    MUSIC_SCORE,
    GUITAR_TAB,
    GUITAR_SCORE,
}

fun TemplateKind.newTemplate(): PageTemplate = when (this) {
    TemplateKind.BLANK -> PageTemplate.Blank
    TemplateKind.LINED -> PageTemplate.Lined()
    TemplateKind.DOTTED -> PageTemplate.Dotted()
    TemplateKind.GRID -> PageTemplate.Grid()
    TemplateKind.HEXAGONAL -> PageTemplate.Hexagonal()
    TemplateKind.CORNELL_RULED -> PageTemplate.CornellRuled()
    TemplateKind.CORNELL -> PageTemplate.Cornell()
    TemplateKind.ENGINEERING -> PageTemplate.Engineering()
    TemplateKind.GUITAR_SCORE -> PageTemplate.GuitarScore()
    TemplateKind.LEGAL -> PageTemplate.Legal()
    TemplateKind.MUSIC_SCORE -> PageTemplate.MusicScore()
    TemplateKind.GUITAR_TAB -> PageTemplate.GuitarTab()
}

/** Null for a custom layout. */
val PageTemplate.kind: TemplateKind?
    get() = when (this) {
        PageTemplate.Blank -> TemplateKind.BLANK
        is PageTemplate.Lined -> TemplateKind.LINED
        is PageTemplate.Dotted -> TemplateKind.DOTTED
        is PageTemplate.Grid -> TemplateKind.GRID
        is PageTemplate.Hexagonal -> TemplateKind.HEXAGONAL
        is PageTemplate.CornellRuled -> TemplateKind.CORNELL_RULED
        is PageTemplate.Cornell -> TemplateKind.CORNELL
        is PageTemplate.Engineering -> TemplateKind.ENGINEERING
        is PageTemplate.GuitarScore -> TemplateKind.GUITAR_SCORE
        is PageTemplate.Legal -> TemplateKind.LEGAL
        is PageTemplate.MusicScore -> TemplateKind.MUSIC_SCORE
        is PageTemplate.GuitarTab -> TemplateKind.GUITAR_TAB
        is PageTemplate.Custom -> null
    }

val DENSITY_RANGE_PT = 4f..96f
val LINE_WEIGHT_RANGE_PT = 0.1f..4f

/** The spacing a density control moves, in points, or null if the template has none. */
val PageTemplate.density: Float?
    get() = when (this) {
        is PageTemplate.Lined -> spacing
        is PageTemplate.Dotted -> spacing
        is PageTemplate.Grid -> spacing
        is PageTemplate.Hexagonal -> side
        is PageTemplate.Cornell -> spacing
        is PageTemplate.CornellRuled -> spacing
        is PageTemplate.Engineering -> spacing
        is PageTemplate.GuitarScore -> lineGap
        is PageTemplate.Legal -> spacing
        is PageTemplate.MusicScore -> lineGap
        is PageTemplate.GuitarTab -> lineGap
        PageTemplate.Blank, is PageTemplate.Custom -> null
    }

/** Sets [density], kept inside [DENSITY_RANGE_PT]. Templates with none come back unchanged. */
fun PageTemplate.withDensity(value: Float): PageTemplate {
    val v = value.coerceIn(DENSITY_RANGE_PT)
    return when (this) {
        is PageTemplate.Lined -> copy(spacing = v)
        is PageTemplate.Dotted -> copy(spacing = v)
        is PageTemplate.Grid -> copy(spacing = v)
        is PageTemplate.Hexagonal -> copy(side = v)
        is PageTemplate.Cornell -> copy(spacing = v)
        is PageTemplate.CornellRuled -> copy(spacing = v)
        is PageTemplate.Engineering -> copy(spacing = v)
        is PageTemplate.GuitarScore -> copy(lineGap = v)
        is PageTemplate.Legal -> copy(spacing = v)
        is PageTemplate.MusicScore -> copy(lineGap = v)
        is PageTemplate.GuitarTab -> copy(lineGap = v)
        PageTemplate.Blank, is PageTemplate.Custom -> this
    }
}

/** The line width, or the dot diameter on a dotted page, in points, or null if the template has none. */
val PageTemplate.lineWeight: Float?
    get() = when (this) {
        is PageTemplate.Lined -> lineWidth
        is PageTemplate.Dotted -> dotDiameter
        is PageTemplate.Grid -> lineWidth
        is PageTemplate.Hexagonal -> lineWidth
        is PageTemplate.Cornell -> lineWidth
        is PageTemplate.CornellRuled -> lineWidth
        is PageTemplate.Engineering -> lineWidth
        is PageTemplate.GuitarScore -> lineWidth
        is PageTemplate.Legal -> lineWidth
        is PageTemplate.MusicScore -> lineWidth
        is PageTemplate.GuitarTab -> lineWidth
        PageTemplate.Blank, is PageTemplate.Custom -> null
    }

/** Sets [lineWeight], kept inside [LINE_WEIGHT_RANGE_PT]. Templates with none come back unchanged. */
fun PageTemplate.withLineWeight(value: Float): PageTemplate {
    val v = value.coerceIn(LINE_WEIGHT_RANGE_PT)
    return when (this) {
        is PageTemplate.Lined -> copy(lineWidth = v)
        is PageTemplate.Dotted -> copy(dotDiameter = v)
        is PageTemplate.Grid -> copy(lineWidth = v)
        is PageTemplate.Hexagonal -> copy(lineWidth = v)
        is PageTemplate.Cornell -> copy(lineWidth = v)
        is PageTemplate.CornellRuled -> copy(lineWidth = v)
        is PageTemplate.Engineering -> copy(lineWidth = v)
        is PageTemplate.GuitarScore -> copy(lineWidth = v)
        is PageTemplate.Legal -> copy(lineWidth = v)
        is PageTemplate.MusicScore -> copy(lineWidth = v)
        is PageTemplate.GuitarTab -> copy(lineWidth = v)
        PageTemplate.Blank, is PageTemplate.Custom -> this
    }
}

/** The steps of the picker's density and line width sliders. */
val TEMPLATE_LEVELS = 1..10

/** The line width step a new template starts on, as in the reference. */
private const val DEFAULT_WEIGHT_LEVEL = 3

/** What each density step multiplies a template's default spacing by. Higher is denser, and step 7 is the default, as in the reference. */
private val DENSITY_FACTORS = floatArrayOf(3.4f, 2.9f, 2.3f, 1.9f, 1.4f, 1.2f, 1f, 0.86f, 0.71f, 0.57f)

private fun PageTemplate.defaultOfKind(): PageTemplate? = kind?.newTemplate()

/** The density step nearest this template's spacing, or null if it has no density. */
val PageTemplate.densityLevel: Int?
    get() {
        val value = density ?: return null
        val base = defaultOfKind()?.density ?: return null
        return TEMPLATE_LEVELS.minBy { kotlin.math.abs(base * DENSITY_FACTORS[it - 1] - value) }
    }

fun PageTemplate.withDensityLevel(level: Int): PageTemplate {
    val base = defaultOfKind()?.density ?: return this
    return withDensity(base * DENSITY_FACTORS[level.coerceIn(TEMPLATE_LEVELS) - 1])
}

/** The line width step nearest this template's weight, or null if it has none. */
val PageTemplate.lineWeightLevel: Int?
    get() {
        val value = lineWeight ?: return null
        val base = defaultOfKind()?.lineWeight ?: return null
        return TEMPLATE_LEVELS.minBy { kotlin.math.abs(base * it / DEFAULT_WEIGHT_LEVEL - value) }
    }

fun PageTemplate.withLineWeightLevel(level: Int): PageTemplate {
    val base = defaultOfKind()?.lineWeight ?: return this
    return withLineWeight(base * level.coerceIn(TEMPLATE_LEVELS) / DEFAULT_WEIGHT_LEVEL)
}

/** Paper colors offered by the picker, measured from the reference. */
val PAPER_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    PageBackground.DEFAULT_PAPER_COLOR,
    0xFFD5C6A7.toInt(),
    0xFFDCE3ED.toInt(),
    0xFF2E2E2E.toInt(),
    0xFF000000.toInt(),
)

/** An undoable change of size on the pages that can be resized. A canvas or PDF page keeps its size. */
fun Document.resizePages(pageIds: Collection<Long>, size: PageSize): Edit? = pageEdit(pageIds) { page ->
    if (page.isResizable) page.copy(size = size) else null
}

/**
 * An undoable change of background on the given pages. A page from a PDF keeps its look, because the
 * PDF paints over it. A template that only exists on a page with edges does not go on a canvas,
 * where it would draw nothing. Returns null if no page would change.
 */
fun Document.changeBackground(pageIds: Collection<Long>, background: PageBackground): Edit? =
    pageEdit(pageIds) { page ->
        val accepts = page.source == PageSource.Blank && (page.size != null || background.template.tiles)
        if (accepts) page.copy(background = background) else null
    }

/** An undoable change of the color alone, keeping each page's template. */
fun Document.changePaperColor(pageIds: Collection<Long>, color: Int): Edit? = pageEdit(pageIds) { page ->
    if (page.source == PageSource.Blank) page.copy(background = page.background.copy(color = color)) else null
}

/**
 * Puts a custom layout on the given pages. It brings its own size, since that is what its lines were
 * placed for, so it goes only on pages that can be resized. A page keeps its own copy, so changing or
 * deleting the layout later leaves the page as it is.
 */
fun Document.applyCustomLayout(pageIds: Collection<Long>, layout: CustomLayout): Edit? = pageEdit(pageIds) { page ->
    if (page.isResizable) {
        page.copy(size = layout.size, background = page.background.copy(template = PageTemplate.Custom(layout)))
    } else {
        null
    }
}

private inline fun Document.pageEdit(pageIds: Collection<Long>, change: (Page) -> Page?): Edit? {
    val edits = pageIds.mapNotNull { id ->
        val before = page(id)
        change(before)?.takeIf { it != before }?.let { UpdatePage(before, it) }
    }
    return when (edits.size) {
        0 -> null
        1 -> edits[0]
        else -> BatchEdit(edits)
    }
}
