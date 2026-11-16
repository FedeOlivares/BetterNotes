package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.edit.Grouping
import app.betternotes.core.edit.ZPlace
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.tools.NoteOptions
import app.betternotes.fonts.FontLibrary
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OPAQUE_ALPHA
import app.betternotes.ui.theme.SelectionToolbarStyle as Style
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import kotlin.math.roundToInt

/** The least opacity a picture can be given, so it never vanishes. */
private const val MIN_IMAGE_OPACITY = 0.1f
private const val MIN_LINE_WIDTH = 0.5f
private const val MAX_LINE_WIDTH = 24f
private const val LINE_WIDTH_STEP = 0.5f
private const val MIN_TEXT_SIZE = 6
private const val MAX_TEXT_SIZE = 200
private const val NOTE_COLOR_COLUMNS = 4

/** What the selection is made of, which decides what the toolbar offers. */
private enum class SelectionKind { LINES, TEXT, EDITING, NOTES, OTHER }

private fun kindOf(items: List<PageItem>): SelectionKind = when {
    items.all { it is StrokeItem || (it is ShapeItem && it.kind != ShapeKind.TAPE) } -> SelectionKind.LINES
    items.size == 1 && items.first() is TextItem -> SelectionKind.TEXT
    items.all { it is StickyNoteItem } -> SelectionKind.NOTES
    else -> SelectionKind.OTHER
}

/**
 * What can be done with what the lasso has selected, in a bar that floats under the selection (over it when there is no room below):
 *  - lines and shapes: color, line type, position, thickness, copy, sticker, delete;
 *  - a text box: color, font, size, position, copy, delete;
 *  - post-its: color, opacity, keep open, position, copy, delete;
 *  - anything else: position, copy, sticker, delete, and the options of a table or picture.
 * The "..." button opens a list with lock, group, mirror, cut and the rest. How it looks comes from [Style]; what each button does is in [EditorState].
 */
@Composable
internal fun SelectionToolbar(state: EditorState, modifier: Modifier = Modifier) {
    val selected = state.selection.orEmpty()
    // A text box open for typing is not a selection, but its look is changed by the same kind of bar, for its selected letters or all of it.
    val editing = state.textEdit?.takeIf { selected.isEmpty() && state.activeTool == ActiveTool.TEXT }
    val shown = !state.viewOnly && (selected.isNotEmpty() || editing != null)
    androidx.compose.runtime.LaunchedEffect(shown) { if (!shown) state.selectionPopup = null }
    if (!shown) return
    // Read so that the bar follows the selection when the page moves.
    @Suppress("UNUSED_VARIABLE") val moved = state.viewMoves + state.historyVersion
    val items = selected
    val box = if (editing != null) floatArrayOf(editing.left, editing.top, editing.left + editing.width, editing.top + editing.height) else state.selectionScreenBounds() ?: return
    val density = LocalDensity.current
    val gap = with(density) { Style.gap.roundToPx() }
    val margin = with(density) { Style.screenMargin.roundToPx() }
    val kind = if (editing != null) SelectionKind.EDITING else kindOf(items)
    val popup = state.selectionPopup
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(Modifier.masksInk(state, "selectionToolbar")) { ToolbarContent(state, items, kind) }
            if (popup != null) Box(Modifier.masksInk(state, "selectionPopup")) { PopupContent(state, items, kind, popup) }
        },
    ) { measurables, constraints ->
        val maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxWidth)
        val bar = measurables[0].measure(loose)
        val menu = measurables.getOrNull(1)?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val maxX = (constraints.maxWidth - margin - bar.width).coerceAtLeast(margin)
            val x = ((box[0] + box[2]) / 2f - bar.width / 2f).roundToInt().coerceIn(margin, maxX)
            val above = box[1].roundToInt() - gap - bar.height
            val below = box[3].roundToInt() + gap
            val roomBelow = constraints.maxHeight - margin - bar.height
            // Under the selection when it fits there, else over it, else at the top of the screen.
            val y = when {
                below <= roomBelow -> below
                above >= margin -> above
                else -> margin
            }
            bar.place(x, y)
            if (menu != null) {
                val menuX = if (popup == SelectionPopup.MORE) (x + bar.width - menu.width).coerceAtLeast(margin) else x
                val under = y + bar.height + gap / 2
                val menuY = if (under + menu.height <= constraints.maxHeight - margin) under else (y - gap / 2 - menu.height).coerceAtLeast(margin)
                menu.place(menuX.coerceAtMost((constraints.maxWidth - margin - menu.width).coerceAtLeast(margin)), menuY)
            }
        }
    }
}

