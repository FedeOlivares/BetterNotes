package app.betternotes.canvas

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.model.Document
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.template.PageBackground
import app.betternotes.core.tools.EraseMode
import app.betternotes.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the real view, with ink's native renderer, on a device or emulator, fed with hand-built motion
 * events. This covers the wiring from a touch to a saved item. It says nothing about latency.
 */
@RunWith(AndroidJUnit4::class)
class CanvasViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var canvas: CanvasView
    private lateinit var editor: NoteEditor
    private lateinit var store: DocumentStore
    private lateinit var document: Document

    private val pageId get() = document.pages[0].id

    /** Ink only keeps the axes the event's device reports, so events need a real device behind them. */
    private val touchDeviceId by lazy {
        InputDevice.getDeviceIds().first { id ->
            val device = InputDevice.getDevice(id)
            device != null && device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                device.getMotionRange(MotionEvent.AXIS_PRESSURE) != null
        }
    }

    @Before
    fun setUp() {
        val root = File(instrumentation.targetContext.cacheDir, "canvas-test")
        root.deleteRecursively()
        store = DocumentStore(root)
        document = Document.newNote("Test", PageSize.A4, PageBackground(), 0L)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            editor = NoteEditor(store, DocumentSession.create(store, document))
            canvas = CanvasView(activity)
            activity.setContentView(canvas)
            canvas.open(editor)
        }
        // Waits for the view to be laid out, which places the first page.
        waitUntil { canvas.width > 0 && canvas.isAttachedToWindow }
    }

    @After
    fun tearDown() {
        scenario.onActivity {
            canvas.close()
            editor.close()
        }
        scenario.close()
    }

    @Test
    fun aStylusStrokeBecomesAnItemInPageCoordinates() {
        val start = worldToScreen(100f, 100f)
        val end = worldToScreen(200f, 150f)
        stylusStroke(start, end)
        val stroke = onlyStroke()
        assertEquals(100f, stroke.points.x(0), 0.5f)
        assertEquals(100f, stroke.points.y(0), 0.5f)
        val last = stroke.points.size - 1
        assertEquals(200f, stroke.points.x(last), 0.5f)
        assertEquals(150f, stroke.points.y(last), 0.5f)
    }

    @Test
    fun aStrokeKeepsItsPageSizeWhenDrawnZoomedIn() {
        onMain { canvas.zoomTo(4f) }
        val start = worldToScreen(100f, 100f)
        val end = worldToScreen(110f, 105f)
        stylusStroke(start, end)
        val stroke = onlyStroke()
        assertEquals(10f, stroke.points.x(stroke.points.size - 1) - stroke.points.x(0), 0.2f)
    }

    @Test
    fun aSavedStrokeMeshesTheSameAsTheOneJustDrawn() {
        for (zoom in floatArrayOf(1f, 3f, 15f)) {
            onMain {
                canvas.zoomTo(zoom)
            }
            val before = pageContent().size
            stylusStroke(worldToScreen(100f, 100f), worldToScreen(120f, 110f), pressure = true)
            waitUntil { pageContent().size == before + 1 }
            onMain {
                val item = pageContent()[before] as StrokeItem
                val drawn = editor.strokes.get(item)
                val reloaded = item.toInkStroke()
                assertEquals(drawn.shape.computeBoundingBox(), reloaded.shape.computeBoundingBox())
                assertEquals(drawn.shape.renderGroupMeshes(0).size, reloaded.shape.renderGroupMeshes(0).size)
                assertEquals(
                    drawn.shape.renderGroupMeshes(0).sumOf { it.vertexCount },
                    reloaded.shape.renderGroupMeshes(0).sumOf { it.vertexCount },
                )
            }
        }
    }

    @Test
    fun aStylusStrokeKeepsPressure() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f), pressure = true)
        val stroke = onlyStroke()
        assertEquals(0.5f, stroke.points.pressure(stroke.points.size / 2), 0.01f)
    }

    @Test
    fun aFingerMovesThePageInStylusMode() {
        val panBefore = onMain { canvas.viewport.panY }
        fingerDrag(from = worldToScreen(100f, 200f), dy = -300f)
        val panAfter = onMain { canvas.viewport.panY }
        assertNotEquals(panBefore, panAfter)
        assertEquals(0, pageContent().size)
    }

    @Test
    fun aFingerDrawsInFingerMode() {
        onMain { canvas.inputMode = InputMode.FINGER_DRAW }
        val panBefore = onMain { canvas.viewport.panY }
        fingerDrag(from = worldToScreen(100f, 200f), dy = 200f)
        waitUntil { pageContent().size == 1 }
        assertEquals(panBefore, onMain { canvas.viewport.panY })
    }

    @Test
    fun nothingDrawsInViewOnlyMode() {
        onMain { canvas.inputMode = InputMode.VIEW_ONLY }
        val panBefore = onMain { canvas.viewport.panY }
        stylusStroke(worldToScreen(100f, 300f), worldToScreen(100f, 100f))
        assertEquals(0, pageContent().size)
        assertNotEquals(panBefore, onMain { canvas.viewport.panY })
    }

    @Test
    fun aStrokeStartingOffThePageIsIgnored() {
        val (x, y) = worldToScreen(-10f, 100f)
        assertTrue("The margin left of the page must be on screen", x >= 0f)
        stylusStroke(x to y, worldToScreen(100f, 100f))
        assertEquals(0, pageContent().size)
    }

    @Test
    fun twoFingerTapsUndoAndRedo() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        waitUntil { pageContent().size == 1 }
        // A touch right after the stylus lifts is taken for the palm.
        SystemClock.sleep(200)

        val at = worldToScreen(150f, 300f)
        twoFingerTap(at)
        twoFingerTap(at)
        // Undo has to wait in case a third tap makes this a redo.
        assertEquals(1, pageContent().size)
        waitUntil { pageContent().size == 0 }

        twoFingerTap(at)
        twoFingerTap(at)
        twoFingerTap(at)
        waitUntil { pageContent().size == 1 }
    }

    @Test
    fun pinchingZoomsAboutTheFingersUpToTheLimit() {
        val (cx, cy) = worldToScreen(150f, 200f)
        val worldX = onMain { canvas.viewport.toWorldX(cx) }
        val worldY = onMain { canvas.viewport.toWorldY(cy) }

        pinch(cx, cy, startGap = 100f, endGap = 300f)
        // The first few pixels of movement only establish that this is a pinch.
        assertTrue(onMain { canvas.viewport.zoom } > 1.8f)
        assertEquals(worldX, onMain { canvas.viewport.toWorldX(cx) }, 1f)
        assertEquals(worldY, onMain { canvas.viewport.toWorldY(cy) }, 1f)

        repeat(4) { pinch(canvas.width / 2f, canvas.height / 2f, startGap = 50f, endGap = 400f) }
        assertEquals(15f, onMain { canvas.viewport.zoom }, 0f)
    }

    @Test
    fun zoomOneIsRealSize() {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val expected = (metrics.xdpi + metrics.ydpi) / 2f / 2.54f
        val (x0, _) = worldToScreen(0f, 0f)
        val (x1, _) = worldToScreen(72f / 2.54f, 0f)
        assertEquals(expected, x1 - x0, 0.01f)
    }

    @Test
    fun holdingThePenStillStraightensTheStrokeAndItsEndKeepsFollowing() {
        val holdMs = 100
        onMain { canvas.tool = CanvasTool.Ink(InkStyle(), holdToLineMs = holdMs) }
        val tool = MotionEvent.TOOL_TYPE_STYLUS
        val down = SystemClock.uptimeMillis()
        val start = worldToScreen(100f, 100f)
        send(event(down, down, MotionEvent.ACTION_DOWN, tool, listOf(start)))
        // A wobbly path, which must not survive.
        for (i in 1..10) {
            val at = worldToScreen(100f + i * 10f, 100f + (i % 2) * 40f)
            send(event(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, tool, listOf(at)))
        }
        SystemClock.sleep(holdMs * 3L)
        waitForIdle()
        val end = worldToScreen(260f, 180f)
        send(event(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, tool, listOf(end)))
        send(event(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, tool, listOf(end)))

        val stroke = onlyStroke()
        assertEquals(2, stroke.points.size)
        assertEquals(100f, stroke.points.x(0), 0.5f)
        assertEquals(100f, stroke.points.y(0), 0.5f)
        assertEquals(260f, stroke.points.x(1), 0.5f)
        assertEquals(180f, stroke.points.y(1), 0.5f)
    }

    @Test
    fun aStrokeWithoutAHoldKeepsItsShape() {
        onMain { canvas.tool = CanvasTool.Ink(InkStyle(), holdToLineMs = 5000) }
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        assertTrue(onlyStroke().points.size > 2)
    }

    @Test
    fun theHistoryReportsWhatCanBeUndone() {
        var reports = 0
        onMain {
            canvas.onHistoryChanged = { reports++ }
            assertFalse(canvas.canUndo)
        }
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        waitUntil { pageContent().size == 1 }
        onMain {
            assertTrue(canvas.canUndo)
            assertFalse(canvas.canRedo)
            canvas.undo()
            assertFalse(canvas.canUndo)
            assertTrue(canvas.canRedo)
        }
        assertTrue(reports >= 2)
    }

    @Test
    fun theLaserLeavesNothingOnThePage() {
        onMain { canvas.tool = CanvasTool.Laser }
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        waitForIdle()
        SystemClock.sleep(100)
        assertEquals(0, pageContent().size)
        assertFalse(onMain { canvas.canUndo })
    }

    @Test
    fun aStrokeStartingByTheRulerRunsAlongItsEdge() {
        onMain { canvas.rulerVisible = true }
        // The ruler sits level at the center of the view, so its lower edge is a fixed screen line.
        val edgeY = onMain { canvas.height / 2f + 70f * canvas.resources.displayMetrics.density }
        val centerX = onMain { canvas.width / 2f }
        val toPageY = { y: Float -> onMain { canvas.viewport.toWorldY(y) } }
        drag(MotionEvent.TOOL_TYPE_STYLUS, centerX - 200f to edgeY + 6f, centerX + 200f to edgeY + 60f)
        val stroke = onlyStroke()
        assertEquals(2, stroke.points.size)
        assertEquals(stroke.points.y(0), stroke.points.y(1), 0.01f)
        val pageOriginY = onMain { canvas.viewport.toWorldY(canvas.viewport.toScreenY(0f)) }
        assertEquals(toPageY(edgeY) - pageOriginY, stroke.points.y(0), 1f)
    }

    @Test
    fun twoFingersOnTheRulerMoveItAndNotThePage() {
        onMain { canvas.rulerVisible = true }
        val cx = onMain { canvas.width / 2f }
        val cy = onMain { canvas.height / 2f }
        val panBefore = onMain { canvas.viewport.panY }
        val tool = MotionEvent.TOOL_TYPE_FINGER
        val down = SystemClock.uptimeMillis() - 200
        val start = listOf(cx - 100f to cy, cx + 100f to cy)
        send(event(down, down, MotionEvent.ACTION_DOWN, tool, listOf(start[0])))
        send(event(down, down + 10, pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), tool, start))
        val moved = listOf(cx - 100f to cy + 300f, cx + 100f to cy + 300f)
        send(event(down, down + 40, MotionEvent.ACTION_MOVE, tool, moved))
        send(event(down, down + 50, pointerAction(MotionEvent.ACTION_POINTER_UP, 1), tool, moved))
        send(event(down, down + 60, MotionEvent.ACTION_UP, tool, listOf(moved[0])))
        waitForIdle()
        assertEquals(panBefore, onMain { canvas.viewport.panY }, 0f)

        // The ruler is no longer at the center, so a stroke there is free again.
        SystemClock.sleep(200)
        val edgeY = cy + 70f * onMain { canvas.resources.displayMetrics.density }
        drag(MotionEvent.TOOL_TYPE_STYLUS, cx - 100f to edgeY, cx + 100f to edgeY + 40f)
        assertTrue(onlyStroke().points.size > 2)
    }

    @Test
    fun theEraserEndOfAStylusSplitsAStroke() {
        stylusStroke(worldToScreen(50f, 100f), worldToScreen(250f, 100f))
        waitUntil { pageContent().size == 1 }
        drag(
            MotionEvent.TOOL_TYPE_ERASER,
            worldToScreen(150f, 80f),
            worldToScreen(150f, 120f),
        )
        waitUntil { pageContent().size == 2 }
        onMain { canvas.undo() }
        waitUntil { pageContent().size == 1 }
    }

    @Test
    fun theEraserToolRemovesAWholeStroke() {
        stylusStroke(worldToScreen(50f, 100f), worldToScreen(250f, 100f))
        waitUntil { pageContent().size == 1 }
        onMain { canvas.tool = CanvasTool.Eraser(EraseMode.WHOLE, 6f) }
        drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(150f, 80f), worldToScreen(150f, 120f))
        waitUntil { pageContent().size == 0 }
    }

    @Test
    fun inkSurvivesAReopen() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f), pressure = true)
        val original = onlyStroke()
        onMain { editor.saveNow() }
        val reopened = DocumentSession.open(store)
        assertEquals(document.id, reopened.document.id)
        val item = reopened.content(pageId)[0] as StrokeItem
        assertEquals(original.points, item.points)
        assertEquals(original.style, item.style)
    }

    @Test
    fun aStrokeThatJustEndedIsNotLostOnLeaving() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        onMain {
            canvas.close()
            editor.close()
        }
        waitUntil { store.readPage(pageId).size == 1 }
    }

    @Test
    fun undoReachesOnlyWhatWasDrawn() {
        onMain { canvas.undo() }
        assertFalse(onMain { editor.content(pageId).size > 0 })
    }

    // ---- Helpers ----

    private fun pageContent(): PageContent = onMain { editor.content(pageId) }

    private fun onlyStroke(): StrokeItem {
        waitUntil { pageContent().size == 1 }
        return onMain { pageContent()[0] as StrokeItem }
    }

    private fun worldToScreen(x: Float, y: Float): Pair<Float, Float> =
        onMain { canvas.viewport.toScreenX(x) to canvas.viewport.toScreenY(y) }

    private fun stylusStroke(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        pressure: Boolean = false,
    ) {
        drag(MotionEvent.TOOL_TYPE_STYLUS, from, to, if (pressure) 0.5f else 1f)
    }

    private fun fingerDrag(from: Pair<Float, Float>, dy: Float) {
        drag(MotionEvent.TOOL_TYPE_FINGER, from, from.first to from.second + dy)
    }

    private fun drag(
        toolType: Int,
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        pressure: Float = 1f,
    ) {
        val steps = 20
        val down = SystemClock.uptimeMillis() - (steps + 1) * 8
        var time = down
        send(event(down, time, MotionEvent.ACTION_DOWN, toolType, listOf(from), pressure))
        for (i in 1..steps) {
            time += 8
            val t = i.toFloat() / steps
            val at = from.first + (to.first - from.first) * t to from.second + (to.second - from.second) * t
            send(event(down, time, MotionEvent.ACTION_MOVE, toolType, listOf(at), pressure))
        }
        time += 8
        send(event(down, time, MotionEvent.ACTION_UP, toolType, listOf(to), pressure))
    }

    private fun twoFingerTap(at: Pair<Float, Float>) {
        val first = at
        val second = at.first + 120f to at.second
        val fingers = listOf(first, second)
        val tool = MotionEvent.TOOL_TYPE_FINGER
        val down = SystemClock.uptimeMillis() - 50
        send(event(down, down, MotionEvent.ACTION_DOWN, tool, listOf(first)))
        send(event(down, down + 10, pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), tool, fingers))
        send(event(down, down + 40, pointerAction(MotionEvent.ACTION_POINTER_UP, 1), tool, fingers))
        send(event(down, down + 50, MotionEvent.ACTION_UP, tool, listOf(first)))
        SystemClock.sleep(60)
    }

    private fun pinch(cx: Float, cy: Float, startGap: Float, endGap: Float) {
        val tool = MotionEvent.TOOL_TYPE_FINGER
        fun fingers(gap: Float) = listOf(cx - gap / 2f to cy, cx + gap / 2f to cy)
        val steps = 30
        val down = SystemClock.uptimeMillis() - 200 - (steps + 3) * 8
        var time = down
        val start = fingers(startGap)
        send(event(down, time, MotionEvent.ACTION_DOWN, tool, listOf(start[0])))
        time += 200
        send(event(down, time, pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), tool, start))
        for (i in 0..steps) {
            time += 8
            send(event(down, time, MotionEvent.ACTION_MOVE, tool, fingers(startGap + (endGap - startGap) * i / steps)))
        }
        time += 8
        val end = fingers(endGap)
        send(event(down, time, pointerAction(MotionEvent.ACTION_POINTER_UP, 1), tool, end))
        send(event(down, time + 8, MotionEvent.ACTION_UP, tool, listOf(end[0])))
        waitForIdle()
    }

    private fun pointerAction(action: Int, index: Int) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun event(
        downTime: Long,
        time: Long,
        action: Int,
        toolType: Int,
        pointers: List<Pair<Float, Float>>,
        pressure: Float = 1f,
    ): MotionEvent {
        val properties = Array(pointers.size) { i ->
            PointerProperties().apply {
                id = i
                this.toolType = toolType
            }
        }
        val coords = Array(pointers.size) { i ->
            PointerCoords().apply {
                x = pointers[i].first
                y = pointers[i].second
                this.pressure = pressure
                size = 1f
            }
        }
        val source = if (toolType == MotionEvent.TOOL_TYPE_FINGER) {
            InputDevice.SOURCE_TOUCHSCREEN
        } else {
            InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS
        }
        return MotionEvent.obtain(
            downTime,
            time,
            action,
            pointers.size,
            properties,
            coords,
            0,
            0,
            1f,
            1f,
            touchDeviceId,
            0,
            source,
            0,
        )
    }

    private fun send(event: MotionEvent) {
        onMain { canvas.dispatchTouchEvent(event) }
        event.recycle()
    }

    private fun waitForIdle() = instrumentation.waitForIdleSync()

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
