package app.betternotes.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.betternotes.R
import app.betternotes.canvas.physicalDpi
import app.betternotes.core.library.Library
import app.betternotes.core.library.LibraryEntry
import app.betternotes.core.library.LibrarySort
import app.betternotes.core.library.LibraryView
import app.betternotes.library.LibraryLayout
import app.betternotes.library.LibraryPreferences
import app.betternotes.library.LibraryRepository
import app.betternotes.pdf.PdfImportResult
import app.betternotes.settings.SettingsRepository
import app.betternotes.ui.settings.SettingsScreen
import app.betternotes.ui.settings.SettingsTheme
import app.betternotes.ui.theme.AppColors
import app.betternotes.ui.theme.LocalAppColors
import app.betternotes.ui.theme.ThemeMode
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.opensFrom
import kotlinx.coroutines.launch
import java.util.Calendar

private val MinCardWidth = 300.dp
private val SideMargin = 22.dp
private val CardGap = 34.dp

/** Where the first row of tiles starts, in the reference. */
private val HeaderHeight = 324.dp

private val Top = LibraryView.InFolder(null)

private const val LATE_NIGHT_END_HOUR = 5
private const val BREADCRUMB_FOLDERS = 3

private val BreadcrumbMaxWidth = 620.dp
private val RowGap = 12.dp
private val MenuShape = RoundedCornerShape(24.dp)

/** From the circled "..." to the menu's top right corner, measured on the reference. */
private val MenuOffsetX = 19.dp
private val MenuOffsetY = 75.dp

/** Everything a tile or menu can ask for that needs a dialog first. */
private sealed interface PendingDialog {
    data object NewFolder : PendingDialog
    class Rename(val entry: LibraryEntry) : PendingDialog
    class Move(val ids: Set<Long>) : PendingDialog
    class Tags(val entry: LibraryEntry) : PendingDialog
    class DeleteForever(val entry: LibraryEntry) : PendingDialog
    data object EmptyBin : PendingDialog
    data object AskName : PendingDialog
    class PdfPassword(val uri: Uri) : PendingDialog
}

private val ViewSaver = Saver<LibraryView, String>(
    save = { view ->
        when (view) {
            is LibraryView.InFolder -> "f" + (view.id?.toString() ?: "")
            LibraryView.Starred -> "s"
            is LibraryView.Tagged -> "t" + view.tag
            LibraryView.Bin -> "b"
            is LibraryView.Search -> "f"
        }
    },
    restore = { saved ->
        val rest = saved.drop(1)
        when (saved[0]) {
            's' -> LibraryView.Starred
            't' -> LibraryView.Tagged(rest)
            'b' -> LibraryView.Bin
            else -> LibraryView.InFolder(rest.toLongOrNull())
        }
    },
)

