package app.betternotes.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.R
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.export.ImageExportOptions
import app.betternotes.core.export.PageSelection
import app.betternotes.core.export.PdfExportOptions
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.Page
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.model.TextItem
import app.betternotes.core.search.HandwritingRecognizer
import app.betternotes.core.search.InkLine
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.PomodoroPhase
import app.betternotes.core.settings.PomodoroSettings
import app.betternotes.core.settings.PomodoroState
import app.betternotes.core.settings.PomodoroStore
import app.betternotes.core.settings.SettingsStore
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.template.PageBackground
import app.betternotes.export.DocumentExporter
import app.betternotes.pomodoro.PomodoroAlarmReceiver
import app.betternotes.pomodoro.PomodoroTimer
import app.betternotes.search.DocumentSearch
import app.betternotes.ui.theme.AppLanguage
import app.betternotes.settings.SettingsRepository
import app.betternotes.ui.settings.SearchPanel
import app.betternotes.ui.settings.SettingsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SearchExportPomodoroTest {
    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val root = File(context.cacheDir, "screens-test")
    private lateinit var store: DocumentStore

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

    private val paper = 0xFFFFFFFF.toInt()
    private val a4 = Page(1, PageSize.A4, PageBackground(paper))
    private val second = Page(2, PageSize.A4, PageBackground(paper))
    private val third = Page(3, PageSize.A4, PageBackground(paper))

    private fun blackLine(id: Long, y: Float): StrokeItem {
        val points = StrokePointsBuilder()
        for (i in 0..40) points.add(100f + i * 5f, y, pressure = 0.5f, timeMs = i * 8f)
        return StrokeItem(id, InkStyle(color = Color.BLACK, width = 10f), points.build())
    }

    private fun pixel(bitmap: Bitmap, x: Int, y: Int) = bitmap.getPixel(x, y)

    private fun pdfPage(bytes: ByteArray, onPage: (PdfRenderer.Page, Bitmap) -> Unit) {
        val file = File(root, "out.pdf").apply { writeBytes(bytes) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    onPage(page, bitmap)
                }
            }
        }
    }

    // ---- Language ----

    @Test
    fun everyShortcutHasAName() {
        assertEquals(EditorAction.entries.size, context.resources.getStringArray(R.array.editor_action_names).size)
    }

    @Test
    fun theAppSpeaksEnglishAndLatinAmericanSpanish() {
        fun inLocale(tag: String): Context {
            val configuration = android.content.res.Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(tag))
            return context.createConfigurationContext(configuration)
        }
        val en = inLocale("en")
        val es = inLocale("es-419")
        assertEquals("Export", en.getString(R.string.export_title))
        assertEquals("Exportar", es.getString(R.string.export_title))
        assertEquals("Configuración", es.getString(R.string.settings_title))
        assertEquals(EditorAction.entries.size, es.resources.getStringArray(R.array.editor_action_names).size)
        assertEquals("Deshacer", es.resources.getStringArray(R.array.editor_action_names)[EditorAction.UNDO.ordinal])
        assertEquals("1 match", en.resources.getQuantityString(R.plurals.search_matches, 1, 1))
        assertEquals("3 coincidencias", es.resources.getQuantityString(R.plurals.search_matches, 3, 3))
        assertTrue(AppLanguage.entries.map { it.tag }.containsAll(listOf("en", "es-419")))
    }

    // ---- Export ----

    @Test
    fun pageScaleResizesAPdfPage() {
        store.writePage(a4.id, listOf(blackLine(10, 200f)))
        val out = ByteArrayOutputStream()
        DocumentExporter(store, "T", listOf(a4)).writePdf(PageSelection.All, out, PdfExportOptions(scale = 0.5f))
        pdfPage(out.toByteArray()) { page, _ ->
            assertEquals(298, page.width)
            assertEquals(421, page.height)
        }
    }

    @Test
    fun invertedPdfTurnsPaperDarkAndInkLight() {
        store.writePage(a4.id, listOf(blackLine(10, 200f)))
        val out = ByteArrayOutputStream()
        DocumentExporter(store, "T", listOf(a4)).writePdf(PageSelection.All, out, PdfExportOptions(invert = true))
        pdfPage(out.toByteArray()) { _, bitmap ->
            assertTrue("paper should be dark", Color.red(pixel(bitmap, 200, 400)) < 40)
            assertTrue("ink should be light", Color.red(pixel(bitmap, 200, 200)) > 215)
        }
    }

    @Test
    fun rasterizedPdfLooksTheSameButIsPictures() {
        store.writePage(a4.id, listOf(blackLine(10, 200f)))
        val vector = ByteArrayOutputStream()
        val raster = ByteArrayOutputStream()
        val exporter = DocumentExporter(store, "T", listOf(a4))
        exporter.writePdf(PageSelection.All, vector)
        exporter.writePdf(PageSelection.All, raster, PdfExportOptions(rasterize = true))
        pdfPage(raster.toByteArray()) { page, bitmap ->
            assertEquals(596, page.width)
            assertTrue("ink missing", Color.red(pixel(bitmap, 200, 200)) < 100)
            assertTrue("paper missing", Color.red(pixel(bitmap, 200, 400)) > 240)
        }
        assertTrue("a picture of a page takes more room than its vectors", raster.size() > vector.size() * 2)
    }

    @Test
    fun invertedImageAndScale() {
        store.writePage(a4.id, listOf(blackLine(10, 200f)))
        val files = HashMap<String, ByteArrayOutputStream>()
        DocumentExporter(store, "T", listOf(a4)).writeImages(
            PageSelection.All,
            ImageExportOptions(dpi = 72f, scale = 2f, invert = true),
            { name -> ByteArrayOutputStream().also { files[name] = it } },
        )
        val bytes = files.values.single()
        val bitmap = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
        assertEquals(1191, bitmap.width)
        assertTrue("paper should be dark", Color.red(pixel(bitmap, 400, 800)) < 40)
        assertTrue("ink should be light", Color.red(pixel(bitmap, 400, 400)) > 215)
    }

    @Test
    fun thumbnailShowsThePage() {
        store.writePage(a4.id, listOf(blackLine(10, 200f)))
        val thumbnail = DocumentExporter(store, "T", listOf(a4)).thumbnail(0, 100)
        assertEquals(100, thumbnail.width)
        assertEquals(142, thumbnail.height)
        assertTrue("ink missing", Color.red(pixel(thumbnail, 34, 34)) < 120)
        assertTrue("paper missing", Color.red(pixel(thumbnail, 50, 100)) > 240)
    }

    // ---- Search and automatic names ----

    private class NoInk : HandwritingRecognizer {
        override val engineId = "none"
        override val isReady = false
        override fun recognize(line: InkLine) = emptyList<String>()
    }

    private class Reads(private val text: String) : HandwritingRecognizer {
        override val engineId = "fixed"
        override val isReady = true
        override fun recognize(line: InkLine) = listOf(text)
    }

    private fun typed(id: Long, text: String) = TextItem(id, Frame(100f, 100f, 300f, 30f), text)

    @Test
    fun searchPanelListsThePagesThatHaveTheText() {
        val pages = listOf(a4, second, third)
        val document = Document(9, "Test", DocumentKind.NOTE, 0, 0, pages)
        val session = DocumentSession.create(store, document)
        store.writePage(third.id, listOf(typed(1, "hola mundo")))
        store.writePage(a4.id, listOf(typed(2, "adios mundo"), typed(3, "otra linea")))
        val editor = NoteEditor(store, session)
        val search = DocumentSearch(store, NoInk())
        lateinit var canvas: CanvasView
        compose.setContent {
            canvas = CanvasView(androidx.compose.ui.platform.LocalContext.current)
            canvas.open(editor)
            SettingsTheme { SearchPanel(editor, search, canvas, onClose = {}) }
        }
        compose.onNode(hasSetTextAction()).performTextInput("mundo")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Page 3", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Page 1", substring = true).assertExists()
        compose.onNodeWithText("Page 2", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 match", substring = true).assertExists()

        compose.onNode(hasSetTextAction()).performTextInput(" inexistente")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No matches").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Page 3", substring = true).assertDoesNotExist()
        search.close()
        editor.close()
    }

    @Test
    fun aNewNoteTakesItsNameFromTheFirstWords() {
        val document = Document(9, "Test", DocumentKind.NOTE, 0, 0, listOf(a4))
        DocumentSession.create(store, document)
        val first = blackLine(10, 100f)
        store.writePage(a4.id, listOf(first))
        val search = DocumentSearch(store, Reads("mi clase de anatomia general y mas"))
        val done = java.util.concurrent.CountDownLatch(1)
        var suggested: String? = null
        instrumentation.runOnMainSync {
            search.onIndexed = {
                search.suggestTitle(document.pages) {
                    suggested = it
                    done.countDown()
                }
            }
            search.refresh(document.pages)
        }
        assertTrue(done.await(20, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("Mi clase de anatomia general y", suggested)
        instrumentation.runOnMainSync { search.close() }
    }

    // ---- Pomodoro ----

    private fun allowNotifications() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun savedSettings(pomodoro: PomodoroSettings) {
        SettingsStore(SettingsRepository.fileIn(context)).write(AppSettings(pomodoro = pomodoro))
    }

    @Test
    fun theAlarmMovesTheTimerOnAndNotifies() {
        allowNotifications()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        savedSettings(PomodoroSettings(autoStartNext = false))
        val state = PomodoroStore(File(context.filesDir, "pomodoro.bnt"))
        state.write(PomodoroState(PomodoroPhase.FOCUS, 0, System.currentTimeMillis() - 1000, null))

        PomodoroAlarmReceiver().onReceive(context, Intent())

        val after = state.read()
        assertEquals(PomodoroPhase.SHORT_BREAK, after.phase)
        assertEquals(1, after.completedFocus)
        assertTrue(!after.isRunning)
        val shown = manager.activeNotifications
        assertEquals(1, shown.size)
        assertEquals(context.getString(R.string.pomodoro_focus_done), shown[0].notification.extras.getString("android.title"))
        manager.cancelAll()
    }

    @Test
    fun aStaleAlarmDoesNothing() {
        allowNotifications()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        savedSettings(PomodoroSettings())
        val state = PomodoroStore(File(context.filesDir, "pomodoro.bnt"))
        val running = PomodoroState().start(PomodoroSettings(), System.currentTimeMillis())
        state.write(running)

        PomodoroAlarmReceiver().onReceive(context, Intent())

        assertEquals(running, state.read())
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun startingSchedulesAnAlarmAndPausingCancelsIt() {
        File(context.filesDir, "pomodoro.bnt").delete()
        val timer = PomodoroTimer(context)
        val settings = PomodoroSettings()
        timer.start(settings)
        assertNotNull(timer.state.value.endsAtMs)
        assertTrue(PomodoroStore(File(context.filesDir, "pomodoro.bnt")).read().isRunning)
        timer.pause(settings)
        assertNull(timer.state.value.endsAtMs)
        timer.reset()
        assertEquals(PomodoroState(), PomodoroStore(File(context.filesDir, "pomodoro.bnt")).read())
    }
}
