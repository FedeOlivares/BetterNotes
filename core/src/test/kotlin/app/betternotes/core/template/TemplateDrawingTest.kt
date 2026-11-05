package app.betternotes.core.template

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class TemplateDrawingTest {
    private data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    private class Recorder : TemplateSink {
        val lines = mutableListOf<Line>()
        val dots = mutableListOf<Pair<Float, Float>>()
        override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
            lines += Line(x0, y0, x1, y1)
        }

        override fun dot(x: Float, y: Float, radius: Float, color: Int) {
            dots += x to y
        }
    }

    private val page = PageSize(100f, 200f)
    private val wholePage = Bounds(0f, 0f, 100f, 200f)

    private fun PageTemplate.record(p: PageSize? = page, region: Bounds = wholePage) =
        Recorder().also { draw(p, region, it) }

    @Test
    fun `lined page skips both edges`() {
        val lines = PageTemplate.Lined(spacing = 50f).record().lines
        assertEquals(listOf(50f, 100f, 150f), lines.map { it.y0 })
        assertTrue(lines.all { it.x0 == 0f && it.x1 == 100f })
    }

    @Test
    fun `density changes the line count`() {
        val sparse = PageTemplate.Lined(spacing = 40f).record().lines.size
        val dense = PageTemplate.Lined(spacing = 10f).record().lines.size
        assertEquals(4, sparse)
        assertEquals(19, dense)
    }

    @Test
    fun `grid draws both directions`() {
        val lines = PageTemplate.Grid(spacing = 50f).record().lines
        assertEquals(3, lines.count { it.y0 == it.y1 })
        assertEquals(1, lines.count { it.x0 == it.x1 })
    }

    @Test
    fun `dots form a lattice inside the region`() {
        val dots = PageTemplate.Dotted(spacing = 50f).record().dots
        assertEquals(setOf(50f to 50f, 50f to 100f, 50f to 150f), dots.toSet())
    }

    @Test
    fun `only the requested region is drawn`() {
        val lines = PageTemplate.Lined(spacing = 10f).record(region = Bounds(0f, 95f, 100f, 125f)).lines
        assertEquals(listOf(100f, 110f, 120f), lines.map { it.y0 })
    }

    @Test
    fun `region outside the page draws nothing`() {
        val lines = PageTemplate.Lined(spacing = 10f).record(region = Bounds(200f, 300f, 300f, 400f)).lines
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `infinite canvas tiles from the world origin, including negative space`() {
        val region = Bounds(-5f, -25f, 5f, 25f)
        val lines = PageTemplate.Lined(spacing = 10f).record(p = null, region = region).lines
        assertEquals(listOf(-20f, -10f, 0f, 10f, 20f), lines.map { it.y0 })
    }

    @Test
    fun `page bound layouts draw nothing on an infinite canvas`() {
        assertFalse(PageTemplate.Cornell().tiles)
        assertTrue(PageTemplate.Cornell().record(p = null).lines.isEmpty())
        assertTrue(PageTemplate.MusicScore().record(p = null).lines.isEmpty())
    }

    @Test
    fun `hexagon tessellation has equal edges and shares every vertex with three edges`() {
        val side = 10f
        val lines = PageTemplate.Hexagonal(side = side).record().lines
        assertTrue(lines.isNotEmpty())
        lines.forEach { assertEquals(side, hypot(it.x1 - it.x0, it.y1 - it.y0), 1e-3f) }

        val uniqueEdges = lines.map { edgeKey(it) }
        assertEquals("edges must not be drawn twice", uniqueEdges.size, uniqueEdges.toSet().size)

        val degree = HashMap<Pair<Int, Int>, Int>()
        lines.forEach { line ->
            for (vertex in listOf(key(line.x0, line.y0), key(line.x1, line.y1))) {
                degree.merge(vertex, 1, Int::plus)
            }
        }
        val interior = degree.filterKeys { (x, y) -> abs(x - 500) < 150 && abs(y - 1000) < 150 }
        assertTrue(interior.isNotEmpty())
        interior.values.forEach { assertEquals(3, it) }
    }

    private fun key(x: Float, y: Float) = (x * 10).roundToInt() to (y * 10).roundToInt()

    private fun edgeKey(l: Line): Set<Pair<Int, Int>> = setOf(key(l.x0, l.y0), key(l.x1, l.y1))

    @Test
    fun `cornell has three dividers and rules only the note area`() {
        val a4 = PageSize.A4
        val lines = PageTemplate.Cornell(spacing = 24f).record(a4, Bounds(0f, 0f, a4.widthPt, a4.heightPt)).lines
        val cueRight = a4.widthPt * 0.3f
        assertEquals(1, lines.count { it.x0 == it.x1 && it.x0 == cueRight })
        val fullWidth = lines.filter { it.x0 == 0f && it.x1 == a4.widthPt }
        assertEquals(2, fullWidth.size)
        val ruled = lines.filter { it.x0 == cueRight && it.y0 == it.y1 }
        assertTrue(ruled.size > 10)
        assertTrue(ruled.all { it.y0 > fullWidth[0].y0 && it.y0 < fullWidth[1].y0 })
    }

    @Test
    fun `legal has a double margin and a blank head`() {
        val lines = PageTemplate.Legal(spacing = 50f).record(PageSize(200f, 300f), Bounds(0f, 0f, 200f, 300f)).lines
        assertEquals(2, lines.count { it.x0 == it.x1 })
        assertEquals(72f, lines.filter { it.y0 == it.y1 }.minOf { it.y0 }, 0f)
    }

    @Test
    fun `music has staves of five lines and guitar of six`() {
        val a4 = PageSize.A4
        val region = Bounds(0f, 0f, a4.widthPt, a4.heightPt)
        val music = PageTemplate.MusicScore(lineGap = 10f, staffGap = 50f).record(a4, region).lines
        val guitar = PageTemplate.GuitarTab(lineGap = 10f, staffGap = 50f).record(a4, region).lines
        assertEquals(0, music.size % 5)
        assertEquals(0, guitar.size % 6)
        assertEquals(46f, music[1].y0, 0f)
        assertEquals(50f, music[5].y0 - music[4].y0 - 0f, 0f)
        assertTrue(music.all { it.y0 <= a4.heightPt - 36f })
    }

    @Test
    fun `custom layout draws its elements on a page only`() {
        val layout = CustomLayout(
            name = "Mine",
            size = PageSize.of(10f, 10f, LengthUnit.CM),
            displayUnit = LengthUnit.CM,
            elements = listOf(
                LayoutElement.Line(0f, 0f, 10f, 10f, 1f, 0),
                LayoutElement.Rectangle(0f, 0f, 10f, 10f, 1f, 0),
                LayoutElement.Table(0f, 0f, 30f, 20f, rows = 2, columns = 3, lineWidth = 1f, color = 0),
                LayoutElement.Ruled(0f, 0f, 30f, 25f, spacing = 10f, lineWidth = 1f, color = 0),
            ),
        )
        val template = PageTemplate.Custom(layout)
        // 1 line, 4 sides, table of 4 sides plus 1 row divider and 2 column dividers, 2 ruled lines.
        assertEquals(1 + 4 + 7 + 2, template.record().lines.size)
        assertTrue(template.record(p = null).lines.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `spacing below the minimum is rejected`() {
        PageTemplate.Lined(spacing = 0f)
    }

    @Test
    fun `ruled cornell rules the cue column too and plain cornell does not`() {
        fun startsAtEdge(t: PageTemplate) = t.record().lines.count { it.x0 == 0f && it.y0 == it.y1 }
        assertEquals(2, startsAtEdge(PageTemplate.Cornell()))
        assertTrue(startsAtEdge(PageTemplate.CornellRuled()) > 4)
    }

    @Test
    fun `engineering grid stays inside its frame and has no pattern on a canvas`() {
        val lines = PageTemplate.Engineering().record().lines
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it.x0 >= 18f && it.x1 <= page.widthPt - 18f && it.y0 >= 18f && it.y1 <= page.heightPt - 18f })
        assertTrue(PageTemplate.Engineering().record(p = null).lines.isEmpty())
    }

    @Test
    fun `guitar score pairs a five line staff with a six line one, joined on the left`() {
        val lines = PageTemplate.GuitarScore().record().lines
        val horizontal = lines.count { it.y0 == it.y1 }
        val vertical = lines.count { it.x0 == it.x1 }
        assertTrue(vertical > 0)
        assertEquals(vertical * 11, horizontal)
    }

    @Test
    fun `a custom layout draws its base pattern under its elements`() {
        val layout = CustomLayout(
            "L", page, LengthUnit.PT,
            listOf(LayoutElement.Line(1f, 1f, 2f, 2f, 1f, 0)),
            base = PageTemplate.Lined(spacing = 50f),
        )
        val lines = PageTemplate.Custom(layout).record().lines
        assertEquals(listOf(50f, 100f, 150f, 1f), lines.map { it.y0 })
    }

    @Test
    fun `book style mirrors every second page`() {
        val book = CustomLayout("L", page, LengthUnit.PT, emptyList(), bookStyle = true)
        assertEquals(listOf(false, true, false, true), (0..3).map { book.isMirroredOn(it) })
        assertFalse(book.copy(bookStyle = false).isMirroredOn(1))
    }
}
