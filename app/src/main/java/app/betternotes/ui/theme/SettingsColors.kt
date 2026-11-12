package app.betternotes.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors of the settings screens. The dark set is measured from the owner's screenshots. */
@Immutable
internal data class SettingsColors(
    val screen: Color,
    val sheet: Color,
    val card: Color,
    val closeButton: Color,
    val field: Color,
    val inset: Color,
    val kindSelected: Color,
    val title: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val onAccent: Color,
    val switchOffTrack: Color,
    val switchOff: Color,
    val chipSelected: Color,
    val chipSelectedText: Color,
    val chipIdle: Color,
    val chipIdleText: Color,
    val sliderIdle: Color,
    val sliderDotOn: Color,
    val sliderDotOff: Color,
    val divider: Color,
    val menu: Color,
    val danger: Color,
)

internal val DarkSettings = SettingsColors(
    screen = Color(0xFF000000),
    sheet = Color(0xFF1E1F23),
    card = Color(0xFF333746),
    closeButton = Color(0xFF2C2C2E),
    field = Color(0xFF3D4455),
    inset = Color(0xFF26282D),
    kindSelected = Color(0xFF1F3560),
    title = Color(0xFFE6E7EA),
    text = Color(0xFFE3E4E9),
    secondary = Color(0xFFC6C8D0),
    accent = Color(0xFF6D9CF8),
    onAccent = Color(0xFF0E2B71),
    switchOffTrack = Color(0xFF3F4553),
    switchOff = Color(0xFF90909A),
    chipSelected = Color(0xFF224970),
    chipSelectedText = Color(0xFFD6E6FB),
    chipIdle = Color(0xFF252D3A),
    chipIdleText = Color(0xFFA9B3C2),
    sliderIdle = Color(0xFF4F5052),
    sliderDotOn = Color(0xFF0E2B71),
    sliderDotOff = Color(0xFFD9DADD),
    divider = Color(0xFF466FC1),
    menu = Color(0xFF2A2D35),
    danger = Color(0xFFF2B8B5),
)

/** No light screenshot exists, so this is the same set of roles on a light ground. */
internal val LightSettings = SettingsColors(
    screen = Color(0xFFF2F3F6),
    sheet = Color(0xFFFFFFFF),
    card = Color(0xFFE6E9F2),
    closeButton = Color(0xFFE3E4E8),
    field = Color(0xFFE2E5EE),
    inset = Color(0xFFEDEFF3),
    kindSelected = Color(0xFFDCE7FB),
    title = Color(0xFF1B1C20),
    text = Color(0xFF1F2026),
    secondary = Color(0xFF55585F),
    accent = Color(0xFF3E78EE),
    onAccent = Color(0xFFFFFFFF),
    switchOffTrack = Color(0xFFD8DAE2),
    switchOff = Color(0xFF7C7E87),
    chipSelected = Color(0xFFD3E2FB),
    chipSelectedText = Color(0xFF1B3F86),
    chipIdle = Color(0xFFE7E9EE),
    chipIdleText = Color(0xFF50535B),
    sliderIdle = Color(0xFFCDCFD6),
    sliderDotOn = Color(0xFFFFFFFF),
    sliderDotOff = Color(0xFF5B5E66),
    divider = Color(0xFFA9C2F4),
    menu = Color(0xFFF7F8FA),
    danger = Color(0xFFB3261E),
)

internal val LocalSettingsColors = staticCompositionLocalOf { DarkSettings }
