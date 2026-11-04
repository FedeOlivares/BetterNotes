package app.betternotes.library

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.library.LibraryStore
import app.betternotes.core.library.LibraryView
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.DocumentKind
import app.betternotes.R
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.TextItem
import app.betternotes.ui.MainActivity
import app.betternotes.ui.library.LibraryActivity
import app.betternotes.ui.text.TextNoteActivity
import app.betternotes.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.util.Locale

/** Runs the real home screen and repository on a device, with touches sent through Compose's test tools. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val repository get() = LibraryRepository.get(context)

    /** The name must be set before the activity starts, or the first run's question covers the screen. */
    private val namePreset = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                preferences().edit { clear().putString("user_name", "Test") }
                base.evaluate()
            }
        }
    }

    private val compose = createAndroidComposeRule<LibraryActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(namePreset).around(compose)

    private fun preferences() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun library() = repository.library.value

    private fun waitForText(text: String) =
        compose.waitUntilAtLeastOneExists(hasText(text), TIMEOUT_MS)

    private fun waitGone(text: String) =
        compose.waitUntilDoesNotExist(hasText(text), TIMEOUT_MS)

    private fun menuOf(cardName: String) {
        // Tiles come in the order of the list, and each has one menu button.
        val index = library()!!.list(LibraryView.InFolder(null)).indexOfFirst { it.name == cardName }
        compose.onAllNodesWithContentDescription("More options")[index].performClick()
    }

    private fun addFolder(name: String) {
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Folder").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Create").performClick()
        waitForText(name)
    }

    @Before
    fun emptyTheLibrary() {
        compose.waitUntil(TIMEOUT_MS) { library() != null }
        compose.runOnUiThread {
            library()!!.entries.filter { it.parentId == null }.forEach { repository.deleteForever(it.id) }
        }
        compose.waitForIdle()
    }

    @Test
    fun theGreetingUsesTheName() {
        compose.onNodeWithText("Test", substring = true).assertExists()
        compose.onNodeWithText("A moment to gather your thoughts.").assertExists()
    }

    @Test
    fun aNewFolderShowsAsATileAndOpens() {
        addFolder("Surgery")
        compose.onNodeWithText("0 items").assertExists()

        compose.onNodeWithText("Surgery").performClick()
        // Inside, the greeting stays, and a house and the folder's name take the place of "All".
        compose.waitUntilAtLeastOneExists(hasContentDescription("Home"), TIMEOUT_MS)
        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithText("Test", substring = true).assertExists()
        compose.onNodeWithText("Surgery").assertExists()
        compose.onAllNodesWithContentDescription("Home")[0].performClick()
        waitForText("0 items")
        compose.onNodeWithText("All").assertExists()
    }

    @Test
    fun aDocumentMovesIntoAFolder() {
        compose.runOnUiThread { repository.createFolder("Surgery", null) }
        waitForText("Surgery")
        var created = 0L
        compose.runOnUiThread { repository.createNote("Case notes", null) { created = it } }
        waitForText("Case notes")
        compose.waitUntil(TIMEOUT_MS) { created != 0L }

        menuOf("Case notes")
        compose.onNodeWithText("Move to folder").performClick()
        compose.onAllNodesWithText("Surgery").fetchSemanticsNodes().also { assertTrue(it.size >= 2) }
        compose.onAllNodesWithText("Surgery")[1].performClick()

        waitForText("1 item")
        waitGone("Case notes")
        assertEquals(library()!!.entries.first { it.isFolder }.id, library()!![created]!!.parentId)
    }

    @Test
    fun theBinHoldsAnItemUntilItIsRestoredOrDeleted() {
        var created = 0L
        compose.runOnUiThread { repository.createNote("Old notes", null) { created = it } }
        waitForText("Old notes")
        compose.waitUntil(TIMEOUT_MS) { created != 0L }
        val folder = repository.documentStore(created).root

        menuOf("Old notes")
        compose.onNodeWithText("Move to bin").performClick()
        waitGone("Old notes")

        compose.onNodeWithContentDescription("Bin").performClick()
        waitForText("Old notes")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Restore").performClick()
        waitGone("Old notes")
        assertNull(library()!![created]!!.trashedAtMs)
        compose.onNodeWithText("All").performClick()
        waitForText("Old notes")
        assertTrue(folder.exists())

        compose.runOnUiThread { repository.trash(created) }
        compose.onNodeWithContentDescription("Bin").performClick()
        waitForText("Old notes")
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete forever").performClick()
        compose.onNodeWithText("Delete forever").performClick()
        waitGone("Old notes")
        compose.waitUntil(TIMEOUT_MS) { !folder.exists() }
    }

    @Test
    fun searchFindsItemsInsideFoldersIgnoringAccents() {
        compose.runOnUiThread { repository.createFolder("Surgery", null) }
        waitForText("Surgery")
        val folderId = library()!!.entries.first { it.isFolder }.id
        compose.runOnUiThread { repository.createNote("Clínica quirúrgica", folderId) {} }
        compose.waitUntil(TIMEOUT_MS) { library()!!.entries.any { it.document != null } }

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("quirurgica")
        waitForText("Clínica quirúrgica")
        compose.onNodeWithContentDescription("Close search").performClick()
        waitGone("Clínica quirúrgica")
    }

    @Test
    fun aSavedChangeReachesTheTileOnRefresh() {
        var created = 0L
        compose.runOnUiThread { repository.createNote("Before", null) { created = it } }
        waitForText("Before")
        compose.waitUntil(TIMEOUT_MS) { created != 0L }

        // What closing the editor leaves behind: a newer description in the document's folder.
        val documents = repository.documentStore(created)
        Thread.sleep(FILE_TIME_STEP_MS)
        documents.writeDocument(documents.readDocument().apply { title = "After" })
        compose.runOnUiThread { repository.refresh() }

        waitForText("After")
    }

    @Test
    fun theThemePersists() {
        compose.runOnUiThread { LibraryPreferences.get(context).updateTheme(ThemeMode.LIGHT) }
        assertEquals("LIGHT", preferences().getString("theme", null))
        compose.runOnUiThread { LibraryPreferences.get(context).updateTheme(ThemeMode.DARK) }
    }

    @Test
    fun theTextTileOpensATypedNotebook() {
        val monitor: Instrumentation.ActivityMonitor =
            instrumentation.addMonitor(TextNoteActivity::class.java.name, null, true)
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Text").performClick()
        compose.waitUntil(TIMEOUT_MS) { monitor.hits == 1 }
        instrumentation.removeMonitor(monitor)
        val note = library()!!.entries.first { it.document != null }
        assertEquals("New note " + LocalDate.now(), note.name)
        assertEquals(DocumentKind.TEXT, note.document!!.kind)
    }

    @Test
    fun theOptionsMenuSwitchesToAListAndSelects() {
        compose.runOnUiThread { repository.createFolder("Surgery", null) }
        waitForText("Surgery")
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Last modified").assertExists()
        compose.onNodeWithText("List").performClick()
        assertEquals("LIST", preferences().getString("library_layout", null))
        waitForText("0 items")

        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithText("Surgery").performClick()
        waitForText("1 selected")
        compose.onNodeWithContentDescription("Move to bin").performClick()
        waitGone("Surgery")
        assertTrue(library()!!.entries.single().trashedAtMs != null)

        compose.runOnUiThread { LibraryPreferences.get(context).updateLayout(LibraryLayout.GRID) }
    }

    @Test
    fun everyLibraryTextHasASpanishVersion() {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("es-419")) }
        val spanish = context.createConfigurationContext(config)
        assertEquals("Empecemos", spanish.getString(R.string.library_new_title))
        assertEquals("Ajustes", spanish.getString(R.string.settings_title))
    }

    @Test
    fun aTypedNotebookSavesItsText() {
        var created = 0L
        compose.runOnUiThread { repository.createText("Lecture", null) { created = it } }
        compose.waitUntil(TIMEOUT_MS) { created != 0L }
        val store = repository.documentStore(created)
        val intent = Intent(context, TextNoteActivity::class.java).putExtra(TextNoteActivity.EXTRA_DOCUMENT_ID, created)
        ActivityScenario.launch<TextNoteActivity>(intent).use { scenario ->
            compose.onNodeWithText("Start typing").performTextInput("Hello")
            compose.waitForIdle()
            scenario.moveToState(Lifecycle.State.CREATED)
        }
        val document = store.readDocument()
        val item = store.readPage(document.pages[0].id).single() as TextItem
        assertEquals("Hello", item.text)
    }

    @Test
    fun theCanvasTileMakesADrawingNote() {
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, true)
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Let's get started").assertExists()
        compose.onNodeWithText("Link").assertDoesNotExist()
        compose.onNodeWithText("Canvas").performClick()
        compose.waitUntil(TIMEOUT_MS) { monitor.hits == 1 }
        instrumentation.removeMonitor(monitor)
        // A drawing canvas with pages, not an infinite one.
        assertEquals(DocumentKind.NOTE, library()!!.entries.first { it.document != null }.document!!.kind)
        compose.onNodeWithText("Let's get started").assertDoesNotExist()
    }

    @Test
    fun aPhotoBecomesANoteWithItsPicture() {
        val file = File(context.cacheDir, "photo-test.png")
        val picture = createBitmap(400, 200).apply { eraseColor(Color.RED) }
        FileOutputStream(file).use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var created = 0L
        compose.runOnUiThread {
            repository.createFromPhotos(
                context.contentResolver,
                listOf(Uri.fromFile(file), Uri.fromFile(file)),
                "Photos",
                PhotoLayout.FILLS_PAGE,
                null,
                onFailed = { throw AssertionError("The import failed") },
                onCreated = { created = it },
            )
        }
        waitForText("Photos")
        compose.waitUntil(TIMEOUT_MS) { created != 0L }

        val documents = repository.documentStore(created)
        val document = documents.readDocument()
        assertEquals(2, document.pages.size)
        val page = document.pages[0]
        assertEquals(page.size!!.widthPt / 2f, page.size!!.heightPt, 0.01f)
        val image = documents.readPage(page.id).single() as ImageItem
        assertEquals(page.size!!.widthPt, image.frame.width, 0.01f)
        assertTrue(documents.assetFile(image.assetId).length() > 0)

        val bitmap = ThumbnailRenderer.render(documents)
        assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun aRecordingBecomesANoteAndAFailedImportLeavesNothing() {
        val wav = File(context.cacheDir, "audio-test.wav")
        wav.writeBytes(oneSecondOfSilence())
        var created = 0L
        compose.runOnUiThread {
            repository.createFromAudio(
                context.contentResolver,
                Uri.fromFile(wav),
                "Lecture",
                null,
                onFailed = { throw AssertionError("The import failed") },
                onCreated = { created = it },
            )
        }
        waitForText("Lecture")
        compose.waitUntil(TIMEOUT_MS) { created != 0L }
        val documents = repository.documentStore(created)
        val audio = documents.readPage(documents.readDocument().pages[0].id).single() as AudioItem
        assertEquals(1000L, audio.durationMs)

        var failed = false
        compose.runOnUiThread {
            repository.createFromAudio(
                context.contentResolver,
                Uri.fromFile(File(context.cacheDir, "does-not-exist.wav")),
                "Missing",
                null,
                onFailed = { failed = true },
                onCreated = { throw AssertionError("Nothing to create") },
            )
        }
        compose.waitUntil(TIMEOUT_MS) { failed }
        compose.onNodeWithText("Missing").assertDoesNotExist()
        assertTrue(LibraryStore(context.filesDir).documentsDir.list()!!.none { it.startsWith(".import-") })
    }

    /** A valid WAV file: 8 kHz, 8 bits, mono, one second. */
    private fun oneSecondOfSilence(): ByteArray {
        val samples = 8000
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + samples)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(samples)
            putInt(samples)
            putShort(1)
            putShort(8)
            put("data".toByteArray())
            putInt(samples)
        }
        return header.array() + ByteArray(samples) { 128.toByte() }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** More than any file system's time resolution, so the new file has a new time. */
        const val FILE_TIME_STEP_MS = 20L
    }
}
