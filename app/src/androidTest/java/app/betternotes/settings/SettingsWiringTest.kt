package app.betternotes.settings

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.edit.InsertPage
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.Page
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.KeyBindings
import app.betternotes.core.settings.KeyChord
import app.betternotes.core.settings.KeyCodes
import app.betternotes.core.settings.GestureSettings
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.settings.ScreenCalibration
import app.betternotes.core.settings.StylusAction
import app.betternotes.core.settings.StylusSettings
import app.betternotes.core.settings.SettingsStore
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.changeBackground
import app.betternotes.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsWiringTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun down(keyCode: Int, meta: Int = 0, repeat: Int = 0) =
        KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, repeat, meta)

    /** The core module cannot see Android, so this holds its copies of the key codes to the real ones. */
    @Test
    fun keyCodesMatchAndroid() {
        assertEquals(KeyEvent.KEYCODE_0, KeyCodes.KEY_0)
        assertEquals(KeyEvent.KEYCODE_5, KeyCodes.KEY_5)
        assertEquals(KeyEvent.KEYCODE_C, KeyCodes.C)
        assertEquals(KeyEvent.KEYCODE_E, KeyCodes.E)
        assertEquals(KeyEvent.KEYCODE_F, KeyCodes.F)
        assertEquals(KeyEvent.KEYCODE_G, KeyCodes.G)
        assertEquals(KeyEvent.KEYCODE_H, KeyCodes.H)
        assertEquals(KeyEvent.KEYCODE_I, KeyCodes.I)
        assertEquals(KeyEvent.KEYCODE_K, KeyCodes.K)
        assertEquals(KeyEvent.KEYCODE_L, KeyCodes.L)
        assertEquals(KeyEvent.KEYCODE_N, KeyCodes.N)
        assertEquals(KeyEvent.KEYCODE_O, KeyCodes.O)
        assertEquals(KeyEvent.KEYCODE_P, KeyCodes.P)
        assertEquals(KeyEvent.KEYCODE_Q, KeyCodes.Q)
        assertEquals(KeyEvent.KEYCODE_R, KeyCodes.R)
        assertEquals(KeyEvent.KEYCODE_S, KeyCodes.S)
        assertEquals(KeyEvent.KEYCODE_T, KeyCodes.T)
        assertEquals(KeyEvent.KEYCODE_U, KeyCodes.U)
        assertEquals(KeyEvent.KEYCODE_V, KeyCodes.V)
        assertEquals(KeyEvent.KEYCODE_W, KeyCodes.W)
        assertEquals(KeyEvent.KEYCODE_X, KeyCodes.X)
        assertEquals(KeyEvent.KEYCODE_Y, KeyCodes.Y)
        assertEquals(KeyEvent.KEYCODE_Z, KeyCodes.Z)
        assertEquals(KeyEvent.KEYCODE_COMMA, KeyCodes.COMMA)
        assertEquals(KeyEvent.KEYCODE_TAB, KeyCodes.TAB)
        assertEquals(KeyEvent.KEYCODE_DEL, KeyCodes.DEL)
        assertEquals(KeyEvent.KEYCODE_MINUS, KeyCodes.MINUS)
        assertEquals(KeyEvent.KEYCODE_EQUALS, KeyCodes.EQUALS)
        assertEquals(KeyEvent.KEYCODE_PAGE_UP, KeyCodes.PAGE_UP)
        assertEquals(KeyEvent.KEYCODE_PAGE_DOWN, KeyCodes.PAGE_DOWN)
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, KeyCodes.FORWARD_DEL)
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, KeyCodes.MOVE_HOME)
        assertEquals(KeyEvent.KEYCODE_MOVE_END, KeyCodes.MOVE_END)
    }

    @Test
    fun keyEventsResolveWithTheirModifiers() {
        val keys = KeyBindings.Default
        assertEquals(EditorAction.UNDO, keys.actionFor(down(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)))
        assertEquals(EditorAction.WIDTH_1, keys.actionFor(down(KeyEvent.KEYCODE_1, KeyEvent.META_SHIFT_ON)))
        assertEquals(EditorAction.COLOR_1, keys.actionFor(down(KeyEvent.KEYCODE_1)))
        assertNull(keys.actionFor(down(KeyEvent.KEYCODE_Z)))
        val rebound = keys.rebind(EditorAction.PEN, KeyChord(KeyCodes.Q, KeyChord.ALT or KeyChord.SHIFT))
        assertEquals(EditorAction.PEN, rebound.actionFor(down(KeyEvent.KEYCODE_Q, KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON)))
    }

    @Test
    fun onlyFittingKeyEventsCount() {
        val keys = KeyBindings.Default
        val up = KeyEvent(0L, 0L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_P, 0)
        assertNull(keys.actionFor(up))
        assertNull("a tool must not retrigger while held", keys.actionFor(down(KeyEvent.KEYCODE_P, repeat = 1)))
        assertEquals(EditorAction.NEXT_PAGE, keys.actionFor(down(KeyEvent.KEYCODE_PAGE_DOWN, repeat = 3)))
    }

    @Test
    fun aKeyNoViewTookReachesTheCanvas() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val canvas = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    .getChildAt(0).let { (it as android.view.ViewGroup).getChildAt(0) as CanvasView }
                val before = canvas.viewport.zoom
                activity.onKeyDown(KeyEvent.KEYCODE_EQUALS, down(KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON))
                assertEquals(before * 1.25f, canvas.viewport.zoom, 0.001f)
            }
        }
    }

    @Test
    fun repositoryKeepsTheLatestValueAcrossRestarts() {
        val file = File(instrumentation.targetContext.cacheDir, "settings-test.bns").apply { delete() }
        val repository = SettingsRepository(SettingsStore(file))
        val changed = AppSettings(scrollDirection = ScrollDirection.LEFT_TO_RIGHT)
        repository.update { changed }
        assertEquals(changed, repository.settings.value)

        val deadline = SystemClock.uptimeMillis() + 5000
        while (SettingsStore(file).read() != changed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
        assertEquals(changed, SettingsRepository(SettingsStore(file)).settings.value)
        file.delete()
    }

    private fun withCanvas(body: (CanvasView, NoteEditor, Document) -> Unit) {
        val root = File(instrumentation.targetContext.cacheDir, "settings-canvas-test").apply { deleteRecursively() }
        val store = DocumentStore(root)
        val document = Document.newNote("Test", PageSize.A4, PageBackground(), 0L)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var canvas: CanvasView
            lateinit var editor: NoteEditor
            scenario.onActivity { activity ->
                editor = NoteEditor(store, DocumentSession.create(store, document))
                canvas = CanvasView(activity)
                activity.setContentView(canvas)
                canvas.open(editor)
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!(canvas.width > 0 && canvas.isAttachedToWindow) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(20)
            }
            scenario.onActivity {
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
                body(canvas, editor, document)
                canvas.close()
                editor.close()
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun canvasFollowsScrollDirectionAndKeepsThePage() = withCanvas { canvas, _, document ->
        repeat(4) { canvas.perform(InsertPage(document.pages.size, Page(100L + it, PageSize.A4))) }
        canvas.goToPage(3)
        assertEquals(3, canvas.currentPageIndex)
        canvas.applySettings(AppSettings(scrollDirection = ScrollDirection.LEFT_TO_RIGHT))
        assertEquals(ScrollDirection.LEFT_TO_RIGHT, canvas.scrollDirection)
        assertEquals(3, canvas.currentPageIndex)
        canvas.applySettings(AppSettings(scrollDirection = ScrollDirection.RIGHT_TO_LEFT))
        assertEquals(3, canvas.currentPageIndex)
    }

    @Test
    fun shortcutsMoveBetweenPagesAndZoom() = withCanvas { canvas, _, document ->
        canvas.perform(InsertPage(1, Page(100, PageSize.A4)))
        canvas.perform(InsertPage(2, Page(101, PageSize.A4)))
        assertEquals(3, document.pages.size)
        assertTrue(canvas.perform(EditorAction.LAST_PAGE))
        assertEquals(2, canvas.currentPageIndex)
        canvas.perform(EditorAction.PREVIOUS_PAGE)
        assertEquals(1, canvas.currentPageIndex)
        canvas.perform(EditorAction.NEXT_PAGE)
        canvas.perform(EditorAction.NEXT_PAGE)
        assertEquals(2, canvas.currentPageIndex)
        canvas.perform(EditorAction.FIRST_PAGE)
        assertEquals(0, canvas.currentPageIndex)

        canvas.perform(EditorAction.ZOOM_IN)
        assertEquals(1.25f, canvas.viewport.zoom, 0.001f)
        canvas.perform(EditorAction.ZOOM_OUT)
        assertEquals(1f, canvas.viewport.zoom, 0.001f)
        canvas.perform(EditorAction.ZOOM_IN)
        canvas.perform(EditorAction.ZOOM_REAL_SIZE)
        assertEquals(1f, canvas.viewport.zoom, 0.001f)
    }

    @Test
    fun shortcutsSwitchModesAndViewOnlyBlocksUndo() = withCanvas { canvas, _, document ->
        val modes = ArrayList<InputMode>()
        canvas.onInputModeChanged = { modes += it }
        canvas.perform(EditorAction.TOGGLE_FINGER_DRAW)
        assertEquals(InputMode.FINGER_DRAW, canvas.inputMode)

        canvas.perform(EditorAction.TOGGLE_VIEW_ONLY)
        assertEquals(InputMode.VIEW_ONLY, canvas.inputMode)
        canvas.perform(EditorAction.TOGGLE_FINGER_DRAW)
        assertEquals(InputMode.VIEW_ONLY, canvas.inputMode)

        canvas.perform(InsertPage(1, Page(100, PageSize.A4)))
        assertEquals(2, document.pages.size)
        canvas.perform(EditorAction.UNDO)
        assertEquals("undo ran in view-only mode", 2, document.pages.size)

        canvas.perform(EditorAction.TOGGLE_VIEW_ONLY)
        assertEquals("view-only must restore the earlier mode", InputMode.FINGER_DRAW, canvas.inputMode)
        canvas.perform(EditorAction.UNDO)
        assertEquals(1, document.pages.size)
        assertEquals(listOf(InputMode.FINGER_DRAW, InputMode.VIEW_ONLY, InputMode.FINGER_DRAW), modes)
    }

    @Test
    fun calibrationChangesRealSizeAndClearingItGoesBack() = withCanvas { canvas, _, _ ->
        val reported = canvas.viewport.screenDpi
        canvas.applySettings(AppSettings(calibration = ScreenCalibration(reported * 1.1f)))
        assertEquals(reported * 1.1f / 72f, canvas.viewport.pxPerPt, 0.01f)
        canvas.applySettings(AppSettings())
        assertEquals(reported, canvas.viewport.screenDpi)
    }

    @Test
    fun oneFingerSettingSetsTheInputMode() = withCanvas { canvas, _, _ ->
        canvas.applySettings(AppSettings(gestures = GestureSettings(oneFinger = OneFingerSetting.DRAW)))
        assertEquals(InputMode.FINGER_DRAW, canvas.inputMode)
        canvas.applySettings(AppSettings())
        assertEquals(InputMode.STYLUS_DRAW, canvas.inputMode)
    }

    @Test
    fun settingsDoNotBreakViewOnly() = withCanvas { canvas, _, _ ->
        canvas.inputMode = InputMode.VIEW_ONLY
        canvas.applySettings(AppSettings(gestures = GestureSettings(oneFinger = OneFingerSetting.DRAW)))
        assertEquals(InputMode.VIEW_ONLY, canvas.inputMode)
    }

    @Test
    fun zoomTapGoesToTwiceAndBack() = withCanvas { canvas, _, _ ->
        canvas.onZoomTap(100f, 100f)
        assertEquals(2f, canvas.viewport.zoom, 0.001f)
        canvas.onZoomTap(100f, 100f)
        assertEquals(1f, canvas.viewport.zoom, 0.001f)
    }

    private fun hover(canvas: CanvasView, buttons: Int, timeMs: Long) {
        val properties = MotionEvent.PointerProperties().apply { toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coords = MotionEvent.PointerCoords().apply {
            x = 100f
            y = 100f
        }
        val event = MotionEvent.obtain(
            timeMs, timeMs, MotionEvent.ACTION_HOVER_MOVE, 1, arrayOf(properties), arrayOf(coords),
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
        canvas.onGenericMotionEvent(event)
        event.recycle()
    }

    @Test
    fun stylusButtonsReachTheToolbars() = withCanvas { canvas, _, _ ->
        val asked = ArrayList<EditorAction>()
        canvas.onToolAction = {
            asked += it
            true
        }
        val now = SystemClock.uptimeMillis()
        hover(canvas, MotionEvent.BUTTON_STYLUS_PRIMARY, now)
        hover(canvas, 0, now + 50)
        assertEquals(listOf(EditorAction.PEN), asked)
        hover(canvas, MotionEvent.BUTTON_STYLUS_SECONDARY, now + 100)
        assertEquals(listOf(EditorAction.PEN, EditorAction.HIGHLIGHTER), asked)
        hover(canvas, 0, now + 150)
    }

    @Test
    fun aStylusButtonCanUndo() = withCanvas { canvas, _, document ->
        canvas.perform(InsertPage(1, Page(100, PageSize.A4)))
        canvas.applySettings(AppSettings(stylus = StylusSettings(secondaryButton = StylusAction.UNDO)))
        hover(canvas, MotionEvent.BUTTON_STYLUS_SECONDARY, SystemClock.uptimeMillis())
        assertEquals(1, document.pages.size)
        hover(canvas, 0, SystemClock.uptimeMillis())
    }

    @Test
    fun aDocumentContinuesFromItsLastPage() {
        val root = File(instrumentation.targetContext.cacheDir, "last-page-test").apply { deleteRecursively() }
        val store = DocumentStore(root)
        val pages = List(4) { Page(it + 1L, PageSize.A4) }
        val document = Document(9, "Test", DocumentKind.NOTE, 0, 0, pages)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var canvas: CanvasView
            lateinit var editor: NoteEditor
            scenario.onActivity { activity ->
                editor = NoteEditor(store, DocumentSession.create(store, document))
                canvas = CanvasView(activity)
                activity.setContentView(canvas)
                canvas.applySettings(AppSettings())
                canvas.open(editor)
            }
            waitFor { canvas.width > 0 }
            scenario.onActivity {
                canvas.goToPage(2)
                canvas.close()
                editor.close()
            }
            lateinit var second: NoteEditor
            scenario.onActivity {
                second = NoteEditor(store, DocumentSession.open(store))
                canvas.open(second)
                assertEquals(2, second.lastPageIndex())
                assertEquals(2, canvas.currentPageIndex)
                canvas.close()
                second.close()
            }
        }
        root.deleteRecursively()
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
    }

    @Test
    fun actionsForTheToolbarsAreLeftToThem() = withCanvas { canvas, _, _ ->
        assertFalse(canvas.perform(EditorAction.PEN))
        assertFalse(canvas.perform(EditorAction.SEARCH))
    }

    @Test
    fun aNewBackgroundLandsAndUndoes() = withCanvas { canvas, _, document ->
        val background = PageBackground(0xFF101010.toInt(), PageTemplate.Grid())
        val before = document.pages[0].background
        canvas.perform(document.changeBackground(listOf(document.pages[0].id), background)!!)
        assertEquals(background, document.pages[0].background)
        canvas.undo()
        assertEquals(before, document.pages[0].background)
    }
}
