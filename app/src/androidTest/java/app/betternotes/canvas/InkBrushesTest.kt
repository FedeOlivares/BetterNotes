package app.betternotes.canvas

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The ink library rejects some brushes only when it builds them (at most 10 layers, distinct ranges). */
@RunWith(AndroidJUnit4::class)
class InkBrushesTest {
    @Test
    fun everyCreativeBrushBuilds() {
        for (kind in BrushKind.entries) {
            assertNotNull(kind.name, InkBrushes.brush(InkStyle(tool = InkTool.PENCIL, brush = kind)))
        }
    }

    /** A tile only paints what paintBounds says can reach it, so anything past it is cut off at the tile's edge. */
    @Test
    fun everyBrushPaintsInsideItsPaintBounds() {
        val past = StringBuilder()
        for (kind in BrushKind.entries) {
            val width = 12f
            val points = StrokePointsBuilder()
            for (i in 0..120) points.add(100f + i * 3f, 200f + (i % 30) * 2f, pressure = 0.6f, timeMs = i * 8f)
            val item = StrokeItem(IdSource.Random.next(), InkStyle(tool = InkTool.PENCIL, brush = kind, width = width), points.build())
            val mesh = checkNotNull(item.toInkStroke().shape.computeBoundingBox())
            val overPaint = reachPast(item.paintBounds, mesh.xMin, mesh.yMin, mesh.xMax, mesh.yMax) / width
            if (overPaint > 0f) past.append("$kind by $overPaint widths; ")
        }
        assertTrue(past.toString(), past.isEmpty())
    }

    private fun reachPast(box: Bounds, xMin: Float, yMin: Float, xMax: Float, yMax: Float) =
        maxOf(box.left - xMin, box.top - yMin, xMax - box.right, yMax - box.bottom)
}
