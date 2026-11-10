package app.betternotes.fonts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.betternotes.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The file types a font comes as. Some file explorers call them nothing better than binary data, so that is allowed too. */
private val FONT_TYPES = arrayOf(
    "font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf",
    "application/x-font-opentype", "application/vnd.ms-opentype", "application/octet-stream",
)

/**
 * Returns what opens the system's file explorer to choose a font file. The chosen font is copied into the app on a
 * background thread, and a message tells how it went. [onAdded] gets the font's name once it is available.
 */
@Composable
fun rememberFontPicker(onAdded: (String) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { FontLibrary.add(context.contentResolver, uri) }
            val message = when (result) {
                is FontLibrary.AddResult.Added -> resources.getString(R.string.font_added, result.name)
                FontLibrary.AddResult.NotAFont -> resources.getString(R.string.font_not_a_font)
                FontLibrary.AddResult.TooBig -> resources.getString(R.string.font_too_big)
                FontLibrary.AddResult.Failed -> resources.getString(R.string.font_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (result is FontLibrary.AddResult.Added) onAdded(result.name)
        }
    }
    return { launcher.launch(FONT_TYPES) }
}
