package app.betternotes.fonts

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import app.betternotes.core.text.FontFiles
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * The fonts a person added: copies of their .ttf and .otf files in the app's own storage, so that they stay after the
 * original is moved or deleted. A font is known by its file's name; notes store that name, and show the app's own font
 * where it is missing. Safe to use from any thread, as pages are painted off the main thread for export.
 */
object FontLibrary {
    private var folder: File? = null
    private var assets: AssetManager? = null
    private val faces = ConcurrentHashMap<String, Typeface>()
    private val families = ConcurrentHashMap<String, FontFamily>()

    /** The names of the added fonts, sorted. A state, so that the lists showing them follow additions and removals. */
    var names by mutableStateOf<List<String>>(emptyList())
        private set

    /** Finds the saved fonts. Cheap: only the folder's listing is read, each font loads when first drawn. */
    fun install(context: Context) {
        assets = context.assets
        val dir = File(context.filesDir, FOLDER)
        folder = dir
        names = list(dir)
    }

    /** Whether [name] is one of the fonts that come with the app. */
    fun isBuiltIn(name: String) = name in BUILT_IN

    /** The font [name] as [bold] or [italic] text, or null when it is empty, missing or broken, for the caller to use its own. */
    fun face(name: String, bold: Boolean, italic: Boolean): Typeface? {
        if (name.isEmpty()) return null
        val key = "$name|$bold|$italic"
        faces[key]?.let { return it }
        val base = load(name) ?: return null
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style).also { faces[key] = it }
    }

    /** The same font for Compose, which is given the bold and italic face itself. */
    fun family(name: String, bold: Boolean, italic: Boolean): FontFamily? {
        val key = "$name|$bold|$italic"
        families[key]?.let { return it }
        val face = face(name, bold, italic) ?: return null
        return FontFamily(face).also { families[key] = it }
    }

    private fun load(name: String): Typeface? {
        val file = fileOf(name)
        return try {
            when {
                file != null -> Typeface.Builder(file).build()
                name in BUILT_IN -> Typeface.createFromAsset(assets ?: return null, "$ASSET_FOLDER/$name.ttf")
                else -> null
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Adds the font file at [uri]. The name comes from the file; adding one with the same name replaces it. */
    fun add(resolver: ContentResolver, uri: Uri): AddResult {
        val dir = folder ?: return AddResult.Failed
        val fileName = displayName(resolver, uri) ?: uri.lastPathSegment.orEmpty()
        val id = FontFiles.idOf(fileName) ?: return AddResult.NotAFont
        val extension = FontFiles.extensionOf(fileName) ?: return AddResult.NotAFont
        return try {
            dir.mkdirs()
            val temp = File(dir, "$id.part")
            val copied = resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyLimited(it, MAX_BYTES) } }
            if (copied == null || copied > MAX_BYTES) {
                temp.delete()
                return if (copied == null) AddResult.Failed else AddResult.TooBig
            }
            val valid = try {
                Typeface.Builder(temp).build() != null
            } catch (_: RuntimeException) {
                false
            }
            if (!valid) {
                temp.delete()
                return AddResult.NotAFont
            }
            fileOf(id)?.delete()
            if (!temp.renameTo(File(dir, "$id.$extension"))) {
                temp.delete()
                return AddResult.Failed
            }
            forget(id)
            names = list(dir)
            AddResult.Added(id)
        } catch (_: IOException) {
            AddResult.Failed
        }
    }

    fun remove(name: String) {
        fileOf(name)?.delete()
        forget(name)
        folder?.let { names = list(it) }
    }

    private fun forget(name: String) {
        faces.keys.removeAll { it.startsWith("$name|") }
        families.keys.removeAll { it.startsWith("$name|") }
    }

    private fun fileOf(name: String): File? {
        val dir = folder ?: return null
        return FontFiles.EXTENSIONS.map { File(dir, "$name.$it") }.firstOrNull { it.isFile }
    }

    private fun list(dir: File): List<String> =
        dir.listFiles().orEmpty()
            .filter { it.isFile && FontFiles.extensionOf(it.name) != null }
            .map { it.name.substringBeforeLast('.') }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** A font file bigger than this is not a font anyone would want to load for every page. */
    private const val MAX_BYTES = 30L * 1024 * 1024
    private const val FOLDER = "fonts"
    private const val ASSET_FOLDER = "fonts"

    /**
     * Fonts that come with the app, all under the SIL Open Font License 1.1 (notices in assets/fonts/licenses). They are
     * variable fonts at their regular weight, so bold is made from it. A font added with the same name takes their place.
     */
    val BUILT_IN = listOf("Roboto", "Open Sans", "Lora", "Caveat", "Roboto Mono")

    sealed interface AddResult {
        data class Added(val name: String) : AddResult
        data object NotAFont : AddResult
        data object TooBig : AddResult
        data object Failed : AddResult
    }
}

/** Copies at most [limit] bytes plus one, so that a file over the limit shows up without being read to its end. */
private fun java.io.InputStream.copyLimited(out: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total <= limit) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return total
}
