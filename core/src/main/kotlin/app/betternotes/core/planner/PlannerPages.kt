package app.betternotes.core.planner

import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.SetPlannerPages
import app.betternotes.core.edit.UpdatePage
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.Page
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate

/** Makes the pages of a planner, and the edit that moves it to other dates. */
object PlannerPages {
    /** The cover's design id. */
    const val COVER_ID = "planner"

    /** Planner paper is white, as in the reference. */
    const val PAPER = 0xFFFFFFFF.toInt()

    val SIZE: PageSize = PageSize.A4

    const val MAX_TITLE = 60

    /** Four semesters, a start and an end each. */
    const val SEMESTER_DATES = 8

    /**
     * The cover and all pages for [setup]. A page whose kind and number are in [reuse] keeps that id, so a planner moved to other dates
     * keeps what was drawn on the pages that are still there.
     */
    fun build(
        setup: PlannerSetup,
        reuse: Map<PlannerPage, Long>,
        ids: IdSource,
        title: String = "",
        semesters: List<Int> = emptyList(),
        colors: List<Int> = emptyList(),
        dateInk: List<DateStroke> = emptyList(),
        roles: PlannerRoleStyles = PlannerRoleStyles.NONE,
        /** The styles of single elements, kept with the page they were made on. */
        elementStyles: Map<PlannerPage, Map<ElementKey, RoleStyle>> = emptyMap(),
    ): List<Page> {
        val plan = PlannerPlan(setup)
        val used = HashSet<Long>(reuse.values)
        fun idFor(page: PlannerPage): Long = reuse[page] ?: generateSequence { ids.next() }.first { used.add(it) }
        val cover = Page(
            idFor(PlannerPage.Cover),
            SIZE,
            PageBackground(PAPER, PageTemplate.Planner(PlannerPage.Cover, setup, title, colors = colors, roles = roles.onlyRoles())),
            cover = COVER_ID,
        )
        return buildList(plan.size + 1) {
            add(cover)
            for (page in plan.pages) {
                val shows = page.kind in DATED_PAGES
                val template = PageTemplate.Planner(page, setup, semesters = if (shows) semesters else emptyList(), colors = colors, dateInk = if (shows) dateInk else emptyList(), roles = rolesOfPage(roles, elementStyles[page]))
                add(Page(idFor(page), SIZE, PageBackground(PAPER, template)))
            }
        }
    }

    /** The dates a new planner starts with: the full calendar year of today, or of the next year from December 1st on. */
    fun defaultSetup(today: java.time.LocalDate): PlannerSetup =
        PlannerSetup(if (today.monthValue == 12) today.year + 1 else today.year, 1)

    /** The setup of a planner document, or null if it is not one. */
    fun setupOf(document: Document): PlannerSetup? {
        if (document.kind != DocumentKind.PLANNER) return null
        return (document.pages.first().background.template as? PageTemplate.Planner)?.setup
    }

    /** The undoable change that gives the planner the dates of [setup], or null if they are the same. */
    fun change(document: Document, setup: PlannerSetup, ids: IdSource = IdSource.Random): Edit? {
        if (setupOf(document) == setup) return null
        val reuse = HashMap<PlannerPage, Long>()
        for (page in document.pages) (page.background.template as? PageTemplate.Planner)?.let { reuse[it.page] = page.id }
        val own = HashMap<PlannerPage, Map<ElementKey, RoleStyle>>()
        for (page in document.pages) (page.background.template as? PageTemplate.Planner)?.let { if (it.roles.byElement.isNotEmpty()) own[it.page] = it.roles.byElement }
        return SetPlannerPages(document.pages.toList(), build(setup, reuse, ids, titleOf(document), semestersOf(document), colorsOf(document), dateInkOf(document), rolesOf(document), own))
    }

    /** The pages that show the semester dates. */
    private val DATED_PAGES = setOf(PlannerKind.DASHBOARD, PlannerKind.SEMESTER_PLAN)

    /** Whether a page of [kind] has semester date blanks, where handwriting is shared. */
    fun showsDates(kind: PlannerKind) = kind in DATED_PAGES

    /** The semester dates (8 numbers, [PageTemplate.Planner.NO_DATE] for none), or all none. */
    fun semestersOf(document: Document): List<Int> {
        val page = document.pages.firstOrNull { (it.background.template as? PageTemplate.Planner)?.page?.kind == PlannerKind.DASHBOARD }
        val dates = (page?.background?.template as? PageTemplate.Planner)?.semesters.orEmpty()
        return dates.ifEmpty { List(SEMESTER_DATES) { PageTemplate.Planner.NO_DATE } }
    }

