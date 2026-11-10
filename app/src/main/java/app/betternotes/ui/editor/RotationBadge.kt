package app.betternotes.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import app.betternotes.ui.theme.OPEN_MS
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The fade takes this many times as long as a menu does to open. */
private const val FADE_FACTOR = 3

/**
 * The angle the page is turned at, at the bottom of the screen. It shows while the page is being turned, and when a
 * tap lands where it sits. Tapping it turns the page back upright.
 */
@Composable
internal fun RotationBadge(state: EditorState, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(state.rotationBadgeShown, state.rotating) {
        alpha.snapTo(1f)
        if (state.rotationBadgeShown && !state.rotating) {
            delay(state.rotationBadgeHoldMs)
            alpha.animateTo(0f, tween(FADE_FACTOR * OPEN_MS))
            state.rotationBadgeShown = false
        }
    }
    val visible = state.rotating || state.rotationBadgeShown
    if (!visible) return
    val colors = LocalEditorColors.current
    val degrees = ((state.rotationDegrees.roundToInt() % FULL_TURN) + FULL_TURN) % FULL_TURN
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha.value }
            .height(48.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.pill)
            .border(1.dp, colors.pillBorder, RoundedCornerShape(24.dp))
            .clickable(enabled = !state.rotating, role = Role.Button, onClickLabel = stringResource(R.string.rotation_reset)) {
                state.resetRotation()
            }
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            EditorIcons.Rotate,
            stringResource(R.string.rotation_reset),
            tint = colors.icon,
            modifier = Modifier.size(24.dp),
        )
        Text(
            "$degrees°",
            color = colors.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private const val FULL_TURN = 360
