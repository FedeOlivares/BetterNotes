package app.betternotes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import app.betternotes.core.library.FolderStyle
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.betternotes.core.library.LibraryEntry
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.OPAQUE_ALPHA
import app.betternotes.ui.theme.RGB_MASK
import app.betternotes.ui.common.AddCustomColor
import app.betternotes.ui.theme.LocalAppColors
import androidx.compose.ui.graphics.toArgb
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.Swatches
import app.betternotes.ui.theme.UiColors
import app.betternotes.ui.theme.opensFrom

// No screenshot shows these dialogs. They are plain Material stand-ins, and need the owner's references.

@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
internal fun ConfirmDialog(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
internal fun MoveDialog(
    canMoveToTop: Boolean,
    targets: List<LibraryEntry>,
    onMove: (folderId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_move_to)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                if (canMoveToTop) {
                    item { ListRow(stringResource(R.string.library_top_level)) { onMove(null) } }
                }
                items(targets, key = { it.id }) { folder -> ListRow(folder.name) { onMove(folder.id) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
internal fun TagsDialog(
    known: List<String>,
    initial: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(initial) }
    var added by remember { mutableStateOf("") }
    val shown = (known + chosen).distinct().sorted()
    AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_tags)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it }) { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                chosen = if (tag in chosen) chosen - tag else chosen + tag
                            },
                        ) {
                            Checkbox(tag in chosen, onCheckedChange = null, modifier = Modifier.padding(Spacing.S12))
                            Text(tag)
                        }
                    }
                }
                OutlinedTextField(
                    added,
                    { added = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_new_tag)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tag = added.trim()
                onConfirm(if (tag.isEmpty()) chosen else chosen + tag)
            }) { Text(stringResource(R.string.library_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
private fun ListRow(label: String, onClick: () -> Unit) {
    Text(label, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.S14))
}

/** The folder's color, opacity and whether it shows its files. The opacity fades the folder only, never what is in it. */
@Composable
internal fun FolderStyleDialog(initial: FolderStyle, onConfirm: (FolderStyle) -> Unit, onDismiss: () -> Unit) {
    var color by remember { mutableStateOf(initial.color) }
    var opacity by remember { mutableFloatStateOf(initial.opacity) }
    var previews by remember { mutableStateOf(initial.previews) }
    val app = LocalAppColors.current
    AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_edit_folder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S12)) {
                Text(stringResource(R.string.library_folder_color))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.S10)) {
                    for (swatch in FolderColors) {
                        val chosen = swatch == color
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(AppShapes.Circle)
                                .background(if (swatch == FolderStyle.DEFAULT_COLOR) DefaultFolderColor else Color(swatch or OPAQUE_ALPHA))
                                .border(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else UiColors.SwatchEdge, AppShapes.Circle)
                                .clickable(role = Role.RadioButton) { color = swatch },
                        )
                    }
                    AddCustomColor(
                        if (color == FolderStyle.DEFAULT_COLOR) UiColors.DefaultFolder.toArgb() else color or OPAQUE_ALPHA,
                        app.title,
                        // A folder's color is kept without alpha, and a plain black is nudged off 0, which stands for the default folder color.
                        onPick = { color = (it and RGB_MASK).takeIf { rgb -> rgb != FolderStyle.DEFAULT_COLOR } ?: CUSTOM_BLACK },
                        size = 36.dp,
                    )
                }
                Text(stringResource(R.string.library_folder_opacity, (opacity * 100).roundToInt()))
                Slider(opacity, { opacity = it }, valueRange = MIN_FOLDER_OPACITY..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.library_folder_previews), Modifier.weight(1f))
                    Switch(previews, { previews = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(FolderStyle(color, opacity, previews)) }) { Text(stringResource(R.string.library_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

/** The nearest a custom folder color can be to black without being 0, which is the default folder color. */
private const val CUSTOM_BLACK = 0x000001

private val FolderColors = listOf(FolderStyle.DEFAULT_COLOR) + Swatches.Folder
private const val MIN_FOLDER_OPACITY = 0.1f

/** The swatch for the default folder: the folder blue the library draws it in. */
private val DefaultFolderColor = UiColors.DefaultFolder
