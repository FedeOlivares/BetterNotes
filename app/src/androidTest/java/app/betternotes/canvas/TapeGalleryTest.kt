package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.model.TapePattern
import app.betternotes.core.tools.TapeOptions
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Draws strips of tape with each pattern and the warning tape onto a PNG: adb pull /sdcard/Android/data/app.betternotes/files/tapes.png */
@RunWith(AndroidJUnit4::class)
class TapeGalleryTest {
    @Test
    fun drawTapes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scale = 3f
        val bitmap = Bitmap.createBitmap((320 * scale).toInt(), (260 * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        val painter = ItemPainter()
        var id = 1L
        var y = 30f
        for (pattern in TapePattern.entries) {
            val options = TapeOptions(selectedColor = 3, pattern = pattern, thickness = 28)
            painter.paint(canvas, options.item(id++, 20f, y, 300f, y))
            y += 40f
        }
        // Warning tape, and a slanted strip with a stronger shadow.
        painter.paint(canvas, TapeOptions(selectedColor = TapeOptions().colors.size, pattern = TapePattern.DOTS, thickness = 28).item(id++, 20f, y, 300f, y))
        painter.paint(canvas, TapeOptions(selectedColor = 1, pattern = TapePattern.LINES, thickness = 24, shadowDistance = 4f, shadowOpacity = 0.5f).item(id++, 40f, 245f, 280f, 190f))
        File(context.getExternalFilesDir(null), "tapes.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
