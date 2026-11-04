package app.betternotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colors of the editor's floating controls. The dark set is measured from the owner's Notewise
 * screenshots. There are no light screenshots, so the light set is the same roles on a light ground.
 */
@Immutable
data class EditorColors(
    val pill: Color,
    val pillBorder: Color,
    val pillDivider: Color,
    val raisedButton: Color,
    val panel: Color,
    val panelDivider: Color,
    val field: Color,
    val closeButton: Color,
    val icon: Color,
    val iconDisabled: Color,
    val title: Color,
    val label: Color,
    val accent: Color,
    val accentContainer: Color,
    val toolSelected: Color,
    val toolSelectedIcon: Color,
    val sliderTrack: Color,
    val switchOn: Color,
    val switchOnThumb: Color,
    val switchOff: Color,
    val switchOffThumb: Color,
    val swatchRing: Color,
    val swatchEdge: Color,
    val previewDot: Color,
    val canvasBackdrop: Color,
)

val DarkEditorColors = EditorColors(
    pill = Color(0xFF25282D),
    pillBorder = Color(0xFF3A3D43),
    pillDivider = Color(0xFF484B52),
    raisedButton = Color(0xFF3C3F46),
    panel = Color(0xFF1E1F24),
    panelDivider = Color(0xFF3C3D42),
    field = Color(0xFF25282D),
    closeButton = Color(0xFF2C2C2E),
    icon = Color(0xFFE6E7EA),
    iconDisabled = Color(0xFF676A71),
    title = Color(0xFFE6E7EA),
    label = Color(0xFFA5A4AD),
    accent = Color(0xFF5F9FFB),
    accentContainer = Color(0xFF1F355A),
    toolSelected = Color(0xFF174A72),
    toolSelectedIcon = Color(0xFFB9D8F7),
    sliderTrack = Color(0xFF0F4A73),
    switchOn = Color(0xFF5E9EFF),
    switchOnThumb = Color(0xFF012B75),
    switchOff = Color(0xFF3C3F46),
    switchOffThumb = Color(0xFFA5A4AD),
    swatchRing = Color(0xFF4B4952),
    swatchEdge = Color(0x33FFFFFF),
    previewDot = Color(0xFF3C3D42),
    canvasBackdrop = Color(0xFF0E1013),
)

val LightEditorColors = EditorColors(
    pill = Color(0xFFF3F4F6),
    pillBorder = Color(0xFFD5D7DC),
    pillDivider = Color(0xFFC6C9CF),
    raisedButton = Color(0xFFDFE1E6),
    panel = Color(0xFFFBFBFC),
    panelDivider = Color(0xFFDCDEE3),
    field = Color(0xFFECEDF0),
    closeButton = Color(0xFFE6E7EA),
    icon = Color(0xFF25282D),
    iconDisabled = Color(0xFFA5A8AE),
    title = Color(0xFF1E1F24),
    label = Color(0xFF55575E),
    accent = Color(0xFF2F6FE0),
    accentContainer = Color(0xFFD8E5FC),
    toolSelected = Color(0xFFD8E5FC),
    toolSelectedIcon = Color(0xFF2F6FE0),
    sliderTrack = Color(0xFFBBD3F5),
    switchOn = Color(0xFF2F6FE0),
    switchOnThumb = Color(0xFFFFFFFF),
    switchOff = Color(0xFFD5D7DC),
    switchOffThumb = Color(0xFF55575E),
    swatchRing = Color(0xFFB9BBC2),
    swatchEdge = Color(0x33000000),
    previewDot = Color(0xFFD5D7DC),
    canvasBackdrop = Color(0xFFE3E4E8),
)

val LocalEditorColors = staticCompositionLocalOf { DarkEditorColors }

@Composable
fun BetterNotesTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val colors = if (mode == ThemeMode.DARK) DarkEditorColors else LightEditorColors
    val scheme = if (mode == ThemeMode.DARK) {
        darkColorScheme(primary = colors.accent, surface = colors.panel, onSurface = colors.title)
    } else {
        lightColorScheme(primary = colors.accent, surface = colors.panel, onSurface = colors.title)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalEditorColors provides colors, content = content)
    }
}