@Composable
private fun ToolbarContent(state: EditorState, items: List<PageItem>, kind: SelectionKind) {
    Pill {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            when (kind) {
                SelectionKind.LINES -> {
                    LineColorButton(state)
                    PopupIcon(state, SelectionPopup.LINE_TYPE, EditorIcons.LineNever, R.string.selection_line_type)
                    ArrangeButton(state)
                    PopupIcon(state, SelectionPopup.THICKNESS, EditorIcons.Thickness, R.string.selection_thickness)
                    PillDivider()
                    CopyButton(state)
                    StickerButton(state)
                }
                SelectionKind.TEXT -> {
                    val text = items.first() as TextItem
                    ToolbarCellBox(onClick = { state.colorTarget = ColorTarget.SELECTED_TEXT }) { ColorDot(text.color, diameter = 26.dp) }
                    PopupIcon(state, SelectionPopup.FONT, EditorIcons.Text, R.string.selection_font)
                    PopupIcon(state, SelectionPopup.SIZE, EditorIcons.TextSize, R.string.selection_font_size)
                    ArrangeButton(state)
                    PillDivider()
                    CopyButton(state)
                }
                SelectionKind.EDITING -> {
                    ToolbarCellBox(onClick = { state.colorTarget = ColorTarget.TEXT_COLOR }) { ColorDot(state.shownTextColor(state.note), diameter = 26.dp) }
                    PopupIcon(state, SelectionPopup.FONT, EditorIcons.Text, R.string.selection_font)
                    PopupIcon(state, SelectionPopup.SIZE, EditorIcons.TextSize, R.string.selection_font_size)
                }
                SelectionKind.NOTES -> {
                    val notes = items.filterIsInstance<StickyNoteItem>()
                    // Only the color in use shows; touching it opens the palette, and the next button opens the color wheel.
                    ToolbarCellBox(onClick = { state.toggleSelectionPopup(SelectionPopup.COLORS) }) {
                        ColorDot(notes.first().color or OPAQUE_ALPHA, diameter = 26.dp)
                    }
                    ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = ColorTarget.SELECTED_NOTE }
                    PopupIcon(state, SelectionPopup.OPACITY, EditorIcons.Translucent, R.string.selection_opacity)
                    val keep = notes.all { it.keepOpen }
                    ToolbarIcon(EditorIcons.StickyNote, stringResource(R.string.selection_keep_open), selected = keep) { state.setSelectedNotesKeepOpen(!keep) }
                    ArrangeButton(state)
                    PillDivider()
                    CopyButton(state)
                }
                SelectionKind.OTHER -> {
                    OtherOptions(state, items)
                    ArrangeButton(state)
                    PillDivider()
                    CopyButton(state)
                    StickerButton(state)
                }
            }
            if (kind != SelectionKind.EDITING) DeleteButton(state)
            if (kind != SelectionKind.EDITING) ToolbarIcon(
                EditorIcons.More,
                stringResource(R.string.selection_more),
                selected = state.selectionPopup == SelectionPopup.MORE,
            ) { state.toggleSelectionPopup(SelectionPopup.MORE) }
        }
    }
}

/** What only a picture or a table has: how see-through the picture is, and the table's line, fill and header colors. */
@Composable
private fun OtherOptions(state: EditorState, items: List<PageItem>) {
    val tables = items.filterIsInstance<TableItem>()
    if (items.any { it is ImageItem }) PopupIcon(state, SelectionPopup.OPACITY, EditorIcons.Translucent, R.string.selection_translucent)
    if (tables.isNotEmpty()) {
        // Line, fill and header: a touch opens the color wheel, and a set fill or header can be taken off with the cross beside it.
        TableColorButton(R.string.selection_table_line, tables.first().lineColor, set = true, onClear = null) { state.colorTarget = ColorTarget.SELECTED_TABLE_LINE }
        val filled = tables.any { it.fillColor ushr 24 != 0 }
        TableColorButton(R.string.selection_table_fill, tables.first().fillColor, set = filled, onClear = { state.recolorSelectedTables { it.copy(fillColor = 0) } }) { state.colorTarget = ColorTarget.SELECTED_TABLE_FILL }
        val headed = tables.any { it.headerColor != 0 }
        TableColorButton(R.string.selection_table_header, tables.first().headerColor, set = headed, onClear = { state.recolorSelectedTables { it.copy(headerColor = 0) } }) { state.colorTarget = ColorTarget.SELECTED_TABLE_HEADER }
        PillDivider()
    }
}

