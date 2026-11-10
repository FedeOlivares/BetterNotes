package app.betternotes.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.ui.theme.LocalAppColors

/** Where a dragged item would land if let go now. */
internal sealed interface DropTarget {
    /** Inside this folder. */
    data class Into(val folderId: Long) : DropTarget

    /** Before this item, or last when [beforeId] is null, in the folder being shown. */
    data class Before(val beforeId: Long?) : DropTarget

    /** Out of the folder being shown, into the one that holds it. */
    data object Out : DropTarget
}

/** One tile on screen, in the grid's own coordinates. */
internal class Cell(val id: Long, val isFolder: Boolean, val rect: Rect)

/** What a drag is doing: which item, where the finger is, and where it would land. */
internal class DragState {
    var id by mutableStateOf<Long?>(null)
    var finger by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var target by mutableStateOf<DropTarget?>(null)

    /** The line that shows where a reordered item goes, or the tile of the folder it would go into. */
    var marker by mutableStateOf<Rect?>(null)
    var markerIsLine by mutableStateOf(false)

    fun end() {
        id = null
        target = null
        marker = null
    }
}

/**
 * Where [finger] would drop an item, and the rectangle to mark it with. The middle of a folder's tile puts the item in the folder; the rest
 * of a tile, or the space around it, puts it before or after that tile. [listed] says whether the tiles run down one column.
 */
internal fun dropTarget(finger: Offset, cells: List<Cell>, dragged: Long, listed: Boolean): Triple<DropTarget, Rect, Boolean>? {
    if (cells.isEmpty()) return null
    val hit = cells.firstOrNull { finger.x in it.rect.left..it.rect.right && finger.y in it.rect.top..it.rect.bottom }
        ?: cells.minBy { (it.rect.center - finger).getDistanceSquared() }
    if (hit.id == dragged) return null
    if (hit.isFolder && hit.rect.deflate(minOf(hit.rect.width, hit.rect.height) * FOLDER_EDGE).contains(finger)) {
        return Triple(DropTarget.Into(hit.id), hit.rect, false)
    }
    val before = if (listed) finger.y < hit.rect.center.y else finger.x < hit.rect.center.x
    val others = cells.filter { it.id != dragged }
    val next = others.getOrNull(others.indexOfFirst { it.id == hit.id } + 1)?.id
    val r = hit.rect
    // A line with no width or no height, along the edge of the tile the item would go beside.
    val line = when {
        listed && before -> Rect(r.left, r.top, r.right, r.top)
        listed -> Rect(r.left, r.bottom, r.right, r.bottom)
        before -> Rect(r.left, r.top, r.left, r.bottom)
        else -> Rect(r.right, r.top, r.right, r.bottom)
    }
    return Triple(DropTarget.Before(if (before) hit.id else next), line, true)
}

private const val FOLDER_EDGE = 0.22f

/**
 * Holding a tile and dragging it moves it: into a folder, between other tiles, or out of the folder being shown by taking it to the
 * strip at the top. The grid scrolls when the finger is near its top or bottom edge.
 */
@Composable
internal fun Modifier.dragToOrganize(
    enabled: Boolean,
    state: LazyGridState,
    drag: DragState,
    entries: () -> List<Pair<Long, Boolean>>,
    listed: Boolean,
    outZonePx: Float,
    canMoveOut: Boolean,
    onDrop: (id: Long, target: DropTarget) -> Unit,
): Modifier {
    if (!enabled) return this
    val haptic = LocalHapticFeedback.current
    fun cells(): List<Cell> {
        val folders = entries().toMap()
        val order = entries().map { it.first }
        return state.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            val id = info.key as? Long ?: return@mapNotNull null
            if (id !in folders) return@mapNotNull null
            Cell(id, folders.getValue(id), Rect(Offset(info.offset.x.toFloat(), info.offset.y.toFloat()), Size(info.size.width.toFloat(), info.size.height.toFloat())))
        }.sortedBy { order.indexOf(it.id) }
    }
    fun retarget() {
        val dragged = drag.id ?: return
        if (canMoveOut && drag.finger.y < outZonePx) {
            drag.target = DropTarget.Out
            drag.marker = null
            return
        }
        val found = dropTarget(drag.finger, cells(), dragged, listed)
        drag.target = found?.first
        drag.marker = found?.second
        drag.markerIsLine = found?.third ?: false
    }
    val config = LocalViewConfiguration.current
    return pointerInput(listed, canMoveOut) {
        // Watched before the grid sees the touch, so that pulling a held tile up or down is a drag and not a scroll.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val moved = withTimeoutOrNull(config.longPressTimeoutMillis) {
                var result = false
                while (!result) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    result = change == null || change.changedToUp() || (change.position - down.position).getDistance() > config.touchSlop
                }
                result
            }
            if (moved != null) return@awaitEachGesture
            val under = cells().firstOrNull { down.position.x in it.rect.left..it.rect.right && down.position.y in it.rect.top..it.rect.bottom }
                ?: return@awaitEachGesture
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.id = under.id
            drag.grab = down.position - under.rect.topLeft
            drag.finger = down.position
            retarget()
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                change.consume()
                drag.finger = change.position
                retarget()
            }
            val id = drag.id
            val target = drag.target
            drag.end()
            if (id != null && target != null) onDrop(id, target)
        }
    }
}

/** Scrolls the grid while a finger holding a tile rests near its top or bottom edge. */
@Composable
internal fun DragAutoScroll(state: LazyGridState, drag: DragState, heightPx: Float, edgePx: Float) {
    val active = drag.id != null
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val seconds = if (last == 0L) 0f else (now - last) / NANOS_PER_SECOND
            last = now
            val y = drag.finger.y
            val speed = when {
                y < edgePx -> -(edgePx - y) / edgePx
                y > heightPx - edgePx -> (y - (heightPx - edgePx)) / edgePx
                else -> 0f
            }
            if (speed != 0f) state.scrollBy(speed * SCROLL_PX_PER_SECOND * seconds)
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val SCROLL_PX_PER_SECOND = 1400f

/** Draws where the item would land, and the strip for taking it out of a folder. */
@Composable
internal fun DragOverlay(drag: DragState, canMoveOut: Boolean, outZoneHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    if (drag.id == null) return
    val colors = LocalAppColors.current
    Canvas(modifier) {
        val marker = drag.marker ?: return@Canvas
        if (drag.markerIsLine) {
            val thickness = 4.dp.toPx()
            val vertical = marker.width == 0f
            drawRoundRect(
                colors.accent,
                topLeft = if (vertical) Offset(marker.left - thickness / 2, marker.top) else Offset(marker.left, marker.top - thickness / 2),
                size = if (vertical) Size(thickness, marker.height) else Size(marker.width, thickness),
                cornerRadius = CornerRadius(thickness / 2),
            )
        } else {
            drawRoundRect(
                colors.accent,
                topLeft = marker.topLeft,
                size = marker.size,
                cornerRadius = CornerRadius(25.dp.toPx()),
                style = Stroke(4.dp.toPx()),
            )
        }
    }
    if (canMoveOut) {
        Box(
            modifier
                .fillMaxWidth()
                .height(outZoneHeight)
                .background(if (drag.target == DropTarget.Out) colors.accent else colors.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.library_drop_out),
                color = if (drag.target == DropTarget.Out) colors.onAccent else colors.accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
