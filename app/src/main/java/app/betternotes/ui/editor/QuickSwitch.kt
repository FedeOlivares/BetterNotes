package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.library.LibraryRepository
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.library.Thumbnail
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.opensFrom

private const val RECENT_LIMIT = 40

/** The list of recent notes under the stack button. A tap opens one. */
@Composable
internal fun QuickSwitch(state: EditorState, actions: EditorActions, notices: Notices) {
    val colors = LocalEditorColors.current
    val context = LocalContext.current
    val repository = remember { LibraryRepository.get(context) }
    val library by repository.library.collectAsState()
    val recents = remember(library) {
        library?.entries.orEmpty()
            .filter { it.document != null && it.trashedAtMs == null }
            .sortedByDescending { it.modifiedAtMs }
            .take(RECENT_LIMIT)
    }
    val currentId = actions.documentId()
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                state.quickSwitchOpen = false
            },
    ) {
        val shape = AppShapes.Rounded22
        Column(
            Modifier
                .padding(start = Spacing.S6, top = 62.dp, bottom = Spacing.S8)
                .opensFrom(OpenFrom.TopStart)
                .width(PanelWidth)
                .masksInk(state, "quick-switch")
                .shadow(12.dp, shape, clip = false)
                .clip(shape)
                .background(colors.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(Modifier.fillMaxWidth().height(54.dp)) {
                Text(
                    stringResource(R.string.quick_switch_title),
                    color = colors.title,
                    fontSize = TextSize.S16,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Spacing.S14)
                        .size(27.dp)
                        .clip(AppShapes.Circle)
                        .background(colors.closeButton)
                        .clickable(role = Role.Button) { state.quickSwitchOpen = false },
                ) {
                    Icon(EditorIcons.Close, stringResource(R.string.panel_close), tint = colors.label, modifier = Modifier.size(18.dp))
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = Spacing.S14)
                            .fillMaxWidth()
                            .clip(AppShapes.Rounded14)
                            .background(colors.field)
                            .padding(horizontal = Spacing.S16, vertical = Spacing.S16),
                    ) {
                        Text(
                            stringResource(R.string.quick_switch_pin),
                            color = colors.title,
                            fontSize = TextSize.S14,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            EditorIcons.Info,
                            stringResource(R.string.panel_info),
                            tint = colors.label,
                            modifier = Modifier.size(20.dp).clickable { notices.notBuilt() },
                        )
                    }
                    Text(
                        stringResource(R.string.quick_switch_recents),
                        color = colors.label,
                        fontSize = TextSize.S14,
                        modifier = Modifier.padding(start = Spacing.S20, top = Spacing.S20, bottom = Spacing.S10),
                    )
                }
                items(recents, key = { it.id }) { entry ->
                    val info = entry.document ?: return@items
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = Spacing.S14)
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                state.quickSwitchOpen = false
                                if (entry.id != currentId) actions.onOpenDocument(entry.id)
                            }
                            .padding(horizontal = Spacing.S14, vertical = Spacing.S8),
                    ) {
                        Thumbnail(entry.id, info, repository.thumbnails, Modifier.size(40.dp).clip(AppShapes.Rounded4))
                        Spacer(Modifier.width(Spacing.S12))
                        Text(
                            entry.name,
                            color = if (entry.id == currentId) colors.accent else colors.title,
                            fontSize = TextSize.S15,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            EditorIcons.More,
                            stringResource(R.string.editor_more),
                            tint = colors.icon,
                            modifier = Modifier.size(24.dp).clickable { notices.notBuilt() },
                        )
                    }
                }
            }
        }
    }
}
