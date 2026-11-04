package app.betternotes.core.gesture

import kotlin.math.hypot

enum class PointerKind { FINGER, STYLUS, STYLUS_ERASER }

enum class InputMode {
    /** The stylus draws and fingers move the page. */
    STYLUS_DRAW,

    /** One finger draws too, and two fingers move the page. */
    FINGER_DRAW,

    /** Nothing edits: the stylus moves the page like a finger, and undo and redo do nothing. */
    VIEW_ONLY,
}

/** What one finger does when it is not drawing. */
enum class OneFingerAction { IGNORED, SCROLL }

/** What two fingers do when they move. */
enum class TwoFingerAction { IGNORED, ZOOM_AND_PAN, SCROLL }

data class GestureConfig(
    /** Longest gap between one two-finger tap lifting and the next one landing. Undo waits this long. */
    val multiTapWindowMs: Long = 300,
    /** Longest a two-finger tap may stay down. */
    val tapMaxDurationMs: Long = 250,
    /** Longest gap between two fingers landing for them to count as one tap. */
    val fingerLandingWindowMs: Long = 100,
    /** How far a finger may wander, in pixels, before it counts as moving rather than tapping. */
    val touchSlopPx: Float = 24f,
    /** How long after the stylus lifts a new touch is still taken for the palm. */
    val palmGuardMs: Long = 150,
    val oneFinger: OneFingerAction = OneFingerAction.SCROLL,
    val twoFingers: TwoFingerAction = TwoFingerAction.ZOOM_AND_PAN,
    /** Off, two taps do nothing. A third still redoes, if that is on. */
    val undoTaps: Boolean = true,
    /** Off, nothing can follow the second tap, so undo need not wait. */
    val redoTaps: Boolean = true,
    /** One finger tapping twice calls [GestureListener.onZoomTap], except where a finger draws. */
    val zoomTaps: Boolean = false,
)

/**
 * What the recognizer decided. Calls arrive on the input thread, and none of them allocate,
 * because input can run at 360 Hz.
 */
interface GestureListener {
    fun onDrawStart(pointerId: Int, kind: PointerKind, x: Float, y: Float)
    fun onDrawMove(pointerId: Int, x: Float, y: Float)
    fun onDrawEnd(pointerId: Int)

    /** The system or a second finger took this pointer back, so its stroke should be discarded. */
    fun onDrawCancel(pointerId: Int)

    /**
     * Fingers moved the page. Pan by ([dx], [dy]) in pixels, then multiply zoom by [scale] about
     * ([focusX], [focusY]). That order keeps the page under the fingers.
     */
    fun onTransform(dx: Float, dy: Float, scale: Float, focusX: Float, focusY: Float)

    /** The fingers have lifted, so this is the time to start a fling or re-render sharply. */
    fun onTransformEnd()

    fun onUndo()
    fun onRedo()

    /** One finger tapped twice at about this point. */
    fun onZoomTap(x: Float, y: Float) = Unit
}

/**
 * Decides what each touch means, with no reference to Android, so it can be tested on the JVM.
 *
 * Feed it every pointer event, in order, with the event's own timestamp. Moves are per pointer, and
 * an unknown pointer is ignored, so it is safe to forward everything.
 *
 * Two fingers tapping twice undoes and three times redoes. So undo cannot fire on the second tap,
 * because a third may follow. The recognizer has no clock of its own: whenever [nextDeadlineMs]
 * is set, schedule a call to [onDeadline] for that time. A late call is harmless, and any newer
 * event settles an overdue decision first, so undo always comes before what the next touch does.
 */
