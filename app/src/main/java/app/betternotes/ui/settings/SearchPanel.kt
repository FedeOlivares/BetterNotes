package app.betternotes.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.NoteEditor
import app.betternotes.core.search.TextMatch
import app.betternotes.core.search.TextSource
import app.betternotes.export.exporter
import app.betternotes.search.DocumentSearch
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.opensFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TYPING_PAUSE_MS = 250L
private const val THUMBNAIL_PX = 100
private const val SNIPPET_CHARS = 60

/**
 * The search panel: a field, and a column down the right edge of the pages that have the text, in the
 * style of the quick switch panel. Choosing a page, or stepping with the arrows, takes the canvas to
 * the match and marks all of them.
 *
 * Handwriting and typed text come from the index, which lags edits by a second or so and by however
 * long recognition takes. PDF text is searched directly in the file, so it is exact and complete. The
 * index needs all the words on one line. A PDF phrase that wraps is found by the other search.
 */
@Composable
internal fun SearchPanel(
    editor: NoteEditor,
    search: DocumentSearch?,
    canvas: CanvasView,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    val pages = remember { editor.document.pages.toList() }
    val pageIndex = remember { pages.withIndex().associate { (index, page) -> page.id to index } }
    val exporter = remember { editor.exporter() }
    val worker = remember { Dispatchers.IO.limitedParallelism(1) }
    val focus = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var indexMatches by remember { mutableStateOf(emptyList<TextMatch>()) }
    var snippets by remember { mutableStateOf(emptyMap<Long, String>()) }
    val pdfMatches = remember { mutableStateListOf<TextMatch>() }
    var indexDone by remember { mutableStateOf(true) }
    var pdfDone by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf<TextMatch?>(null) }
    val generation = remember { intArrayOf(0) }
    val thumbnails = remember { mutableStateMapOf<Long, android.graphics.Bitmap>() }

    val matches = remember(indexMatches, pdfMatches.size) {
        (indexMatches + pdfMatches).sortedWith(
            compareBy<TextMatch>({ pageIndex[it.pageId] ?: Int.MAX_VALUE }, { it.rects.firstOrNull()?.top ?: 0f }, { it.rects.firstOrNull()?.left ?: 0f }),
        )
    }
    val searching = !indexDone || !pdfDone

    fun jump(match: TextMatch) {
        current = match
        canvas.reveal(match)
    }

    fun step(direction: Int) {
        if (matches.isEmpty()) return
        val at = matches.indexOf(current)
        val next = if (at < 0) 0 else Math.floorMod(at + direction, matches.size)
        jump(matches[next])
    }

    fun start(text: String) {
        val mine = ++generation[0]
        editor.cancelPdfSearch()
        pdfMatches.clear()
        indexMatches = emptyList()
        snippets = emptyMap()
        current = null
        if (text.isBlank()) {
            indexDone = true
            pdfDone = true
            return
        }
        indexDone = search == null
        pdfDone = false
        search?.search(text, pages) { hits ->
            if (mine != generation[0]) return@search
            // Printed text of a PDF comes from the file search below, with exact rectangles for each word.
            val found = hits.filter { it.source != TextSource.PRINTED }
            indexMatches = found.map { TextMatch(it.pageId, listOf(it.bounds)) }
            snippets = found.groupBy { it.pageId }.mapValues { (_, list) -> list.first().text.take(SNIPPET_CHARS) }
            indexDone = true
        }
        editor.searchPdfText(
            text,
            onMatches = { if (mine == generation[0]) pdfMatches += it },
            onFinished = { if (mine == generation[0]) pdfDone = true },
        )
    }

    LaunchedEffect(query) {
        delay(TYPING_PAUSE_MS)
        start(query)
    }
    LaunchedEffect(Unit) {
        search?.refresh(pages)
        focus.requestFocus()
    }
    // Results arrive in pieces, so the first one found is where the canvas goes.
    LaunchedEffect(matches) {
        if (matches.isNotEmpty() && (current == null || current !in matches)) jump(matches[0])
        canvas.showMatches(matches, current)
    }
    LaunchedEffect(current) { canvas.showMatches(matches, current) }
    DisposableEffect(Unit) {
        onDispose {
            generation[0]++
            editor.cancelPdfSearch()
            canvas.showMatches(emptyList())
        }
    }

    val shape = RoundedCornerShape(32.dp)
    Column(modifier.opensFrom(OpenFrom.Center).shadow(16.dp, shape, clip = false).clip(shape).background(colors.sheet).imePadding()) {
        Box(Modifier.fillMaxWidth().height(80.dp)) {
            Text(stringResource(R.string.search_title), color = colors.title, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
            CloseButton(onClose, Modifier.align(Alignment.CenterEnd).padding(end = 22.dp))
        }
        SearchField(query, { query = it }, focus, onPrevious = { step(-1) }, onNext = { step(1) }, hasMatches = matches.isNotEmpty())
        val pending = search?.pendingLines ?: 0
        val note = when {
            query.isBlank() -> null
            searching && matches.isEmpty() -> stringResource(R.string.search_searching)
            matches.isEmpty() -> stringResource(R.string.search_none)
            pending > 0 -> stringResource(R.string.search_pending)
            else -> null
        }
        if (note != null) {
            Text(note, color = colors.secondary, fontSize = 15.sp, modifier = Modifier.padding(start = 31.dp, end = 31.dp, top = 16.dp))
        }
        val groups = remember(matches) { matches.groupBy { it.pageId }.entries.toList() }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(groups, key = { it.key }) { (pageId, found) ->
                LaunchedEffect(pageId) {
                    if (pageId !in thumbnails) {
                        val index = pageIndex[pageId] ?: return@LaunchedEffect
                        val made = withContext(worker) {
                            try {
                                exporter.thumbnail(index, THUMBNAIL_PX)
                            } catch (_: IOException) {
                                null
                            }
                        }
                        if (made != null) thumbnails[pageId] = made
                    }
                }
                PageHitRow(
                    page = (pageIndex[pageId] ?: 0) + 1,
                    count = found.size,
                    snippet = snippets[pageId],
                    selected = current?.pageId == pageId,
                    thumbnail = thumbnails[pageId],
                ) { jump(found[0]) }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasMatches: Boolean,
) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.inset)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Icon(SettingsIcons.Search, null, tint = colors.secondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value,
            onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 18.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(stringResource(R.string.search_hint), color = colors.secondary, fontSize = 18.sp)
                inner()
            },
        )
        if (hasMatches) {
            RoundIcon(SettingsIcons.Up, 20.dp, stringResource(R.string.search_previous), onPrevious)
            RoundIcon(SettingsIcons.Down, 20.dp, stringResource(R.string.search_next), onNext)
        }
    }
}

@Composable
private fun PageHitRow(page: Int, count: Int, snippet: String?, selected: Boolean, thumbnail: android.graphics.Bitmap?, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)).background(colors.inset)) {
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.search_page, page),
                color = if (selected) colors.accent else colors.title,
                fontSize = 17.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(pluralStringResource(R.plurals.search_matches, count, count), snippet).joinToString(" · "),
                color = colors.secondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 65.dp).height(1.dp).background(colors.divider.copy(alpha = 0.25f)))
}

@Composable
internal fun SearchHost(editor: NoteEditor, search: DocumentSearch?, canvas: CanvasView, onDismiss: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        SearchPanel(
            editor,
            search,
            canvas,
            onDismiss,
            Modifier.padding(top = 80.dp, end = 22.dp, bottom = 22.dp).width(440.dp).fillMaxHeight(),
        )
    }
}
