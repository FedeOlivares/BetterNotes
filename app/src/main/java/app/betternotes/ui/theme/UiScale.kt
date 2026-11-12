package app.betternotes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import kotlin.math.min

/*
 * How the screens' sizes become pixels. Every dp in the screens passes through one of the two scales here, so the
 * layout comes out the same on the Pad 7 and grows or shrinks with the screen on other tablets.
 */

/** The short side of the screenshots the app's measurements were taken from, in their pixels. */
private const val REFERENCE_SHORT_SIDE = 1068f

/**
 * On a screen much smaller than the tablet, keeps things from shrinking past what a finger can hit. Low
 * enough to stay out of the way on the Pad 7, whose density of 2.75 would otherwise change its scale.
 */
private const val MIN_DP_PER_UNIT = 0.6f

/**
 * On a phone one unit is this many dp. The floor above gives 0.6 there, which sets the greeting about a fifth narrower
 * against the screen than Notewise's phone does; this is the size that matches its screenshots.
 */
private const val COMPACT_DP_PER_UNIT = 0.71f

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
    val scale = if (isCompactScreen()) {
        density.density * COMPACT_DP_PER_UNIT
    } else {
        maxOf(shortSide / REFERENCE_SHORT_SIDE, density.density * MIN_DP_PER_UNIT)
    }
    CompositionLocalProvider(LocalDensity provides Density(scale, density.fontScale), content = content)
}

/**
 * The sizes in the editor's controls were read off screenshots at 2.5 pixels per unit, and the tablet those
 * screenshots came from has 2.75 pixels per dp. This makes them come out the same size on it.
 */
private const val EDITOR_SCREENSHOT_SCALE = 2.5f / 2.75f

/**
 * The editor's floating controls: the device's dp, scaled so the screenshot measurements land at their size on the
 * Pad 7. On another tablet they keep the same physical size, like any Android control.
 */
@Composable
fun EditorScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val designDensity = Density(density.density * EDITOR_SCREENSHOT_SCALE, density.fontScale)
    CompositionLocalProvider(LocalDensity provides designDensity, content = content)
}