class GestureRecognizer(
    var config: GestureConfig = GestureConfig(),
    private val listener: GestureListener,
) {
    /** Changing the mode abandons any gesture in progress. */
    var mode = InputMode.STYLUS_DRAW
        set(value) {
            if (field != value) {
                reset()
                field = value
            }
        }

    // Navigation fingers. Only two are tracked, and any other touch is ignored as if it were palm.
    private val fingerId = IntArray(MAX_FINGERS)
    private val currentX = FloatArray(MAX_FINGERS)
    private val currentY = FloatArray(MAX_FINGERS)
    private val downX = FloatArray(MAX_FINGERS)
    private val downY = FloatArray(MAX_FINGERS)
    private var fingerCount = 0

    private var navigating = false
    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var lastSpread = 0f

    private var drawPointer = NO_POINTER
    private var stylusDrawing = false
    private var palmGuardUntilMs = Long.MIN_VALUE

    // The touch in progress might still turn out to be a tap.
    private var tapCandidate = false
    private var tapDownMs = 0L
    private var tapFingers = 0

    // Taps already completed in this sequence.
    private var tapCount = 0
    private var lastTapUpMs = 0L

    // The last one-finger tap, which a second could turn into a zoom.
    private var singleTapUpMs = NO_SINGLE_TAP
    private var singleTapX = 0f
    private var singleTapY = 0f

    /** When [onDeadline] should next be called, or [NO_DEADLINE] if nothing is waiting on time. */
    val nextDeadlineMs: Long
        get() = when {
            tapCount == 0 -> NO_DEADLINE
            tapCandidate -> candidateLimitMs()
            else -> lastTapUpMs + config.multiTapWindowMs
        }

    /** Settles anything whose time has passed. See [nextDeadlineMs]. */
    fun onDeadline(nowMs: Long) {
        if (tapCount == 0) return
        if (tapCandidate) {
            if (nowMs >= candidateLimitMs()) failTapCandidate()
        } else if (nowMs >= lastTapUpMs + config.multiTapWindowMs) {
            endTapSequence()
        }
    }

    fun pointerDown(pointerId: Int, kind: PointerKind, x: Float, y: Float, timeMs: Long) {
        onDeadline(timeMs)
        if (kind == PointerKind.FINGER || mode == InputMode.VIEW_ONLY) {
            fingerDown(pointerId, x, y, timeMs)
        } else {
            stylusDown(pointerId, kind, x, y)
        }
    }

    fun pointerMove(pointerId: Int, x: Float, y: Float, timeMs: Long) {
        onDeadline(timeMs)
        if (pointerId == drawPointer) listener.onDrawMove(pointerId, x, y)
        val slot = slotOf(pointerId)
        if (slot < 0) return
        currentX[slot] = x
        currentY[slot] = y

        if (tapCandidate && movedPastSlop(slot)) failTapCandidate()
        if (drawPointer != NO_POINTER) return
        if (navigating) {
            if (fingersMayNavigate()) emitTransform() else rebase()
        } else if (movedPastSlop(slot)) {
            navigating = true
            rebase()
        }
    }

    fun pointerUp(pointerId: Int, timeMs: Long) {
        onDeadline(timeMs)
        if (pointerId == drawPointer) {
            if (stylusDrawing) palmGuardUntilMs = timeMs + config.palmGuardMs
            listener.onDrawEnd(pointerId)
            clearDraw()
        }
        // Read before the finger leaves its slot.
        val slot = slotOf(pointerId)
        val liftX = if (slot >= 0) currentX[slot] else 0f
        val liftY = if (slot >= 0) currentY[slot] else 0f
        if (!removeFinger(pointerId) || fingerCount > 0) return
        endNavigation()
        if (tapCandidate) {
            tapCandidate = false
            val inTime = timeMs - tapDownMs < config.tapMaxDurationMs
            when {
                tapFingers == 2 && inTime -> tapCompleted(timeMs)
                tapFingers == 1 && inTime -> {
                    endTapSequence()
                    singleTapCompleted(liftX, liftY, timeMs)
                }
                else -> endTapSequence()
            }
        }
    }

    /** The system took the pointer back, as it does when it decides a touch was a palm. */
    fun pointerCancel(pointerId: Int, timeMs: Long) {
        onDeadline(timeMs)
        if (pointerId == drawPointer) {
            listener.onDrawCancel(pointerId)
            clearDraw()
        }
        if (!removeFinger(pointerId)) return
        if (tapCandidate) failTapCandidate()
        if (fingerCount == 0) endNavigation()
    }

    /** Drops every touch in progress and any pending undo, as when the editor closes. */
    fun reset() {
        if (drawPointer != NO_POINTER) listener.onDrawCancel(drawPointer)
        clearDraw()
        fingerCount = 0
        endNavigation()
        tapCandidate = false
        tapCount = 0
        palmGuardUntilMs = Long.MIN_VALUE
    }

    private fun stylusDown(pointerId: Int, kind: PointerKind, x: Float, y: Float) {
        if (stylusDrawing) return
        // Undo, if one is pending, must land before the new stroke rather than erase it.
        tapCandidate = false
        endTapSequence()
        if (drawPointer != NO_POINTER) {
            listener.onDrawCancel(drawPointer)
            clearDraw()
        }
        // Any finger still down is the hand resting on the screen, so it stops being tracked.
        fingerCount = 0
        endNavigation()
        startDraw(pointerId, kind, x, y)
        stylusDrawing = true
    }

    private fun fingerDown(pointerId: Int, x: Float, y: Float, timeMs: Long) {
        if (stylusDrawing || timeMs < palmGuardUntilMs) return
        when (fingerCount) {
            0 -> firstFingerDown(pointerId, x, y, timeMs)
            1 -> secondFingerDown(pointerId, x, y, timeMs)
            else -> if (tapCandidate) failTapCandidate()
        }
    }

    private fun firstFingerDown(pointerId: Int, x: Float, y: Float, timeMs: Long) {
        addFinger(pointerId, x, y)
        tapCandidate = true
        tapDownMs = timeMs
        tapFingers = 1
        // While taps are pending this could be the next one, so a stroke has to wait until it isn't.
        if (mode == InputMode.FINGER_DRAW && tapCount == 0) startDraw(pointerId, PointerKind.FINGER, x, y)
    }

    private fun secondFingerDown(pointerId: Int, x: Float, y: Float, timeMs: Long) {
        val landedTogether = tapCandidate && timeMs - tapDownMs <= config.fingerLandingWindowMs
        if (drawPointer != NO_POINTER) {
            // Too late to be a pinch or a tap, so this stroke is real and the finger is a stray touch.
            if (!landedTogether) return
            listener.onDrawCancel(drawPointer)
            clearDraw()
        }
        if (landedTogether) tapFingers = 2 else if (tapCandidate) failTapCandidate()
        // A stroke that was waiting on the failed candidate has now started and owns the screen.
        if (drawPointer != NO_POINTER) return
        addFinger(pointerId, x, y)
        if (navigating) rebase()
    }

    private fun startDraw(pointerId: Int, kind: PointerKind, x: Float, y: Float) {
        drawPointer = pointerId
        listener.onDrawStart(pointerId, kind, x, y)
    }

    private fun clearDraw() {
        drawPointer = NO_POINTER
        stylusDrawing = false
    }

    private fun candidateLimitMs() =
        tapDownMs + if (tapFingers == 2) config.tapMaxDurationMs else config.fingerLandingWindowMs

    private fun tapCompleted(timeMs: Long) {
        tapCount++
        lastTapUpMs = timeMs
        singleTapUpMs = NO_SINGLE_TAP
        if (tapCount == REDO_TAPS) {
            tapCount = 0
            if (config.redoTaps && mode != InputMode.VIEW_ONLY) listener.onRedo()
        } else if (tapCount == UNDO_TAPS && !config.redoTaps) {
            endTapSequence()
        }
    }

    private fun singleTapCompleted(x: Float, y: Float, timeMs: Long) {
        if (!config.zoomTaps || mode == InputMode.FINGER_DRAW) return
        val isSecond = singleTapUpMs != NO_SINGLE_TAP && timeMs - singleTapUpMs <= ZOOM_TAP_WINDOW_MS &&
            hypot(x - singleTapX, y - singleTapY) <= config.touchSlopPx * ZOOM_TAP_REACH
        if (isSecond) {
            singleTapUpMs = NO_SINGLE_TAP
            listener.onZoomTap(x, y)
        } else {
            singleTapUpMs = timeMs
            singleTapX = x
            singleTapY = y
        }
    }

    /** Only view-only mode overrides "ignored", since moving the page is all it has left. */
    private fun fingersMayNavigate() = when (fingerCount) {
        1 -> config.oneFinger != OneFingerAction.IGNORED || mode == InputMode.VIEW_ONLY
        else -> config.twoFingers != TwoFingerAction.IGNORED
    }

    /** The touch in progress is not a tap, which also closes any sequence it might have continued. */
    private fun failTapCandidate() {
        tapCandidate = false
        endTapSequence()
        val deferredStroke = mode == InputMode.FINGER_DRAW && tapFingers == 1 && fingerCount == 1 &&
            drawPointer == NO_POINTER && !navigating
        if (deferredStroke) startDraw(fingerId[0], PointerKind.FINGER, currentX[0], currentY[0])
    }

    private fun endTapSequence() {
        val isUndo = tapCount == UNDO_TAPS
        tapCount = 0
        if (isUndo && config.undoTaps && mode != InputMode.VIEW_ONLY) listener.onUndo()
    }

    private fun endNavigation() {
        if (navigating) {
            navigating = false
            listener.onTransformEnd()
        }
    }

    private fun addFinger(pointerId: Int, x: Float, y: Float) {
        val slot = fingerCount++
        fingerId[slot] = pointerId
        currentX[slot] = x
        currentY[slot] = y
        downX[slot] = x
        downY[slot] = y
    }

    private fun removeFinger(pointerId: Int): Boolean {
        val slot = slotOf(pointerId)
        if (slot < 0) return false
        fingerCount--
        if (slot == 0 && fingerCount == 1) {
            fingerId[0] = fingerId[1]
            currentX[0] = currentX[1]
            currentY[0] = currentY[1]
            downX[0] = downX[1]
            downY[0] = downY[1]
        }
        if (navigating && fingerCount > 0) rebase()
        return true
    }

    private fun slotOf(pointerId: Int): Int {
        for (slot in 0 until fingerCount) {
            if (fingerId[slot] == pointerId) return slot
        }
        return -1
    }

    private fun movedPastSlop(slot: Int) =
        hypot(currentX[slot] - downX[slot], currentY[slot] - downY[slot]) > config.touchSlopPx

    /** Restarts the deltas from where the fingers are now, so a finger landing or lifting causes no jump. */
    private fun rebase() {
        lastCentroidX = centroidX()
        lastCentroidY = centroidY()
        lastSpread = spread()
    }

    private fun emitTransform() {
        val cx = centroidX()
        val cy = centroidY()
        val newSpread = spread()
        val zooms = fingerCount == 2 && lastSpread >= MIN_SPREAD_PX && config.twoFingers == TwoFingerAction.ZOOM_AND_PAN
        val scale = if (zooms) newSpread / lastSpread else 1f
        listener.onTransform(cx - lastCentroidX, cy - lastCentroidY, scale, cx, cy)
        lastCentroidX = cx
        lastCentroidY = cy
        lastSpread = newSpread
    }

    private fun centroidX() = if (fingerCount == 2) (currentX[0] + currentX[1]) / 2f else currentX[0]
    private fun centroidY() = if (fingerCount == 2) (currentY[0] + currentY[1]) / 2f else currentY[0]
    private fun spread() =
        if (fingerCount == 2) hypot(currentX[0] - currentX[1], currentY[0] - currentY[1]) else 0f

    companion object {
        const val NO_DEADLINE = -1L
        private const val NO_POINTER = -1
        private const val MAX_FINGERS = 2
        private const val UNDO_TAPS = 2
        private const val REDO_TAPS = 3

        /** Fingers this close are effectively one point, and dividing by the gap would explode the zoom. */
        private const val MIN_SPREAD_PX = 1f

        private const val NO_SINGLE_TAP = -1L

        /** Taken from Android's own double tap timeout. */
        private const val ZOOM_TAP_WINDOW_MS = 300L

        /** Two taps this many touch slops apart still count as one spot. */
        private const val ZOOM_TAP_REACH = 4f
    }
}