@Composable
private fun LineColorButton(state: EditorState) {
    val color = state.selectedLineColor() ?: return
    ToolbarCellBox(onClick = { state.colorTarget = ColorTarget.SELECTED_LINE }) { ColorDot(color, diameter = 26.dp) }
}

@Composable
private fun PopupIcon(state: EditorState, popup: SelectionPopup, icon: ImageVector, label: Int) {
    ToolbarIcon(icon, stringResource(label), selected = state.selectionPopup == popup) { state.toggleSelectionPopup(popup) }
}

/** The submenu of positions: it opens a row of front, middle and back. */
@Composable
private fun ArrangeButton(state: EditorState) = PopupIcon(state, SelectionPopup.ARRANGE, EditorIcons.Layers, R.string.selection_arrange)

@Composable
private fun CopyButton(state: EditorState) =
    ToolbarIcon(EditorIcons.Copy, stringResource(R.string.selection_copy)) { state.duplicateSelection() }

@Composable
private fun StickerButton(state: EditorState) {
    if (state.selectionCanBeSticker) ToolbarIcon(EditorIcons.Sticker, stringResource(R.string.selection_sticker)) { state.saveSelectionAsSticker() }
}

@Composable
private fun DeleteButton(state: EditorState) =
    ToolbarIcon(EditorIcons.Trash, stringResource(R.string.selection_delete), tintOverride = Style.trashTint) { state.deleteSelection() }

/** The pop-up under the toolbar: the one the toolbar's buttons or its "..." button opened. */
@Composable
private fun PopupContent(state: EditorState, items: List<PageItem>, kind: SelectionKind, popup: SelectionPopup) {
    PopupFrame(wide = popup == SelectionPopup.MORE || popup == SelectionPopup.FONT) {
        when (popup) {
            SelectionPopup.ARRANGE -> ArrangeRow(state)
            SelectionPopup.MORE -> MoreList(state, items, kind)
            SelectionPopup.LINE_TYPE -> LineTypeRow(state)
            SelectionPopup.THICKNESS -> LineWidthRow(state)
            SelectionPopup.FONT ->
                if (kind == SelectionKind.EDITING) FontList(state.shownFont(state.note)) { state.setFont(it) }
                else (items.firstOrNull() as? TextItem)?.let { text -> FontList(text.style.font) { state.setSelectedTextFont(it) } }
            SelectionPopup.SIZE ->
                if (kind == SelectionKind.EDITING) TextSizeRow(state.shownTextSize(state.note)) { state.setTextSize(it) }
                else (items.firstOrNull() as? TextItem)?.let { text -> TextSizeRow(text.textSize.roundToInt()) { state.setSelectedTextSize(it) } }
            SelectionPopup.COLORS -> NoteColors(state, items.filterIsInstance<StickyNoteItem>())
            SelectionPopup.OPACITY -> OpacityRow(state, items)
        }
    }
}

@Composable
private fun PopupFrame(wide: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier
            .then(if (wide) Modifier.width(Style.popupWidth) else Modifier)
            .shadow(Style.popupElevation, Style.popupShape, clip = false)
            .clip(Style.popupShape)
            .background(Style.popupColor())
            .border(1.dp, Style.popupBorder(), Style.popupShape)
            .padding(Style.popupPadding),
    ) { content() }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Style.roundButton)
            .clip(AppShapes.Circle)
            .background(Style.roundButtonColor())
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) { Icon(icon, label, tint = Style.roundButtonIcon(), modifier = Modifier.size(24.dp)) }
}

/** Front, middle and back: the solid square is the top, the middle and the bottom one, in that order. */
@Composable
private fun ArrangeRow(state: EditorState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Style.roundButtonGap)) {
        RoundButton(EditorIcons.BringToFront, stringResource(R.string.selection_bring_front)) { state.arrangeSelected(ZPlace.FRONT) }
        RoundButton(EditorIcons.MoveToMiddle, stringResource(R.string.selection_move_middle)) { state.arrangeSelected(ZPlace.MIDDLE) }
        RoundButton(EditorIcons.SendToBack, stringResource(R.string.selection_send_back)) { state.arrangeSelected(ZPlace.BACK) }
    }
}