    /**
     * The undoable change that sets the start or [end] date of [semester] (0 to 3) to [epochDay] (null clears it) on every page that
     * shows it. Null when nothing changes or this is not a planner.
     */
    fun setSemesterDate(document: Document, semester: Int, end: Boolean, epochDay: Int?): Edit? {
        if (document.kind != DocumentKind.PLANNER || semester !in 0..3) return null
        val dates = semestersOf(document).toMutableList()
        dates[semester * 2 + if (end) 1 else 0] = epochDay ?: PageTemplate.Planner.NO_DATE
        val slot = semester * 2 + if (end) 1 else 0
        val wipeInk = epochDay == null
        val edits = document.pages.mapNotNull { page ->
            val template = page.background.template as? PageTemplate.Planner ?: return@mapNotNull null
            val ink = if (wipeInk) template.dateInk.filter { it.slot != slot } else template.dateInk
            if (template.page.kind !in DATED_PAGES || (template.semesters == dates && ink == template.dateInk)) return@mapNotNull null
            val dated = if (dates.all { it == PageTemplate.Planner.NO_DATE }) emptyList() else dates
            UpdatePage(page, page.copy(background = page.background.copy(template = template.copy(semesters = dated, dateInk = ink))))
        }
        return when (edits.size) {
            0 -> null
            1 -> edits[0]
            else -> app.betternotes.core.edit.BatchEdit(edits)
        }
    }

    /** The planner's colors (see [PlannerColors]), or none when they are the defaults. */
    fun colorsOf(document: Document): List<Int> =
        (document.pages.firstOrNull()?.background?.template as? PageTemplate.Planner)?.colors.orEmpty()

    private fun rolesOfPage(roles: PlannerRoleStyles, own: Map<ElementKey, RoleStyle>?): PlannerRoleStyles =
        if (own.isNullOrEmpty()) roles.onlyRoles() else PlannerRoleStyles(roles.byRole, own)

    /** The styles the owner gave to whole roles of the planner (the same on every page), or none. */
    fun rolesOf(document: Document): PlannerRoleStyles =
        (document.pages.firstOrNull()?.background?.template as? PageTemplate.Planner)?.roles?.onlyRoles() ?: PlannerRoleStyles.NONE

    /** What the owner does to the look of elements: sets [slot] to [color] or, for the font, to [font]; a null [slot] puts back all of it. */
    class StyleChange(val slot: RoleSlot?, val color: Int? = null, val font: String? = null) {
        fun apply(style: RoleStyle): RoleStyle = when (slot) {
            null -> RoleStyle()
            RoleSlot.FONT -> style.withFont(font)
            else -> style.withColor(slot, color)
        }
    }

    /**
     * The undoable change that applies [change] to the elements [keys] of the page [pageId], or, when [similar], to every element with one of
     * their roles on every page. Null when nothing changes or this is not a planner. Elements are never moved, and their text is never changed.
     *
     * Putting back all ([StyleChange.slot] null) leaves the exact look the page draws: with [similar] the role's style and the style of every
     * element of that role go, else only the elements' own styles go, and where the role is restyled they are marked plain.
     */
    fun restyle(document: Document, pageId: Long, keys: Collection<ElementKey>, similar: Boolean, change: StyleChange): Edit? {
        if (document.kind != DocumentKind.PLANNER || keys.isEmpty()) return null
        val roles = keys.mapTo(HashSet()) { it.role }
        // A change of lines, or putting all back, reaches the lines of the other half of a section; nothing else of it.
        val partners = if (similar && (change.slot == RoleSlot.LINE || change.slot == null)) roles.mapNotNull { linePartner(it) }.filter { it !in roles } else emptyList()
        val lineChange = StyleChange(RoleSlot.LINE, change.color)
        val before = document.pages.toList()
        val after = before.map { page ->
            val template = page.background.template as? PageTemplate.Planner ?: return@map page
            var styles = template.roles
            if (similar) {
                for (role in roles) {
                    styles = styles.withRole(role, change.apply(styles.byRole[role] ?: RoleStyle()))
                    // An element restyled on its own takes the change too, or it would keep what the owner now wants gone.
                    for ((key, own) in styles.byElement.entries.toList()) if (key.role == role) styles = styles.withElement(key, change.apply(own))
                }
                for (role in partners) styles = styles.withRoleLines(role, lineChange)
            } else if (page.id == pageId) {
                for (key in keys) {
                    val own = styles.byElement[key]
                    val next = if (change.slot == null && styles.byRole.containsKey(key.role)) RoleStyle(plain = true) else change.apply(own ?: RoleStyle())
                    styles = styles.withElement(key, next)
                }
            }
            if (styles == template.roles) page else page.copy(background = page.background.copy(template = template.copy(roles = styles)))
        }
        return if (after == before) null else SetPlannerPages(before, after)
    }

