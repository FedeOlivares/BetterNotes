package app.betternotes.core.gesture

/**
 * Notices a pointer staying still after it has been moving, which is how a stroke asks to become a
 * straight line. Moves within [slop] of where the pointer settled do not count, since a hand is never
 * quite still. A pointer that has not moved yet is not holding, so resting the pen before a stroke
 * does nothing. It has no clock: the caller asks with the current time.
 */
class HoldDetector(private val slop: Float) {
    /** Where the pointer last was. */
    var x = 0f
        private set
    var y = 0f
        private set

    private var anchorX = 0f
    private var anchorY = 0f
    private var anchorTimeMs = 0L
    private var moved = false

    fun start(x: Float, y: Float, timeMs: Long) {
        this.x = x
        this.y = y
        anchorX = x
        anchorY = y
        anchorTimeMs = timeMs
        moved = false
    }

    fun move(x: Float, y: Float, timeMs: Long) {
        this.x = x
        this.y = y
        val dx = x - anchorX
        val dy = y - anchorY
        if (dx * dx + dy * dy <= slop * slop) return
        anchorX = x
        anchorY = y
        anchorTimeMs = timeMs
        moved = true
    }

    fun isHeld(nowMs: Long, delayMs: Long) = moved && nowMs - anchorTimeMs >= delayMs

    /** When the pointer will count as held if it stays where it is. */
    fun heldAtMs(delayMs: Long) = anchorTimeMs + delayMs
}
