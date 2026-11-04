package app.betternotes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.gesture.TwoFingerAction
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.GestureSettings
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.settings.StylusAction
import app.betternotes.core.settings.StylusEraserEnd
import app.betternotes.core.settings.StylusHoldAction
import kotlin.math.roundToLong

private const val TIMING_STEP_MS = 50L

/** The sheet over the editor for what the stylus and fingers do. Matches the owner's screenshot. */
@Composable
fun StylusFingerSheet(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSheet(stringResource(R.string.stylus_finger_title), onClose, modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 41.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            StylusCard(settings, onChange)
            SingleFingerCard(settings.gestures.oneFinger) { value -> onChange { it.withGestures { copy(oneFinger = value) } } }
            DoubleFingerCard(settings.gestures.twoFingers) { value -> onChange { it.withGestures { copy(twoFingers = value) } } }
            MoreGesturesCard(settings.gestures) { change -> onChange { it.withGestures(change) } }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private inline fun AppSettings.withGestures(change: GestureSettings.() -> GestureSettings) =
    copy(gestures = gestures.change())

@Composable
private fun StylusCard(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val stylus = settings.stylus
    fun set(change: (AppSettings) -> AppSettings) = onChange(change)
    SheetCard {
        IconCardTitle(SettingsIcons.Pen, stringResource(R.string.stylus))
        DropdownRow(
            stringResource(R.string.stylus_latency),
            stylus.predictInk,
            listOf(true, false),
            { stringResource(if (it) R.string.stylus_latency_fast else R.string.stylus_latency_normal) },
            detail = stringResource(R.string.stylus_latency_detail),
        ) { value -> set { s -> s.copy(stylus = s.stylus.copy(predictInk = value)) } }
        DropdownRow(stringResource(R.string.stylus_primary), stylus.primaryButton, StylusAction.entries, { actionLabel(it) }) { value ->
            set { s -> s.copy(stylus = s.stylus.copy(primaryButton = value)) }
        }
        DropdownRow(stringResource(R.string.stylus_secondary), stylus.secondaryButton, StylusAction.entries, { actionLabel(it) }) { value ->
            set { s -> s.copy(stylus = s.stylus.copy(secondaryButton = value)) }
        }
        DropdownRow(
            stringResource(R.string.stylus_hold),
            stylus.primaryHold,
            StylusHoldAction.entries,
            { stringResource(if (it == StylusHoldAction.ERASE) R.string.stylus_hold_erase else R.string.stylus_none) },
        ) { value -> set { s -> s.copy(stylus = s.stylus.copy(primaryHold = value)) } }
        DropdownRow(
            stringResource(R.string.stylus_eraser_end),
            stylus.eraserEnd,
            StylusEraserEnd.entries,
            { stringResource(if (it == StylusEraserEnd.ERASER) R.string.stylus_eraser else R.string.stylus_none) },
        ) { value -> set { s -> s.copy(stylus = s.stylus.copy(eraserEnd = value)) } }
        Text(
            stringResource(R.string.stylus_note),
            color = LocalSettingsColors.current.secondary,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun actionLabel(action: StylusAction) = stringResource(
    when (action) {
        StylusAction.NONE -> R.string.stylus_none
        StylusAction.PEN -> R.string.stylus_switch_pen
        StylusAction.HIGHLIGHTER -> R.string.stylus_switch_highlighter
        StylusAction.ERASER -> R.string.stylus_switch_eraser
        StylusAction.LASSO -> R.string.stylus_switch_lasso
        StylusAction.UNDO -> R.string.stylus_undo
        StylusAction.REDO -> R.string.stylus_redo
    },
)

@Composable
private fun SingleFingerCard(value: OneFingerSetting, onChange: (OneFingerSetting) -> Unit) {
    SheetCard {
        IconCardTitle(SettingsIcons.OneFinger, stringResource(R.string.single_finger))
        RadioRow(stringResource(R.string.finger_ignored), value == OneFingerSetting.IGNORED) { onChange(OneFingerSetting.IGNORED) }
        RadioRow(stringResource(R.string.finger_draw), value == OneFingerSetting.DRAW) { onChange(OneFingerSetting.DRAW) }
        RadioRow(stringResource(R.string.finger_scroll), value == OneFingerSetting.SCROLL) { onChange(OneFingerSetting.SCROLL) }
    }
}

@Composable
private fun DoubleFingerCard(value: TwoFingerAction, onChange: (TwoFingerAction) -> Unit) {
    SheetCard {
        IconCardTitle(SettingsIcons.TwoFingers, stringResource(R.string.double_finger))
        RadioRow(stringResource(R.string.finger_ignored), value == TwoFingerAction.IGNORED) { onChange(TwoFingerAction.IGNORED) }
        RadioRow(stringResource(R.string.finger_zoom_pan), value == TwoFingerAction.ZOOM_AND_PAN) { onChange(TwoFingerAction.ZOOM_AND_PAN) }
        RadioRow(stringResource(R.string.finger_scroll), value == TwoFingerAction.SCROLL) { onChange(TwoFingerAction.SCROLL) }
    }
}

@Composable
private fun MoreGesturesCard(gestures: GestureSettings, onChange: (GestureSettings.() -> GestureSettings) -> Unit) {
    val range = GestureSettings.MULTI_TAP_WINDOW_RANGE_MS
    val span = (range.last - range.first).toFloat()
    fun setWindow(ms: Long) {
        val stepped = (ms.toFloat() / TIMING_STEP_MS).roundToLong() * TIMING_STEP_MS
        val clamped = stepped.coerceIn(range)
        onChange { copy(multiTapWindowMs = clamped) }
    }
    SheetCard {
        IconCardTitle(SettingsIcons.Hand, stringResource(R.string.more_gestures))
        ToggleRow(stringResource(R.string.gesture_zoom_tap), gestures.doubleTapZoom) { value -> onChange { copy(doubleTapZoom = value) } }
        DropdownRow(
            stringResource(R.string.gesture_undo),
            gestures.undoTaps,
            listOf(true, false),
            { stringResource(if (it) R.string.gesture_undo_on else R.string.gesture_off) },
        ) { value -> onChange { copy(undoTaps = value) } }
        DropdownRow(
            stringResource(R.string.gesture_redo),
            gestures.redoTaps,
            listOf(true, false),
            { stringResource(if (it) R.string.gesture_redo_on else R.string.gesture_off) },
        ) { value -> onChange { copy(redoTaps = value) } }
        SettingRow(stringResource(R.string.gesture_timing), detail = stringResource(R.string.gesture_timing_detail)) {
            ValuePill(stringResource(R.string.gesture_timing_value, gestures.multiTapWindowMs.toInt()))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ValueSlider(
                fraction = (gestures.multiTapWindowMs - range.first) / span,
                onChange = { setWindow(range.first + (it * span).toLong()) },
                onStep = { setWindow(gestures.multiTapWindowMs + it * TIMING_STEP_MS) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(0.dp))
        }
    }
}
