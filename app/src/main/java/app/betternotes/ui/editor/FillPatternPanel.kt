package app.betternotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.FillPatternKind
import app.betternotes.core.tools.PatternMarks
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/**
 * The paint bucket's pattern, in its options: which marks go over the color (none, dots, lines, diagonal lines, crosshatch), in what color,
 * and how far apart and how heavy they are. The previews are drawn by the same generator as the page.
 */
@Composable
internal fun FillPatternSection(state: EditorState) {
    val pattern = state.fillPattern
    LabeledRow(R.string.fill_pattern) {
        PanelSwitch(state.fillPatternOn, stringResource(R.string.fill_pattern), state::chooseFillPatternOn)
    }
    if (!state.fillPatternOn) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding),
    ) {
        for (kind in FillPatternKind.entries.filter { it != FillPatternKind.SOLID }) {
            PatternCell(kind, pattern, state.fillColor, Modifier.weight(1f)) { state.updateFillPattern { it.copy(kind = kind) } }
        }
    }
    LabeledRow(R.string.fill_pattern_color) {}
    PatternColors(state)
    // Density is the spacing the other way round: the closer the marks, the denser the pattern, so the slider's right end is the densest.
    val density = 1f - (pattern.spacing - FillPattern.MIN_SPACING) / (FillPattern.MAX_SPACING - FillPattern.MIN_SPACING)
    LabeledRow(R.string.fill_pattern_density) { ValuePill { ValueText("${(density * 100).roundToInt()}%") } }
    StepperSlider(
        fraction = density,
        onChange = { f -> state.updateFillPattern { it.copy(spacing = (FillPattern.MAX_SPACING - f * (FillPattern.MAX_SPACING - FillPattern.MIN_SPACING)).roundToHalf()) } },
        steps = 21,
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledRow(R.string.fill_pattern_weight) { ValuePill { ValueText("${(pattern.weight / FillPattern.MAX_WEIGHT * 100).roundToInt()}%") } }
    StepperSlider(
        fraction = (pattern.weight - FillPattern.MIN_WEIGHT) / (FillPattern.MAX_WEIGHT - FillPattern.MIN_WEIGHT),
        onChange = { f -> state.updateFillPattern { it.copy(weight = FillPattern.MIN_WEIGHT + f * (FillPattern.MAX_WEIGHT - FillPattern.MIN_WEIGHT)) } },
        steps = 13,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Float.roundToHalf() = (this * 2f).roundToInt() / 2f

/** Black, white and the colors of the bucket's bar, and the wheel for any other. */
@Composable
private fun PatternColors(state: EditorState) {
    val colors = (listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) + state.fillColors).distinct()
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding, vertical = 4.dp),
    ) {
        for (color in colors.take(MAX_PATTERN_COLORS)) {
            ToolbarCellBox(onClick = { state.updateFillPattern { it.copy(color = color) } }) {
                ColorDot(color)
                if (color == state.fillPattern.color) SelectedDot()
            }
        }
        ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = ColorTarget.FILL_PATTERN }
    }
}

private const val MAX_PATTERN_COLORS = 7

/** One choice of pattern: a swatch of the bucket's color with the marks on it, drawn small. */
@Composable
private fun PatternCell(kind: FillPatternKind, current: FillPattern, base: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    val selected = kind == current.kind
    val shape = RoundedCornerShape(8.dp)
    val marks = remember { PatternMarks() }
    val label = stringResource(patternName(kind))
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(Color(base))
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.swatchEdge, shape)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (kind == FillPatternKind.SOLID) return@Box
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            // The preview is at the pattern's own spacing and weight, one point to a pixel's worth of the density, so it shows what the page will.
            val scale = density
            val preview = current.copy(kind = kind)
            marks.make(preview, 0f, 0f, size.width / scale, size.height / scale)
            val tint = Color(current.color)
            val stroke = marks.thickness * scale
            for (i in 0 until marks.dotCount step 2) {
                drawCircle(tint, stroke / 2f, Offset(marks.dots[i] * scale, marks.dots[i + 1] * scale))
            }
            for (i in 0 until marks.lineCount step 4) {
                drawLine(
                    tint,
                    Offset(marks.lines[i] * scale, marks.lines[i + 1] * scale),
                    Offset(marks.lines[i + 2] * scale, marks.lines[i + 3] * scale),
                    stroke,
                    StrokeCap.Butt,
                )
            }
        }
    }
}

private fun patternName(kind: FillPatternKind) = when (kind) {
    FillPatternKind.SOLID -> R.string.fill_pattern_solid
    FillPatternKind.DOTS -> R.string.fill_pattern_dots
    FillPatternKind.LINES -> R.string.fill_pattern_lines
    FillPatternKind.DIAGONAL -> R.string.fill_pattern_diagonal
    FillPatternKind.CROSSHATCH -> R.string.fill_pattern_crosshatch
}
