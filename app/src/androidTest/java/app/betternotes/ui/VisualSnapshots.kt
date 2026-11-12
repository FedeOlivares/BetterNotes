package app.betternotes.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.library.LibraryStore
import app.betternotes.ui.library.LibraryActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshots of the library and the editor with its panels, for comparing two builds pixel by pixel. Run the
 * demo seed first (see LibraryDemoSeed), then, with the app and test APKs installed:
 *
 * adb shell am instrument -w -e snapshots before -e class app.betternotes.ui.VisualSnapshots \
 *   app.betternotes.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The pictures land in the app's external files folder, under snapshots/<label>.
 */
@RunWith(AndroidJUnit4::class)
class VisualSnapshots {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val label: String? = InstrumentationRegistry.getArguments().getString("snapshots")
    private val folder by lazy { File(context.getExternalFilesDir(null), "snapshots/$label").apply { mkdirs() } }

    private fun shoot(name: String) {
        compose.waitForIdle()
        Thread.sleep(SETTLE_MS)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(folder, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tapDescription(description: String, index: Int = 0): Boolean = runCatching {
        compose.onAllNodesWithContentDescription(description)[index].performClick()
    }.isSuccess

    private fun tapText(text: String, index: Int = 0): Boolean = runCatching {
        compose.onAllNodesWithText(text)[index].performClick()
    }.isSuccess

    private fun noteId(name: String): Long =
        LibraryStore(context.filesDir).read().entries.first { it.name == name }.id

    private fun editor(name: String, block: () -> Unit) {
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DOCUMENT_ID, noteId(name))
        ActivityScenario.launch<MainActivity>(intent).use {
            Thread.sleep(OPEN_MS)
            block()
        }
    }

    @Test
    fun library() {
        assumeTrue(label != null)
        ActivityScenario.launch(LibraryActivity::class.java).use {
            Thread.sleep(OPEN_MS)
            shoot("library")
            if (tapDescription("Add")) shoot("library-add")
        }
        ActivityScenario.launch(LibraryActivity::class.java).use {
            Thread.sleep(OPEN_MS)
            if (tapDescription("More options")) shoot("library-tile-menu")
        }
    }

    @Test
    fun editorTools() {
        assumeTrue(label != null)
        editor("MED LOCAL Firemen Care Notes") { shoot("editor") }
        for (tool in TOOLS) {
            editor("MED LOCAL Firemen Care Notes") {
                if (tapDescription(tool)) {
                    shoot("tool-$tool-1")
                    if (tapDescription("Tool options")) shoot("tool-$tool-2")
                }
            }
        }
        for (button in BUTTONS) {
            editor("MED LOCAL Firemen Care Notes") { if (tapDescription(button)) shoot("button-$button") }
        }
    }

    private companion object {
        const val OPEN_MS = 2500L
        const val SETTLE_MS = 900L
        val TOOLS = listOf(
            "Pen", "Creative brush", "Highlighter", "Eraser", "Lasso", "Tape", "Text", "Emoji", "Video", "Shapes",
            "Image", "Table", "Clipboard", "Sticky note", "Loupe", "Ruler", "Recorder", "Laser pointer", "Settings",
        )
        val BUTTONS = listOf("Sidebar", "All pages", "Page switcher", "More", "Tool options", "View only")
    }
}
