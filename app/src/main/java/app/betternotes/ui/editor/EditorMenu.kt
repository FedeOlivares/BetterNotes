package app.betternotes.ui.editor

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.ui.settings.SettingsOverlay
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import java.util.Date

/** What the menu shows of the open document. */
internal class DocumentInfo(val title: String, val pageCount: Int, val modifiedMs: Long, val createdMs: Long)

/**
 * The menu behind the "..." button, laid out from the owner's screenshot: the document's card, a row of
 * tiles, and the screens that can be opened. Sharing is left out, as nothing leaves the device. A touch
 * anywhere else closes it.
 */
@Composable
internal fun EditorMenu(state: EditorState, actions: EditorActions, onClose: () -> Unit) {
    val colors = LocalEditorColors.current
    val info = remember { actions.documentInfo() }
    var renaming by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 8.dp)
                .width(MenuWidth)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(bottom = 10.dp),
        ) {
            if (info != null) {
                Header(state, info)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                ) {
                    Tile(EditorIcons.Export, R.string.menu_export, Modifier.weight(1f)) {
                        onClose()
                        actions.onOpenOverlay(SettingsOverlay.EXPORT)
                    }
                    Tile(EditorIcons.EditColor, R.string.menu_rename, Modifier.weight(1f)) { renaming = true }
                    Tile(EditorIcons.GoToPage, R.string.menu_go_to_page, Modifier.weight(1f)) {
                        onClose()
                        state.pagesOpen = true
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Entry(EditorIcons.Template, R.string.menu_change_template) {
                onClose()
                actions.onOpenOverlay(SettingsOverlay.TEMPLATE)
            }
            Entry(EditorIcons.ScrollLayout, R.string.menu_scroll_layout) {
                onClose()
                actions.onOpenOverlay(SettingsOverlay.SCREEN)
            }
            Entry(EditorIcons.StylusFinger, R.string.menu_stylus_finger) {
                onClose()
                actions.onOpenOverlay(SettingsOverlay.STYLUS_FINGER)
            }
            Entry(EditorIcons.Settings, R.string.menu_settings) {
                onClose()
                actions.onOpenOverlay(SettingsOverlay.SCREEN)
            }
            Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(colors.panelDivider))
            Entry(EditorIcons.Trash, R.string.menu_delete_selection) {
                onClose()
                state.deleteSelection()
            }
            Entry(EditorIcons.Eye, R.string.menu_real_size) {
                onClose()
                actions.onZoomToRealSize()
            }
            Entry(EditorIcons.Sun, R.string.menu_theme) {
                onClose()
                actions.onToggleTheme()
            }
            actions.onTogglePerfReadout?.let { toggle ->
                Entry(EditorIcons.Sliders, R.string.menu_perf) {
                    onClose()
                    toggle()
                }
            }
        }
    }
    if (renaming && info != null) {
        RenameDialog(info.title, onDismiss = { renaming = false }) { name ->
            renaming = false
            onClose()
            actions.onRename(name)
        }
    }
}

@Composable
private fun Header(state: EditorState, info: DocumentInfo) {
    val colors = LocalEditorColors.current
    val first = remember { state.pages().firstOrNull() }
    val thumbnail by produceState<Bitmap?>(null, first?.id, state.historyVersion) {
        value = first?.let { state.thumbnail(it.id, THUMBNAIL_PX) }
    }
    val format = DateFormat.getMediumDateFormat(androidx.compose.ui.platform.LocalContext.current)
    val time = DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
    fun stamp(ms: Long) = "${format.format(Date(ms))}, ${time.format(Date(ms))}"
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Box(
            Modifier
                .size(THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.field),
        ) {
            thumbnail?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(THUMBNAIL_SIZE)) {
            Text(info.title, color = colors.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.menu_page_count, info.pageCount), color = colors.label, fontSize = 13.sp)
            Text(stringResource(R.string.menu_modified, stamp(info.modifiedMs)), color = colors.label, fontSize = 13.sp)
            Text(stringResource(R.string.menu_created, stamp(info.createdMs)), color = colors.label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(TILE_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accentContainer)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, null, tint = colors.toolSelectedIcon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(label), color = colors.toolSelectedIcon, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Entry(icon: ImageVector, label: Int, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Icon(icon, null, tint = colors.icon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(stringResource(label), color = colors.title, fontSize = 16.sp)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_rename)) },
        text = { TextField(name, { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onDone(name.trim()) }) { Text(stringResource(R.string.scan_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_cancel)) } },
    )
}

private val MenuWidth = 400.dp
private val THUMBNAIL_SIZE = 84.dp
private val TILE_HEIGHT = 78.dp
private const val THUMBNAIL_PX = 240
