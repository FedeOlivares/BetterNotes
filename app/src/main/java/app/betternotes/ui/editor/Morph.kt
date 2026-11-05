package app.betternotes.ui.editor

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** How long the second toolbar takes to turn into the next one's: its box, the icons that stay, the colors and the new icons. */
internal const val MORPH_MS = 220

/** What a cell of the second toolbar needs to take part in the change from one tool's row to the next. */
@OptIn(ExperimentalSharedTransitionApi::class)
internal class MorphScopes(val shared: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

internal val LocalMorph = compositionLocalOf<MorphScopes?> { null }

/**
 * Marks a part of a row for the change of tool. With a [key], the same key in the row that comes next is the same
 * thing: it moves and resizes to where it is there, and a color changes to the new color. With no key, the part grows
 * from nothing when the new row brings it, and shrinks away when the old row takes it. Outside the second toolbar it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.morph(key: String?): Modifier {
    val scopes = LocalMorph.current ?: return this
    val tween = tween<Float>(MORPH_MS)
    return with(scopes.visibility) {
        with(scopes.shared) {
            if (key == null) {
                this@morph.animateEnterExit(enter = fadeIn(tween) + scaleIn(tween), exit = fadeOut(tween) + scaleOut(tween))
            } else {
                this@morph.sharedBounds(
                    rememberSharedContentState(key),
                    scopes.visibility,
                    enter = fadeIn(tween),
                    exit = fadeOut(tween),
                    boundsTransform = BoundsTransform { _, _ -> tween(MORPH_MS) },
                )
            }
        }
    }
}

/** The row's box itself: it grows or shrinks to the next row's size. */
@Composable
internal fun Modifier.morphBar(): Modifier = morph("bar")
