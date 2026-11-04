package app.betternotes.pomodoro

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.app.Notification
import app.betternotes.R
import app.betternotes.core.settings.PomodoroPhase
import app.betternotes.core.settings.PomodoroSettings
import app.betternotes.core.settings.PomodoroState
import app.betternotes.core.settings.PomodoroStore
import app.betternotes.core.settings.SettingsStore
import app.betternotes.settings.SettingsRepository
import app.betternotes.ui.MainActivity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val CHANNEL_ID = "pomodoro"
private const val NOTIFICATION_ID = 1

/** How long after a phase ends the timer waits for its alarm before moving on without it. */
private const val ALARM_GRACE_MS = 3_000L

/**
 * The Pomodoro timer. The state on disk is the truth, and an exact alarm wakes [PomodoroAlarmReceiver]
 * when a phase ends, so the timer keeps going with the app closed. Everything works offline. HyperOS may
 * still delay or drop an alarm if it has been told to restrict the app in the background.
 */
class PomodoroTimer(context: Context) {
    private val app = context.applicationContext
    private val store = PomodoroStore(stateFile(app))
    private val mutableState = MutableStateFlow(store.read())

    val state: StateFlow<PomodoroState> = mutableState

    fun start(settings: PomodoroSettings) = set(mutableState.value.start(settings, now()))

    fun pause(settings: PomodoroSettings) = set(mutableState.value.pause(settings, now()))

    /** Moves on to the next phase without waiting, keeping the timer running if it was. */
    fun skip(settings: PomodoroSettings) {
        val current = mutableState.value
        val next = current.next(settings)
        set(if (current.isRunning) next.start(settings, now()) else next)
    }

    fun reset() = set(PomodoroState())

    /**
     * Catches up with a phase that has ended. The alarm normally gets there first and has already told the
     * owner, so this only reads what it wrote. If the alarm never came, as when the system holds it back,
     * the timer moves on by itself after a short wait, without a notification.
     */
    fun refresh(settings: PomodoroSettings) {
        val current = mutableState.value
        val ended = current.endsAtMs ?: return
        val now = now()
        if (now < ended) return
        val onDisk = store.read()
        if (onDisk != current) {
            mutableState.value = onDisk
        } else if (now - ended >= ALARM_GRACE_MS) {
            set(settle(current, settings, now))
        }
    }

    private fun set(next: PomodoroState) {
        store.write(next)
        mutableState.value = next
        schedule(app, next)
    }

    private fun now() = System.currentTimeMillis()

    internal companion object {
        fun stateFile(context: Context) = File(context.filesDir, "pomodoro.bnt")

        /** Plays every phase that has ended by [nowMs], as a long sleep may have passed several. */
        fun settle(state: PomodoroState, settings: PomodoroSettings, nowMs: Long): PomodoroState {
            var current = state
            while (current.isFinished(nowMs)) current = current.afterFinish(settings)
            return current
        }

        fun schedule(context: Context, state: PomodoroState) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val intent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PomodoroAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val ends = state.endsAtMs
            when {
                ends == null -> alarms.cancel(intent)
                alarms.canScheduleExactAlarms() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ends, intent)
                else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ends, intent)
            }
        }
    }
}

/** A phase ended: says so, and moves on to the next one. */
class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val settings = SettingsStore(SettingsRepository.fileIn(app)).read().pomodoro
        val store = PomodoroStore(PomodoroTimer.stateFile(app))
        val state = store.read()
        // A pause or skip may have raced this alarm, which then has nothing to do.
        if (!state.isFinished(System.currentTimeMillis())) return
        val next = PomodoroTimer.settle(state, settings, System.currentTimeMillis())
        store.write(next)
        PomodoroTimer.schedule(app, next)
        if (settings.notify) notifyFinished(app, state.phase, next)
    }

    private fun notifyFinished(context: Context, finished: PomodoroPhase, next: PomodoroState) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pomodoro_title),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(
            if (finished == PomodoroPhase.FOCUS) R.string.pomodoro_focus_done else R.string.pomodoro_break_done,
        )
        val text = context.getString(
            when (next.phase) {
                PomodoroPhase.FOCUS -> R.string.pomodoro_next_focus
                PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_next_short
                PomodoroPhase.LONG_BREAK -> R.string.pomodoro_next_long
            },
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pomodoro)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
