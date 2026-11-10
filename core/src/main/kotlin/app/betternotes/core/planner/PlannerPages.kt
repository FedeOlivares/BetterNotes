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
    ): List<Page> {
        val plan = PlannerPlan(setup)
        val used = HashSet<Long>(reuse.values)
        fun idFor(page: PlannerPage): Long = reuse[page] ?: generateSequence { ids.next() }.first { used.add(it) }
        val cover = Page(
            idFor(PlannerPage.Cover),
            SIZE,
            PageBackground(PAPER, PageTemplate.Planner(PlannerPage.Cover, setup, title)),
            cover = COVER_ID,
        )
        return buildList(plan.size + 1) {
            add(cover)
            for (page in plan.pages) {
                val dated = if (page.kind in DATED_PAGES) semesters else emptyList()
                add(Page(idFor(page), SIZE, PageBackground(PAPER, PageTemplate.Planner(page, setup, semesters = dated))))
            }
        }
    }

    /** The dates a new planner starts with: the mid-year one that began last July (or this July, from July on), as in the reference. */
    fun defaultSetup(today: java.time.LocalDate): PlannerSetup =
        PlannerSetup(if (today.monthValue >= 7) today.year else today.year - 1, 7)

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
        return SetPlannerPages(document.pages.toList(), build(setup, reuse, ids, titleOf(document), semestersOf(document)))
    }

    /** The pages that show the semester dates. */
    private val DATED_PAGES = setOf(PlannerKind.DASHBOARD, PlannerKind.SEMESTER_PLAN)

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
        val edits = document.pages.mapNotNull { page ->
            val template = page.background.template as? PageTemplate.Planner ?: return@mapNotNull null
            if (template.page.kind !in DATED_PAGES || template.semesters == dates) return@mapNotNull null
            val dated = if (dates.all { it == PageTemplate.Planner.NO_DATE }) emptyList() else dates
            UpdatePage(page, page.copy(background = page.background.copy(template = template.copy(semesters = dated))))
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
