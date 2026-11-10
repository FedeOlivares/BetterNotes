package app.betternotes.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.model.Page
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.opensFrom
import kotlinx.coroutines.yield

/** Every page of the document as a thumbnail, to go to, add to, or rearrange by holding and dragging. */
@Composable
internal fun AllPagesPanel(state: EditorState, notices: Notices) {
    val colors = LocalEditorColors.current
    val grid = rememberLazyGridState()
    val pages = remember(state.historyVersion) { state.pages() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    val latestDragging by rememberUpdatedState(dragging)
    Box(
        Modifier
            .fillMaxSize()
            .background(SCRIM)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                state.pagesOpen = false
            },
    ) {
        Column(
            Modifier
                .opensFrom(OpenFrom.Center)
                .fillMaxSize()
                .padding(horizontal = PANEL_SIDE_MARGIN, vertical = PANEL_END_MARGIN)
                .clip(PanelShape)
                .background(colors.panel)
                // Taps on the panel stay on the panel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(62.dp).padding(start = 16.dp, end = 10.dp),
            ) {
                Icon(EditorIcons.PageGrid, null, tint = colors.iconDisabled, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pages_all), color = colors.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Icon(EditorIcons.ChevronDown, null, tint = colors.icon, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                if (state.canAddPages) {
                    ToolbarIcon(EditorIcons.Plus, stringResource(R.string.pages_add)) { state.addPage(pages.lastIndex) }
                }
                ToolbarIcon(EditorIcons.CheckCircle, stringResource(R.string.pages_select), onClick = notices::notBuilt)
                ToolbarIcon(EditorIcons.Undo, stringResource(R.string.editor_undo), enabled = state.canUndo, onClick = state::undo)
                ToolbarIcon(EditorIcons.Redo, stringResource(R.string.editor_redo), enabled = state.canRedo, onClick = state::redo)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(27.dp)
                        .clip(CircleShape)
                        .background(colors.closeButton)
                        .clickable(role = Role.Button) { state.pagesOpen = false },
                ) {
                    Icon(EditorIcons.Close, stringResource(R.string.panel_close), tint = colors.label, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.panelDivider))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(THUMBNAIL_WIDTH),
                state = grid,
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pages.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { at ->
                                dragging = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    at.x.toInt() in item.offset.x..item.offset.x + item.size.width &&
                                        at.y.toInt() in item.offset.y..item.offset.y + item.size.height
                                }?.index?.takeUnless { it == 0 && pages.first().isCover }
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                            onDrag = { change, _ ->
                                change.consume()
                                val from = latestDragging ?: return@detectDragGesturesAfterLongPress
                                val at = change.position
                                val over = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    at.x.toInt() in item.offset.x..item.offset.x + item.size.width &&
                                        at.y.toInt() in item.offset.y..item.offset.y + item.size.height
                                }?.index
                                // The cover is locked in place, so nothing is dropped before it.
                                if (over != null && over != from && !(over == 0 && pages.first().isCover)) {
                                    state.movePage(from, over)
                                    dragging = over
                                }
                            },
                        )
                    },
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    PageTile(state, page, index, isCurrent = index == state.currentPage, isDragged = index == dragging)
                }
            }
        }
    }
}

@Composable
private fun PageTile(state: EditorState, page: Page, index: Int, isCurrent: Boolean, isDragged: Boolean) {
    val colors = LocalEditorColors.current
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val thumbnail by produceState<Bitmap?>(null, page.id, state.historyVersion) {
        // A long document paints its pages a few at a time, not all in one frame.
        yield()
        value = state.thumbnail(page.id, THUMBNAIL_PX)
    }
    val pdf by produceState<Bitmap?>(null, page.id) { value = state.pdfThumbnail(page.id, THUMBNAIL_PX) }
    val size = page.size
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (size != null) size.widthPt / size.heightPt else 1f)
                .clip(shape)
                .background(Color(page.background.color))
                .then(if (isCurrent || isDragged) Modifier.border(2.dp, colors.accent, shape) else Modifier)
                .clickable(role = Role.Button) { state.goToPage(index) },
        ) {
            pdf?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize()) }
            thumbnail?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize()) }
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(role = Role.Button) { menu = true }.padding(8.dp),
            ) {
                Text("${index + 1 - state.coverOffset}", color = colors.title, fontSize = 13.sp)
                Icon(EditorIcons.ChevronDown, stringResource(R.string.editor_more), tint = colors.icon, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (state.canAddPages) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pages_add_after), fontSize = 14.sp) },
                        onClick = {
                            menu = false
                            state.addPage(index)
                        },
                    )
                }
                if (state.pageCount > 1 && !page.isCover) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pages_delete), fontSize = 14.sp) },
                        onClick = {
                            menu = false
                            state.deletePage(index)
                        },
                    )
                }
            }
        }
    }
}

private val SCRIM = Color(0x66000000)
private val PANEL_SIDE_MARGIN = 86.dp
private val PANEL_END_MARGIN = 29.dp
private val THUMBNAIL_WIDTH = 150.dp
private const val THUMBNAIL_PX = 420
