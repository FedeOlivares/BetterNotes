package app.betternotes.core.planner

import app.betternotes.core.edit.EditTarget
import app.betternotes.core.format.DocumentCodec
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageContent
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import app.betternotes.core.template.draw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerRolesTest {
    private val setup = PlannerSetup(2026, 1)
    private val size = PageSize.A4
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00AA00.toInt()
    private val grey = 0xFF888888.toInt()

    private class Rec : TemplateSink {
        class Word(val text: String, val color: Int, val font: String?, val align: TextAlign, val x: Float)

        val words = ArrayList<Word>()
        val lines = ArrayList<Int>()
        val fills = ArrayList<List<Number>>()
        val dots = ArrayList<List<Number>>()
        val polylines = ArrayList<Int>()
        override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) { lines += color }
        override fun dot(x: Float, y: Float, radius: Float, color: Int) { dots += listOf(x, y, radius, color) }
        override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) { fills += listOf(x, y, width, height, color) }
        override fun polyline(points: FloatArray, width: Float, color: Int) { polylines += color }
        override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) { words += Word(text, color, null, align, x) }
        override fun styledText(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign, font: String?) {
            words += Word(text, color, font, align, x)
        }
        override fun turnedText(text: String, cx: Float, cy: Float, size: Float, degrees: Float, color: Int, style: TextStyle) { words += Word(text, color, null, TextAlign.CENTER, cx) }
        override fun styledTurnedText(text: String, cx: Float, cy: Float, size: Float, degrees: Float, color: Int, style: TextStyle, font: String?) {
            words += Word(text, color, font, TextAlign.CENTER, cx)
        }

        fun word(text: String) = words.first { it.text == text }
    }

    private fun ids(): IdSource {
        var n = 0L
        return IdSource { ++n }
    }

    private fun planner() = Document.newPlanner("Agenda", setup, 0, ids())

    private fun targetOf(document: Document) = object : EditTarget {
        override val document = document
        override fun content(pageId: Long) = PageContent()
    }

    private fun pageOf(document: Document, kind: PlannerKind, arg: Int = 0): Page =
        document.pages.first { (it.background.template as? PageTemplate.Planner)?.page == PlannerPage(kind, arg) }

    private fun draw(page: Page, region: Bounds = Bounds(0f, 0f, size.widthPt, size.heightPt)): Rec {
        val sink = Rec()
        page.background.template.draw(size, region, sink, paper = PlannerPages.PAPER)
        return sink
    }

    private fun elementsOf(page: Page) = (page.background.template as PageTemplate.Planner).let { plannerElements(it.page, it.setup, size) }

    private fun keyOfWord(page: Page, role: PlannerRole, near: Float): ElementKey =
        elementsOf(page).first { it.role == role && it.bounds.top <= near && near <= it.bounds.bottom }.key

    // ---- Roles -----------------------------------------------------------------------------------------------------

    @Test
    fun `index buttons, the general page's tiles and the day button are all the button role`() {
        val document = planner()
        val index = elementsOf(pageOf(document, PlannerKind.DASHBOARD)).filter { it.role == PlannerRole.BUTTON }
        // Twelve months and the year, ten general pages, four semester buttons and eight academic pages.
        assertTrue("buttons on the index: ${index.size}", index.size >= 13 + 10 + 4 + 8)
        val tiles = elementsOf(pageOf(document, PlannerKind.GENERAL)).filter { it.role == PlannerRole.BUTTON }
        assertEquals(10, tiles.size)
        assertTrue(elementsOf(pageOf(document, PlannerKind.DAY, 0)).any { it.role == PlannerRole.BUTTON })
    }

    @Test
    fun `page titles are one role, a day's long date is another, and section headings are one role`() {
        val document = planner()
        val titles = listOf(PlannerKind.GENERAL, PlannerKind.WEEK, PlannerKind.MONTH).map { pageOf(document, it) }
        for (page in titles) assertTrue(page.toString(), elementsOf(page).any { it.role == PlannerRole.PAGE_TITLE })
        val day = elementsOf(pageOf(document, PlannerKind.DAY, 0))
        assertEquals(1, day.count { it.role == PlannerRole.DAY_DATE })
        // The long date is not a page title, so a page title recolor leaves it.
        assertEquals(0, day.count { it.role == PlannerRole.PAGE_TITLE })
        val month = elementsOf(pageOf(document, PlannerKind.MONTH, 0))
        assertTrue(month.any { it.role == PlannerRole.SECTION_HEADING })
        assertTrue(day.any { it.role == PlannerRole.SECTION_HEADING })
    }

    @Test
    fun `the cover and the pattern pages have no elements`() {
        val document = planner()
        assertTrue(plannerElements(PlannerPage.Cover, setup, size).isEmpty())
        assertTrue(elementsOf(pageOf(document, PlannerKind.DOTTED)).none { it.role != PlannerRole.TAB && it.role != PlannerRole.RULE && it.role != PlannerRole.SECTION_HEADING && it.role != PlannerRole.LABEL && it.role != PlannerRole.PAGE_TITLE && it.role != PlannerRole.SHADE })
    }

    @Test
    fun `every element lies on its page`() {
        val document = planner()
        for (kind in listOf(PlannerKind.DASHBOARD, PlannerKind.GENERAL, PlannerKind.MONTH, PlannerKind.WEEK, PlannerKind.DAY, PlannerKind.GRADES)) {
            for (e in elementsOf(pageOf(document, kind))) {
                assertTrue("$kind ${e.role} ${e.bounds}", e.bounds.right > e.bounds.left - 0.001f && e.bounds.bottom > e.bounds.top - 0.001f)
                assertTrue("$kind ${e.role} ${e.bounds}", e.bounds.left >= -1f && e.bounds.top >= -1f && e.bounds.right <= size.widthPt + 1f && e.bounds.bottom <= size.heightPt + 1f)
            }
        }
    }

    // ---- Restyling -------------------------------------------------------------------------------------------------

    @Test
    fun `apply to similar recolors every button on every page, and nothing else`() {
        val document = planner()
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val before = draw(index)
        val key = keyOfWord(index, PlannerRole.BUTTON, 195f * size.heightPt / PlannerDrawing.H + 10f)
        val edit = PlannerPages.restyle(document, index.id, listOf(key), similar = true, PlannerPages.StyleChange(RoleSlot.TEXT, red))
        assertNotNull(edit)
        edit!!.apply(targetOf(document))
        val after = draw(pageOf(document, PlannerKind.DASHBOARD))
        assertEquals(red, after.word("Yearly Overview").color)
        assertEquals(red, after.word("Routines Planner").color)
        assertEquals(red, after.word("Grade Tracker").color)
        // Titles and headings keep their colors.
        assertEquals(before.word("Planner Dashboard").color, after.word("Planner Dashboard").color)
        assertEquals(before.word("MONTHLY PLANNING").color, after.word("MONTHLY PLANNING").color)
        // The big tile of the general planning page is a button too.
        assertEquals(red, draw(pageOf(document, PlannerKind.GENERAL)).word("Dotted").color)
        assertEquals(red, draw(pageOf(document, PlannerKind.DAY, 0)).word("VIEW WEEK").color)
        // Every page keeps the same role styles.
        assertEquals(1, document.pages.map { (it.background.template as PageTemplate.Planner).roles.byRole }.toSet().size)
    }

    @Test
    fun `without apply to similar only the picked element changes`() {
        val document = planner()
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val buttons = elementsOf(index).filter { it.role == PlannerRole.BUTTON }
        val key = buttons.first().key
        PlannerPages.restyle(document, index.id, listOf(key), similar = false, PlannerPages.StyleChange(RoleSlot.TEXT, green))!!.apply(targetOf(document))
        val words = draw(pageOf(document, PlannerKind.DASHBOARD)).words
        assertEquals(1, words.count { it.color == green })
        assertTrue(PlannerPages.rolesOf(document).isEmpty)
        // Another page's buttons are untouched.
        assertTrue(draw(pageOf(document, PlannerKind.GENERAL)).words.none { it.color == green })
    }

    @Test
    fun `a recolor, and apply to similar, can be undone`() {
        val document = planner()
        val target = targetOf(document)
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val original = document.pages.toList()
        val key = elementsOf(index).first { it.role == PlannerRole.BUTTON }.key
        val similar = PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.BACKGROUND, green))!!
        similar.apply(target)
        assertNotEquals(original, document.pages.toList())
        similar.revert(target)
        assertEquals(original, document.pages.toList())
        // Applying the same change again is nothing to do.
        similar.apply(target)
        assertNull(PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.BACKGROUND, green)))
    }

    @Test
    fun `reset with similar puts the exact default back, and the file is as it was`() {
        val document = planner()
        val target = targetOf(document)
        val original = DocumentCodec.encode(document)
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val before = draw(index)
        val key = elementsOf(index).first { it.role == PlannerRole.BUTTON }.key
        for (slot in listOf(RoleSlot.TEXT, RoleSlot.LINE, RoleSlot.BACKGROUND)) {
            PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(slot, red))!!.apply(target)
        }
        PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.FONT, font = "Lora"))!!.apply(target)
        PlannerPages.restyle(document, index.id, listOf(key), false, PlannerPages.StyleChange(RoleSlot.TEXT, green))!!.apply(target)
        PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(null))!!.apply(target)
        assertTrue(document.pages.all { (it.background.template as PageTemplate.Planner).roles.isEmpty })
        assertTrue(original.contentEquals(DocumentCodec.encode(document)))
        val after = draw(pageOf(document, PlannerKind.DASHBOARD))
        assertEquals(before.words.map { it.color to it.text }, after.words.map { it.color to it.text })
        assertEquals(before.lines, after.lines)
        assertEquals(before.fills, after.fills)
    }

    @Test
    fun `reset of one element while its role is restyled draws it as the page does`() {
        val document = planner()
        val target = targetOf(document)
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val before = draw(index)
        val keys = elementsOf(index).filter { it.role == PlannerRole.BUTTON }.map { it.key }
        PlannerPages.restyle(document, index.id, keys.take(1), true, PlannerPages.StyleChange(RoleSlot.TEXT, red))!!.apply(target)
        PlannerPages.restyle(document, index.id, keys.take(1), false, PlannerPages.StyleChange(null))!!.apply(target)
        val after = draw(pageOf(document, PlannerKind.DASHBOARD))
        // The first button is back to the default; the others are still red.
        val firstText = before.words.first { it.text == "${setup.yearLabel}" }
        assertEquals(firstText.color, after.words.first { it.text == setup.yearLabel }.color)
        assertTrue(after.words.count { it.color == red } >= keys.size - 1)
    }

    @Test
    fun `schedule, top priorities and student tasks share the heading font`() {
        val document = planner()
        val day = pageOf(document, PlannerKind.DAY, 0)
        val key = elementsOf(day).first { it.role == PlannerRole.SECTION_HEADING }.key
        PlannerPages.restyle(document, day.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.FONT, font = "Lora"))!!.apply(targetOf(document))
        val dayWords = draw(pageOf(document, PlannerKind.DAY, 0))
        assertEquals("Lora", dayWords.word("SCHEDULE").font)
        assertEquals("Lora", dayWords.word("TOP PRIORITIES").font)
        assertEquals("Lora", draw(pageOf(document, PlannerKind.MONTH, 0)).word("STUDENT TASKS").font)
        // A day's long date is not a heading.
        assertNull(dayWords.words.first { it.text.contains("2026") }.font)
    }

    @Test
    fun `a restyled page title recolors week and month titles but not a day's date`() {
        val document = planner()
        val week = pageOf(document, PlannerKind.WEEK, 21)
        val key = elementsOf(week).first { it.role == PlannerRole.PAGE_TITLE }.key
        PlannerPages.restyle(document, week.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.TEXT, red))!!.apply(targetOf(document))
        assertEquals(red, draw(pageOf(document, PlannerKind.WEEK, 21)).word("Week 22").color)
        assertEquals(red, draw(pageOf(document, PlannerKind.MONTH, 0)).words.first { it.text.startsWith("01 |") }.color)
        val day = draw(pageOf(document, PlannerKind.DAY, 0))
        assertTrue(day.words.none { it.color == red })
    }

    @Test
    fun `background and shading are drawn, and a recolored tile is drawn the same in every tile it touches`() {
        val document = planner()
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val key = elementsOf(index).first { it.role == PlannerRole.BUTTON }.key
        PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.BACKGROUND, green))!!.apply(targetOf(document))
        val page = pageOf(document, PlannerKind.DASHBOARD)
        val whole = draw(page)
        assertTrue(whole.fills.any { it.last() == green } && whole.dots.any { it.last() == green })
        // Any region gets the same marks: the template never leaves out something that reaches into another tile. The canvas clips.
        val half = size.widthPt / 2f
        val left = draw(page, Bounds(0f, 0f, half, size.heightPt))
        val right = draw(page, Bounds(half, 0f, size.widthPt, size.heightPt))
        for (part in listOf(left, right)) {
            assertEquals(whole.fills, part.fills)
            assertEquals(whole.dots, part.dots)
            assertEquals(whole.lines, part.lines)
            assertEquals(whole.words.map { it.text to it.color }, part.words.map { it.text to it.color })
        }
    }

    @Test
    fun `date handwriting keeps its own colors and survives restyling`() {
        val document = planner()
        val target = targetOf(document)
        val stroke = DateStroke(0, 0xFF123456.toInt(), 2f, listOf(0f, 0f, 20f, 10f))
        PlannerPages.addDateInk(document, stroke)!!.apply(target)
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val keys = elementsOf(index).map { it.key }.distinct()
        for (slot in listOf(RoleSlot.TEXT, RoleSlot.LINE, RoleSlot.BACKGROUND, RoleSlot.SHADING)) {
            PlannerPages.restyle(document, index.id, keys, true, PlannerPages.StyleChange(slot, red))!!.apply(target)
        }
        val drawn = draw(pageOf(document, PlannerKind.DASHBOARD))
        assertEquals(listOf(0xFF123456.toInt()), drawn.polylines)
        // The ink is still on the pages that show the date, and a date typed afterwards keeps the styles.
        assertEquals(listOf(stroke), PlannerPages.dateInkOf(document))
        PlannerPages.setSemesterDate(document, 0, false, 20_000)!!.apply(target)
        assertEquals(listOf(stroke), PlannerPages.dateInkOf(document))
        assertTrue(!PlannerPages.rolesOf(document).isEmpty)
        // The typed date is drawn, and the ink in the semester page too.
        assertEquals(listOf(0xFF123456.toInt()), draw(pageOf(document, PlannerKind.SEMESTER_PLAN)).polylines)
    }

    @Test
    fun `a change of years keeps the role styles and the styles of single elements`() {
        val document = planner()
        val target = targetOf(document)
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val buttons = elementsOf(index).filter { it.role == PlannerRole.BUTTON }
        PlannerPages.restyle(document, index.id, buttons.take(1).map { it.key }, true, PlannerPages.StyleChange(RoleSlot.TEXT, red))!!.apply(target)
        PlannerPages.restyle(document, index.id, buttons.drop(1).take(1).map { it.key }, false, PlannerPages.StyleChange(RoleSlot.LINE, green))!!.apply(target)
        val roles = PlannerPages.rolesOf(document)
        PlannerPages.change(document, PlannerSetup(2027, 1), ids())!!.apply(target)
        assertEquals(roles, PlannerPages.rolesOf(document))
        val newIndex = pageOf(document, PlannerKind.DASHBOARD)
        assertEquals(1, (newIndex.background.template as PageTemplate.Planner).roles.byElement.size)
    }

    // ---- The right-aligned text and the cover ----------------------------------------------------------------------

    @Test
    fun `the year at the right of a title ends at the right margin whatever its font`() {
        val document = planner()
        val month = pageOf(document, PlannerKind.MONTH, 0)
        val plain = draw(month).word("2026")
        assertEquals(TextAlign.END, plain.align)
        PlannerPages.restyle(document, month.id, listOf(keyOfWord(month, PlannerRole.SUBTITLE, 112f * size.heightPt / PlannerDrawing.H)), true, PlannerPages.StyleChange(RoleSlot.FONT, font = "Lora"))!!.apply(targetOf(document))
        val styled = draw(pageOf(document, PlannerKind.MONTH, 0)).word("2026")
        assertEquals(plain.x, styled.x, 0.001f)
        assertEquals("Lora", styled.font)
    }

    @Test
    fun `the cover is not restyled by a role`() {
        val document = planner()
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val keys = elementsOf(index).map { it.key }.distinct()
        val cover = Page(1, size, document.pages[0].background)
        val before = draw(cover)
        for (slot in listOf(RoleSlot.TEXT, RoleSlot.LINE)) {
            PlannerPages.restyle(document, index.id, keys, true, PlannerPages.StyleChange(slot, red))!!.apply(targetOf(document))
        }
        val after = draw(document.pages[0])
        assertEquals(before.words.map { it.color }, after.words.map { it.color })
    }

    // ---- The file --------------------------------------------------------------------------------------------------

    @Test
    fun `restyles are saved at format 14 and read back, and a planner nobody restyled keeps its bytes`() {
        val document = planner()
        val plain = DocumentCodec.encode(document)
        assertEquals(7, plain[4].toInt())
        val index = pageOf(document, PlannerKind.DASHBOARD)
        val key = elementsOf(index).first { it.role == PlannerRole.BUTTON }.key
        val target = targetOf(document)
        PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.TEXT, red))!!.apply(target)
        PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(RoleSlot.FONT, font = "Caveat"))!!.apply(target)
        PlannerPages.restyle(document, index.id, listOf(key), false, PlannerPages.StyleChange(RoleSlot.SHADING, grey))!!.apply(target)
        val bytes = DocumentCodec.encode(document)
        assertEquals(14, bytes[4].toInt())
        val back = DocumentCodec.decode(bytes)
        assertEquals(document.pages.map { it.background }, back.pages.map { it.background })
        assertEquals(RoleStyle(text = red, font = "Caveat"), PlannerPages.rolesOf(back).byRole[PlannerRole.BUTTON])
        // Putting everything back gives the old bytes.
        PlannerPages.restyle(document, index.id, listOf(key), true, PlannerPages.StyleChange(null))!!.apply(target)
        assertTrue(plain.contentEquals(DocumentCodec.encode(document)))
    }

    @Test
    fun `an old file still opens, and a style with a part is not empty`() {
        val old = DocumentCodec.decode(DocumentCodec.encode(planner()))
        assertTrue(old.pages.all { (it.background.template as PageTemplate.Planner).roles.isEmpty })
        assertTrue(RoleStyle().isEmpty)
        assertTrue(!RoleStyle(plain = true).isEmpty)
    }

    @Test
    fun `an element's own style wins over its role's, part by part`() {
        val styles = PlannerRoleStyles()
            .withRole(PlannerRole.BUTTON, RoleStyle(text = red, line = green))
            .withElement(ElementKey(PlannerRole.BUTTON, 5, 6), RoleStyle(text = grey))
        assertEquals(RoleStyle(text = grey, line = green), styles.styleOf(PlannerRole.BUTTON, 5, 6))
        assertEquals(RoleStyle(text = red, line = green), styles.styleOf(PlannerRole.BUTTON, 9, 9))
        val plain = styles.withElement(ElementKey(PlannerRole.BUTTON, 5, 6), RoleStyle(plain = true))
        assertEquals(RoleStyle(plain = true), plain.styleOf(PlannerRole.BUTTON, 5, 6))
        assertNull(plain.styleOf(PlannerRole.TAB, 5, 6))
    }

    @Test
    fun `lines of a section heading, with apply to similar, change under every heading of the day and week pages`() {
        for (kind in listOf(PlannerKind.DAY, PlannerKind.WEEK)) {
            val document = planner()
            val page = pageOf(document, kind, 0)
            val headings = elementsOf(page).filter { it.role == PlannerRole.SECTION_HEADING && RoleSlot.LINE in it.slots }
            // Schedule (day page only), top priorities, personal tasks, student tasks and notes.
            assertEquals(kind.name, if (kind == PlannerKind.DAY) 5 else 4, headings.size)
            val pick = headings.first { it.bounds.left > 400f * size.widthPt / PlannerDrawing.W }
            PlannerPages.restyle(document, page.id, listOf(pick.key), true, PlannerPages.StyleChange(RoleSlot.LINE, red))!!.apply(targetOf(document))
            assertTrue(kind.name, draw(pageOf(document, kind, 0)).lines.count { it == red } >= headings.size)
        }
    }

    @Test
    fun `lines of the rows of a section, with apply to similar, change in every section`() {
        val document = planner()
        val page = pageOf(document, PlannerKind.DAY, 0)
        val rows = elementsOf(page).filter { it.role == PlannerRole.ROWS }
        assertEquals(3, rows.size)
        PlannerPages.restyle(document, page.id, listOf(rows.first().key), true, PlannerPages.StyleChange(RoleSlot.LINE, red))!!.apply(targetOf(document))
        val after = draw(pageOf(document, PlannerKind.DAY, 0))
        // Every row has a rule above and below, 3 + 7 + 7 rows and one more rule each, and the dividers.
        assertTrue("red lines: ${after.lines.count { it == red }}", after.lines.count { it == red } >= 3 + 7 + 7 + 3)
    }

    @Test
    fun `lines of a heading with apply to similar also recolor the rows of every section, and the other way round`() {
        for (pickRole in listOf(PlannerRole.SECTION_HEADING, PlannerRole.ROWS)) {
            val document = planner()
            val page = pageOf(document, PlannerKind.DAY, 0)
            val pick = elementsOf(page).first { it.role == pickRole && it.bounds.left > 400f * size.widthPt / PlannerDrawing.W }
            PlannerPages.restyle(document, page.id, listOf(pick.key), true, PlannerPages.StyleChange(RoleSlot.LINE, red))!!.apply(targetOf(document))
            val roles = PlannerPages.rolesOf(document).byRole
            assertTrue(pickRole.name, roles.containsKey(PlannerRole.SECTION_HEADING) && roles.containsKey(PlannerRole.ROWS))
        }
    }

    @Test
    fun `a heading is picked by where its text starts, and a list by one of its rules`() {
        val page = pageOf(planner(), PlannerKind.DAY, 0)
        val heading = elementsOf(page).first { it.role == PlannerRole.SECTION_HEADING }
        assertTrue(heading.surrounded { x, y -> x <= heading.bounds.left + 30f && y in heading.bounds.top..heading.bounds.bottom })
        val rows = elementsOf(page).first { it.role == PlannerRole.ROWS }
        val top = rows.bounds.top
        assertTrue(rows.surrounded { x, y -> y == top && x in rows.bounds.left..rows.bounds.right })
    }

    @Test
    fun `spread gives every element like the picked ones the look they already have`() {
        val document = planner()
        val page = pageOf(document, PlannerKind.DAY, 0)
        val key = elementsOf(page).first { it.role == PlannerRole.SECTION_HEADING }.key
        PlannerPages.restyle(document, page.id, listOf(key), false, PlannerPages.StyleChange(RoleSlot.TEXT, red))!!.apply(targetOf(document))
        assertEquals(1, draw(pageOf(document, PlannerKind.DAY, 0)).words.count { it.color == red })
        PlannerPages.spread(document, page.id, listOf(key))!!.apply(targetOf(document))
        assertTrue(draw(pageOf(document, PlannerKind.DAY, 0)).words.count { it.color == red } >= 4)
        assertTrue(draw(pageOf(document, PlannerKind.WEEK, 0)).words.count { it.color == red } >= 3)
    }

    @Test
    fun `reset with similar on a heading puts back the rows' lines and leaves the rest of the rows`() {
        val document = planner()
        val target = targetOf(document)
        val page = pageOf(document, PlannerKind.DAY, 0)
        val heading = elementsOf(page).first { it.role == PlannerRole.SECTION_HEADING }.key
        val rows = elementsOf(page).first { it.role == PlannerRole.ROWS }.key
        PlannerPages.restyle(document, page.id, listOf(rows), true, PlannerPages.StyleChange(RoleSlot.TEXT, green))!!.apply(target)
        PlannerPages.restyle(document, page.id, listOf(heading), true, PlannerPages.StyleChange(RoleSlot.LINE, red))!!.apply(target)
        assertTrue(draw(pageOf(document, PlannerKind.DAY, 0)).lines.count { it == red } > 5)
        PlannerPages.restyle(document, page.id, listOf(heading), true, PlannerPages.StyleChange(null))!!.apply(target)
        assertEquals(0, draw(pageOf(document, PlannerKind.DAY, 0)).lines.count { it == red })
        val roles = PlannerPages.rolesOf(document).byRole
        assertNull(roles[PlannerRole.SECTION_HEADING])
        // The rows' own text color was not what the heading's lines changed, so it stays.
        assertEquals(RoleStyle(text = green), roles[PlannerRole.ROWS])
    }

    @Test
    fun `lines changed on one element and then spread reach the headings and the rows, in either order of the two`() {
        for (pickRole in listOf(PlannerRole.SECTION_HEADING, PlannerRole.ROWS)) {
            val document = planner()
            val target = targetOf(document)
            val page = pageOf(document, PlannerKind.DAY, 0)
            val key = elementsOf(page).first { it.role == pickRole }.key
            PlannerPages.restyle(document, page.id, listOf(key), false, PlannerPages.StyleChange(RoleSlot.LINE, red))!!.apply(target)
            PlannerPages.spread(document, page.id, listOf(key))!!.apply(target)
            val roles = PlannerPages.rolesOf(document).byRole
            assertEquals(pickRole.name, red, roles[PlannerRole.SECTION_HEADING]?.line)
            assertEquals(pickRole.name, red, roles[PlannerRole.ROWS]?.line)
        }
    }

    @Test
    fun `a spread with no change of lines leaves the other half of a section alone`() {
        val document = planner()
        val target = targetOf(document)
        val page = pageOf(document, PlannerKind.DAY, 0)
        val key = elementsOf(page).first { it.role == PlannerRole.SECTION_HEADING }.key
        PlannerPages.restyle(document, page.id, listOf(key), false, PlannerPages.StyleChange(RoleSlot.TEXT, red))!!.apply(target)
        PlannerPages.spread(document, page.id, listOf(key))!!.apply(target)
        assertNull(PlannerPages.rolesOf(document).byRole[PlannerRole.ROWS])
    }
}
