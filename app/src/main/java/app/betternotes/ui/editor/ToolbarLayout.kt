package app.betternotes.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.withTimeoutOrNull

/** The screen edge the toolbars are on. On the left and right edges the pills run vertically. */
enum class ToolbarEdge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    ;

    val vertical get() = this == LEFT || this == RIGHT
}

/** The grid overlay: [cross] is the dotted center lines, [thirds] the # that cuts each axis in three. Both may show at once. */
data class GridOptions(val on: Boolean = false, val cross: Boolean = true, val thirds: Boolean = false)

/** True while the three pills are laid out as columns. */
internal val LocalToolbarVertical = compositionLocalOf { false }

/** How far a screen edge's pull on the held toolbar reaches: one and a half times a strip plus the second toolbar beside it. */
internal val EdgeReach = (PillHeight * 2 + PillGap) * 1.5f

/** The edge [point] is nearest, and how far from it. */
private fun closest(point: Offset, width: Float, height: Float): Pair<ToolbarEdge, Float> = listOf(
    ToolbarEdge.TOP to point.y,
    ToolbarEdge.BOTTOM to height - point.y,
    ToolbarEdge.LEFT to point.x,
    ToolbarEdge.RIGHT to width - point.x,
).minBy { it.second }

/** The edge the toolbar held at [point] turns to: the nearest one, once it is within [reach] of it. Null when it is not near any. */
internal fun edgeWithin(point: Offset, width: Float, height: Float, reach: Float): ToolbarEdge? =
    closest(point, width, height).takeIf { it.second <= reach }?.first

/** The edge the toolbar goes to when let go at [point]. */
internal fun edgeToSettle(point: Offset, width: Float, height: Float): ToolbarEdge = closest(point, width, height).first

/**
 * Lets a long press on the tool strip pick it up when "Move toolbar" is on. It then follows the finger wherever it goes, turning upright
 * once it is within reach of an edge, and settles on the closest edge only when the finger lifts. A touch that moves
 * before the press is long goes through to the buttons, so the strip still scrolls.
 */
@Composable
internal fun Modifier.toolbarMover(state: EditorState, screenWidth: () -> Float, screenHeight: () -> Float): Modifier {
    if (!state.moveToolbar) return this
    val config = LocalViewConfiguration.current
    val haptic = LocalHapticFeedback.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { coordinates[0] = it }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Null when the time ran out with the finger still down and still, which is a long press.
            val cancelled = withTimeoutOrNull(config.longPressTimeoutMillis) {
                var result = false
                while (!result) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    result = change == null || change.changedToUp() || (change.position - down.position).getDistance() > config.touchSlop
                }
                result
            }
            if (cancelled != null) return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val start = coordinates[0]?.localToRoot(down.position) ?: return@awaitEachGesture
            state.toolbarDragPoint = start
            // Held, it follows the finger anywhere and flips to the orientation of an edge it comes near. It goes to an edge only when let go.
            state.toolbarLiveEdge = state.toolbarEdge
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                // The bar moves with the finger, so the finger's place in it is not what counts: its place on screen is.
                val at = Offset(
                    state.toolbarDragPoint!!.x + (change.position.x - change.previousPosition.x),
                    state.toolbarDragPoint!!.y + (change.position.y - change.previousPosition.y),
                )
                state.toolbarDragPoint = at
                edgeWithin(at, screenWidth(), screenHeight(), EdgeReach.toPx())?.let { state.toolbarLiveEdge = it }
                if (!change.pressed) break
            }
            state.toolbarDragPoint?.let { at -> state.chooseToolbarEdge(edgeToSettle(at, screenWidth(), screenHeight())) }
            state.toolbarDragPoint = null
            state.toolbarLiveEdge = null
        }
    }
}

