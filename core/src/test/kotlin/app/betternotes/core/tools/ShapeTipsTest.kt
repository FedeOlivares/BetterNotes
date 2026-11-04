package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeTipsTest {
    private val arrow = ShapeItem(1L, Frame(0f, 0f, 200f, 0f), ShapeKind.ARROW, strokeWidth = 4f)

    @Test
    fun anArrowDefaultsToItsOldProportions() {
        assertEquals(24f, ShapeTips.length(ShapeKind.ARROW, 200f, 0f, 0f), 0.001f)
        assertEquals(28.8f, ShapeTips.width(ShapeKind.ARROW, 200f, 0f, 0f, 0f), 0.001f)
    }

    @Test
    fun draggingTheLengthHandleSetsTheHeadLength() {
        val position = ShapeTips.position(arrow, ShapeParam.TIP_LENGTH)
        assertEquals(176f, position[0], 0.001f)
        val dragged = ShapeTips.dragged(arrow, ShapeParam.TIP_LENGTH, 150f, 0f)
        assertEquals(50f, dragged.tipLength, 0.001f)
    }

    @Test
    fun draggingTheWidthHandleSetsTheHeadWidth() {
        val dragged = ShapeTips.dragged(arrow, ShapeParam.TIP_WIDTH, 176f, -20f)
        assertEquals(40f, dragged.tipWidth, 0.001f)
    }

    @Test
    fun draggingTheStrokeHandleSetsTheStrokeWidth() {
        val dragged = ShapeTips.dragged(arrow, ShapeParam.STROKE_WIDTH, 100f, -5f)
        assertEquals(10f, dragged.strokeWidth, 0.001f)
    }

    @Test
    fun tipSizesStayInRange() {
        assertEquals(3f, ShapeTips.dragged(arrow, ShapeParam.TIP_LENGTH, 199f, 0f).tipLength, 0.001f)
        assertEquals(180f, ShapeTips.dragged(arrow, ShapeParam.TIP_LENGTH, -50f, 0f).tipLength, 0.001f)
    }

    @Test
    fun aRectangleHasOnlyAStrokeHandle() {
        assertEquals(setOf(ShapeParam.STROKE_WIDTH), ShapeTips.params(ShapeKind.RECTANGLE))
        assertTrue(ShapeTips.params(ShapeKind.TAPE).isEmpty())
    }

    @Test
    fun scalingAShapeScalesItsTips() {
        val scaled = arrow.copy(tipLength = 10f, tipWidth = 6f)
            .transformed(app.betternotes.core.geometry.Affine(a = 2f, d = 2f))
        assertEquals(20f, scaled.tipLength, 0.001f)
        assertEquals(12f, scaled.tipWidth, 0.001f)
    }

    @Test
    fun theBiteOfAPieFollowsItsHandle() {
        val pie = ShapeItem(2L, Frame(0f, 0f, 100f, 100f), ShapeKind.PIE)
        // A handle straight up from the center leaves a bite of a quarter turn.
        val up = ShapeTips.dragged(pie, ShapeParam.TIP_LENGTH, 50f, 0f)
        assertEquals(90f, up.tipLength, 0.01f)
        val wide = ShapeTips.dragged(pie, ShapeParam.TIP_LENGTH, 0f, 50f)
        assertEquals(180f, wide.tipLength, 0.01f)
    }

    @Test
    fun theWedgeBodyGrowsWithoutTheTopTriangle() {
        val wedge = ShapeItem(3L, Frame(0f, 0f, 100f, 100f), ShapeKind.PRISM)
        // By default the top is 30 and the body 70.
        val taller = ShapeTips.dragged(wedge, ShapeParam.TIP_LENGTH, 50f, 150f)
        assertEquals(150f, taller.frame.height, 0.01f)
        assertEquals(120f, taller.tipLength, 0.01f)
        assertEquals(30f, taller.frame.height - taller.tipLength, 0.01f)
    }

    @Test
    fun aSpeechTailAlwaysSticksOutOfTheBubble() {
        val bubble = ShapeItem(4L, Frame(0f, 0f, 200f, 100f), ShapeKind.SPEECH_ROUND)
        val inside = ShapeTips.dragged(bubble, ShapeParam.TIP_LENGTH, 100f, 37f)
        val dx = (inside.tipLength - 100f) / 100f
        val dy = (inside.tipWidth - 37.5f) / 37.5f
        assertTrue(dx * dx + dy * dy >= 1f)
    }
}
