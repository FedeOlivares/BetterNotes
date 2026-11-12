package app.betternotes.core.template

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CoverGroup { SIMPLE, SOLID, ENCLOSED, BLACK_AND_WHITE, BROWN, PLAIN, BRIGHT, CUSTOM }

/**
 * A notebook cover, drawn through a [TemplateSink] at the size of the cover page, so the editor, the previews and
 * the home tiles share one drawing. The designs are redrawn from the reference, not copied from it.
 * [id] is what the document file keeps.
 */
class CoverDesign(val id: String, val group: CoverGroup, val name: String, internal val paint: CoverPainter.() -> Unit) {
    fun draw(sink: TemplateSink, width: Float, height: Float) = CoverPainter(sink, width, height).paint()

    /** Draws it with some of its colors swapped: each key of [recolor] is replaced by its value. */
    fun draw(sink: TemplateSink, width: Float, height: Float, recolor: Map<Int, Int>) =
        draw(if (recolor.isEmpty()) sink else RecolorSink(sink, recolor), width, height)

    /** Draws the cover of a page whose paper pattern is [pattern]: a planner's cover has its title and years printed over the design. */
    fun draw(sink: TemplateSink, size: PageSize, pattern: PageTemplate) {
        draw(sink, size.widthPt, size.heightPt, if (pattern is PageTemplate.Planner) app.betternotes.core.planner.PlannerColors.coverRecolor(pattern.colors) else emptyMap())
        if (pattern is PageTemplate.Planner) pattern.draw(size, Bounds(0f, 0f, size.widthPt, size.heightPt), sink)
    }

    companion object {
        /** Cover pages are drawn this wide in points, and sizes scale to it, so a design looks the same on every page size. */
        fun byId(id: String?): CoverDesign? = if (id == null) null else ALL.firstOrNull { it.id == id } ?: PLANNER.takeIf { it.id == id }

        /** The ones shown at the top of the chooser, under "Common". */
        val COMMON: List<CoverDesign> by lazy { listOf(CUSTOM_ID, "simple-allblack", "simple-black", "solid-babyblue").mapNotNull(::byId) }

        /** A blank cover, white, drawn entirely by the owner with shapes, lines and fills. Every design can also be turned into this one. */
        const val CUSTOM_ID = "custom"

        val ALL: List<CoverDesign> by lazy { buildList { add(custom); addAll(simple); addAll(solid); addAll(enclosed); addAll(blackAndWhite); addAll(brown); addAll(plain); addAll(bright); addAll(moreCovers) } }

        /** A planner's cover. It is not in the chooser: a planner always has it, and draws its title and years over it. */
        val PLANNER = CoverDesign("planner", CoverGroup.PLAIN, "Planner") {
            background(0xFF27324F.toInt())
            rect(0f, 0f, 7 * u, height, 0xFF1A2240.toInt())
            rect(width - 14 * u, 0f, 1.2f * u, height, 0xFFE2C84A.toInt())
        }

        /** The default cover, the first of the simple ones, as in the reference. */
        const val DEFAULT_ID = "simple"
    }
}

/** Drawing helpers. Everything is relative to the page, so [u] is one hundredth of its width. */
class CoverPainter internal constructor(val sink: TemplateSink, val width: Float, val height: Float) {
    val u = width / 100f

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) = sink.fill(x, y, w, h, color)

    fun background(color: Int) = rect(0f, 0f, width, height, color)

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int) = sink.line(x0, y0, x1, y1, w, color)

    fun dot(x: Float, y: Float, r: Float, color: Int) = sink.dot(x, y, r, color)

    /** A vertical blend from [top] to [bottom] over the box, in bands too thin to see. */
    fun gradient(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int) = sink.gradient(x, y, w, h, top, bottom)

    /** The small ruled mark and pen at the bottom right of the plain covers. */
    fun penMark(color: Int, x: Float = width * 0.62f, y: Float = height * 0.91f) {
        line(x, y, x + 18 * u, y, 0.35f * u, color)
        line(x + 18 * u, y, x + 19.5f * u, y - 1.8f * u, 0.5f * u, color)
    }

    /** A wavy line across the page, as joined short segments. */
    fun wave(y: Float, amplitude: Float, period: Float, phase: Float, w: Float, color: Int, x0: Float = 0f, x1: Float = width) {
        val points = ArrayList<Float>()
        var x = x0
        points += x
        points += y + amplitude * sin(phase)
        val step = u
        while (x < x1) {
            val nx = minOf(x + step, x1)
            points += nx
            points += y + amplitude * sin((nx - x0) / period * 2f * PI.toFloat() + phase)
            x = nx
        }
        if (points.size >= 4) sink.polyline(points.toFloatArray(), w, color)
    }
}

