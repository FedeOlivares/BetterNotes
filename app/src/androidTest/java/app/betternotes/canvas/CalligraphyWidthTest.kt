package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.PenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The calligraphy pen is widest going straight up and down, thinnest going across, and half way between at 45 degrees. */
@RunWith(AndroidJUnit4::class)
class CalligraphyWidthTest {
    /** The width across a straight stroke at [degrees] from horizontal, in points, measured from a drawing of it. */
    private fun widthAt(degrees: Double): Float {
        val scale = 4f
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val batch = MutableStrokeInputBatch()
        val length = 100f
        val radians = Math.toRadians(degrees)
        for (i in 0..40) {
            val t = i / 40f
            val x = 20f + t * length * cos(radians).toFloat()
            val y = 75f - t * length * sin(radians).toFloat()
            batch.add(StrokeInput().apply { update(x, y, i * 8L, InputToolType.STYLUS, 1f / 0.3527778f, 1f) })
        }
        val style = InkStyle(tool = InkTool.PEN, penType = PenType.CALLIGRAPHY, width = 12f, color = Color.BLACK)
        val matrix = Matrix().apply { setScale(scale, scale) }
        canvas.save()
        canvas.concat(matrix)
        CanvasStrokeRenderer.create().draw(canvas, Stroke(InkBrushes.brush(style), batch), matrix)
        canvas.restore()
        // The middle of the stroke, then the dark pixels along the line across it.
        val mx = (20f + length * cos(radians).toFloat() / 2f) * scale
        val my = (75f - length * sin(radians).toFloat() / 2f) * scale
        val nx = -sin(radians).toFloat()
        val ny = -cos(radians).toFloat()
        var dark = 0
        for (step in -400..400) {
            val px = (mx + nx * step / 4f).toInt()
            val py = (my + ny * step / 4f).toInt()
            if (px in 0 until bitmap.width && py in 0 until bitmap.height && Color.red(bitmap.getPixel(px, py)) < 128) dark++
        }
        return dark / 4f / scale
    }

    @Test
    fun widthFollowsTheDirectionOfTheLine() {
        val vertical = widthAt(90.0)
        val horizontal = widthAt(0.0)
        val diagonal = widthAt(45.0)
        assertTrue("vertical $vertical is wider than horizontal $horizontal", vertical > horizontal * 3f)
        val middle = (vertical + horizontal) / 2f
        assertEquals("a 45 degree stroke is about half way ($vertical, $horizontal, $diagonal)", middle, diagonal, middle * 0.2f)
    }
}
