package app.betternotes.ui.library

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.betternotes.core.library.FolderStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private val CardShape = RoundedCornerShape(25.dp)
private val RowShape = RoundedCornerShape(18.dp)
private val HeaderHeight = 64.dp
private val FooterHeight = 52.dp
/** A tile is a tall rectangle, a fifth shorter than the Goodnotes reference (624 by 900), which is 624 by 720. */
private const val CARD_RATIO = 624f / 720f
private const val DAYS_SHOWN_BY_NAME = 7L

/** One thing a tile's "..." menu can do. */
internal class CardAction(val label: String, val run: () -> Unit)

@Composable
internal fun DocumentCard(
    entry: LibraryEntry,
    info: DocumentInfo,
    nowMs: Long,
    thumbnails: ThumbnailStore,
    selected: Boolean?,
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
        // As in the Goodnotes reference: the name and the menu on top, the picture in the middle, the date underneath.
        Box(Modifier.fillMaxWidth().height(HeaderHeight)) {
            Text(
                entry.name,
                color = colors.title,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 48.dp),
            )
            if (selected == null) {
                CardMenu(actions, colors.secondaryText, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
            }
        }
        Thumbnail(entry.id, info, thumbnails, Modifier.fillMaxWidth().weight(1f))
        Box(Modifier.fillMaxWidth().height(FooterHeight)) {
            Text(
                formatDate(entry.modifiedAtMs, nowMs),
                color = colors.secondaryText,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 12.dp),
            )
        }
    }
    CardBorder()
    SelectionMark(selected)
}

@Composable
internal fun FolderCard(
    entry: LibraryEntry,
    itemCount: Int,
    previews: List<LibraryEntry>,
    thumbnails: ThumbnailStore,
    selected: Boolean?,
    actions: @Composable () -> List<CardAction>,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val look = folderLook(entry.style)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        FolderArt(Modifier.fillMaxSize(), entry.style)
        if (entry.style.previews && previews.isNotEmpty()) FolderPreviews(previews, thumbnails)
        Text(
            pluralStringResource(R.plurals.library_folder_items, itemCount, itemCount),
            color = look.dim ?: colors.folderDim,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 14.dp, top = 17.dp),
        )
        if (selected == null) {
            CardMenu(actions, look.dots ?: colors.folderDots, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 4.dp))
        }
        Text(
            entry.name,
            color = look.text ?: colors.folderText,
            fontSize = 19.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 19.dp, end = 19.dp, bottom = 19.dp),
        )
    }
    SelectionMark(selected)
}

/** The rows of the list layout. No screenshot shows them, so they borrow the quick switcher's look. */
@Composable
internal fun LibraryRow(
    entry: LibraryEntry,
    itemCount: Int,
    nowMs: Long,
    thumbnails: ThumbnailStore,
    selected: Boolean?,
    actions: @Composable () -> List<CardAction>,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val info = entry.document
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RowShape)
            .background(if (selected == true) colors.accentContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
    ) {
        val pictureModifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp))
        if (info != null) Thumbnail(entry.id, info, thumbnails, pictureModifier) else FolderArt(pictureModifier, entry.style)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(entry.name, color = colors.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (info != null) formatDate(entry.modifiedAtMs, nowMs) else pluralStringResource(R.plurals.library_folder_items, itemCount, itemCount),
                color = colors.secondaryText,
                fontSize = 16.sp,
                maxLines = 1,
            )
        }
        if (selected == null) CardMenu(actions, colors.secondaryText, Modifier)
    }
}

/** No screenshot shows selecting, so this is a plain mark. [selected] is null when not selecting. */
@Composable
private fun SelectionMark(selected: Boolean?) {
    if (selected == null) return
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).then(if (selected) Modifier.border(3.dp, colors.accent, CardShape) else Modifier)) {
        Icon(
            if (selected) LibraryIcons.CheckCircle else LibraryIcons.EmptyCircle,
            null,
            tint = if (selected) colors.accent else colors.secondaryText,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp),
        )
    }
}

