package app.betternotes.ui

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.betternotes.BuildConfig
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.library.LibraryRepository
import app.betternotes.perf.CanvasBenchmark
import app.betternotes.perf.HandwritingCheck
import app.betternotes.perf.PerfProbe
import app.betternotes.search.DocumentSearch
import app.betternotes.search.MlKitRecognizer
import app.betternotes.settings.SettingsRepository
import app.betternotes.settings.actionFor
import app.betternotes.ui.settings.SettingsOverlay
import app.betternotes.ui.settings.SettingsOverlayHost
import kotlinx.coroutines.launch
import java.io.File

/**
 * The drawing surface, showing the document the library asked for. Started with no document, as the
 * tests and measuring scripts do, it opens a scratch note or canvas.
 */
class MainActivity : ComponentActivity() {
    private lateinit var canvas: CanvasView
    private var editor: NoteEditor? = null
    private var probe: PerfProbe? = null
    private var search: DocumentSearch? = null
    private lateinit var settings: SettingsRepository
    private val readout = mutableStateOf("")
    private val shownOverlay = mutableStateOf<SettingsOverlay?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()

        canvas = CanvasView(this)
        settings = SettingsRepository(this)
        canvas.onInputModeChanged = ::rememberFingerMode
        // Applied once here so that the document opens with them, and again whenever they change.
        apply(settings.settings.value)
        lifecycleScope.launch { settings.settings.collect(::apply) }
        if (BuildConfig.PERF_TOOLS) {
            val perf = PerfProbe(window)
            perf.onSummary = { readout.value = it }
            perf.logDisplay(display, canvas.viewport.screenDpi)
            canvas.observer = perf
            probe = perf
        }

        val chrome = ComposeView(this).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    DevChrome(canvas, readout, ::openScratch) { shownOverlay.value = it }
                }
            }
        }
        val overlays = ComposeView(this).apply {
            setContent {
                SettingsOverlayHost(shownOverlay.value, settings, canvas, onDismiss = { shownOverlay.value = null })
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(canvas, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(
                    chrome,
                    FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL),
                )
                addView(overlays, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            },
        )

        val benchmarkStrokes = if (BuildConfig.PERF_TOOLS) intent.getIntExtra(EXTRA_BENCHMARK_STROKES, 0) else 0
        when {
            benchmarkStrokes > 0 -> runBenchmark(benchmarkStrokes)
            intent.hasExtra(EXTRA_DOCUMENT_ID) -> openDocument(intent.getLongExtra(EXTRA_DOCUMENT_ID, 0L))
            else -> openScratch(canvasKind = false)
        }
        if (BuildConfig.DEV_CHECKS) HandwritingCheck.runIfAsked(intent)
    }

    /** Keys get here only if no view took them, so typing in a text field never fires a shortcut. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = settings.settings.value.keyBindings.actionFor(event)
        return (action != null && canvas.perform(action)) || super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        canvas.rememberPage()
        editor?.saveNow()
        LibraryRepository.get(this).refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        canvas.close()
        editor?.close()
        search?.close()
        probe?.close()
    }

    private fun openDocument(id: Long) {
        val store = LibraryRepository.get(this).documentStore(id)
        show(NoteEditor(store, DocumentSession.open(store)))
    }

    private fun openScratch(canvasKind: Boolean) {
        val name = if (canvasKind) "scratch-canvas" else "scratch-note"
        val store = DocumentStore(File(filesDir, "documents/$name"))
        val session = if (store.exists()) {
            DocumentSession.open(store)
        } else {
            val background = PageBackground(template = PageTemplate.Dotted())
            val now = System.currentTimeMillis()
            val document = if (canvasKind) {
                Document.newCanvas("Scratch canvas", background, now)
            } else {
                Document.newNote("Scratch note", PageSize.A4, background, now)
            }
            DocumentSession.create(store, document)
        }
        show(NoteEditor(store, session))
    }

    private fun show(next: NoteEditor) {
        canvas.close()
        editor?.close()
        search?.close()
        editor = next
        canvas.open(next)
        val nextSearch = DocumentSearch(next.store, MlKitRecognizer())
        search = nextSearch
        next.onSaved = { nextSearch.refresh(next.document.pages) }
        nextSearch.refresh(next.document.pages)
    }

    private fun runBenchmark(strokes: Int) {
        val perf = probe ?: return
        val store = DocumentStore(File(cacheDir, "benchmark"))
        store.root.deleteRecursively()
        val document = CanvasBenchmark.writeDocument(store, strokes)
        CanvasBenchmark.logMeshCost(store, document)
        val next = NoteEditor(store, DocumentSession.open(store))
        show(next)
        canvas.post { CanvasBenchmark.run(canvas, perf) { finish() } }
    }

    private fun apply(next: AppSettings) {
        canvas.applySettings(next)
        if (next.canvas.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (next.canvas.hideSystemBars) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** The finger mode button and the sheet's "single finger" are one setting, so it survives a restart. */
    private fun rememberFingerMode(mode: InputMode) {
        settings.update { current ->
            val finger = current.gestures.oneFinger
            val next = when {
                mode == InputMode.FINGER_DRAW -> OneFingerSetting.DRAW
                mode == InputMode.STYLUS_DRAW && finger == OneFingerSetting.DRAW -> OneFingerSetting.SCROLL
                else -> finger
            }
            current.copy(gestures = current.gestures.copy(oneFinger = next))
        }
    }

    companion object {
        /** The library's id of the document to open. */
        const val EXTRA_DOCUMENT_ID = "document_id"

        /** What the user came to do in a new document, if anything. So far only [START_RECORDING]. */
        const val EXTRA_START_ACTION = "start_action"
        const val START_RECORDING = "recording"

        /** Debug and profile builds only: run the scripted benchmark on a page of this many strokes. */
        private const val EXTRA_BENCHMARK_STROKES = "benchmark_strokes"
    }
}