internal fun mix(a: Int, b: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val from = (a ushr shift) and 0xFF
        val to = (b ushr shift) and 0xFF
        return (from + (to - from) * t).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private val custom = CoverDesign(CoverDesign.CUSTOM_ID, CoverGroup.CUSTOM, "Custom") { background(0xFFFFFFFF.toInt()) }

private const val KRAFT = 0xFFB09E85.toInt()
private const val KRAFT_LINE = 0xFF8C7E69.toInt()

private fun kraft(id: String, name: String, spine: Int) = CoverDesign(id, CoverGroup.SIMPLE, name) {
    background(KRAFT)
    rect(0f, 0f, 7 * u, height, spine)
    line(18 * u, height * 0.27f, width - 8 * u, height * 0.27f, 0.3f * u, KRAFT_LINE)
    penMark(KRAFT_LINE)
}

private val simple = listOf(
    kraft("simple", "Simple", 0xFFF8F8F8.toInt()),
    kraft("simple-yellow", "Simple", 0xFFFFFF6A.toInt()),
    CoverDesign("simple-allblack", CoverGroup.SIMPLE, "Simple Allblack") {
        background(0xFF363A43.toInt())
        rect(0f, 0f, 7 * u, height, 0xFF000000.toInt())
        line(18 * u, height * 0.27f, width - 8 * u, height * 0.27f, 0.3f * u, 0xFF70757F.toInt())
        penMark(0xFF70757F.toInt())
    },
    kraft("simple-black", "Simple Black", 0xFF363A43.toInt()),
    kraft("simple-blue", "Simple Blue", 0xFF7DB1FF.toInt()),
    kraft("simple-green", "Simple Green", 0xFF7BA76B.toInt()),
    kraft("simple-pink", "Simple Pink", 0xFFF8CFCF.toInt()),
    kraft("simple-red", "Simple Red", 0xFFB93B38.toInt()),
)

private fun solid(id: String, name: String, color: Int, band: Int, thin: Int = band) =
    CoverDesign(id, CoverGroup.SOLID, name) {
        background(color)
        rect(width - 14 * u, 0f, 2.4f * u, height, band)
        rect(width - 11.6f * u, 0f, 1.2f * u, height, thin)
    }

private val solid = listOf(
    solid("solid-babyblue", "Solid Babyblue", 0xFFA3C9F0.toInt(), 0xFF0A7BFF.toInt(), 0xFF7DB1FF.toInt()),
    solid("solid-blue", "Solid Blue", 0xFF104A87.toInt(), 0xFF7DB1FF.toInt(), 0xFF104A87.toInt()),
    solid("solid-green", "Solid Green", 0xFF7BA76B.toInt(), 0xFF9BD14C.toInt()),
    solid("solid-gray", "Solid Gray", 0xFF363A43.toInt(), 0xFF8CCB3C.toInt(), 0xFF363A43.toInt()),
    solid("solid-pink", "Solid Pink", 0xFFF8D9CF.toInt(), 0xFFE55F5A.toInt(), 0xFFF3C7C0.toInt()),
    solid("solid-red", "Solid Red", 0xFFC76B6B.toInt(), 0xFFF09A6A.toInt(), 0xFFC76B6B.toInt()),
    solid("solid-teal", "Solid Teal", 0xFF8EC4B5.toInt(), 0xFF5A9EA6.toInt(), 0xFF8EC4B5.toInt()),
    solid("solid-white", "Solid White", 0xFFFFFFFF.toInt(), 0xFFE2C84A.toInt(), 0xFFFFFFFF.toInt()),
    solid("solid-yellow", "Solid Yellow", 0xFFE3C849.toInt(), 0xFFFFFFFF.toInt(), 0xFFE3C849.toInt()),
)

private fun enclosed(n: Int, top: Int, bottom: Int) = CoverDesign("enclosed-$n", CoverGroup.ENCLOSED, "Enclosed $n") {
    background(0xFFFAFAFA.toInt())
    gradient(0f, height * 0.2f, width, height * 0.8f, top, bottom)
    penMark(0xFF6F6F6F.toInt(), x = 8 * u, y = height * 0.15f)
}

private val enclosed = listOf(
    enclosed(1, 0xFF58585C.toInt(), 0xFF6A676C.toInt()),
    enclosed(2, 0xFF9A5250.toInt(), 0xFF5B4A54.toInt()),
    enclosed(3, 0xFF6A80C4.toInt(), 0xFF4A586E.toInt()),
    enclosed(4, 0xFF84927A.toInt(), 0xFF50664C.toInt()),
    enclosed(5, 0xFFC3B6AC.toInt(), 0xFF9A9A9A.toInt()),
)

private val blackAndWhite = listOf(
    CoverDesign("circle-and-dots", CoverGroup.BLACK_AND_WHITE, "Circle And Dots") {
        background(0xFF0A0A0A.toInt())
        val white = 0xFFFFFFFF.toInt()
        val cx = width * 0.46f
        // A disc of dots above and a disc of diagonal stripes below.
        val r = 17 * u
        val cy = height * 0.18f
        val step = 2.6f * u
        var y = cy - r
        while (y <= cy + r) {
            var x = cx - r
            while (x <= cx + r) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) dot(x, y, 0.5f * u, white)
                x += step
            }
            y += step
        }
        val cy2 = height * 0.82f
        var k = -r
        while (k <= r) {
            val half = kotlin.math.sqrt(r * r - k * k)
            line(cx - half, cy2 + k, cx + half, cy2 + k, 0.9f * u, white)
            k += 2.2f * u
        }
    },
    CoverDesign("geometric", CoverGroup.BLACK_AND_WHITE, "Geometric") {
        background(0xFFF7F7F7.toInt())
        val ink = 0xFF2B2B2B.toInt()
        val y0 = height * 0.62f
        for (i in 0 until 16) {
            val t = i * 2.2f * u
            line(0f, y0 + t, width * 0.3f, y0 + t * 0.9f, 0.12f * u, ink)
            line(width * 0.3f, y0 + t * 0.9f, width, y0 - width * 0.3f + t * 0.9f, 0.12f * u, ink)
        }
    },
    CoverDesign("line-waves", CoverGroup.BLACK_AND_WHITE, "Line Waves") {
        background(0xFFFFFFFF.toInt())
        for (i in 0 until 14) wave(height * 0.64f + i * 2.8f * u, 1.6f * u, 12f * u, i * 0.7f, 0.25f * u, 0xFF1A1A1A.toInt())
    },
    CoverDesign("line-waves-black", CoverGroup.BLACK_AND_WHITE, "Line Waves Black") {
        background(0xFF0A0A0A.toInt())
        for (i in 0 until 14) wave(height * 0.64f + i * 2.8f * u, 1.6f * u, 12f * u, i * 0.7f, 0.25f * u, 0xFFF0F0F0.toInt())
    },
)

