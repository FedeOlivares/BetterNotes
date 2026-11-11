package app.betternotes.core.handfont

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The space a letter is drawn in: a square [SIZE] units on a side, with the top at the ascender and the bottom at the
 * descender. The guide lines inside it are where capitals, small letters and the baseline go. Ink stays inside it, so a
 * letter never runs into the line above or below.
 */
object GlyphSpace {
    const val SIZE = 1000f
    const val BASELINE = 750f
    const val CAP_LINE = 50f
    const val X_LINE = 270f

    /** The width of the pen, in the same units. A thin dot of ink is a disc of this size. */
    const val PEN = 50f
}

/** A letter as drawn: one point list per stroke, as x, y pairs in [GlyphSpace], origin at the top left. */
class HandGlyph(val strokes: List<FloatArray>) {
    val isEmpty get() = strokes.none { it.isNotEmpty() }

    override fun equals(other: Any?) = other is HandGlyph && strokes.size == other.strokes.size &&
        strokes.indices.all { strokes[it].contentEquals(other.strokes[it]) }

    override fun hashCode() = strokes.fold(1) { h, s -> 31 * h + s.contentHashCode() }
}

/** The glyphs a font can hold, in the order they are offered. Anything left undrawn is shown in a default font instead. */
object HandCharset {
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"
    const val PUNCTUATION = ".,:;!?'\"-()/&@#%+=*_"
    const val SPANISH = "¿¡ñÑáéíóúÁÉÍÓÚü"
    val SECTIONS = listOf(LOWER, UPPER, DIGITS, PUNCTUATION, SPANISH)
    val ALL: String = SECTIONS.joinToString("")
}

/** A font being made: the name it is saved under and what was drawn for each letter. */
class HandFont(val name: String, val glyphs: Map<Char, HandGlyph>)

/** Thins out a stroke that was sampled from a moving pen, keeping its shape to within [tolerance] units. */
fun simplifyStroke(points: FloatArray, tolerance: Float = 2f): FloatArray {
    val n = points.size / 2
    if (n <= 2) return points.copyOf()
    val keep = BooleanArray(n)
    keep[0] = true
    keep[n - 1] = true
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, n - 1))
    while (stack.isNotEmpty()) {
        val (a, b) = stack.removeLast()
        var far = -1
        var farDistance = tolerance
        for (i in a + 1 until b) {
            val d = distanceToSegment(points, i, a, b)
            if (d > farDistance) {
                far = i
                farDistance = d
            }
        }
        if (far >= 0) {
            keep[far] = true
            stack.addLast(intArrayOf(a, far))
            stack.addLast(intArrayOf(far, b))
        }
    }
    val out = ArrayList<Float>()
    for (i in 0 until n) if (keep[i]) {
        out.add(points[2 * i])
        out.add(points[2 * i + 1])
    }
    return out.toFloatArray()
}

private fun distanceToSegment(p: FloatArray, i: Int, a: Int, b: Int): Float {
    val ax = p[2 * a]
    val ay = p[2 * a + 1]
    val dx = p[2 * b] - ax
    val dy = p[2 * b + 1] - ay
    val px = p[2 * i] - ax
    val py = p[2 * i + 1] - ay
    val length2 = dx * dx + dy * dy
    if (length2 < 1e-6f) return hypot(px, py)
    val t = ((px * dx + py * dy) / length2).coerceIn(0f, 1f)
    return hypot(px - t * dx, py - t * dy)
}

/** Keeps the stroke's centre far enough in that the pen stays inside the drawing space. */
fun clampToSpace(points: FloatArray): FloatArray {
    val low = GlyphSpace.PEN / 2
    val high = GlyphSpace.SIZE - low
    return FloatArray(points.size) { points[it].coerceIn(low, high) }
}

/** The bytes of the glyphs as kept inside the font file, so that it can be opened and edited again. */
internal object HandFontData {
    private const val VERSION = 1
    private const val SCALE = 4f

    fun encode(font: HandFont): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val data = java.io.DataOutputStream(out)
        data.writeByte(VERSION)
        val drawn = font.glyphs.filter { !it.value.isEmpty }.toSortedMap()
        data.writeShort(drawn.size)
        for ((char, glyph) in drawn) {
            data.writeChar(char.code)
            val strokes = glyph.strokes.filter { it.isNotEmpty() }
            data.writeShort(strokes.size)
            for (stroke in strokes) {
                data.writeShort(stroke.size / 2)
                for (v in stroke) data.writeShort((v * SCALE).roundToInt().coerceIn(0, 0xFFFF))
            }
        }
        return out.toByteArray()
    }

    fun decode(name: String, bytes: ByteArray): HandFont? = try {
        val data = java.io.DataInputStream(bytes.inputStream())
        if (data.readUnsignedByte() > VERSION) null else {
            val glyphs = HashMap<Char, HandGlyph>()
            repeat(data.readUnsignedShort()) {
                val char = data.readChar()
                val strokes = List(data.readUnsignedShort()) {
                    FloatArray(data.readUnsignedShort() * 2) { data.readUnsignedShort() / SCALE }
                }
                glyphs[char] = HandGlyph(strokes)
            }
            HandFont(name, glyphs)
        }
    } catch (_: java.io.IOException) {
        null
    }
}

