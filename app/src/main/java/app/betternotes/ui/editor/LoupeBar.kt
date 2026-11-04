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
import androidx.compose.runtime.Composable
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
    Column(modifier.fillMaxWidth().fillMaxHeight(LOUPE_SHARE_OF_SCREEN).padding(4.dp)) {
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
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.iconDisabled),
            )
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
private const val LOUPE_SHARE_OF_SCREEN = 0.42f