private val brownGround = 0xFFC4B497.toInt()

private val brown = listOf(
    CoverDesign("brown-dots", CoverGroup.BROWN, "Dots") {
        background(0xFFC0B8A0.toInt())
        for (row in 0 until 14) {
            for (col in 0 until 9) {
                val x = (col + (row % 2) * 0.5f) * 11.5f * u + 4 * u
                val y = row * 7.5f * u + 4 * u
                dot(x, y, 0.9f * u, if ((row + col) % 2 == 0) 0xFF2E2E2E.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    },
    CoverDesign("brown-sticks", CoverGroup.BROWN, "Sticks") {
        background(brownGround)
        val white = 0xFFFFFFFF.toInt()
        for (i in 0 until 26) {
            val x = ((i * 37) % 100) * u
            val y = ((i * 53) % 130) * u * (height / width) / 1.3f
            val a = (i * 47 % 180) * PI.toFloat() / 180f
            line(x, y, x + 7 * u * cos(a), y + 7 * u * sin(a), 1.3f * u, white)
        }
    },
    CoverDesign("brown-waves", CoverGroup.BROWN, "Waves") {
        background(brownGround)
        for (i in 0 until 8) wave(height * (0.06f + i * 0.13f), 5 * u, 36 * u, i * 0.5f, 1.8f * u, 0xFFFFFFFF.toInt())
    },
    CoverDesign("brown-zigzag", CoverGroup.BROWN, "Zigzag") {
        background(0xFF9A8B6B.toInt())
        val light = brownGround
        var row = 0
        var y = 0f
        while (y < height) {
            var x = -5 * u
            while (x < width) {
                // A row of triangles, as stacked strips that narrow toward the point.
                val base = 10 * u
                val steps = 8
                for (s in 0 until steps) {
                    val span = base * (1f - s / steps.toFloat())
                    rect(x + (base - span) / 2f, y + s * 1.1f * u, span, 1.1f * u, if (row % 2 == 0) light else 0xFF9A8B6B.toInt())
                }
                x += base
            }
            y += 9.4f * u
            row++
        }
    },
)

private fun plain(id: String, name: String, base: Int, mid: Int, light: Int) = CoverDesign(id, CoverGroup.PLAIN, name) {
    background(base)
    rect(width * 0.2f, height * 0.34f, width * 0.8f, height * 0.66f, mid)
    rect(width * 0.3f, height * 0.4f, width * 0.7f, height * 0.6f, light)
    penMark(0xFF6F6F6F.toInt())
}

private val plain = listOf(
    plain("plain-white", "Plain White", 0xFFFFFFFF.toInt(), 0xFFEDEDED.toInt(), 0xFFF5F5F5.toInt()),
    plain("plain-sand", "Plain Sand", 0xFFE2D3BF.toInt(), 0xFFEAD8CB.toInt(), 0xFFEEE3D9.toInt()),
    plain("plain-charcoal", "Plain Charcoal", 0xFF59574F.toInt(), 0xFF7A7872.toInt(), 0xFF8E8D88.toInt()),
    plain("plain-sage", "Plain Sage", 0xFFA8B3A3.toInt(), 0xFFB5C0B3.toInt(), 0xFFBBC4B9.toInt()),
    plain("plain-gray", "Plain Gray", 0xFFC9C9C9.toInt(), 0xFFD6D6D6.toInt(), 0xFFE0E0E0.toInt()),
)

private val bright = listOf(
    CoverDesign("koi", CoverGroup.BRIGHT, "Koi") {
        background(0xFFEEEEEA.toInt())
        val colors = listOf(0xFF2B5797, 0xFF579AC9, 0xFFFFF5CB, 0xFFF3BD00, 0xFFF07800, 0xFFF02A00).map { it.toInt() }
        // Rows of overlapping scales, the top row first so each lower row covers it.
        for (row in 0 until 8) {
            val y = height * 0.3f + row * 13f * u
            var x = if (row % 2 == 0) 0f else -10 * u
            while (x < width + 10 * u) {
                dot(x, y, 12 * u, colors[minOf(row, colors.lastIndex)])
                x += 20 * u
            }
        }
    },
)
