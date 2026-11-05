package app.betternotes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.ui.theme.LocalAppColors
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.opensFrom

/** What the "+" panel can start. */
internal class NewItemActions(
    val text: () -> Unit,
    val canvas: () -> Unit,
    val audio: () -> Unit,
    val fileOrDoc: () -> Unit,
    val takePhoto: () -> Unit,
    val scan: () -> Unit,
    val photos: () -> Unit,
    val folder: () -> Unit,
)

private val PanelShape = RoundedCornerShape(28.dp)
private val TileShape = RoundedCornerShape(22.dp)
private val PanelWidth = 550.dp
private val PanelSide = 22.dp

/** Measured from the owner's screenshot, in its pixels. Its "Link" row is gone, since the app is offline. */
@Composable
internal fun NewItemPanel(actions: NewItemActions, onDismiss: () -> Unit, modifier: Modifier) {
    val colors = LocalAppColors.current
    fun choose(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }

    // Touches outside close the panel and go no further.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    Column(
        modifier
            .opensFrom(OpenFrom.BottomEnd)
            .width(PanelWidth)
            .shadow(16.dp, PanelShape)
            .background(colors.surface, PanelShape)
            .clip(PanelShape)
            // Swallows touches between the rows, which would otherwise reach the area behind.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.fillMaxWidth().height(93.dp)) {
            Text(
                stringResource(R.string.library_new_title),
                color = colors.title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center).padding(top = 1.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = PanelSide)
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, colors.border, CircleShape)
                    .clickable(onClick = onDismiss),
            ) {
                Icon(
                    LibraryIcons.Close,
                    stringResource(R.string.library_close),
                    tint = colors.title,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Row(Modifier.padding(horizontal = PanelSide), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StartTile(LibraryIcons.TextCursor, R.string.library_new_text, null, Modifier.weight(1f), choose(actions.text))
            StartTile(LibraryIcons.Canvas, R.string.library_new_canvas, null, Modifier.weight(1f), choose(actions.canvas))
        }
        Spacer(Modifier.height(17.dp))
        Row(Modifier.padding(horizontal = PanelSide), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StartTile(LibraryIcons.Microphone, R.string.library_new_audio, null, Modifier.weight(1f), choose(actions.audio))
            StartTile(
                LibraryIcons.Document,
                R.string.library_new_file,
                R.string.library_new_file_kinds,
                Modifier.weight(1f),
                choose(actions.fileOrDoc),
            )
        }
        Spacer(Modifier.height(15.dp))
        StartRow(LibraryIcons.Camera, R.string.library_new_photo, R.string.library_new_photo_hint, choose(actions.takePhoto))
        StartRow(LibraryIcons.Scan, R.string.library_new_scan, R.string.library_new_scan_hint, choose(actions.scan))
        StartRow(LibraryIcons.Photos, R.string.library_new_photos, R.string.library_new_photos_hint, choose(actions.photos))
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .padding(horizontal = PanelSide)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(colors.border),
        )
        Spacer(Modifier.height(17.dp))
        StartRow(LibraryIcons.Folder, R.string.library_new_folder_row, null, choose(actions.folder))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StartTile(icon: ImageVector, label: Int, detail: Int?, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(137.dp)
            .clip(TileShape)
            .background(colors.raisedSurface)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, null, tint = colors.onRaisedAccent, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(if (detail == null) 12.dp else 8.dp))
        Text(stringResource(label), color = colors.onRaisedAccent, fontSize = 22.sp, lineHeight = 26.sp)
        if (detail != null) {
            Text(stringResource(detail), color = colors.subtitle, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StartRow(icon: ImageVector, label: Int, hint: Int?, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(69.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = PanelSide),
    ) {
        Icon(icon, null, tint = colors.title, modifier = Modifier.size(30.dp))
        Text(
            stringResource(label),
            color = colors.title,
            fontSize = 22.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 24.dp).weight(1f),
        )
        if (hint != null) {
            Text(stringResource(hint), color = colors.secondaryText, fontSize = 20.sp, letterSpacing = 0.5.sp, maxLines = 1)
        }
    }
}
