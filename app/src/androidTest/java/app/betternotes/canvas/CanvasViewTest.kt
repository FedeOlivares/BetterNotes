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
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.template.PageBackground
import app.betternotes.core.tools.ALL_ITEM_KINDS
import app.betternotes.core.tools.EraseMode
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.core.tools.TapeOptions
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
    fun theLassoMovesAndResizesWhatItSelects() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        waitUntil { pageContent().size == 1 }
        val before = onlyStroke()
        onMain { canvas.tool = CanvasTool.Lasso(rectangle = true, kinds = ALL_ITEM_KINDS, fullOnly = false) }
        drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(80f, 80f), worldToScreen(220f, 170f))
        assertTrue(onMain { canvas.hasSelection })
        assertEquals(1, pageContent().size)

        // Inside the selection, away from its handles, so this moves it.
        drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(150f, 125f), worldToScreen(170f, 145f))
        val moved = onlyStroke()
        assertEquals(before.points.x(0) + 20f, moved.points.x(0), 0.5f)
        assertEquals(before.points.y(0) + 20f, moved.points.y(0), 0.5f)
        assertTrue(onMain { canvas.hasSelection })

        onMain { canvas.undo() }
        assertEquals(before.points.x(0), onlyStroke().points.x(0), 0f)
        assertFalse(onMain { canvas.hasSelection })
    }

    @Test
    fun theLassoIgnoresKindsSwitchedOff() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        waitUntil { pageContent().size == 1 }
        onMain { canvas.tool = CanvasTool.Lasso(rectangle = true, kinds = setOf(ItemKind.IMAGE), fullOnly = false) }
        drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(80f, 80f), worldToScreen(220f, 170f))
        assertFalse(onMain { canvas.hasSelection })
    }

    @Test
    fun aDraggedShapeLandsOnThePageAndCanBeUndone() {
        val options = ShapeOptions().withKind(ShapeKind.STAR)
        onMain { canvas.tool = CanvasTool.Shape(options) }
        drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(100f, 100f), worldToScreen(200f, 180f))
        waitUntil { pageContent().size == 1 }
        val shape = onMain { pageContent()[0] as ShapeItem }
        assertEquals(ShapeKind.STAR, shape.kind)
        assertEquals(100f, shape.frame.width, 0.5f)
        assertEquals(80f, shape.frame.height, 0.5f)
        onMain { canvas.undo() }
        assertEquals(0, pageContent().size)
    }

    @Test
    fun inkInAnOpenNoteStaysInItAndATapOnAClosedOneOpensIt() {
        onMain { canvas.tool = CanvasTool.Sticky(NoteOptions()) }
        val at = worldToScreen(100f, 100f)
        drag(MotionEvent.TOOL_TYPE_STYLUS, at, at.first + 1f to at.second)
        waitUntil { pageContent().size == 1 }
        onMain { canvas.endTextEdit() }
        onMain { canvas.tool = CanvasTool.Ink(InkStyle()) }

        SystemClock.sleep(100)
        stylusStroke(worldToScreen(120f, 120f), worldToScreen(180f, 150f))
        waitUntil { (onMain { pageContent()[0] } as StickyNoteItem).items.isNotEmpty() }
        assertEquals(1, pageContent().size)
        val note = onMain { pageContent()[0] as StickyNoteItem }
        val ink = note.items.single() as StrokeItem
        // Kept relative to the note, so that it moves with it.
        assertEquals(20f, ink.points.x(0), 0.5f)
        assertEquals(20f, ink.points.y(0), 0.5f)
    }

    @Test
    fun aTableIsPlacedWhereThePenLands() {
        val options = TableOptions(columns = 2, rows = 2)
        onMain { canvas.tool = CanvasTool.Table(options) }
        val at = worldToScreen(100f, 100f)
        drag(MotionEvent.TOOL_TYPE_STYLUS, at, at.first + 1f to at.second)
        waitUntil { pageContent().size == 1 }
        val table = onMain { pageContent()[0] as TableItem }
        assertEquals(100f, table.frame.left, 0.5f)
        assertEquals(4, table.cells.size)
    }

    @Test
    fun inkInTheLoupeLandsSmallerOnThePage() {
        val top = onMain {
            canvas.setLoupeArea(0f, canvas.height / 2f, canvas.width.toFloat(), canvas.height.toFloat())
            canvas.loupeOpen = true
            canvas.height / 2f
        }
        val width = onMain { canvas.width.toFloat() }
        val drag = 300f
        drag(MotionEvent.TOOL_TYPE_STYLUS, width / 2f - drag / 2f to top + 200f, width / 2f + drag / 2f to top + 200f)
        val stroke = onlyStroke()
        val pxPerPt = onMain { canvas.viewport.pxPerPt }
        // The box is 45% of what the view shows across, and the window shows that box across all of it.
        val expected = drag * 0.45f / pxPerPt
        assertEquals(expected, stroke.points.x(stroke.points.size - 1) - stroke.points.x(0), 1f)
    }

    @Test
    fun aMovedSelectionLinesUpWithThePageUnlessToldNotTo() {
        for (snapping in listOf(true, false)) {
            onMain {
                canvas.undo()
                canvas.snapToAlign = snapping
                canvas.tool = CanvasTool.Shape(ShapeOptions())
            }
            drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(100f, 100f), worldToScreen(150f, 150f))
            waitUntil { pageContent().size == 1 }
            onMain { canvas.tool = CanvasTool.Lasso(rectangle = true, kinds = ALL_ITEM_KINDS, fullOnly = false) }
            drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(80f, 80f), worldToScreen(170f, 170f))
            // To within a point of the page's left edge, on the box of the border too.
            val shape = onMain { pageContent()[0] as ShapeItem }
            val toEdge = -shape.bounds.left + 1f
            drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(125f, 125f), worldToScreen(125f + toEdge, 125f))
            val moved = onMain { pageContent()[0] as ShapeItem }
            if (snapping) {
                assertEquals(0f, moved.bounds.left, 0.2f)
            } else {
                assertEquals(1f, moved.bounds.left, 0.3f)
            }
            onMain { canvas.undo() }
        }
    }

    @Test
    fun tapeAndEmojiLandOnThePage() {
        onMain { canvas.tool = CanvasTool.Tape(TapeOptions()) }
        drag(MotionEvent.TOOL_TYPE_STYLUS, worldToScreen(100f, 100f), worldToScreen(200f, 100f))
        waitUntil { pageContent().size == 1 }
        assertEquals(ShapeKind.TAPE, onMain { (pageContent()[0] as ShapeItem).kind })

        SystemClock.sleep(100)
        onMain { canvas.tool = CanvasTool.Emoji("⭐", 28) }
        val at = worldToScreen(300f, 300f)
        drag(MotionEvent.TOOL_TYPE_STYLUS, at, at.first + 1f to at.second)
        waitUntil { pageContent().size == 2 }
        assertEquals("⭐", onMain { (pageContent()[1] as TextItem).text })
    }

    @Test
    fun clearingThePageCanBeUndone() {
        stylusStroke(worldToScreen(100f, 100f), worldToScreen(200f, 150f))
        waitUntil { pageContent().size == 1 }
        onMain { canvas.clearPage() }
        assertEquals(0, pageContent().size)
        onMain { canvas.undo() }
        assertEquals(1, pageContent().size)
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

    @Test
    fun touchingACircleDrawnWithCircleToLassoSelectsWhatItSurrounds() {
        onMain { canvas.tool = CanvasTool.Ink(InkStyle(), circleToLasso = true) }
        var requested = false
        onMain { canvas.onLassoRequested = { requested = true } }
        // Something to be inside the circle.
        stylusStroke(worldToScreen(190f, 200f), worldToScreen(210f, 200f))
        waitUntil { pageContent().size == 1 }
        val circle = (0..60).map { i ->
            val angle = 2.0 * Math.PI * 1.05 * i / 60
            worldToScreen((200 + 60 * Math.cos(angle)).toFloat(), (200 + 60 * Math.sin(angle)).toFloat())
        }
        stylusPath(circle)
        waitUntil { pageContent().size == 2 }
        // A touch on the line of the circle.
        val onLine = worldToScreen(260f, 200f)
        stylusStroke(onLine, onLine.first to onLine.second + 1f)
        // The circle walks as ants for a moment before it becomes the selection.
        waitUntil { onMain { requested } }
        assertTrue("The lasso was not asked for", onMain { requested })
        assertTrue("Nothing was selected", onMain { canvas.hasSelection })
        // The circle's own line is gone, and only the stroke inside it is selected.
        assertEquals(1, pageContent().size)
    }

    @Test
    fun aRecordingDroppedOnThePageBecomesABarThatPlaysAndFolds() {
        onMain { canvas.addAudio("sound", 4000L) }
        val id = onMain { canvas.recordings().single().id }
        val played = mutableListOf<Long>()
        onMain {
            canvas.audio = object : AudioPlayback {
                override fun isPlaying(id: Long) = false
                override fun fraction(id: Long) = 0f
                override fun toggle(item: app.betternotes.core.model.AudioItem) { played += item.id }
                override fun seek(item: app.betternotes.core.model.AudioItem, fraction: Float) = Unit
            }
        }
        val drop = worldToScreen(300f, 300f)
        assertTrue(onMain { canvas.placeAudio(id, drop.first, drop.second) })
        fun bar() = pageContent().let { content ->
            (0 until content.size).map { content[it] }.filterIsInstance<app.betternotes.core.model.AudioItem>().single()
        }
        val open = onMain { bar().frame }
        assertTrue(app.betternotes.core.model.AudioBar.isOpen(open))
        // A touch on the play button plays it and draws no ink.
        val play = worldToScreen(open.left + 8f, open.centerY)
        stylusTap(play)
        waitUntil { played.isNotEmpty() }
        assertEquals(1, onMain { pageContent().size })
        // A touch on the corner folds it down to the button and its arrow.
        val corner = worldToScreen(open.left + open.width - 3f, open.top + 3f)
        stylusTap(corner)
        waitUntil { !app.betternotes.core.model.AudioBar.isOpen(onMain { bar().frame }) }
    }

    @Test
    fun holdingAPlacedRecordingMovesIt() {
        onMain { canvas.addAudio("sound", 4000L) }
        val id = onMain { canvas.recordings().single().id }
        onMain {
            canvas.audio = object : AudioPlayback {
                override fun isPlaying(id: Long) = false
                override fun fraction(id: Long) = 0f
                override fun toggle(item: app.betternotes.core.model.AudioItem) = Unit
                override fun seek(item: app.betternotes.core.model.AudioItem, fraction: Float) = Unit
            }
        }
        val drop = worldToScreen(300f, 300f)
        onMain { canvas.placeAudio(id, drop.first, drop.second) }
        fun frame() = pageContent().let { c ->
            (0 until c.size).map { c[it] }.filterIsInstance<app.betternotes.core.model.AudioItem>().single().frame
        }
        val before = onMain { frame() }
        // Hold on the bar's play button, then carry it elsewhere.
        val from = worldToScreen(before.left + 8f, before.centerY)
        val to = worldToScreen(150f, 500f)
        val tool = MotionEvent.TOOL_TYPE_STYLUS
        val down = SystemClock.uptimeMillis()
        send(event(down, down, MotionEvent.ACTION_DOWN, tool, listOf(from), 1f))
        SystemClock.sleep(700)
        send(event(down, down + 710, MotionEvent.ACTION_MOVE, tool, listOf(to), 1f))
        send(event(down, down + 720, MotionEvent.ACTION_UP, tool, listOf(to), 1f))
        waitUntil { frame().centerY > 450f }
        assertEquals(150f, onMain { frame().centerX }, 2f)
    }

    @Test
    fun aFingerTappingInsideACircleDrawnWithCircleToLassoSelectsIt() {
        onMain { canvas.tool = CanvasTool.Ink(InkStyle(), circleToLasso = true) }
        var requested = false
        onMain { canvas.onLassoRequested = { requested = true } }
        stylusStroke(worldToScreen(190f, 200f), worldToScreen(210f, 200f))
        waitUntil { pageContent().size == 1 }
        val circle = (0..60).map { i ->
            val angle = 2.0 * Math.PI * 1.05 * i / 60
            worldToScreen((200 + 60 * Math.cos(angle)).toFloat(), (200 + 60 * Math.sin(angle)).toFloat())
        }
        stylusPath(circle)
        waitUntil { pageContent().size == 2 }
        val inside = worldToScreen(200f, 240f)
        val tool = MotionEvent.TOOL_TYPE_FINGER
        val down = SystemClock.uptimeMillis()
        send(event(down, down, MotionEvent.ACTION_DOWN, tool, listOf(inside)))
        send(event(down, down + 40, MotionEvent.ACTION_UP, tool, listOf(inside)))
        waitUntil { onMain { requested } }
        assertEquals(1, pageContent().size)
    }

    // ---- Helpers ----

    private fun stylusPath(points: List<Pair<Float, Float>>) {
        val tool = MotionEvent.TOOL_TYPE_STYLUS
        val down = SystemClock.uptimeMillis() - (points.size + 1) * 8
        var time = down
        send(event(down, time, MotionEvent.ACTION_DOWN, tool, listOf(points.first())))
        for (point in points.drop(1)) {
            time += 8
            send(event(down, time, MotionEvent.ACTION_MOVE, tool, listOf(point)))
        }
        time += 8
        send(event(down, time, MotionEvent.ACTION_UP, tool, listOf(points.last())))
    }

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

    /** A pen touch with no movement, quick enough not to count as a hold. */
    private fun stylusTap(at: Pair<Float, Float>) {
        val tool = MotionEvent.TOOL_TYPE_STYLUS
        val down = SystemClock.uptimeMillis()
        send(event(down, down, MotionEvent.ACTION_DOWN, tool, listOf(at), 1f))
        send(event(down, down + 20, MotionEvent.ACTION_UP, tool, listOf(at), 1f))
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
