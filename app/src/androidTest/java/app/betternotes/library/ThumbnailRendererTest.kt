package app.betternotes.library

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.template.PageBackground
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Draws with ink's real renderer on a device, off the main thread, as the home screen does. */
@RunWith(AndroidJUnit4::class)
class ThumbnailRendererTest {
    private val paper = 0xFFFAF6EA.toInt()
    private lateinit var store: DocumentStore

    @Before
    fun setUp() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "thumbnail-test")
        root.deleteRecursively()
        store = DocumentStore(root)
    }

    @After
    fun tearDown() {
        store.root.deleteRecursively()
    }

    private fun line(x0: Float, y: Float, x1: Float) = StrokeItem(
        id = x0.toLong() + 1,
        style = InkStyle(color = Color.BLACK, width = 8f),
        points = StrokePointsBuilder().apply {
            add(x0, y, 0.8f, timeMs = 0f)
            add((x0 + x1) / 2, y, 0.8f, timeMs = 10f)
            add(x1, y, 0.8f, timeMs = 20f)
        }.build(),
    )

    private fun Bitmap.hasInkIn(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        for (y in top until bottom) {
            for (x in left until right) {
                if (Color.luminance(getPixel(x, y)) < 0.2f) return true
            }
        }
        return false
    }

    @Test
    fun aNoteShowsThePaperAndTheInkAtTheTopOfItsPage() {
        val document = Document.newNote("Note", PageSize.A4, PageBackground(color = paper), 0L)
        store.writeDocument(document)
        // Near the top, and far below what the tile shows.
        store.writePage(document.pages[0].id, listOf(line(100f, 50f, 300f), line(100f, 800f, 300f)))

        val bitmap = ThumbnailRenderer.render(store)

        assertEquals(ThumbnailRenderer.WIDTH_PX, bitmap.width)
        assertEquals(paper, bitmap.getPixel(5, 5))
        assertTrue(bitmap.hasInkIn(120, 50, 360, 70))
        assertFalse(bitmap.hasInkIn(0, 100, bitmap.width, bitmap.height))
    }

    @Test
    fun aWidePageSitsInTheMiddleOfTheTile() {
        val document = Document.newNote("Wide", PageSize(842f, 200f), PageBackground(color = paper), 0L)
        store.writeDocument(document)
        store.writePage(document.pages[0].id, listOf(line(100f, 100f, 700f)))

        val bitmap = ThumbnailRenderer.render(store)

        assertTrue(bitmap.hasInkIn(200, bitmap.height / 2 - 12, 500, bitmap.height / 2 + 12))
    }

    @Test
    fun aCanvasShowsAllOfItsInkWhereverItIs() {
        val document = Document.newCanvas("Canvas", PageBackground(color = paper), 0L)
        store.writeDocument(document)
        store.writePage(document.pages[0].id, listOf(line(-5000f, 3000f, -4800f), line(2000f, -1000f, 2200f)))

        val bitmap = ThumbnailRenderer.render(store)

        assertTrue(bitmap.hasInkIn(0, 0, bitmap.width / 2, bitmap.height))
        assertTrue(bitmap.hasInkIn(bitmap.width / 2, 0, bitmap.width, bitmap.height))
    }

    @Test
    fun anEmptyCanvasIsJustPaper() {
        val document = Document.newCanvas("Empty", PageBackground(color = paper), 0L)
        store.writeDocument(document)

        assertEquals(paper, ThumbnailRenderer.render(store).getPixel(300, 300))
    }

    @Test
    fun aPdfShowsItsPageWithTheInkOnTop() {
        PdfDocument().apply {
            val page = startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
            page.canvas.drawRect(0f, 0f, 300f, 40f, Paint().apply { color = Color.RED })
            finishPage(page)
            FileOutputStream(store.assetFile("source.pdf")).use { writeTo(it) }
            close()
        }
        val document = Document.fromPdf("Pdf", "source.pdf", listOf(PageSize(600f, 800f)), 0L)
        store.writeDocument(document)
        store.writePage(document.pages[0].id, listOf(line(100f, 100f, 300f)))

        val bitmap = ThumbnailRenderer.render(store)

        assertEquals(Color.WHITE, bitmap.getPixel(700, 500))
        assertTrue(Color.red(bitmap.getPixel(100, 20)) > 200 && Color.green(bitmap.getPixel(100, 20)) < 80)
        assertTrue(bitmap.hasInkIn(130, 110, 350, 130))
    }
}
