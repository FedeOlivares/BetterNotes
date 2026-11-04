package app.betternotes.core.tools

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapTest {
    private val other = Bounds(100f, 100f, 200f, 200f)

    @Test
    fun anEdgeInReachLinesUp() {
        val result = snapToAlign(Bounds(203f, 300f, 253f, 350f), listOf(other), null, null, reach = 5f)
        assertEquals(-3f, result.dx, 0f)
        assertEquals(200f, result.verticalLine)
        assertEquals(0f, result.dy, 0f)
        assertNull(result.horizontalLine)
    }

    @Test
    fun centersLineUpToo() {
        val result = snapToAlign(Bounds(300f, 124f, 350f, 174f), listOf(other), null, null, reach = 5f)
        assertEquals(1f, result.dy, 0f)
        assertEquals(150f, result.horizontalLine)
    }

    @Test
    fun theClosestLineWins() {
        val near = Bounds(205f, 500f, 215f, 510f)
        val result = snapToAlign(Bounds(204f, 300f, 254f, 350f), listOf(other, near), null, null, reach = 5f)
        assertEquals(205f, result.verticalLine)
    }

    @Test
    fun thePageIsSomethingToLineUpWith() {
        val result = snapToAlign(Bounds(2f, 300f, 52f, 350f), emptyList(), 600f, 800f, reach = 5f)
        assertEquals(-2f, result.dx, 0f)
        assertEquals(0f, result.verticalLine)
    }

    @Test
    fun nothingInReachMeansNoMove() {
        val result = snapToAlign(Bounds(300f, 300f, 350f, 350f), listOf(other), null, null, reach = 5f)
        assertEquals(0f, result.dx, 0f)
        assertEquals(0f, result.dy, 0f)
        assertNull(result.verticalLine)
    }

    @Test
    fun tapeRunsBetweenThePointsWithItsOwnWidth() {
        val tape = TapeOptions().item(1L, 10f, 10f, 110f, 10f)
        assertEquals(ShapeKind.TAPE, tape.kind)
        assertEquals(100f, tape.frame.width, 0.01f)
        assertEquals(TapeOptions().width.toFloat(), tape.frame.height, 0.01f)
        assertEquals(10f, tape.frame.centerY, 0.01f)
        assertEquals(ItemKind.TAPE, tape.kind())
    }

    private fun app.betternotes.core.model.ShapeItem.kind() = (this as app.betternotes.core.model.PageItem).kind
}
