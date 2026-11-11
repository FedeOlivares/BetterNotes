package app.betternotes.core.planner

/**
 * What a generated element of a planner page is, for the owner's "apply to similar items". Every element a page draws has one role;
 * elements with the same role look alike and are recolored together. The order is what the file keeps, so only add at the end.
 * [NONE] is never stored: it marks what is not styled (the pattern pages, the date handwriting).
 */
enum class PlannerRole {
    NONE,

    /** A rounded button or tile: Yearly Overview, Routines Planner, Grade Tracker, VIEW WEEK, and the large Dotted tile. */
    BUTTON,

    /** A page title: "Week 22", "03 | March", "General Planning". */
    PAGE_TITLE,

    /** The small text at the right of a title: the year, the week's dates. */
    SUBTITLE,

    /** The long date of a day page ("Friday, May 1, 2026") and the line under its weekday. */
    DAY_DATE,

    /** A section heading, with its line when it has one: SCHEDULE, TOP PRIORITIES, STUDENT TASKS. */
    SECTION_HEADING,

    /** Small plain text: form labels, the day numbers, the hours. */
    LABEL,

    /** The row of weekday names. */
    WEEKDAY_HEADER,

    /** A loose rule. */
    RULE,

    /** A block of ruled rows, with the numbers and slashes in them. */
    ROWS,

    /** A frame drawn around an area. */
    BOX,

    /** A filled band: the week-number column, the days outside the month, the hours. */
    SHADE,

    /** The header band of a table, with its column names. */
    TABLE_HEADER,

    /** A tab on the right edge. */
    TAB,
}

/** The parts of an element the owner can change. */
enum class RoleSlot { TEXT, LINE, BACKGROUND, SHADING, FONT }

/**
 * How one role or one element looks, each part null when it is left as drawn. [font] is the name of a font of the font library.
 * [background] is a solid color behind the element; [shading] is the color of its see-through fills.
 */
data class RoleStyle(
    val text: Int? = null,
    val line: Int? = null,
    val background: Int? = null,
    val shading: Int? = null,
    val font: String? = null,
    /**
     * Only on a style of one element: its role's style does not apply to it, so it is drawn as the page draws it, except for the parts set here.
     * It is how "reset" puts one element back while its role stays restyled.
     */
    val plain: Boolean = false,
) {
    val isEmpty get() = text == null && line == null && background == null && shading == null && font == null && !plain

    /** This style where it says something, [base] elsewhere ([base] not at all for a [plain] style). */
    fun over(base: RoleStyle?): RoleStyle = if (base == null || plain) this else RoleStyle(
        text ?: base.text,
        line ?: base.line,
        background ?: base.background,
        shading ?: base.shading,
        font ?: base.font,
    )

    /** The color of [slot], or null. */
    fun colorOf(slot: RoleSlot): Int? = when (slot) {
        RoleSlot.TEXT -> text
        RoleSlot.LINE -> line
        RoleSlot.BACKGROUND -> background
        RoleSlot.SHADING -> shading
        RoleSlot.FONT -> null
    }

    /** This style with [slot] set to [color] (null puts it back to as drawn). */
    fun withColor(slot: RoleSlot, color: Int?): RoleStyle = when (slot) {
        RoleSlot.TEXT -> copy(text = color)
        RoleSlot.LINE -> copy(line = color)
        RoleSlot.BACKGROUND -> copy(background = color)
        RoleSlot.SHADING -> copy(shading = color)
        RoleSlot.FONT -> this
    }

    fun withFont(name: String?): RoleStyle = copy(font = name?.takeIf { it.isNotEmpty() })
}

/** One element of one page: its role and where it starts, in the planner's reference units (1000 x 1414), rounded. */
data class ElementKey(val role: PlannerRole, val x: Int, val y: Int)

/**
 * The look the owner gave to the elements of a planner: by role (every element with that role, on every page) and by single element
 * (only on the page whose template holds it). An element's own style wins over its role's, part by part. Nothing here means the planner is
 * drawn exactly as before, and its file is unchanged.
 */
data class PlannerRoleStyles(
    val byRole: Map<PlannerRole, RoleStyle> = emptyMap(),
    val byElement: Map<ElementKey, RoleStyle> = emptyMap(),
) {
    val isEmpty get() = byRole.isEmpty() && byElement.isEmpty()

    /** How the element [role] at ([x], [y]) looks, or null when nothing was changed. */
    fun styleOf(role: PlannerRole, x: Int, y: Int): RoleStyle? {
        val base = byRole[role]
        if (byElement.isEmpty()) return base
        val own = byElement[ElementKey(role, x, y)] ?: return base
        return own.over(base)
    }

    /** These styles with [style] for every element of [role] (an empty style removes it). */
    fun withRole(role: PlannerRole, style: RoleStyle): PlannerRoleStyles =
        copy(byRole = if (style.isEmpty) byRole - role else byRole + (role to style))

    /** These styles with [style] for the one element [key] (an empty style removes it). */
    fun withElement(key: ElementKey, style: RoleStyle): PlannerRoleStyles =
        copy(byElement = if (style.isEmpty) byElement - key else byElement + (key to style))

    /** Only the role styles, for the pages that do not hold the element styles of another. */
    fun onlyRoles() = if (byElement.isEmpty()) this else copy(byElement = emptyMap())

    companion object {
        val NONE = PlannerRoleStyles()

        /** The most styles one page keeps, so a corrupt file cannot grow a template without end. */
        const val MAX_ELEMENTS = 2000
    }
}

/** An element found on a planner page by [plannerElements]: what the lasso can pick. [bounds] are in the page's points. */
class PlannerElement(
    val role: PlannerRole,
    val key: ElementKey,
    var bounds: app.betternotes.core.geometry.Bounds,
) {
    var hasText = false
    var hasLine = false
    var hasShade = false

    /**
     * Points (x, y pairs, in the page's points) on what this element draws: the middle of each of its lines and fills, the start of each text.
     * A loop that surrounds one of them picks the element, so a single heading, rule or word can be picked out of a big element.
     */
    var anchors = FloatArray(0)
        private set
    private var anchorCount = 0

    fun addAnchor(x: Float, y: Float) {
        if (anchorCount + 2 > MAX_ANCHORS * 2) return
        if (anchorCount + 2 > anchors.size) anchors = anchors.copyOf(maxOf(16, anchors.size * 2))
        anchors[anchorCount++] = x
        anchors[anchorCount++] = y
    }

    /** Whether [inside] says yes to the middle of the element or to any of its anchors. */
    inline fun surrounded(inside: (Float, Float) -> Boolean): Boolean {
        if (inside(bounds.centerX, bounds.centerY)) return true
        var i = 0
        while (i < anchorCount()) {
            if (inside(anchors[i], anchors[i + 1])) return true
            i += 2
        }
        return false
    }

    fun anchorCount() = anchorCount

    companion object {
        const val MAX_ANCHORS = 96
    }

    /** The parts of this element that can be changed: the background always, the others when it has them. */
    val slots: List<RoleSlot>
        get() = buildList {
            if (hasText) add(RoleSlot.TEXT)
            if (hasLine) add(RoleSlot.LINE)
            add(RoleSlot.BACKGROUND)
            if (hasShade) add(RoleSlot.SHADING)
            if (hasText) add(RoleSlot.FONT)
        }
}
