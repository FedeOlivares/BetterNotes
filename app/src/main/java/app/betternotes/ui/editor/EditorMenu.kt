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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import app.betternotes.R
import app.betternotes.library.LibraryPreferences
import app.betternotes.ui.settings.SettingsOverlay
import app.betternotes.ui.theme.ThemeMode
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.opensFrom
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
    var choosingLanguage by remember { mutableStateOf(false) }
    val dark = LibraryPreferences.get(androidx.compose.ui.platform.LocalContext.current).theme == ThemeMode.DARK
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = Spacing.S8)
                .opensFrom(OpenFrom.TopEnd)
                .width(MenuWidth)
                .clip(AppShapes.Rounded26)
                .background(colors.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(bottom = Spacing.S10),
        ) {
            if (info != null) {
                Header(state, info)
                // Two rows of two: add or edit cover and rename on top, export and go to page below.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S8), modifier = Modifier.padding(horizontal = Spacing.S10)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S8), modifier = Modifier.fillMaxWidth()) {
                        if (state.canHaveCover) {
                            val hasCover = state.coverId != null
                            Tile(
                                EditorIcons.Cover,
                                if (hasCover) R.string.menu_edit_cover else R.string.menu_add_cover,
                                Modifier.weight(1f),
                            ) {
                                onClose()
                                state.coverOpen = true
                            }
                        } else if (state.isInfinite) {
                            // An infinite canvas has no cover, so its first tile is the size, which the template editor sets.
                            Tile(EditorIcons.Template, R.string.menu_edit_size, Modifier.weight(1f)) {
                                onClose()
                                actions.onOpenOverlay(SettingsOverlay.TEMPLATE)
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Tile(EditorIcons.EditColor, R.string.menu_rename, Modifier.weight(1f)) { renaming = true }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S8), modifier = Modifier.fillMaxWidth()) {
                        Tile(EditorIcons.Export, R.string.menu_export, Modifier.weight(1f)) {
                            onClose()
                            actions.onOpenOverlay(SettingsOverlay.EXPORT)
                        }
                        Tile(EditorIcons.GoToPage, R.string.menu_go_to_page, Modifier.weight(1f)) {
                            onClose()
                            state.pagesOpen = true
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.S8))
            }
            Entry(EditorIcons.Search, R.string.menu_search) {
                onClose()
                actions.onOpenOverlay(SettingsOverlay.SEARCH)
            }
            Entry(EditorIcons.Template, R.string.menu_change_template) {
                onClose()
                actions.onOpenOverlay(SettingsOverlay.TEMPLATE)
            }
            Entry(EditorIcons.Template, R.string.menu_template_language) { choosingLanguage = true }
            Entry(EditorIcons.Split, if (actions.splitOpen()) R.string.menu_split_close else R.string.menu_split) {
                onClose()
                actions.onSplit()
            }
            if (actions.splitOpen()) {
                Entry(EditorIcons.Split, if (actions.splitSideBySide()) R.string.menu_split_stack else R.string.menu_split_side) {
                    onClose()
                    actions.onSplitFlip()
                }
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
            Box(Modifier.padding(horizontal = Spacing.S18, vertical = Spacing.S6).fillMaxWidth().height(1.dp).background(colors.panelDivider))
            Entry(EditorIcons.Trash, R.string.menu_delete_selection) {
                onClose()
                state.deleteSelection()
            }
            Entry(EditorIcons.Eye, R.string.menu_real_size) {
                onClose()
                actions.onZoomToRealSize()
            }
            ToggleEntry(EditorIcons.Sun, R.string.menu_theme, dark, actions.onToggleTheme)
            actions.onTogglePerfReadout?.let { toggle ->
                Entry(EditorIcons.Sliders, R.string.menu_perf) {
                    onClose()
                    toggle()
                }
            }
        }
    }
    if (choosingLanguage) {
        // This notebook's own language for the words on its templates; it overrides the app's choice for this notebook only.
        AlertDialog(
            modifier = Modifier.opensFrom(OpenFrom.Center),
            onDismissRequest = { choosingLanguage = false },
            title = { Text(stringResource(R.string.menu_template_language)) },
            text = {
                Column {
                    Text(stringResource(R.string.menu_template_language_detail), color = colors.label, fontSize = TextSize.S14)
                    Spacer(Modifier.height(Spacing.S12))
                    for ((code, name) in listOf("en" to "English", "es" to "Español")) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) {
                                    choosingLanguage = false
                                    onClose()
                                    state.setTemplateLanguage(code)
                                }
                                .padding(vertical = Spacing.S10),
                        ) {
                            androidx.compose.material3.RadioButton(selected = state.templateLanguage == code, onClick = null)
                            Text(name, color = colors.title, fontSize = TextSize.S17, modifier = Modifier.padding(start = Spacing.S12))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingLanguage = false }) { Text(stringResource(R.string.scan_cancel)) } },
        )
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
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.S18, vertical = Spacing.S16)) {
        Box(
            Modifier
                .size(THUMBNAIL_SIZE)
                .clip(AppShapes.Rounded10)
                .background(colors.field),
        ) {
            thumbnail?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(Spacing.S14))
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(THUMBNAIL_SIZE)) {
            Text(info.title, color = colors.title, fontSize = TextSize.S18, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(Spacing.S2))
            Text(stringResource(R.string.menu_page_count, info.pageCount), color = colors.label, fontSize = TextSize.S13)
            Text(stringResource(R.string.menu_modified, stamp(info.modifiedMs)), color = colors.label, fontSize = TextSize.S13)
            Text(stringResource(R.string.menu_created, stamp(info.createdMs)), color = colors.label, fontSize = TextSize.S13)
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
            .clip(AppShapes.Rounded16)
            .background(colors.accentContainer)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, null, tint = colors.toolSelectedIcon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(Spacing.S6))
        Text(stringResource(label), color = colors.toolSelectedIcon, fontSize = TextSize.S13, maxLines = 1)
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
            .padding(horizontal = Spacing.S20, vertical = Spacing.S13),
    ) {
        Icon(icon, null, tint = colors.icon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.S18))
        Text(stringResource(label), color = colors.title, fontSize = TextSize.S16)
    }
}

/** A row with a switch. The menu stays open so the change shows at once. */
@Composable
private fun ToggleEntry(icon: ImageVector, label: Int, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(start = Spacing.S20, end = Spacing.S16, top = Spacing.S5, bottom = Spacing.S5),
    ) {
        Icon(icon, null, tint = colors.icon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.S18))
        Text(stringResource(label), color = colors.title, fontSize = TextSize.S16, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
internal fun RenameDialog(current: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        modifier = Modifier.opensFrom(OpenFrom.Center),
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
