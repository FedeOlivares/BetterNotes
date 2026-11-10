package app.betternotes.core.tools

import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.StrokePointsBuilder
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private inline fun <reified T> assertIs(value: Any?): T {
    assertTrue("was $value", value is T)
    return value as T
}

class ShapeFitTest {
    private val random = Random(7)

    /** Hand tremor: up to [jitter] points off in any direction. */
    private fun shaky(path: List<Pair<Float, Float>>, jitter: Float = 1.2f): FloatArray {
        val out = FloatArray(path.size * 2)
        path.forEachIndexed { i, (x, y) ->
            out[i * 2] = x + (random.nextFloat() - 0.5f) * 2 * jitter
            out[i * 2 + 1] = y + (random.nextFloat() - 0.5f) * 2 * jitter
        }
        return out
    }

    private fun along(corners: List<Pair<Float, Float>>, closed: Boolean, perSide: Int = 40): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        val n = corners.size
        for (k in 0 until if (closed) n else n - 1) {
            val (ax, ay) = corners[k]
            val (bx, by) = corners[(k + 1) % n]
            for (s in 0 until perSide) out += (ax + (bx - ax) * s / perSide) to (ay + (by - ay) * s / perSide)
        }
        out += if (closed) corners[0] else corners.last()
        return out
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, degrees: Float, turns: Float = 1.03f): List<Pair<Float, Float>> {
        val r = Math.toRadians(degrees.toDouble())
        return (0..120).map {
            val a = 2 * PI * turns * it / 120
            val u = rx * cos(a)
            val v = ry * sin(a)
            (cx + u * cos(r) - v * sin(r)).toFloat() to (cy + u * sin(r) + v * cos(r)).toFloat()
        }
    }

    @Test
    fun aCircleBecomesAnEllipse() {
        val fit = ShapeFit.fit(shaky(ellipse(300f, 300f, 80f, 80f, 0f)))
        val box = assertIs<FittedShape.Box>(fit)
        assertEquals(ShapeKind.ELLIPSE, box.kind)
        assertEquals(300f, box.cx, 4f)
        assertEquals(160f, box.width, 8f)
        assertEquals(160f, box.height, 8f)
    }

    @Test
    fun aTiltedEllipseKeepsItsTilt() {
        val fit = ShapeFit.fit(shaky(ellipse(200f, 250f, 120f, 50f, 30f)))
        val box = assertIs<FittedShape.Box>(fit)
        assertEquals(ShapeKind.ELLIPSE, box.kind)
        assertEquals(30f, box.degrees, 6f)
        // The long axis is the width, whichever way round the search found it.
        assertEquals(240f, maxOf(box.width, box.height), 14f)
    }

    @Test
    fun aRectangleBecomesARectangle() {
        val corners = listOf(100f to 100f, 300f to 100f, 300f to 220f, 100f to 220f)
        val fit = ShapeFit.fit(shaky(along(corners, true)))
        val box = assertIs<FittedShape.Box>(fit)
        assertEquals(ShapeKind.RECTANGLE, box.kind)
        assertEquals(200f, box.cx, 5f)
        assertEquals(160f, box.cy, 5f)
    }

    @Test
    fun aTurnedRectangleKeepsItsAngle() {
        val r = Math.toRadians(25.0)
        val local = listOf(-100f to -60f, 100f to -60f, 100f to 60f, -100f to 60f)
        val corners = local.map { (x, y) -> (250 + x * cos(r) - y * sin(r)).toFloat() to (250 + x * sin(r) + y * cos(r)).toFloat() }
        val box = assertIs<FittedShape.Box>(ShapeFit.fit(shaky(along(corners, true))))
        assertEquals(ShapeKind.RECTANGLE, box.kind)
        assertEquals(25f, box.degrees, 4f)
    }

    @Test
    fun aTriangleKeepsItsThreeCorners() {
        val corners = listOf(150f to 80f, 330f to 300f, 60f to 270f)
        val path = assertIs<FittedShape.Path>(ShapeFit.fit(shaky(along(corners, true))))
        assertTrue(path.closed && !path.smooth)
        assertEquals(3, path.points.size / 2)
        for (c in corners) {
            val near = (0 until 3).minOf { hypot(path.points[it * 2] - c.first, path.points[it * 2 + 1] - c.second) }
            assertTrue("corner $c is $near away", near < 14f)
        }
    }

    @Test
    fun aStraightStrokeBecomesALine() {
        val fit = ShapeFit.fit(shaky(along(listOf(50f to 60f, 400f to 120f), false, 60)))
        val line = assertIs<FittedShape.Line>(fit)
        assertEquals(0f, line.headLength, 0f)
        assertEquals(400f, line.x1, 5f)
    }

    @Test
    fun aShaftWithABarbedHeadBecomesAnArrow() {
        // Out to the tip at (300, 100), back along one barb, back to the tip, along the other.
        val corners = listOf(50f to 100f, 300f to 100f, 260f to 70f, 300f to 100f, 260f to 130f)
        val fit = ShapeFit.fit(shaky(along(corners, false, 30), 0.8f))
        val arrow = assertIs<FittedShape.Line>(fit)
        assertTrue("head ${arrow.headLength}", arrow.headLength > 20f)
        assertEquals(300f, arrow.x1, 8f)
        assertEquals(50f, arrow.x0, 8f)
    }

    @Test
    fun aWaveBecomesASmoothCurve() {
        val wave = (0..100).map { (40f + it * 3f) to (200f + 60f * sin(it / 100.0 * 2 * PI).toFloat()) }
        val path = assertIs<FittedShape.Path>(ShapeFit.fit(shaky(wave, 0.8f)))
        assertTrue(path.smooth && !path.closed)
        assertTrue(path.points.size / 2 in 3..8)
    }

    @Test
    fun aTinyOrScribbledStrokeStaysInk() {
        assertNull(ShapeFit.fit(shaky(along(listOf(10f to 10f, 14f to 12f), false, 5))))
        val scribble = (0..80).map { (100f + (it % 2) * 40f + random.nextFloat() * 10f) to (100f + it * 1.5f) }
        assertNull(ShapeFit.fit(shaky(scribble)))
    }

    @Test
    fun convertKeepsTheStrokesColorAndWidth() {
        val builder = StrokePointsBuilder()
        val packed = shaky(ellipse(100f, 100f, 50f, 30f, 0f))
        for (i in 0 until packed.size / 2) builder.add(packed[i * 2], packed[i * 2 + 1])
        val stroke = StrokeItem(1L, InkStyle(color = 0xFF112233.toInt(), width = 5f), builder.build())
        val item = assertIs<ShapeItem>(ShapeFit.convert(stroke))
        assertEquals(0xFF112233.toInt(), item.strokeColor)
        assertEquals(5f, item.strokeWidth, 0f)
    }

    @Test
    fun aSmoothPathBoundsTakeInItsBulge() {
        val item = PathShapeItem(1L, floatArrayOf(0f, 0f, 50f, 100f, 100f, 0f), closed = false, smooth = true)
        assertTrue(item.bounds.bottom >= 100f)
        assertTrue(item.outline().size > item.points.size)
    }

    @Test
    fun aRecognizedShapeSurvivesTheFile() {
        val item = PathShapeItem(9L, floatArrayOf(1f, 2f, 30f, 40f, 5f, 60f), closed = true, smooth = false, strokeColor = 0xFF00FF00.toInt(), strokeWidth = 3f)
        val back = app.betternotes.core.format.PageCodec.decode(app.betternotes.core.format.PageCodec.encode(listOf(item)))
        assertEquals(listOf<Any>(item), back)
    }

    @Test
    fun aSlightlyTiltedRectangleIsPutUpright() {
        val r = Math.toRadians(5.0)
        val local = listOf(-100f to -60f, 100f to -60f, 100f to 60f, -100f to 60f)
        val corners = local.map { (x, y) -> (250 + x * cos(r) - y * sin(r)).toFloat() to (250 + x * sin(r) + y * cos(r)).toFloat() }
        val box = assertIs<FittedShape.Box>(ShapeFit.fit(shaky(along(corners, true))))
        assertEquals(ShapeKind.RECTANGLE, box.kind)
        assertEquals(0f, box.degrees, 0f)
    }

    @Test
    fun aNearlyLevelEllipseIsPutUpright() {
        val box = assertIs<FittedShape.Box>(ShapeFit.fit(shaky(ellipse(200f, 250f, 120f, 50f, 6f))))
        assertEquals(0f, box.degrees, 0f)
    }

    @Test
    fun aSimpleBowGetsFewControlPoints() {
        val bow = (0..100).map { (40f + it * 3f) to (200f - 50f * sin(it / 100.0 * PI).toFloat()) }
        val path = assertIs<FittedShape.Path>(ShapeFit.fit(shaky(bow, 1.5f)))
        assertTrue("${path.points.size / 2} points", path.points.size / 2 <= 4)
    }

    @Test
    fun anArrowHeadIsSymmetricalAboutItsShaft() {
        // Barbs of unequal reach still give one head size and a width split evenly.
        val corners = listOf(50f to 100f, 300f to 100f, 262f to 76f, 300f to 100f, 258f to 120f)
        val arrow = assertIs<FittedShape.Line>(ShapeFit.fit(shaky(along(corners, false, 30), 0.8f)))
        assertTrue(arrow.headLength > 20f)
        assertEquals(44f, arrow.headWidth, 8f)
    }

    @Test
    fun aCornerHandleRoundsARectangle() {
        val rect = ShapeItem(1L, app.betternotes.core.geometry.Frame(0f, 0f, 200f, 100f), ShapeKind.RECTANGLE)
        val at = ShapeTips.position(rect, ShapeParam.CORNER_RADIUS)
        val dragged = ShapeTips.dragged(rect, ShapeParam.CORNER_RADIUS, at[0] + 20f, at[1])
        assertEquals(20f, dragged.cornerRadius, 0.01f)
        assertEquals(50f, ShapeTips.dragged(rect, ShapeParam.CORNER_RADIUS, 500f, 0f).cornerRadius, 0.01f)
    }
}
