package app.betternotes.core.template

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageAppearanceTest {
    private fun note() = Document.newNote("Note", PageSize.A4, PageBackground(), 0L, CountingIds())
    private fun pdf() = Document.fromPdf("PDF", "asset", listOf(PageSize.Letter), 0L, CountingIds())
    private fun canvas() = Document.newCanvas("Canvas", PageBackground(), 0L, CountingIds())

    @Test
    fun `every kind makes a template of that kind`() {
        for (kind in TemplateKind.entries) assertEquals(kind, kind.newTemplate().kind)
    }

    @Test
    fun `density and line weight are read and set for every template that has them`() {
        for (kind in TemplateKind.entries) {
            val template = kind.newTemplate()
            if (kind == TemplateKind.BLANK) {
                assertNull(template.density)
                assertNull(template.lineWeight)
                assertEquals(template, template.withDensity(10f))
            } else {
                assertEquals(30f, template.withDensity(30f).density)
                assertEquals(1.5f, template.withLineWeight(1.5f).lineWeight)
            }
        }
    }

    @Test
    fun `density and line weight stay in range`() {
        assertEquals(DENSITY_RANGE_PT.start, PageTemplate.Grid().withDensity(0f).density)
        assertEquals(DENSITY_RANGE_PT.endInclusive, PageTemplate.Grid().withDensity(9999f).density)
        assertEquals(LINE_WEIGHT_RANGE_PT.start, PageTemplate.Grid().withLineWeight(-1f).lineWeight)
    }

    @Test
    fun `slider steps land on each template and step 7 and 3 are its defaults`() {
        for (kind in TemplateKind.entries) {
            val template = kind.newTemplate()
            if (kind == TemplateKind.BLANK) continue
            assertEquals(7, template.densityLevel)
            assertEquals(3, template.lineWeightLevel)
            for (level in TEMPLATE_LEVELS) {
                assertEquals("$kind density", level, template.withDensityLevel(level).densityLevel)
                assertEquals("$kind weight", level, template.withLineWeightLevel(level).lineWeightLevel)
            }
        }
    }

    @Test
    fun `higher density means closer lines`() {
        val loose = PageTemplate.Lined().withDensityLevel(2).density!!
        val tight = PageTemplate.Lined().withDensityLevel(9).density!!
        assert(tight < loose)
    }

    @Test
    fun `resizing skips what cannot change size`() {
        val document = note()
        val edit = document.resizePages(listOf(document.pages[0].id), PageSize.Letter)!!
        edit.apply(FakeTarget(document))
        assertEquals(PageSize.Letter, document.pages[0].size)
        val pdfDoc = pdf()
        assertNull(pdfDoc.resizePages(listOf(pdfDoc.pages[0].id), PageSize.A4))
    }

    @Test
    fun `a lone page can become infinite and back, and a second page rules it out`() {
        val document = note()
        val target = FakeTarget(document)
        val before = document.pages[0]
        val grid = PageBackground(template = PageTemplate.Grid())
        val edit = document.changeAppearance(listOf(before.id), grid, infinite = true)!!
        edit.apply(target)
        assertNull(document.pages[0].size)
        assertEquals(false, document.canAddPages)
        document.resizePages(listOf(before.id), PageSize.A4)!!.apply(target)
        assertEquals(PageSize.A4, document.pages[0].size)
        assertEquals(true, document.canAddPages)
        edit.revert(target)
        edit.apply(target)
        edit.revert(target)
        assertEquals(before, document.pages[0])

        // A template with no tiling would draw nothing on an infinite page.
        assertNull(document.changeAppearance(listOf(before.id), PageBackground(template = PageTemplate.Legal()), infinite = true))

        document.insertPage(1, before.copy(id = 99L))
        assertNull(document.changeAppearance(listOf(before.id), grid, infinite = true))
    }

    @Test
    fun `applying the picker changes paper template and size as one undo`() {
        val document = note()
        val target = FakeTarget(document)
        val before = document.pages[0]
        val background = PageBackground(0xFF000000.toInt(), PageTemplate.Grid())
        val edit = document.changeAppearance(listOf(before.id), background, PageSize.Letter)!!
        edit.apply(target)
        assertEquals(background, document.pages[0].background)
        assertEquals(PageSize.Letter, document.pages[0].size)
        edit.revert(target)
        assertEquals(before, document.pages[0])
    }

    @Test
    fun `applying the picker skips pages that cannot take it`() {
        val background = PageBackground(template = PageTemplate.Custom(layout()))
        val canvasDoc = canvas()
        assertNull(canvasDoc.changeAppearance(listOf(canvasDoc.pages[0].id), background))
        assertNull(canvasDoc.changeAppearance(listOf(canvasDoc.pages[0].id), PageBackground(template = PageTemplate.Legal())))
        val pdfDoc = pdf()
        assertNull(pdfDoc.changeAppearance(listOf(pdfDoc.pages[0].id), PageBackground(template = PageTemplate.Grid())))
        val noteDoc = note()
        noteDoc.changeAppearance(listOf(noteDoc.pages[0].id), background)!!.apply(FakeTarget(noteDoc))
        assertEquals(layout().size, noteDoc.pages[0].size)
    }

    @Test
    fun `changing the background can be undone`() {
        val document = note()
        val target = FakeTarget(document)
        val id = document.pages[0].id
        val before = document.pages[0].background
        val after = PageBackground(0xFF000000.toInt(), PageTemplate.Lined())

        val edit = document.changeBackground(listOf(id), after)!!
        edit.apply(target)
        assertEquals(after, document.pages[0].background)
        edit.revert(target)
        assertEquals(before, document.pages[0].background)
    }

    @Test
    fun `no change makes no edit`() {
        val document = note()
        val id = document.pages[0].id
        assertNull(document.changeBackground(listOf(id), document.pages[0].background))
    }

    @Test
    fun `a pdf page keeps its look and its size`() {
        val document = pdf()
        val id = document.pages[0].id
        assertNull(document.changeBackground(listOf(id), PageBackground(template = PageTemplate.Lined())))
        assertNull(document.changePaperColor(listOf(id), 0xFF000000.toInt()))
        assertNull(document.applyCustomLayout(listOf(id), layout()))
    }

    @Test
    fun `a canvas takes repeating templates only`() {
        val document = canvas()
        val id = document.pages[0].id
        assertNull(document.changeBackground(listOf(id), PageBackground(template = PageTemplate.Cornell())))
        assertNull(document.applyCustomLayout(listOf(id), layout()))
        val edit = document.changeBackground(listOf(id), PageBackground(template = PageTemplate.Hexagonal()))!!
        edit.apply(FakeTarget(document))
        assertEquals(PageTemplate.Hexagonal(), document.pages[0].background.template)
    }

    @Test
    fun `a custom layout brings its size and undoes as one`() {
        val document = note()
        val target = FakeTarget(document)
        val edit = document.applyCustomLayout(listOf(document.pages[0].id), layout())!!
        edit.apply(target)
        assertEquals(layout().size, document.pages[0].size)
        assertEquals(PageTemplate.Custom(layout()), document.pages[0].background.template)
        edit.revert(target)
        assertEquals(PageSize.A4, document.pages[0].size)
        assertEquals(PageTemplate.Blank, document.pages[0].background.template)
    }

    private fun layout() = CustomLayout("Mine", PageSize(200f, 300f), LengthUnit.CM, emptyList())
}

