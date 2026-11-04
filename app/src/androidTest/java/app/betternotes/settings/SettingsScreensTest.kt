package app.betternotes.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.gesture.TwoFingerAction
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.OneFingerSetting
import app.betternotes.core.settings.StylusAction
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.ui.settings.ApplyScope
import app.betternotes.ui.settings.CustomLayoutSheet
import app.betternotes.ui.settings.SettingsScreen
import app.betternotes.ui.settings.SettingsTheme
import app.betternotes.ui.settings.StylusFingerSheet
import app.betternotes.ui.settings.TemplateSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real screens by their visible text, and checks what they change. Says nothing about how they look. */
@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(AppSettings())

    private fun update(change: (AppSettings) -> AppSettings) {
        settings = change(settings)
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    private fun showStylusSheet() = compose.setContent {
        SettingsTheme { StylusFingerSheet(settings, ::update, onClose = {}) }
    }

    private fun showScreen() = compose.setContent {
        SettingsTheme { SettingsScreen(settings, reportedDpi = 300f, onChange = ::update, onBack = {}) }
    }

    @Test
    fun fingersFollowTheRadioButtons() {
        showStylusSheet()
        tap("Draw")
        assertEquals(OneFingerSetting.DRAW, settings.gestures.oneFinger)
        // "Scroll" and "Ignored" appear on both cards. The second of each is for two fingers.
        compose.onAllNodesWithText("Scroll")[1].performScrollTo().performClick()
        assertEquals(TwoFingerAction.SCROLL, settings.gestures.twoFingers)
        compose.onAllNodesWithText("Ignored")[0].performScrollTo().performClick()
        assertEquals(OneFingerSetting.IGNORED, settings.gestures.oneFinger)
    }

    @Test
    fun stylusButtonsFollowTheMenus() {
        showStylusSheet()
        tap("Primary action")
        compose.onNodeWithText("Switch to lasso").performClick()
        assertEquals(StylusAction.LASSO, settings.stylus.primaryButton)
    }

    @Test
    fun undoGestureCanBeTurnedOff() {
        showStylusSheet()
        compose.onNodeWithText("Double tap with two fingers").performScrollTo().performClick()
        compose.onNodeWithText("Off").performClick()
        assertFalse(settings.gestures.undoTaps)
    }

    @Test
    fun zoomTapToggles() {
        showStylusSheet()
        assertTrue(settings.gestures.doubleTapZoom)
        tap("Double tap to change zoom level")
        assertFalse(settings.gestures.doubleTapZoom)
    }

    @Test
    fun settingsScreenTogglesAndOpensScrollLayout() {
        showScreen()
        tap("Keep screen on")
        assertFalse(settings.canvas.keepScreenOn)
        tap("Scroll and layout")
        tap("Side to side, right to left")
        assertEquals(ScrollDirection.RIGHT_TO_LEFT, settings.scrollDirection)
    }

    @Test
    fun onlineFeaturesAreLeftOut() {
        showScreen()
        for (text in listOf("Notifications", "Security", "Link", "Auto name notes and sources")) {
            compose.onNodeWithText(text).assertDoesNotExist()
        }
    }

    @Test
    fun calibrationCorrectsTheDensityAndCanBeUndone() {
        showScreen()
        tap("Real size calibration")
        compose.onNode(hasSetTextAction()).performTextInput("7.5")
        tap("Apply")
        assertEquals(320f, settings.calibration!!.dpi, 0.01f)
        tap("Use the device's value")
        assertNull(settings.calibration)
    }

    @Test
    fun templateSheetChangesNothingUntilApply() {
        val applied = ArrayList<Triple<PageBackground, Boolean, ApplyScope>>()
        compose.setContent {
            SettingsTheme {
                TemplateSheet(
                    current = PageBackground(template = PageTemplate.Dotted()),
                    pageSize = PageSize.A4,
                    canResize = true,
                    canBeInfinite = true,
                    customLayouts = emptyList(),
                    onApply = { background, size, infinite, scope ->
                        assertNull(size)
                        applied += Triple(background, infinite, scope)
                    },
                    onNewLayout = {},
                    onEditLayout = {},
                    onClose = {},
                )
            }
        }
        tap("Grid")
        assertTrue(applied.isEmpty())
        compose.onNodeWithText("Apply to current page").performClick()
        val (background, infinite, scope) = applied.single()
        assertTrue(background.template is PageTemplate.Grid)
        assertFalse("the size was not touched", infinite)
        assertEquals(ApplyScope.CURRENT_PAGE, scope)
    }

    @Test
    fun infiniteIsOfferedAsAPageSizeAndAppliedAsOne() {
        var result: Triple<PageBackground, PageSize?, Boolean>? = null
        compose.setContent {
            SettingsTheme {
                TemplateSheet(
                    current = PageBackground(template = PageTemplate.Cornell()),
                    pageSize = PageSize.A4,
                    canResize = true,
                    canBeInfinite = true,
                    customLayouts = emptyList(),
                    onApply = { background, size, infinite, _ -> result = Triple(background, size, infinite) },
                    onNewLayout = {},
                    onEditLayout = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("Size: 595 x 842").performClick()
        compose.onNodeWithText("Infinite").performClick()
        compose.onNodeWithText("Size: Infinite").assertIsDisplayed()
        compose.onNodeWithText("Apply to current page").performClick()
        val (background, size, infinite) = result!!
        assertTrue("a template that does not repeat gives way to a blank one", background.template is PageTemplate.Blank)
        assertNull(size)
        assertTrue(infinite)
    }

    @Test
    fun aPdfPageOffersNoSizeChoice() {
        compose.setContent {
            SettingsTheme {
                TemplateSheet(
                    current = PageBackground(),
                    pageSize = PageSize.Letter,
                    canResize = false,
                    canBeInfinite = false,
                    customLayouts = emptyList(),
                    onApply = { _, _, _, _ -> },
                    onNewLayout = {},
                    onEditLayout = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("Size: 612 x 792").assertIsDisplayed().performClick()
        compose.onNodeWithText("A5").assertDoesNotExist()
    }

    @Test
    fun aNewLayoutNeedsANameAndSavesWhatWasAdded() {
        var saved: CustomLayout? = null
        compose.setContent {
            SettingsTheme { CustomLayoutSheet(null, onSave = { saved = it }, onDelete = {}, onClose = {}) }
        }
        compose.onNodeWithText("Save").performClick()
        assertNull(saved)

        compose.onNodeWithText("Table").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Lab")
        compose.onNodeWithText("Save").performClick()
        assertEquals("Lab", saved!!.name)
        assertEquals(1, saved!!.elements.size)
    }
}
