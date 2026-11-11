package app.betternotes.core.planner

/**
 * The kinds of planner page. The order is what the file keeps, so only add at the end.
 * [COURSE], [MONTH], [WEEK] and [DAY] use [PlannerPage.arg].
 */
enum class PlannerKind {
    COVER,
    DASHBOARD,
    YEAR_CALENDAR,
    GENERAL,
    SEMESTER_PLAN,
    ACADEMIC,
    GRADES,
    STUDY,
    ASSIGNMENTS,
    NOTES,
    SEMESTER_OVERVIEW,
    COURSE,
    EXAM,
    GROUP,
    YEARLY_OVERVIEW,
    MEAL,
    ROUTINES,
    HABIT,
    GOALS,
    TODO,
    BLANK,
    DOTTED,
    LINED,
    GRID,
    MONTH,
    WEEK,
    DAY,
}

/** One page of a planner: its [kind] and, for a course, month, week or day, which one ([arg], from 0 except courses, which are 1 to 12). */
data class PlannerPage(val kind: PlannerKind, val arg: Int = 0) {
    companion object {
        const val COURSES = 12
        val Dashboard = PlannerPage(PlannerKind.DASHBOARD)
        val YearCalendar = PlannerPage(PlannerKind.YEAR_CALENDAR)
        val Cover = PlannerPage(PlannerKind.COVER)
        fun course(n: Int) = PlannerPage(PlannerKind.COURSE, n)
        fun month(i: Int) = PlannerPage(PlannerKind.MONTH, i)
        fun week(i: Int) = PlannerPage(PlannerKind.WEEK, i)
        fun day(i: Int) = PlannerPage(PlannerKind.DAY, i)
    }
}

/** What a tap on a part of a planner page does. */
sealed interface PlannerAction {
    data class Go(val page: PlannerPage) : PlannerAction

    /** Shows the list of weekdays for the date [dayIndex]; picking one moves every date. */
    data class PickWeekday(val dayIndex: Int) : PlannerAction

    /** On the cover: choose the year or years. */
    data object PickYears : PlannerAction

    /** On the cover: change the title. */
    data object EditTitle : PlannerAction

    /** On the index or the semester planning page: pick the start or [end] date of semester [semester] (from 0). */
    data class PickDate(val semester: Int, val end: Boolean, val inkX: Float = 0f, val inkY: Float = 0f) : PlannerAction
}

/** A tab on the right edge of every page. [month] is the planner month for a month tab, else -1. */
data class PlannerTab(val kind: Kind, val month: Int = -1) {
    enum class Kind { INDEX, YEAR, MONTH }

    val target: PlannerPage
        get() = when (kind) {
            Kind.INDEX -> PlannerPage.Dashboard
            Kind.YEAR -> PlannerPage.YearCalendar
            Kind.MONTH -> PlannerPage.month(month)
        }
}

/**
 * The pages of a planner, in order, and where each group starts. A planner has about 500 pages, so nothing here is drawn:
 * a page is only drawn when it is on screen.
 */
class PlannerPlan(val setup: PlannerSetup) {
    val pages: List<PlannerPage>

    private val firstOfMonth: IntArray

    /** Index in [pages] of every page, for a tap that jumps to one. */
    private val indexByPage: Map<PlannerPage, Int>

    init {
        val list = ArrayList<PlannerPage>(FIXED.size + setup.monthCount + setup.weekCount + setup.dayCount)
        list.addAll(FIXED)
        firstOfMonth = IntArray(setup.monthCount)
        val weeksOfMonth = Array(setup.monthCount) { ArrayList<Int>() }
        for (w in 0 until setup.weekCount) weeksOfMonth[setup.monthOfWeek(w)].add(w)
        var day = 0
        for (m in 0 until setup.monthCount) {
            firstOfMonth[m] = list.size
            list.add(PlannerPage.month(m))
            for (w in weeksOfMonth[m]) list.add(PlannerPage.week(w))
            repeat(setup.daysInMonth(m)) { list.add(PlannerPage.day(day++)) }
        }
        pages = list
        indexByPage = HashMap<PlannerPage, Int>(list.size * 2).also { map -> list.forEachIndexed { i, p -> map[p] = i } }
    }

    val size get() = pages.size

    /** Index in [pages] of [page], or -1 if this planner has none like it. */
    fun indexOf(page: PlannerPage): Int = indexByPage[page] ?: -1

    /** The tabs, from the top: Index, Year, then one for each month. */
    val tabs: List<PlannerTab> = plannerTabs(setup)

    /** The tab a page flows into: its month's, the Year tab for the calendar, or the Index tab for the planning pages. */
    fun tabOf(page: PlannerPage): Int = plannerTabOf(setup, page)

    companion object {
        /** Everything before the first month, in the order of the owner's list. */
        val FIXED: List<PlannerPage> = buildList {
            add(PlannerPage.Dashboard)
            add(PlannerPage.YearCalendar)
            for (kind in listOf(
                PlannerKind.GENERAL, PlannerKind.SEMESTER_PLAN, PlannerKind.ACADEMIC, PlannerKind.GRADES, PlannerKind.STUDY,
                PlannerKind.ASSIGNMENTS, PlannerKind.NOTES, PlannerKind.SEMESTER_OVERVIEW,
            )) add(PlannerPage(kind))
            for (n in 1..PlannerPage.COURSES) add(PlannerPage.course(n))
            for (kind in listOf(
                PlannerKind.EXAM, PlannerKind.GROUP, PlannerKind.YEARLY_OVERVIEW, PlannerKind.MEAL, PlannerKind.ROUTINES,
                PlannerKind.HABIT, PlannerKind.GOALS, PlannerKind.TODO, PlannerKind.BLANK, PlannerKind.DOTTED,
                PlannerKind.LINED, PlannerKind.GRID,
            )) add(PlannerPage(kind))
        }
    }
}
