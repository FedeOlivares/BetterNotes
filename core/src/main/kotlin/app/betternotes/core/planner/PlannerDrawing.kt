package app.betternotes.core.planner

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.template.BuiltInInk
import app.betternotes.core.template.Layer
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import app.betternotes.core.template.draw
import java.time.LocalDate

/** Told of each tappable box of a planner page, in the page's points, in the order they are drawn: the last one over a point wins. */
fun interface LinkOut {
    fun link(x0: Float, y0: Float, x1: Float, y1: Float, action: PlannerAction)
}

/** What a tap at ([x], [y]) on a planner [page] does, or null if it lands on nothing. Coordinates are in the page's points. */
fun plannerActionAt(page: PlannerPage, setup: PlannerSetup, size: PageSize, x: Float, y: Float): PlannerAction? {
    var hit: PlannerAction? = null
    val out = LinkOut { x0, y0, x1, y1, action -> if (x in x0..x1 && y in y0..y1) hit = action }
    PlannerDrawing.draw(page, setup, size, BuiltInInk(PLAIN_PAPER), NullSink, out)
    return hit
}

private const val PLAIN_PAPER = 0xFFFFFFFF.toInt()

private object NullSink : TemplateSink {
    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) = Unit
    override fun dot(x: Float, y: Float, radius: Float, color: Int) = Unit
    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) = Unit
}

/**
 * Draws the planner's pages from the dates of a [PlannerSetup]. The drawing and the tappable boxes come from the same code,
 * so they cannot drift apart. Reference paper: 1000 x 1414, with the right strip left for the tabs.
 */
internal object PlannerDrawing {
    const val W = 1000f
    const val H = 1414f
    private const val L = 70f
    private const val R = 890f
    /** Where the page ends and the tabs begin. */
    private const val XE = 924f
    private const val TOP_TITLE = 112f
    private const val BUTTON_RADIUS = 12f
    private const val SEMESTER_BUTTON = 110f
    private const val TILE_RADIUS = 22f

    fun draw(
        page: PlannerPage,
        setup: PlannerSetup,
        size: PageSize,
        ink: BuiltInInk,
        sink: TemplateSink,
        links: LinkOut?,
        paper: Int = PLAIN_PAPER,
        title: String = "",
        semesters: List<Int> = emptyList(),
        dateInk: List<DateStroke> = emptyList(),
    ) {
        val c = Layer(size, ink, sink, 0f, 0f, W, H, links)
        val s = Sheet(c, ink, setup, sink, size, paper, semesters, dateInk)
        if (page.kind == PlannerKind.COVER) return s.cover(title)
        s.tabs(page)
        when (page.kind) {
            PlannerKind.DASHBOARD -> s.dashboard()
            PlannerKind.YEAR_CALENDAR -> s.yearCalendar()
            PlannerKind.GENERAL -> s.general()
            PlannerKind.SEMESTER_PLAN -> s.semesterPlan()
            PlannerKind.ACADEMIC -> s.academic()
            PlannerKind.GRADES -> s.grades()
            PlannerKind.STUDY -> s.study()
            PlannerKind.ASSIGNMENTS -> s.assignments()
            PlannerKind.NOTES -> s.notes()
            PlannerKind.SEMESTER_OVERVIEW -> s.semesterOverview()
            PlannerKind.COURSE -> s.course(page.arg)
            PlannerKind.EXAM -> s.exam()
            PlannerKind.GROUP -> s.group()
            PlannerKind.YEARLY_OVERVIEW -> s.yearlyOverview()
            PlannerKind.MEAL -> s.meal()
            PlannerKind.ROUTINES -> s.routines()
            PlannerKind.HABIT -> s.habit()
            PlannerKind.GOALS -> s.goals()
            PlannerKind.TODO -> s.todo()
            PlannerKind.BLANK -> Unit
            PlannerKind.DOTTED -> s.pattern(PageTemplate.Dotted(spacing = 20f))
            PlannerKind.LINED -> s.pattern(PageTemplate.Lined(spacing = 30f))
            PlannerKind.GRID -> s.pattern(PageTemplate.Grid(spacing = 20f))
            PlannerKind.MONTH -> s.month(page.arg)
            PlannerKind.WEEK -> s.week(page.arg)
            PlannerKind.DAY -> s.day(page.arg)
            PlannerKind.COVER -> Unit
        }
    }

