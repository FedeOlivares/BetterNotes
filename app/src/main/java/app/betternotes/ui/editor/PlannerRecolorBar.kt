package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.planner.PlannerPages
import app.betternotes.core.planner.RoleSlot
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/**
 * What can be done with generated elements of a planner page that the lasso picked: change the color of their text, lines, background or
 * shading, change their font, put them back, and choose whether the change goes to every element like them. They cannot be moved,
 * copied, deleted or typed into. Shown only while such elements are picked.
 */
@Composable
internal fun PlannerRecolorBar(state: EditorState, modifier: Modifier = Modifier) {
    if (state.plannerPicked.isEmpty() || state.viewOnly) return
    // Read so that the bar follows the page when it moves and shows what an edit or an undo left.
    @Suppress("UNUSED_VARIABLE") val moved = state.viewMoves + state.historyVersion
    val anchor = state.plannerAnchor() ?: return
    val slots = state.plannerSlots()
    Layout(
        modifier = modifier.fillMaxSize(),
        content = { PlannerRecolorContent(state, slots) },
    ) { measurables, constraints ->
        val bar = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (anchor[0] - bar.width).coerceIn(0f, (constraints.maxWidth - bar.width).coerceAtLeast(0).toFloat())
            val y = (anchor[1] + GAP_PX).coerceIn(0f, (constraints.maxHeight - bar.height).coerceAtLeast(0).toFloat())
            bar.place(x.roundToInt(), y.roundToInt())
        }
    }
}

@Composable
private fun PlannerRecolorContent(state: EditorState, slots: List<RoleSlot>) {
    val colors = LocalEditorColors.current
    // The few parts that are changed most stay on the bar; the rest unfolds from the sliders, as a pen's options do.
    val key = slots.filter { it in KEY_SLOTS }
    val more = slots.filter { it !in KEY_SLOTS }
    val hasMore = more.isNotEmpty()
    Column(Modifier.masksInk(state, "plannerRecolorBar")) {
        Pill {
            for (slot in key) SlotChip(state, slot)
            PillDivider()
            BarChip(stringResource(R.string.planner_style_similar_short), selected = state.plannerSimilar) { state.togglePlannerSimilar() }
            PillDivider()
            ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options), selected = state.plannerMoreOpen) {
                state.plannerMoreOpen = !state.plannerMoreOpen
            }
            BarChip(stringResource(R.string.planner_style_done), selected = false) { state.clearPlannerPick() }
        }
        if (state.plannerMoreOpen) {
            Pill(Modifier.padding(top = 6.dp)) {
                for (slot in more) if (slot != RoleSlot.FONT) SlotChip(state, slot)
                if (RoleSlot.FONT in more) {
                    BarChip(stringResource(R.string.planner_style_font), selected = state.plannerFontOpen) { state.plannerFontOpen = !state.plannerFontOpen }
                }
                if (hasMore) PillDivider()
                BarChip(stringResource(R.string.planner_style_reset), selected = false) { state.restylePlanner(PlannerPages.StyleChange(null)) }
            }
        }
        if (state.plannerMoreOpen && state.plannerFontOpen && RoleSlot.FONT in slots) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .width(FONT_PANEL_WIDTH)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.pill)
                    .border(1.dp, colors.pillBorder, RoundedCornerShape(16.dp)),
            ) {
                FontSelector(state.plannerFont().orEmpty()) { name -> state.restylePlanner(PlannerPages.StyleChange(RoleSlot.FONT, font = name)) }
            }
        }
    }
}

/** A part of the picked elements with the color it has now (an empty ring when it is as drawn); a tap opens the color wheel for it. */
@Composable
private fun SlotChip(state: EditorState, slot: RoleSlot) {
    val label = when (slot) {
        RoleSlot.TEXT -> R.string.planner_style_text
        RoleSlot.LINE -> R.string.planner_style_lines
        RoleSlot.BACKGROUND -> R.string.planner_style_background
        else -> R.string.planner_style_shading
    }
    val selected = state.plannerSlot == slot && state.colorTarget == ColorTarget.PLANNER_ELEMENT
    Row(verticalAlignment = Alignment.CenterVertically) {
        BarChip(stringResource(label), selected = selected) { state.pickPlannerColor(slot) }
        ColorDot(state.plannerColorOf(slot) ?: 0, diameter = 16.dp)
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun BarChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Text(
        label,
        color = if (selected) colors.accent else colors.title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** The parts on the bar itself; the others are under the sliders. */
private val KEY_SLOTS = setOf(RoleSlot.TEXT, RoleSlot.LINE, RoleSlot.BACKGROUND)

private const val GAP_PX = 12f
private val FONT_PANEL_WIDTH = 280.dp
