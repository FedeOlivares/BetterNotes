package app.betternotes.core.settings

import app.betternotes.core.format.WireEnum
import app.betternotes.core.format.decodeFile
import app.betternotes.core.format.encodeFile
import app.betternotes.core.format.writeAtomically
import java.io.File
import java.io.IOException

/** The lengths and habits of the Pomodoro timer. */
data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
    val autoStartNext: Boolean = true,
    val notify: Boolean = true,
) {
    init {
        require(focusMinutes in MINUTES_RANGE && shortBreakMinutes in MINUTES_RANGE && longBreakMinutes in MINUTES_RANGE) {
            "Minutes out of range"
        }
        require(sessionsBeforeLongBreak in SESSIONS_RANGE) { "Sessions out of range" }
    }

    fun minutesOf(phase: PomodoroPhase) = when (phase) {
        PomodoroPhase.FOCUS -> focusMinutes
        PomodoroPhase.SHORT_BREAK -> shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> longBreakMinutes
    }

    companion object {
        val MINUTES_RANGE = 1..180
        val SESSIONS_RANGE = 1..12
    }
}

enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

private const val MS_PER_MINUTE = 60_000L

/**
 * Where the timer is. It holds an end time rather than counting ticks, so that it stays right however
 * rarely anything looks at it, and across the app being killed. Immutable.
 */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    /** Focus sessions finished since the last long break. */
    val completedFocus: Int = 0,
    /** When the phase ends, if the timer is running. */
    val endsAtMs: Long? = null,
    /** What is left when the timer is not running. Null means the whole phase. */
    val pausedRemainingMs: Long? = null,
) {
    val isRunning get() = endsAtMs != null

    fun remainingMs(settings: PomodoroSettings, nowMs: Long): Long = when {
        endsAtMs != null -> (endsAtMs - nowMs).coerceAtLeast(0L)
        pausedRemainingMs != null -> pausedRemainingMs
        else -> settings.minutesOf(phase) * MS_PER_MINUTE
    }

    fun start(settings: PomodoroSettings, nowMs: Long): PomodoroState =
        if (isRunning) this else copy(endsAtMs = nowMs + remainingMs(settings, nowMs), pausedRemainingMs = null)

    fun pause(settings: PomodoroSettings, nowMs: Long): PomodoroState =
        if (!isRunning) this else copy(endsAtMs = null, pausedRemainingMs = remainingMs(settings, nowMs))

    /** The start of the next phase, not running. Only a finished focus session counts toward the long break. */
    fun next(settings: PomodoroSettings): PomodoroState = when (phase) {
        PomodoroPhase.FOCUS -> {
            val done = completedFocus + 1
            if (done >= settings.sessionsBeforeLongBreak) {
                PomodoroState(PomodoroPhase.LONG_BREAK, 0)
            } else {
                PomodoroState(PomodoroPhase.SHORT_BREAK, done)
            }
        }
        PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> PomodoroState(PomodoroPhase.FOCUS, completedFocus)
    }

    /**
     * Settles a phase whose time has passed, ending at its finish time. If the next one starts by
     * itself, it starts from that moment rather than from now, so a late alarm doesn't stretch it.
     */
    fun afterFinish(settings: PomodoroSettings): PomodoroState {
        val finishedAt = endsAtMs ?: return this
        val following = next(settings)
        return if (settings.autoStartNext) following.start(settings, finishedAt) else following
    }

    fun isFinished(nowMs: Long) = endsAtMs != null && nowMs >= endsAtMs
}

private const val STATE_MAGIC = "BNPM"
private val PHASES = WireEnum(PomodoroPhase.FOCUS, PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK)

/** The timer's state, on its own because the alarm reads and writes it with the app closed. Blocks on the disk. */
class PomodoroStore(private val file: File) {
    /** A damaged file only loses the timer's place, so it reads as a fresh timer. */
    fun read(): PomodoroState {
        if (!file.exists()) return PomodoroState()
        return try {
            decodeFile(file.readBytes(), STATE_MAGIC) {
                val phase = PHASES.read(this)
                val completed = varint()
                val ends = if (bool()) long() else null
                val paused = if (bool()) long() else null
                PomodoroState(phase, completed, ends, paused)
            }
        } catch (_: IOException) {
            PomodoroState()
        }
    }

    fun write(state: PomodoroState) = writeAtomically(
        file,
        encodeFile(STATE_MAGIC) {
            PHASES.write(this, state.phase)
            varint(state.completedFocus)
            bool(state.endsAtMs != null)
            state.endsAtMs?.let { long(it) }
            bool(state.pausedRemainingMs != null)
            state.pausedRemainingMs?.let { long(it) }
        },
    )
}
