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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.library.LibraryRepository
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.library.Thumbnail
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
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
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .padding(start = 6.dp, top = 62.dp, bottom = 8.dp)
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
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(27.dp)
                        .clip(CircleShape)
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
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.field)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.quick_switch_pin),
                            color = colors.title,
                            fontSize = 14.sp,
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
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
                    )
                }
                items(recents, key = { it.id }) { entry ->
                    val info = entry.document ?: return@items
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                state.quickSwitchOpen = false
                                if (entry.id != currentId) actions.onOpenDocument(entry.id)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Thumbnail(entry.id, info, repository.thumbnails, Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            entry.name,
                            color = if (entry.id == currentId) colors.accent else colors.title,
                            fontSize = 15.sp,
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