@Composable
internal fun LibraryScreen(
    repository: LibraryRepository,
    preferences: LibraryPreferences,
    settings: SettingsRepository,
    onOpenDocument: (id: Long, startAction: String?) -> Unit,
) {
    val colors = LocalAppColors.current
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    val library by repository.library.collectAsStateWithLifecycle()
    var shelf by rememberSaveable(stateSaver = ViewSaver) { mutableStateOf<LibraryView>(Top) }
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<PendingDialog?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        nowMs = System.currentTimeMillis()
        onPauseOrDispose {}
    }

    // There is no account to take a name from, so the first run asks for one.
    LaunchedEffect(Unit) {
        if (preferences.userName == null) dialog = PendingDialog.AskName
    }

    // Leaves a folder that went away, such as one found by a scan to have lost its files.
    val openFolder = (shelf as? LibraryView.InFolder)?.id?.let { library?.get(it) }
    LaunchedEffect(shelf, library) {
        val current = library ?: return@LaunchedEffect
        val id = (shelf as? LibraryView.InFolder)?.id ?: return@LaunchedEffect
        if (current[id]?.trashedAtMs != null || current[id] == null) shelf = Top
    }

    val view = query?.let { LibraryView.Search(it) } ?: shelf
    // Whatever was selected belongs to the items being shown.
    LaunchedEffect(view) { selection = null }
    BackHandler(view != Top || selection != null) {
        when {
            selection != null -> selection = null
            query != null -> query = null
            shelf is LibraryView.InFolder -> shelf = LibraryView.InFolder(openFolder?.parentId)
            else -> shelf = Top
        }
    }

    val folderForNewItems = (shelf as? LibraryView.InFolder)?.id
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    BackHandler(adding) { adding = false }
    val flows = rememberNewItemActions(
        repository,
        settings,
        folderForNewItems,
        onOpenDocument,
        onNewFolder = { dialog = PendingDialog.NewFolder },
        importPdf = { uri, password ->
            scope.launch {
                when (repository.importPdf(context.contentResolver, uri, folderForNewItems, password)) {
                    is PdfImportResult.Imported -> Unit
                    PdfImportResult.NeedsPassword -> dialog = PendingDialog.PdfPassword(uri)
                    is PdfImportResult.Failed -> showImportFailed(context)
                }
            }
        },
    )

    Box(Modifier.fillMaxSize().background(colors.background)) {
        TopGlow(colors)
        val current = library
        val entries = remember(current, view, preferences.sort) {
            current?.list(view, preferences.sort).orEmpty()
        }
        val listed = preferences.layout == LibraryLayout.LIST
        LazyVerticalGrid(
            columns = if (listed) GridCells.Fixed(1) else GridCells.Adaptive(MinCardWidth),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            contentPadding = PaddingValues(
                start = SideMargin,
                end = SideMargin,
                bottom = 110.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Header(
                    shelf = shelf,
                    query = query,
                    onQuery = { query = it },
                    onShelf = {
                        query = null
                        shelf = it
                    },
                    library = current,
                    preferences = preferences,
                    onEmptyBin = { dialog = PendingDialog.EmptyBin },
                    onSelect = { selection = emptySet() },
                    onSettings = { showingSettings = true },
                    nowMs = nowMs,
                )
            }
            items(entries, key = { it.id }, contentType = { if (listed) "row" else it.isFolder }) { entry ->
                val inBin = view == LibraryView.Bin
                val actions: @Composable () -> List<CardAction> = {
                    cardActions(entry, inBin, repository) { dialog = it }
                }
                val picked = selection?.let { entry.id in it }
                val open = {
                    val chosen = selection
                    when {
                        chosen != null -> selection = if (entry.id in chosen) chosen - entry.id else chosen + entry.id
                        inBin -> Unit
                        entry.isFolder -> {
                            query = null
                            shelf = LibraryView.InFolder(entry.id)
                        }
                        else -> onOpenDocument(entry.id, null)
                    }
                }
                val count = current?.itemCount(entry.id) ?: 0
                Box(Modifier.padding(bottom = if (listed) RowGap else CardGap)) {
                    val info = entry.document
                    when {
                        listed -> LibraryRow(entry, count, nowMs, repository.thumbnails, picked, actions, open)
                        info != null -> DocumentCard(entry, info, nowMs, repository.thumbnails, picked, actions, open)
                        else -> FolderCard(entry, count, picked, actions, open)
                    }
                }
            }
        }
        BottomScrim(colors, Modifier.align(Alignment.BottomCenter))
        selection?.let { chosen ->
            SelectionBar(
                chosen,
                view == LibraryView.Bin,
                repository,
                onMove = { dialog = PendingDialog.Move(chosen) },
                onDone = { selection = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp),
            )
        }
        HomePill(
            selected = view == Top,
            onClick = {
                query = null
                shelf = Top
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = SideMargin, bottom = 18.dp),
        )
        if (adding) {
            NewItemPanel(
                flows.panel,
                onDismiss = { adding = false },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = SideMargin, bottom = 96.dp),
            )
        }
        AddButton(
            onClick = { adding = !adding },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = SideMargin, bottom = 17.dp),
        )
    }

    ShowDialog(dialog, library, repository, preferences, folderForNewItems, flows.importPdfWithPassword) {
        dialog = null
        // A move ends the selection it was made for.
        if (selection?.isNotEmpty() == true) selection = null
    }
    if (showingSettings) {
        val current by settings.settings.collectAsStateWithLifecycle()
        SettingsTheme(preferences.theme == ThemeMode.DARK) {
            SettingsScreen(current, physicalDpi(context), settings::update, { showingSettings = false })
        }
    }
}

