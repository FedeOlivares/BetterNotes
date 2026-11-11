package app.betternotes.core.format

import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.planner.DateStroke
import app.betternotes.core.planner.ElementKey
import app.betternotes.core.planner.PlannerColors
import app.betternotes.core.planner.PlannerRole
import app.betternotes.core.planner.PlannerRoleStyles
import app.betternotes.core.planner.RoleStyle
import app.betternotes.core.planner.PlannerKind
import app.betternotes.core.planner.PlannerPage
import app.betternotes.core.planner.PlannerPages
import app.betternotes.core.planner.PlannerSetup
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageSource
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.LayoutElement
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate

private const val DOCUMENT_MAGIC = "BNDC"

private const val MIN_PAGE_BYTES = Long.SIZE_BYTES
private const val MIN_ELEMENT_BYTES = 1

private const val TEMPLATE_BLANK = 0
private const val TEMPLATE_LINED = 1
private const val TEMPLATE_DOTTED = 2
private const val TEMPLATE_GRID = 3
private const val TEMPLATE_HEXAGONAL = 4
private const val TEMPLATE_CORNELL = 5
private const val TEMPLATE_LEGAL = 6
private const val TEMPLATE_MUSIC = 7
private const val TEMPLATE_GUITAR = 8
private const val TEMPLATE_CUSTOM = 9
private const val TEMPLATE_CORNELL_RULED = 10
private const val TEMPLATE_ENGINEERING = 11
private const val TEMPLATE_GUITAR_SCORE = 12
private const val TEMPLATE_SEMESTER = 13
private const val TEMPLATE_MIND_MAP = 14
private const val TEMPLATE_WEEK_PLANNER = 15
private const val TEMPLATE_PLANNER = 16

/** A planner page that also has a title (the cover's). Pages without one keep tag 16, so files made before titles still open. */
private const val TEMPLATE_PLANNER_TITLED = 17

/** A planner page with semester dates (and a title, which may be empty): the index and the semester planning page once a date is typed. */
private const val TEMPLATE_PLANNER_DATED = 18

/** A planner page with chosen colors or handwriting in the date blanks (format 7): the title, the semester dates (or none), the colors (or none) and the strokes. */
private const val TEMPLATE_PLANNER_STYLED = 19

/** A planner page whose elements were restyled (format 14): everything of tag 19, then the styles by role and by element. */
private const val TEMPLATE_PLANNER_ROLES = 20

private const val ELEMENT_LINE = 0
private const val ELEMENT_RECTANGLE = 1
private const val ELEMENT_TABLE = 2
private const val ELEMENT_RULED = 3
private const val ELEMENT_STICKY = 4

private const val SOURCE_BLANK = 0
private const val SOURCE_PDF = 1

internal val DOCUMENT_KINDS = WireEnum(DocumentKind.NOTE, DocumentKind.CANVAS, DocumentKind.PDF, DocumentKind.TEXT, DocumentKind.PLANNER)
internal val LENGTH_UNITS = WireEnum(LengthUnit.PT, LengthUnit.INCH, LengthUnit.CM, LengthUnit.MM, LengthUnit.PX)

/** The file describing a document and listing its pages in order. */
object DocumentCodec {
    /** A document is written at [LAYOUT_FORMAT_VERSION], or at [PLANNER_ROLES_FORMAT_VERSION] only when a planner page was restyled. */
    fun encode(document: Document, version: Int = versionOf(document)): ByteArray = encodeFile(DOCUMENT_MAGIC, version) {
        long(document.id)
        string(document.title)
        DOCUMENT_KINDS.write(this, document.kind)
        long(document.createdAtMs)
        long(document.modifiedAtMs)
        varint(document.pages.size)
        document.pages.forEach { page(it) }
    }

    private fun versionOf(document: Document): Int =
        if (document.pages.any { (it.background.template as? PageTemplate.Planner)?.roles?.isEmpty == false }) PLANNER_ROLES_FORMAT_VERSION else LAYOUT_FORMAT_VERSION

