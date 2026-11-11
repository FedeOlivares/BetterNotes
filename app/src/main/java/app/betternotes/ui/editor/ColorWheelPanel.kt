package app.betternotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import app.betternotes.core.tools.NoteOptions
import kotlin.math.sin
import android.graphics.Color as AndroidColor

/**
 * The owner asks for a color wheel, and the screenshot shows only the button that opens it. This is a
 * plain wheel in the panel's style until there is a screenshot of the real one to match.
 */
@Composable
internal fun ColorWheelPanel(state: EditorState, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val start = remember(state.colorTarget) {
        FloatArray(3).also { AndroidColor.colorToHSV(state.colorStart, it) }
    }
    var hue by remember(state.colorTarget) { mutableFloatStateOf(start[0]) }
    var saturation by remember(state.colorTarget) { mutableFloatStateOf(start[1]) }
    var value by remember(state.colorTarget) { mutableFloatStateOf(start[2]) }
    val picked = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    // A post-it's color can be see-through, with an opacity of its own or the same as the others'.
    val hasOpacity = state.colorHasOpacity
    val general = state.stickyOpacity
    var opacity by remember(state.colorTarget) { mutableFloatStateOf(state.opacityStart) }
    var follows by remember(state.colorTarget) { mutableStateOf(state.followsGeneralStart) }
    val shownOpacity = if (hasOpacity && follows) general else opacity

    PanelSheet(stringResource(R.string.panel_color), onClose = { state.colorTarget = null }, modifier = modifier) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            HueSaturationWheel(hue, saturation) { h, s ->
                hue = h
                saturation = s
            }
        }
        StepperSlider(
            fraction = value,
            onChange = { value = it },
            steps = 20,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasOpacity) {
            Text(
                stringResource(R.string.panel_opacity_percent, (shownOpacity * 100f).roundToInt()),
                color = colors.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = PanelPadding),
            )
            StepperSlider(
                fraction = (shownOpacity - NoteOptions.MIN_OPACITY) / (1f - NoteOptions.MIN_OPACITY),
                onChange = { f ->
                    opacity = opacityAt(f)
                    follows = false
                },
                steps = OPACITY_STEPS,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.colorTarget == ColorTarget.NOTE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding),
                ) {
                    Text(
                        stringResource(R.string.panel_opacity_general, (general * 100f).roundToInt()),
                        color = colors.label,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    PanelSwitch(follows, stringResource(R.string.panel_opacity_general, (general * 100f).roundToInt())) { follows = it }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(PanelPadding),
        ) {
            ColorDot(if (hasOpacity) NoteOptions.argb(picked, shownOpacity) else picked, diameter = 35.dp)
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(35.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.accentContainer)
                    .clickable(role = Role.Button) { state.applyColor(picked, if (hasOpacity && !follows) opacity else null) }
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    stringResource(R.string.panel_done),
                    color = colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun HueSaturationWheel(hue: Float, saturation: Float, onPick: (hue: Float, saturation: Float) -> Unit) {
    val latestPick by rememberUpdatedState(onPick)
    val thumbColor = LocalEditorColors.current.title
    Canvas(
        Modifier
            .size(240.dp)
            .pointerInput(Unit) {
                fun pick(at: Offset) {
                    val radius = min(size.width, size.height) / 2f
                    val dx = at.x - size.width / 2f
                    val dy = at.y - size.height / 2f
                    val degrees = (atan2(dy, dx) * RADIANS_TO_DEGREES + 360f) % 360f
                    latestPick(degrees, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        pick(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(HueStops, center), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
        val angle = hue / RADIANS_TO_DEGREES
        val thumb = Offset(
            center.x + cos(angle) * saturation * radius,
            center.y + sin(angle) * saturation * radius,
        )
        drawCircle(thumbColor, 9.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
    }
}

/** 5% to 100% in steps of 5%. */
internal const val OPACITY_STEPS = 19

/** The opacity at [fraction] of an opacity slider: from the least to full, in steps of 5%. */
internal fun opacityAt(fraction: Float): Float = (NoteOptions.MIN_OPACITY + fraction * (1f - NoteOptions.MIN_OPACITY)).let { (it * 20f).roundToInt() / 20f }

private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()