@Composable
private fun ShowDialog(
    pending: PendingDialog?,
    library: Library?,
    repository: LibraryRepository,
    preferences: LibraryPreferences,
    folderForNewItems: Long?,
    importPdf: (Uri, String) -> Unit,
    dismiss: () -> Unit,
) {
    when (pending) {
        null -> Unit
        PendingDialog.NewFolder -> TextDialog(
            stringResource(R.string.library_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.library_create),
            onConfirm = {
                repository.createFolder(it, folderForNewItems)
                dismiss()
            },
            onDismiss = dismiss,
        )
        is PendingDialog.Rename -> TextDialog(
            stringResource(R.string.library_rename),
            initial = pending.entry.name,
            confirmLabel = stringResource(R.string.library_save),
            onConfirm = {
                repository.rename(pending.entry, it)
                dismiss()
            },
            onDismiss = dismiss,
        )
        is PendingDialog.Move -> MoveDialog(
            canMoveToTop = pending.ids.any { library?.get(it)?.parentId != null },
            targets = library?.moveTargets(pending.ids).orEmpty(),
            onMove = { folder ->
                pending.ids.forEach { repository.move(it, folder) }
                dismiss()
            },
            onDismiss = dismiss,
        )
        is PendingDialog.Tags -> TagsDialog(
            known = library?.tags.orEmpty(),
            initial = pending.entry.tags,
            onConfirm = {
                repository.setTags(pending.entry.id, it)
                dismiss()
            },
            onDismiss = dismiss,
        )
        is PendingDialog.DeleteForever -> ConfirmDialog(
            stringResource(R.string.library_delete_confirm, pending.entry.name),
            stringResource(R.string.library_delete_forever),
            onConfirm = {
                repository.deleteForever(pending.entry.id)
                dismiss()
            },
            onDismiss = dismiss,
        )
        PendingDialog.EmptyBin -> ConfirmDialog(
            stringResource(R.string.library_empty_bin_confirm),
            stringResource(R.string.library_empty_bin),
            onConfirm = {
                repository.emptyBin()
                dismiss()
            },
            onDismiss = dismiss,
        )
        PendingDialog.AskName -> TextDialog(
            stringResource(R.string.library_ask_name),
            initial = preferences.userName.orEmpty(),
            confirmLabel = stringResource(R.string.library_save),
            onConfirm = {
                preferences.updateUserName(it)
                dismiss()
            },
            onDismiss = dismiss,
        )
        is PendingDialog.PdfPassword -> TextDialog(
            stringResource(R.string.library_pdf_password),
            initial = "",
            confirmLabel = stringResource(R.string.library_import_pdf),
            onConfirm = {
                importPdf(pending.uri, it)
                dismiss()
            },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun cardActions(
    entry: LibraryEntry,
    inBin: Boolean,
    repository: LibraryRepository,
    ask: (PendingDialog) -> Unit,
): List<CardAction> = if (inBin) {
    listOf(
        CardAction(stringResource(R.string.library_restore)) { repository.restore(entry.id) },
        CardAction(stringResource(R.string.library_delete_forever)) { ask(PendingDialog.DeleteForever(entry)) },
    )
} else {
    val star = if (entry.starred) R.string.library_unstar else R.string.library_star
    listOf(
        CardAction(stringResource(R.string.library_rename)) { ask(PendingDialog.Rename(entry)) },
        CardAction(stringResource(star)) { repository.setStarred(entry.id, !entry.starred) },
        CardAction(stringResource(R.string.library_tags)) { ask(PendingDialog.Tags(entry)) },
        CardAction(stringResource(R.string.library_move_to)) { ask(PendingDialog.Move(setOf(entry.id))) },
        CardAction(stringResource(R.string.library_move_to_bin)) { repository.trash(entry.id) },
    )
}

/** Laid out from the top of the screen, at the positions measured on the reference. */
@Composable
private fun Header(
    shelf: LibraryView,
    query: String?,
    onQuery: (String?) -> Unit,
    onShelf: (LibraryView) -> Unit,
    library: Library?,
    preferences: LibraryPreferences,
    onEmptyBin: () -> Unit,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    nowMs: Long,
) {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
        Text(
            greeting(preferences.userName, nowMs),
            color = colors.greeting,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 41.sp,
            lineHeight = 56.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(y = 144.dp),
        )
        Text(
            stringResource(if (isLateNight(nowMs)) R.string.library_subtitle_night else R.string.library_subtitle),
            color = colors.subtitle,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.05.em,
            maxLines = 1,
            modifier = Modifier.offset(y = 206.dp),
        )
        Row(Modifier.align(Alignment.TopEnd).offset(y = 62.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsButton(onSettings)
            Spacer(Modifier.width(12.dp))
            SearchControl(query, onQuery, Modifier)
        }
        FilterRow(shelf, query != null, onShelf, library, Modifier.offset(y = 257.dp))
        OptionsMenu(
            shelf,
            preferences,
            onEmptyBin,
            onSelect,
            Modifier.align(Alignment.TopEnd).offset(x = (-11).dp, y = 257.dp),
        )
    }
}

private fun hourOf(nowMs: Long) = Calendar.getInstance().apply { timeInMillis = nowMs }[Calendar.HOUR_OF_DAY]

/** The reference greets differently at 2:05 AM than at 8:21 PM. Where the night ends is a guess. */
private fun isLateNight(nowMs: Long) = hourOf(nowMs) < LATE_NIGHT_END_HOUR

@Composable
private fun greeting(name: String?, nowMs: Long): String {
    val hour = hourOf(nowMs)
    val timeOfDay = stringResource(
        when {
            isLateNight(nowMs) -> R.string.library_good_night
            hour < 12 -> R.string.library_good_morning
            hour < 18 -> R.string.library_good_afternoon
            else -> R.string.library_good_evening
        },
    )
    return if (name == null) timeOfDay else stringResource(R.string.library_greeting_named, timeOfDay, name)
}

/** Settings sits beside search, in the same round style. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.5.dp, colors.border, CircleShape),
    ) {
        IconButton(LibraryIcons.Settings, stringResource(R.string.library_settings), colors.title, 66.dp, onClick)
    }
}

/** The reference's pill held search and the account's photo. With no account, only search is left. */
@Composable
private fun SearchControl(query: String?, onQuery: (String?) -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.border, RoundedCornerShape(33.dp)),
    ) {
        if (query != null) {
            BasicTextField(
                value = query,
                onValueChange = { onQuery(it) },
                singleLine = true,
                textStyle = TextStyle(color = colors.title, fontSize = 19.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.padding(start = 24.dp).size(280.dp, 28.dp),
            )
            IconButton(LibraryIcons.Close, stringResource(R.string.library_close_search), colors.strongIcon, 66.dp) {
                onQuery(null)
            }
        } else {
            IconButton(LibraryIcons.Search, stringResource(R.string.library_search), colors.title, 66.dp) { onQuery("") }
        }
    }
}

@Composable
private fun FilterRow(
    shelf: LibraryView,
    searching: Boolean,
    onShelf: (LibraryView) -> Unit,
    library: Library?,
    modifier: Modifier,
) {
    val colors = LocalAppColors.current
    var tagsOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.height(49.dp)) {
        val allSelected = !searching && shelf is LibraryView.InFolder
        val folderId = (shelf as? LibraryView.InFolder)?.id
        val path = if (!searching && folderId != null) library?.pathTo(folderId).orEmpty() else emptyList()
        if (path.isNotEmpty()) {
            Breadcrumb(path, onShelf)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp, 49.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .then(if (allSelected) Modifier.background(colors.accentContainer) else Modifier)
                    .clickable { onShelf(Top) },
            ) {
                Text(
                    stringResource(R.string.library_filter_all),
                    color = if (allSelected) colors.onAccentContainer else colors.icon,
                    fontSize = 20.sp,
                )
            }
        }
        FilterIcon(LibraryIcons.Star, R.string.library_filter_starred, !searching && shelf == LibraryView.Starred) {
            onShelf(LibraryView.Starred)
        }
        Box {
            val tagged = (shelf as? LibraryView.Tagged)?.tag
            FilterIcon(LibraryIcons.Tag, R.string.library_filter_tags, !searching && tagged != null) { tagsOpen = true }
            DropdownMenu(expanded = tagsOpen, onDismissRequest = { tagsOpen = false }) {
                val tags = library?.tags.orEmpty()
                if (tags.isEmpty()) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.library_no_tags)) }, onClick = { tagsOpen = false })
                }
                for (tag in tags) {
                    DropdownMenuItem(
                        text = { Text(tag) },
                        onClick = {
                            tagsOpen = false
                            onShelf(LibraryView.Tagged(tag))
                        },
                    )
                }
            }
        }
        FilterIcon(LibraryIcons.Bin, R.string.library_filter_bin, !searching && shelf == LibraryView.Bin) {
            onShelf(LibraryView.Bin)
        }
    }
}

