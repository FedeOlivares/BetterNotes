package app.betternotes.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.betternotes.R
import app.betternotes.library.LibraryPreferences
import app.betternotes.ui.editor.ColorWheelSheet
import app.betternotes.ui.theme.BetterNotesTheme

/**
 * The app's color wheel ([ColorWheelSheet]) over a screen that is not the editor, the library and the settings: the very sheet the editor
 * opens, in the theme the app is in. [onPick] gets the color as an opaque ARGB.
 */
@Composable
internal fun ColorWheelDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val theme = LibraryPreferences.get(LocalContext.current).theme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BetterNotesTheme(theme) {
            // A touch outside the sheet closes it, as in the editor.
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                ColorWheelSheet(initial, onPick = onPick, onClose = onDismiss, modifier = Modifier.clickable(enabled = false) {})
            }
        }
    }
}

/**
 * The + circle for the end of a set of colors, with the wheel it opens: the one thing every set of swatches in the library and the settings
 * ends in. [tint] is the color of the plus, and [onPick] gets the color of one's own as an opaque ARGB.
 */
@Composable
internal fun AddCustomColor(initial: Int, tint: Color, onPick: (Int) -> Unit, size: Dp = ADD_SIZE) {
    var open by remember { mutableStateOf(false) }
    if (open) ColorWheelDialog(initial, onPick = { onPick(it); open = false }, onDismiss = { open = false })
    AddColorCircle(stringResource(R.string.editor_add_color), tint, onClick = { open = true }, size = size)
}

private val ADD_SIZE = 40.dp
