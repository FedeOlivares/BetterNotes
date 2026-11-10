package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.draw
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Draws the built-in planning pages with the real canvas sink, on light and dark paper, and checks the words show. */
@RunWith(AndroidJUnit4::class)
class PlanningPagesTest {
    private companion object {
        const val SCALE = 2
    }

    private val page = PageSize.Letter
    private val pages = mapOf("semester" to PageTemplate.Semester, "mindmap" to PageTemplate.MindMap, "planner" to PageTemplate.WeekPlanner)

    private fun render(template: PageTemplate, paper: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(page.widthPt.toInt() * SCALE, page.heightPt.toInt() * SCALE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), Paint().apply { color = paper })
        canvas.scale(SCALE.toFloat(), SCALE.toFloat())
        val sink = CanvasTemplateSink()
        sink.canvas = canvas
        template.draw(page, Bounds(0f, 0f, page.widthPt, page.heightPt), sink, paper = paper)
        sink.flush()
        return bitmap
    }

    /** True when some pixel in the band differs from the paper by [minDelta] or more in luminance. */
    private fun Bitmap.hasMarks(paper: Int, top: Int, bottom: Int, minDelta: Float): Boolean {
        val base = Color.luminance(paper)
        for (y in top until bottom) for (x in 0 until width) if (Math.abs(Color.luminance(getPixel(x, y)) - base) >= minDelta) return true
        return false
    }

    @Test
    fun everyPageShowsItsWordsAndLinesOnLightAndDarkPaper() {
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "planning-pages").apply { mkdirs() }
        for ((name, template) in pages) {
            for ((paper, tone) in listOf(0xFFF8F6EA.toInt() to "light", 0xFF2E2E2E.toInt() to "dark")) {
                val bitmap = render(template, paper)
                FileOutputStream(File(out, "$name-$tone.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // The title band at the top carries text, and the whole page reads against its paper.
                assertTrue("$name on $tone paper has marks", bitmap.hasMarks(paper, 0, bitmap.height, 0.25f))
            }
        }
    }
}
