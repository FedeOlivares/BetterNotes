package app.betternotes.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/** How long any menu or panel takes to open. */
const val OPEN_MS = 100

private const val START_SCALE = 0.3f

/** Where a menu grows from, as a fraction of its own size: its anchor corner, or the edge of the screen it comes out of. */
object OpenFrom {
    val TopStart = TransformOrigin(0f, 0f)
    val TopEnd = TransformOrigin(1f, 0f)
    val Start = TransformOrigin(0f, 0.5f)
    val BottomEnd = TransformOrigin(1f, 1f)
    val Center = TransformOrigin.Center
}

/**
 * Makes a menu or panel grow from [origin] to its full size and fade in, in [OPEN_MS], when it enters the composition.
 * Only the drawing changes; the layout, and so the touch area, is the full size from the first frame.
 */
@Composable
fun Modifier.opensFrom(origin: TransformOrigin): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(OPEN_MS)) }
    return graphicsLayer {
        val p = progress.value
        val scale = START_SCALE + (1f - START_SCALE) * p
        scaleX = scale
        scaleY = scale
        alpha = p
        transformOrigin = origin
    }
}
