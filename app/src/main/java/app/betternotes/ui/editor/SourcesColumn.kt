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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
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
            .background(androidx.compose.ui.graphics.Color(SCRIM))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .opensFrom(OpenFrom.Start)
                .fillMaxHeight()
                .width(ColumnWidth)
                .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                .background(colors.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(EditorIcons.Back, stringResource(R.string.editor_back), onClose)
                RoundButton(EditorIcons.More, stringResource(R.string.editor_more)) {
                    onClose()
                    state.moreMenuOpen = true
                }
            }
            if (info != null) {
                Spacer(Modifier.height(26.dp))
                val titleStyle = TextStyle(
                    color = colors.title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
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
                Text(stamp, color = colors.label, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(34.dp))
            SectionRow(stringResource(R.string.sources_tabs)) { notices.notBuilt() }
            Spacer(Modifier.height(34.dp))
            SectionRow(stringResource(R.string.sources_count, 1)) { state.sourcesAddOpen = true }
            Spacer(Modifier.height(10.dp))
            if (info != null) SourceCard(state, info)
        }
        if (state.sourcesAddOpen) AddToNote(state, sources, recorder, notices, onClose)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(1.dp, colors.pillBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) { Icon(icon, description, tint = colors.icon, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun SectionRow(label: String, onAdd: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = colors.label, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
        Icon(EditorIcons.ChevronDown, null, tint = colors.label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onAdd),
        ) { Icon(EditorIcons.Plus, stringResource(R.string.sources_add), tint = colors.icon, modifier = Modifier.size(22.dp)) }
    }
}

/** The note itself is its first source: its pages, with the first one as the picture. */
@Composable
private fun SourceCard(state: EditorState, info: DocumentInfo) {
    val colors = LocalEditorColors.current
    val first = remember { state.pages().firstOrNull() }
    val thumbnail by produceState<Bitmap?>(null, first?.id, state.historyVersion) {
        value = first?.let { state.thumbnail(it.id, THUMBNAIL_PX) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.field)
            .clickable {
                state.sourcesOpen = false
                state.pagesOpen = true
            }
            .padding(10.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(colors.pill)) {
            thumbnail?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(info.title, color = colors.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(stringResource(R.string.sources_pages, info.pageCount), color = colors.label, fontSize = 13.sp)
        }
    }
}

private class AddOption(val icon: ImageVector, val title: Int, val subtitle: Int, val action: () -> Unit)

/** The sheet that lists what can be added to the note. Each card does what the toolbar's tool does. */
@Composable
private fun AddToNote(state: EditorState, sources: ImageSources, recorder: RecorderState, notices: Notices, closeColumn: () -> Unit) {
    val colors = LocalEditorColors.current
    fun done(action: () -> Unit): () -> Unit = {
        state.sourcesAddOpen = false
        closeColumn()
        action()
    }
    val options = listOf(
        AddOption(EditorIcons.Text, R.string.add_text, R.string.add_text_detail, done { state.select(ActiveTool.TEXT) }),
        AddOption(EditorIcons.Mic, R.string.add_audio, R.string.add_audio_detail, done { recorder.menuOpen = true }),
        AddOption(EditorIcons.Pen, R.string.add_canvas, R.string.add_canvas_detail, done { notices.notBuilt() }),
        AddOption(EditorIcons.Image, R.string.add_photos, R.string.add_photos_detail, done { sources.pickPhoto() }),
        AddOption(EditorIcons.Camera, R.string.add_take_photo, R.string.add_take_photo_detail, done { sources.takePhoto() }),
        AddOption(EditorIcons.Scan, R.string.add_scan, R.string.add_scan_detail, done { sources.scan() }),
        AddOption(EditorIcons.Tray, R.string.add_file, R.string.add_file_detail, done { sources.pickDocument() }),
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
                .clip(RoundedCornerShape(28.dp))
                .background(colors.panel)
                .border(1.dp, colors.pillBorder, RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(46.dp)) {
                Text(
                    stringResource(R.string.add_title),
                    color = colors.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    RoundButton(EditorIcons.Close, stringResource(R.string.panel_close)) { state.sourcesAddOpen = false }
                }
            }
            Spacer(Modifier.height(12.dp))
            for (row in options.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, colors.pillBorder, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = option.action)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(option.icon, null, tint = colors.accent, modifier = Modifier.size(38.dp))
        Column {
            Text(stringResource(option.title), color = colors.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(option.subtitle), color = colors.label, fontSize = 14.sp, maxLines = 2)
        }
    }
}

private const val SCRIM = 0x66000000
private const val THUMBNAIL_PX = 160
private val ColumnWidth = 340.dp
private val AddSheetWidth = 520.dp
private val CARD_HEIGHT = 124.dp
