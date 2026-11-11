package app.betternotes.core.handfont

import java.awt.Font
import java.awt.font.FontRenderContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrueTypeWriterTest {
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y1)

    private val font = HandFont(
        "My Hand",
        mapOf(
            'l' to HandGlyph(listOf(line(300f, 50f, 300f, 750f))),
            'o' to HandGlyph(listOf(floatArrayOf(200f, 400f, 300f, 270f, 400f, 400f, 300f, 750f, 200f, 400f))),
            '.' to HandGlyph(listOf(floatArrayOf(500f, 740f))),
            'g' to HandGlyph(listOf(line(100f, 300f, 100f, 960f))),
            'x' to HandGlyph(emptyList()),
        ),
    )

    private fun load(f: HandFont = font): Font = Font.createFont(Font.TRUETYPE_FONT, TrueTypeWriter.write(f).inputStream())

    @Test
    fun `the file is a font that java can load, named as given`() {
        val loaded = load()
        assertEquals("My Hand", loaded.family)
    }

    @Test
    fun `drawn letters and the space are in the font, undrawn ones are not`() {
        val loaded = load()
        for (c in "lo.g ") assertTrue("has $c", loaded.canDisplay(c))
        for (c in "xa") assertFalse("lacks $c", loaded.canDisplay(c))
    }

    @Test
    fun `a letter's ink is where it was drawn, within the pen width`() {
        val sized = load().deriveFont(1000f)
        val context = FontRenderContext(null, true, true)
        val bounds = sized.createGlyphVector(context, "l").visualBounds
        val pen = GlyphSpace.PEN
        // A vertical line from the cap line to the baseline: 700 units tall plus a round cap at each end.
        assertEquals((700 + pen).toDouble(), bounds.height, 3.0)
        assertEquals(pen.toDouble(), bounds.width, 3.0)
        assertEquals((-(GlyphSpace.BASELINE - GlyphSpace.CAP_LINE) - pen / 2).toDouble(), bounds.y, 3.0)
    }

    @Test
    fun `descenders go below the baseline and ink stays inside the line`() {
        val sized = load().deriveFont(1000f)
        val context = FontRenderContext(null, true, true)
        val bounds = sized.createGlyphVector(context, "g").visualBounds
        assertTrue(bounds.maxY > 100)
        assertTrue(bounds.maxY <= TrueTypeWriter.DESCENT + 1)
        assertTrue(bounds.minY >= -TrueTypeWriter.ASCENT)
    }

    @Test
    fun `letters are spaced by their own width`() {
        val sized = load().deriveFont(1000f)
        val context = FontRenderContext(null, true, true)
        val l = sized.createGlyphVector(context, "l").logicalBounds.width
        val o = sized.createGlyphVector(context, "o").logicalBounds.width
        val space = sized.createGlyphVector(context, " ").logicalBounds.width
        assertTrue(o > l)
        assertEquals(TrueTypeWriter.SPACE_ADVANCE.toDouble(), space, 1.0)
    }

    @Test
    fun `the strokes can be read back from the file`() {
        val bytes = TrueTypeWriter.write(font)
        val back = TrueTypeWriter.read("My Hand", bytes)
        assertNotNull(back)
        assertEquals(setOf('l', 'o', '.', 'g'), back!!.glyphs.keys)
        for ((char, glyph) in back.glyphs) {
            val original = font.glyphs.getValue(char)
            assertEquals(original.strokes.size, glyph.strokes.size)
            for (i in glyph.strokes.indices) {
                assertEquals(original.strokes[i].size, glyph.strokes[i].size)
                for (k in glyph.strokes[i].indices) assertEquals(original.strokes[i][k], glyph.strokes[i][k], 0.26f)
            }
        }
    }

    @Test
    fun `a font file of another kind holds no strokes`() {
        assertNull(TrueTypeWriter.read("x", ByteArray(10)))
        assertNull(TrueTypeWriter.read("x", ByteArray(0)))
    }

    @Test
    fun `a font of the whole character set builds`() {
        val all = HandCharset.ALL.associateWith { HandGlyph(listOf(line(100f, 100f, 400f, 700f), line(400f, 100f, 100f, 700f))) }
        val loaded = load(HandFont("Full", all))
        for (c in HandCharset.ALL) assertTrue("has $c", loaded.canDisplay(c))
    }

    @Test
    fun `simplifying keeps the shape and drops points on a line`() {
        val straight = FloatArray(40) { if (it % 2 == 0) it * 5f else 100f }
        assertEquals(4, simplifyStroke(straight).size)
        val bent = floatArrayOf(0f, 0f, 50f, 0f, 100f, 0f, 100f, 50f, 100f, 100f)
        assertEquals(6, simplifyStroke(bent).size)
    }
}