class CustomLayoutBuilderTest {
    private val line = LayoutElement.Line(10f, 10f, 110f, 10f, 1f, 0)
    private val table = LayoutElement.Table(50f, 50f, 100f, 100f, 2, 2, 1f, 0)

    @Test
    fun `the size can be set in each unit`() {
        val builder = CustomLayoutBuilder("Mine")
        builder.unit = LengthUnit.CM
        builder.setSize(10f, 20f)
        assertEquals(10f * 72f / 2.54f, builder.size.widthPt, 0.01f)

        builder.unit = LengthUnit.INCH
        builder.setSize(2f, 3f)
        assertEquals(144f, builder.size.widthPt, 0.01f)
        assertEquals(216f, builder.size.heightPt, 0.01f)
        assertEquals(2f, builder.width, 0.001f)

        builder.unit = LengthUnit.PX
        builder.setSize(96f, 192f)
        assertEquals(72f, builder.size.widthPt, 0.01f)
    }

    @Test
    fun `switching the unit keeps the size`() {
        val builder = CustomLayoutBuilder("Mine", PageSize(144f, 288f), LengthUnit.INCH)
        assertEquals(2f, builder.width, 0.001f)
        builder.unit = LengthUnit.CM
        assertEquals(5.08f, builder.width, 0.001f)
        assertEquals(PageSize(144f, 288f), builder.size)
    }

    @Test
    fun `sizes stay in range`() {
        val builder = CustomLayoutBuilder("Mine", unit = LengthUnit.PT)
        builder.setSize(1f, 1_000_000f)
        assertEquals(PageSize(36f, 14400f), builder.size)
    }

    @Test
    fun `elements can be added changed and removed`() {
        val builder = CustomLayoutBuilder("Mine")
        val a = builder.add(line)
        val b = builder.add(table)
        assertEquals(listOf(line, table), builder.build().elements)
        builder.update(a, line.copy(lineWidth = 3f))
        builder.remove(b)
        assertEquals(listOf<LayoutElement>(line.copy(lineWidth = 3f)), builder.build().elements)
    }

    @Test
    fun `moving stops at the page edge`() {
        val builder = CustomLayoutBuilder("Mine", PageSize(200f, 200f))
        val index = builder.add(table)
        builder.moveBy(index, -500f, 500f)
        assertEquals(Bounds(0f, 100f, 100f, 200f), builder.elements[index].bounds)
    }

    @Test
    fun `an element past the edge after a resize can move back`() {
        val builder = CustomLayoutBuilder("Mine", PageSize(100f, 100f), elements = listOf(table))
        builder.moveBy(0, -60f, 0f)
        assertEquals(0f, builder.elements[0].bounds.left, 0.001f)
    }

    @Test
    fun `hit testing finds the top element and follows lines`() {
        val builder = CustomLayoutBuilder("Mine")
        builder.add(table)
        builder.add(table.copy(x = 60f, y = 60f))
        builder.add(line)
        assertEquals(1, builder.elementAt(100f, 100f, 2f))
        assertEquals(0, builder.elementAt(55f, 55f, 2f))
        assertEquals(2, builder.elementAt(60f, 11f, 2f))
        assertEquals(-1, builder.elementAt(60f, 30f, 2f))
    }

    @Test
    fun `a saved layout opens in the builder as it was`() {
        val layout = CustomLayout("Mine", PageSize(200f, 300f), LengthUnit.INCH, listOf(line))
        assertEquals(layout, CustomLayoutBuilder(layout).build())
    }
}