/** Inside a folder, in place of "All": a house, then each folder down to this one. */
@Composable
private fun Breadcrumb(path: List<LibraryEntry>, onShelf: (LibraryView) -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(49.dp)
            .widthIn(max = BreadcrumbMaxWidth)
            .clip(RoundedCornerShape(25.dp))
            .background(colors.accentContainer)
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            LibraryIcons.Home,
            stringResource(R.string.library_home),
            tint = colors.onAccentContainer,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onShelf(Top) },
        )
        // A deep path keeps its last folders, which are the ones that say where this is.
        for (folder in path.takeLast(BREADCRUMB_FOLDERS)) {
            Icon(LibraryIcons.Chevron, null, tint = colors.onAccentContainer, modifier = Modifier.padding(horizontal = 4.dp).size(20.dp))
            Text(
                folder.name,
                color = colors.onAccentContainer,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable { onShelf(LibraryView.InFolder(folder.id)) },
            )
        }
    }
}

@Composable
private fun FilterIcon(icon: ImageVector, label: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = if (selected) 4.dp else 0.dp, end = if (selected) 4.dp else 0.dp)
            .size(if (selected) 66.dp else 50.dp, 49.dp)
            .clip(RoundedCornerShape(25.dp))
            .then(if (selected) Modifier.background(colors.accentContainer) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            stringResource(label),
            tint = if (selected) colors.onAccentContainer else colors.icon,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Measured from `... on home.jpg`. Settings is added, since the avatar that led there is gone. */
@Composable
private fun OptionsMenu(
    shelf: LibraryView,
    preferences: LibraryPreferences,
    onEmptyBin: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    fun choose(action: () -> Unit): () -> Unit = {
        open = false
        action()
    }
    Box(modifier) {
        IconButton(LibraryIcons.MoreCircled, stringResource(R.string.library_options), colors.strongIcon, 49.dp) {
            open = true
        }
        // A popup starts a new composition, with the device's density, so the reference's scale is handed in.
        val scale = LocalDensity.current
        if (open) Popup(
            alignment = Alignment.TopEnd,
            offset = with(scale) { IntOffset(MenuOffsetX.roundToPx(), MenuOffsetY.roundToPx()) },
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true),
        ) { CompositionLocalProvider(LocalDensity provides scale) { Column(
            Modifier
                .opensFrom(OpenFrom.TopEnd)
                .width(274.dp)
                .padding(bottom = 12.dp)
                .shadow(12.dp, MenuShape)
                .background(colors.surface, MenuShape)
                .clip(MenuShape),
        ) {
            OptionRow(LibraryIcons.CheckCircle, stringResource(R.string.library_select), height = 64.dp, onClick = choose(onSelect))
            OptionDivider()
            val grid = preferences.layout == LibraryLayout.GRID
            OptionRow(
                LibraryIcons.GridView,
                stringResource(R.string.library_grid),
                chosen = grid,
                onClick = choose { preferences.updateLayout(LibraryLayout.GRID) },
            )
            OptionRow(
                LibraryIcons.ListView,
                stringResource(R.string.library_list),
                chosen = !grid,
                onClick = choose { preferences.updateLayout(LibraryLayout.LIST) },
            )
            OptionDivider()
            val byName = preferences.sort == LibrarySort.NAME
            OptionRow(
                LibraryIcons.SortDown,
                stringResource(R.string.library_sort_by),
                detail = stringResource(if (byName) R.string.library_sort_name else R.string.library_sort_modified),
                height = 64.dp,
                onClick = choose { preferences.updateSort(if (byName) LibrarySort.MODIFIED else LibrarySort.NAME) },
            )
            if (shelf == LibraryView.Bin) {
                OptionDivider()
                OptionRow(LibraryIcons.Bin, stringResource(R.string.library_empty_bin), onClick = choose(onEmptyBin))
            }
        } } }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    chosen: Boolean = false,
    height: Dp = 60.dp,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val tint = if (chosen) colors.onRaisedAccent else colors.title
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onClick)
            .padding(start = 17.dp, end = 18.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Column(Modifier.padding(start = 15.dp).weight(1f)) {
            Text(label, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (detail != null) Text(detail, color = colors.subtitle, fontSize = 16.sp, maxLines = 1)
        }
        if (chosen) Icon(LibraryIcons.Check, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun OptionDivider() {
    Box(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalAppColors.current.border),
    )
}

/** No screenshot shows selecting, so this is a plain bar. */
@Composable
private fun SelectionBar(
    chosen: Set<Long>,
    inBin: Boolean,
    repository: LibraryRepository,
    onMove: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalAppColors.current
    val onAll = { action: (Long) -> Unit ->
        chosen.forEach(action)
        onDone()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.pillBorder, RoundedCornerShape(33.dp))
            .padding(horizontal = 12.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.library_selected_count, chosen.size, chosen.size),
            color = colors.title,
            fontSize = 19.sp,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
        )
        if (chosen.isNotEmpty()) {
            if (inBin) {
                IconButton(LibraryIcons.Back, stringResource(R.string.library_restore), colors.title, 52.dp) {
                    onAll(repository::restore)
                }
            } else {
                IconButton(LibraryIcons.Star, stringResource(R.string.library_star), colors.title, 52.dp) {
                    onAll { repository.setStarred(it, true) }
                }
                IconButton(LibraryIcons.Folder, stringResource(R.string.library_move_to), colors.title, 52.dp, onMove)
                IconButton(LibraryIcons.Bin, stringResource(R.string.library_move_to_bin), colors.title, 52.dp) {
                    onAll(repository::trash)
                }
            }
        }
        IconButton(LibraryIcons.Close, stringResource(R.string.library_end_selection), colors.title, 52.dp, onDone)
    }
}

@Composable
private fun IconButton(icon: ImageVector, description: String, tint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/** The reference's pill also holds AI and an unidentified grid icon. Home is the only one kept so far. */
@Composable
private fun HomePill(selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(91.dp, 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.pillBorder, RoundedCornerShape(32.dp)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(69.dp, 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .then(if (selected) Modifier.background(colors.accentContainer) else Modifier)
                .clickable(onClick = onClick),
        ) {
            Icon(
                LibraryIcons.Home,
                stringResource(R.string.library_home),
                tint = if (selected) colors.onAccentContainer else colors.icon,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = onClick),
    ) {
        Icon(LibraryIcons.Plus, stringResource(R.string.library_add), tint = colors.onAccent, modifier = Modifier.size(32.dp))
    }
}

/** The blue glow and staggered dots along the top, fixed behind the scrolling tiles. */
@Composable
private fun TopGlow(colors: AppColors) {
    Canvas(Modifier.fillMaxWidth().height(180.dp).graphicsLayer {}) {
        drawRect(Brush.horizontalGradient(*colors.glowStops.toTypedArray()))
        val spacing = 18.6.dp.toPx()
        val points = ArrayList<Offset>()
        var row = 0
        var y = spacing / 2f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else spacing / 2f
            while (x < size.width) {
                points += Offset(x, y)
                x += spacing
            }
            y += spacing
            row++
        }
        drawPoints(points, PointMode.Points, colors.glowDot, 3.dp.toPx(), StrokeCap.Round)
        drawRect(
            Brush.verticalGradient(
                0.25f to Color.Transparent,
                1f to colors.background,
            ),
            size = Size(size.width, size.height),
        )
    }
}

/** Measured from the reference: nothing 200 units up, then quickly almost opaque at the bottom edge. */
@Composable
private fun BottomScrim(colors: AppColors, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                Brush.verticalGradient(
                    0f to colors.scrim.copy(alpha = 0f),
                    0.4f to colors.scrim.copy(alpha = 0.36f),
                    0.65f to colors.scrim.copy(alpha = 0.7f),
                    0.9f to colors.scrim.copy(alpha = 0.86f),
                    1f to colors.scrim.copy(alpha = 0.86f),
                ),
            ),
    )
}
