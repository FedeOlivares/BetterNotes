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
import app.betternotes.core.format.Binder
import app.betternotes.core.format.DocumentSession
import app.betternotes.library.CoverDraft
import app.betternotes.library.MediaImport
import app.betternotes.library.PhotoLayout
import app.betternotes.pdf.PdfImportResult
import app.betternotes.pdf.PdfImporter
import app.betternotes.ui.editor.SourceEntry
import app.betternotes.ui.editor.SourceKind
import app.betternotes.ui.editor.SourceRequest
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import app.betternotes.ui.editor.DocumentInfo
import app.betternotes.ui.editor.EditorActions
import app.betternotes.ui.editor.EditorChrome
import app.betternotes.ui.editor.EditorState
import app.betternotes.ui.editor.ToolStore
import app.betternotes.ui.split.PaneFrame
import app.betternotes.ui.split.SplitHost
import app.betternotes.ui.split.SplitLayout
import app.betternotes.ui.split.SplitPane
import app.betternotes.ui.theme.BetterNotesTheme
import app.betternotes.ui.theme.DarkEditorColors
import app.betternotes.ui.theme.LightEditorColors
import app.betternotes.ui.theme.ThemeMode
import app.betternotes.ui.theme.templateLanguage
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
    private lateinit var tools: ToolStore
    private lateinit var firstState: EditorState

    /** Holds the first pane, and the second while the screen is split. */
    private lateinit var split: SplitLayout
    private lateinit var firstPane: PaneFrame
    private var second: SplitPane? = null

    /** The pane the owner touched last: the second one, or null for the first. The settings, search and export screens work on it. */
    private var active by mutableStateOf<SplitPane?>(null)
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

        tools = ToolStore(this)
        val state = EditorState(canvas, tools)
        firstState = state
        state.onStickyOpacity = ::saveStickyOpacity
        state.onAddSource = { request -> addSource(request, state) }
        val chrome = ComposeView(this).apply {
            setContent {
                val library = remember { LibraryPreferences.get(this@MainActivity) }
                val theme = library.theme
                var showReadout by remember { mutableStateOf(false) }
                var showingCanvas by remember { mutableStateOf(false) }
                canvas.backdropColor = backdropOf(theme)
                val actions = remember {
                    EditorActions(
                        onBack = { if (second != null) closeFirstPane() else finish() },
                        onZoomToRealSize = { canvas.zoomTo(1f) },
                        onToggleTheme = library::toggleTheme,
                        onToggleScratchKind = {
                            showingCanvas = !showingCanvas
                            openScratch(showingCanvas)
                        },
                        onOpenSettings = { shownOverlay.value = SettingsOverlay.SCREEN },
                        documentId = { editor?.document?.id },
                        documentInfo = {
                            editor?.document?.let { DocumentInfo(it.title, it.pages.size - if (it.coverId != null) 1 else 0,it.modifiedAtMs, it.createdAtMs) }
                        },
                        onOpenOverlay = { shownOverlay.value = it },
                        onRename = { name ->
                            val repository = LibraryRepository.get(this@MainActivity)
                            val entry = repository.library.value?.entries?.firstOrNull { it.id == editor?.document?.id }
                            if (entry != null) {
                                repository.rename(entry, name)
                            } else {
                                editor?.document?.title = name
                                editor?.saveNow()
                            }
                        },
                        onOpenDocument = { id ->
                            if (second?.libraryId == id) {
                                Toast.makeText(this@MainActivity, R.string.split_already_open, Toast.LENGTH_SHORT).show()
                                return@EditorActions
                            }
                            canvas.rememberPage()
                            editor?.saveNow()
                            openDocument(id)
                        },
                        sources = ::sourceEntries,
                        currentSourceId = { editor?.document?.id },
                        onOpenSource = { entry -> switchTo(entry.store) },
                        onRemoveSource = ::removeSource,
                        linkableNotebooks = {
                            val linked = binderOwner?.let { Binder(it).links() }.orEmpty().toSet()
                            val ownId = binderOwner?.let { runCatching { it.readDocument().id }.getOrNull() }
                            LibraryRepository.get(this@MainActivity).library.value?.entries.orEmpty()
                                .filter { it.document != null && it.id != ownId && it.id !in linked }
                                .map { it.id to it.name }
                        },
                        onTogglePerfReadout = if (BuildConfig.PERF_TOOLS) ({ showReadout = !showReadout }) else null,
                        onSplit = { if (second != null) closeFirstPane() else openSplit() },
                        splitOpen = { second != null },
                        splitSideBySide = ::splitSideBySide,
                        onSplitFlip = ::flipSplit,
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
                    activeCanvas,
                    onDismiss = { shownOverlay.value = null },
                    search = searchState.value,
                )
            }
        }
        firstPane = PaneFrame(this) { activate(null) }.apply {
            penCanvas = canvas
            addView(canvas, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            // It covers the canvas, but only its controls take touches. The rest fall through.
            addView(chrome, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        split = SplitLayout(this).apply {
            addView(firstPane)
            onCollapse = { first -> if (first) closeFirstPane() else second?.let(splitHost::close) }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(split, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(overlays, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            },
        )

        val benchmarkStrokes = if (BuildConfig.PERF_TOOLS) intent.getIntExtra(EXTRA_BENCHMARK_STROKES, 0) else 0
        canvas.startOnCover = intent.getStringExtra(EXTRA_START_ACTION) == START_COVER
        when {
            BuildConfig.PERF_TOOLS && intent.getBooleanExtra(EXTRA_BENCHMARK_HEAVY, false) -> runHeavyBenchmark()
            benchmarkStrokes > 0 -> runBenchmark(benchmarkStrokes)
            intent.getBooleanExtra(EXTRA_COVER_DRAFT, false) -> openCoverDraft()
            intent.hasExtra(EXTRA_DOCUMENT_ID) -> openDocument(intent.getLongExtra(EXTRA_DOCUMENT_ID, 0L))
            else -> openScratch(canvasKind = false)
        }
        if (BuildConfig.DEV_CHECKS) HandwritingCheck.runIfAsked(intent)
    }

    /** Keys get here only if no view took them, so typing in a text field never fires a shortcut. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = settings.settings.value.keyBindings.actionFor(event)
        return (action != null && (activeCanvas.perform(action) || openOverlayFor(action))) || super.onKeyDown(keyCode, event)
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
        second?.save()
        LibraryRepository.get(this).refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.betternotes.ui.theme.TemplateText.documentLanguage = null
        canvas.close()
        editor?.close()
        search?.close()
        probe?.close()
        second?.close()
    }

    private fun openDocument(id: Long) {
        val store = LibraryRepository.get(this).documentStore(id)
        binderOwner = store
        show(NoteEditor(store, DocumentSession.open(store)))
    }

    // ---- The binder: the sources of the notebook the owner came in through ----

    /** The notebook whose Sources panel is shown. It stays the same while the owner moves between its sources. */
    private var binderOwner: DocumentStore? = null

    /** The notebook itself first, then its sources oldest first, then the notebooks linked into it. Blocks on the disk. */
    private fun sourceEntries(): List<SourceEntry> {
        val owner = binderOwner ?: return emptyList()
        val binder = Binder(owner)
        val entries = ArrayList<SourceEntry>()
        try {
            val own = owner.readDocument()
            entries += SourceEntry(own.id, own.title, own.pages.size, SourceKind.OWNER, owner)
        } catch (_: java.io.IOException) {
            return emptyList()
        }
        for ((store, document) in binder.children()) {
            val kind = when (document.kind) {
                app.betternotes.core.model.DocumentKind.PDF -> SourceKind.PDF
                app.betternotes.core.model.DocumentKind.CANVAS -> SourceKind.CANVAS
                else -> SourceKind.NOTE
            }
            entries += SourceEntry(document.id, document.title, document.pages.size, kind, store)
        }
        val repository = LibraryRepository.get(this)
        for (libraryId in binder.links()) {
            val store = repository.documentStore(libraryId)
            try {
                val document = store.readDocument()
                entries += SourceEntry(document.id, document.title, document.pages.size, SourceKind.LINKED, store, libraryId)
            } catch (_: java.io.IOException) {
                // A linked notebook that was deleted from the library simply drops out of the list.
            }
        }
        return entries
    }

    /** Moves to a source, keeping the binder. The page being left is remembered and saved. */
    private fun switchTo(store: DocumentStore) {
        canvas.rememberPage()
        editor?.saveNow()
        show(NoteEditor(store, DocumentSession.open(store)))
    }

    private fun removeSource(entry: SourceEntry) {
        val owner = binderOwner ?: return
        if (entry.kind == SourceKind.OWNER) return
        val binder = Binder(owner)
        val wasOpen = editor?.document?.id == entry.id
        if (wasOpen) switchTo(owner)
        if (entry.libraryId != null) binder.unlink(entry.libraryId) else binder.remove(entry.store)
    }

    /** Makes the source the owner asked for, off the main thread, and opens it. A linked notebook only joins the list. */
    private fun addSource(request: SourceRequest, state: EditorState) {
        val owner = binderOwner ?: return
        val binder = Binder(owner)
        canvas.rememberPage()
        editor?.saveNow()
        lifecycleScope.launch {
            val made: DocumentStore? = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val background = PageBackground(template = PageTemplate.Dotted())
                try {
                    when (request) {
                        SourceRequest.Canvas -> binder.add { store ->
                            Document.newCanvas(getString(R.string.source_canvas), background, now).also(store::writeDocument)
                        }
                        is SourceRequest.Image -> binder.add { store ->
                            MediaImport.photos(contentResolver, listOf(request.uri), store, getString(R.string.source_image), PhotoLayout.FILLS_PAGE, PageBackground(), now)
                        }
                        is SourceRequest.File -> addFile(binder, request.uri, now)
                        is SourceRequest.Notebook -> {
                            binder.link(request.libraryId)
                            null
                        }
                    }
                } catch (_: java.io.IOException) {
                    null
                }
            }
            state.sourcesVersion++
            if (made != null) {
                state.sourcesOpen = false
                switchTo(made)
            } else if (request !is SourceRequest.Notebook) {
                Toast.makeText(this@MainActivity, R.string.library_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** A PDF becomes a PDF source, a recording an audio source, a picture an image source. Blocks on the disk. */
    private fun addFile(binder: Binder, uri: android.net.Uri, now: Long): DocumentStore? {
        val type = contentResolver.getType(uri).orEmpty()
        return when {
            type == "application/pdf" -> {
                binder.directory.mkdirs()
                (PdfImporter.import(contentResolver, uri, binder.directory) as? PdfImportResult.Imported)?.store
            }
            type.startsWith("audio/") -> binder.add { store ->
                MediaImport.audio(contentResolver, uri, store, getString(R.string.source_audio), PageBackground(), now)
            }
            type.startsWith("image/") -> binder.add { store ->
                MediaImport.photos(contentResolver, uri.let(::listOf), store, getString(R.string.source_image), PhotoLayout.FILLS_PAGE, PageBackground(), now)
            }
            else -> null
        }
    }

    /**
     * Opens the cover being edited for the New Notebook dialog, which is waiting for this screen to close. Whatever way the owner
     * leaves, the draft is kept and the dialog is told it was edited.
     */
    private fun openCoverDraft() {
        val store = CoverDraft.store(this)
        binderOwner = null
        setResult(RESULT_OK)
        canvas.startOnCover = true
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
        // The notebook's own template language wins over the app's while it is open.
        app.betternotes.ui.theme.TemplateText.documentLanguage = next.store.templateLanguage()
        canvas.open(next)
        val nextSearch = DocumentSearch(next.store, MlKitRecognizer())
        search = nextSearch
        searchState.value = nextSearch
        nextSearch.onIndexed = { nameFromText(next, nextSearch) }
        next.onSaved = { nextSearch.refresh(next.document.pages) }
        nextSearch.refresh(next.document.pages)
    }

    // ---- Split screen ----

    /** The canvas the settings, search and export screens work on. */
    private val activeCanvas: CanvasView get() = active?.canvas ?: canvas

    private fun activate(pane: SplitPane?) {
        if (active === pane) return
        active = pane
        val target = pane?.editor ?: editor
        app.betternotes.ui.theme.TemplateText.documentLanguage = target?.store?.templateLanguage()
        searchState.value = pane?.search ?: search
    }

    private val splitHost = object : SplitHost {
        override fun activated(pane: SplitPane) = activate(pane)

        override fun openOverlay(overlay: SettingsOverlay) {
            shownOverlay.value = overlay
        }

        override fun sideBySide() = splitSideBySide()

        override fun flip() = flipSplit()

        override fun openInOtherPane(pane: SplitPane, libraryId: Long): Boolean {
            val open = editor?.document?.id == libraryId
            if (open) Toast.makeText(this@MainActivity, R.string.split_already_open, Toast.LENGTH_SHORT).show()
            return open
        }

        /** The second pane's "close": it goes, and the first takes the whole screen. */
        override fun close(pane: SplitPane) {
            pane.close()
            split.removeView(pane.view)
            second = null
            firstState.otherPane = null
            activate(null)
        }
    }

    /** The owner's choice of layout, kept while the app is open; null follows the screen's shape. */
    private var sideBySideChoice: Boolean? = null

    private fun splitSideBySide() = sideBySideChoice ?: (split.width >= split.height)

    private fun flipSplit() {
        sideBySideChoice = !splitSideBySide()
        split.sideBySideChoice = sideBySideChoice
    }

    /** Splits the screen: the most recent other notebook opens in a second pane, with the list of notebooks over it to choose another. */
    private fun openSplit() {
        val own = editor?.document?.id
        val library = LibraryRepository.get(this).library.value
        val next = library?.entries.orEmpty()
            .filter { it.document != null && it.trashedAtMs == null && it.id != own }
            .maxByOrNull { it.modifiedAtMs }
        if (next == null) {
            Toast.makeText(this, R.string.split_no_other, Toast.LENGTH_SHORT).show()
            return
        }
        split.sideBySideChoice = sideBySideChoice
        val pane = SplitPane(this, splitHost, tools, settings.settings.value)
        pane.open(next.id)
        second = pane
        split.addView(pane.view)
        firstState.otherPane = pane.canvas
        pane.state.otherPane = canvas
        pane.state.onStickyOpacity = ::saveStickyOpacity
        activate(pane)
        pane.state.quickSwitchOpen = true
    }

    /** The first pane's "close": the second pane's notebook moves into it, and the second pane goes. */
    private fun closeFirstPane() {
        val pane = second ?: return
        val id = pane.libraryId
        splitHost.close(pane)
        canvas.rememberPage()
        editor?.saveNow()
        openDocument(id)
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

    private fun runHeavyBenchmark() {
        val perf = probe ?: return
        getExternalFilesDir(null) // Makes the folder the script pushes a PDF into, with this app as its owner.
        val realPdf = intent.getStringExtra(EXTRA_BENCHMARK_PDF)?.let(::File)
        val withInk = intent.getBooleanExtra(EXTRA_BENCHMARK_INK, true)
        val store = CanvasBenchmark.writeHeavyDocument(this, File(cacheDir, "benchmark-heavy"), realPdf, withInk)
        show(NoteEditor(store, DocumentSession.open(store)))
        canvas.post { CanvasBenchmark.run(canvas, perf) { finish() } }
    }

    private fun apply(next: AppSettings) {
        canvas.applySettings(next)
        second?.apply(next)
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

    /** The general opacity of new post-its, set in the post-it tool's panel, is kept with the other settings. */
    private fun saveStickyOpacity(opacity: Float) {
        settings.update { it.copy(canvas = it.canvas.copy(stickyOpacity = opacity)) }
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

        /** Open on the cover, page 0, instead of the first page. */
        const val START_COVER = "cover"

        /** Open the cover draft of the New Notebook dialog (see `CoverDraft`), and come back to the dialog when it closes. */
        const val EXTRA_COVER_DRAFT = "cover_draft"

        /** Debug and profile builds only: run the scripted benchmark on a page of this many strokes. */
        private const val EXTRA_BENCHMARK_STROKES = "benchmark_strokes"

        /** Debug and profile builds only: run the scripted benchmark on a PDF covered in every kind of ink. */
        private const val EXTRA_BENCHMARK_HEAVY = "benchmark_heavy"

        /** With [EXTRA_BENCHMARK_HEAVY]: a PDF on the device to use instead of a generated one, and whether to cover it in ink. */
        private const val EXTRA_BENCHMARK_PDF = "benchmark_pdf"
        private const val EXTRA_BENCHMARK_INK = "benchmark_ink"
    }
}

/** The color behind the pages in the theme. */
internal fun backdropOf(theme: ThemeMode) =
    (if (theme == ThemeMode.DARK) DarkEditorColors else LightEditorColors).canvasBackdrop.toArgb()

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
