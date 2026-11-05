package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class AngleGuideTest {
    @Test
    fun anglesAreCountedAnticlockwiseFromTheRightWithYDown() {
        assertEquals(0f, AngleGuide.angle(0f, 0f, 10f, 0f), 0.01f)
        assertEquals(90f, AngleGuide.angle(0f, 0f, 0f, -10f), 0.01f)
        assertEquals(180f, AngleGuide.angle(0f, 0f, -10f, 0f), 0.01f)
        assertEquals(270f, AngleGuide.angle(0f, 0f, 0f, 10f), 0.01f)
        assertEquals(315f, AngleGuide.angle(0f, 0f, 10f, 10f), 0.01f)
        assertEquals(45f, AngleGuide.angle(0f, 0f, 10f, -10f), 0.01f)
    }

    @Test
    fun theNearestReferenceWrapsAroundTheCircle() {
        assertEquals(0f, AngleGuide.nearest(358f), 0.01f)
        assertEquals(45f, AngleGuide.nearest(50f), 0.01f)
        assertEquals(315f, AngleGuide.nearest(300f), 0.01f)
        assertEquals(2f, AngleGuide.distance(358f), 0.01f)
    }

    @Test
    fun aLineWithinThreeDegreesSnapsAndKeepsItsLength() {
        // About 2 degrees above horizontal.
        val end = AngleGuide.snap(0f, 0f, 100f, -3.5f)
        assertEquals(0f, end[1], 0.001f)
        assertEquals(Math.hypot(100.0, 3.5).toFloat(), end[0], 0.01f)
        // About 5 degrees off is left alone.
        val loose = AngleGuide.snap(0f, 0f, 100f, -8.8f)
        assertEquals(100f, loose[0], 0.001f)
        assertEquals(-8.8f, loose[1], 0.001f)
        // A diagonal snaps onto exactly 45.
        val diagonal = AngleGuide.snap(10f, 10f, 70f, -48f)
        assertEquals(45f, AngleGuide.angle(10f, 10f, diagonal[0], diagonal[1]), 0.01f)
    }

    @Test
    fun aVeryShortLineIsNotSnapped() {
        val end = AngleGuide.snap(0f, 0f, 3f, -0.1f)
        assertEquals(-0.1f, end[1], 0f)
    }
}
