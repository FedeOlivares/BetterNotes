package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.tools.NoteOptions
import app.betternotes.ui.common.ColorPickerState
import app.betternotes.ui.common.HexField
import app.betternotes.ui.common.HueSaturationWheel
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import kotlin.math.roundToInt

/**
 * The editor's way into the color wheel: [ColorWheelSheet] for the color the tool or selection is changing. A post-it's color can be
 * see-through, with an opacity of its own or the same as the others', so for those the sheet also has the opacity.
 */
@Composable
internal fun ColorWheelPanel(state: EditorState, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val initial = remember(state.colorTarget) { state.colorStart }
    val hasOpacity = state.colorHasOpacity
    val general = state.stickyOpacity
    var opacity by remember(state.colorTarget) { mutableFloatStateOf(state.opacityStart) }
    var follows by remember(state.colorTarget) { mutableStateOf(state.followsGeneralStart) }
    val shownOpacity = if (hasOpacity && follows) general else opacity
    ColorWheelSheet(
        initial,
        onPick = { state.applyColor(it, if (hasOpacity && !follows) opacity else null) },
        onClose = { state.colorTarget = null },
        modifier = modifier,
        previewOpacity = if (hasOpacity) shownOpacity else null,
        extra = if (hasOpacity) {
            {
                Text(
                    stringResource(R.string.panel_opacity_percent, (shownOpacity * 100f).roundToInt()),
                    color = colors.label,
                    fontSize = TextSize.S14,
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
                            fontSize = TextSize.S14,
                            modifier = Modifier.weight(1f),
                        )
                        PanelSwitch(follows, stringResource(R.string.panel_opacity_general, (general * 100f).roundToInt())) { follows = it }
                    }
                }
            }
        } else {
            null
        },
    )
}

/**
 * The app's one color wheel: the sheet that opens from the option that asked for a color, with the wheel, the brightness, the hex code of
 * the color, and Cancel and Done at opposite ends. [initial] is the color it opens on; [onPick] gets the color chosen, as an opaque ARGB.
 * Every place that asks for a color opens this, in the editor, the library and the settings, so they all look and work the same.
 * [extra] adds what only some need, such as how see-through a post-it's color is, and [previewOpacity] shows that in the preview dot.
 */
@Composable
internal fun ColorWheelSheet(
    initial: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    previewOpacity: Float? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalEditorColors.current
    val picker = remember(initial) { ColorPickerState(initial) }
    PanelSheet(stringResource(R.string.panel_color), onClose = onClose, modifier = modifier) {
        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.S8), contentAlignment = Alignment.Center) {
            HueSaturationWheel(picker, thumbColor = colors.title)
        }
        StepperSlider(
            fraction = picker.value,
            onChange = picker::pickValue,
            steps = 20,
            modifier = Modifier.fillMaxWidth(),
        )
        HexField(
            picker,
            textColor = colors.title,
            borderColor = colors.panelDivider,
            errorColor = colors.accent,
            modifier = Modifier.padding(top = Spacing.S8),
        )
        extra?.invoke(this)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(PanelPadding),
        ) {
            SheetButton(R.string.notebook_cancel, colors.title, colors.field, onClose)
            ColorDot(if (previewOpacity != null) NoteOptions.argb(picker.argb, previewOpacity) else picker.argb, diameter = 35.dp)
            SheetButton(R.string.panel_done, colors.accent, colors.accentContainer, onClick = { if (!picker.hexInvalid) onPick(picker.argb) })
        }
    }
}

@Composable
private fun SheetButton(label: Int, textColor: androidx.compose.ui.graphics.Color, fill: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(35.dp)
            .clip(AppShapes.Rounded18)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.S20),
    ) {
        Text(stringResource(label), color = textColor, fontSize = TextSize.S14, fontWeight = FontWeight.Medium)
    }
}

/** 5% to 100% in steps of 5%. */
internal const val OPACITY_STEPS = 19

/** The opacity at [fraction] of an opacity slider: from the least to full, in steps of 5%. */
internal fun opacityAt(fraction: Float): Float = (NoteOptions.MIN_OPACITY + fraction * (1f - NoteOptions.MIN_OPACITY)).let { (it * 20f).roundToInt() / 20f }
