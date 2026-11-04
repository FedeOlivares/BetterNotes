package app.betternotes.ui.library

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.library.DocumentInfo
import app.betternotes.core.library.LibraryEntry
import app.betternotes.library.ThumbnailStore
import app.betternotes.ui.theme.LocalAppColors
import java.util.Calendar
import java.util.Locale

private val CardShape = RoundedCornerShape(25.dp)
private val HeaderHeight = 92.dp
private const val CARD_RATIO = 319f / 320f
private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** One thing a tile's "..." menu can do. */
internal class CardAction(val label: String, val run: () -> Unit)

@Composable
internal fun DocumentCard(
    entry: LibraryEntry,
    info: DocumentInfo,
    nowMs: Long,
    thumbnails: ThumbnailStore,
    actions: @Composable () -> List<CardAction>,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clip(CardShape)
            .background(colors.surface)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
            Text(
                formatDate(entry.modifiedAtMs, nowMs),
                color = colors.secondaryText,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 44.dp),
            )
            Text(
                entry.name,
                color = colors.title,
                fontSize = 19.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, top = 37.dp, end = 44.dp),
            )
            CardMenu(actions, colors.secondaryText, Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 4.dp))
        }
        Thumbnail(entry.id, info, thumbnails, Modifier.fillMaxWidth().weight(1f))
    }
    CardBorder()
}

@Composable
internal fun FolderCard(
    entry: LibraryEntry,
    itemCount: Int,
    actions: @Composable () -> List<CardAction>,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        FolderArt(Modifier.fillMaxSize())
        Text(
            pluralStringResource(R.plurals.library_folder_items, itemCount, itemCount),
            color = colors.folderDim,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 14.dp, top = 17.dp),
        )
        CardMenu(actions, colors.folderDots, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 4.dp))
        Text(
            entry.name,
            color = colors.folderText,
            fontSize = 19.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 19.dp, end = 19.dp, bottom = 19.dp),
        )
    }
}

/** The folder drawn in the tile's own units, 319 wide, then stretched to the tile's real size. */
@Composable
private fun FolderArt(modifier: Modifier) {
    val colors = LocalAppColors.current
    val paper = remember {
        Path().apply {
            moveTo(162f, 67f)
            lineTo(282f, 60f)
            cubicTo(292f, 60f, 300f, 67f, 300f, 78f)
            lineTo(300f, 92f)
            lineTo(170f, 92f)
            close()
        }
    }
    val front = remember {
        Path().apply {
            moveTo(0f, 72f)
            cubicTo(0f, 60f, 10f, 52f, 22f, 52f)
            lineTo(139f, 52f)
            cubicTo(153f, 52f, 160f, 63f, 170f, 70f)
            cubicTo(176f, 75f, 182f, 78f, 190f, 78f)
            lineTo(292f, 78f)
            cubicTo(306f, 78f, 319f, 84f, 319f, 94f)
            lineTo(319f, 330f)
            lineTo(0f, 330f)
            close()
        }
    }
    Canvas(modifier.background(colors.folderBack)) {
        val scale = size.width / 319f
        drawContext.canvas.save()
        drawContext.canvas.scale(scale, scale)
        drawPath(paper, colors.folderPaper)
        drawPath(front, colors.folderFront)
        drawContext.canvas.restore()
    }
}

/** On top of the tile's content, so the picture cannot cover it. */
@Composable
private fun CardBorder() {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .border(1.5.dp, colors.border, CardShape),
    )
}

@Composable
private fun Thumbnail(id: Long, info: DocumentInfo, thumbnails: ThumbnailStore, modifier: Modifier) {
    // Keeps showing the old picture while a changed document's new one is drawn.
    val bitmap by produceState(thumbnails.cached(id, info.savedAtMs), id, info.savedAtMs) {
        thumbnails.load(id, info.savedAtMs)?.let { value = it }
    }
    Box(modifier.background(Color(info.paperColor))) {
        bitmap?.let {
            Image(
                remember(it) { it.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CardMenu(actions: @Composable () -> List<CardAction>, dotColor: Color, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.library_item_menu)
    Box(modifier) {
        Canvas(
            Modifier
                .size(44.dp, 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable { open = true }
                .semantics { contentDescription = description },
        ) {
            val radius = 2.6.dp.toPx()
            val gap = 8.dp.toPx()
            for (i in -1..1) drawCircle(dotColor, radius, Offset(center.x + i * gap, center.y))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (action in actions()) {
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        open = false
                        action.run()
                    },
                )
            }
        }
    }
}

/**
 * As the reference shows them: the day and time within the last week, the date and time this year.
 * Older dates, which it does not show, leave out the time to fit a tile. The patterns follow the
 * device's language and its 12 or 24 hour clock.
 */
@Composable
private fun formatDate(ms: Long, nowMs: Long): String {
    val context = LocalContext.current
    return remember(ms, nowMs) {
        val date = Calendar.getInstance().apply { timeInMillis = ms }
        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val time = if (DateFormat.is24HourFormat(context)) "Hmm" else "hmm"
        val skeleton = when {
            nowMs - ms in 0 until WEEK_MS -> "EEEE$time"
            date[Calendar.YEAR] == now[Calendar.YEAR] -> "MMMd$time"
            else -> "yMMMd"
        }
        DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), date).toString()
    }
}