    private class Sheet(
        val c: Layer,
        val ink: BuiltInInk,
        val setup: PlannerSetup,
        val sink: TemplateSink,
        val size: PageSize,
        val paper: Int,
        val semesters: List<Int>,
        val dateInk: List<DateStroke>,
    ) {
        private val es = sink.language == "es"
        private fun t(en: String, es: String) = c.pick(en, es)
        private fun go(page: PlannerPage) = PlannerAction.Go(page)

        // ---- Words -------------------------------------------------------------------------------------------

        private val months = if (es) {
            listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        } else {
            listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        }
        private val monthsShort = if (es) {
            listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
        } else {
            listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        }
        private val weekdays = if (es) {
            listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        } else {
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        }
        private val weekdaysShort = if (es) {
            listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")
        } else {
            listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        }
        private val initials = if (es) listOf("L", "M", "M", "J", "V", "S", "D") else listOf("M", "T", "W", "T", "F", "S", "S")

        private fun monthName(date: LocalDate) = months[date.monthValue - 1]

        /** A typed semester date as "Jul 1, 2026" or "1 jul 2026", or null when none is set. */
        private fun semesterDate(index: Int, short: Boolean = false): String? {
            val day = semesters.getOrNull(index)?.takeIf { it != 0 } ?: return null
            val date = LocalDate.ofEpochDay(day.toLong())
            // The index has little room: numbers only, day first in Spanish and month first in English.
            if (short) {
                val yy = two(date.year % 100)
                return if (es) "${two(date.dayOfMonth)}/${two(date.monthValue)}/$yy" else "${two(date.monthValue)}/${two(date.dayOfMonth)}/$yy"
            }
            val month = monthsShort[date.monthValue - 1].lowercase()
            return if (es) "${date.dayOfMonth} $month ${date.year}" else "${month.replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}, ${date.year}"
        }

        /** A date as the day pages write it: "Wednesday, July 1, 2026" or "Miércoles, 1 de julio de 2026" (without the weekday). */
        private fun longDate(date: LocalDate) =
            if (es) "${date.dayOfMonth} de ${monthName(date).lowercase()} de ${date.year}" else "${monthName(date)} ${date.dayOfMonth}, ${date.year}"

        /** The handwriting of date [slot], with its blank's top-left at ([x], [y]). */
        private fun writing(slot: Int, x: Float, y: Float) {
            for (stroke in dateInk) if (stroke.slot == slot) c.polyline(stroke.points, x, y, stroke.width, stroke.color)
        }

        private fun two(n: Int) = n.toString().padStart(2, '0')

        // ---- Drawing helpers ---------------------------------------------------------------------------------

        fun title(text: String, y: Float = TOP_TITLE) = c.text(text, L, y, 40f, style = TextStyle.TITLE)

        /** Text that ends at [right]. The width is estimated, because a sink only measures nothing. */
        fun rightText(text: String, right: Float, y: Float, size: Float, style: TextStyle = TextStyle.HEADING, color: Int = ink.text) {
            val width = text.length * size * (if (style == TextStyle.TITLE) 0.6f else 0.74f)
            c.text(text, right - width / 2f, y, size, color, style, TextAlign.CENTER)
        }

        fun heading(text: String, x0: Float, x1: Float, y: Float) {
            c.text(text, x0, y, 16f, style = TextStyle.HEADING)
            c.hline(x0, x1, y + 18f, ink.accent)
        }

        /**
         * A blue rule on top, [n] rows closed by gray rules (the last by a blue one), and an optional vertical gray divider at [divX].
         * [slashes] puts a date slash in a column [slashW] wide at the left.
         */
        fun rows(x0: Float, x1: Float, top: Float, n: Int, step: Float, divX: Float? = null, slashW: Float = 0f, numbered: Boolean = false) {
            c.hline(x0, x1, top, ink.accent)
            for (i in 0 until n) {
                val y = top + step * (i + 1)
                if (slashW > 0f) c.text("/", x0 + slashW / 2f, y - step / 2f, 17f, ink.faint, align = TextAlign.CENTER)
                if (numbered) c.text(two(i + 1), x0 + 8f, y - step / 2f, 15f, ink.faint)
                c.hline(x0, x1, y, if (i == n - 1) ink.accent else ink.rule)
            }
            val bottom = top + step * n
            if (divX != null) c.vline(divX, top, bottom, ink.rule)
            if (slashW > 0f) c.vline(x0 + slashW, top, bottom, ink.rule)
        }

        /** Rows with a label at the left of each. */
        fun labelRows(x0: Float, x1: Float, top: Float, labels: List<String>, step: Float, lastAccent: Boolean = true) {
            c.hline(x0, x1, top, ink.accent)
            for ((i, label) in labels.withIndex()) {
                val y = top + step * (i + 1)
                c.text(label, x0 + 2f, y - step / 2f, 15f, ink.text)
                c.hline(x0, x1, y, if (lastAccent && i == labels.lastIndex) ink.accent else ink.rule)
            }
        }

        /** A box with round corners, outlined in [color]. */
        fun roundBox(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, color: Int, weight: Float = 1.5f) {
            c.hline(x0 + r, x1 - r, y0, color, weight)
            c.hline(x0 + r, x1 - r, y1, color, weight)
            c.vline(x0, y0 + r, y1 - r, color, weight)
            c.vline(x1, y0 + r, y1 - r, color, weight)
            arc(x0 + r, y0 + r, r, 180f, 270f, color, weight)
            arc(x1 - r, y0 + r, r, 270f, 360f, color, weight)
            arc(x1 - r, y1 - r, r, 0f, 90f, color, weight)
            arc(x0 + r, y1 - r, r, 90f, 180f, color, weight)
        }

        fun button(x0: Float, y0: Float, x1: Float, y1: Float, text: String, action: PlannerAction?, size: Float = 15f) {
            roundBox(x0, y0, x1, y1, minOf(BUTTON_RADIUS, (y1 - y0) / 2f), ink.rule)
            c.text(text, (x0 + x1) / 2f, (y0 + y1) / 2f, size, align = TextAlign.CENTER)
            if (action != null) c.link(x0, y0, x1, y1, action)
        }

        /** A grid of buttons, [cols] to a row. Returns the y under the last row. */
        fun buttons(items: List<Pair<String, PlannerAction?>>, top: Float, cols: Int = 4, height: Float = 36f, gap: Float = 12f): Float {
            val width = (R - L - gap * (cols - 1)) / cols
            for ((i, item) in items.withIndex()) {
                val x = L + (i % cols) * (width + gap)
                val y = top + (i / cols) * (height + gap)
                button(x, y, x + width, y + height, item.first, item.second)
            }
            return top + ((items.size + cols - 1) / cols) * (height + gap)
        }

        fun box(x0: Float, y0: Float, x1: Float, y1: Float) = c.box(x0, y0, x1, y1, ink.accent)

        // ---- Tabs ----------------------------------------------------------------------------------------------

        /** [color] with [alpha] (0 to 255) of black over it, for the shadow one tab casts on the next. */
        private fun shadow(alpha: Int) = alpha shl 24

        /** A filled box with round corners on the right, for a tab: opaque [color] so overlaps do not show. */
        private fun tabBody(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, color: Int) {
            c.fill(x0, y0, x1 - r, y1, color)
            c.fill(x1 - r, y0 + r, x1, y1 - r, color)
            c.dot(x1 - r, y0 + r, r, color)
            c.dot(x1 - r, y1 - r, r, color)
        }

        /** The outline of a tab's top, round right-hand corners, right side and bottom. */
        private fun tabOutline(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, color: Int) {
            c.hline(x0, x1 - r, y0, color, 1.2f)
            c.hline(x0, x1 - r, y1, color, 1.2f)
            c.vline(x1, y0 + r, y1 - r, color, 1.2f)
            arc(x1 - r, y0 + r, r, 270f, 360f, color)
            arc(x1 - r, y1 - r, r, 0f, 90f, color)
        }

        private fun arc(cx: Float, cy: Float, r: Float, fromDeg: Float, toDeg: Float, color: Int, weight: Float = 1.2f) {
            val steps = 5
            var px = cx + r * kotlin.math.cos(Math.toRadians(fromDeg.toDouble())).toFloat()
            var py = cy + r * kotlin.math.sin(Math.toRadians(fromDeg.toDouble())).toFloat()
            for (i in 1..steps) {
                val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * i / steps).toDouble())
                val x = cx + r * kotlin.math.cos(a).toFloat()
                val y = cy + r * kotlin.math.sin(a).toFloat()
                c.line(px, py, x, y, color, weight)
                px = x
                py = y
            }
        }

        /**
         * The tabs down the right edge, as the tabs of a real planner: each is a rounded, shaded piece of paper under the one above it.
         * The tab of the page's own group is paper-colored and runs into the page, which has no edge line there, so the page flows
         * into it: every page of July, whichever day, is joined to the July tab.
         */
        fun tabs(page: PlannerPage) {
            val tabs = plannerTabs(setup)
            val current = plannerTabOf(setup, page)
            // The tabs fill the edge from the top to the foot of the page, so the last one ends at the bottom.
            val top = 44f
            val step = (H - top - 44f) / tabs.size
            val r = 12f
            val x0 = XE
            val x1 = W - 4f
            val loose = 0xFFEDEDEF.toInt()
            // The page's edge, interrupted where the current tab joins it.
            val joinTop = top + step * current
            val joinBottom = joinTop + step
            if (current < 0) {
                c.vline(XE, 0f, H, ink.rule, 1f)
            } else {
                c.vline(XE, 0f, joinTop, ink.rule, 1f)
                c.vline(XE, joinBottom, H, ink.rule, 1f)
            }
            for ((i, tab) in tabs.withIndex()) {
                val y0 = top + step * i
                val y1 = y0 + step
                if (i != current) {
                    tabBody(x0, y0, x1, y1, r, loose)
                    // The shadow of the tab above falls along the top and fades down; the foot is a little darker too.
                    for (band in 0 until 6) {
                        c.fill(x0, y0 + band * 3f, x1 - r, y0 + band * 3f + 3f, shadow(0x26 - band * 5))
                    }
                    c.fill(x0, y1 - 4f, x1 - r, y1, shadow(0x0E))
                    tabOutline(x0, y0, x1, y1, r, ink.rule)
                }
                val label = when (tab.kind) {
                    PlannerTab.Kind.INDEX -> t("INDEX", "ÍNDICE")
                    PlannerTab.Kind.YEAR -> t("YEAR", "AÑO")
                    PlannerTab.Kind.MONTH -> monthsShort[(setup.monthStart(tab.month).monthValue - 1)]
                }
                c.link(x0, y0, W, y1, go(tab.target))
                if (i == current) continue
                c.turnedText(label, (x0 + x1) / 2f + 2f, (y0 + y1) / 2f, 14f, 90f, style = TextStyle.HEADING)
            }
            if (current >= 0) {
                // On top of the others, in the paper's color so it is one piece with the page.
                tabBody(x0 - 3f, joinTop, x1, joinBottom, r, paper)
                tabOutline(x0 - 1f, joinTop, x1, joinBottom, r, ink.rule)
                val label = when (val tab = tabs[current]) {
                    PlannerTab(PlannerTab.Kind.INDEX) -> t("INDEX", "ÍNDICE")
                    PlannerTab(PlannerTab.Kind.YEAR) -> t("YEAR", "AÑO")
                    else -> monthsShort[(setup.monthStart(tab.month).monthValue - 1)]
                }
                c.turnedText(label, (x0 + x1) / 2f + 2f, (joinTop + joinBottom) / 2f, 15f, 90f, ink.accent, TextStyle.HEADING)
            }
        }

        // ---- Cover ---------------------------------------------------------------------------------------------

        fun cover(title: String) {
            val white = 0xFFFFFFFF.toInt()
            val shown = title.ifEmpty { t("PLANNER", "AGENDA") }
            // A long title is set smaller so that it stays on the cover.
            val size = minOf(120f, 1150f / maxOf(shown.length, 1))
            c.text(shown, 500f, 520f, size, white, TextStyle.TITLE, TextAlign.CENTER)
            c.link(120f, 390f, 880f, 600f, PlannerAction.EditTitle)
            c.hline(250f, 750f, 580f, white, 3f)
            c.text(setup.yearLabel, 500f, 660f, 64f, white, TextStyle.HEADING, TextAlign.CENTER)
            c.link(220f, 610f, 780f, 710f, PlannerAction.PickYears)
        }

        // ---- Dashboard -----------------------------------------------------------------------------------------

        fun dashboard() {
            title(t("Planner Dashboard", "Panel de la agenda"))
            heading(t("MONTHLY PLANNING", "PLANIFICACIÓN MENSUAL"), L, R, 172f)
            val months = buildList {
                add(setup.yearLabel to go(PlannerPage.YearCalendar))
                for (m in 0 until setup.monthCount) add(monthName(setup.monthStart(m)) to go(PlannerPage.month(m)))
            }
            var y = buttons(months, 200f)
            heading(t("GENERAL PLANNING PAGES", "PÁGINAS DE PLANIFICACIÓN GENERAL"), L, R, y + 12f)
            y = buttons(
                listOf(
                    t("Yearly Overview", "Resumen anual") to go(PlannerPage(PlannerKind.YEARLY_OVERVIEW)),
                    t("Routines Planner", "Rutinas") to go(PlannerPage(PlannerKind.ROUTINES)),
                    t("Meal Planner", "Comidas") to go(PlannerPage(PlannerKind.MEAL)),
                    t("To-Do List", "Lista de tareas") to go(PlannerPage(PlannerKind.TODO)),
                    t("Habit Tracker", "Hábitos") to go(PlannerPage(PlannerKind.HABIT)),
                    t("Goal Planner", "Metas") to go(PlannerPage(PlannerKind.GOALS)),
                    t("Blank", "En blanco") to go(PlannerPage(PlannerKind.BLANK)),
                    t("Dotted", "Punteada") to go(PlannerPage(PlannerKind.DOTTED)),
                    t("Lined", "Rayada") to go(PlannerPage(PlannerKind.LINED)),
                    t("Grid", "Cuadriculada") to go(PlannerPage(PlannerKind.GRID)),
                ),
                y + 40f,
            )
            heading(t("SEMESTERS / QUARTERS", "SEMESTRES / TRIMESTRES"), L, R, y + 12f)
            y += 40f
            val half = (R - L) / 2f
            for (i in 0 until 4) {
                val x = L + (i % 2) * half
                val top = y + (i / 2) * 56f
                // The button is 15% narrower than before (130 to 110) with the same text size, and the start date moves in with it,
                // keeping the same gap, so there is more room to write.
                button(x, top, x + SEMESTER_BUTTON, top + 36f, t("Smstr / Qtr ${i + 1}", "Sem / Trim ${i + 1}"), go(PlannerPage(PlannerKind.SEMESTER_PLAN)), 14f)
                val startX = x + SEMESTER_BUTTON + 14f
                val endX = x + half / 2f + 74f
                val startLabel = t("Start Date:", "Inicio:")
                val endLabel = t("End Date:", "Fin:")
                c.text(startLabel, startX, top + 18f, 14f)
                c.hline(startX, endX - 12f, top + 36f, ink.rule)
                c.text(endLabel, endX, top + 18f, 14f)
                c.hline(endX, x + half - 14f, top + 36f, ink.rule)
                // The blank starts after the label and reaches its rule at the foot; handwriting is kept relative to it.
                val startInkX = startX + startLabel.length * 6.6f + 6f
                val endInkX = endX + endLabel.length * 6.6f + 6f
                val inkY = top + 36f - DateStroke.SLOT_HEIGHT
                semesterDate(i * 2, short = true)?.let { c.text(it, startInkX, top + 18f, 13f) }
                semesterDate(i * 2 + 1, short = true)?.let { c.text(it, endInkX, top + 18f, 13f) }
                writing(i * 2, startInkX, inkY)
                writing(i * 2 + 1, endInkX, inkY)
                c.link(startX, top, endX - 12f, top + 36f, PlannerAction.PickDate(i, false, startInkX, inkY))
                c.link(endX, top, x + half - 14f, top + 36f, PlannerAction.PickDate(i, true, endInkX, inkY))
            }
            y += 120f
            heading(t("ACADEMIC PLANNING PAGES", "PÁGINAS ACADÉMICAS"), L, R, y + 12f)
            y = buttons(
                listOf(
                    t("Grade Tracker", "Notas") to go(PlannerPage(PlannerKind.GRADES)),
                    t("Study Session Planner", "Sesiones de estudio") to go(PlannerPage(PlannerKind.STUDY)),
                    t("Assignment Planner", "Trabajos prácticos") to go(PlannerPage(PlannerKind.ASSIGNMENTS)),
                    t("Exam Preparation", "Preparación de exámenes") to go(PlannerPage(PlannerKind.EXAM)),
                    t("Group Project Planner", "Trabajo en grupo") to go(PlannerPage(PlannerKind.GROUP)),
                    t("Semester Overview", "Resumen del semestre") to go(PlannerPage(PlannerKind.SEMESTER_OVERVIEW)),
                    t("Notes", "Notas libres") to go(PlannerPage(PlannerKind.NOTES)),
                    t("Academic Planner", "Plan académico") to go(PlannerPage(PlannerKind.ACADEMIC)),
                ),
                y + 40f,
            )
            heading(t("NOTES", "NOTAS"), L, R, y + 12f)
            c.hline(L, R, 1340f, ink.accent)
        }

        // ---- Hubs and planning pages (no reference: same look, own layout) ---------------------------------------

        private fun tiles(items: List<Pair<String, PlannerAction?>>, top: Float, cols: Int, height: Float, gap: Float = 18f): Float {
            val width = (R - L - gap * (cols - 1)) / cols
            for ((i, item) in items.withIndex()) {
                val x = L + (i % cols) * (width + gap)
                val y = top + (i / cols) * (height + gap)
                roundBox(x, y, x + width, y + height, TILE_RADIUS, ink.rule)
                c.text(item.first, (x + x + width) / 2f, y + height / 2f, 19f, style = TextStyle.HEADING, align = TextAlign.CENTER)
                if (item.second != null) c.link(x, y, x + width, y + height, item.second!!)
            }
            return top + ((items.size + cols - 1) / cols) * (height + gap)
        }

        fun general() {
            title(t("General Planning", "Planificación general"))
            heading(t("PAGES", "PÁGINAS"), L, R, 172f)
            tiles(
                listOf(
                    t("Yearly Overview", "Resumen anual") to go(PlannerPage(PlannerKind.YEARLY_OVERVIEW)),
                    t("Routines Planner", "Rutinas") to go(PlannerPage(PlannerKind.ROUTINES)),
                    t("Meal Planner", "Comidas") to go(PlannerPage(PlannerKind.MEAL)),
                    t("To-Do List", "Lista de tareas") to go(PlannerPage(PlannerKind.TODO)),
                    t("Habit Tracker", "Hábitos") to go(PlannerPage(PlannerKind.HABIT)),
                    t("Goal Planner", "Metas") to go(PlannerPage(PlannerKind.GOALS)),
                    t("Blank", "En blanco") to go(PlannerPage(PlannerKind.BLANK)),
                    t("Dotted", "Punteada") to go(PlannerPage(PlannerKind.DOTTED)),
                    t("Lined", "Rayada") to go(PlannerPage(PlannerKind.LINED)),
                    t("Grid", "Cuadriculada") to go(PlannerPage(PlannerKind.GRID)),
                ),
                210f,
                2,
                160f,
            )
        }

        fun academic() {
            title(t("Academic Planner", "Plan académico"))
            heading(t("PAGES", "PÁGINAS"), L, R, 172f)
            var y = tiles(
                listOf(
                    t("Grade Tracker", "Notas") to go(PlannerPage(PlannerKind.GRADES)),
                    t("Study Session Planner", "Sesiones de estudio") to go(PlannerPage(PlannerKind.STUDY)),
                    t("Assignment Planner", "Trabajos prácticos") to go(PlannerPage(PlannerKind.ASSIGNMENTS)),
                    t("Exam Preparation", "Preparación de exámenes") to go(PlannerPage(PlannerKind.EXAM)),
                    t("Group Project Planner", "Trabajo en grupo") to go(PlannerPage(PlannerKind.GROUP)),
                    t("Semester Overview", "Resumen del semestre") to go(PlannerPage(PlannerKind.SEMESTER_OVERVIEW)),
                ),
                210f,
                2,
                110f,
            )
            heading(t("COURSES", "MATERIAS"), L, R, y + 14f)
            buttons((1..PlannerPage.COURSES).map { t("Course $it", "Materia $it") to go(PlannerPage.course(it)) }, y + 44f)
        }

        fun semesterPlan() {
            title(t("Semesters / Quarters", "Semestres / Trimestres"))
            val half = (R - L - 30f) / 2f
            for (i in 0 until 4) {
                val x = L + (i % 2) * (half + 30f)
                val y = 170f + (i / 2) * 590f
                c.text(t("SEMESTER / QUARTER ${i + 1}", "SEMESTRE / TRIMESTRE ${i + 1}"), x, y, 17f, style = TextStyle.HEADING)
                c.hline(x, x + half, y + 18f, ink.accent)
                c.text(t("Start Date:", "Fecha de inicio:"), x, y + 52f, 15f)
                c.hline(x + 110f, x + half / 2f - 10f, y + 62f, ink.rule)
                c.text(t("End Date:", "Fecha de fin:"), x + half / 2f + 10f, y + 52f, 15f)
                c.hline(x + half / 2f + 100f, x + half, y + 62f, ink.rule)
                semesterDate(i * 2)?.let { c.text(it, x + 116f, y + 52f, 15f) }
                semesterDate(i * 2 + 1)?.let { c.text(it, x + half / 2f + 106f, y + 52f, 15f) }
                val inkY = y + 62f - DateStroke.SLOT_HEIGHT
                writing(i * 2, x + 116f, inkY)
                writing(i * 2 + 1, x + half / 2f + 106f, inkY)
                c.link(x, y + 30f, x + half / 2f - 10f, y + 70f, PlannerAction.PickDate(i, false, x + 116f, inkY))
                c.link(x + half / 2f + 10f, y + 30f, x + half, y + 70f, PlannerAction.PickDate(i, true, x + half / 2f + 106f, inkY))
                c.text(t("COURSES", "MATERIAS"), x, y + 112f, 15f, style = TextStyle.HEADING)
                rows(x, x + half, y + 126f, 6, 36f)
                c.text(t("GOALS", "METAS"), x, y + 388f, 15f, style = TextStyle.HEADING)
                rows(x, x + half, y + 402f, 4, 36f, numbered = true)
            }
        }

        fun semesterOverview() {
            c.text(t("Quarter/Semester Overview", "Resumen del semestre"), L, TOP_TITLE, 34f, style = TextStyle.TITLE)
            c.text(t("Start Date:", "Inicio:"), 560f, TOP_TITLE, 15f)
            c.text(t("End Date:", "Fin:"), 730f, TOP_TITLE, 15f)
            c.hline(560f, R, TOP_TITLE + 14f, ink.accent)
            val left = L + 40f
            val top = 190f
            val head = 220f
            val hours = 15
            val bottom = 900f
            val dayW = (R - left) / 7f
            val step = (bottom - head) / hours
            for (d in 0 until 7) c.text(weekdaysShort[d], left + dayW * (d + 0.5f), (top + head) / 2f, 15f, style = TextStyle.HEADING, align = TextAlign.CENTER)
            for (h in 0 until hours) {
                val y = head + step * h
                c.text(((h + 7) % 12 + 1).toString(), L + 14f, y + step / 2f, 14f, ink.faint, align = TextAlign.CENTER)
                c.hline(left, R, y, ink.rule, 1f)
            }
            for (d in 1 until 7) c.vline(left + dayW * d, head, bottom, ink.rule, 1f)
            box(left, head, R, bottom)
            heading(t("MY COURSES:", "MIS MATERIAS:"), L, R, 944f)
            val gap = 24f
            val w = (R - L - gap) / 2f
            for (i in 0 until PlannerPage.COURSES) {
                val x = L + (i % 2) * (w + gap)
                val y = 985f + (i / 2) * 58f
                c.box(x, y, x + w, y + 50f, ink.rule, 1.5f)
                c.hline(x, x + w, y + 25f, ink.rule, 1.5f)
                c.text(t("Course ${i + 1}:", "Materia ${i + 1}:"), x + 8f, y + 12f, 14f)
                c.text(t("Location:", "Aula:"), x + 8f, y + 38f, 14f)
                c.text(t("view course info", "ver materia"), x + w - 100f, y + 38f, 12f, ink.faint)
                c.link(x, y, x + w, y + 50f, go(PlannerPage.course(i + 1)))
            }
        }

        fun course(n: Int) {
            title(t("Course $n Overview", "Materia $n"))
            c.link(L, 80f, 500f, 135f, go(PlannerPage(PlannerKind.SEMESTER_OVERVIEW)))
            val lw = 440f
            heading(t("DETAILS", "DETALLES"), L, L + lw, 172f)
            labelRows(L, L + lw, 190f, listOf(t("Course:", "Materia:"), t("Instructor:", "Docente:"), t("Time:", "Horario:"), t("Location:", "Aula:"), t("Office hours:", "Consultas:"), t("Contact:", "Contacto:")), 40f)
            heading(t("TEXTBOOKS & MATERIALS", "LIBROS Y MATERIALES"), L, L + lw, 500f)
            rows(L, L + lw, 520f, 8, 38f, divX = L + lw - 60f)
            heading(t("IMPORTANT DATES", "FECHAS IMPORTANTES"), L, L + lw, 870f)
            rows(L, L + lw, 890f, 11, 38f, slashW = 54f)
            val x = L + lw + 30f
            c.text(t("DUE", "ENTREGA"), x + 4f, 172f, 16f, style = TextStyle.HEADING)
            c.text(t("ASSIGNMENT", "TRABAJO"), x + 190f, 172f, 16f, style = TextStyle.HEADING)
            rows(x, R, 190f, 18, 38f, slashW = 70f)
            heading(t("NOTES", "NOTAS"), x, R, 905f)
            box(x, 930f, R, 1340f)
        }

        fun exam() {
            title(t("Exam Preparation", "Preparación de examen"))
            val mid = 455f
            heading(t("DETAILS", "DETALLES"), L, mid, 172f)
            labelRows(L, mid, 190f, listOf(t("Course:", "Materia:"), t("Exam:", "Examen:"), t("Date:", "Fecha:"), t("Location:", "Lugar:")), 40f)
            heading(t("WAYS TO PRACTICE", "CÓMO PRACTICAR"), L, mid, 400f)
            box(L, 425f, mid, 690f)
            heading(t("STUDY LOG", "REGISTRO DE ESTUDIO"), L, mid, 740f)
            c.fill(L, 760f, mid, 795f, ink.tint)
            c.text(t("Topic Studied", "Tema estudiado"), L + 8f, 777f, 14f)
            c.text(t("Date", "Fecha"), mid - 70f, 777f, 14f)
            rows(L, mid, 795f, 14, 38f, divX = mid - 90f)
            val x = mid + 30f
            heading(t("TOPICS TO BE COVERED", "TEMAS A ESTUDIAR"), x, R, 172f)
            rows(x, R, 190f, 13, 38f, divX = R - 60f)
            heading(t("BREAKDOWN", "BALANCE"), x, R, 740f)
            val labels = listOf(t("Topics I'm Confident On", "Temas que domino"), t("Topics I'm Unclear About", "Temas que dudo"), t("Topics I Don't Understand", "Temas que no entiendo"))
            for (i in 0 until 3) {
                val y = 760f + i * 195f
                box(x, y, R, y + 195f)
                c.text(labels[i], x + 10f, y + 22f, 14f)
            }
        }

        fun group() {
            title(t("Group Project Planner", "Trabajo en grupo"))
            val mid = 360f
            heading(t("DETAILS", "DETALLES"), L, mid, 172f)
            labelRows(L, mid, 190f, listOf(t("Project:", "Proyecto:"), "", t("Due:", "Entrega:"), t("Course:", "Materia:")), 40f)
            heading(t("GROUP MEMBERS", "INTEGRANTES"), L, mid, 400f)
            for (i in 0 until 6) {
                val y = 425f + i * 150f
                c.box(L, y, mid, y + 128f, ink.rule, 1.5f)
                c.hline(L, mid, y + 42f, ink.rule, 1.5f)
                c.hline(L, mid, y + 86f, ink.rule, 1.5f)
                c.text(t("Name:", "Nombre:"), L + 10f, y + 21f, 14f)
                c.text(t("Contact:", "Contacto:"), L + 10f, y + 64f, 14f)
            }
            val x = mid + 30f
            c.text(t("TASK", "TAREA"), x, 172f, 16f, style = TextStyle.HEADING)
            c.text(t("MEMBER", "RESPONSABLE"), 600f, 172f, 16f, style = TextStyle.HEADING)
            c.text(t("DUE", "ENTREGA"), 770f, 172f, 16f, style = TextStyle.HEADING)
            rows(x, R, 190f, 17, 38f, divX = 595f)
            c.vline(745f, 190f, 190f + 17 * 38f, ink.rule)
            for (i in 0 until 17) c.text("/", 790f, 190f + 38f * i + 19f, 17f, ink.faint, align = TextAlign.CENTER)
            heading(t("NOTES", "NOTAS"), x, R, 880f)
            c.hline(x, R, 1340f, ink.accent)
        }

        fun yearlyOverview() {
            title(t("Yearly Overview", "Resumen anual"))
            rightText(setup.yearLabel, R, TOP_TITLE, 26f, TextStyle.TITLE)
            val colW = 262f
            for (i in 0 until minOf(12, setup.monthCount)) {
                val x = L + (i % 3) * (colW + 17f)
                val y = 170f + (i / 3) * 290f
                val date = setup.monthStart(i)
                c.text(monthName(date).uppercase(), x, y, 15f, style = TextStyle.HEADING)
                c.hline(x, x + colW, y + 18f, ink.accent)
                c.link(x, y - 12f, x + colW, y + 20f, go(PlannerPage.month(i)))
                for (r in 0 until 7) c.hline(x, x + colW, y + 18f + 34f * (r + 1), if (r == 6) ink.accent else ink.rule)
            }
        }

        fun meal() {
            title(t("Meal Planner", "Comidas"))
            c.text(t("Week of:", "Semana del:"), 600f, TOP_TITLE - 4f, 14f)
            c.hline(600f, R, TOP_TITLE + 10f, ink.accent)
            val left = L + 40f
            val top = 160f
            val bottom = 700f
            val dayW = (R - left) / 7f
            for (d in 0 until 7) c.text(weekdaysShort[d], left + dayW * (d + 0.5f), top - 14f, 14f, style = TextStyle.HEADING, align = TextAlign.CENTER)
            val meals = listOf(t("BREAKFAST", "DESAYUNO"), t("LUNCH", "ALMUERZO"), t("DINNER", "CENA"), t("SNACKS", "MERIENDA"))
            val rowH = (bottom - top) / 4f
            for ((i, name) in meals.withIndex()) {
                c.turnedText(name, L + 16f, top + rowH * (i + 0.5f), 13f, -90f, style = TextStyle.HEADING)
                if (i > 0) c.hline(left, R, top + rowH * i, ink.rule)
            }
            for (d in 1 until 7) c.vline(left + dayW * d, top, bottom, ink.rule)
            box(left, top, R, bottom)
            c.text(t("SHOPPING LIST", "LISTA DE COMPRAS"), L, 740f, 16f, style = TextStyle.HEADING)
            c.hline(L + 170f, R, 740f, ink.accent)
            val colW = (R - L - 40f) / 3f
            for (i in 0 until 3) {
                val x = L + i * (colW + 20f)
                rows(x, x + colW, 775f, 15, 37f, divX = x + colW - 40f)
            }
        }

        fun routines() {
            title(t("Routines Planner", "Rutinas"))
            val mid = 440f
            c.text(t("WEEKLY", "SEMANAL"), L + 40f, 160f, 15f, style = TextStyle.HEADING)
            val left = L + 40f
            val top = 182f
            val bottom = 1340f
            val dayH = (bottom - top) / 7f
            for (d in 0 until 7) {
                c.turnedText(weekdays[d].uppercase(), L + 16f, top + dayH * (d + 0.5f), 13f, -90f, style = TextStyle.HEADING)
                if (d > 0) c.hline(left, mid, top + dayH * d, ink.rule)
            }
            box(left, top, mid, bottom)
            val x = mid + 30f
            val sections = listOf(t("MORNING", "MAÑANA"), t("AFTERNOON", "TARDE"), t("EVENING", "NOCHE"))
            for ((i, name) in sections.withIndex()) {
                val y = 160f + i * 395f
                c.text(name, x, y, 15f, style = TextStyle.HEADING)
                rows(x, R, y + 20f, 8, 43f, divX = x + 60f)
                for (r in 0 until 8) c.text(":", x + 30f, y + 20f + 43f * r + 21f, 17f, ink.faint, align = TextAlign.CENTER)
            }
        }

        fun habit() {
            title(t("Habit Tracker", "Hábitos"))
            c.text(t("Month of:", "Mes de:"), 640f, TOP_TITLE - 4f, 14f)
            c.hline(640f, R, TOP_TITLE + 10f, ink.accent)
            val rows = 18
            val top = 160f
            val rowH = 64f
            val nameW = 270f
            val cellsX = L + nameW
            val cellW = (R - cellsX) / 16f
            for (r in 0 until rows) {
                val y = top + rowH * r
                c.box(L, y, cellsX, y + rowH, ink.accent, 1.2f)
                c.box(cellsX, y, R, y + rowH, ink.accent, 1.2f)
                c.hline(cellsX, R, y + rowH / 2f, ink.rule, 1f)
                for (k in 0 until 16) {
                    val x = cellsX + cellW * (k + 0.5f)
                    c.text("${k + 1}", x, y + rowH / 4f, 11f, ink.faint, align = TextAlign.CENTER)
                    if (k < 15) c.text("${k + 17}", x, y + rowH * 0.75f, 11f, ink.faint, align = TextAlign.CENTER)
                }
            }
        }

        fun goals() {
            title(t("Goal Planner", "Metas"))
            heading(t("MY GOAL", "MI META"), L, R, 172f)
            rows(L, R, 190f, 2, 44f)
            heading(t("WHY IT MATTERS", "POR QUÉ IMPORTA"), L, 480f, 330f)
            rows(L, 480f, 348f, 4, 40f)
            heading(t("TARGET DATE", "FECHA OBJETIVO"), 510f, R, 330f)
            rows(510f, R, 348f, 1, 40f, slashW = 60f)
            c.text(t("PROGRESS", "PROGRESO"), 510f, 440f, 15f, style = TextStyle.HEADING)
            for (i in 0 until 10) c.box(510f + i * 38f, 462f, 510f + i * 38f + 32f, 494f, ink.accent, 1.2f)
            heading(t("ACTION STEPS", "PASOS"), L, R, 560f)
            c.hline(L, R, 580f, ink.accent)
            for (i in 0 until 9) {
                val y = 580f + 46f * (i + 1)
                c.box(L + 8f, y - 33f, L + 28f, y - 13f, ink.rule, 1.2f)
                c.text("/", R - 40f, y - 23f, 17f, ink.faint, align = TextAlign.CENTER)
                c.hline(L, R, y, ink.rule)
            }
            c.vline(R - 90f, 580f, 580f + 46f * 9, ink.rule)
            heading(t("MILESTONES", "HITOS"), L, 480f, 1040f)
            rows(L, 480f, 1058f, 6, 38f)
            heading(t("NOTES", "NOTAS"), 510f, R, 1040f)
            box(510f, 1062f, R, 1340f)
        }

        fun todo() {
            title(t("To-Do List", "Lista de tareas"))
            c.text(t("Date:", "Fecha:"), 640f, TOP_TITLE - 4f, 14f)
            c.hline(640f, R, TOP_TITLE + 10f, ink.accent)
            heading(t("TOP PRIORITIES", "PRIORIDADES"), L, R, 172f)
            rows(L, R, 190f, 3, 40f, slashW = 0f, numbered = true)
            heading(t("TASKS", "TAREAS"), L, R, 350f)
            c.hline(L, R, 370f, ink.accent)
            val n = 21
            for (i in 0 until n) {
                val y = 370f + 44f * (i + 1)
                c.box(L + 8f, y - 33f, L + 28f, y - 13f, ink.rule, 1.2f)
                c.hline(L, R, y, if (i == n - 1) ink.accent else ink.rule)
            }
            c.vline(R - 90f, 370f, 370f + 44f * n, ink.rule)
            c.text(t("DUE", "FECHA"), R - 45f, 350f, 13f, style = TextStyle.HEADING, align = TextAlign.CENTER)
        }

        private fun table(columns: List<Pair<String, Float>>, top: Float, n: Int, step: Float, checkboxes: Boolean = false) {
            var x = L
            val widthTotal = columns.sumOf { it.second.toDouble() }.toFloat()
            val scale = (R - L) / widthTotal
            c.fill(L, top, R, top + 36f, ink.tint)
            for ((name, w) in columns) {
                c.text(name, x + 6f, top + 18f, 13f, style = TextStyle.HEADING)
                x += w * scale
                if (x < R - 1f) c.vline(x, top, top + 36f + step * n, ink.rule)
            }
            c.hline(L, R, top, ink.accent)
            for (i in 0 until n) {
                val y = top + 36f + step * (i + 1)
                c.hline(L, R, y, if (i == n - 1) ink.accent else ink.rule)
            }
            if (checkboxes) {
                val x0 = R - columns.last().second * scale
                for (i in 0 until n) {
                    val y = top + 36f + step * i + step / 2f
                    c.box(x0 + 10f, y - 9f, x0 + 28f, y + 9f, ink.rule, 1.2f)
                }
            }
        }

        fun grades() {
            title(t("Grade Tracker", "Notas"))
            c.text(t("Course:", "Materia:"), L, 150f, 15f)
            c.hline(L + 70f, 480f, 160f, ink.accent)
            c.text(t("Semester:", "Semestre:"), 520f, 150f, 15f)
            c.hline(600f, R, 160f, ink.accent)
            table(
                listOf(
                    t("ASSIGNMENT / EXAM", "TRABAJO / EXAMEN") to 300f,
                    t("DATE", "FECHA") to 110f,
                    t("WEIGHT %", "PESO %") to 110f,
                    t("GRADE", "NOTA") to 110f,
                    t("NOTES", "NOTAS") to 190f,
                ),
                190f,
                23,
                44f,
            )
            c.text(t("CURRENT GRADE", "NOTA ACTUAL"), L, 1300f, 16f, style = TextStyle.HEADING)
            c.hline(L + 190f, 420f, 1310f, ink.accent)
            c.text(t("TARGET GRADE", "NOTA OBJETIVO"), 500f, 1300f, 16f, style = TextStyle.HEADING)
            c.hline(500f + 190f, R, 1310f, ink.accent)
        }

        fun study() {
            title(t("Study Session Planner", "Sesiones de estudio"))
            c.text(t("Week of:", "Semana del:"), 600f, TOP_TITLE - 4f, 14f)
            c.hline(600f, R, TOP_TITLE + 10f, ink.accent)
            table(
                listOf(
                    t("DATE", "FECHA") to 100f,
                    t("COURSE", "MATERIA") to 150f,
                    t("TOPIC", "TEMA") to 250f,
                    t("START", "INICIO") to 80f,
                    t("END", "FIN") to 80f,
                    t("GOAL", "OBJETIVO") to 200f,
                    t("DONE", "LISTO") to 80f,
                ),
                160f,
                19,
                46f,
                checkboxes = true,
            )
            heading(t("HOW DID IT GO?", "¿CÓMO FUE?"), L, R, 1110f)
            box(L, 1135f, R, 1340f)
        }

        fun assignments() {
            title(t("Assignment Planner", "Trabajos prácticos"))
            table(
                listOf(
                    t("ASSIGNMENT", "TRABAJO") to 300f,
                    t("COURSE", "MATERIA") to 170f,
                    t("DUE", "ENTREGA") to 110f,
                    t("PRIORITY", "PRIORIDAD") to 110f,
                    t("DONE", "LISTO") to 80f,
                ),
                150f,
                25,
                46f,
                checkboxes = true,
            )
        }

        fun notes() {
            title(t("Notes", "Notas"))
            c.text(t("Topic:", "Tema:"), L, 160f, 15f)
            c.hline(L + 60f, 520f, 170f, ink.accent)
            c.text(t("Date:", "Fecha:"), 560f, 160f, 15f)
            c.hline(620f, R, 170f, ink.accent)
            val cue = 250f
            c.text(t("CUES", "IDEAS CLAVE"), L, 220f, 15f, style = TextStyle.HEADING)
            c.text(t("NOTES", "NOTAS"), cue + 20f, 220f, 15f, style = TextStyle.HEADING)
            c.hline(L, R, 240f, ink.accent)
            c.vline(cue, 240f, 1250f, ink.rule)
            var y = 240f + 40f
            while (y < 1250f) {
                c.hline(cue, R, y, ink.rule, 1f)
                y += 40f
            }
            heading(t("SUMMARY", "RESUMEN"), L, R, 1275f)
            c.hline(L, R, 1345f, ink.accent)
        }

        fun pattern(template: PageTemplate) {
            val width = size.widthPt * (XE - 6f) / W
            template.draw(size, Bounds(0f, 0f, width, size.heightPt), sink, paper = paper)
        }

        // ---- Calendar pages ------------------------------------------------------------------------------------

        fun yearCalendar() {
            title(t("Yearly Calendar", "Calendario anual"))
            rightText(setup.yearLabel, R, TOP_TITLE, 28f, TextStyle.TITLE)
            val colW = 262f
            val count = minOf(12, setup.monthCount)
            for (i in 0 until count) {
                val x = L + (i % 3) * (colW + 17f)
                val y = 170f + (i / 3) * 290f
                val first = setup.monthStart(i)
                c.text(monthName(first).uppercase(), x, y, 14f, style = TextStyle.HEADING)
                c.hline(x, x + colW, y + 17f, ink.accent)
                c.link(x, y - 12f, x + colW, y + 19f, go(PlannerPage.month(i)))
                val wkW = 30f
                val dayW = (colW - wkW) / 7f
                c.text(t("WK", "SM"), x + 10f, y + 40f, 11f, style = TextStyle.HEADING)
                for (d in 0 until 7) c.text(initials[d], x + wkW + dayW * (d + 0.5f), y + 40f, 11f, style = TextStyle.HEADING, align = TextAlign.CENTER)
                var monday = setup.mondayOf(first)
                var row = 0
                val last = first.withDayOfMonth(first.lengthOfMonth())
                while (!monday.isAfter(last)) {
                    val ry = y + 66f + row * 33f
                    val week = setup.weekIndexOf(monday)
                    c.text("${week + 1}", x + 10f, ry, 12f, style = TextStyle.HEADING)
                    if (week in 0 until setup.weekCount) c.link(x, ry - 14f, x + wkW, ry + 14f, go(PlannerPage.week(week)))
                    for (d in 0 until 7) {
                        val date = monday.plusDays(d.toLong())
                        if (date.monthValue != first.monthValue || date.year != first.year) continue
                        val cx = x + wkW + dayW * (d + 0.5f)
                        c.text("${date.dayOfMonth}", cx, ry, 12f, align = TextAlign.CENTER)
                        c.link(cx - dayW / 2f, ry - 15f, cx + dayW / 2f, ry + 15f, go(PlannerPage.day(setup.dayIndex(date))))
                    }
                    monday = monday.plusDays(7)
                    row++
                }
            }
        }

        fun month(index: Int) {
            val first = setup.monthStart(index)
            title("${two(index + 1)} | ${monthName(first)}")
            rightText("${first.year}", R, TOP_TITLE, 30f, TextStyle.TITLE)
            val left = L + 30f
            val top = 175f
            val dayW = (R - left) / 7f
            for (d in 0 until 7) c.text(weekdaysShort[d], left + dayW * (d + 0.5f), 155f, 14f, style = TextStyle.HEADING, align = TextAlign.CENTER)
            val last = first.withDayOfMonth(first.lengthOfMonth())
            val rowsCount = (((setup.mondayOf(last).toEpochDay() - setup.mondayOf(first).toEpochDay()) / 7).toInt()) + 1
            val rowH = minOf(150f, 760f / rowsCount)
            var monday = setup.mondayOf(first)
            for (row in 0 until rowsCount) {
                val y = top + rowH * row
                val week = setup.weekIndexOf(monday)
                c.fill(L, y, left, y + 24f, ink.shade)
                c.text("${week + 1}", L + 15f, y + 12f, 12f, ink.faint, align = TextAlign.CENTER)
                if (week in 0 until setup.weekCount) c.link(L, y, left, y + 24f, go(PlannerPage.week(week)))
                for (d in 0 until 7) {
                    val date = monday.plusDays(d.toLong())
                    val x = left + dayW * d
                    val inside = date.monthValue == first.monthValue && date.year == first.year
                    if (!inside) {
                        c.fill(x, y, x + dayW, y + rowH, ink.shade)
                    } else {
                        c.text("${date.dayOfMonth}", x + dayW - 14f, y + 16f, 14f, align = TextAlign.CENTER)
                        c.link(x, y, x + dayW, y + rowH, go(PlannerPage.day(setup.dayIndex(date))))
                    }
                }
                if (row > 0) c.hline(left, R, y, ink.rule)
                monday = monday.plusDays(7)
            }
            for (d in 1 until 7) c.vline(left + dayW * d, top, top + rowH * rowsCount, ink.rule)
            box(left, top, R, top + rowH * rowsCount)
            val y = top + rowH * rowsCount + 50f
            val half = (R - L - 30f) / 2f
            c.text(t("STUDENT TASKS", "TAREAS DE ESTUDIO"), L, y, 15f, style = TextStyle.HEADING)
            rows(L, L + half, y + 14f, 7, 36f, divX = L + half - 40f)
            c.text(t("PERSONAL TASKS", "TAREAS PERSONALES"), L + half + 30f, y, 15f, style = TextStyle.HEADING)
            rows(L + half + 30f, R, y + 14f, 7, 36f, divX = R - 40f)
        }

        /** The right-hand column shared by the week and day pages. */
        private fun sideColumn(x: Float, top: Float, bottom: Float) {
            heading(t("TOP PRIORITIES", "PRIORIDADES"), x, R, top)
            rows(x, R, top + 18f, 3, 34f, divX = x + 36f, numbered = true)
            var y = top + 18f + 3 * 34f + 44f
            heading(t("PERSONAL TASKS", "TAREAS PERSONALES"), x, R, y)
            rows(x, R, y + 18f, 7, 34f, divX = R - 44f)
            y += 18f + 7 * 34f + 44f
            heading(t("STUDENT TASKS", "TAREAS DE ESTUDIO"), x, R, y)
            rows(x, R, y + 18f, 7, 34f, divX = R - 44f)
            y += 18f + 7 * 34f + 44f
            heading(t("NOTES", "NOTAS"), x, R, y)
            c.hline(x, R, bottom, ink.accent)
        }

        fun week(index: Int) {
            val monday = setup.weekMonday(index)
            val sunday = monday.plusDays(6)
            title(t("Week ${index + 1}", "Semana ${index + 1}"))
            val range = if (es) {
                "${monday.dayOfMonth} ${monthsShort[monday.monthValue - 1]} - ${sunday.dayOfMonth} ${monthsShort[sunday.monthValue - 1]}"
            } else {
                "${monthName(monday).uppercase()} ${monday.dayOfMonth} - ${monthName(sunday).uppercase()} ${sunday.dayOfMonth}"
            }
            rightText(range, R, TOP_TITLE - 4f, 15f)
            val x1 = 470f
            val top = 150f
            val bottom = 1340f
            val dayH = (bottom - top) / 7f
            for (d in 0 until 7) {
                val date = monday.plusDays(d.toLong())
                val y = top + dayH * d
                if (d > 0) c.hline(L, x1, y, ink.rule)
                c.text("${date.dayOfMonth} ${weekdays[d]}", L + 10f, y + 20f, 15f)
                if (setup.contains(date)) c.link(L, y, x1, y + dayH, go(PlannerPage.day(setup.dayIndex(date))))
            }
            box(L, top, x1, bottom)
            sideColumn(x1 + 30f, 150f, bottom)
        }

        fun day(index: Int) {
            val date = setup.day(index)
            val dayName = weekdays[setup.weekday(date)]
            val titleSize = 34f
            c.text("$dayName, ${longDate(date)}", L, TOP_TITLE, titleSize, style = TextStyle.TITLE)
            // The width of the weekday is estimated, which is enough for an underline and a tap area.
            val nameWidth = dayName.length * titleSize * 0.5f
            // The weekday is a menu: picking another one moves every date of the planner.
            c.hline(L, L + nameWidth, TOP_TITLE + 22f, ink.accent, 1.5f)
            c.link(L - 4f, TOP_TITLE - 30f, L + nameWidth + 8f, TOP_TITLE + 30f, PlannerAction.PickWeekday(index))
            val week = setup.weekIndexOf(date)
            button(740f, 88f, R, 128f, t("VIEW WEEK", "VER SEMANA"), go(PlannerPage.week(week)), 14f)
            val x1 = 470f
            heading(t("SCHEDULE", "HORARIO"), L, x1, 172f)
            val top = 190f
            val bottom = 1340f
            val hours = 19
            val step = (bottom - top) / hours
            c.fill(L, top, L + 46f, bottom, ink.shade)
            for (h in 0 until hours) {
                val y = top + step * h
                c.text(two((h + 4) % 12 + 1), L + 23f, y + step / 2f, 14f, ink.faint, align = TextAlign.CENTER)
                if (h > 0) c.hline(L, x1, y, ink.rule)
                c.hline(L + 46f, x1, y + step / 2f, ink.rule, 1f)
            }
            c.hline(L, x1, bottom, ink.accent)
            sideColumn(x1 + 30f, 172f, bottom)
        }
    }
}

/** The tabs of a planner, from the top: Index, Year, then a month each. */
internal fun plannerTabs(setup: PlannerSetup): List<PlannerTab> = buildList {
    add(PlannerTab(PlannerTab.Kind.INDEX))
    add(PlannerTab(PlannerTab.Kind.YEAR))
    for (m in 0 until setup.monthCount) add(PlannerTab(PlannerTab.Kind.MONTH, m))
}

/** The tab a page sits under: its month, the Year tab for the calendar, and the Index tab for everything before the months. */
internal fun plannerTabOf(setup: PlannerSetup, page: PlannerPage): Int = when (page.kind) {
    PlannerKind.DASHBOARD -> 0
    PlannerKind.YEAR_CALENDAR -> 1
    PlannerKind.MONTH -> 2 + page.arg
    PlannerKind.WEEK -> 2 + setup.monthOfWeek(page.arg)
    PlannerKind.DAY -> 2 + setup.monthIndexOf(setup.day(page.arg))
    // The planning pages come from the index, so the page flows into the Index tab.
    else -> 0
}
