package app.betternotes.core.tools

/**
 * The tail of a laser pointer: recent positions that fade with age. It is a fixed ring of samples, so
 * following the pen allocates nothing. A new [start] lifts the pen, leaving the old tail to fade without
 * joining it to the new one.
 */
class FadingTrail(private val lifetimeMs: Long, capacity: Int = DEFAULT_CAPACITY) {
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val times = LongArray(capacity)
    private val joined = BooleanArray(capacity)
    private var head = 0
    private var size = 0

    fun start(x: Float, y: Float, timeMs: Long) = push(x, y, timeMs, joinsPrevious = false)

    fun add(x: Float, y: Float, timeMs: Long) = push(x, y, timeMs, joinsPrevious = size > 0)

    /** Forgets samples that have faded out. Returns true while anything is left to draw. */
    fun prune(nowMs: Long): Boolean {
        while (size > 0 && nowMs - times[head] >= lifetimeMs) {
            head = (head + 1) % xs.size
            size--
        }
        return size > 0
    }

    /** Calls [draw] for each joined pair of samples, oldest first. [strength] falls from 1 to 0 with age. */
    inline fun forEachSegment(nowMs: Long, draw: (x0: Float, y0: Float, x1: Float, y1: Float, strength: Float) -> Unit) {
        for (i in 1 until sampleCount) {
            if (joinedAt(i)) draw(xAt(i - 1), yAt(i - 1), xAt(i), yAt(i), strengthAt(i, nowMs))
        }
    }

    /** The index of the newest sample, for the bright point at the tip, or -1 if there are none. */
    val tipIndex get() = if (size > 0) size - 1 else -1

    @PublishedApi internal val sampleCount get() = size

    fun xAt(index: Int) = xs[slot(index)]

    fun yAt(index: Int) = ys[slot(index)]

    fun strengthAt(index: Int, nowMs: Long) =
        (1f - (nowMs - times[slot(index)]).toFloat() / lifetimeMs).coerceIn(0f, 1f)

    @PublishedApi internal fun joinedAt(index: Int) = joined[slot(index)]

    private fun slot(index: Int) = (head + index) % xs.size

    private fun push(x: Float, y: Float, timeMs: Long, joinsPrevious: Boolean) {
        if (size == xs.size) {
            head = (head + 1) % xs.size
            size--
        }
        val at = slot(size)
        xs[at] = x
        ys[at] = y
        times[at] = timeMs
        joined[at] = joinsPrevious
        size++
    }

    private companion object {
        /** About two seconds of a pen at 240 samples a second. */
        const val DEFAULT_CAPACITY = 512
    }
}
