package app.betternotes.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.canvas.LaserMode
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LetterSpacing
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.Swatches
import app.betternotes.ui.theme.TextSize
import kotlin.math.roundToInt


/** The laser's panel from the owner's screenshot: how it shows, how long its tail lasts, and its color. */
@Composable
internal fun LaserPanel(state: EditorState, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    PanelSheet(stringResource(R.string.tool_laser), onClose = { state.panelOpen = false }, modifier = modifier) {
        DottedPreview {
            val color = Color(state.laserColor)
            val tipAt = Offset(size.width * 0.86f, size.height * 0.45f)
            if (state.laserMode == LaserMode.DOT) {
                drawCircle(color.copy(alpha = 0.35f), 12.dp.toPx(), tipAt)
                drawCircle(color, 6.dp.toPx(), tipAt)
            } else {
                val tail = Path().apply {
                    moveTo(size.width * 0.44f, size.height * 0.12f)
                    cubicTo(
                        size.width * 0.56f, size.height * 0.6f,
                        size.width * 0.72f, size.height * 0.75f,
                        tipAt.x, tipAt.y,
                    )
                }
                drawPath(tail, color.copy(alpha = 0.35f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawPath(tail, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.S10),
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding, vertical = Spacing.S12),
        ) {
            ModeCard(R.string.laser_dot, 4.dp, 4.dp, state.laserMode == LaserMode.DOT) {
                state.selectLaserMode(LaserMode.DOT)
            }
            ModeCard(R.string.laser_line, 24.dp, 3.dp, state.laserMode == LaserMode.LINE) {
                state.selectLaserMode(LaserMode.LINE)
            }
            ModeCard(R.string.laser_delay, 24.dp, 5.dp, state.laserMode == LaserMode.DELAY) {
                state.selectLaserMode(LaserMode.DELAY)
            }
        }
        LabeledRow(R.string.laser_length) { ValuePill { ValueText(state.laserLength.toString()) } }
        val steps = EditorState.MAX_LASER_LENGTH - EditorState.MIN_LASER_LENGTH
        StepperSlider(
            fraction = (state.laserLength - EditorState.MIN_LASER_LENGTH).toFloat() / steps,
            onChange = { f -> state.selectLaserLength(EditorState.MIN_LASER_LENGTH + (f * steps).roundToInt()) },
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(49.dp).padding(start = PanelPadding, end = PanelPadding - Spacing.S6),
        ) {
            Text(
                stringResource(R.string.panel_color),
                color = colors.label,
                fontSize = TextSize.S14,
                fontWeight = FontWeight.Medium,
                letterSpacing = LetterSpacing.Label,
            )
            Spacer(Modifier.weight(1f))
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_add_swatch)) {
                state.colorTarget = ColorTarget.LASER
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding - Spacing.S8).padding(bottom = Spacing.S18),
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                for (color in Swatches.Laser) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).tappable(radius = 20.dp) { state.selectLaserColor(color) },
                    ) {
                        if (color == state.laserColor) Box(Modifier.size(35.dp).border(5.dp, colors.swatchRing, AppShapes.Circle))
                        ColorDot(color)
                    }
                }
            }
            Box(Modifier.padding(horizontal = Spacing.S4).size(2.dp, 28.dp).background(colors.panelDivider))
            Box(
                Modifier
                    .padding(horizontal = Spacing.S6)
                    .size(24.dp)
                    .clip(AppShapes.Circle)
                    .background(Brush.sweepGradient(Swatches.Hues))
                    .clickable(role = Role.Button) { state.colorTarget = ColorTarget.LASER },
            )
        }
    }
}

@Composable
private fun RowScope.ModeCard(
    @StringRes label: Int,
    glyphWidth: Dp,
    glyphHeight: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val shape = AppShapes.Rounded16
    val tint = if (selected) colors.accent else colors.title
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .height(67.dp)
            .clip(shape)
            .background(if (selected) colors.accentContainer else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.panelDivider, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.size(glyphWidth, glyphHeight).background(tint, AppShapes.Circle))
        Spacer(Modifier.height(Spacing.S10))
        Text(stringResource(label), color = tint, fontSize = TextSize.S13, fontWeight = FontWeight.Medium)
    }
}
