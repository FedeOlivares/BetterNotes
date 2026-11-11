package app.betternotes.core.handfont

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds a TrueType font file from what was drawn. Every stroke becomes a pen's width of ink: a rectangle along each
 * segment and a round disc at each point, overlapping, which the font renderer fills as one shape. The file holds the
 * strokes too, in a table of its own that other software ignores, so the font can be opened and edited again.
 *
 * A glyph that was not drawn is left out of the font, so text in it shows in the phone's default font. A space is
 * always there, as wide as [SPACE_ADVANCE].
 */
object TrueTypeWriter {
    const val UNITS = 1000
    const val ASCENT = 800
    const val DESCENT = 250
    const val SPACE_ADVANCE = 330
    private const val SIDE = 30
    const val DATA_TABLE = "BNhf"

    fun write(font: HandFont): ByteArray {
        val glyphs = glyphsOf(font)
        val (glyf, loca) = glyfAndLoca(glyphs)
        val tables = sortedMapOf(
            DATA_TABLE to HandFontData.encode(font),
            "OS/2" to os2(glyphs),
            "cmap" to cmap(glyphs),
            "glyf" to glyf,
            "head" to head(glyphs),
            "hhea" to hhea(glyphs),
            "hmtx" to hmtx(glyphs),
            "loca" to loca,
            "maxp" to maxp(glyphs),
            "name" to names(font.name),
            "post" to post(),
        )
        return assemble(tables)
    }

    /** The strokes held in [bytes], a font file this writer made, or null when it holds none. [name] is what the font is called. */
    fun read(name: String, bytes: ByteArray): HandFont? {
        return try {
            val buffer = ByteBuffer.wrap(bytes)
            val count = buffer.getShort(4).toInt() and 0xFFFF
            for (i in 0 until count) {
                val entry = 12 + 16 * i
                if (String(bytes, entry, 4, Charsets.ISO_8859_1) == DATA_TABLE) {
                    val offset = buffer.getInt(entry + 8)
                    val length = buffer.getInt(entry + 12)
                    return HandFontData.decode(name, bytes.copyOfRange(offset, offset + length))
                }
            }
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        } catch (_: java.nio.BufferUnderflowException) {
            null
        }
    }

    private class Contour(val x: IntArray, val y: IntArray, val on: BooleanArray)

    private class Glyph(val code: Int, val advance: Int, val contours: List<Contour>) {
        val points = contours.sumOf { it.x.size }
        val xMin = contours.minOfOrNull { it.x.min() } ?: 0
        val xMax = contours.maxOfOrNull { it.x.max() } ?: 0
        val yMin = contours.minOfOrNull { it.y.min() } ?: 0
        val yMax = contours.maxOfOrNull { it.y.max() } ?: 0
    }

    /** Glyph 0 is the box shown for anything unknown; then the glyphs in code order, so a run of codes is a run of glyphs. */
    private fun glyphsOf(font: HandFont): List<Glyph> {
        val drawn = font.glyphs.filter { !it.value.isEmpty && it.key != ' ' }.map { (char, glyph) -> outline(char.code, glyph) }
        val space = Glyph(' '.code, SPACE_ADVANCE, emptyList())
        val notdef = Glyph(-1, 600, listOf(box(60, 0, 540, 700)))
        return listOf(notdef) + (drawn + space).sortedBy { it.code }
    }

    private fun box(x0: Int, y0: Int, x1: Int, y1: Int) =
        Contour(intArrayOf(x0, x0, x1, x1), intArrayOf(y0, y1, y1, y0), BooleanArray(4) { true })

    private fun outline(code: Int, glyph: HandGlyph): Glyph {
        val r = GlyphSpace.PEN / 2
        val strokes = glyph.strokes.filter { it.isNotEmpty() }.map { clampToSpace(it) }
        val minX = strokes.minOf { s -> (0 until s.size / 2).minOf { s[2 * it] } }
        val maxX = strokes.maxOf { s -> (0 until s.size / 2).maxOf { s[2 * it] } }
        val shift = SIDE + r - minX
        val contours = ArrayList<Contour>()
        for (stroke in strokes) {
            val n = stroke.size / 2
            val px = FloatArray(n) { stroke[2 * it] + shift }
            val py = FloatArray(n) { GlyphSpace.BASELINE - stroke[2 * it + 1] }
            for (i in 0 until n) contours.add(disc(px[i], py[i], r))
            for (i in 0 until n - 1) segment(px[i], py[i], px[i + 1], py[i + 1], r)?.let(contours::add)
        }
        return Glyph(code, (maxX - minX + 2 * r + 2 * SIDE).roundToInt(), contours)
    }

    /** A circle of eight off-curve points, which TrueType rounds into a smooth outline. Clockwise, as an outer shape is. */
    private fun disc(cx: Float, cy: Float, r: Float): Contour {
        val reach = r / cos(PI / 8).toFloat()
        val x = IntArray(8)
        val y = IntArray(8)
        for (i in 0 until 8) {
            val angle = -(2 * PI * i / 8 + PI / 8)
            x[i] = (cx + reach * cos(angle)).roundToInt()
            y[i] = (cy + reach * sin(angle)).roundToInt()
        }
        return Contour(x, y, BooleanArray(8))
    }

