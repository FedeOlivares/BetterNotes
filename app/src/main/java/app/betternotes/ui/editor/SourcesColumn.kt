package app.betternotes.ui.editor

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.betternotes.R
import app.betternotes.library.ThumbnailRenderer
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppFonts
import app.betternotes.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import app.betternotes.ui.theme.opensFrom
import java.util.Date

/**
 * The column that slides in from the left behind the sidebar button, from the owner's screenshot: the
 * note's title and date, its tabs, and its sources, with a + that opens "Add to Note". The assistant
 * bar, the link card and linked tabs are left out: they need the internet or a second canvas.
 */
@Composable
internal fun SourcesColumn(
    state: EditorState,
    actions: EditorActions,
    sources: ImageSources,
    recorder: RecorderState,
    notices: Notices,
    onClose: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val info = remember { actions.documentInfo() }
    var title by remember { mutableStateOf(info?.title.orEmpty()) }
    var renaming by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .opensFrom(OpenFrom.Start)
                .fillMaxHeight()
                .width(ColumnWidth)
                .clip(AppShapes.EndRounded28)
                .background(colors.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = Spacing.S18, vertical = Spacing.S14),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(EditorIcons.Back, stringResource(R.string.editor_back), onClose)
                RoundButton(EditorIcons.More, stringResource(R.string.editor_more)) {
                    onClose()
                    state.moreMenuOpen = true
                }
            }
            if (info != null) {
                Spacer(Modifier.height(Spacing.S26))
                val titleStyle = TextStyle(
                    color = colors.title,
                    fontFamily = AppFonts.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = TextSize.S28,
                )
                if (renaming) {
                    // The name is edited where it stands. Done, or leaving the field, keeps it.
                    var text by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
                    val focus = remember { FocusRequester() }
                    var focused by remember { mutableStateOf(false) }
                    fun commit() {
                        renaming = false
                        val name = text.text.trim()
                        if (name.isNotEmpty() && name != title) {
                            title = name
                            actions.onRename(name)
                        }
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(colors.title),
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged {
                                if (it.isFocused) focused = true else if (focused) commit()
                            },
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                } else {
                    Text(
                        title,
                        style = titleStyle,
                        maxLines = 2,
                        modifier = Modifier.clickable(role = Role.Button) { renaming = true },
                    )
                }
                val context = LocalContext.current
                val stamp = "${DateFormat.getMediumDateFormat(context).format(Date(info.createdMs))}, " +
                    DateFormat.getTimeFormat(context).format(Date(info.createdMs))
                Text(stamp, color = colors.label, fontSize = TextSize.S14, modifier = Modifier.padding(top = Spacing.S4))
            }
            Spacer(Modifier.height(Spacing.S34))
            SectionRow(stringResource(R.string.sources_tabs)) { notices.notBuilt() }
            Spacer(Modifier.height(Spacing.S34))
            // The binder: the notebook itself, the sources made from here, and notebooks linked as sources.
            val entries by produceState(emptyList<SourceEntry>(), state.sourcesVersion) {
                value = withContext(Dispatchers.IO) { actions.sources() }
            }
            val currentId = actions.currentSourceId()
            SectionRow(stringResource(R.string.sources_count, entries.size.coerceAtLeast(1))) { state.sourcesAddOpen = true }
            Spacer(Modifier.height(Spacing.S10))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S8)) {
                for (entry in entries) {
                    SourceCard(
                        entry,
                        current = entry.id == currentId,
                        onOpen = {
                            if (entry.id != currentId) actions.onOpenSource(entry)
                            onClose()
                        },
                        onRemove = {
                            actions.onRemoveSource(entry)
                            state.sourcesVersion++
                        },
                    )
                }
            }
        }
        if (state.sourcesAddOpen) AddToNote(state, actions, sources, recorder, notices, onClose)
        if (state.notebookPickerOpen) NotebookPicker(state, actions, onClose)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(AppShapes.Circle)
            .border(1.dp, colors.pillBorder, AppShapes.Circle)
            .clickable(role = Role.Button, onClick = onClick),
    ) { Icon(icon, description, tint = colors.icon, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun SectionRow(label: String, onAdd: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = colors.label, fontSize = TextSize.S17)
        Spacer(Modifier.width(Spacing.S8))
        Icon(EditorIcons.ChevronDown, null, tint = colors.label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(AppShapes.Circle).clickable(role = Role.Button, onClick = onAdd),
        ) { Icon(EditorIcons.Plus, stringResource(R.string.sources_add), tint = colors.icon, modifier = Modifier.size(22.dp)) }
    }
}

