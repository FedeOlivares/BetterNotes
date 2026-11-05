package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Draws the angle tool at a few angles onto a PNG: adb pull /sdcard/Android/data/app.betternotes/files/angles.png */
@RunWith(AndroidJUnit4::class)
class AngleGuideGalleryTest {
    @Test
    fun drawGuides() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = 2.75f
        val bitmap = Bitmap.createBitmap(1500, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val painter = AngleGuidePainter(density)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 4f }
        // Far from a reference angle, close to one, and on one.
        listOf(20f, 47f, 90f, 135f, 300f).forEachIndexed { i, degrees ->
            val sx = 140f + i * 290f
            val sy = 300f
            val radians = Math.toRadians(degrees.toDouble())
            val ex = sx + 200f * cos(radians).toFloat()
            val ey = sy - 200f * sin(radians).toFloat()
            canvas.drawLine(sx, sy, ex, ey, ink)
            painter.draw(canvas, sx, sy, ex, ey, degrees)
        }
        File(context.getExternalFilesDir(null), "angles.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
