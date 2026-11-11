package app.betternotes.search

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Puts the handwriting models, which ship in the APK, where ML Kit looks for downloaded ones, so it finds them
 * and never has to download. ML Kit has no API for bundled models. It keeps downloaded files in
 * `files/mlkit_digital_ink_recognition/` and remembers what it has in `shared_prefs/gms_icing_mdd_*.xml`.
 * The APK carries the five model files and a copy of those preference files, taken from a real download, and this
 * restores both. See docs/handwriting-models.md for how that copy is made again when ML Kit is updated.
 *
 * Runs once per process, off the main thread, and must finish before ML Kit's first use, because ML Kit reads its
 * preference files only once. If anything fails, ML Kit still works as before and can download the models.
 */
internal object InkModelSeeder {
    private const val TAG = "BetterNotesInk"
    private const val ASSET_DIR = "ink-models"
    private const val MODELS_DIR = "mlkit_digital_ink_recognition/shared/datadownload/public"
    private const val GROUPS_PREFS = "gms_icing_mdd_groupsmlkit_digital_ink_recognition.xml"

    /** One model file: where ML Kit expects it, the zip in the APK holding it, and its size once unzipped. */
    private class Entry(val target: String, val zip: String, val size: Long)

    fun seed(context: Context) {
        try {
            val entries = readEntries(context)
            val models = File(context.filesDir, MODELS_DIR)
            val prefs = File(context.dataDir, "shared_prefs")
            // Models ML Kit downloaded itself, or an earlier seeding, are left alone.
            if (File(prefs, GROUPS_PREFS).exists() && hasModelFiles(models)) return
            models.deleteRecursively()
            for (entry in entries) unzip(context, entry, File(models, entry.target))
            // The preference files go last: they are what says the models are there.
            prefs.mkdirs()
            for (name in context.assets.list("$ASSET_DIR/state").orEmpty()) {
                copy(context, "$ASSET_DIR/state/$name", File(prefs, name))
            }
            Log.i(TAG, "Handwriting models restored from the app")
        } catch (e: IOException) {
            Log.w(TAG, "Could not restore the handwriting models from the app", e)
        }
    }

    private fun hasModelFiles(models: File) = models.walkTopDown().any { it.isFile && it.length() > 1_000_000 }

    private fun readEntries(context: Context): List<Entry> =
        context.assets.open("$ASSET_DIR/files.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map {
                val f = it.split('|')
                Entry(f[0], f[1], f[2].toLong())
            }.toList()
        }

    private fun unzip(context: Context, entry: Entry, target: File) {
        target.parentFile!!.mkdirs()
        val temp = File(target.path + ".part")
        ZipInputStream(context.assets.open("$ASSET_DIR/${entry.zip}").buffered()).use { zip ->
            zip.nextEntry ?: throw IOException("${entry.zip} is empty")
            temp.outputStream().use { zip.copyTo(it) }
        }
        if (temp.length() != entry.size || !temp.renameTo(target)) {
            temp.delete()
            throw IOException("${entry.zip} did not unpack to ${entry.size} bytes")
        }
    }

    private fun copy(context: Context, asset: String, target: File) {
        val temp = File(target.path + ".part")
        context.assets.open(asset).use { input -> temp.outputStream().use { input.copyTo(it) } }
        if (!temp.renameTo(target)) throw IOException("Could not write $target")
    }
}
