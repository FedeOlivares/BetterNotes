package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.model.ImageItem
import app.betternotes.core.edit.ZPlace
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.tools.NoteOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors

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
    val locked = items.any { it.locked }
    Layout(
        modifier = modifier.fillMaxSize(),
        content = { SelectionBarContent(state, items, notes, images, locked) },
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
private fun SelectionBarContent(state: EditorState, items: List<PageItem>, notes: List<StickyNoteItem>, images: List<ImageItem>, locked: Boolean) {
    Pill(Modifier.masksInk(state, "selectionBar")) {
        ToolbarIcon(
            if (locked) EditorIcons.Lock else EditorIcons.Unlock,
            stringResource(if (locked) R.string.selection_unlock else R.string.selection_lock),
            selected = locked,
            onClick = { state.setSelectionLocked(!locked) },
        )
        // Post-its are always at the bottom, so a selection of only post-its has nothing to arrange, except on the cover.
        if (notes.size < items.size || state.selectionOnCover) {
            PillDivider()
            // Front, middle, back: the solid square is the top, the middle and the bottom one, in that order.
            ToolbarIcon(EditorIcons.BringToFront, stringResource(R.string.selection_bring_front), onClick = { state.arrangeSelected(ZPlace.FRONT) })
            ToolbarIcon(EditorIcons.MoveToMiddle, stringResource(R.string.selection_move_middle), onClick = { state.arrangeSelected(ZPlace.MIDDLE) })
            ToolbarIcon(EditorIcons.SendToBack, stringResource(R.string.selection_send_back), onClick = { state.arrangeSelected(ZPlace.BACK) })
        }
        if (state.otherPane != null) {
            PillDivider()
            ToolbarIcon(EditorIcons.Split, stringResource(R.string.selection_copy_other_pane), onClick = { state.copySelectionToOtherPane() })
        }
        if (notes.isNotEmpty()) {
            PillDivider()
            for (color in NoteOptions.STICKY_PALETTE) {
                NoteSwatch(color, selected = notes.all { it.color == color }) { state.recolorSelectedNotes(color) }
            }
            ToolbarIcon(
                EditorIcons.EditColor,
                stringResource(R.string.selection_note_color),
                onClick = { state.colorTarget = ColorTarget.SELECTED_NOTE },
            )
            val keep = notes.all { it.keepOpen }
            ToolbarIcon(
                EditorIcons.StickyNote,
                stringResource(R.string.selection_keep_open),
                selected = keep,
                onClick = { state.setSelectedNotesKeepOpen(!keep) },
            )
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

@Composable
private fun NoteSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        Modifier
            .padding(horizontal = 3.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.swatchRing, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {}
}
