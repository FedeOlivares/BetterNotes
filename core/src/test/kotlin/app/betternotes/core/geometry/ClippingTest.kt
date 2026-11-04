package app.betternotes.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClippingTest {
    private val eps = 1e-4f

    @Test
    fun `disc cuts the middle of a segment that crosses it`() {
        val disc = Capsule(50f, 0f, 50f, 0f, 10f)
        assertTrue(disc.clip(0f, 0f, 100f, 0f))
        assertEquals(0.4f, disc.enter, eps)
        assertEquals(0.6f, disc.exit, eps)
    }

    @Test
    fun `margin grows the region`() {
        val disc = Capsule(50f, 0f, 50f, 0f, 10f)
        assertFalse(disc.clip(0f, 15f, 100f, 15f))
        assertTrue(disc.clip(0f, 15f, 100f, 15f, margin = 6f))
    }

    @Test
    fun `segment parallel to a capsule is cut by its whole length and rounded ends`() {
        val capsule = Capsule(40f, 0f, 60f, 0f, 10f)
        assertTrue(capsule.clip(0f, 0f, 100f, 0f))
        assertEquals(0.3f, capsule.enter, eps)
        assertEquals(0.7f, capsule.exit, eps)
    }

    @Test
    fun `segment crossing the straight side of a capsule uses the slab`() {
        val capsule = Capsule(0f, 0f, 100f, 0f, 5f)
        assertTrue(capsule.clip(50f, -10f, 50f, 10f))
        assertEquals(0.25f, capsule.enter, eps)
        assertEquals(0.75f, capsule.exit, eps)
    }

    @Test
    fun `segment fully inside reports the whole interval`() {
        val capsule = Capsule(0f, 0f, 100f, 0f, 20f)
        assertTrue(capsule.clip(40f, 1f, 60f, 1f))
        assertEquals(0f, capsule.enter, eps)
        assertEquals(1f, capsule.exit, eps)
    }

    @Test
    fun `segment past the rounded end misses`() {
        val capsule = Capsule(0f, 0f, 100f, 0f, 5f)
        assertFalse(capsule.clip(106f, -10f, 106f, 10f))
    }

    @Test
    fun `degenerate segment is a point test`() {
        val disc = Capsule(0f, 0f, 0f, 0f, 5f)
        assertTrue(disc.clip(3f, 0f, 3f, 0f))
        assertFalse(disc.clip(6f, 0f, 6f, 0f))
    }

    @Test
    fun `rect clips a crossing segment`() {
        val clipper = RectClipper(Bounds(10f, 0f, 20f, 10f))
        assertTrue(clipper.clip(0f, 5f, 40f, 5f))
        assertEquals(0.25f, clipper.enter, eps)
        assertEquals(0.5f, clipper.exit, eps)
        assertFalse(clipper.clip(0f, 20f, 40f, 20f))
    }

    @Test
    fun `polygon contains uses even odd`() {
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(polygonContains(square, 5f, 5f))
        assertFalse(polygonContains(square, 15f, 5f))
    }

    @Test
    fun `frame scales without moving the fixed point`() {
        val moved = Frame(10f, 10f, 20f, 20f).transformed(Affine.scaleAbout(2f, 3f, 10f, 10f))
        assertEquals(10f, moved.left, eps)
        assertEquals(10f, moved.top, eps)
        assertEquals(40f, moved.width, eps)
        assertEquals(60f, moved.height, eps)
        assertEquals(0f, moved.rotation, eps)
    }

    @Test
    fun `rotated frame keeps its rotation under a uniform scale`() {
        val frame = Frame(0f, 0f, 10f, 20f, rotation = 30f)
        val scaled = frame.transformed(Affine.scaleAbout(2f, 2f, frame.centerX, frame.centerY))
        assertEquals(30f, scaled.rotation, eps)
        assertEquals(20f, scaled.width, eps)
        assertEquals(40f, scaled.height, eps)
        assertEquals(frame.centerX, scaled.centerX, eps)
    }

    @Test
    fun `rotated frame contains and maps points in its own axes`() {
        val frame = Frame(0f, 0f, 100f, 10f, rotation = 90f)
        assertTrue(frame.contains(50f, -40f))
        assertFalse(frame.contains(0f, 5f))
        assertEquals(50f, frame.worldX(1f, 0.5f), eps)
        assertEquals(55f, frame.worldY(1f, 0.5f), eps)
    }
}