/** The folder drawn in the tile's own units, 319 wide, then stretched to the tile's real size. */
@Composable
private fun FolderArt(modifier: Modifier, style: FolderStyle = FolderStyle.DEFAULT) {
    val colors = LocalAppColors.current
    val look = folderLook(style)
    val opacity = style.opacity
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
    Canvas(modifier) {
        val scale = size.width / 319f
        // The tile is taller than the folder was drawn for, so the folder stretches down to fill it.
        val scaleY = if (size.height > size.width * 1.1f) size.height / 330f else scale
        // The opacity belongs to the folder alone: the pictures of the files in it are drawn over it, at full strength.
        drawRect((look.back ?: colors.folderBack).copy(alpha = opacity))
        drawContext.canvas.save()
        drawContext.canvas.scale(scale, scaleY)
        drawPath(paper, (look.paper ?: colors.folderPaper), alpha = opacity)
        drawPath(front, (look.front ?: colors.folderFront), alpha = opacity)
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
internal fun Thumbnail(id: Long, info: DocumentInfo, thumbnails: ThumbnailStore, modifier: Modifier) {
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
                // The picture is square like the box, so little is cut; a cover is drawn around its middle.
                alignment = Alignment.Center,
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
 * As the reference shows them: "Today" and the time, then the day and time for the 6 days before, and
 * after that the date and time. That is by calendar days, so a Wednesday a week ago is not "Wednesday".
 * It shows no older year, so those leave out the time to fit a tile. The patterns follow the device's
 * language and its 12 or 24 hour clock.
 */
@Composable
private fun formatDate(ms: Long, nowMs: Long): String {
    val context = LocalContext.current
    val todayFormat = stringResource(R.string.library_today)
    return remember(ms, nowMs, todayFormat) {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val age = ChronoUnit.DAYS.between(date, today)
        val time = if (DateFormat.is24HourFormat(context)) "Hmm" else "hmm"
        fun format(skeleton: String) =
            DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), ms).toString()
        when {
            age == 0L -> todayFormat.format(format(time))
            age in 1 until DAYS_SHOWN_BY_NAME -> format("EEEE$time")
            date.year == today.year -> format("MMMd$time")
            else -> format("yMMMd")
        }
    }
}

/** The folder's own colors, or null for each one the theme decides. */
internal class FolderLook(val back: Color?, val front: Color?, val paper: Color?, val text: Color?, val dim: Color?, val dots: Color?)

internal fun folderLook(style: FolderStyle): FolderLook {
    if (style.color == FolderStyle.DEFAULT_COLOR) return FolderLook(null, null, null, null, null, null)
    val front = Color(style.color or OPAQUE)
    val light = front.luminance() > LIGHT_FOLDER
    val ink = if (light) Color(0xFF1F2328) else Color.White
    return FolderLook(
        back = lerp(front, Color.Black, 0.12f),
        front = front,
        paper = lerp(front, Color.White, 0.55f),
        text = ink,
        dim = ink.copy(alpha = 0.72f),
        dots = ink,
    )
}

private const val OPAQUE = 0xFF000000.toInt()
private const val LIGHT_FOLDER = 0.55f

/**
 * Up to three of the folder's documents stacked: the first lies in front and shows whole, and each one behind it shows less, only its
 * upper edge. Drawn over the folder at full strength, whatever the folder's opacity is.
 */
@Composable
private fun FolderPreviews(documents: List<LibraryEntry>, thumbnails: ThumbnailStore) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val unit = maxWidth / 319f
        for (position in documents.take(PREVIEWS).indices.reversed()) {
            val entry = documents[position]
            val info = entry.document ?: continue
            val inset = 24f + 14f * position
            val top = 112f - 14f * position
            Thumbnail(
                entry.id,
                info,
                thumbnails,
                Modifier
                    .offset(unit * inset, unit * top)
                    .size(unit * (319f - 2 * inset), unit * (242f - top))
                    .shadow(3.dp, PreviewShape)
                    .clip(PreviewShape)
                    .border(1.dp, Color(0x22000000), PreviewShape),
            )
        }
    }
}

private val PreviewShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
private const val PREVIEWS = 3
