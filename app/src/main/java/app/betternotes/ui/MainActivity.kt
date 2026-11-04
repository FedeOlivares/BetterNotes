package app.betternotes.ui

import android.os.Bundle
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
import app.betternotes.BuildConfig
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.perf.CanvasBenchmark
import app.betternotes.perf.PerfProbe
import app.betternotes.ui.editor.EditorActions
import app.betternotes.ui.editor.EditorChrome
import app.betternotes.ui.editor.EditorState
import app.betternotes.ui.editor.ToolStore
import app.betternotes.ui.theme.BetterNotesTheme
import app.betternotes.ui.theme.DarkEditorColors
import app.betternotes.ui.theme.LightEditorColors
import app.betternotes.ui.theme.ThemeMode
import java.io.File

/**
 * For now, opens a scratch note or canvas straight on the drawing surface. The library will replace
 * this as the way in.
 */
class MainActivity : ComponentActivity() {
    private lateinit var canvas: CanvasView
    private var editor: NoteEditor? = null
    private var probe: PerfProbe? = null
    private val readout = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        goFullscreen()

        canvas = CanvasView(this)
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
                var theme by remember { mutableStateOf(tools.theme) }
                var showReadout by remember { mutableStateOf(false) }
                var showingCanvas by remember { mutableStateOf(false) }
                canvas.backdropColor = backdropOf(theme)
                val actions = remember {
                    EditorActions(
                        onBack = ::finish,
                        onZoomToRealSize = { canvas.zoomTo(1f) },
                        onToggleTheme = {
                            theme = if (theme == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                            tools.theme = theme
                        },
                        onToggleScratchKind = {
                            showingCanvas = !showingCanvas
                            openScratch(showingCanvas)
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
        setContentView(
            FrameLayout(this).apply {
                addView(canvas, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                // It covers the canvas, but only its controls take touches. The rest fall through.
                addView(chrome, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            },
        )

        val benchmarkStrokes = if (BuildConfig.PERF_TOOLS) intent.getIntExtra(EXTRA_BENCHMARK_STROKES, 0) else 0
        if (benchmarkStrokes > 0) runBenchmark(benchmarkStrokes) else openScratch(canvasKind = false)
    }

    override fun onStop() {
        super.onStop()
        editor?.saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        canvas.close()
        editor?.close()
        probe?.close()
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
        editor = next
        canvas.open(next)
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

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Asks for the fastest mode at the current resolution: 144 Hz on the Pad 7. The system may refuse. */
    private fun requestHighestRefreshRate() {
        val current = display.mode
        val best = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        window.isFrameRatePowerSavingsBalanced = false
    }

    private fun backdropOf(theme: ThemeMode) =
        (if (theme == ThemeMode.DARK) DarkEditorColors else LightEditorColors).canvasBackdrop.toArgb()

    private companion object {
        /** Debug and profile builds only: run the scripted benchmark on a page of this many strokes. */
        const val EXTRA_BENCHMARK_STROKES = "benchmark_strokes"
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
