package app.betternotes.ui.library

import androidx.compose.foundation.clickable
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
import app.betternotes.ui.theme.OpenFrom
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
                            Checkbox(tag in chosen, onCheckedChange = null, modifier = Modifier.padding(12.dp))
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
    Text(label, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp))
}
