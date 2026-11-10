package app.betternotes.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.ui.icons.EditorIcons
import kotlin.math.roundToInt

/** How long the button takes to fade in and out. */
private const val FADE_MS = 200
private const val SLIDE_MS = 280

/** The button's own look: a blue disc with a white plus. */
private val ADD_BLUE = Color(0xFF2F6FE0)

/**
 * The standard blue "Add cover" button, in the room before the first page of a notebook with no cover. It opens the cover list.
 * It slides down from under the toolbar when the room opens, and back under it when the room closes. It is composed before the
 * toolbar, so the toolbar covers it while it slides.
 */
@Composable
internal fun AddCoverButton(state: EditorState) {
    @Suppress("UNUSED_VARIABLE") val moved = state.viewMoves + state.historyVersion
    val spot = if (state.viewOnly) null else state.addCoverSpot()
    var last by remember { mutableStateOf<FloatArray?>(null) }
    if (spot != null) last = spot
    val slide by animateFloatAsState(if (spot != null) 1f else 0f, tween(SLIDE_MS), label = "addCover")
    val at = last
    if (at == null || slide == 0f) return
    // Where the button's lower edge is while it is hidden: the lower edge of the toolbar's two rows.
    val hiddenBottom = with(androidx.compose.ui.platform.LocalDensity.current) { (EdgeMargin + PillHeight + PillGap + PillHeight).toPx() }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(52.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(ADD_BLUE)
                    .masksInk(state, "addCover")
                    .clickable(enabled = spot != null && slide > 0.9f, role = Role.Button) { state.coverOpen = true }
                    .padding(horizontal = 28.dp),
            ) {
                Text(stringResource(R.string.menu_add_cover), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        },
    ) { measurables, constraints ->
        val button = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val hiddenCenter = hiddenBottom - button.height / 2f
            val centerY = hiddenCenter + (at[1] - hiddenCenter) * slide
            button.place((at[0] - button.width / 2f).roundToInt(), (centerY - button.height / 2f).roundToInt())
        }
    }
}
/**
 * A blue circle with a plus, in the empty room past the last page, which fades in once there is room there for at least
 * a third of a new page and fades out when there is not. It adds a page at the end.
 */
@Composable
internal fun AddPageButton(state: EditorState) {
    // Read so that the button follows the page when it moves.
    @Suppress("UNUSED_VARIABLE") val moved = state.viewMoves + state.historyVersion
    val spot = if (state.viewOnly) null else state.addPageSpot()
    var last by remember { mutableStateOf<FloatArray?>(null) }
    if (spot != null) last = spot
    val alpha by animateFloatAsState(if (spot != null) 1f else 0f, tween(FADE_MS), label = "addPage")
    val at = last
    if (at == null || alpha == 0f) return
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer { this.alpha = alpha }
                    .size(56.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(ADD_BLUE)
                    .masksInk(state, "addPage")
                    .clickable(enabled = spot != null, role = Role.Button, onClickLabel = stringResource(R.string.add_page)) {
                        state.addPageAtEnd()
                    },
            ) {
                Icon(EditorIcons.Plus, stringResource(R.string.add_page), tint = Color.White, modifier = Modifier.size(30.dp))
            }
        },
    ) { measurables, constraints ->
        val button = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            button.place((at[0] - button.width / 2f).roundToInt(), (at[1] - button.height / 2f).roundToInt())
        }
    }
}
