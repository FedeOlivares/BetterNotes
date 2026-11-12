package app.betternotes.ui.text

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.betternotes.R
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.TextItem
import app.betternotes.library.LibraryPreferences
import app.betternotes.library.LibraryRepository
import app.betternotes.ui.library.LibraryIcons
import app.betternotes.ui.requestHighestRefreshRate
import app.betternotes.ui.theme.AppFonts
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.AppTheme
import app.betternotes.ui.theme.LocalAppColors
import app.betternotes.ui.theme.ReferenceScale
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val AUTOSAVE_DELAY_MS = 1000L
private const val PAGE_MARGIN_PT = 48f

/**
 * A typed notebook: a title and one body of text. No screenshot shows Notewise's, so this is a plain
 * layout. The text is saved as the single text item on the document's page, so that the rest of the
 * app, such as its thumbnail and search, sees it as it sees any other item.
 */
class TextNoteActivity : ComponentActivity() {
    private lateinit var session: DocumentSession
    private var pageId = 0L
    private var saveJob: Job? = null

    /** What is typed but not yet on the page. Applying it once per save keeps undo from growing per letter. */
    private var pendingBody: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        val repository = LibraryRepository.get(this)
        session = DocumentSession.open(repository.documentStore(intent.getLongExtra(EXTRA_DOCUMENT_ID, 0L)))
        pageId = session.document.pages[0].id
        val preferences = LibraryPreferences.get(this)
        val initialBody = (session.content(pageId).toList().firstOrNull() as? TextItem)?.text.orEmpty()
        setContent {
            val mode = preferences.theme
            LaunchedEffect(mode) {
                val bars = if (mode == ThemeMode.DARK) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            AppTheme(mode) {
                ReferenceScale {
                    TextNoteScreen(session.document.title, initialBody, ::changeTitle, ::changeBody, ::finish)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        saveJob?.cancel()
        applyPendingBody()
        // The app may be killed from here on, and the library reads what this writes.
        val save = session.prepareSave()
        if (save != null) {
            runBlocking(Dispatchers.IO) { save.write() }
            session.finishSave(save)
        }
        LibraryRepository.get(this).refresh()
    }

    private fun changeTitle(title: String) {
        session.document.title = title
        scheduleSave()
    }

    private fun changeBody(text: String) {
        pendingBody = text
        scheduleSave()
    }

    private fun applyPendingBody() {
        val text = pendingBody ?: return
        pendingBody = null
        val content = session.content(pageId)
        val current = content.toList().firstOrNull() as? TextItem
        val edit = if (current != null) {
            ItemsEdit.replace(pageId, content, intArrayOf(0), listOf(current.copy(text = text)))
        } else {
            val size = session.document.pages[0].size!!
            val frame = Frame(PAGE_MARGIN_PT, PAGE_MARGIN_PT, size.widthPt - PAGE_MARGIN_PT * 2, size.heightPt - PAGE_MARGIN_PT * 2)
            ItemsEdit.addOnTop(pageId, content, TextItem(IdSource.Random.next(), frame, text))
        }
        session.history.perform(edit, session)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            applyPendingBody()
            val save = session.prepareSave() ?: return@launch
            withContext(Dispatchers.IO) { save.write() }
            session.finishSave(save)
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"
    }
}

@Composable
private fun TextNoteScreen(
    initialTitle: String,
    initialBody: String,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    val cursor = SolidColor(colors.accent)
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.S22, end = Spacing.S22, top = 62.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(66.dp).clip(AppShapes.Circle).background(colors.surface).clickable(onClick = onBack),
            ) {
                Icon(LibraryIcons.Back, stringResource(R.string.library_back), tint = colors.title, modifier = Modifier.size(30.dp))
            }
            HintedField(
                title,
                stringResource(R.string.text_note_title),
                TextStyle(colors.greeting, TextSize.S41, FontWeight.SemiBold, fontFamily = AppFonts.Display),
                cursor,
                Modifier.padding(start = Spacing.S20).weight(1f),
            ) {
                title = it
                onTitle(it)
            }
        }
        HintedField(
            body,
            stringResource(R.string.text_note_body),
            TextStyle(colors.title, TextSize.S24, lineHeight = TextSize.S34),
            cursor,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 108.dp, end = 108.dp, top = Spacing.S28, bottom = 60.dp),
        ) {
            body = it
            onBody(it)
        }
    }
}

@Composable
private fun HintedField(
    value: String,
    hint: String,
    style: TextStyle,
    cursor: SolidColor,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    BasicTextField(
        value,
        onChange,
        textStyle = style,
        cursorBrush = cursor,
        modifier = modifier,
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(hint, style = style.copy(color = colors.secondaryText))
                field()
            }
        },
    )
}
