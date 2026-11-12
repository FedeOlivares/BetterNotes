package app.betternotes.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.PomodoroPhase
import app.betternotes.core.settings.PomodoroSettings
import app.betternotes.pomodoro.PomodoroTimer
import app.betternotes.ui.theme.LocalSettingsColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import kotlinx.coroutines.delay
import java.util.Locale

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * The Pomodoro timer. The owner asked for it in place of the reference's Notifications card, and no
 * screenshot of it exists, so it follows the look of the cards around it. It reads the clock once a
 * second only while it is on screen and running.
 */
@Composable
internal fun PomodoroCard(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val timer = remember { PomodoroTimer(context) }
    val state by timer.state.collectAsState()
    val pomodoro = settings.pomodoro
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isRunning) {
        timer.refresh(pomodoro)
        while (timer.state.value.isRunning) {
            now = System.currentTimeMillis()
            timer.refresh(pomodoro)
            delay(MS_PER_SECOND - now % MS_PER_SECOND)
        }
        now = System.currentTimeMillis()
    }

    fun update(change: PomodoroSettings.() -> PomodoroSettings) = onChange { it.copy(pomodoro = it.pomodoro.change()) }
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update { copy(notify = granted) }
    }

    SettingsCard {
        CardTitle(stringResource(R.string.pomodoro_title))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S12)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(phaseName(state.phase)), color = colors.secondary, fontSize = TextSize.S17)
                Text(clock(state.remainingMs(pomodoro, now)), color = colors.text, fontSize = TextSize.S44, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S6)) {
                TimerAction(stringResource(R.string.pomodoro_reset)) { timer.reset() }
                TimerAction(stringResource(R.string.pomodoro_skip)) { timer.skip(pomodoro) }
                TimerAction(stringResource(if (state.isRunning) R.string.pomodoro_pause else R.string.pomodoro_start)) {
                    if (state.isRunning) {
                        timer.pause(pomodoro)
                    } else {
                        val allowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        if (pomodoro.notify && !allowed) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        timer.start(pomodoro)
                    }
                }
            }
        }
        MinutesRow(stringResource(R.string.pomodoro_focus_minutes), pomodoro.focusMinutes) { update { copy(focusMinutes = it) } }
        MinutesRow(stringResource(R.string.pomodoro_short_minutes), pomodoro.shortBreakMinutes) { update { copy(shortBreakMinutes = it) } }
        MinutesRow(stringResource(R.string.pomodoro_long_minutes), pomodoro.longBreakMinutes) { update { copy(longBreakMinutes = it) } }
        StepperRow(stringResource(R.string.pomodoro_sessions), pomodoro.sessionsBeforeLongBreak, PomodoroSettings.SESSIONS_RANGE) {
            update { copy(sessionsBeforeLongBreak = it) }
        }
        ToggleRow(stringResource(R.string.pomodoro_auto_start), pomodoro.autoStartNext) { value -> update { copy(autoStartNext = value) } }
        ToggleRow(stringResource(R.string.pomodoro_notify), pomodoro.notify, stringResource(R.string.pomodoro_notify_detail)) { value ->
            val allowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (value && !allowed) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS) else update { copy(notify = value) }
        }
    }
}

private fun phaseName(phase: PomodoroPhase) = when (phase) {
    PomodoroPhase.FOCUS -> R.string.pomodoro_phase_focus
    PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_phase_short
    PomodoroPhase.LONG_BREAK -> R.string.pomodoro_phase_long
}

/** Rounds up, as a countdown should, so it shows 25:00 at the start and reaches 00:00 at the end. */
internal fun clock(ms: Long): String {
    val seconds = (ms + MS_PER_SECOND - 1) / MS_PER_SECOND
    return String.format(Locale.ROOT, "%02d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

@Composable
private fun TimerAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = LocalSettingsColors.current.accent,
        fontSize = TextSize.S19_5,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = Spacing.S12, vertical = Spacing.S16),
    )
}

@Composable
private fun MinutesRow(label: String, value: Int, onChange: (Int) -> Unit) =
    StepperRow(label, value, PomodoroSettings.MINUTES_RANGE, onChange)

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SettingRow(label) {
        RoundIcon(SettingsIcons.Minus, 26.dp, stringResource(R.string.settings_less)) { onChange((value - 1).coerceIn(range)) }
        ValuePill(value.toString())
        RoundIcon(SettingsIcons.Plus, 26.dp, stringResource(R.string.settings_more)) { onChange((value + 1).coerceIn(range)) }
    }
}