@Composable
private fun LineTypeRow(state: EditorState) {
    val current = state.selectedLineKind()
    val colors = LocalEditorColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(Style.roundButtonGap)) {
        for (kind in LineKind.entries) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(Style.roundButton)
                    .clip(AppShapes.Circle)
                    .background(if (kind == current) Style.roundButtonColor() else colors.raisedButton)
                    .clickable(role = Role.Button) { state.setSelectedLineKind(kind) },
            ) { LineTypeGlyph(kind, if (kind == current) Style.roundButtonIcon() else colors.icon, Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun LineWidthRow(state: EditorState) {
    val width = state.selectedLineWidth() ?: return
    StepperRow(
        text = "%.1f".format(width),
        onMinus = { state.setSelectedLineWidth((width - LINE_WIDTH_STEP).coerceAtLeast(MIN_LINE_WIDTH)) },
        onPlus = { state.setSelectedLineWidth((width + LINE_WIDTH_STEP).coerceAtMost(MAX_LINE_WIDTH)) },
    )
}

@Composable
private fun TextSizeRow(size: Int, onSize: (Int) -> Unit) {
    StepperRow(
        text = "$size",
        onMinus = { onSize((size - 1).coerceAtLeast(MIN_TEXT_SIZE)) },
        onPlus = { onSize((size + 1).coerceAtMost(MAX_TEXT_SIZE)) },
    )
}

/** The post-it colors to pick from, in columns, with the color wheel's button after them. */
@Composable
private fun NoteColors(state: EditorState, notes: List<StickyNoteItem>) {
    val palette = NoteOptions.STICKY_PALETTE
    Column(verticalArrangement = Arrangement.spacedBy(Style.roundButtonGap)) {
        for (row in palette.chunked(NOTE_COLOR_COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Style.roundButtonGap)) {
                for (color in row) {
                    val chosen = notes.all { (it.color and NoteOptions.RGB_MASK) == (color and NoteOptions.RGB_MASK) }
                    Box(Modifier.size(Style.roundButton).clickable(role = Role.Button) { state.pickSelectedNotesColor(color) }, contentAlignment = Alignment.Center) {
                        ColorDot(color, diameter = 34.dp)
                        if (chosen) SelectedDot()
                    }
                }
            }
        }
        MoreRow(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = ColorTarget.SELECTED_NOTE }
    }
}

@Composable
private fun StepperRow(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Style.roundButtonGap)) {
        RoundButton(EditorIcons.Minus, stringResource(R.string.panel_decrease), onClick = onMinus)
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { ValueText(text) }
        RoundButton(EditorIcons.Plus, stringResource(R.string.panel_increase), onClick = onPlus)
    }
}

/** How see-through the selected post-its or picture are, as a slider that is recorded as one step when it is let go. */
@Composable
private fun OpacityRow(state: EditorState, items: List<PageItem>) {
    val notes = items.filterIsInstance<StickyNoteItem>()
    val images = items.filterIsInstance<ImageItem>()
    var shown by remember { mutableStateOf<Float?>(null) }
    if (notes.isNotEmpty()) {
        val opacity = shown ?: NoteOptions.alphaOf(notes.first().color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueText("${(opacity * 100f).roundToInt()}%")
            StepperSlider(
                fraction = (opacity - NoteOptions.MIN_OPACITY) / (1f - NoteOptions.MIN_OPACITY),
                onChange = { f ->
                    val next = opacityAt(f)
                    shown = next
                    state.adjustSelectedNotesOpacity(next)
                },
                steps = OPACITY_STEPS,
                onChangeFinished = {
                    shown = null
                    state.finishSelectionAdjust()
                },
                modifier = Modifier.width(240.dp),
            )
        }
    } else if (images.isNotEmpty()) {
        StepperSlider(
            fraction = ((shown ?: images.first().opacity) - MIN_IMAGE_OPACITY) / (1f - MIN_IMAGE_OPACITY),
            onChange = { fraction ->
                val opacity = MIN_IMAGE_OPACITY + fraction * (1f - MIN_IMAGE_OPACITY)
                shown = opacity
                state.adjustSelectedImageOpacity(opacity)
            },
            steps = 9,
            onChangeFinished = {
                shown = null
                state.finishSelectionAdjust()
            },
            modifier = Modifier.width(260.dp),
        )
    }
}

