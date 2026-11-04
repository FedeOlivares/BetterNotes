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
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.edit.UpdatePage
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageSource
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.search.TextMatch
import app.betternotes.core.template.PageBackground
import app.betternotes.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the platform's real PDF engine on PDFs written with the platform's PdfDocument, and checks what
 * reaches the screen by copying its pixels. Says nothing about speed on a real tablet.
 */
@RunWith(AndroidJUnit4::class)
class PdfTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var root: File
    private lateinit var documentsDir: File
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var canvas: CanvasView
    private var editor: NoteEditor? = null

    @Before
    fun setUp() {
        root = File(context.cacheDir, "pdf-test")
        root.deleteRecursively()
        documentsDir = File(root, "documents").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        scenario?.let { open ->
            open.onActivity {
                canvas.close()
                editor?.close()
            }
            open.close()
        }
    }

    // ---- Import ----

    @Test
    fun importKeepsEachPagesOwnSizeAndTheFileName() {
        val pdf = writePdf("Mi libro.pdf", listOf(200 to 300, 612 to 792, 500 to 100))
        val imported = import(pdf)
        assertEquals("Mi libro", imported.document.title)
        assertEquals(
            listOf(PageSize(200f, 300f), PageSize(612f, 792f), PageSize(500f, 100f)),
            imported.document.pages.map { it.size },
        )
        assertEquals(
            listOf(0, 1, 2),
            imported.document.pages.map { (it.source as PageSource.PdfPage).pageIndex },
        )
        assertEquals(imported.document.pages, imported.store.readDocument().pages)
        assertEquals(java.lang.Long.toHexString(imported.document.id), imported.store.root.name)
    }

    @Test
    fun aPdfPageRefusesANewSizeEvenAfterAReopen() {
        val imported = import(writePdf("a.pdf", listOf(200 to 300)))
        val session = DocumentSession.open(imported.store)
        val page = session.document.pages[0]
        assertFalse(page.isResizable)
        try {
            session.history.perform(UpdatePage(page, page.copy(size = PageSize.A4)), session)
            fail("The page changed size")
        } catch (_: IllegalArgumentException) {
            assertEquals(PageSize(200f, 300f), session.document.pages[0].size)
        }
    }

    @Test
    fun aFileThatIsNotAPdfFailsAndLeavesNothingBehind() {
        val bad = File(root, "bad.pdf").apply { writeText("this is not a pdf") }
        val result = PdfImporter.import(context.contentResolver, Uri.fromFile(bad), documentsDir)
        assertTrue(result is PdfImportResult.Failed)
        assertEquals(emptyList<String>(), documentsDir.list()!!.toList())
    }

    @Test
    fun theImportedCopyDoesNotDependOnTheOriginal() {
        val pdf = writePdf("a.pdf", listOf(200 to 300))
        val bytes = pdf.readBytes()
        val imported = import(pdf)
        pdf.delete()
        val source = imported.document.pages[0].source as PageSource.PdfPage
        assertArrayEquals(bytes, imported.store.assetFile(source.assetId).readBytes())
        PdfThumbnails.render(imported.store, source, 50)
    }

    @Test
    fun anEncryptedPdfAsksForItsPasswordAndIsStoredOpen() {
        // Made with pypdf: one 200 by 300 page with the usual square, user password "clave".
        val file = File(root, "encrypted.pdf")
        instrumentation.context.assets.open("encrypted.pdf").use { input ->
            FileOutputStream(file).use { input.copyTo(it) }
        }
        val uri = Uri.fromFile(file)
        assertEquals(PdfImportResult.NeedsPassword, PdfImporter.import(context.contentResolver, uri, documentsDir))
        assertEquals(PdfImportResult.NeedsPassword, PdfImporter.import(context.contentResolver, uri, documentsDir, "mala"))
        assertEquals(emptyList<String>(), documentsDir.list()!!.toList())

        val imported = PdfImporter.import(context.contentResolver, uri, documentsDir, "clave") as PdfImportResult.Imported
        assertEquals(PageSize(200f, 300f), imported.document.pages[0].size)
        // It opens from now on with no password, which only a decrypted copy can do.
        val bitmap = PdfThumbnails.render(imported.store, imported.document.pages[0].source as PageSource.PdfPage, 200)
        assertTrue(Integer.toHexString(bitmap.getPixel(40, 40)), isDark(bitmap.getPixel(40, 40)))
        assertEquals(listOf("source.pdf"), imported.store.assetFile("source.pdf").parentFile!!.list()!!.toList())
    }

    // ---- Thumbnails ----

    @Test
    fun aThumbnailShowsThePageOnWhite() {
        val imported = import(writePdf("a.pdf", listOf(200 to 400)))
        val source = imported.document.pages[0].source as PageSource.PdfPage
        val bitmap = PdfThumbnails.render(imported.store, source, 100)
        assertEquals(100, bitmap.width)
        assertEquals(200, bitmap.height)
        // writePdf paints a black square at 20..60 points on every page.
        assertTrue(Integer.toHexString(bitmap.getPixel(20, 20)), isDark(bitmap.getPixel(20, 20)))
        assertTrue(Integer.toHexString(bitmap.getPixel(50, 100)), isWhite(bitmap.getPixel(50, 100)))
    }

    // ---- The canvas ----

    @Test
    fun thePageReachesTheScreenAtTheRightPlaceAndSize() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600))))
        // Inside the square, then just outside each of its edges.
        waitForPixel(40f, 40f, ::isDark)
        waitForPixel(70f, 40f, ::isWhite)
        waitForPixel(40f, 70f, ::isWhite)
        waitForPixel(10f, 40f, ::isWhite)
    }

    @Test
    fun zoomingInPaintsEdgesSharply() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600))))
        onMain {
            canvas.zoomTo(8f)
            val viewport = canvas.viewport
            viewport.panX = canvas.width / 2f - 60f * viewport.pxPerPt
            viewport.panY = canvas.height / 2f - 40f * viewport.pxPerPt
            canvas.zoomTo(8f)
        }
        // A stretched preview would smear this edge over many pixels. A sharp tile keeps it to a few.
        val scale = onMain { canvas.viewport.pxPerPt }
        waitForPixel(60f - 3f / scale, 40f, ::isDark)
        waitForPixel(60f + 3f / scale, 40f, ::isWhite)
    }

    @Test
    fun aPinchEndsWithSharpEdges() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600))))
        waitForPixel(40f, 40f, ::isDark)
        onMain {
            // Far enough past the stretch limit for tiles to be painted again during the pinch.
            val x = canvas.viewport.toScreenX(60f)
            val y = canvas.viewport.toScreenY(40f)
            canvas.onTransform(0f, 0f, 3f, x, y)
            canvas.onTransform(0f, 0f, 3f, x, y)
            canvas.onTransformEnd()
        }
        val scale = onMain { canvas.viewport.pxPerPt }
        waitForPixel(60f - 3f / scale, 40f, ::isDark)
        waitForPixel(60f + 3f / scale, 40f, ::isWhite)
    }

    @Test
    fun aFastTripThroughManyPagesStillEndsSharp() {
        openOnCanvas(import(writePdf("a.pdf", List(60) { 400 to 600 })))
        // Each stop at this zoom asks for dozens of tiles, which can be more than the queue holds.
        for (page in 59 downTo 0) {
            onMain {
                canvas.zoomTo(8f)
                val viewport = canvas.viewport
                viewport.panX = canvas.width / 2f - 60f * viewport.pxPerPt
                viewport.panY = canvas.height / 2f - (40f + page * 612f) * viewport.pxPerPt
                canvas.zoomTo(8f)
            }
            SystemClock.sleep(16)
        }
        val scale = onMain { canvas.viewport.pxPerPt }
        waitForPixel(60f - 3f / scale, 40f, ::isDark)
        waitForPixel(60f + 3f / scale, 40f, ::isWhite)
    }

    @Test
    fun aStylusStrokeBecomesInkOnThePageAndLeavesThePdfAlone() {
        val imported = import(writePdf("a.pdf", listOf(400 to 600)))
        val source = imported.document.pages[0].source as PageSource.PdfPage
        val before = imported.store.assetFile(source.assetId).readBytes()
        openOnCanvas(imported)
        waitForPixel(40f, 40f, ::isDark)

        stylusStroke(90f, 200f, 170f, 200f)
        val pageId = imported.document.pages[0].id
        waitUntil { content(pageId).size == 1 }
        val stroke = content(pageId)[0] as StrokeItem
        assertEquals(90f, stroke.points.x(0), 1f)
        assertEquals(200f, stroke.points.y(0), 1f)
        waitForPixel(130f, 200f) { !isWhite(it) }
        // The ink sits over the PDF, which still shows beside it.
        waitForPixel(40f, 40f, ::isDark)

        onMain { canvas.undo() }
        waitForPixel(130f, 200f, ::isWhite)
        onMain { canvas.redo() }
        waitForPixel(130f, 200f) { !isWhite(it) }
        onMain { editor!!.saveNow() }
        assertArrayEquals(before, imported.store.assetFile(source.assetId).readBytes())
        assertEquals(1, DocumentSession.open(imported.store).content(pageId).size)
    }

    // ---- Search ----

    @Test
    fun searchIgnoresCaseAndAccentsAndFindsThePlace() {
        val imported = import(
            writePdf("a.pdf", listOf(400 to 600, 400 to 600), text = { if (it == 1) "Camión rojo" else "nada" }),
        )
        openOnCanvas(imported)
        val matches = search("CAMION")
        assertEquals(1, matches.size)
        assertEquals(imported.document.pages[1].id, matches[0].pageId)
        val rect = matches[0].rects.single()
        // writePdf puts the text's baseline at x 50, y 300.
        assertEquals(50f, rect.left, 3f)
        assertTrue("Top ${rect.top}", rect.top in 270f..300f)
        assertTrue("Bottom ${rect.bottom}", rect.bottom in 295f..310f)
    }

    @Test
    fun searchFindsEveryMatchOnEveryPageInOrder() {
        val imported = import(
            writePdf("a.pdf", List(3) { 400 to 600 }, text = { if (it == 1) "nada" else "uno dos uno" }),
        )
        openOnCanvas(imported)
        val pages = imported.document.pages
        val matches = search("uno")
        assertEquals(listOf(pages[0].id, pages[0].id, pages[2].id, pages[2].id), matches.map { it.pageId })
        assertTrue(matches[0].rects[0].left < matches[1].rects[0].left)
    }

    @Test
    fun aMatchAcrossALineBreakGetsARectangleOnEachLine() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600), text = { "primera linea\nsegunda linea" })))
        val rects = search("linea segunda").single().rects
        assertEquals(2, rects.size)
        assertTrue("$rects", rects[0].bottom <= rects[1].top + 4f)
        assertTrue("$rects", rects[0].left > rects[1].left)
    }

    @Test
    fun aHyphenThatOnlyBreaksALineDoesNotStopAMatch() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600), text = { "una pala-\nbra partida" })))
        assertEquals(2, search("palabra").single().rects.size)
    }

    @Test
    fun rectanglesAreRightAfterAnAccentAndALigature() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600), text = { "Camión ﬁno destino" })))
        val first = search("camion").single().rects.single()
        val last = search("destino").single().rects.single()
        assertTrue("$first $last", first.right <= last.left)
        assertEquals(1, search("fino").size)
    }

    @Test
    fun aSearchWithNoHitsFinishesEmpty() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600), text = { "algo" })))
        assertEquals(emptyList<TextMatch>(), search("ausente"))
    }

    @Test
    fun aMatchIsMarkedOnTheScreen() {
        openOnCanvas(import(writePdf("a.pdf", listOf(400 to 600), text = { "encontrado" })))
        waitForPixel(40f, 40f, ::isDark)
        val match = search("encontrado").single()
        val rect = match.rects.single()
        val x = rect.left + 1f
        val y = rect.top + 1f
        waitForPixel(x, y, ::isWhite)
        onMain { canvas.showMatches(listOf(match), match) }
        waitForPixel(x, y) { !isWhite(it) }
        onMain { canvas.showMatches(emptyList()) }
        waitForPixel(x, y, ::isWhite)
    }

    @Test
    fun revealScrollsToAMatchOnAFarPage() {
        val imported = import(writePdf("a.pdf", List(20) { 400 to 600 }, text = { if (it == 15) "lejos" else "" }))
        openOnCanvas(imported)
        val match = search("lejos").single()
        onMain { canvas.reveal(match) }
        val visible = onMain { canvas.viewport.visibleWorld(canvas.width, canvas.height) }
        val origin = 15 * (600f + 12f)
        val rect = match.rects[0]
        assertTrue("$visible", visible.top <= origin + rect.top && origin + rect.bottom <= visible.bottom)
    }

    @Test
    fun aNoteWithoutAPdfHasNoWorkerAndFinishesASearchAtOnce() {
        val store = DocumentStore(File(root, "note"))
        val note = Document.newNote("n", PageSize.A4, PageBackground(), 0L)
        val noteEditor = onMain { NoteEditor(store, DocumentSession.create(store, note)) }
        var finished = false
        onMain { noteEditor.searchPdfText("x", { fail("A note has no PDF text") }, { finished = true }) }
        assertTrue(finished)
        onMain { noteEditor.close() }
    }

    // ---- Helpers ----

    /** A PDF whose pages each have a black square at 20..60 points, and optional text at 50,300. */
    private fun writePdf(name: String, sizes: List<Pair<Int, Int>>, text: (Int) -> String = { "" }): File {
        val out = File(root, name)
        val pdf = PdfDocument()
        val square = Paint().apply { color = Color.BLACK }
        val letters = Paint().apply {
            color = Color.DKGRAY
            textSize = 24f
        }
        sizes.forEachIndexed { index, (width, height) ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
            page.canvas.drawRect(20f, 20f, 60f, 60f, square)
            val words = text(index)
            words.lineSequence().forEachIndexed { line, part ->
                if (part.isNotEmpty()) page.canvas.drawText(part, 50f, 300f + line * 30f, letters)
            }
            pdf.finishPage(page)
        }
        FileOutputStream(out).use(pdf::writeTo)
        pdf.close()
        return out
    }

    private fun import(file: File): PdfImportResult.Imported {
        val result = PdfImporter.import(context.contentResolver, Uri.fromFile(file), documentsDir)
        if (result is PdfImportResult.Failed) throw result.cause
        return result as PdfImportResult.Imported
    }

    private fun openOnCanvas(imported: PdfImportResult.Imported) {
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        launched.onActivity { activity ->
            val opened = NoteEditor(imported.store, DocumentSession.open(imported.store))
            editor = opened
            canvas = CanvasView(activity)
            activity.setContentView(canvas)
            canvas.open(opened)
        }
        waitUntil { canvas.width > 0 && canvas.isAttachedToWindow }
    }

    private fun content(pageId: Long): PageContent = onMain { editor!!.content(pageId) }

    private fun search(query: String): List<TextMatch> {
        val found = ArrayList<TextMatch>()
        val done = CountDownLatch(1)
        onMain { editor!!.searchPdfText(query, { found += it }, { done.countDown() }) }
        assertTrue("The search did not finish", done.await(10, TimeUnit.SECONDS))
        return found
    }

    /** Waits for the screen pixel over a point on page 0 to pass [test], since rendering takes time. */
    private fun waitForPixel(worldX: Float, worldY: Float, test: (Int) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var last = 0
        while (SystemClock.uptimeMillis() < deadline) {
            last = pixelAt(worldX, worldY)
            if (test(last)) return
            SystemClock.sleep(50)
        }
        fail("Pixel at $worldX,$worldY stayed ${Integer.toHexString(last)}")
    }

    private fun pixelAt(worldX: Float, worldY: Float): Int {
        val (x, y) = onMain {
            val location = IntArray(2)
            canvas.getLocationInWindow(location)
            val viewport = canvas.viewport
            viewport.toScreenX(worldX).toInt() + location[0] to viewport.toScreenY(worldY).toInt() + location[1]
        }
        // A copy from outside the window fails and leaves 0, which would pass for a dark pixel.
        assertTrue("$worldX,$worldY is off screen", onMain { x in 0 until canvas.width && y in 0 until canvas.height })
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        val window = AtomicReference<Window>()
        scenario!!.onActivity { window.set(it.window) }
        PixelCopy.request(window.get(), Rect(x, y, x + 1, y + 1), bitmap, { done.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return bitmap.getPixel(0, 0)
    }

    private fun isDark(color: Int) = Color.red(color) < 60 && Color.green(color) < 60 && Color.blue(color) < 60

    private fun isWhite(color: Int) = Color.red(color) > 245 && Color.green(color) > 245 && Color.blue(color) > 245

    private fun stylusStroke(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val (startX, startY, endX, endY) = onMain {
            val viewport = canvas.viewport
            listOf(viewport.toScreenX(fromX), viewport.toScreenY(fromY), viewport.toScreenX(toX), viewport.toScreenY(toY))
        }
        val steps = 20
        val down = SystemClock.uptimeMillis() - (steps + 1) * 8
        var time = down
        send(event(down, time, MotionEvent.ACTION_DOWN, startX, startY))
        for (i in 1..steps) {
            time += 8
            val t = i.toFloat() / steps
            send(event(down, time, MotionEvent.ACTION_MOVE, startX + (endX - startX) * t, startY + (endY - startY) * t))
        }
        send(event(down, time + 8, MotionEvent.ACTION_UP, endX, endY))
    }

    private fun event(down: Long, time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val device = InputDevice.getDeviceIds().first { id ->
            val input = InputDevice.getDevice(id)
            input != null && input.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                input.getMotionRange(MotionEvent.AXIS_PRESSURE) != null
        }
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            down,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            device,
            0,
            InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS,
            0,
        )
    }

    private fun send(event: MotionEvent) {
        onMain { canvas.dispatchTouchEvent(event) }
        event.recycle()
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!onMain(condition)) {
            assertTrue("Timed out waiting", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(10)
        }
    }
}
