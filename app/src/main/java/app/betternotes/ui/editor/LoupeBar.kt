package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors

/**
 * The loupe's title bar, over the empty space that the canvas draws its magnified window into. The empty
 * space takes no touches, so the pen in it reaches the canvas.
 */
@Composable
internal fun LoupeBar(state: EditorState, modifier: Modifier) {
    val colors = LocalEditorColors.current
    val scope = rememberCoroutineScope()
    val screenHeight = LocalWindowInfo.current.containerSize.height.toFloat().coerceAtLeast(1f)
    // The window's height, as a share of the screen. It shrinks to nothing when pulled under a fifth of its first height.
    val share = state.loupeShare
    val shrink = (share / (LOUPE_DEFAULT_SHARE * CLOSE_BELOW)).coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(share.coerceAtLeast(MIN_DRAWN_SHARE))
            .graphicsLayer { alpha = shrink }
            .padding(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .masksInk(state, "loupe-bar")
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.panel)
                .padding(horizontal = 8.dp),
        ) {
            ToolbarIcon(EditorIcons.Crosshair, stringResource(R.string.loupe_center), onClick = state::recenterLoupe)
            Spacer(Modifier.weight(1f))
            // The handle: sliding it makes the window taller or shorter, and shortening it far enough closes the loupe.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(HANDLE_TOUCH_WIDTH)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dy ->
                            change.consume()
                            if (state.loupeClosing) return@detectVerticalDragGestures
                            state.loupeShare = (state.loupeShare - dy / screenHeight).coerceIn(0f, MAX_SHARE)
                            if (state.loupeShare < LOUPE_DEFAULT_SHARE * CLOSE_BELOW) {
                                state.loupeClosing = true
                                scope.launch {
                                    Animatable(state.loupeShare).animateTo(0f, tween(CLOSE_MS)) { state.loupeShare = value }
                                    state.closeLoupe()
                                }
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.iconDisabled),
                )
            }
            Spacer(Modifier.weight(1f))
            ToolbarIcon(EditorIcons.Back, stringResource(R.string.loupe_back)) { state.stepLoupe(forward = false) }
            ToolbarIcon(EditorIcons.Forward, stringResource(R.string.loupe_forward)) { state.stepLoupe(forward = true) }
            ToolbarIcon(EditorIcons.Undo, stringResource(R.string.editor_undo), enabled = state.canUndo, onClick = state::undo)
            ToolbarIcon(EditorIcons.Redo, stringResource(R.string.editor_redo), enabled = state.canRedo, onClick = state::redo)
            ToolbarIcon(EditorIcons.Close, stringResource(R.string.panel_close), onClick = state::toggleLoupe)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    val area = coordinates.boundsInRoot()
                    state.setLoupeArea(area.left, area.top, area.right, area.bottom)
                },
        )
    }
}

private val BAR_HEIGHT = 44.dp
internal const val LOUPE_DEFAULT_SHARE = 0.42f

/** The window closes once it is under this share of its first height. */
private const val CLOSE_BELOW = 0.2f
private const val MAX_SHARE = 0.85f

/** Kept above zero while it shrinks, so that the layout never asks for nothing. */
private const val MIN_DRAWN_SHARE = 0.005f
private const val CLOSE_MS = 180
private val HANDLE_TOUCH_WIDTH = 120.dp