    fun decode(bytes: ByteArray): Document = decodeFile(bytes, DOCUMENT_MAGIC, PLANNER_ROLES_FORMAT_VERSION) {
        val id = long()
        val title = string()
        val kind = DOCUMENT_KINDS.read(this)
        val createdAtMs = long()
        val modifiedAtMs = long()
        val pageCount = count(MIN_PAGE_BYTES)
        Document(id, title, kind, createdAtMs, modifiedAtMs, List(pageCount) { page() })
    }
}

private fun ByteWriter.page(page: Page) {
    long(page.id)
    if (version >= 4) float(page.rotation)
    // Version 5 added the cover: the id of the design, on the first page only.
    if (version >= 5) {
        bool(page.cover != null)
        page.cover?.let { string(it) }
    }
    bool(page.size != null)
    page.size?.let { pageSize(it) }
    int(page.background.color)
    template(page.background.template)
    when (val source = page.source) {
        PageSource.Blank -> byte(SOURCE_BLANK)
        is PageSource.PdfPage -> {
            byte(SOURCE_PDF)
            string(source.assetId)
            varint(source.pageIndex)
        }
    }
}

private fun ByteReader.page(): Page {
    val id = long()
    val rotation = if (version >= 4) float() else 0f
    val cover = if (version >= 5 && bool()) string() else null
    val size = if (bool()) pageSize() else null
    val background = PageBackground(int(), template())
    val source = when (val tag = byte()) {
        SOURCE_BLANK -> PageSource.Blank
        SOURCE_PDF -> PageSource.PdfPage(string(), varint())
        else -> throw CorruptFileException("Unknown page source $tag")
    }
    return Page(id, size, background, source, rotation, cover)
}

internal fun ByteWriter.pageSize(size: PageSize) {
    float(size.widthPt)
    float(size.heightPt)
}

internal fun ByteReader.pageSize() = PageSize(float(), float())

