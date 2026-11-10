package app.betternotes.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.clipboard.ClipEntry
import app.betternotes.core.clipboard.ImageClip
import app.betternotes.core.clipboard.ItemsClip
import app.betternotes.core.clipboard.TextClip
import app.betternotes.ui.theme.LocalEditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ThumbSize = 56.dp
private const val THUMB_PX = 160

/** What was copied lately, newest first. A tap on an entry pastes it into the middle of the view. */
@Composable
internal fun ClipboardSheet(state: EditorState, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val entries = state.clipboard.history.entries.toList()
    PanelSheet(stringResource(R.string.clipboard_title), onClose = { state.clipboardOpen = false }, modifier = modifier) {
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.clipboard_empty),
                color = colors.label,
                fontSize = 14.sp,
                modifier = Modifier.padding(PanelPadding),
            )
        }
        for (entry in entries) ClipRow(entry) { state.pasteEntry(entry) }
        if (entries.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(role = Role.Button) {
                        state.clipboard.history.clear()
                        state.clipboardOpen = false
                    },
            ) {
                Text(stringResource(R.string.clipboard_clear), color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ClipRow(entry: ClipEntry, onPaste: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPaste)
            .padding(horizontal = PanelPadding, vertical = 8.dp),
    ) {
        when (entry) {
            is TextClip -> Text(
                entry.text,
                color = colors.title,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            is ImageClip -> {
                val thumb by produceState<Bitmap?>(null, entry.path) { value = withContext(Dispatchers.IO) { decodeThumb(entry.path) } }
                Box(Modifier.size(ThumbSize).clip(RoundedCornerShape(8.dp)).background(colors.field)) {
                    thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(ThumbSize)) }
                }
                Text(
                    stringResource(R.string.clipboard_picture),
                    color = colors.label,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
            }
            is ItemsClip -> Text(
                pluralStringResource(R.plurals.clipboard_items, entry.count, entry.count),
                color = colors.title,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Box(Modifier.padding(horizontal = PanelPadding).fillMaxWidth().height(1.dp).background(colors.panelDivider))
}

/** A small copy of the picture for the list, read with the least memory the file allows. */
private fun decodeThumb(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
