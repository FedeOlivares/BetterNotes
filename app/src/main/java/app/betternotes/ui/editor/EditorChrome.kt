package app.betternotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.model.LineKind
import app.betternotes.core.tools.InkPreset
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/** What the toolbars ask of whoever hosts the editor. */
internal class EditorActions(
    val onBack: () -> Unit,
    val onZoomToRealSize: () -> Unit,
    val onToggleTheme: () -> Unit,
    /** Stand-ins until the library opens documents: not part of the design. */
    val onToggleScratchKind: () -> Unit,
    val onTogglePerfReadout: (() -> Unit)?,
)

private const val LEFT = "left"
private const val CENTER = "center"
private const val RIGHT = "right"
private const val TOOL_ROW = "row"
private val EdgeMargin = 6.dp
private val PillGap = 10.dp
private val PanelEndMargin = 8.dp
private val PanelGap = 7.dp
private const val SAVE_DELAY_MS = 400L

/**
 * The floating controls over the canvas, laid out from the owner's Notewise screenshots: the left,
 * center and right pills in a row, and the active tool's row centered under the center pill.
 */
@Composable
internal fun EditorChrome(state: EditorState, actions: EditorActions) {
    val notices = rememberNotices()
    val preset = state.preset

    LaunchedEffect(preset) {
        delay(SAVE_DELAY_MS)
        state.saveTools()
    }

    Box(Modifier.fillMaxSize()) {
        Layout(
            content = {
                LeftPill(state, actions, notices, Modifier.layoutId(LEFT).masksInk(state, LEFT))
                if (!state.viewOnly) {
                    CenterPill(state, notices, Modifier.layoutId(CENTER).masksInk(state, CENTER))
                }
                RightPill(state, actions, Modifier.layoutId(RIGHT).masksInk(state, RIGHT))
                if (!state.viewOnly && !state.toolRowCollapsed && preset != null) {
                    ToolRow(state, preset, Modifier.layoutId(TOOL_ROW).masksInk(state, TOOL_ROW))
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = EdgeMargin.roundToPx()
            val gap = PillGap.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            fun slot(id: String) = measurables.firstOrNull { it.layoutId == id }
            val left = slot(LEFT)!!.measure(loose)
            val right = slot(RIGHT)!!.measure(loose)
            val centerMax = (constraints.maxWidth - left.width - right.width - 2 * margin - 2 * gap).coerceAtLeast(0)
            val center = slot(CENTER)?.measure(Constraints(maxWidth = centerMax, maxHeight = constraints.maxHeight))
            val centerStart = center?.let {
                // In the middle of the screen when there is room, and otherwise between its neighbors.
                ((constraints.maxWidth - it.width) / 2).coerceIn(
                    left.width + margin + gap,
                    (constraints.maxWidth - right.width - margin - gap - it.width).coerceAtLeast(left.width + margin + gap),
                )
            } ?: 0
            val row = slot(TOOL_ROW)?.measure(Constraints(maxWidth = constraints.maxWidth - 2 * margin, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                left.place(margin, margin)
                right.place(constraints.maxWidth - right.width - margin, margin)
                center?.place(centerStart, margin)
                if (row != null) {
                    val rowCenter = if (center != null) centerStart + center.width / 2 else constraints.maxWidth / 2
                    val rowStart = (rowCenter - row.width / 2).coerceIn(margin, (constraints.maxWidth - row.width - margin).coerceAtLeast(margin))
                    row.place(rowStart, margin + left.height + gap)
                }
            }
        }

        val panelTop = EdgeMargin + PillHeight + PillGap + PillHeight + PanelGap
        val panelModifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = panelTop, end = PanelEndMargin, bottom = PanelEndMargin)
        if (preset != null && state.colorTarget != null) {
            ColorWheelPanel(state, preset, panelModifier.masksInk(state, "wheel"))
        } else if (preset != null && state.panelOpen) {
            InkPanel(state, preset, notices, panelModifier.masksInk(state, "panel"))
        }
    }
}

@Composable
private fun LeftPill(state: EditorState, actions: EditorActions, notices: Notices, modifier: Modifier) {
    Pill(modifier) {
        ToolbarIcon(EditorIcons.Back, stringResource(R.string.editor_back), onClick = actions.onBack)
        ToolbarIcon(EditorIcons.Sidebar, stringResource(R.string.editor_sidebar), onClick = notices::notBuilt)
        ToolbarIcon(EditorIcons.PageGrid, stringResource(R.string.editor_page_grid), onClick = notices::notBuilt)
        ToolbarIcon(EditorIcons.PageStack, stringResource(R.string.editor_page_stack), onClick = notices::notBuilt)
        ToolbarIcon(EditorIcons.ChevronsUpDown, stringResource(R.string.editor_chevrons), onClick = notices::notBuilt)
    }
}

/** One button in the strip that scrolls. A tool without a reference screenshot only says so for now. */
private class StripItem(val icon: ImageVector, val label: Int, val tool: ActiveTool? = null)

private val StripItems by lazy {
    listOf(
        StripItem(EditorIcons.Pen, R.string.tool_pen, ActiveTool.PEN),
        StripItem(EditorIcons.Pencil, R.string.tool_pencil, ActiveTool.PENCIL),
        StripItem(EditorIcons.Highlighter, R.string.tool_highlighter, ActiveTool.HIGHLIGHTER),
        StripItem(EditorIcons.Eraser, R.string.tool_eraser, ActiveTool.ERASER),
        StripItem(EditorIcons.Lasso, R.string.tool_lasso),
        StripItem(EditorIcons.Shapes, R.string.tool_shapes),
        StripItem(EditorIcons.Image, R.string.tool_image),
        StripItem(EditorIcons.Camera, R.string.tool_camera),
        StripItem(EditorIcons.Table, R.string.tool_table),
        StripItem(EditorIcons.Tray, R.string.tool_clipboard),
        StripItem(EditorIcons.StickyAdd, R.string.tool_sticky),
        StripItem(EditorIcons.Loupe, R.string.tool_loupe),
        StripItem(EditorIcons.Ruler, R.string.tool_ruler),
        StripItem(EditorIcons.Mic, R.string.tool_recorder),
        StripItem(EditorIcons.Laser, R.string.tool_laser),
        StripItem(EditorIcons.Settings, R.string.tool_settings),
    )
}

@Composable
private fun CenterPill(state: EditorState, notices: Notices, modifier: Modifier) {
    Pill(modifier) {
        ToolbarIcon(EditorIcons.Undo, stringResource(R.string.editor_undo), enabled = state.canUndo, onClick = state::undo)
        ToolbarIcon(EditorIcons.Redo, stringResource(R.string.editor_redo), enabled = state.canRedo, onClick = state::redo)
        ToolbarIcon(
            EditorIcons.FingerMode,
            stringResource(R.string.editor_finger_mode),
            selected = state.fingerDraws,
            onClick = state::toggleFingerDraw,
        )
        PillDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState()),
        ) {
            for (item in StripItems) {
                val tool = item.tool
                ToolbarIcon(
                    item.icon,
                    stringResource(item.label),
                    selected = tool != null && tool == state.activeTool,
                    onClick = { if (tool != null) state.select(tool) else notices.notBuilt() },
                )
            }
        }
        PillDivider()
        ToolbarIcon(
            if (state.toolRowCollapsed) EditorIcons.ChevronDown else EditorIcons.ChevronUp,
            stringResource(R.string.editor_collapse),
            onClick = { state.toolRowCollapsed = !state.toolRowCollapsed },
        )
    }
}