@Composable
private fun FontList(current: String, onPick: (String) -> Unit) {
    val names = listOf("") + FontLibrary.BUILT_IN + FontLibrary.names.filter { !FontLibrary.isBuiltIn(it) }
    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        for (name in names) {
            val family = name.takeIf { it.isNotEmpty() }?.let { FontLibrary.family(it, bold = false, italic = false) } ?: FontFamily.Default
            val chosen = name == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(AppShapes.Rounded12)
                    .clickable { onPick(name) }
                    .padding(horizontal = Spacing.S12),
            ) {
                Text(
                    name.ifEmpty { stringResource(R.string.font_default) },
                    color = if (chosen) LocalEditorColors.current.accent else Style.rowText(),
                    fontFamily = family,
                    fontSize = TextSize.S16,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The list the "..." button opens: lock, group, mirror, cut and the like, with the positions as round buttons at the top. */
@Composable
private fun MoreList(state: EditorState, items: List<PageItem>, kind: SelectionKind) {
    val shapes = items.filterIsInstance<ShapeItem>().filter { it.kind != ShapeKind.TAPE }
    val notes = items.filterIsInstance<StickyNoteItem>()
    val locked = items.any { it.locked }
    Row(horizontalArrangement = Arrangement.spacedBy(Style.roundButtonGap), modifier = Modifier.padding(bottom = Spacing.S6)) {
        if (shapes.isNotEmpty()) {
            // Turns the shapes over from left to right; when all of them already are, it puts them back.
            val flipped = shapes.all { it.mirrored }
            RoundButton(EditorIcons.Mirror, stringResource(R.string.selection_mirror)) { state.mirrorSelectedShapes(!flipped) }
        }
        RoundButton(EditorIcons.BringToFront, stringResource(R.string.selection_bring_front)) { state.arrangeSelected(ZPlace.FRONT) }
        RoundButton(EditorIcons.MoveToMiddle, stringResource(R.string.selection_move_middle)) { state.arrangeSelected(ZPlace.MIDDLE) }
        RoundButton(EditorIcons.SendToBack, stringResource(R.string.selection_send_back)) { state.arrangeSelected(ZPlace.BACK) }
    }
    MoreRow(
        if (locked) EditorIcons.Lock else EditorIcons.Unlock,
        stringResource(if (locked) R.string.selection_unlock else R.string.selection_lock),
    ) { state.setSelectionLocked(!locked) }
    // One row: joining what is selected groups it, and a group is split with the same row. A single item has nothing to join.
    if (items.size > 1) {
        val grouped = Grouping.isOneGroup(items)
        MoreRow(
            if (grouped) EditorIcons.Unlink else EditorIcons.Link,
            stringResource(if (grouped) R.string.selection_ungroup else R.string.selection_group),
        ) { state.groupSelection(!grouped) }
    }
    MoreRow(EditorIcons.Cut, stringResource(R.string.selection_cut)) { state.cutSelection() }
    if (notes.size == 1) MoreRow(EditorIcons.Text, stringResource(R.string.selection_note_text)) { state.editSelectedNoteText() }
    if (state.otherPane != null) MoreRow(EditorIcons.Split, stringResource(R.string.selection_copy_other_pane)) { state.copySelectionToOtherPane() }
    // The sticker button sits in the toolbar for lines and the rest, but not for text boxes and post-its.
    if ((kind == SelectionKind.TEXT || kind == SelectionKind.NOTES) && state.selectionCanBeSticker) {
        MoreRow(EditorIcons.Sticker, stringResource(R.string.selection_sticker)) { state.saveSelectionAsSticker() }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(Style.popupRowHeight)
            .clip(AppShapes.Rounded12)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.S8),
    ) {
        Icon(icon, null, tint = Style.rowIcon(), modifier = Modifier.padding(end = Spacing.S14).size(24.dp))
        Text(label, color = Style.rowText(), fontSize = TextSize.S16, fontWeight = FontWeight.Medium)
    }
}

/** One part of a table's color: its swatch opens the wheel, and the cross beside it, shown when the color is set, puts the usual look back. */
@Composable
private fun TableColorButton(label: Int, color: Int, set: Boolean, onClear: (() -> Unit)?, onClick: () -> Unit) {
    val name = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolbarIcon(EditorIcons.EditColor, name, onClick = onClick)
        FillSwatch(if (set) color else 0)
        if (set && onClear != null) ToolbarIcon(EditorIcons.Close, name, onClick = onClear)
    }
}
