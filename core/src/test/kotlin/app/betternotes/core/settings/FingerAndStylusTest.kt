package app.betternotes.core.settings

import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.gesture.GestureListener
import app.betternotes.core.gesture.GestureRecognizer
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.gesture.OneFingerAction
import app.betternotes.core.gesture.PointerKind
import app.betternotes.core.gesture.TwoFingerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerSettingsTest {
    private val events = ArrayList<String>()
    private val listener = object : GestureListener {
        override fun onDrawStart(pointerId: Int, kind: PointerKind, x: Float, y: Float) { events += "draw" }
        override fun onDrawMove(pointerId: Int, x: Float, y: Float) = Unit
        override fun onDrawEnd(pointerId: Int) = Unit
        override fun onDrawCancel(pointerId: Int) = Unit
        override fun onTransform(dx: Float, dy: Float, scale: Float, focusX: Float, focusY: Float) {
            events += if (scale == 1f) "pan" else "zoom"
        }
        override fun onTransformEnd() = Unit
        override fun onUndo() { events += "undo" }
        override fun onRedo() { events += "redo" }
        override fun onZoomTap(x: Float, y: Float) { events += "zoomTap" }
    }

    private fun recognizer(config: GestureConfig) = GestureRecognizer(config, listener)

    private fun GestureRecognizer.oneFingerDrag() {
        pointerDown(1, PointerKind.FINGER, 0f, 0f, 0)
        for (i in 1..5) pointerMove(1, 0f, i * 50f, i * 10L)
        pointerUp(1, 60)
    }

    private fun GestureRecognizer.pinch() {
        pointerDown(1, PointerKind.FINGER, 100f, 100f, 0)
        pointerDown(2, PointerKind.FINGER, 200f, 100f, 10)
        for (i in 1..5) {
            pointerMove(1, 100f - i * 20f, 100f, 200L + i * 10L)
            pointerMove(2, 200f + i * 20f, 100f, 200L + i * 10L)
        }
        pointerUp(1, 300)
        pointerUp(2, 300)
    }

    private fun GestureRecognizer.twoFingerTap(atMs: Long) {
        pointerDown(1, PointerKind.FINGER, 100f, 100f, atMs)
        pointerDown(2, PointerKind.FINGER, 200f, 100f, atMs + 5)
        pointerUp(1, atMs + 50)
        pointerUp(2, atMs + 50)
    }

    private fun GestureRecognizer.oneFingerTap(atMs: Long, x: Float = 100f) {
        pointerDown(1, PointerKind.FINGER, x, 100f, atMs)
        pointerUp(1, atMs + 40)
    }

    @Test
    fun `one finger scrolls by default and can be ignored`() {
        recognizer(GestureConfig()).oneFingerDrag()
        assertTrue("pan" in events)
        events.clear()
        recognizer(GestureConfig(oneFinger = OneFingerAction.IGNORED)).oneFingerDrag()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `view only still moves the page when one finger is ignored`() {
        val r = recognizer(GestureConfig(oneFinger = OneFingerAction.IGNORED))
        r.mode = InputMode.VIEW_ONLY
        r.oneFingerDrag()
        assertTrue("pan" in events)
    }

    @Test
    fun `two fingers zoom by default, only scroll if asked, and can be ignored`() {
        recognizer(GestureConfig()).pinch()
        assertTrue("zoom" in events)
        events.clear()
        recognizer(GestureConfig(twoFingers = TwoFingerAction.SCROLL)).pinch()
        assertTrue("zoom" !in events && "pan" in events)
        events.clear()
        recognizer(GestureConfig(twoFingers = TwoFingerAction.IGNORED)).pinch()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `undo off makes two taps do nothing and three still redo`() {
        val r = recognizer(GestureConfig(undoTaps = false))
        r.twoFingerTap(0)
        r.twoFingerTap(100)
        r.onDeadline(10_000)
        assertEquals(emptyList<String>(), events)
        r.twoFingerTap(20_000)
        r.twoFingerTap(20_100)
        r.twoFingerTap(20_200)
        assertEquals(listOf("redo"), events)
    }

    @Test
    fun `redo off lets undo fire on the second tap without waiting`() {
        val r = recognizer(GestureConfig(redoTaps = false))
        r.twoFingerTap(0)
        assertEquals(emptyList<String>(), events)
        r.twoFingerTap(100)
        assertEquals(listOf("undo"), events)
        r.twoFingerTap(200)
        r.onDeadline(10_000)
        assertEquals("a third tap starts over", listOf("undo"), events)
    }

    @Test
    fun `two finger tap timing follows the setting`() {
        val config = GestureSettings(multiTapWindowMs = 600).applyTo(GestureConfig())
        val r = recognizer(config)
        r.twoFingerTap(0)
        r.twoFingerTap(500)
        r.onDeadline(700)
        assertEquals("still waiting for a third tap", emptyList<String>(), events)
        r.onDeadline(1200)
        assertEquals(listOf("undo"), events)
    }

    @Test
    fun `one finger double tap asks for a zoom only when on`() {
        val r = recognizer(GestureConfig(zoomTaps = true))
        r.oneFingerTap(0)
        r.oneFingerTap(150)
        assertEquals(listOf("zoomTap"), events)

        events.clear()
        val off = recognizer(GestureConfig())
        off.oneFingerTap(0)
        off.oneFingerTap(150)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `tapping far apart or slowly is not a zoom`() {
        val r = recognizer(GestureConfig(zoomTaps = true))
        r.oneFingerTap(0)
        r.oneFingerTap(150, x = 900f)
        assertEquals(emptyList<String>(), events)
        r.oneFingerTap(5000, x = 900f)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `settings map to the recognizer`() {
        val config = GestureSettings(
            oneFinger = OneFingerSetting.IGNORED,
            twoFingers = TwoFingerAction.SCROLL,
            undoTaps = false,
            redoTaps = false,
            doubleTapZoom = false,
        ).applyTo(GestureConfig())
        assertEquals(OneFingerAction.IGNORED, config.oneFinger)
        assertEquals(TwoFingerAction.SCROLL, config.twoFingers)
        assertEquals(false, config.undoTaps)
        assertEquals(false, config.redoTaps)
        assertEquals(false, config.zoomTaps)
    }

    @Test
    fun `everything on the stylus and finger sheet survives a save`() {
        val settings = AppSettings(
            gestures = GestureSettings(oneFinger = OneFingerSetting.DRAW, twoFingers = TwoFingerAction.IGNORED, undoTaps = false, doubleTapZoom = false),
            stylus = StylusSettings(false, StylusAction.LASSO, StylusAction.REDO, StylusHoldAction.NONE, StylusEraserEnd.IGNORED),
            canvas = CanvasSettings(continueFromLastPage = false, keepScreenOn = false, hideSystemBars = false),
            newNoteName = NewNoteName.UNTITLED,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }
}

class StylusButtonsTest {
    private val events = ArrayList<String>()
    private val listener = object : StylusButtonListener {
        override fun onStylusAction(action: StylusAction) { events += action.name }
        override fun onHoldStart() { events += "holdStart" }
        override fun onHoldEnd() { events += "holdEnd" }
    }
    private val buttons = StylusButtons(listener)

    @Test
    fun `a click runs the button's action when it lifts`() {
        buttons.primaryDown(0)
        assertEquals(emptyList<String>(), events)
        buttons.primaryUp(100)
        assertEquals(listOf("PEN"), events)
    }

    @Test
    fun `holding erases until the button lifts and runs no action`() {
        buttons.primaryDown(0)
        assertEquals(StylusButtons.HOLD_MS, buttons.nextDeadlineMs)
        buttons.onDeadline(StylusButtons.HOLD_MS)
        assertEquals(StylusButtons.NO_DEADLINE, buttons.nextDeadlineMs)
        buttons.primaryUp(1000)
        assertEquals(listOf("holdStart", "holdEnd"), events)
    }

    @Test
    fun `a late release still counts as a hold`() {
        buttons.primaryDown(0)
        buttons.primaryUp(1000)
        assertEquals(listOf("holdStart", "holdEnd"), events)
    }

    @Test
    fun `with hold off a long press is a click`() {
        buttons.settings = StylusSettings(primaryHold = StylusHoldAction.NONE)
        buttons.primaryDown(0)
        assertEquals(StylusButtons.NO_DEADLINE, buttons.nextDeadlineMs)
        buttons.primaryUp(5000)
        assertEquals(listOf("PEN"), events)
    }

    @Test
    fun `the secondary button acts at once and none does nothing`() {
        buttons.secondaryDown()
        assertEquals(listOf("HIGHLIGHTER"), events)
        events.clear()
        buttons.settings = StylusSettings(primaryButton = StylusAction.NONE, secondaryButton = StylusAction.NONE)
        buttons.secondaryDown()
        buttons.primaryDown(0)
        buttons.primaryUp(10)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `a release with no press is ignored`() {
        buttons.primaryUp(10)
        assertEquals(emptyList<String>(), events)
    }
}
