package app.betternotes.core.template

import kotlin.math.PI
import kotlin.math.hypot

/** Passes everything on to [inner] with its colors swapped by [recolor]; a color not in the map is left as it is. */
internal class RecolorSink(private val inner: TemplateSink, private val recolor: Map<Int, Int>) : TemplateSink {
    private fun c(color: Int) = recolor[color] ?: color

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) = inner.line(x0, y0, x1, y1, width, c(color))

    override fun dot(x: Float, y: Float, radius: Float, color: Int) = inner.dot(x, y, radius, c(color))

    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) = inner.fill(x, y, width, height, c(color))

    override fun polyline(points: FloatArray, width: Float, color: Int) = inner.polyline(points, width, c(color))

    override fun gradient(x: Float, y: Float, width: Float, height: Float, top: Int, bottom: Int) =
        inner.gradient(x, y, width, height, c(top), c(bottom))

    override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) =
        inner.text(text, x, baseline, size, c(color), style, align)

    override val language: String get() = inner.language
}

/** Adds up how much of the cover each color covers, so the main colors can be listed. */
private class PaletteSink : TemplateSink {
    val weight = LinkedHashMap<Int, Float>()

    private fun add(color: Int, amount: Float) {
        weight[color] = (weight[color] ?: 0f) + amount
    }

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) =
        add(color, hypot(x1 - x0, y1 - y0) * width)

    override fun polyline(points: FloatArray, width: Float, color: Int) {
        for (i in 0 until points.size / 2 - 1) line(points[2 * i], points[2 * i + 1], points[2 * i + 2], points[2 * i + 3], width, color)
    }

    override fun dot(x: Float, y: Float, radius: Float, color: Int) = add(color, (PI * radius * radius).toFloat())

    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) = add(color, width * height)

    override fun gradient(x: Float, y: Float, width: Float, height: Float, top: Int, bottom: Int) {
        add(top, width * height / 2f)
        add(bottom, width * height / 2f)
    }

    override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) =
        add(color, size * size * text.length)
}

/**
 * The main colors of the design, the ones covering the most first, at most [max]. A blend counts as its two ends. These are what the
 * owner can change, with [CoverDesign.draw] and `recolor`.
 */
fun CoverDesign.palette(max: Int = 6): List<Int> {
    val sink = PaletteSink()
    draw(sink, PALETTE_WIDTH, PALETTE_HEIGHT)
    return sink.weight.entries.sortedByDescending { it.value }.take(max).map { it.key }
}

private const val PALETTE_WIDTH = 595f
private const val PALETTE_HEIGHT = 842f
