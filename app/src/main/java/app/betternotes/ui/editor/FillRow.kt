package app.betternotes.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.ui.icons.EditorIcons

/** The paint bucket's row: its color bar as the pen's is kept, a way to sample a color from the page, and how see-through the fill is. */
@Composable
internal fun FillRow(state: EditorState, modifier: Modifier) {
    Pill(modifier) {
        val draft = state.fillDraft
        if (draft != null) {
            ColorEditor(
                draft,
                setDraft = { state.fillDraft = it },
                accept = state::acceptFillDraft,
                discard = { state.fillDraft = null },
            )
        } else {
            for (color in state.fillColors) {
                ToolbarCellBox(onClick = { state.chooseFillColor(color) }) {
                    ColorDot(color)
                    if (color == state.fillColor) SelectedDot()
                }
            }
            Box {
                ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.moreColorsOpen = true }
                if (state.moreColorsOpen) {
                    FavoriteColors(
                        favorites = (state.fillColors + MORE_FAVORITES).distinct(),
                        onBar = state.fillColors,
                        onPick = state::addFillColor,
                        onWheel = { state.colorTarget = ColorTarget.FILL },
                        onDismiss = { state.moreColorsOpen = false },
                    )
                }
            }
            ToolbarIcon(EditorIcons.EditColor, stringResource(R.string.editor_edit_color)) { state.fillDraft = state.fillColors }
        }
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) { state.panelOpen = !state.panelOpen }
    }
}

/** The paint bucket's settings: whether it hands the tool back, and after how long. */
@Composable
internal fun FillPanel(state: EditorState, modifier: Modifier) {
    val colors = LocalEditorColors.current
    PanelSheet(stringResource(R.string.tool_fill), onClose = { state.panelOpen = false }, modifier = modifier) {
        // How see-through the fill is, and the way to take a color from the page.
        LabeledRow(R.string.fill_opacity) { ValuePill { ValueText("${(state.fillOpacity * 100).roundToInt()}%") } }
        StepperSlider(
            fraction = (state.fillOpacity - EditorState.MIN_FILL_OPACITY) / (1f - EditorState.MIN_FILL_OPACITY),
            onChange = { f -> state.chooseFillOpacity(EditorState.MIN_FILL_OPACITY + f * (1f - EditorState.MIN_FILL_OPACITY)) },
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        FillPatternSection(state)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(49.dp)
                .clickable(role = Role.Button) {
                    state.panelOpen = false
                    state.startPickingColor()
                }
                .padding(horizontal = PanelPadding),
        ) {
            Text(stringResource(R.string.fill_pick_color), color = colors.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(EditorIcons.Eyedropper, null, tint = if (state.pickingColor) colors.accent else colors.label, modifier = Modifier.size(22.dp))
        }
        ReturnRows(state.fillReturn, state.fillReturnDelay, state::chooseFillReturn, state::chooseFillReturnDelay)
    }
}

/** The switch that hands the tool in use before back, and the folded-away delay before it does. */
@Composable
internal fun ReturnRows(on: Boolean, delayTenths: Int, onChoose: (Boolean) -> Unit, onChooseDelay: (Int) -> Unit) {
    val colors = LocalEditorColors.current
    var delayOpen by remember { mutableStateOf(false) }
    LabeledRow(R.string.fill_return) {
        PanelSwitch(on, stringResource(R.string.fill_return), onChoose)
    }
    if (!on) return
    // The delay stays folded away until it is wanted.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp)
            .clickable(role = Role.Button) { delayOpen = !delayOpen }
            .padding(horizontal = PanelPadding),
    ) {
        Text(stringResource(R.string.fill_return_delay), color = colors.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("%.1f s".format(delayTenths / 10f), color = colors.label, fontSize = 15.sp)
        Icon(if (delayOpen) EditorIcons.ChevronUp else EditorIcons.ChevronDown, null, tint = colors.label, modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
    if (delayOpen) {
        StepperSlider(
            fraction = (delayTenths - EditorState.MIN_FILL_RETURN_TENTHS).toFloat() / (EditorState.MAX_FILL_RETURN_TENTHS - EditorState.MIN_FILL_RETURN_TENTHS),
            onChange = { f ->
                onChooseDelay(EditorState.MIN_FILL_RETURN_TENTHS + (f * (EditorState.MAX_FILL_RETURN_TENTHS - EditorState.MIN_FILL_RETURN_TENTHS)).roundToInt())
            },
            steps = EditorState.MAX_FILL_RETURN_TENTHS - EditorState.MIN_FILL_RETURN_TENTHS,
        )
    }
}

/** The same two rows for the highlighter, eraser, lasso, text and sticker tools, each with a switch of its own. */
@Composable
internal fun ToolReturnRows(state: EditorState, tool: ActiveTool) {
    val choice = state.toolReturn(tool)
    ReturnRows(choice.on, choice.tenths, { state.chooseToolReturn(tool, it) }, { state.chooseToolReturnDelay(tool, it) })
}

/** The same two rows for the tools that put an item on the page: shapes, notes, tables and pictures. */
@Composable
internal fun CreateReturnRows(state: EditorState) =
    ReturnRows(state.createReturn, state.createReturnDelay, state::chooseCreateReturn, state::chooseCreateReturnDelay)
