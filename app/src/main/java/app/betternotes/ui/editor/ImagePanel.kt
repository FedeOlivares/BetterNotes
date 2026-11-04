package app.betternotes.ui.editor

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.betternotes.R
import app.betternotes.core.model.ImageShape
import app.betternotes.core.tools.ImageOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** The ways a picture can come in, each of which leaves the app and comes back with an answer. */
internal class ImageSources(
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
    val pickFile: () -> Unit,
)

@Composable
internal fun rememberImageSources(state: EditorState): ImageSources {
    val context = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        if (saved && uri != null) state.importImage(uri)
    }
    val openCamera = {
        val photo = newPhotoUri(context)
        pendingPhoto = photo
        camera.launch(photo)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) state.importImage(uri)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.importImage(uri)
    }
    return remember {
        ImageSources(
            takePhoto = {
                // An app that declares the camera permission must hold it even to use another app's camera.
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            pickFile = { files.launch("image/*") },
        )
    }
}

private fun newPhotoUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(folder, "photo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
}

/** The picture tool's row: framing, the camera, recent pictures, and the way into the panel. */
@Composable
internal fun ImageRow(state: EditorState, options: ImageOptions, sources: ImageSources, modifier: Modifier) {
    val colors = LocalEditorColors.current
    val recents = rememberRecentImages(RECENTS_IN_ROW, state.panelOpen)
    Pill(modifier) {
        ToolbarIcon(EditorIcons.List, stringResource(R.string.panel_image_library)) { state.panelOpen = true }
        ToolbarCellBox(onClick = {
            state.updateImage { it.copy(borderWidth = (it.borderWidth + 1) % (ImageOptions.MAX_BORDER + 1)) }
        }) {
            Canvas(Modifier.size(24.dp)) {
                val thick = (2f + options.borderWidth).dp.toPx()
                drawCircle(colors.label, size.minDimension / 2f - thick / 2f, style = Stroke(thick))
            }
        }
        ToolbarCellBox(onClick = {
            state.updateImage { it.copy(cornerRadius = (it.cornerRadius + 1) % (ImageOptions.MAX_CORNER_RADIUS + 1)) }
        }) {
            Canvas(Modifier.size(22.dp)) {
                val radius = (1f + options.cornerRadius).dp.toPx()
                drawRoundRect(
                    colors.icon,
                    Offset.Zero,
                    ComposeSize(size.width, size.height),
                    CornerRadius(radius),
                    style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }
        ToolbarIcon(
            if (options.shape == ImageShape.ELLIPSE) EditorIcons.ImageRound else EditorIcons.ImageSquare,
            stringResource(R.string.panel_image_shape),
        ) {
            state.updateImage {
                it.copy(shape = if (it.shape == ImageShape.ELLIPSE) ImageShape.RECTANGLE else ImageShape.ELLIPSE)
            }
        }
        PillDivider()
        ToolbarIcon(EditorIcons.Camera, stringResource(R.string.tool_camera), onClick = sources.takePhoto)
        for (uri in recents) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).clickable { state.importImage(uri) },
            ) {
                ImageThumbnail(uri)
            }
        }
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

@Composable
internal fun ImagePanel(state: EditorState, sources: ImageSources, notices: Notices, modifier: Modifier) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(canReadImages(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    LaunchedEffect(Unit) {
        if (!permitted) permission.launch(Manifest.permission.READ_MEDIA_IMAGES)
    }
    val recents = rememberRecentImages(RECENTS_IN_PANEL, permitted)
    PanelSheet(stringResource(R.string.tool_image), onClose = { state.panelOpen = false }, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            SourceButton(EditorIcons.Camera, R.string.tool_camera, sources.takePhoto)
            SourceButton(EditorIcons.AddImage, R.string.panel_add_image, sources.pickPhoto)
            SourceButton(EditorIcons.Folder, R.string.panel_from_files, sources.pickFile)
            SourceButton(EditorIcons.Scan, R.string.panel_scan, notices::notBuilt)
        }
        Spacer(Modifier.height(8.dp))
        for (row in recents.chunked(RECENTS_PER_ROW)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(start = PanelPadding, end = PanelPadding, bottom = 10.dp),
            ) {
                for (uri in row) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { state.importImage(uri) },
                    ) {
                        ImageThumbnail(uri)
                    }
                }
                repeat(RECENTS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SourceButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.toolSelected)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, stringResource(label), tint = colors.icon, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ImageThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
            } catch (_: IOException) {
                null
            }
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun rememberRecentImages(limit: Int, refresh: Boolean): List<Uri> {
    val context = LocalContext.current
    val recents by produceState(emptyList<Uri>(), refresh) {
        value = if (canReadImages(context)) withContext(Dispatchers.IO) { recentImages(context, limit) } else emptyList()
    }
    return recents
}

private fun canReadImages(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED

private fun recentImages(context: Context, limit: Int): List<Uri> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val found = ArrayList<Uri>(limit)
    context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Images.Media._ID),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        while (found.size < limit && cursor.moveToNext()) {
            found += ContentUris.withAppendedId(collection, cursor.getLong(0))
        }
    }
    return found
}

private const val RECENTS_IN_ROW = 6
private const val RECENTS_IN_PANEL = 30
private const val RECENTS_PER_ROW = 3
private const val THUMBNAIL_PX = 256
