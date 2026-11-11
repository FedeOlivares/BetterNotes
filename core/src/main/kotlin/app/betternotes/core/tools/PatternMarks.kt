package app.betternotes.core.tools

import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.FillPatternKind

/**
 * The marks of a [FillPattern] over a rectangle of the page, as dots (x, y pairs) and line segments (x0, y0, x1, y1), reused from one call
 * to the next. The grid belongs to the page, not to the rectangle asked about, so any rectangle gets exactly the marks of the whole page
 * that reach it: tiles, thumbnails and exports agree.
 */
class PatternMarks {
    var dots = FloatArray(256)
        private set
    var dotCount = 0
        private set
    var lines = FloatArray(256)
        private set
    var lineCount = 0
        private set

    /** The mark thickness the marks were made for: a dot's diameter, a line's width. */
    var thickness = 0f
        private set

    /** Fills [dots] and [lines] with what reaches the rectangle. Marks may stick out of it by half their thickness, so none is missing at its edge. */
    fun make(pattern: FillPattern, left: Float, top: Float, right: Float, bottom: Float) {
        dotCount = 0
        lineCount = 0
        val s = pattern.spacing.coerceIn(FillPattern.MIN_SPACING, FillPattern.MAX_SPACING)
        val w = pattern.weight.coerceIn(FillPattern.MIN_WEIGHT, FillPattern.MAX_WEIGHT)
        thickness = if (pattern.kind == FillPatternKind.DOTS) 2f * w * s else w * s
        val reach = thickness / 2f
        val l = left - reach
        val t = top - reach
        val r = right + reach
        val b = bottom + reach
        if (r < l || b < t) return
        when (pattern.kind) {
            FillPatternKind.SOLID -> Unit
            FillPatternKind.DOTS -> dotGrid(s, l, t, r, b)
            FillPatternKind.LINES -> horizontal(s, l, t, r, b)
            FillPatternKind.DIAGONAL -> diagonal(s, l, t, r, b, falling = false)
            FillPatternKind.CROSSHATCH -> {
                diagonal(s, l, t, r, b, falling = false)
                diagonal(s, l, t, r, b, falling = true)
            }
        }
    }

    /** Rows half a spacing apart, every other one shifted half a spacing: the 45 degree screen of a halftone. */
    private fun dotGrid(s: Float, l: Float, t: Float, r: Float, b: Float) {
        val half = s / 2f
        var row = Math.floor((t / half).toDouble()).toInt()
        while (row * half <= b) {
            val y = row * half
            val shift = if (row and 1 == 0) 0f else half
            var column = Math.ceil(((l - shift) / s).toDouble()).toInt()
            while (column * s + shift <= r) {
                addDot(column * s + shift, y)
                column++
            }
            row++
        }
    }

    private fun horizontal(s: Float, l: Float, t: Float, r: Float, b: Float) {
        var row = Math.ceil((t / s).toDouble()).toInt()
        while (row * s <= b) {
            addLine(l, row * s, r, row * s)
            row++
        }
    }

    /** Lines a perpendicular [s] apart: x + y constant going up to the right, x - y constant going down to the right. */
    private fun diagonal(s: Float, l: Float, t: Float, r: Float, b: Float, falling: Boolean) {
        val step = s * ROOT_TWO
        if (!falling) {
            var k = Math.ceil(((l + t) / step).toDouble()).toInt()
            while (k * step <= r + b) {
                val c = k * step
                val from = maxOf(l, c - b)
                val to = minOf(r, c - t)
                if (from <= to) addLine(from, c - from, to, c - to)
                k++
            }
        } else {
            var k = Math.ceil(((l - b) / step).toDouble()).toInt()
            while (k * step <= r - t) {
                val c = k * step
                val from = maxOf(l, c + t)
                val to = minOf(r, c + b)
                if (from <= to) addLine(from, from - c, to, to - c)
                k++
            }
        }
    }

    private fun addDot(x: Float, y: Float) {
        if (dotCount + 2 > dots.size) dots = dots.copyOf(dots.size * 2)
        dots[dotCount++] = x
        dots[dotCount++] = y
    }

    private fun addLine(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (lineCount + 4 > lines.size) lines = lines.copyOf(lines.size * 2)
        lines[lineCount++] = x0
        lines[lineCount++] = y0
        lines[lineCount++] = x1
        lines[lineCount++] = y1
    }

    private companion object {
        const val ROOT_TWO = 1.4142135f
    }
}
