package app.betternotes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/** A phone: the smallest side is under this many dp. The Pad 7 (about 776) and any tablet are above it. */
private const val COMPACT_BELOW_DP = 600

/** Whether the screen is a phone's, which gets a more compact layout. It follows the device, not the window, so a split screen on a tablet keeps the tablet's. */
@Composable
@ReadOnlyComposable
fun isCompactScreen(): Boolean = LocalConfiguration.current.smallestScreenWidthDp < COMPACT_BELOW_DP