    private fun segment(x0: Float, y0: Float, x1: Float, y1: Float, r: Float): Contour? {
        val length = hypot(x1 - x0, y1 - y0)
        if (length < 1f) return null
        val nx = -(y1 - y0) / length * r
        val ny = (x1 - x0) / length * r
        // Clockwise in a y-up system: out along one side, back along the other.
        val x = intArrayOf((x0 - nx).roundToInt(), (x1 - nx).roundToInt(), (x1 + nx).roundToInt(), (x0 + nx).roundToInt())
        val y = intArrayOf((y0 - ny).roundToInt(), (y1 - ny).roundToInt(), (y1 + ny).roundToInt(), (y0 + ny).roundToInt())
        return Contour(x, y, BooleanArray(4) { true })
    }

    private fun glyfAndLoca(glyphs: List<Glyph>): Pair<ByteArray, ByteArray> {
        val glyfBytes = ByteArrayOutputStream()
        val glyf = DataOutputStream(glyfBytes)
        val locaBytes = ByteArrayOutputStream()
        val loca = DataOutputStream(locaBytes)
        for (glyph in glyphs) {
            loca.writeInt(glyfBytes.size())
            if (glyph.contours.isEmpty()) continue
            glyf.writeShort(glyph.contours.size)
            glyf.writeShort(glyph.xMin)
            glyf.writeShort(glyph.yMin)
            glyf.writeShort(glyph.xMax)
            glyf.writeShort(glyph.yMax)
            var end = -1
            for (c in glyph.contours) {
                end += c.x.size
                glyf.writeShort(end)
            }
            glyf.writeShort(0)
            var first = true
            for (c in glyph.contours) for (i in c.x.indices) {
                // Bit 0: on the curve. Bit 6: contours overlap, which they do here.
                glyf.writeByte((if (c.on[i]) 1 else 0) or (if (first) 0x40 else 0))
                first = false
            }
            var previous = 0
            for (c in glyph.contours) for (v in c.x) {
                glyf.writeShort(v - previous)
                previous = v
            }
            previous = 0
            for (c in glyph.contours) for (v in c.y) {
                glyf.writeShort(v - previous)
                previous = v
            }
            while (glyfBytes.size() % 4 != 0) glyf.writeByte(0)
        }
        loca.writeInt(glyfBytes.size())
        return glyfBytes.toByteArray() to locaBytes.toByteArray()
    }

    private fun head(glyphs: List<Glyph>): ByteArray = table {
        writeInt(0x00010000)
        writeInt(0x00010000)
        writeInt(0) // The file's checksum adjustment, filled in once the file is whole.
        writeInt(0x5F0F3CF5)
        writeShort(3)
        writeShort(UNITS)
        writeLong(0)
        writeLong(0)
        writeShort(glyphs.minOf { it.xMin })
        writeShort(glyphs.minOf { it.yMin })
        writeShort(glyphs.maxOf { it.xMax })
        writeShort(glyphs.maxOf { it.yMax })
        writeShort(0)
        writeShort(8)
        writeShort(2)
        writeShort(1)
        writeShort(0)
    }

    private fun hhea(glyphs: List<Glyph>): ByteArray = table {
        writeInt(0x00010000)
        writeShort(ASCENT)
        writeShort(-DESCENT)
        writeShort(0)
        writeShort(glyphs.maxOf { it.advance })
        writeShort(glyphs.minOf { it.xMin })
        writeShort(glyphs.minOf { it.advance - it.xMax })
        writeShort(glyphs.maxOf { it.xMax })
        writeShort(1)
        writeShort(0)
        writeShort(0)
        repeat(4) { writeShort(0) }
        writeShort(0)
        writeShort(glyphs.size)
    }

    private fun hmtx(glyphs: List<Glyph>): ByteArray = table {
        for (g in glyphs) {
            writeShort(g.advance)
            writeShort(g.xMin)
        }
    }

    private fun maxp(glyphs: List<Glyph>): ByteArray = table {
        writeInt(0x00010000)
        writeShort(glyphs.size)
        writeShort(glyphs.maxOf { it.points })
        writeShort(glyphs.maxOf { it.contours.size })
        writeShort(0)
        writeShort(0)
        writeShort(1)
        repeat(8) { writeShort(0) }
    }