    /**
     * The undoable change that gives every element like the picked [keys] (same role, every page) the look they have now: what the owner
     * changed on them one by one, then pressed "similar" for. Null when nothing differs or this is not a planner.
     */
    fun spread(document: Document, pageId: Long, keys: Collection<ElementKey>): Edit? {
        if (document.kind != DocumentKind.PLANNER || keys.isEmpty()) return null
        val own = (document.pages.firstOrNull { it.id == pageId }?.background?.template as? PageTemplate.Planner)?.roles ?: return null
        // What each picked role looks like on the picked elements: the first one's, with its own changes over the role's.
        val looks = LinkedHashMap<PlannerRole, RoleStyle>()
        for (key in keys) if (key.role !in looks) {
            val look = own.styleOf(key.role, key.x, key.y) ?: continue
            looks[key.role] = RoleStyle(look.text, look.line, look.background, look.shading, look.font)
        }
        if (looks.isEmpty()) return null
        val before = document.pages.toList()
        val after = before.map { page ->
            val template = page.background.template as? PageTemplate.Planner ?: return@map page
            var styles = template.roles
            for ((role, look) in looks) {
                styles = styles.withRole(role, look)
                // An element restyled on its own takes the look too.
                for ((key, _) in styles.byElement.entries.toList()) if (key.role == role) styles = styles.withElement(key, RoleStyle())
                val partner = linePartner(role)
                if (look.line != null && partner != null && partner !in looks) styles = styles.withRoleLines(partner, StyleChange(RoleSlot.LINE, look.line))
            }
            if (styles == template.roles) page else page.copy(background = page.background.copy(template = template.copy(roles = styles)))
        }
        return if (after == before) null else SetPlannerPages(before, after)
    }

    /** The lines of a section are its heading's rule and its ruled rows: for all elements like one, the owner means them together. */
    private fun linePartner(role: PlannerRole): PlannerRole? = when (role) {
        PlannerRole.SECTION_HEADING -> PlannerRole.ROWS
        PlannerRole.ROWS -> PlannerRole.SECTION_HEADING
        else -> null
    }

    /** These styles with [change], a change of lines, on [role] and on every element of it restyled on its own. */
    private fun PlannerRoleStyles.withRoleLines(role: PlannerRole, change: StyleChange): PlannerRoleStyles {
        var styles = withRole(role, change.apply(byRole[role] ?: RoleStyle()))
        for ((key, own) in styles.byElement.entries.toList()) if (key.role == role) styles = styles.withElement(key, change.apply(own))
        return styles
    }

    /** The handwriting in the semester date blanks. */
    fun dateInkOf(document: Document): List<DateStroke> {
        val page = document.pages.firstOrNull { (it.background.template as? PageTemplate.Planner)?.page?.kind == PlannerKind.DASHBOARD }
        return (page?.background?.template as? PageTemplate.Planner)?.dateInk.orEmpty()
    }

    /** The undoable change to the planner's [colors] on every page, or null if they are the same or this is not a planner. Ink on the pages stays. */
    fun recolor(document: Document, colors: List<Int>): Edit? {
        if (document.kind != DocumentKind.PLANNER) return null
        val clean = PlannerColors.normalized(colors)
        if (colorsOf(document) == clean) return null
        val before = document.pages.toList()
        val after = before.map { page ->
            val template = page.background.template as? PageTemplate.Planner ?: return@map page
            page.copy(background = page.background.copy(template = template.copy(colors = clean)))
        }
        return SetPlannerPages(before, after)
    }

    /**
     * The undoable change that adds [stroke] (in reference units, relative to its blank) to the handwriting of its date on every page
     * that shows it. Null when this is not a planner or that date already holds as many strokes as it keeps.
     */
    fun addDateInk(document: Document, stroke: DateStroke): Edit? {
        val ink = dateInkOf(document)
        if (ink.count { it.slot == stroke.slot } >= DateStroke.MAX_PER_SLOT) return null
        return setDateInk(document, ink + stroke)
    }

