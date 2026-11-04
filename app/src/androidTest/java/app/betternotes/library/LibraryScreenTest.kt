package app.betternotes.library

import android.app.Instrumentation
import android.content.Context
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.library.LibraryView
import app.betternotes.ui.MainActivity
import app.betternotes.ui.library.LibraryActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        compose.onNodeWithText("New folder").performClick()
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
        // Inside, its name replaces the greeting, and back returns to the top.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Back"), TIMEOUT_MS)
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("0 items")
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
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Light theme").performClick()
        compose.onNodeWithContentDescription("Options").performClick()
        compose.onNodeWithText("Dark theme").assertExists()
        assertEquals("LIGHT", preferences().getString("theme", null))
    }

    @Test
    fun aNewNoteOpensTheEditor() {
        val monitor: Instrumentation.ActivityMonitor =
            instrumentation.addMonitor(MainActivity::class.java.name, null, true)
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("New note").performClick()
        compose.waitUntil(TIMEOUT_MS) { monitor.hits == 1 }
        instrumentation.removeMonitor(monitor)
        assertNotNull(library()!!.entries.firstOrNull { it.name == "Untitled note" })
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** More than any file system's time resolution, so the new file has a new time. */
        const val FILE_TIME_STEP_MS = 20L
    }
}