internal fun ByteWriter.template(template: PageTemplate) {
    when (template) {
        PageTemplate.Blank -> byte(TEMPLATE_BLANK)
        is PageTemplate.Lined -> {
            byte(TEMPLATE_LINED)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Dotted -> {
            byte(TEMPLATE_DOTTED)
            float(template.spacing)
            float(template.dotDiameter)
            int(template.color)
        }
        is PageTemplate.Grid -> {
            byte(TEMPLATE_GRID)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Hexagonal -> {
            byte(TEMPLATE_HEXAGONAL)
            float(template.side)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Cornell -> {
            byte(TEMPLATE_CORNELL)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Legal -> {
            byte(TEMPLATE_LEGAL)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
            int(template.marginColor)
        }
        is PageTemplate.MusicScore -> {
            byte(TEMPLATE_MUSIC)
            float(template.lineGap)
            float(template.staffGap)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.GuitarTab -> {
            byte(TEMPLATE_GUITAR)
            float(template.lineGap)
            float(template.staffGap)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.CornellRuled -> {
            byte(TEMPLATE_CORNELL_RULED)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.Engineering -> {
            byte(TEMPLATE_ENGINEERING)
            float(template.spacing)
            float(template.lineWidth)
            int(template.color)
        }
        is PageTemplate.GuitarScore -> {
            byte(TEMPLATE_GUITAR_SCORE)
            float(template.lineGap)
            float(template.staffGap)
            float(template.lineWidth)
            int(template.color)
        }
        PageTemplate.Semester -> byte(TEMPLATE_SEMESTER)
        PageTemplate.MindMap -> byte(TEMPLATE_MIND_MAP)
        PageTemplate.WeekPlanner -> byte(TEMPLATE_WEEK_PLANNER)
        is PageTemplate.Planner -> {
            byte(
                when {
                    !template.roles.isEmpty -> TEMPLATE_PLANNER_ROLES
                    template.colors.isNotEmpty() || template.dateInk.isNotEmpty() -> TEMPLATE_PLANNER_STYLED
                    template.semesters.isNotEmpty() -> TEMPLATE_PLANNER_DATED
                    template.title.isEmpty() -> TEMPLATE_PLANNER
                    else -> TEMPLATE_PLANNER_TITLED
                },
            )
            byte(template.page.kind.ordinal)
            varint(template.page.arg)
            varint(template.setup.startYear)
            byte(template.setup.startMonth)
            byte(template.setup.weekdayShift)
            if (template.colors.isNotEmpty() || template.dateInk.isNotEmpty() || !template.roles.isEmpty) {
                string(template.title)
                bool(template.semesters.isNotEmpty())
                template.semesters.forEach { int(it) }
                byte(template.colors.size)
                template.colors.forEach { int(it) }
                varint(template.dateInk.size)
                for (stroke in template.dateInk) {
                    byte(stroke.slot)
                    int(stroke.color)
                    float(stroke.width)
                    varint(stroke.points.size / 2)
                    stroke.points.forEach { float(it) }
                }
                if (!template.roles.isEmpty) roleStyles(template.roles)
            } else if (template.semesters.isNotEmpty()) {
                string(template.title)
                template.semesters.forEach { int(it) }
            } else if (template.title.isNotEmpty()) {
                string(template.title)
            }
        }
        is PageTemplate.Custom -> {
            byte(TEMPLATE_CUSTOM)
            customLayout(template.layout)
        }
    }
}

internal fun ByteReader.template(): PageTemplate = when (val tag = byte()) {
    TEMPLATE_BLANK -> PageTemplate.Blank
    TEMPLATE_LINED -> PageTemplate.Lined(float(), float(), int())
    TEMPLATE_DOTTED -> PageTemplate.Dotted(float(), float(), int())
    TEMPLATE_GRID -> PageTemplate.Grid(float(), float(), int())
    TEMPLATE_HEXAGONAL -> PageTemplate.Hexagonal(float(), float(), int())
    TEMPLATE_CORNELL -> PageTemplate.Cornell(float(), float(), int())
    TEMPLATE_LEGAL -> PageTemplate.Legal(float(), float(), int(), int())
    TEMPLATE_MUSIC -> PageTemplate.MusicScore(float(), float(), float(), int())
    TEMPLATE_GUITAR -> PageTemplate.GuitarTab(float(), float(), float(), int())
    TEMPLATE_CUSTOM -> PageTemplate.Custom(customLayout())
    TEMPLATE_CORNELL_RULED -> PageTemplate.CornellRuled(float(), float(), int())
    TEMPLATE_ENGINEERING -> PageTemplate.Engineering(float(), float(), int())
    TEMPLATE_GUITAR_SCORE -> PageTemplate.GuitarScore(float(), float(), float(), int())
    TEMPLATE_SEMESTER -> PageTemplate.Semester
    TEMPLATE_MIND_MAP -> PageTemplate.MindMap
    TEMPLATE_WEEK_PLANNER -> PageTemplate.WeekPlanner
    TEMPLATE_PLANNER -> plannerTemplate(titled = false, dated = false)
    TEMPLATE_PLANNER_TITLED -> plannerTemplate(titled = true, dated = false)
    TEMPLATE_PLANNER_DATED -> plannerTemplate(titled = true, dated = true)
    TEMPLATE_PLANNER_STYLED -> plannerTemplate(titled = true, dated = false, styled = true)
    TEMPLATE_PLANNER_ROLES -> plannerTemplate(titled = true, dated = false, styled = true, restyled = true)
    else -> throw CorruptFileException("Unknown template $tag")
}

private fun ByteReader.plannerTemplate(titled: Boolean, dated: Boolean, styled: Boolean = false, restyled: Boolean = false): PageTemplate {
    val kinds = PlannerKind.entries
    val kind = byte()
    if (kind !in kinds.indices) throw CorruptFileException("Unknown planner page $kind")
    val arg = varint()
    val year = varint()
    val month = byte()
    val shift = byte()
    if (year !in PlannerSetup.MIN_YEAR..PlannerSetup.MAX_YEAR || month !in 1..12 || shift !in 0..6) {
        throw CorruptFileException("Planner dates out of range: $year, $month, $shift")
    }
    val page = PlannerPage(kinds[kind], arg)
    val setup = PlannerSetup(year, month, shift)
    if (!styled) return PageTemplate.Planner(page, setup, if (titled) string() else "", if (dated) List(PlannerPages.SEMESTER_DATES) { int() } else emptyList())
    val title = string()
    val semesters = if (bool()) List(PlannerPages.SEMESTER_DATES) { int() } else emptyList()
    val colorCount = byte()
    if (colorCount != 0 && colorCount != PlannerColors.COUNT) throw CorruptFileException("Planner colors: $colorCount")
    val colors = List(colorCount) { int() }
    val strokeCount = varint()
    if (strokeCount !in 0..PlannerPages.SEMESTER_DATES * DateStroke.MAX_PER_SLOT) throw CorruptFileException("Planner date strokes: $strokeCount")
    val ink = List(strokeCount) {
        val slot = byte()
        if (slot !in 0 until PlannerPages.SEMESTER_DATES) throw CorruptFileException("Planner date slot $slot")
        val color = int()
        val width = float()
        val points = varint()
        if (points !in 1..DateStroke.MAX_POINTS) throw CorruptFileException("Planner date stroke points: $points")
        DateStroke(slot, color, width, List(points * 2) { float() })
    }
    val roles = if (restyled) roleStyles() else PlannerRoleStyles.NONE
    return PageTemplate.Planner(page, setup, title, semesters, colors, ink, roles)
}

private const val STYLE_TEXT = 1
private const val STYLE_LINE = 2
private const val STYLE_BACKGROUND = 4
private const val STYLE_SHADING = 8
private const val STYLE_FONT = 16
private const val STYLE_PLAIN = 32

private fun ByteWriter.roleStyle(style: RoleStyle) {
    var mask = 0
    if (style.text != null) mask = mask or STYLE_TEXT
    if (style.line != null) mask = mask or STYLE_LINE
    if (style.background != null) mask = mask or STYLE_BACKGROUND
    if (style.shading != null) mask = mask or STYLE_SHADING
    if (style.font != null) mask = mask or STYLE_FONT
    if (style.plain) mask = mask or STYLE_PLAIN
    byte(mask)
    style.text?.let { int(it) }
    style.line?.let { int(it) }
    style.background?.let { int(it) }
    style.shading?.let { int(it) }
    style.font?.let { string(it) }
}

private fun ByteReader.roleStyle(): RoleStyle {
    val mask = byte()
    if (mask == 0 || mask >= STYLE_PLAIN * 2) throw CorruptFileException("Planner style parts: $mask")
    val text = if (mask and STYLE_TEXT != 0) int() else null
    val line = if (mask and STYLE_LINE != 0) int() else null
    val background = if (mask and STYLE_BACKGROUND != 0) int() else null
    val shading = if (mask and STYLE_SHADING != 0) int() else null
    val font = if (mask and STYLE_FONT != 0) string() else null
    return RoleStyle(text, line, background, shading, font, mask and STYLE_PLAIN != 0)
}

private fun ByteWriter.roleStyles(roles: PlannerRoleStyles) {
    varint(roles.byRole.size)
    for ((role, style) in roles.byRole) {
        byte(role.ordinal)
        roleStyle(style)
    }
    varint(roles.byElement.size)
    for ((key, style) in roles.byElement) {
        byte(key.role.ordinal)
        int(key.x)
        int(key.y)
        roleStyle(style)
    }
}

private fun ByteReader.roleStyles(): PlannerRoleStyles {
    val roles = PlannerRole.entries
    val roleCount = varint()
    if (roleCount !in 0..roles.size) throw CorruptFileException("Planner role styles: $roleCount")
    val byRole = LinkedHashMap<PlannerRole, RoleStyle>()
    repeat(roleCount) {
        val ordinal = byte()
        if (ordinal !in 1 until roles.size) throw CorruptFileException("Unknown planner role $ordinal")
        byRole[roles[ordinal]] = roleStyle()
    }
    val elementCount = varint()
    if (elementCount !in 0..PlannerRoleStyles.MAX_ELEMENTS) throw CorruptFileException("Planner element styles: $elementCount")
    val byElement = LinkedHashMap<ElementKey, RoleStyle>()
    repeat(elementCount) {
        val ordinal = byte()
        if (ordinal !in 1 until roles.size) throw CorruptFileException("Unknown planner role $ordinal")
        val x = int()
        val y = int()
        byElement[ElementKey(roles[ordinal], x, y)] = roleStyle()
    }
    return PlannerRoleStyles(byRole, byElement)
}

internal fun ByteWriter.customLayout(layout: CustomLayout) {
    string(layout.name)
    pageSize(layout.size)
    LENGTH_UNITS.write(this, layout.displayUnit)
    varint(layout.elements.size)
    layout.elements.forEach { element(it) }
    // Version 2 added the paper color, the pattern under the elements and book style.
    if (version >= 2) {
        int(layout.paper)
        template(layout.base)
        bool(layout.bookStyle)
    }
    // Version 3 added where a Cornell or legal frame's lines sit.
    if (version >= 3) {
        varint(layout.frameLines.size)
        layout.frameLines.forEach { float(it) }
    }
}

internal fun ByteReader.customLayout(): CustomLayout {
    val name = string()
    val size = pageSize()
    val unit = LENGTH_UNITS.read(this)
    val count = count(MIN_ELEMENT_BYTES)
    val elements = List(count) { element() }
    if (version < 2) return CustomLayout(name, size, unit, elements)
    val paper = int()
    val base = template()
    if (base is PageTemplate.Custom) throw CorruptFileException("A layout's base is a custom layout")
    val bookStyle = bool()
    if (version < 3) return CustomLayout(name, size, unit, elements, paper, base, bookStyle)
    return CustomLayout(name, size, unit, elements, paper, base, bookStyle, List(count(4)) { float() })
}

private fun ByteWriter.element(element: LayoutElement) {
    when (element) {
        is LayoutElement.Line -> {
            byte(ELEMENT_LINE)
            float(element.x0)
            float(element.y0)
            float(element.x1)
            float(element.y1)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Rectangle -> {
            byte(ELEMENT_RECTANGLE)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Table -> {
            byte(ELEMENT_TABLE)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            varint(element.rows)
            varint(element.columns)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Ruled -> {
            byte(ELEMENT_RULED)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            float(element.spacing)
            float(element.lineWidth)
            int(element.color)
        }
        is LayoutElement.Sticky -> {
            byte(ELEMENT_STICKY)
            float(element.x)
            float(element.y)
            float(element.width)
            float(element.height)
            int(element.color)
        }
    }
}

private fun ByteReader.element(): LayoutElement = when (val tag = byte()) {
    ELEMENT_LINE -> LayoutElement.Line(float(), float(), float(), float(), float(), int())
    ELEMENT_RECTANGLE -> LayoutElement.Rectangle(float(), float(), float(), float(), float(), int())
    ELEMENT_TABLE -> LayoutElement.Table(float(), float(), float(), float(), varint(), varint(), float(), int())
    ELEMENT_RULED -> LayoutElement.Ruled(float(), float(), float(), float(), float(), float(), int())
    ELEMENT_STICKY -> LayoutElement.Sticky(float(), float(), float(), float(), int())
    else -> throw CorruptFileException("Unknown layout element $tag")
}
