package app.betternotes.ui.library

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.betternotes.R
import app.betternotes.library.LibraryRepository
import app.betternotes.library.PhotoLayout
import app.betternotes.ui.MainActivity
import java.io.File
import java.time.LocalDate

internal fun showImportFailed(context: Context) {
    Toast.makeText(context, R.string.library_import_failed, Toast.LENGTH_LONG).show()
}

/** The "+" panel's actions, with the system screens several of them go through first. */
internal class NewItemFlows(
    val panel: NewItemActions,
    val importPdfWithPassword: (Uri, String) -> Unit,
)

@Composable
internal fun rememberNewItemActions(
    repository: LibraryRepository,
    folderId: Long?,
    onOpenDocument: (id: Long, startAction: String?) -> Unit,
    onNewFolder: () -> Unit,
    importPdf: (Uri, String?) -> Unit,
): NewItemFlows {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val title = stringResource(R.string.library_new_note_name, LocalDate.now().toString())
    val open: (Long) -> Unit = { onOpenDocument(it, null) }
    val failed = { showImportFailed(context) }
    fun addPhotos(uris: List<Uri>, layout: PhotoLayout) {
        repository.createFromPhotos(resolver, uris, title, layout, folderId, failed, open)
    }

    // Saved because the system may stop the app while the camera is in front.
    var photoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var photoLayout by rememberSaveable { mutableStateOf(PhotoLayout.ON_PAGE) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = photoTarget
        if (taken && target != null) addPhotos(listOf(target.toUri()), photoLayout)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            camera.launch(newPhotoUri(context).also { photoTarget = it.toString() })
        } else {
            Toast.makeText(context, R.string.library_no_camera, Toast.LENGTH_LONG).show()
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) addPhotos(uris, PhotoLayout.ON_PAGE)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val type = resolver.getType(uri).orEmpty()
            when {
                type.startsWith("audio/") -> repository.createFromAudio(resolver, uri, title, folderId, failed, open)
                type.startsWith("image/") -> addPhotos(listOf(uri), PhotoLayout.ON_PAGE)
                else -> importPdf(uri, null)
            }
        }
    }

    fun openCamera(layout: PhotoLayout) {
        photoLayout = layout
        // A manifest that lists the camera permission must hold it before asking the camera app.
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    return NewItemFlows(
        panel = NewItemActions(
            text = { repository.createNote(title, folderId) { onOpenDocument(it, MainActivity.START_TYPING) } },
            canvas = { repository.createCanvas(title, folderId, open) },
            audio = { repository.createNote(title, folderId) { onOpenDocument(it, MainActivity.START_RECORDING) } },
            fileOrDoc = { filePicker.launch(arrayOf("application/pdf", "audio/*", "image/*")) },
            takePhoto = { openCamera(PhotoLayout.ON_PAGE) },
            scan = { openCamera(PhotoLayout.FILLS_PAGE) },
            photos = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            folder = onNewFolder,
        ),
        importPdfWithPassword = importPdf,
    )
}

private fun newPhotoUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(folder, "photo-${System.nanoTime()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".camera", file)
}
