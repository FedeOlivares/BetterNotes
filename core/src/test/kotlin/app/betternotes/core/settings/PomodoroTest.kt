package app.betternotes.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PomodoroTest {
    private val settings = PomodoroSettings(focusMinutes = 10, shortBreakMinutes = 2, longBreakMinutes = 5, sessionsBeforeLongBreak = 2)
    private val minute = 60_000L

    @Test
    fun `a new timer shows the whole focus session and is not running`() {
        val state = PomodoroState()
        assertFalse(state.isRunning)
        assertEquals(10 * minute, state.remainingMs(settings, 12345))
    }

    @Test
    fun `remaining time follows the clock`() {
        val state = PomodoroState().start(settings, 1000)
        assertEquals(1000 + 10 * minute, state.endsAtMs)
        assertEquals(10 * minute - 500, state.remainingMs(settings, 1500))
        assertEquals(0L, state.remainingMs(settings, 1000 + 20 * minute))
    }

    @Test
    fun `pausing keeps what was left and starting again uses it`() {
        val paused = PomodoroState().start(settings, 0).pause(settings, 3 * minute)
        assertFalse(paused.isRunning)
        assertEquals(7 * minute, paused.remainingMs(settings, 100 * minute))
        val resumed = paused.start(settings, 100 * minute)
        assertEquals(107 * minute, resumed.endsAtMs)
        assertNull(resumed.pausedRemainingMs)
    }

    @Test
    fun `starting twice or pausing twice changes nothing`() {
        val running = PomodoroState().start(settings, 0)
        assertEquals(running, running.start(settings, 5000))
        val paused = running.pause(settings, 1000)
        assertEquals(paused, paused.pause(settings, 9000))
    }

    @Test
    fun `the long break comes after the set number of focus sessions`() {
        var state = PomodoroState()
        state = state.next(settings)
        assertEquals(PomodoroPhase.SHORT_BREAK, state.phase)
        assertEquals(1, state.completedFocus)
        state = state.next(settings)
        assertEquals(PomodoroPhase.FOCUS, state.phase)
        state = state.next(settings)
        assertEquals(PomodoroPhase.LONG_BREAK, state.phase)
        assertEquals(0, state.completedFocus)
        state = state.next(settings)
        assertEquals(PomodoroPhase.FOCUS, state.phase)
    }

    @Test
    fun `a finished phase starts the next one from when it ended`() {
        val running = PomodoroState().start(settings, 0)
        assertFalse(running.isFinished(10 * minute - 1))
        assertTrue(running.isFinished(10 * minute))
        val next = running.afterFinish(settings)
        assertEquals(PomodoroPhase.SHORT_BREAK, next.phase)
        assertEquals(12 * minute, next.endsAtMs)
    }

    @Test
    fun `without auto start the next phase waits`() {
        val manual = settings.copy(autoStartNext = false)
        val next = PomodoroState().start(manual, 0).afterFinish(manual)
        assertEquals(PomodoroPhase.SHORT_BREAK, next.phase)
        assertFalse(next.isRunning)
    }

    @Test
    fun `settings outside the range are refused`() {
        assertThrows(IllegalArgumentException::class.java) { PomodoroSettings(focusMinutes = 0) }
        assertThrows(IllegalArgumentException::class.java) { PomodoroSettings(sessionsBeforeLongBreak = 99) }
    }

    @Test
    fun `settings and state survive a save`() {
        val saved = AppSettings(pomodoro = PomodoroSettings(50, 10, 30, 3, autoStartNext = false, notify = false))
        assertEquals(saved, SettingsCodec.decode(SettingsCodec.encode(saved)))

        val dir = Files.createTempDirectory("pomodoro").toFile()
        try {
            val store = PomodoroStore(dir.resolve("state"))
            assertEquals(PomodoroState(), store.read())
            val running = PomodoroState(PomodoroPhase.LONG_BREAK, 2, 1234L, null)
            store.write(running)
            assertEquals(running, store.read())
            val paused = PomodoroState(PomodoroPhase.FOCUS, 1, null, 999L)
            store.write(paused)
            assertEquals(paused, store.read())
            dir.resolve("state").writeBytes(ByteArray(20))
            assertEquals(PomodoroState(), store.read())
        } finally {
            dir.deleteRecursively()
        }
    }
}
