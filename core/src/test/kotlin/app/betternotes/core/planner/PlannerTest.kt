package app.betternotes.core.planner

import app.betternotes.core.edit.EditTarget
import app.betternotes.core.format.DocumentCodec
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.PageContent
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import app.betternotes.core.template.draw
import app.betternotes.core.geometry.Bounds
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private val july = PlannerSetup(2026, 7)
    private val size = PageSize.A4

    private class Counting(override val language: String) : TemplateSink {
        var marks = 0
        var words = 0
        override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) { marks++ }
        override fun dot(x: Float, y: Float, radius: Float, color: Int) { marks++ }
        override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) { marks++ }
        override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) { words++ }
    }

    private fun ids(): IdSource {
        var n = 0L
        return IdSource { ++n }
    }

    @Test
    fun `a mid-year planner starts on 1 July 2026, a Wednesday, and has 13 months`() {
        assertEquals(LocalDate.of(2026, 7, 1), july.firstDay)
        assertEquals(2, july.weekday(july.firstDay))
        assertEquals(13, july.monthCount)
        assertEquals(LocalDate.of(2027, 7, 31), july.lastDay)
        assertEquals("2026 - 2027", july.yearLabel)
        // Week 1 is 29 June to 5 July, as in the reference.
        assertEquals(LocalDate.of(2026, 6, 29), july.weekMonday(0))
    }

    @Test
    fun `a calendar year has 12 months`() {
        val year = PlannerSetup(2027, 1)
        assertEquals(12, year.monthCount)
        assertEquals(365, year.dayCount)
        assertEquals("2027", year.yearLabel)
        assertEquals(LocalDate.of(2027, 12, 31), year.lastDay)
    }

    @Test
    fun `setting the weekday of a date moves every date with it`() {
        val moved = july.withWeekday(LocalDate.of(2026, 7, 1), 0)
        assertEquals(0, moved.weekday(LocalDate.of(2026, 7, 1)))
        assertEquals(1, moved.weekday(LocalDate.of(2026, 7, 2)))
        assertEquals(6, moved.weekday(LocalDate.of(2026, 6, 30)))
        assertEquals(july, moved.withWeekday(LocalDate.of(2026, 7, 1), 2))
    }

    @Test
    fun `every week is filed under one month and every day under its own`() {
        val plan = PlannerPlan(july)
        assertEquals(PlannerPlan.FIXED.size + 13 + july.weekCount + july.dayCount, plan.size)
        assertEquals(plan.size, plan.pages.toSet().size)
        for (w in 0 until july.weekCount) assertTrue(plan.indexOf(PlannerPage.week(w)) >= 0)
        for (d in 0 until july.dayCount) assertTrue(plan.indexOf(PlannerPage.day(d)) >= 0)
        // The first tab pages: Index, Year, then July's month page after all the planning pages.
        assertEquals(0, plan.indexOf(PlannerPage.Dashboard))
        assertEquals(1, plan.indexOf(PlannerPage.YearCalendar))
        assertEquals(PlannerPlan.FIXED.size, plan.indexOf(PlannerPage.month(0)))
        assertEquals(plan.indexOf(PlannerPage.month(0)) + 1, plan.indexOf(PlannerPage.week(0)))
        assertEquals(15, plan.tabs.size)
        assertEquals(2, plan.tabOf(PlannerPage.day(0)))
        assertEquals(2 + 12, plan.tabOf(PlannerPage.day(july.dayCount - 1)))
        assertEquals(0, plan.tabOf(PlannerPage.course(3)))
    }

    @Test
    fun `every page draws in both languages, with words`() {
        val plan = PlannerPlan(july)
        for (language in listOf("en", "es")) {
            for (page in listOf(PlannerPage.Cover) + plan.pages.filter { it.arg < 2 || it.kind == PlannerKind.COURSE }) {
                val sink = Counting(language)
                PageTemplate.Planner(page, july).draw(size, Bounds(0f, 0f, size.widthPt, size.heightPt), sink)
                if (page.kind != PlannerKind.BLANK) assertTrue("$page $language", sink.marks > 0)
            }
        }
    }

    @Test
    fun `tapping a dashboard month, a tab and a day link goes where it says`() {
        val sx = size.widthPt / 1000f
        val sy = size.heightPt / 1414f
        // The "July" button: second in the first row of the dashboard.
        val month = plannerActionAt(PlannerPage.Dashboard, july, size, 300f * sx, 218f * sy)
        assertEquals(PlannerAction.Go(PlannerPage.month(0)), month)
        // The Year tab.
        assertEquals(PlannerAction.Go(PlannerPage.YearCalendar), plannerActionAt(PlannerPage.month(0), july, size, 950f * sx, 44f * sy + 64f * 1.5f * sy))
        // The weekday on a day page opens the weekday menu.
        assertEquals(PlannerAction.PickWeekday(0), plannerActionAt(PlannerPage.day(0), july, size, 100f * sx, 112f * sy))
        // Nothing in the middle of a blank page.
        assertEquals(null, plannerActionAt(PlannerPage(PlannerKind.BLANK), july, size, 300f * sx, 700f * sy))
    }

    @Test
    fun `the file keeps the planner and its dates`() {
        val document = Document.newPlanner("Agenda", july.withWeekday(LocalDate.of(2026, 7, 1), 4), 0, ids())
        val back = DocumentCodec.decode(DocumentCodec.encode(document))
        assertEquals(DocumentKind.PLANNER, back.kind)
        assertEquals(document.pages.size, back.pages.size)
        assertEquals(PlannerPages.setupOf(document), PlannerPages.setupOf(back))
        assertEquals(document.pages.map { it.background.template }, back.pages.map { it.background.template })
        assertTrue(back.pages.first().isCover)
        assertEquals("planner", back.coverId)
        assertTrue(!back.canAddPages)
        assertTrue(!back.canRemovePages)
    }

    @Test
    fun `the cover title is kept in the file and survives a change of years`() {
        val document = Document.newPlanner("Agenda", july, 0, ids())
        val retitle = PlannerPages.retitle(document, "  Mi agenda 2027  ")
        assertNotNull(retitle)
        val target = object : EditTarget {
            override val document = document
            override fun content(pageId: Long) = PageContent()
        }
        retitle!!.apply(target)
        assertEquals("Mi agenda 2027", PlannerPages.titleOf(document))
        val back = DocumentCodec.decode(DocumentCodec.encode(document))
        assertEquals("Mi agenda 2027", PlannerPages.titleOf(back))
        assertEquals("", ((back.pages[1].background.template) as PageTemplate.Planner).title)
        PlannerPages.change(document, PlannerSetup(2027, 1), ids())!!.apply(target)
        assertEquals("Mi agenda 2027", PlannerPages.titleOf(document))
        assertEquals(null, PlannerPages.retitle(document, "Mi agenda 2027"))
    }

    @Test
    fun `a semester date typed once shows on the index and the semester page, and is kept`() {
        val document = Document.newPlanner("Agenda", july, 0, ids())
        val target = object : EditTarget {
            override val document = document
            override fun content(pageId: Long) = PageContent()
        }
        val day = LocalDate.of(2026, 8, 3).toEpochDay().toInt()
        val edit = PlannerPages.setSemesterDate(document, 1, false, day)
        assertNotNull(edit)
        edit!!.apply(target)
        for (kind in listOf(PlannerKind.DASHBOARD, PlannerKind.SEMESTER_PLAN)) {
            val page = document.pages.first { (it.background.template as? PageTemplate.Planner)?.page?.kind == kind }
            assertEquals(day, (page.background.template as PageTemplate.Planner).semesters[2])
        }
        // The other pages carry none, and a change of years keeps the dates.
        assertTrue((document.pages[3].background.template as PageTemplate.Planner).semesters.isEmpty())
        val back = DocumentCodec.decode(DocumentCodec.encode(document))
        assertEquals(day, PlannerPages.semestersOf(back)[2])
        PlannerPages.change(document, PlannerSetup(2027, 1), ids())!!.apply(target)
        assertEquals(day, PlannerPages.semestersOf(document)[2])
        // Clearing the last date leaves the pages as they were before any date was typed.
        PlannerPages.setSemesterDate(document, 1, false, null)!!.apply(target)
        assertTrue((document.pages[1].background.template as PageTemplate.Planner).semesters.isEmpty())
    }

    @Test
    fun `tapping a date field of the index picks that date`() {
        val sx = size.widthPt / 1000f
        val sy = size.heightPt / 1414f
        val found = (0 until 1414 step 4).flatMap { y -> (0 until 900 step 6).map { x -> plannerActionAt(PlannerPage.Dashboard, july, size, x * sx, y * sy) } }
            .filterIsInstance<PlannerAction.PickDate>().toSet()
        assertEquals(8, found.size)
    }

    @Test
    fun `tapping the title of the cover edits it, and the years change the years`() {
        val sx = size.widthPt / 1000f
        val sy = size.heightPt / 1414f
        assertEquals(PlannerAction.EditTitle, plannerActionAt(PlannerPage.Cover, july, size, 500f * sx, 520f * sy))
        assertEquals(PlannerAction.PickYears, plannerActionAt(PlannerPage.Cover, july, size, 500f * sx, 660f * sy))
    }

    @Test
    fun `changing the years keeps the pages that are still there and can be undone`() {
        val document = Document.newPlanner("Agenda", july, 0, ids())
        val target = object : EditTarget {
            override val document = document
            override fun content(pageId: Long) = PageContent()
        }
        val before = document.pages.toList()
        val edit = PlannerPages.change(document, PlannerSetup(2027, 1), ids())
        assertNotNull(edit)
        edit!!.apply(target)
        assertEquals(PlannerSetup(2027, 1), PlannerPages.setupOf(document))
        assertEquals(PlannerPlan(PlannerSetup(2027, 1)).size + 1, document.pages.size)
        // The dashboard page is in both, so it keeps its id.
        assertEquals(before[1].id, document.pages[1].id)
        assertNotEquals(before.size, document.pages.size)
        edit.revert(target)
        assertEquals(before, document.pages.toList())
        assertEquals(null, PlannerPages.change(document, july))
    }
}