    /** Format 4, the one every renderer reads. A run of codes that are glyphs one after the other is one segment. */
    private fun cmap(glyphs: List<Glyph>): ByteArray {
        val runs = ArrayList<IntArray>() // first code, last code, first glyph
        for ((index, g) in glyphs.withIndex()) {
            if (g.code < 0) continue
            val last = runs.lastOrNull()
            if (last != null && last[1] + 1 == g.code) last[1] = g.code else runs.add(intArrayOf(g.code, g.code, index))
        }
        runs.add(intArrayOf(0xFFFF, 0xFFFF, 0))
        val segments = runs.size
        var power = 1
        var log = 0
        while (power * 2 <= segments) {
            power *= 2
            log++
        }
        val subtable = table {
            writeShort(4)
            writeShort(16 + 8 * segments)
            writeShort(0)
            writeShort(segments * 2)
            writeShort(power * 2)
            writeShort(log)
            writeShort(segments * 2 - power * 2)
            for (r in runs) writeShort(r[1])
            writeShort(0)
            for (r in runs) writeShort(r[0])
            for (r in runs) writeShort(if (r[0] == 0xFFFF) 1 else r[2] - r[0])
            repeat(segments) { writeShort(0) }
        }
        return table {
            writeShort(0)
            writeShort(1)
            writeShort(3)
            writeShort(1)
            writeInt(12)
            write(subtable)
        }
    }

    private fun names(name: String): ByteArray {
        val ascii = name.filter { it.code in 33..126 && it !in "[](){}<>/%" }.ifEmpty { "HandFont" }
        val entries = listOf(1 to name, 2 to "Regular", 3 to "BetterNotes $ascii", 4 to name, 5 to "Version 1.0", 6 to ascii)
        val strings = entries.map { it.second.toByteArray(Charsets.UTF_16BE) }
        return table {
            writeShort(0)
            writeShort(entries.size)
            writeShort(6 + 12 * entries.size)
            var offset = 0
            for ((i, e) in entries.withIndex()) {
                writeShort(3)
                writeShort(1)
                writeShort(0x409)
                writeShort(e.first)
                writeShort(strings[i].size)
                writeShort(offset)
                offset += strings[i].size
            }
            for (s in strings) write(s)
        }
    }

    private fun os2(glyphs: List<Glyph>): ByteArray {
        val codes = glyphs.map { it.code }.filter { it >= 0 }
        val average = glyphs.drop(1).map { it.advance }.average().roundToInt()
        return table {
            writeShort(4)
            writeShort(average)
            writeShort(400)
            writeShort(5)
            writeShort(0)
            for (v in listOf(650, 600, 0, 75, 650, 600, 0, 350, 50, 300)) writeShort(v)
            writeShort(0)
            repeat(10) { writeByte(0) }
            writeInt(1)
            writeInt(0)
            writeInt(0)
            writeInt(0)
            write("BNHF".toByteArray(Charsets.US_ASCII))
            writeShort(0x40 or 0x80)
            writeShort(codes.min().coerceAtMost(0xFFFF))
            writeShort(codes.max().coerceAtMost(0xFFFF))
            writeShort(ASCENT)
            writeShort(-DESCENT)
            writeShort(0)
            writeShort(ASCENT)
            writeShort(DESCENT)
            writeInt(1)
            writeInt(0)
            writeShort((GlyphSpace.BASELINE - GlyphSpace.X_LINE).toInt())
            writeShort((GlyphSpace.BASELINE - GlyphSpace.CAP_LINE).toInt())
            writeShort(0)
            writeShort(' '.code)
            writeShort(1)
        }
    }

    private fun post(): ByteArray = table {
        writeInt(0x00030000)
        writeInt(0)
        writeShort(-100)
        writeShort(50)
        repeat(5) { writeInt(0) }
    }

    private fun assemble(tables: Map<String, ByteArray>): ByteArray {
        val count = tables.size
        var power = 1
        var log = 0
        while (power * 2 <= count) {
            power *= 2
            log++
        }
        val out = ByteBuffer.allocate(12 + 16 * count + tables.values.sumOf { (it.size + 3) / 4 * 4 })
        out.putInt(0x00010000)
        out.putShort(count.toShort())
        out.putShort((power * 16).toShort())
        out.putShort(log.toShort())
        out.putShort((count * 16 - power * 16).toShort())
        var offset = 12 + 16 * count
        var headOffset = 0
        for ((tag, data) in tables) {
            out.put(tag.toByteArray(Charsets.ISO_8859_1))
            out.putInt(checksum(data))
            out.putInt(offset)
            out.putInt(data.size)
            if (tag == "head") headOffset = offset
            offset += (data.size + 3) / 4 * 4
        }
        for (data in tables.values) {
            out.put(data)
            repeat((4 - data.size % 4) % 4) { out.put(0) }
        }
        val bytes = out.array()
        // The head table's own checksum was taken with its adjustment as zero; the adjustment makes the whole file sum to a fixed value.
        out.putInt(headOffset + 8, (0xB1B0AFBAL - (checksum(bytes).toLong() and 0xFFFFFFFFL)).toInt())
        return bytes
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < data.size) {
            var word = 0
            for (k in 0 until 4) word = (word shl 8) or (if (i + k < data.size) data[i + k].toInt() and 0xFF else 0)
            sum += word
            i += 4
        }
        return sum
    }

    private inline fun table(body: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply(body)
        return bytes.toByteArray()
    }
}
