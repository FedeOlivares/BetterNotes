package app.betternotes.core.tools

import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.FillPatternKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PatternMarksTest {
    private fun pattern(kind: FillPatternKind, spacing: Float = 6f, weight: Float = 0.2f) = FillPattern(kind, 0xFF000000.toInt(), spacing, weight)

    /** True if a mark of [marks] covers the point, as a pen of the mark's thickness would: dots round, lines flat. */
    private fun covered(marks: PatternMarks, x: Float, y: Float): Boolean {
        val half = marks.thickness / 2f
        for (i in 0 until marks.dotCount step 2) if (hypot(x - marks.dots[i], y - marks.dots[i + 1]) <= half) return true
        for (i in 0 until marks.lineCount step 4) {
            val x0 = marks.lines[i]
            val y0 = marks.lines[i + 1]
            val dx = marks.lines[i + 2] - x0
            val dy = marks.lines[i + 3] - y0
            val length = dx * dx + dy * dy
            val along = if (length == 0f) 0f else ((x - x0) * dx + (y - y0) * dy) / length
            if (along < 0f || along > 1f) continue
            if (hypot(x - (x0 + along * dx), y - (y0 + along * dy)) <= half) return true
        }
        return false
    }

    private fun marksOver(p: FillPattern, left: Float, top: Float, right: Float, bottom: Float) =
        PatternMarks().apply { make(p, left, top, right, bottom) }

    @Test
    fun `a solid fill has no marks`() {
        val marks = marksOver(FillPattern.SOLID, 0f, 0f, 100f, 100f)
        assertEquals(0, marks.dotCount)
        assertEquals(0, marks.lineCount)
    }

    @Test
    fun `every pattern draws something`() {
        for (kind in FillPatternKind.entries.filter { it != FillPatternKind.SOLID }) {
            val marks = marksOver(pattern(kind), 10f, 10f, 40f, 40f)
            assertTrue("$kind", marks.dotCount + marks.lineCount > 0)
        }
    }

    @Test
    fun `tiles that meet draw exactly what the region around them draws, marks that cross the border included`() {
        // A fill spans four tiles. Dots and lines centered in one tile that reach into the next must be in both: what each tile draws
        // equals the part of the whole region's picture over it, whatever the pattern, spacing and weight, and wherever the border is.
        for (kind in FillPatternKind.entries.filter { it != FillPatternKind.SOLID }) {
            for (spacing in listOf(3f, 5.5f, 13f)) {
                for (border in listOf(100f, 100.1f, 99.93f, -50.37f)) {
                    val p = pattern(kind, spacing, 0.34f)
                    val whole = marksOver(p, border - 40f, border - 40f, border + 40f, border + 40f)
                    val tiles = listOf(
                        floatArrayOf(border - 40f, border - 40f, border, border),
                        floatArrayOf(border, border - 40f, border + 40f, border),
                        floatArrayOf(border - 40f, border, border, border + 40f),
                        floatArrayOf(border, border, border + 40f, border + 40f),
                    )
                    for (t in tiles) {
                        val tile = marksOver(p, t[0], t[1], t[2], t[3])
                        var y = t[1]
                        while (y < t[3]) {
                            var x = t[0]
                            while (x < t[2]) {
                                assertEquals("$kind $spacing $border at $x,$y", covered(whole, x, y), covered(tile, x, y))
                                x += 0.37f
                            }
                            y += 0.37f
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a dot that reaches a pixel into the region asked about is drawn`() {
        // Dots of radius 2 (weight .34 of spacing 6) at x = 0, 6, 12 in a row: the one at 12 reaches x = 10, past a region that starts at 10.5.
        val marks = marksOver(pattern(FillPatternKind.DOTS, 6f, 0.34f), 10.5f, -1f, 20f, 1f)
        assertTrue(covered(marks, 10.5f, 0f))
    }

    @Test
    fun `lines are as far apart as asked`() {
        val marks = marksOver(pattern(FillPatternKind.LINES, 10f), 0f, 0f, 100f, 100f)
        val ys = (0 until marks.lineCount step 4).map { marks.lines[it + 1] }.filter { it in 0f..100f }.sorted()
        assertEquals(11, ys.size)
        assertEquals(10f, ys[1] - ys[0], 0.001f)
    }

    @Test
    fun `diagonal lines are a perpendicular spacing apart`() {
        val marks = marksOver(pattern(FillPatternKind.DIAGONAL, 10f), 0f, 0f, 200f, 200f)
        val sums = (0 until marks.lineCount step 4).map { marks.lines[it] + marks.lines[it + 1] }.sorted().distinct()
        assertEquals(10f * 1.4142135f, sums[2] - sums[1], 0.01f)
    }

    @Test
    fun `a crosshatch has lines in both directions`() {
        val marks = marksOver(pattern(FillPatternKind.CROSSHATCH, 10f), 0f, 0f, 100f, 100f)
        val rising = (0 until marks.lineCount step 4).count { (marks.lines[it + 3] - marks.lines[it + 1]) * (marks.lines[it + 2] - marks.lines[it]) < 0f }
        val falling = (0 until marks.lineCount step 4).count { (marks.lines[it + 3] - marks.lines[it + 1]) * (marks.lines[it + 2] - marks.lines[it]) > 0f }
        assertTrue(rising > 0 && falling > 0)
    }
}
