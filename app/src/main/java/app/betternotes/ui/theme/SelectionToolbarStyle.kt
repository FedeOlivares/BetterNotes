package app.betternotes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the toolbar over a selection and its pop-ups look. The layout in `SelectionToolbar.kt` takes every color, size and shape from here, so
 * the look can be replaced without touching what the buttons do.
 */
internal object SelectionToolbarStyle {
    /** How far the toolbar floats from the selection's edge. */
    val gap: Dp = 10.dp

    /** How close to the edge of the screen the toolbar and its pop-ups may come. */
    val screenMargin: Dp = 8.dp

    val popupShape = RoundedCornerShape(24.dp)
    val popupPadding: Dp = 10.dp
    val popupRowHeight: Dp = 52.dp
    val popupWidth: Dp = 260.dp
    val roundButton: Dp = 46.dp
    val roundButtonGap: Dp = 10.dp
    val popupElevation: Dp = 8.dp
    val trashTint: Color = Color(0xFFFF8A8A)

    @Composable
    fun popupColor(): Color = LocalEditorColors.current.panel

    @Composable
    fun popupBorder(): Color = LocalEditorColors.current.pillBorder

    @Composable
    fun roundButtonColor(): Color = LocalEditorColors.current.toolSelected

    @Composable
    fun roundButtonIcon(): Color = LocalEditorColors.current.toolSelectedIcon

    @Composable
    fun rowText(): Color = LocalEditorColors.current.title

    @Composable
    fun rowIcon(): Color = LocalEditorColors.current.icon
}
