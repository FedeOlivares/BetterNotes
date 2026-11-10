package app.betternotes.ui.library

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.betternotes.R
import app.betternotes.core.format.NotebookArchive
import kotlinx.coroutines.launch
import app.betternotes.core.settings.NewNoteName
import app.betternotes.library.LibraryRepository
import app.betternotes.library.PhotoLayout
import app.betternotes.settings.SettingsRepository
import app.betternotes.ui.MainActivity
import java.io.File
import java.time.LocalDate

internal fun showImportFailed(context: Context) {
    Toast.makeText(context, R.string.library_import_failed, Toast.LENGTH_LONG).show()
}

/** The name a file manager shows for [uri], or the last part of its path when the provider gives none. Blocks on the provider. */
internal fun displayName(resolver: ContentResolver, uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { name -> return name }
            }
        } catch (e: RuntimeException) {
            // A provider that refuses the query still may open the file; the type or the path name can decide.
        }
    }
    return uri.lastPathSegment
}

/** The "+" panel's actions, with the system screens several of them go through first. */
internal class NewItemFlows(
    /** The name a new notebook starts with, and what opens a document once it is made. */
    val notebookTitle: String,
    val open: (Long) -> Unit,
    val panel: NewItemActions,
    val importPdfWithPassword: (Uri, String) -> Unit,
)

@Composable
internal fun rememberNewItemActions(
    repository: LibraryRepository,
    settings: SettingsRepository,
    folderId: Long?,
    onOpenDocument: (id: Long, startAction: String?) -> Unit,
    onNewFolder: () -> Unit,
    onNewNotebook: () -> Unit,
    importPdf: (Uri, String?) -> Unit,
): NewItemFlows {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val naming by settings.settings.collectAsStateWithLifecycle()
    val title = when (naming.newNoteName) {
        NewNoteName.DATED -> stringResource(R.string.settings_note_dated, LocalDate.now().toString())
        NewNoteName.UNTITLED -> stringResource(R.string.settings_note_untitled)
    }
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
            val isNotebookFile = type == "application/octet-stream" || type == "application/zip" ||
                uri.lastPathSegment.orEmpty().endsWith("." + NotebookArchive.EXTENSION)
            when {
                type.startsWith("audio/") -> repository.createFromAudio(resolver, uri, title, folderId, failed, open)
                type.startsWith("image/") -> addPhotos(listOf(uri), PhotoLayout.ON_PAGE)
                // A notebook file from another copy of the app: it comes in as a new notebook, with its sources.
                isNotebookFile -> scope.launch {
                    if (repository.importNotebookFile(resolver, uri, folderId) != null) {
                        Toast.makeText(context, R.string.library_notebook_imported, Toast.LENGTH_SHORT).show()
                    } else {
                        failed()
                    }
                }
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
        notebookTitle = title,
        open = open,
        panel = NewItemActions(
            text = { repository.createText(title, folderId, open) },
            notebook = onNewNotebook,
            canvas = { repository.createNote(title, folderId, open) },
            audio = { repository.createNote(title, folderId) { onOpenDocument(it, MainActivity.START_RECORDING) } },
            fileOrDoc = { filePicker.launch(arrayOf("application/pdf", "audio/*", "image/*", "application/octet-stream", "application/zip")) },
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
