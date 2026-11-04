package app.betternotes.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.format.DocumentSession
import app.betternotes.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Times a long PDF from import to sharp pixels, and logs the results under the tag BetterNotesPerf.
 * It asserts nothing about speed, since the numbers only mean something on the Pad 7. Run it there with
 * `gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.betternotes.pdf.PdfSpeedTest`.
 * The time goes to PDFium and the disk, which a debug build does not slow down.
 */
@RunWith(AndroidJUnit4::class)
class PdfSpeedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun aLongPdfFromImportToSharpPixels() {
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "pdf-speed").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(root, "long.pdf")
        writeLongPdf(file)
        log("file: ${file.length() / 1024} KB, $PAGES pages")

        val documentsDir = File(root, "documents")
        val imported = timed("import") {
            PdfImporter.import(context.contentResolver, Uri.fromFile(file), documentsDir)
        } as PdfImportResult.Imported
        val session = timed("open the saved document") { DocumentSession.open(imported.store) }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var canvas: CanvasView
        lateinit var editor: NoteEditor
        val opened = SystemClock.elapsedRealtime()
        scenario.onActivity { activity ->
            editor = NoteEditor(imported.store, session)
            canvas = CanvasView(activity)
            activity.setContentView(canvas)
            canvas.open(editor)
        }
        fun pixel(worldX: Float, worldY: Float): Int {
            val (x, y) = onMain { canvas.viewport.toScreenX(worldX).toInt() to canvas.viewport.toScreenY(worldY).toInt() }
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val done = CountDownLatch(1)
            var window: android.view.Window? = null
            scenario.onActivity { window = it.window }
            PixelCopy.request(window!!, Rect(x, y, x + 1, y + 1), bitmap, { done.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue(done.await(5, TimeUnit.SECONDS))
            return bitmap.getPixel(0, 0)
        }
        fun waitForSquare(worldY: Float) {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (Color.red(pixel(40f, worldY + 40f)) > 60) {
                assertTrue("The page never appeared", SystemClock.elapsedRealtime() < deadline)
                SystemClock.sleep(2)
            }
        }
        waitForSquare(0f)
        log("first pixels of page 1: ${SystemClock.elapsedRealtime() - opened} ms")

        val pageStep = HEIGHT + 12f
        val far = 200
        var start = SystemClock.elapsedRealtime()
        onMain {
            canvas.viewport.panY = -far * pageStep * canvas.viewport.pxPerPt
            canvas.zoomTo(1f)
        }
        waitForSquare(far * pageStep)
        log("jump to page ${far + 1}: ${SystemClock.elapsedRealtime() - start} ms")

        start = SystemClock.elapsedRealtime()
        onMain {
            canvas.zoomTo(4f)
            val viewport = canvas.viewport
            viewport.panX = 0f
            viewport.panY = -far * pageStep * viewport.pxPerPt
            canvas.zoomTo(4f)
        }
        waitForSquare(far * pageStep)
        log("zoom to 400% on that page: ${SystemClock.elapsedRealtime() - start} ms")

        fun search(query: String): Int {
            var count = 0
            val done = CountDownLatch(1)
            onMain { editor.searchPdfText(query, { count += it.size }, { done.countDown() }) }
            assertTrue(done.await(120, TimeUnit.SECONDS))
            return count
        }
        assertEquals(1, timed("first search, reads all the text") { search("marca$far") })
        assertEquals(1, timed("second search, on text already read") { search("marca$far") })
        assertEquals(PAGES * LINES, timed("search hitting every line of every page") { search("lorem") })

        scenario.onActivity {
            canvas.close()
            editor.close()
        }
        scenario.close()
        root.deleteRecursively()
    }

    /** About what a text-heavy book page holds, plus some vector art, which is what costs PDFium time. */
    private fun writeLongPdf(file: File) {
        val pdf = PdfDocument()
        val text = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        val square = Paint().apply { color = Color.BLACK }
        val line = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.5f
        }
        repeat(PAGES) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH.toInt(), HEIGHT.toInt(), index + 1).create())
            page.canvas.drawRect(20f, 20f, 60f, 60f, square)
            page.canvas.drawText("marca$index", 80f, 50f, text)
            for (row in 0 until LINES) {
                page.canvas.drawText(LINE, 40f, 80f + row * 12f, text)
            }
            for (i in 0 until 500) {
                val x = (i * 37 % 500).toFloat() + 40f
                page.canvas.drawLine(x, 780f, x + 20f, 820f, line)
            }
            pdf.finishPage(page)
        }
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()
    }

    private fun <T> timed(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val result = block()
        log("$label: ${SystemClock.elapsedRealtime() - start} ms")
        return result
    }

    private fun log(message: String) {
        Log.i("BetterNotesPerf", "pdf: $message")
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val PAGES = 300
        const val LINES = 60
        const val WIDTH = 595f
        const val HEIGHT = 842f
        const val LINE = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore"
    }
}
