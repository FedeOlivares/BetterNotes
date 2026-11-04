package app.betternotes.core.settings

/** What a stylus button press came to. */
interface StylusButtonListener {
    /** A button was clicked, with an action to run. */
    fun onStylusAction(action: StylusAction)

    /** The primary button has been held long enough, and the stylus erases until [onHoldEnd]. */
    fun onHoldStart()

    fun onHoldEnd()
}

/**
 * Tells a click of a stylus button from holding it. Like the gesture recognizer it has no clock: whenever
 * [nextDeadlineMs] is set, schedule a call to [onDeadline] for that time.
 */
class StylusButtons(private val listener: StylusButtonListener, var settings: StylusSettings = StylusSettings()) {
    private var primaryDownMs = NOT_DOWN
    private var holding = false

    val nextDeadlineMs: Long
        get() = if (primaryDownMs == NOT_DOWN || holding || settings.primaryHold == StylusHoldAction.NONE) {
            NO_DEADLINE
        } else {
            primaryDownMs + HOLD_MS
        }

    fun primaryDown(timeMs: Long) {
        primaryDownMs = timeMs
        holding = false
    }

    fun primaryUp(timeMs: Long) {
        if (primaryDownMs == NOT_DOWN) return
        onDeadline(timeMs)
        primaryDownMs = NOT_DOWN
        if (holding) {
            holding = false
            listener.onHoldEnd()
        } else {
            run(settings.primaryButton)
        }
    }

    /** The secondary button has no hold, so it acts as soon as it is pressed. */
    fun secondaryDown() = run(settings.secondaryButton)

    fun onDeadline(nowMs: Long) {
        val deadline = nextDeadlineMs
        if (deadline == NO_DEADLINE || nowMs < deadline) return
        holding = true
        listener.onHoldStart()
    }

    private fun run(action: StylusAction) {
        if (action != StylusAction.NONE) listener.onStylusAction(action)
    }

    companion object {
        const val NO_DEADLINE = -1L
        const val HOLD_MS = 400L
        private const val NOT_DOWN = -1L
    }
}
