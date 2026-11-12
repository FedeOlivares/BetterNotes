package app.betternotes.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as PixelCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Scan mode: the photo just taken, with four corners to drag onto the page. Done flattens that quadrilateral into
 * a straight rectangle and puts it on the note like any other picture. Everything stays on the device.
 */
@Composable
internal fun ScanCrop(state: EditorState, photo: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, photo) {
        value = withContext(Dispatchers.IO) { decodeUpright(context, photo) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.ScanBackdrop)
            .clickable(enabled = false) {}
            .masksInk(state, "scan"),
    ) {
        val source = bitmap
        if (source == null) {
            Text(stringResource(R.string.scan_loading), color = UiColors.OnFilled, modifier = Modifier.align(Alignment.Center))
        } else {
            var corners by remember(source) { mutableStateOf(initialCorners(source)) }
            var working by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            if (working) {
                Text(stringResource(R.string.scan_loading), color = UiColors.OnFilled, modifier = Modifier.align(Alignment.Center))
            }
            Corners(source, corners, { corners = it }, Modifier.fillMaxSize().padding(bottom = 72.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S16),
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.S16),
            ) {
                ScanButton(R.string.scan_cancel) { state.scanPhoto = null }
                ScanButton(R.string.scan_done) {
                    if (working) return@ScanButton
                    working = true
                    val chosen = corners
                    // Flattening and saving a full-size photo takes long enough to freeze the screen on the main thread.
                    scope.launch {
                        val file = withContext(Dispatchers.Default) {
                            val flat = flatten(source, chosen)
                            val folder = File(context.cacheDir, "scan").apply { mkdirs() }
                            File(folder, "scan-${System.currentTimeMillis()}.jpg").also { out ->
                                out.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                flat.recycle()
                            }
                        }
                        state.scanPhoto = null
                        state.importImage(Uri.fromFile(file))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanButton(label: Int, onClick: () -> Unit) {
    Text(
        stringResource(label),
        color = UiColors.OnFilled,
        fontSize = TextSize.S16,
        modifier = Modifier
            .background(UiColors.ScanButton, AppShapes.Rounded24)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.S28, vertical = Spacing.S12),
    )
}

/** Corners in the bitmap's own pixels, clockwise from the top left. */
@Composable
private fun Corners(bitmap: Bitmap, corners: List<Offset>, onChange: (List<Offset>) -> Unit, modifier: Modifier) {
    var grabbed by remember { mutableStateOf(-1) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        modifier.pointerInput(bitmap) {
            val scale = min(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
            val dx = (size.width - bitmap.width * scale) / 2f
            val dy = (size.height - bitmap.height * scale) / 2f
            var current = corners
            detectDragGestures(
                onDragStart = { at ->
                    grabbed = current.indices.minBy {
                        hypot(at.x - (dx + current[it].x * scale), at.y - (dy + current[it].y * scale))
                    }
                },
                onDragEnd = { grabbed = -1 },
                onDragCancel = { grabbed = -1 },
            ) { change, drag ->
                change.consume()
                val i = grabbed
                if (i >= 0) {
                    val moved = current[i] + drag / scale
                    current = current.toMutableList().also {
                        it[i] = Offset(moved.x.coerceIn(0f, bitmap.width.toFloat()), moved.y.coerceIn(0f, bitmap.height.toFloat()))
                    }
                    onChange(current)
                }
            }
        },
    ) {
        val scale = min(size.width / bitmap.width, size.height / bitmap.height)
        val dx = (size.width - bitmap.width * scale) / 2f
        val dy = (size.height - bitmap.height * scale) / 2f
        drawImage(
            image,
            dstOffset = IntOffset(dx.toInt(), dy.toInt()),
            dstSize = IntSize((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt()),
        )
        val points = corners.map { Offset(dx + it.x * scale, dy + it.y * scale) }
        val outline = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
            close()
        }
        drawPath(outline, UiColors.ScanOutline, style = Stroke(3.dp.toPx()))
        for ((i, p) in points.withIndex()) {
            drawCircle(UiColors.White.copy(alpha = if (i == grabbed) 1f else 0.85f), 14.dp.toPx(), p)
            drawCircle(UiColors.ScanOutline, 14.dp.toPx(), p, style = Stroke(3.dp.toPx()))
        }
    }
}

private fun initialCorners(bitmap: Bitmap): List<Offset> {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val mx = w * 0.08f
    val my = h * 0.08f
    return listOf(Offset(mx, my), Offset(w - mx, my), Offset(w - mx, h - my), Offset(mx, h - my))
}

/** Maps the four corners onto a rectangle as wide and tall as the longer opposite edges. */
private fun flatten(source: Bitmap, corners: List<Offset>): Bitmap {
    fun length(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
    val width = max(length(corners[0], corners[1]), length(corners[3], corners[2])).toInt().coerceAtLeast(1)
    val height = max(length(corners[0], corners[3]), length(corners[1], corners[2])).toInt().coerceAtLeast(1)
    val from = FloatArray(8) { if (it % 2 == 0) corners[it / 2].x else corners[it / 2].y }
    val to = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
    val matrix = Matrix().apply { setPolyToPoly(from, 0, to, 0, 4) }
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCanvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** The photo at no more than [MAX_SIDE] pixels a side, turned the way the camera meant it. */
private fun decodeUpright(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val turn = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> turn.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> turn.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> turn.postRotate(270f)
        else -> return decoded
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, turn, true)
}

private const val MAX_SIDE = 3000
