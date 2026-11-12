package app.betternotes.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {
    @Test
    fun `a code reads with or without the hash, in either case, and short`() {
        assertEquals(0xFF2F5BEA.toInt(), HexColor.parse("#2F5BEA"))
        assertEquals(0xFF2F5BEA.toInt(), HexColor.parse("2f5bea"))
        assertEquals(0xFF2F5BEA.toInt(), HexColor.parse("  #2f5bEA "))
        assertEquals(0xFFFFCC00.toInt(), HexColor.parse("#FC0"))
        assertEquals(0xFF000000.toInt(), HexColor.parse("000"))
    }

    @Test
    fun `what is not a whole code is refused`() {
        for (bad in listOf("", "#", "#12", "#12345", "#1234567", "#GG0000", "12 34 56", "##123456")) assertNull(bad, HexColor.parse(bad))
    }

    @Test
    fun `a color is written in capitals without alpha, and reads back`() {
        assertEquals("#2F5BEA", HexColor.format(0x802F5BEA.toInt()))
        assertEquals("#000001", HexColor.format(1))
        val color = 0xFF10A0F0.toInt()
        assertEquals(color, HexColor.parse(HexColor.format(color)))
    }

    @Test
    fun `typing keeps only a leading hash and hex digits, up to seven characters`() {
        assertEquals("#2F5BEA", HexColor.filter("#2f5bea"))
        assertEquals("2F5BEA1", HexColor.filter("2f5bea1zz"))
        assertEquals("#2F5BEA", HexColor.filter("#2f5bea99"))
        assertEquals("#1A", HexColor.filter("#1#A"))
        assertEquals("", HexColor.filter("xyz"))
    }
}
