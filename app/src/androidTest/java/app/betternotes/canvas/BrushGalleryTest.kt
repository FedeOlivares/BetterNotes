package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ink.brush.InputToolType
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sin

/**
 * Draws one stroke of every creative brush onto a PNG, so the brushes can be looked at without a tablet.
 * Pull it with: adb pull /sdcard/Android/data/app.betternotes/files/brushes.png
 */
@OptIn(ExperimentalInkCustomBrushApi::class)
@RunWith(AndroidJUnit4::class)
class BrushGalleryTest {
    @Test
    fun drawEveryBrush() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renderer = CanvasStrokeRenderer.create(BrushTextures)
        val scale = 3f
        val rowPt = 40f
        val widthPt = 320f
        val kinds = BrushKind.entries
        val bitmap = Bitmap.createBitmap((widthPt * scale).toInt(), (rowPt * kinds.size * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val colors = intArrayOf(0xFF333333.toInt(), 0xFFE07A2F.toInt(), 0xFF8E44E0.toInt(), 0xFFE8C23A.toInt(), 0xFF2FB4E8.toInt(), 0xFFE07A2F.toInt(), 0xFF7A1FE0.toInt(), 0xFFE8B21F.toInt(), 0xFF6FC6E8.toInt(), 0xFF3CC64B.toInt(), 0xFF7A1FE0.toInt())
        kinds.forEachIndexed { row, kind ->
            val style = InkStyle(tool = InkTool.PENCIL, brush = kind, width = 12f, color = colors[row % colors.size])
            val batch = MutableStrokeInputBatch()
            val points = 80
            for (i in 0..points) {
                val t = i.toFloat() / points
                val x = 20f + t * (widthPt - 40f)
                val y = sin(t * 5.0).toFloat() * 6f
                batch.add(StrokeInput().apply { update(x, y, i * 8L, InputToolType.STYLUS, 1f / 0.3527778f, 0.6f) })
            }
            val stroke = Stroke(InkBrushes.brush(style), batch)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(0f, (row * rowPt + rowPt / 2f) * scale)
            }
            canvas.save()
            canvas.concat(matrix)
            renderer.draw(canvas, stroke, matrix)
            canvas.restore()
        }
        val out = File(context.getExternalFilesDir(null), "brushes.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
