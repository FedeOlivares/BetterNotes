package app.betternotes.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.export.ImageExportOptions
import app.betternotes.core.export.ImageFormat
import app.betternotes.core.export.PageSelection
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.Page
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Runs the real ink renderer on the software canvases exports use, and reads the output back. */
@RunWith(AndroidJUnit4::class)
class DocumentExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "export-test")
    private lateinit var store: DocumentStore

    private val paper = 0xFFFFFFFF.toInt()
    private val a4 = Page(1, PageSize.A4, PageBackground(paper))
    private val letter = Page(2, PageSize.Letter, PageBackground(paper))
    private val canvas = Page(3, null, PageBackground(paper))

    /** A thick black line across the page, at height [y], from x 100 to 300. */
    private fun line(id: Long, y: Float): StrokeItem {
        val points = StrokePointsBuilder()
        for (i in 0..40) points.add(100f + i * 5f, y, pressure = 0.5f, timeMs = i * 8f)
        return StrokeItem(id, InkStyle(color = Color.BLACK, width = 10f), points.build())
    }

    @Before
    fun setUp() {
        root.deleteRecursively()
        root.mkdirs()
        store = DocumentStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun exporter(vararg pages: Page) = DocumentExporter(store, "My: note", pages.toList())

    private fun Bitmap.isDark(x: Int, y: Int) = Color.red(getPixel(x, y)) < 100
    private fun Bitmap.isPaper(x: Int, y: Int) = Color.red(getPixel(x, y)) > 240

    private fun render(pdf: ByteArray, pageIndex: Int, onPage: (PdfRenderer.Page, Bitmap) -> Unit) {
        val file = File(root, "out.pdf").apply { writeBytes(pdf) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    onPage(page, bitmap)
                }
            }
        }
    }

    private fun pdfPageCount(pdf: ByteArray): Int {
        val file = File(root, "count.pdf").apply { writeBytes(pdf) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    }

    @Test
    fun pdfKeepsPageSizesAndInk() {
        store.writePage(a4.id, listOf(line(10, 200f)))
        store.writePage(letter.id, listOf(line(11, 400f)))
        val out = ByteArrayOutputStream()
        assertEquals(2, exporter(a4, letter).writePdf(PageSelection.All, out))

        val pdf = out.toByteArray()
        assertEquals(2, pdfPageCount(pdf))
        render(pdf, 0) { page, bitmap ->
            assertEquals(596, page.width)
            assertEquals(842, page.height)
            assertTrue("ink missing", bitmap.isDark(200, 200))
            assertTrue("ink where none was drawn", bitmap.isPaper(200, 400))
        }
        render(pdf, 1) { page, bitmap ->
            assertEquals(612, page.width)
            assertEquals(792, page.height)
            assertTrue("ink missing", bitmap.isDark(200, 400))
        }
    }

    @Test
    fun pdfStaysSharpBecauseInkIsVector() {
        store.writePage(a4.id, listOf(line(10, 200f)))
        val out = ByteArrayOutputStream()
        exporter(a4).writePdf(PageSelection.All, out)
        // A page of one short line: a rasterized A4 page could not be anywhere near this small.
        assertTrue("PDF is ${out.size()} bytes", out.size() < 64 * 1024)
    }

    @Test
    fun pdfDrawsTheTemplate() {
        val lined = Page(4, PageSize.A4, PageBackground(paper, PageTemplate.Lined(spacing = 100f, lineWidth = 4f, color = Color.BLACK)))
        val out = ByteArrayOutputStream()
        exporter(lined).writePdf(PageSelection.All, out)
        render(out.toByteArray(), 0) { _, bitmap ->
            assertTrue("template line missing", bitmap.isDark(300, 100))
            assertTrue("paper missing", bitmap.isPaper(300, 150))
        }
    }

    @Test
    fun pdfHonorsTheSelection() {
        val out = ByteArrayOutputStream()
        assertEquals(1, exporter(a4, letter).writePdf(PageSelection.Pages(listOf(1)), out))
        render(out.toByteArray(), 0) { page, _ -> assertEquals(612, page.width) }
    }

    @Test
    fun imageFollowsTheResolution() {
        store.writePage(a4.id, listOf(line(10, 200f)))
        val files = HashMap<String, ByteArrayOutputStream>()
        val written = exporter(a4).writeImages(
            PageSelection.All,
            ImageExportOptions(ImageFormat.PNG, dpi = 144f),
            { name -> ByteArrayOutputStream().also { files[name] = it } },
        )
        assertEquals(1, written)
        val (name, bytes) = files.entries.single()
        assertEquals("My_ note - 1.png", name)
        val bitmap = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
        assertEquals(1191, bitmap.width)
        assertEquals(1684, bitmap.height)
        assertTrue("ink missing", bitmap.isDark(400, 400))
        assertTrue("paper missing", bitmap.isPaper(400, 800))
    }

    @Test
    fun jpegWritesAnOpaqueImage() {
        store.writePage(a4.id, listOf(line(10, 200f)))
        val out = ByteArrayOutputStream()
        exporter(a4).writeImages(PageSelection.All, ImageExportOptions(ImageFormat.JPEG, dpi = 72f), { out })
        val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        assertTrue("ink missing", bitmap.isDark(200, 200))
    }

    @Test
    fun canvasExportsItsInkAndNothingWhenEmpty() {
        val out = ByteArrayOutputStream()
        assertEquals(0, exporter(canvas).writePdf(PageSelection.All, out))
        assertEquals(0, out.size())

        store.writePage(canvas.id, listOf(line(10, -5000f)))
        assertEquals(1, exporter(canvas).writePdf(PageSelection.All, out))
        render(out.toByteArray(), 0) { page, bitmap ->
            assertTrue("too wide: ${page.width}", page.width in 200..300)
            assertTrue("too tall: ${page.height}", page.height < 100)
            assertTrue("ink missing", bitmap.isDark(page.width / 2, page.height / 2))
        }
    }
}