/** One line of the binder: a picture of its first page, its name and its pages. The one open now is marked. */
@Composable
private fun SourceCard(entry: SourceEntry, current: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalEditorColors.current
    val thumbnail by produceState<Bitmap?>(null, entry.id) {
        value = withContext(Dispatchers.IO) {
            try {
                ThumbnailRenderer.render(entry.store)
            } catch (_: Exception) {
                null
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Rounded18)
            .background(if (current) colors.accentContainer else colors.field)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(Spacing.S10),
    ) {
        Box(Modifier.size(52.dp).clip(AppShapes.Rounded8).background(colors.pill)) {
            thumbnail?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(Spacing.S14))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = colors.title, fontSize = TextSize.S16, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(stringResource(R.string.sources_pages, entry.pageCount), color = colors.label, fontSize = TextSize.S13)
        }
        if (entry.kind != SourceKind.OWNER) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(AppShapes.Circle).clickable(role = Role.Button, onClick = onRemove),
            ) { Icon(EditorIcons.Trash, stringResource(R.string.sources_remove), tint = colors.label, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** The library's notebooks that can be linked into the binder. */
@Composable
private fun NotebookPicker(state: EditorState, actions: EditorActions, closeColumn: () -> Unit) {
    val colors = LocalEditorColors.current
    val notebooks = remember { actions.linkableNotebooks() }
    androidx.compose.material3.AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
        onDismissRequest = { state.notebookPickerOpen = false },
        title = { Text(stringResource(R.string.add_notebook)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (notebooks.isEmpty()) Text(stringResource(R.string.sources_no_notebooks), color = colors.label, fontSize = TextSize.S15)
                for ((id, name) in notebooks) {
                    Text(
                        name,
                        color = colors.title,
                        fontSize = TextSize.S17,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                state.notebookPickerOpen = false
                                state.onAddSource?.invoke(SourceRequest.Notebook(id))
                            }
                            .padding(vertical = Spacing.S12),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { state.notebookPickerOpen = false }) { Text(stringResource(R.string.scan_cancel)) }
        },
    )
}
private class AddOption(val icon: ImageVector, val title: Int, val subtitle: Int, val action: () -> Unit)

/** The sheet that lists what can be added to the note. Each card does what the toolbar's tool does. */
@Composable
private fun AddToNote(state: EditorState, actions: EditorActions, sources: ImageSources, recorder: RecorderState, notices: Notices, closeColumn: () -> Unit) {
    val colors = LocalEditorColors.current
    fun done(action: () -> Unit): () -> Unit = {
        state.sourcesAddOpen = false
        closeColumn()
        action()
    }
    val options = listOf(
        // Everything here becomes a source of the binder, with a canvas of its own. What goes on the open canvas is added
        // from the toolbar's tools.
        AddOption(EditorIcons.Mic, R.string.add_audio, R.string.add_audio_source_detail, done { sources.sourceAudio() }),
        AddOption(EditorIcons.Pen, R.string.add_canvas, R.string.add_canvas_detail, done { state.onAddSource?.invoke(SourceRequest.Canvas) }),
        AddOption(EditorIcons.Image, R.string.add_photos, R.string.add_photos_detail, done { sources.sourcePhoto() }),
        AddOption(EditorIcons.Camera, R.string.add_take_photo, R.string.add_take_photo_detail, done { sources.sourceCamera() }),
        AddOption(EditorIcons.Scan, R.string.add_scan, R.string.add_scan_detail, done { sources.sourceCamera() }),
        AddOption(EditorIcons.Tray, R.string.add_file, R.string.add_file_detail, done { sources.sourceFile() }),
        AddOption(EditorIcons.Folder, R.string.add_notebook, R.string.add_notebook_detail, {
            state.sourcesAddOpen = false
            state.notebookPickerOpen = true
        }),
    )
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                state.sourcesAddOpen = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .opensFrom(OpenFrom.Center)
                .width(AddSheetWidth)
                .clip(AppShapes.Rounded28)
                .background(colors.panel)
                .border(1.dp, colors.pillBorder, AppShapes.Rounded28)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(Spacing.S16),
        ) {
            Box(Modifier.fillMaxWidth().height(46.dp)) {
                Text(
                    stringResource(R.string.add_title),
                    color = colors.title,
                    fontSize = TextSize.S20,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    RoundButton(EditorIcons.Close, stringResource(R.string.panel_close)) { state.sourcesAddOpen = false }
                }
            }
            Spacer(Modifier.height(Spacing.S12))
            for (row in options.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.S12), horizontalArrangement = Arrangement.spacedBy(Spacing.S12)) {
                    for (option in row) AddCard(option, Modifier.weight(1f))
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AddCard(option: AddOption, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Column(
        modifier
            .height(CARD_HEIGHT)
            .clip(AppShapes.Rounded22)
            .border(1.dp, colors.pillBorder, AppShapes.Rounded22)
            .clickable(role = Role.Button, onClick = option.action)
            .padding(Spacing.S16),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(option.icon, null, tint = colors.accent, modifier = Modifier.size(38.dp))
        Column {
            Text(stringResource(option.title), color = colors.title, fontSize = TextSize.S18, fontWeight = FontWeight.SemiBold)
            Text(stringResource(option.subtitle), color = colors.label, fontSize = TextSize.S14, maxLines = 2)
        }
    }
}

private val ColumnWidth = 340.dp
private val AddSheetWidth = 520.dp
private val CARD_HEIGHT = 124.dp
