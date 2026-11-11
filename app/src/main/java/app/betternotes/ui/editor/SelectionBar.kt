package app.betternotes.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.model.ImageItem
import app.betternotes.core.edit.Grouping
import app.betternotes.core.edit.ZPlace
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TableItem
import app.betternotes.ui.icons.EditorIcons

/** The least opacity a picture can be given, so it never vanishes. */
private const val MIN_IMAGE_OPACITY = 0.1f

/**
 * What can be done with what the lasso has selected, in a small bar that the "..." button below the selection opens: lock or unlock it, recolor a sticky
 * note and choose whether it stays open, and make a picture translucent. Shown only while something is selected.
 */
@Composable
internal fun SelectionBar(state: EditorState, modifier: Modifier = Modifier) {
    val items = state.selection
    if (items.isNullOrEmpty() || state.viewOnly || !state.selectionMenuOpen) return
    // Read so that the bar follows the selection when the page moves.
    @Suppress("UNUSED_VARIABLE") val moved = state.viewMoves + state.historyVersion
    val anchor = state.selectionMenuAnchor() ?: return
    val notes = items.filterIsInstance<StickyNoteItem>()
    val images = items.filterIsInstance<ImageItem>()
    val tables = items.filterIsInstance<TableItem>()
    val shapes = items.filterIsInstance<ShapeItem>().filter { it.kind != ShapeKind.TAPE }
    val locked = items.any { it.locked }
    Layout(
        modifier = modifier.fillMaxSize(),
        content = { SelectionBarContent(state, items, notes, images, tables, shapes, locked) },
    ) { measurables, constraints ->
        val bar = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Its right edge sits where the "..." button's did, just under the selection, and it never leaves the screen.
            val x = (anchor[0] - bar.width).coerceIn(0f, (constraints.maxWidth - bar.width).coerceAtLeast(0).toFloat())
            val y = anchor[1].coerceIn(0f, (constraints.maxHeight - bar.height).coerceAtLeast(0).toFloat())
            bar.place(x.roundToInt(), y.roundToInt())
        }
    }
}

@Composable
private fun SelectionBarContent(state: EditorState, items: List<PageItem>, notes: List<StickyNoteItem>, images: List<ImageItem>, tables: List<TableItem>, shapes: List<ShapeItem>, locked: Boolean) {
    Pill(Modifier.masksInk(state, "selectionBar")) {
        ToolbarIcon(
            if (locked) EditorIcons.Lock else EditorIcons.Unlock,
            stringResource(if (locked) R.string.selection_unlock else R.string.selection_lock),
            selected = locked,
            onClick = { state.setSelectionLocked(!locked) },
        )
        // One icon: the links joined groups what is selected, and the links apart with a line across splits the group they are in.
        // A single item has nothing to join.
        if (items.size > 1) {
            PillDivider()
            val grouped = Grouping.isOneGroup(items)
            ToolbarIcon(
                if (grouped) EditorIcons.Unlink else EditorIcons.Link,
                stringResource(if (grouped) R.string.selection_ungroup else R.string.selection_group),
                onClick = { state.groupSelection(!grouped) },
            )
        }
        PillDivider()
        // Front, middle, back: the solid square is the top, the middle and the bottom one, in that order.
        ToolbarIcon(EditorIcons.BringToFront, stringResource(R.string.selection_bring_front), onClick = { state.arrangeSelected(ZPlace.FRONT) })
        ToolbarIcon(EditorIcons.MoveToMiddle, stringResource(R.string.selection_move_middle), onClick = { state.arrangeSelected(ZPlace.MIDDLE) })
        ToolbarIcon(EditorIcons.SendToBack, stringResource(R.string.selection_send_back), onClick = { state.arrangeSelected(ZPlace.BACK) })
        if (state.otherPane != null) {
            PillDivider()
            ToolbarIcon(EditorIcons.Split, stringResource(R.string.selection_copy_other_pane), onClick = { state.copySelectionToOtherPane() })
        }
        if (notes.isNotEmpty()) {
            PillDivider()
            if (notes.size == 1) ToolbarIcon(EditorIcons.Text, stringResource(R.string.selection_note_text), onClick = { state.editSelectedNoteText() })
            val keep = notes.all { it.keepOpen }
            ToolbarIcon(
                EditorIcons.StickyNote,
                stringResource(R.string.selection_keep_open),
                selected = keep,
                onClick = { state.setSelectedNotesKeepOpen(!keep) },
            )
        }
        if (shapes.isNotEmpty()) {
            PillDivider()
            // Turns the shapes over from left to right; when all of them already are, it puts them back.
            val flipped = shapes.all { it.mirrored }
            ToolbarIcon(
                EditorIcons.Mirror,
                stringResource(R.string.selection_mirror),
                selected = flipped,
                onClick = { state.mirrorSelectedShapes(!flipped) },
            )
        }
        if (tables.isNotEmpty()) {
            PillDivider()
            // Line, fill and header: a touch opens the color wheel, and a set fill or header can be taken off with the cross beside it.
            TableColorButton(R.string.selection_table_line, tables.first().lineColor, set = true, onClear = null) { state.colorTarget = ColorTarget.SELECTED_TABLE_LINE }
            val filled = tables.any { it.fillColor ushr 24 != 0 }
            TableColorButton(R.string.selection_table_fill, tables.first().fillColor, set = filled, onClear = { state.recolorSelectedTables { it.copy(fillColor = 0) } }) { state.colorTarget = ColorTarget.SELECTED_TABLE_FILL }
            val headed = tables.any { it.headerColor != 0 }
            TableColorButton(R.string.selection_table_header, tables.first().headerColor, set = headed, onClear = { state.recolorSelectedTables { it.copy(headerColor = 0) } }) { state.colorTarget = ColorTarget.SELECTED_TABLE_HEADER }
        }
        if (images.isNotEmpty()) {
            PillDivider()
            ToolbarIcon(EditorIcons.Translucent, stringResource(R.string.selection_translucent), enabled = false, onClick = {})
            var shown by remember { mutableStateOf<Float?>(null) }
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
                modifier = Modifier.width(240.dp),
            )
        }
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
