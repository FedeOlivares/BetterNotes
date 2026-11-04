package app.betternotes.core.settings

import app.betternotes.core.geometry.PageSize
import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate

/** The timings of the two-finger taps that undo and redo. */
data class GestureSettings(
    /** How long a tap sequence stays open. Undo waits this long, since a third tap would make it a redo. */
    val multiTapWindowMs: Long = DEFAULT_MULTI_TAP_WINDOW_MS,
    /** The longest a two-finger tap may stay down. */
    val tapMaxDurationMs: Long = DEFAULT_TAP_MAX_DURATION_MS,
) {
    init {
        require(multiTapWindowMs in MULTI_TAP_WINDOW_RANGE_MS) { "Window out of range: $multiTapWindowMs" }
        require(tapMaxDurationMs in TAP_DURATION_RANGE_MS) { "Tap duration out of range: $tapMaxDurationMs" }
    }

    fun applyTo(config: GestureConfig) =
        config.copy(multiTapWindowMs = multiTapWindowMs, tapMaxDurationMs = tapMaxDurationMs)

    companion object {
        const val DEFAULT_MULTI_TAP_WINDOW_MS = 300L
        const val DEFAULT_TAP_MAX_DURATION_MS = 250L

        /** Under this, a person cannot land a second tap. Over it, undo feels broken. */
        val MULTI_TAP_WINDOW_RANGE_MS = 100L..1000L
        val TAP_DURATION_RANGE_MS = 100L..1000L
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
