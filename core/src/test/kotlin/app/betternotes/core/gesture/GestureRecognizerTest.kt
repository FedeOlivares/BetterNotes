package app.betternotes.core.gesture

import app.betternotes.core.geometry.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRecognizerTest {
    private val events = mutableListOf<String>()
    private val listener = object : GestureListener {
        override fun onDrawStart(pointerId: Int, kind: PointerKind, x: Float, y: Float) {
            events += "start $pointerId $kind"
        }

        override fun onDrawMove(pointerId: Int, x: Float, y: Float) {
            events += "move $pointerId"
        }

        override fun onDrawEnd(pointerId: Int) {
            events += "end $pointerId"
        }

        override fun onDrawCancel(pointerId: Int) {
            events += "cancel $pointerId"
        }

        override fun onTransform(dx: Float, dy: Float, scale: Float, focusX: Float, focusY: Float) {
            events += "transform"
            totalDx += dx
            totalDy += dy
            totalScale *= scale
        }

        override fun onTransformEnd() {
            events += "transformEnd"
        }

        override fun onUndo() {
            events += "undo"
        }

        override fun onRedo() {
            events += "redo"
        }
    }
    private var totalDx = 0f
    private var totalDy = 0f
    private var totalScale = 1f

    private val config = GestureConfig(
        multiTapWindowMs = 300,
        tapMaxDurationMs = 250,
        fingerLandingWindowMs = 100,
        touchSlopPx = 20f,
        palmGuardMs = 150,
    )
    private val recognizer = GestureRecognizer(config, listener)
    private var now = 1_000L

    private fun fingerDown(id: Int, x: Float = 0f, y: Float = 0f) =
        recognizer.pointerDown(id, PointerKind.FINGER, x, y, now)

    private fun stylusDown(id: Int = 9, kind: PointerKind = PointerKind.STYLUS) =
        recognizer.pointerDown(id, kind, 0f, 0f, now)

    private fun move(id: Int, x: Float, y: Float = 0f) = recognizer.pointerMove(id, x, y, now)
    private fun up(id: Int) = recognizer.pointerUp(id, now)

    private fun twoFingerTap() {
        fingerDown(1, 100f, 100f)
        now += 10
        fingerDown(2, 200f, 100f)
        now += 50
        up(1)
        now += 10
        up(2)
    }

    private fun drive(deadlineFrom: Long = now) {
        now = recognizer.nextDeadlineMs.coerceAtLeast(deadlineFrom)
        recognizer.onDeadline(now)
    }

    // Two-finger tap sequences.

    @Test
    fun `one two-finger tap does nothing`() {
        twoFingerTap()
        drive()
        assertEquals(emptyList<String>(), events)
        assertEquals(GestureRecognizer.NO_DEADLINE, recognizer.nextDeadlineMs)
    }

    @Test
    fun `double tap undoes at once, without waiting for a third tap`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        assertEquals(listOf("undo"), events)
        drive(now + 10_000)
        assertEquals("nothing more happens", listOf("undo"), events)
    }

    @Test
    fun `triple tap takes back the second tap's undo and redoes`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 50
        twoFingerTap()
        assertEquals(listOf("undo", "redo", "redo"), events)
        drive(now + 10_000)
        assertEquals(listOf("undo", "redo", "redo"), events)
    }

    @Test
    fun `changing the timing setting changes the wait`() {
        recognizer.config = config.copy(multiTapWindowMs = 800)
        twoFingerTap()
        now += 700
        twoFingerTap()
        assertEquals("a slow second tap still undoes at once", listOf("undo"), events)
    }

    @Test
    fun `tapping too slowly is two separate single taps`() {
        twoFingerTap()
        now += 301
        twoFingerTap()
        drive(now + 1000)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `a late event settles an overdue undo before anything else`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 1000
        stylusDown()
        assertEquals(listOf("undo", "start 9 STYLUS"), events)
    }

    @Test
    fun `stylus right after a double tap undoes first, not after the new stroke`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 20
        stylusDown()
        assertEquals(listOf("undo", "start 9 STYLUS"), events)
    }

    @Test
    fun `scrolling right after a double tap undoes first`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 20
        fingerDown(1, 0f, 0f)
        assertEquals(listOf("undo"), events)
        now += 5
        move(1, 100f)
        assertEquals("undo", events.first())
    }

    @Test
    fun `a single finger held after a double tap is settled by the landing window`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 20
        fingerDown(1)
        assertEquals(now + 100, recognizer.nextDeadlineMs)
        recognizer.onDeadline(now + 100)
        assertEquals(listOf("undo"), events)
    }

    @Test
    fun `two fingers held after a double tap are settled by the tap duration`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 20
        fingerDown(1)
        fingerDown(2, 100f, 0f)
        assertEquals(now + 250, recognizer.nextDeadlineMs)
        recognizer.onDeadline(now + 250)
        assertEquals(listOf("undo"), events)
    }

    @Test
    fun `a one-finger tap ends a sequence`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 20
        fingerDown(1)
        now += 10
        up(1)
        assertEquals(listOf("undo"), events)
        assertEquals(GestureRecognizer.NO_DEADLINE, recognizer.nextDeadlineMs)
    }

    @Test
    fun `fingers that land too far apart in time are not a tap`() {
        fingerDown(1, 100f, 100f)
        now += 101
        fingerDown(2, 200f, 100f)
        now += 10
        up(1)
        up(2)
        now += 50
        twoFingerTap()
        drive(now + 1000)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `fingers held too long are not a tap`() {
        twoFingerTap()
        now += 50
        fingerDown(1, 100f, 100f)
        fingerDown(2, 200f, 100f)
        now += 251
        up(1)
        up(2)
        drive(now + 1000)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `a tap with a small wobble still counts`() {
        twoFingerTap()
        now += 50
        fingerDown(1, 100f, 100f)
        fingerDown(2, 200f, 100f)
        now += 10
        move(1, 110f, 100f)
        move(2, 190f, 105f)
        now += 10
        up(1)
        up(2)
        drive()
        assertEquals(listOf("undo"), events)
    }

    @Test
    fun `a pinch is not a tap`() {
        fingerDown(1, 100f, 100f)
        fingerDown(2, 200f, 100f)
        now += 10
        move(1, 50f, 100f)
        move(2, 250f, 100f)
        now += 10
        up(1)
        up(2)
        assertEquals(GestureRecognizer.NO_DEADLINE, recognizer.nextDeadlineMs)
        assertTrue("transform" in events)
    }

    @Test
    fun `a third finger cancels the tap`() {
        fingerDown(1, 100f, 100f)
        fingerDown(2, 200f, 100f)
        fingerDown(3, 300f, 100f)
        now += 10
        up(3)
        up(1)
        up(2)
        assertEquals(GestureRecognizer.NO_DEADLINE, recognizer.nextDeadlineMs)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `order of lifting does not matter`() {
        fun tapLiftingSecondFirst() {
            fingerDown(1, 100f, 100f)
            fingerDown(2, 200f, 100f)
            now += 10
            up(2)
            now += 10
            up(1)
        }
        tapLiftingSecondFirst()
        now += 50
        tapLiftingSecondFirst()
        drive()
        assertEquals(listOf("undo"), events)
    }

    @Test
    fun `a canceled finger is not a tap but still settles a pending undo`() {
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 20
        fingerDown(1, 100f, 100f)
        recognizer.pointerCancel(1, now)
        assertEquals(listOf("undo"), events)
    }

    // Navigation.

    @Test
    fun `one finger scrolls after the slop, with no jump`() {
        fingerDown(1, 0f, 0f)
        move(1, 10f)
        assertEquals("inside the slop", emptyList<String>(), events)
        move(1, 30f)
        assertEquals("the move that crosses the slop only starts the gesture", 0f, totalDx, 0f)
        move(1, 40f, 5f)
        assertEquals(10f, totalDx, 1e-4f)
        assertEquals(5f, totalDy, 1e-4f)
        up(1)
        assertEquals("transformEnd", events.last())
    }

    @Test
    fun `two fingers pan and zoom`() {
        fingerDown(1, 100f, 100f)
        fingerDown(2, 200f, 100f)
        // Crossing the slop starts the gesture from a spread of 150, so nothing jumps.
        move(1, 50f, 100f)
        assertEquals(1f, totalScale, 0f)
        now += 5
        move(2, 250f, 100f)
        assertEquals(200f / 150f, totalScale, 1e-4f)
        move(1, 60f, 110f)
        move(2, 260f, 110f)
        assertEquals(200f / 150f, totalScale, 1e-4f)
        assertEquals(10f, totalDy, 1e-4f)
    }

    @Test
    fun `transform keeps the page under the fingers when applied to a viewport`() {
        val viewport = Viewport(345f)
        val applied = object : GestureListener by listener {
            override fun onTransform(dx: Float, dy: Float, scale: Float, focusX: Float, focusY: Float) {
                viewport.panX += dx
                viewport.panY += dy
                viewport.zoomTo(viewport.zoom * scale, focusX, focusY)
            }
        }
        val gestures = GestureRecognizer(config, applied)
        gestures.pointerDown(1, PointerKind.FINGER, 100f, 100f, 0)
        gestures.pointerDown(2, PointerKind.FINGER, 300f, 100f, 0)
        gestures.pointerMove(1, 70f, 100f, 1)
        val worldAtLeft = viewport.toWorldX(70f)
        val worldAtRight = viewport.toWorldX(300f)

        gestures.pointerMove(2, 500f, 200f, 2)
        gestures.pointerMove(1, 120f, 200f, 3)

        // A two-point pinch fixes both fingers only when the page may rotate, so check along x and y.
        assertEquals(120f, viewport.toScreenX(worldAtLeft), 1f)
        assertEquals(500f, viewport.toScreenX(worldAtRight), 1f)
    }

    @Test
    fun `changing finger count causes no jump`() {
        fingerDown(1, 0f, 0f)
        move(1, 30f)
        move(1, 40f)
        val before = totalDx
        fingerDown(2, 500f, 0f)
        assertEquals(before, totalDx, 0f)
        move(1, 50f)
        assertEquals("the centroid moved half of the 10", before + 5f, totalDx, 1e-4f)
        up(2)
        move(1, 60f)
        assertEquals("the one left keeps panning", before + 15f, totalDx, 1e-4f)
    }

    @Test
    fun `fingers close together do not explode the zoom`() {
        fingerDown(1, 100f, 100f)
        fingerDown(2, 100f, 100f)
        move(1, 100f, 150f)
        move(2, 100f, 150f)
        move(2, 400f, 150f)
        assertTrue(totalScale.isFinite())
    }

    // The stylus and palms.

    @Test
    fun `stylus draws from start to end`() {
        stylusDown()
        move(9, 10f)
        up(9)
        assertEquals(listOf("start 9 STYLUS", "move 9", "end 9"), events)
    }

    @Test
    fun `the eraser end of the stylus is reported`() {
        stylusDown(kind = PointerKind.STYLUS_ERASER)
        assertEquals(listOf("start 9 STYLUS_ERASER"), events)
    }

    @Test
    fun `a hand resting while drawing is ignored`() {
        stylusDown()
        fingerDown(1, 300f, 300f)
        move(1, 600f, 600f)
        up(1)
        move(9, 10f)
        up(9)
        assertEquals(listOf("start 9 STYLUS", "move 9", "end 9"), events)
    }

    @Test
    fun `a touch right after the stylus lifts is taken for the palm`() {
        stylusDown()
        up(9)
        events.clear()
        now += 100
        fingerDown(1)
        move(1, 100f)
        move(1, 200f)
        up(1)
        assertEquals(emptyList<String>(), events)

        now += 100
        fingerDown(1)
        move(1, 100f)
        move(1, 200f)
        assertEquals("the guard has passed", "transform", events.last())
    }

    @Test
    fun `the stylus interrupts scrolling and the resting fingers stay out of it`() {
        fingerDown(1, 0f, 0f)
        move(1, 100f)
        stylusDown()
        move(1, 200f)
        up(1)
        move(9, 10f)
        assertEquals(listOf("transformEnd", "start 9 STYLUS", "move 9"), events)
    }

    @Test
    fun `a second stylus contact is ignored while drawing`() {
        stylusDown(9)
        stylusDown(8)
        assertEquals(listOf("start 9 STYLUS"), events)
    }

    @Test
    fun `system cancel discards the stroke`() {
        stylusDown()
        recognizer.pointerCancel(9, now)
        assertEquals(listOf("start 9 STYLUS", "cancel 9"), events)
    }

    // Finger drawing.

    @Test
    fun `in finger mode one finger draws at once`() {
        recognizer.mode = InputMode.FINGER_DRAW
        fingerDown(1)
        move(1, 100f)
        up(1)
        assertEquals(listOf("start 1 FINGER", "move 1", "end 1"), events)
    }

    @Test
    fun `in finger mode a second finger landing together cancels the stroke and pans`() {
        recognizer.mode = InputMode.FINGER_DRAW
        fingerDown(1, 100f, 100f)
        now += 20
        fingerDown(2, 200f, 100f)
        assertEquals(listOf("start 1 FINGER", "cancel 1"), events)
        move(1, 100f, 200f)
        move(2, 200f, 300f)
        assertTrue("transform" in events)
    }

    @Test
    fun `in finger mode a stray second finger cannot interrupt a real stroke`() {
        recognizer.mode = InputMode.FINGER_DRAW
        fingerDown(1, 0f, 0f)
        now += 200
        move(1, 100f)
        fingerDown(2, 500f, 500f)
        move(2, 600f, 600f)
        up(2)
        move(1, 110f)
        up(1)
        assertEquals(listOf("start 1 FINGER", "move 1", "move 1", "end 1"), events)
    }

    @Test
    fun `in finger mode two-finger taps still undo`() {
        recognizer.mode = InputMode.FINGER_DRAW
        twoFingerTap()
        now += 50
        twoFingerTap()
        drive()
        assertEquals("undo", events.last())
        assertEquals(1, events.count { it == "undo" })
    }

    @Test
    fun `in finger mode a stroke right after a double tap comes after the undo`() {
        recognizer.mode = InputMode.FINGER_DRAW
        twoFingerTap()
        now += 50
        twoFingerTap()
        events.clear()
        now += 20
        fingerDown(1)
        assertEquals(emptyList<String>(), events)
        move(1, 100f)
        assertEquals("the undo came with the second tap", listOf("start 1 FINGER"), events)
    }

    @Test
    fun `in finger mode the stylus still draws and takes over`() {
        recognizer.mode = InputMode.FINGER_DRAW
        fingerDown(1)
        now += 200
        stylusDown()
        assertEquals(listOf("start 1 FINGER", "cancel 1", "start 9 STYLUS"), events)
    }

    // View only.

    @Test
    fun `view only never draws and never undoes`() {
        recognizer.mode = InputMode.VIEW_ONLY
        twoFingerTap()
        now += 50
        twoFingerTap()
        drive()
        twoFingerTap()
        now += 50
        twoFingerTap()
        now += 50
        twoFingerTap()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `in view only the stylus scrolls like a finger`() {
        recognizer.mode = InputMode.VIEW_ONLY
        stylusDown()
        move(9, 100f)
        move(9, 150f)
        assertEquals(50f, totalDx, 1e-4f)
        assertTrue(events.none { it.startsWith("start") })
    }

    @Test
    fun `changing mode abandons the stroke in progress`() {
        stylusDown()
        recognizer.mode = InputMode.VIEW_ONLY
        assertEquals(listOf("start 9 STYLUS", "cancel 9"), events)
        recognizer.mode = InputMode.STYLUS_DRAW
        twoFingerTap()
        recognizer.mode = InputMode.FINGER_DRAW
        assertEquals(GestureRecognizer.NO_DEADLINE, recognizer.nextDeadlineMs)
        assertTrue(events.none { it == "undo" })
    }

    @Test
    fun `with the finger in scroll mode a finger never starts a drawing, whatever it does`() {
        recognizer.mode = InputMode.STYLUS_DRAW
        // A drag, a long hold, a circle round and round, and a tap and a half.
        fingerDown(1, 100f, 100f)
        for (step in 1..40) {
            now += 10
            move(1, 100f + step * 6f, 100f)
        }
        up(1)
        now += 20
        fingerDown(1, 300f, 300f)
        now += 1_500
        drive()
        up(1)
        now += 20
        fingerDown(1, 400f, 400f)
        for (step in 0..60) {
            now += 8
            val angle = step * 0.4
            move(1, 400f + 60f * Math.cos(angle).toFloat(), 400f + 60f * Math.sin(angle).toFloat())
        }
        up(1)
        now += 40
        fingerDown(1, 50f, 50f)
        now += 30
        up(1)
        now += 30
        fingerDown(1, 52f, 50f)
        drive(now + 1_000)
        assertTrue(events.filter { it.startsWith("start") || it.startsWith("move") }.toString(), events.none { it.startsWith("start") || it.startsWith("move") })
    }

    @Test
    fun `the finger draws in finger mode, and the stylus draws in either`() {
        recognizer.mode = InputMode.FINGER_DRAW
        fingerDown(1, 100f, 100f)
        for (step in 1..10) {
            now += 10
            move(1, 100f + step * 6f, 100f)
        }
        up(1)
        drive(now + 1_000)
        assertTrue(events.any { it.startsWith("start 1 FINGER") })
        events.clear()
        recognizer.mode = InputMode.STYLUS_DRAW
        stylusDown()
        up(9)
        assertEquals("start 9 STYLUS", events.first())
    }
}