    /** The undoable change that takes the handwriting of [slot] away ([slot] null: of every date), or null if there is none. */
    fun clearDateInk(document: Document, slot: Int?): Edit? {
        val ink = dateInkOf(document)
        val kept = if (slot == null) emptyList() else ink.filter { it.slot != slot }
        return if (kept.size == ink.size) null else setDateInk(document, kept)
    }

    /** The undoable change that takes one [stroke] of the handwriting away, or null when it is not there. */
    fun removeDateStroke(document: Document, stroke: DateStroke): Edit? {
        val ink = dateInkOf(document)
        val at = ink.indexOf(stroke)
        if (at < 0) return null
        return setDateInk(document, ink.filterIndexed { i, _ -> i != at })
    }

    /** The undoable change that takes all of [strokes] away, or null when none of them is there. */
    fun removeDateStrokes(document: Document, strokes: Collection<DateStroke>): Edit? {
        val ink = dateInkOf(document)
        val kept = ink.filter { it !in strokes }
        return if (kept.size == ink.size) null else setDateInk(document, kept)
    }

    /**
     * A pen stroke ([xy]: x, y pairs in the page's points) as the handwriting of the date blank [action] names, measured from the blank's
     * top-left in the planner's reference units. A stroke with more points than a template keeps is thinned.
     */
    fun dateStroke(action: PlannerAction.PickDate, size: PageSize, xy: FloatArray, color: Int, widthPt: Float): DateStroke {
        val sx = PlannerDrawing.W / size.widthPt
        val sy = PlannerDrawing.H / size.heightPt
        val count = xy.size / 2
        val step = (count + DateStroke.MAX_POINTS - 1) / DateStroke.MAX_POINTS
        val out = ArrayList<Float>(minOf(count, DateStroke.MAX_POINTS) * 2 + 2)
        fun add(i: Int) {
            out += xy[2 * i] * sx - action.inkX
            out += xy[2 * i + 1] * sy - action.inkY
        }
        for (i in 0 until count step step) add(i)
        if ((count - 1) % step != 0) add(count - 1)
        return DateStroke(action.semester * 2 + if (action.end) 1 else 0, color, widthPt * sx, out)
    }

    /** The strokes of the date blank under ([x], [y]) on [page] (page points) that an eraser of [radiusPt] there touches. */
    fun dateStrokesNear(page: Page, x: Float, y: Float, radiusPt: Float): List<DateStroke> {
        val template = page.background.template as? PageTemplate.Planner ?: return emptyList()
        val size = page.size ?: return emptyList()
        if (template.dateInk.isEmpty() || template.page.kind !in DATED_PAGES) return emptyList()
        val action = plannerActionAt(template.page, template.setup, size, x, y) as? PlannerAction.PickDate ?: return emptyList()
        val slot = action.semester * 2 + if (action.end) 1 else 0
        val sx = PlannerDrawing.W / size.widthPt
        val px = x * sx - action.inkX
        val py = y * (PlannerDrawing.H / size.heightPt) - action.inkY
        return template.dateInk.filter { it.slot == slot && it.touches(px, py, radiusPt * sx) }
    }

    private fun setDateInk(document: Document, ink: List<DateStroke>): Edit? {
        if (document.kind != DocumentKind.PLANNER) return null
        val edits = document.pages.mapNotNull { page ->
            val template = page.background.template as? PageTemplate.Planner ?: return@mapNotNull null
            if (template.page.kind !in DATED_PAGES || template.dateInk == ink) return@mapNotNull null
            UpdatePage(page, page.copy(background = page.background.copy(template = template.copy(dateInk = ink))))
        }
        return when (edits.size) {
            0 -> null
            1 -> edits[0]
            else -> app.betternotes.core.edit.BatchEdit(edits)
        }
    }

    /** The title on the planner's cover, or empty when the owner has not typed one. */
    fun titleOf(document: Document): String =
        (document.pages.firstOrNull()?.background?.template as? PageTemplate.Planner)?.title.orEmpty()

    /** The undoable change that puts [title] on the cover, or null if it is already there or this is not a planner. */
    fun retitle(document: Document, title: String): Edit? {
        if (document.kind != DocumentKind.PLANNER) return null
        val cover = document.pages.first()
        val template = cover.background.template as? PageTemplate.Planner ?: return null
        val clean = title.trim().take(MAX_TITLE)
        if (template.title == clean) return null
        return UpdatePage(cover, cover.copy(background = cover.background.copy(template = template.copy(title = clean))))
    }
}
