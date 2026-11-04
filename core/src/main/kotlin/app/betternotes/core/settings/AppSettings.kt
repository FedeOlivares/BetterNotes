package app.betternotes.core.settings

import app.betternotes.core.geometry.PageSize
import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.gesture.OneFingerAction
import app.betternotes.core.gesture.TwoFingerAction
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate

/** What fingers do, and the timings of the two-finger taps that undo and redo. */
data class GestureSettings(
    /** How long a tap sequence stays open. Undo waits this long, since a third tap would make it a redo. */
    val multiTapWindowMs: Long = DEFAULT_MULTI_TAP_WINDOW_MS,
    /** The longest a two-finger tap may stay down. */
    val tapMaxDurationMs: Long = DEFAULT_TAP_MAX_DURATION_MS,
    val oneFinger: OneFingerSetting = OneFingerSetting.SCROLL,
    val twoFingers: TwoFingerAction = TwoFingerAction.ZOOM_AND_PAN,
    val undoTaps: Boolean = true,
    val redoTaps: Boolean = true,
    val doubleTapZoom: Boolean = true,
) {
    init {
        require(multiTapWindowMs in MULTI_TAP_WINDOW_RANGE_MS) { "Window out of range: $multiTapWindowMs" }
        require(tapMaxDurationMs in TAP_DURATION_RANGE_MS) { "Tap duration out of range: $tapMaxDurationMs" }
    }

    fun applyTo(config: GestureConfig) = config.copy(
        multiTapWindowMs = multiTapWindowMs,
        tapMaxDurationMs = tapMaxDurationMs,
        oneFinger = if (oneFinger == OneFingerSetting.IGNORED) OneFingerAction.IGNORED else OneFingerAction.SCROLL,
        twoFingers = twoFingers,
        undoTaps = undoTaps,
        redoTaps = redoTaps,
        zoomTaps = doubleTapZoom,
    )

    companion object {
        const val DEFAULT_MULTI_TAP_WINDOW_MS = 300L
        const val DEFAULT_TAP_MAX_DURATION_MS = 250L

        /** Under this, a person cannot land a second tap. Over it, undo feels broken. */
        val MULTI_TAP_WINDOW_RANGE_MS = 100L..1000L
        val TAP_DURATION_RANGE_MS = 100L..1000L
    }
}

/** What one finger does. [DRAW] is the toolbar's finger mode, kept here so that it survives a restart. */
enum class OneFingerSetting { IGNORED, DRAW, SCROLL }

/** What a stylus button can do. Whether a stylus has such a button depends on the stylus. */
enum class StylusAction { NONE, PEN, HIGHLIGHTER, ERASER, LASSO, UNDO, REDO }

/** What holding the primary button does. */
enum class StylusHoldAction { NONE, ERASE }

/** What the eraser end of a stylus does. */
enum class StylusEraserEnd { ERASER, IGNORED }

data class StylusSettings(
    /** Draws ahead of the pen using motion prediction. Off, ink only ever shows where the pen has been. */
    val predictInk: Boolean = true,
    val primaryButton: StylusAction = StylusAction.PEN,
    val secondaryButton: StylusAction = StylusAction.HIGHLIGHTER,
    val primaryHold: StylusHoldAction = StylusHoldAction.ERASE,
    val eraserEnd: StylusEraserEnd = StylusEraserEnd.ERASER,
)

/** How a new note gets its name. */
enum class NewNoteName { DATED, UNTITLED }

/** The behavior of an open note or canvas. */
data class CanvasSettings(
    val continueFromLastPage: Boolean = true,
    val keepScreenOn: Boolean = true,
    val hideSystemBars: Boolean = true,
    /** Items line up with each other as they are dragged. The editor does the aligning. */
    val snapToAlign: Boolean = true,
)

/** Whether a new note takes its name from the first words written in it. */
enum class AutoName { AUTO, OFF }

/**
 * A measured screen density, for when the device misreports its own. Real size depends on this: the
 * Pad 7 reports about 336 dpi against 345 on its spec sheet, which would draw 1 cm about 2.5% short.
 */
data class ScreenCalibration(val dpi: Float) {
    init {
        require(dpi in DPI_RANGE) { "Density out of range: $dpi" }
    }

    companion object {
        val DPI_RANGE = 50f..2000f

        /**
         * The density that makes a line right, from a line meant to be [shownLength] long, drawn at zoom
         * 1 with [currentDpi], which a real ruler says is [measuredLength]. Both lengths in one unit.
         */
        fun fromMeasurement(currentDpi: Float, shownLength: Float, measuredLength: Float): ScreenCalibration {
            require(shownLength > 0f && measuredLength > 0f) { "Lengths must be positive" }
            return ScreenCalibration(currentDpi * shownLength / measuredLength)
        }
    }
}

/** What a new note starts with. */
data class NewPageDefaults(
    val size: PageSize = PageSize.A4,
    val background: PageBackground = PageBackground(template = PageTemplate.Dotted()),
)

/**
 * The settings that apply to the whole app. The light or dark theme is not here, because the library
 * keeps it with the rest of its theming.
 */
data class AppSettings(
    val gestures: GestureSettings = GestureSettings(),
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val newPages: NewPageDefaults = NewPageDefaults(),
    /** Layouts the owner built, each with a name of its own. */
    val customLayouts: List<CustomLayout> = emptyList(),
    val keyBindings: KeyBindings = KeyBindings.Default,
    /** Null trusts the density the device reports. */
    val calibration: ScreenCalibration? = null,
    val stylus: StylusSettings = StylusSettings(),
    val canvas: CanvasSettings = CanvasSettings(),
    val newNoteName: NewNoteName = NewNoteName.DATED,
    val autoName: AutoName = AutoName.AUTO,
    val pomodoro: PomodoroSettings = PomodoroSettings(),
) {
    /** Saves [layout], replacing any layout with the same name. */
    fun withCustomLayout(layout: CustomLayout): AppSettings {
        val index = customLayouts.indexOfFirst { it.name == layout.name }
        val next = if (index < 0) customLayouts + layout else customLayouts.toMutableList().also { it[index] = layout }
        return copy(customLayouts = next)
    }

    /** Pages already using the layout keep their own copy of it. */
    fun withoutCustomLayout(name: String) = copy(customLayouts = customLayouts.filterNot { it.name == name })
}
