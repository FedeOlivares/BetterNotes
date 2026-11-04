package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FadingTrailTest {
    private val trail = FadingTrail(lifetimeMs = 100L, capacity = 4)

    private fun segments(now: Long): List<List<Float>> {
        val out = ArrayList<List<Float>>()
        trail.forEachSegment(now) { x0, y0, x1, y1, strength -> out += listOf(x0, y0, x1, y1, strength) }
        return out
    }

    @Test
    fun samplesFadeWithAgeAndThenGo() {
        trail.start(0f, 0f, 0L)
        trail.add(10f, 0f, 50L)
        assertEquals(listOf(0f, 0f, 10f, 0f), segments(50L)[0].take(4))
        assertEquals(1f, segments(50L)[0][4], 0f)
        assertEquals(0.5f, segments(100L)[0][4], 0f)
        assertTrue(trail.prune(100L))
        assertEquals(0, trail.tipIndex)
        assertFalse(trail.prune(150L))
    }

    @Test
    fun aNewStartIsNotJoinedToTheOldTail() {
        trail.start(0f, 0f, 0L)
        trail.add(10f, 0f, 1L)
        trail.start(50f, 50f, 2L)
        trail.add(60f, 50f, 3L)
        val found = segments(3L)
        assertEquals(2, found.size)
        assertEquals(10f, found[0][2], 0f)
        assertEquals(50f, found[1][0], 0f)
    }

    @Test
    fun aFullRingDropsTheOldest() {
        trail.start(0f, 0f, 0L)
        for (i in 1..5) trail.add(i.toFloat(), 0f, i.toLong())
        val found = segments(5L)
        assertEquals(3, found.size)
        assertEquals(2f, found[0][0], 0f)
        assertEquals(5f, found[2][2], 0f)
    }
}
