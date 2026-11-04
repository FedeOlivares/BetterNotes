package app.betternotes.core.tools

import app.betternotes.core.model.LineKind
import app.betternotes.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ShapeOptionsTest {
    private val options = ShapeOptions()

    @Test
    fun aNewShapeTakesTheOldestPlaceInTheToolbar() {
        val next = options.withKind(ShapeKind.STAR)
        assertEquals(ShapeKind.STAR, next.kind)
        assertEquals(listOf(ShapeKind.TRIANGLE, ShapeKind.RECTANGLE, ShapeKind.STAR), next.recentKinds)
        assertEquals(options.recentKinds, options.withKind(ShapeKind.ELLIPSE).recentKinds)
    }

    @Test
    fun aBoxShapeFillsWhatWasDraggedInAnyDirection() {
        val frame = shapeFrame(ShapeKind.RECTANGLE, 30f, 40f, 10f, 15f)
        assertEquals(10f, frame.left, 0f)
        assertEquals(15f, frame.top, 0f)
        assertEquals(20f, frame.width, 0f)
        assertEquals(25f, frame.height, 0f)
        assertEquals(0f, frame.rotation, 0f)
    }

    @Test
    fun aLineRunsBetweenThePointsItWasDraggedTo() {
        val frame = shapeFrame(ShapeKind.LINE, 10f, 10f, 40f, 50f)
        assertEquals(50f, frame.width, 0.01f)
        assertEquals(0f, frame.height, 0f)
        assertEquals(10f, frame.worldX(0f, 0.5f), 0.01f)
        assertEquals(10f, frame.worldY(0f, 0.5f), 0.01f)
        assertEquals(40f, frame.worldX(1f, 0.5f), 0.01f)
        assertEquals(50f, frame.worldY(1f, 0.5f), 0.01f)
    }

    @Test
    fun theItemCarriesTheSettings() {
        val item = options.copy(line = LineKind.DASHED, dashLength = 9f, fillColor = 0xFF112233.toInt()).item(7L, 0f, 0f, 10f, 10f)
        assertEquals(options.color, item.strokeColor)
        assertEquals(options.width * InkPreset.POINTS_PER_THICKNESS_UNIT, item.strokeWidth, 0f)
        assertEquals(LineKind.DASHED, item.line.kind)
        assertEquals(9f, item.line.dashLength, 0f)
        assertEquals(0xFF112233.toInt(), item.fillColor)
    }
}
