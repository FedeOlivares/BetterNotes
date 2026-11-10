package app.betternotes.ui.split

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.settings.AppSettings
import app.betternotes.library.LibraryPreferences
import app.betternotes.library.LibraryRepository
import app.betternotes.search.DocumentSearch
import app.betternotes.search.MlKitRecognizer
import app.betternotes.ui.backdropOf
import app.betternotes.ui.editor.DocumentInfo
import app.betternotes.ui.editor.EditorActions
import app.betternotes.ui.editor.EditorChrome
import app.betternotes.ui.editor.EditorState
import app.betternotes.ui.editor.ToolStore
import app.betternotes.ui.settings.SettingsOverlay
import app.betternotes.ui.theme.BetterNotesTheme
import app.betternotes.ui.theme.templateLanguage

/**
 * A pane's frame. It tells the host when a touch lands on it, so the host knows which pane the owner is working in, and sends
 * the touch on unchanged.
 */
internal class PaneFrame(context: Context, private val onTouched: () -> Unit) : FrameLayout(context) {
    /** The canvas under the controls: a pen on the text box being typed in goes to it, to write, not to the box's text field. */
    var penCanvas: CanvasView? = null
    private var penOnCanvas = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onTouched()
            penOnCanvas = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS && penCanvas?.penWritesAt(event.x, event.y) == true
        }
        val toCanvas = penOnCanvas
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) penOnCanvas = false
        return if (toCanvas) penCanvas?.dispatchTouchEvent(event) ?: false else super.dispatchTouchEvent(event)
    }
}

/** What a [SplitPane] asks of the screen it sits in. */
internal interface SplitHost {
    /** The owner touched this pane, so it is the one the settings, search and export screens work on. */
    fun activated(pane: SplitPane)

    /** The pane's own back button or "close" entry: it goes away and the other pane takes the whole screen. */
    fun close(pane: SplitPane)

    fun openOverlay(overlay: SettingsOverlay)

    /** Whether the panes are side by side, and the switch between that and one above the other. */
    fun sideBySide(): Boolean
    fun flip()

    /** Whether the other pane already has this notebook open. Two panes on one notebook would overwrite each other's saves. */
    fun openInOtherPane(pane: SplitPane, libraryId: Long): Boolean
}

/**
 * The second editor of the split screen: its own canvas, tool state, toolbars and open notebook. It opens notebooks from the
 * library by id; it has no binder of sources.
 */
internal class SplitPane(
    private val activity: ComponentActivity,
    private val host: SplitHost,
    tools: ToolStore,
    settings: AppSettings,
) {
    val canvas = CanvasView(activity)
    val state = EditorState(canvas, tools)
    val view = PaneFrame(activity) { host.activated(this) }.also { it.penCanvas = canvas }

    var editor: NoteEditor? = null
        private set

    /** The library id of the open notebook. */
    var libraryId: Long = 0L
        private set

    var search: DocumentSearch? = null
        private set

    init {
        canvas.applySettings(settings)
        val chrome = ComposeView(activity).apply {
            setContent {
                val library = remember { LibraryPreferences.get(activity) }
                val theme = library.theme
                canvas.backdropColor = backdropOf(theme)
                val actions = remember { actions(library) }
                BetterNotesTheme(theme) { EditorChrome(state, actions) }
            }
        }
        view.addView(canvas, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // It covers the canvas, but only its controls take touches. The rest fall through.
        view.addView(chrome, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun actions(library: LibraryPreferences) = EditorActions(
        onBack = { host.close(this) },
        onZoomToRealSize = { canvas.zoomTo(1f) },
        onToggleTheme = library::toggleTheme,
        onToggleScratchKind = {},
        onOpenSettings = { host.openOverlay(SettingsOverlay.SCREEN) },
        documentId = { libraryId },
        documentInfo = {
            editor?.document?.let { DocumentInfo(it.title, it.pages.size - if (it.coverId != null) 1 else 0, it.modifiedAtMs, it.createdAtMs) }
        },
        onOpenOverlay = host::openOverlay,
        onRename = { name ->
            val repository = LibraryRepository.get(activity)
            repository.library.value?.entries?.firstOrNull { it.id == libraryId }?.let { repository.rename(it, name) }
        },
        onOpenDocument = { id ->
            if (!host.openInOtherPane(this, id)) open(id)
        },
        onTogglePerfReadout = null,
        onSplit = { host.close(this) },
        splitOpen = { true },
        splitSideBySide = host::sideBySide,
        onSplitFlip = host::flip,
    )

    /** Shows the library notebook [id], first saving the one being left. */
    fun open(id: Long) {
        canvas.rememberPage()
        editor?.saveNow()
        val store = LibraryRepository.get(activity).documentStore(id)
        val next = NoteEditor(store, DocumentSession.open(store))
        canvas.close()
        editor?.close()
        search?.close()
        editor = next
        libraryId = id
        canvas.open(next)
        val nextSearch = DocumentSearch(store, MlKitRecognizer())
        search = nextSearch
        next.onSaved = { nextSearch.refresh(next.document.pages) }
        nextSearch.refresh(next.document.pages)
    }

    /** The template language of the open notebook, which the words on its templates follow. */
    fun templateLanguage() = editor?.store?.templateLanguage()

    fun apply(settings: AppSettings) = canvas.applySettings(settings)

    fun save() {
        canvas.rememberPage()
        editor?.saveNow()
    }

    fun close() {
        save()
        canvas.close()
        editor?.close()
        search?.close()
        editor = null
        search = null
    }
}
