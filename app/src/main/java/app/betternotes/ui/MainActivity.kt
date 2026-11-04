package app.betternotes.ui

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.betternotes.BuildConfig
import app.betternotes.R
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.search.isPlaceholderTitle
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.AutoName
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.library.LibraryRepository
import app.betternotes.perf.CanvasBenchmark
import app.betternotes.perf.HandwritingCheck
import app.betternotes.perf.PerfProbe
import app.betternotes.library.LibraryPreferences
import app.betternotes.ui.editor.EditorActions
import app.betternotes.ui.editor.EditorChrome
import app.betternotes.ui.editor.EditorState
import app.betternotes.ui.editor.ToolStore
import app.betternotes.ui.theme.BetterNotesTheme
import app.betternotes.ui.theme.DarkEditorColors
import app.betternotes.ui.theme.LightEditorColors
import app.betternotes.ui.theme.ThemeMode
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
    private val searchState = mutableStateOf<DocumentSearch?>(null)

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

        val tools = ToolStore(this)
        val state = EditorState(canvas, tools)
        val chrome = ComposeView(this).apply {
            setContent {
                val library = remember { LibraryPreferences.get(this@MainActivity) }
                val theme = library.theme
                var showReadout by remember { mutableStateOf(false) }
                var showingCanvas by remember { mutableStateOf(false) }
                canvas.backdropColor = backdropOf(theme)
                val actions = remember {
                    EditorActions(
                        onBack = ::finish,
                        onZoomToRealSize = { canvas.zoomTo(1f) },
                        onToggleTheme = {
                            library.updateTheme(if (theme == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
                        },
                        onToggleScratchKind = {
                            showingCanvas = !showingCanvas
                            openScratch(showingCanvas)
                        },
                        onOpenSettings = { shownOverlay.value = SettingsOverlay.SCREEN },
                        documentId = { editor?.document?.id },
                        onOpenDocument = { id ->
                            canvas.rememberPage()
                            editor?.saveNow()
                            openDocument(id)
                        },
                        onTogglePerfReadout = if (BuildConfig.PERF_TOOLS) ({ showReadout = !showReadout }) else null,
                    )
                }
                BetterNotesTheme(theme) {
                    EditorChrome(state, actions)
                    if (showReadout && readout.value.isNotEmpty()) PerfReadout(readout.value)
                }
            }
        }
        val overlays = ComposeView(this).apply {
            setContent {
                SettingsOverlayHost(
                    shownOverlay.value,
                    settings,
                    canvas,
                    onDismiss = { shownOverlay.value = null },
                    search = searchState.value,
                )
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(canvas, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                // It covers the canvas, but only its controls take touches. The rest fall through.
                addView(chrome, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
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
        return (action != null && (canvas.perform(action) || openOverlayFor(action))) || super.onKeyDown(keyCode, event)
    }

    /** The shortcuts that open a screen. They toggle, so the same keys close it again. */
    private fun openOverlayFor(action: EditorAction): Boolean {
        val overlay = when (action) {
            EditorAction.SEARCH -> SettingsOverlay.SEARCH
            EditorAction.EXPORT -> SettingsOverlay.EXPORT
            EditorAction.SETTINGS -> SettingsOverlay.SCREEN
            else -> return false
        }
        shownOverlay.value = if (shownOverlay.value == overlay) null else overlay
        return true
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
        searchState.value = nextSearch
        nextSearch.onIndexed = { nameFromText(next, nextSearch) }
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

    /**
     * Gives a note the first words written in it as its name, once they can be read, if it still has the
     * name the app gave it. A name the owner chose is never touched.
     */
    private fun nameFromText(target: NoteEditor, from: DocumentSearch) {
        if (settings.settings.value.autoName != AutoName.AUTO) return
        val document = target.document
        if (!isPlaceholderTitle(document.title, placeholderTitles(document.createdAtMs))) return
        from.suggestTitle(document.pages) { suggested ->
            // The owner may have renamed it while this was being worked out.
            if (suggested != null && target === editor && isPlaceholderTitle(document.title, placeholderTitles(document.createdAtMs))) {
                document.title = suggested
            }
        }
    }

    /** Every name a new note starts with, in both languages, as the language may have changed since. */
    private fun placeholderTitles(createdAtMs: Long): List<String> {
        val date = java.time.Instant.ofEpochMilli(createdAtMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        return listOf("en", "es-419").flatMap { tag ->
            val configuration = android.content.res.Configuration(resources.configuration)
            configuration.setLocale(java.util.Locale.forLanguageTag(tag))
            val localized = createConfigurationContext(configuration)
            listOf(
                localized.getString(R.string.settings_note_dated, date),
                localized.getString(R.string.settings_note_untitled),
                "Scratch note",
                "Scratch canvas",
            )
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

    private fun backdropOf(theme: ThemeMode) =
        (if (theme == ThemeMode.DARK) DarkEditorColors else LightEditorColors).canvasBackdrop.toArgb()

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

@androidx.compose.runtime.Composable
private fun PerfReadout(text: String) {
    Box(Modifier.padding(12.dp), contentAlignment = Alignment.BottomStart) {
        Text(
            text,
            color = Color(0xFFB8F0C0),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(Color(0xE01C1E22), RoundedCornerShape(8.dp))
                .padding(8.dp),
        )
    }
}
