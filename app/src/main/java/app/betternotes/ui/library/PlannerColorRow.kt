package app.betternotes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.planner.PlannerColors
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors

/**
 * The four colors a planner lets its owner change (cover, spine, stripe, page accent) as labelled swatches. Tapping one opens the swatch
 * grid; [colors] is always the four of them, and a color back at its default is not marked.
 */
@Composable
internal fun PlannerColorRow(colors: List<Int>, compact: Boolean, label: Color, border: Color, onChange: (List<Int>) -> Unit) {
    var picking by remember { mutableStateOf<Int?>(null) }
    picking?.let { index ->
        ColorSwatchDialog(
            colors[index],
            onPick = { onChange(colors.toMutableList().also { list -> list[index] = it }); picking = null },
            onReset = { onChange(colors.toMutableList().also { list -> list[index] = PlannerColors.DEFAULT[index] }); picking = null },
            onDismiss = { picking = null },
        )
    }
    val names = listOf(R.string.planner_color_cover, R.string.planner_color_spine, R.string.planner_color_stripe, R.string.planner_color_accent)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (index in 0 until PlannerColors.COUNT) {
            val changed = colors[index] != PlannerColors.DEFAULT[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = if (compact) 4.dp else 8.dp)) {
                Box(
                    Modifier
                        .size(if (compact) 24.dp else 34.dp)
                        .clip(AppShapes.Circle)
                        .background(Color(colors[index]))
                        .border(if (changed) 3.dp else 1.dp, if (changed) UiColors.SystemBlue else border, AppShapes.Circle)
                        .clickable { picking = index },
                )
                Spacer(Modifier.height(Spacing.S2))
                Text(stringResource(names[index]), color = label, fontSize = if (compact) TextSize.S10 else TextSize.S12)
            }
        }
    }
}
