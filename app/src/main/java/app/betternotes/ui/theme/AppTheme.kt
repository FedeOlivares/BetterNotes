package app.betternotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.min

enum class ThemeMode { DARK, LIGHT }

/**
 * The app's colors. The dark set is measured from the owner's screenshot of the library. No light
 * screenshot exists, so the light set is an adaptation with no reference.
 */
@Immutable
data class AppColors(
    val background: Color,
    val glowStops: List<Pair<Float, Color>>,
    val glowDot: Color,
    val surface: Color,
    val raisedSurface: Color,
    val onRaisedAccent: Color,
    val border: Color,
    val pillBorder: Color,
    val greeting: Color,
    val subtitle: Color,
    val title: Color,
    val secondaryText: Color,
    val icon: Color,
    val strongIcon: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onAccent: Color,
    val folderBack: Color,
    val folderFront: Color,
    val folderPaper: Color,
    val folderText: Color,
    val folderDim: Color,
    val folderDots: Color,
    val scrim: Color,
)

private val DarkColors = AppColors(
    background = Color(0xFF000000),
    glowStops = listOf(
        0f to Color(0xFF000000),
        0.19f to Color(0xFF0C0D12),
        0.32f to Color(0xFF182033),
        0.45f to Color(0xFF1D2D44),
        0.58f to Color(0xFF283545),
        0.71f to Color(0xFF283234),
        0.84f to Color(0xFF1A1F22),
        1f to Color(0xFF000000),
    ),
    glowDot = Color(0x4D000000),
    surface = Color(0xFF1E1F23),
    raisedSurface = Color(0xFF26292E),
    onRaisedAccent = Color(0xFF6E9DF9),
    border = Color(0xFF48494D),
    pillBorder = Color(0xFF717075),
    greeting = Color(0xFFECECEE),
    subtitle = Color(0xFFCFCFD3),
    title = Color(0xFFF2F3F5),
    secondaryText = Color(0xFF999A9E),
    icon = Color(0xFFA9A9A9),
    strongIcon = Color(0xFFDCDCDC),
    accent = Color(0xFF719EF9),
    accentContainer = Color(0xFF253557),
    onAccentContainer = Color(0xFF86A9F2),
    onAccent = Color(0xFF001F6E),
    folderBack = Color(0xFF6592E3),
    folderFront = Color(0xFF719EF9),
    folderPaper = Color(0xFF23232B),
    folderText = Color(0xFFFCFCFF),
    folderDim = Color(0xCCFFFFFF),
    folderDots = Color(0xFFB6D1FF),
    scrim = Color(0xFF191A1C),
)

private val LightColors = AppColors(
    background = Color(0xFFF5F6F8),
    glowStops = listOf(
        0f to Color(0xFFF5F6F8),
        0.32f to Color(0xFFDDE6F7),
        0.5f to Color(0xFFD2DEF6),
        0.71f to Color(0xFFDDE5EE),
        1f to Color(0xFFF5F6F8),
    ),
    glowDot = Color(0x1F27457A),
    surface = Color(0xFFFFFFFF),
    raisedSurface = Color(0xFFEEF1F6),
    onRaisedAccent = Color(0xFF2C63D6),
    border = Color(0xFFD3D5DA),
    pillBorder = Color(0xFFC4C6CC),
    greeting = Color(0xFF17181B),
    subtitle = Color(0xFF55575D),
    title = Color(0xFF17181B),
    secondaryText = Color(0xFF6C6E74),
    icon = Color(0xFF5C5E64),
    strongIcon = Color(0xFF34363B),
    accent = Color(0xFF3E78EE),
    accentContainer = Color(0xFFD8E4FB),
    onAccentContainer = Color(0xFF1F4FB5),
    onAccent = Color(0xFFFFFFFF),
    folderBack = Color(0xFF6592E3),
    folderFront = Color(0xFF719EF9),
    folderPaper = Color(0xFF23232B),
    folderText = Color(0xFFFCFCFF),
    folderDim = Color(0xCCFFFFFF),
    folderDots = Color(0xFFD9E6FF),
    scrim = Color(0xFFF5F6F8),
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

/** Material's body text has wide letter spacing, which the reference's text does not. */
private val Typography = Typography().let { it.copy(bodyLarge = it.bodyLarge.copy(letterSpacing = 0.sp)) }

/** The app's colors, and Material's own, for the dialogs and menus built from its components. */
@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (mode == ThemeMode.DARK) DarkColors else LightColors
    val scheme = if (mode == ThemeMode.DARK) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            surface = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            onSurface = colors.title,
            onSurfaceVariant = colors.secondaryText,
            outline = colors.border,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            surface = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            onSurface = colors.title,
            onSurfaceVariant = colors.secondaryText,
            outline = colors.border,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}

/** The short side of the screenshots the app's measurements were taken from, in their pixels. */
private const val REFERENCE_SHORT_SIDE = 1068f

/**
 * On a screen much smaller than the tablet, keeps things from shrinking past what a finger can hit. Low
 * enough to stay out of the way on the Pad 7, whose density of 2.75 would otherwise change its scale.
 */
private const val MIN_DP_PER_UNIT = 0.6f

/**
 * Inside this, one dp is one pixel of the reference screenshots, so a measurement copied from a
 * screenshot lands in the same place on the Pad 7 whatever display size the tablet is set to. Those
 * units come out close to real dp. The system's font scale still applies.
 */
@Composable
fun ReferenceScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val shortSide = min(window.width, window.height)
    val scale = maxOf(shortSide / REFERENCE_SHORT_SIDE, density.density * MIN_DP_PER_UNIT)
    CompositionLocalProvider(LocalDensity provides Density(scale, density.fontScale), content = content)
}