@Composable
private fun RightPill(state: EditorState, actions: EditorActions, modifier: Modifier) {
    val colors = LocalEditorColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Pill(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (state.viewOnly) colors.accentContainer else colors.raisedButton)
                .tappable(radius = 21.dp, onClick = state::toggleViewOnly),
        ) {
            Icon(
                EditorIcons.Eye,
                stringResource(R.string.editor_view_only),
                tint = if (state.viewOnly) colors.accent else colors.icon,
                modifier = Modifier.size(24.dp),
            )
        }
        Box {
            ToolbarIcon(EditorIcons.More, stringResource(R.string.editor_more), onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuEntry(R.string.menu_real_size) {
                    menuOpen = false
                    actions.onZoomToRealSize()
                }
                MenuEntry(R.string.menu_theme) {
                    menuOpen = false
                    actions.onToggleTheme()
                }
                MenuEntry(R.string.menu_scratch_kind) {
                    menuOpen = false
                    actions.onToggleScratchKind()
                }
                actions.onTogglePerfReadout?.let { toggle ->
                    MenuEntry(R.string.menu_perf) {
                        menuOpen = false
                        toggle()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label), fontSize = 14.sp) }, onClick = onClick)
}

/** The second row: line type, three widths, the colors, and the way into the panel. */
@Composable
private fun ToolRow(state: EditorState, preset: InkPreset, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Pill(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ToolbarCellBox(onClick = { state.update { it.copy(line = it.line.next()) } }) {
                LineTypeGlyph(preset.line, colors.icon)
            }
            PillDivider()
            preset.widths.forEachIndexed { slot, thickness ->
                ToolbarCellBox(onClick = { state.update { it.copy(selectedWidth = slot) } }) {
                    WidthGlyph(thickness)
                    if (slot == preset.selectedWidth) SelectedDot()
                }
            }
            PillDivider()
            preset.colors.forEachIndexed { slot, color ->
                ToolbarCellBox(onClick = { state.update { it.copy(selectedColor = slot) } }) {
                    ColorDot(color)
                    if (slot == preset.selectedColor) SelectedDot()
                }
            }
            ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) {
                state.colorTarget = ColorTarget.CUSTOM_SLOT
            }
            ToolbarIcon(EditorIcons.EditColor, stringResource(R.string.editor_edit_color)) {
                state.colorTarget = ColorTarget.SELECTED_SLOT
            }
            PillDivider()
            ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
                state.panelOpen = !state.panelOpen
            }
        }
    }
}

internal fun LineKind.next() = when (this) {
    LineKind.SOLID -> LineKind.DASHED
    LineKind.DASHED -> LineKind.DOTTED
    LineKind.DOTTED -> LineKind.SOLID
}

/** A short bar as thick as the width slot, scaled the way the screenshot's bars are. */
@Composable
private fun WidthGlyph(thickness: Int) {
    val tint = LocalEditorColors.current.icon
    Canvas(Modifier.size(26.dp, 12.dp)) {
        val stroke = (thickness * WIDTH_GLYPH_DP_PER_UNIT).dp.toPx().coerceIn(1.dp.toPx(), size.height)
        val inset = stroke / 2f
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(inset, size.height / 2f),
            androidx.compose.ui.geometry.Offset(size.width - inset, size.height / 2f),
            stroke,
            StrokeCap.Round,
        )
    }
}

private const val WIDTH_GLYPH_DP_PER_UNIT = 0.27f
