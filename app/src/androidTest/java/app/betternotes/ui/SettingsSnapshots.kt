package app.betternotes.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.ui.settings.CustomLayoutSheet
import app.betternotes.ui.settings.SettingsScreen
import app.betternotes.ui.settings.SettingsTheme
import app.betternotes.ui.settings.StylusFingerSheet
import app.betternotes.ui.settings.TemplateSheet
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Screenshots of the settings screens in both themes, for comparing two builds. Run like [VisualSnapshots]. */
@RunWith(AndroidJUnit4::class)
class SettingsSnapshots {
    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val label: String? = InstrumentationRegistry.getArguments().getString("snapshots")
    private val folder by lazy {
        File(instrumentation.targetContext.getExternalFilesDir(null), "snapshots/$label").apply { mkdirs() }
    }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var dark by mutableStateOf(true)

    private fun shoot(name: String, content: @Composable () -> Unit) {
        screen = content
        for (theme in listOf(true, false)) {
            dark = theme
            compose.waitForIdle()
            Thread.sleep(SETTLE_MS)
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: continue
            val file = File(folder, "settings-$name-${if (theme) "dark" else "light"}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun screens() {
        assumeTrue(label != null)
        compose.setContent { key(screen, dark) { SettingsTheme(dark) { screen?.invoke() } } }
        val settings = AppSettings()
        shoot("main") { SettingsScreen(settings, reportedDpi = 300f, onChange = {}, onBack = {}) }
        shoot("stylus") { StylusFingerSheet(settings, onChange = {}, onClose = {}) }
        shoot("layout") { CustomLayoutSheet(null, onSave = {}, onDelete = {}, onClose = {}) }
        shoot("template") {
            TemplateSheet(
                current = PageBackground(template = PageTemplate.Dotted()),
                pageSize = PageSize.A4,
                canResize = true,
                canBeInfinite = true,
                customLayouts = emptyList(),
                onApply = { _, _, _, _ -> },
                onNewLayout = {},
                onEditLayout = {},
                onClose = {},
            )
        }
    }

    private companion object {
        const val SETTLE_MS = 900L
    }
}
