package app.betternotes.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import app.betternotes.core.clipboard.ClipEntry
import app.betternotes.core.clipboard.ClipboardHistory
import app.betternotes.core.clipboard.ImageClip
import app.betternotes.core.clipboard.ItemsClip
import app.betternotes.core.clipboard.TextClip
import app.betternotes.core.model.PageItem
import java.io.File

/**
 * Keeps what was copied lately, newest first, for the clipboard sheet. Nothing is stored for good: the list lives in
 * memory and the pictures in the cache folder, which is emptied each time the app starts a fresh process.
 *
 * Android lets an app read the clipboard only while it is in front, so the list fills when the sheet opens and
 * whenever something is copied while the editor is showing.
 */
internal class ClipboardTracker(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(ClipboardManager::class.java)
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { captureCurrent() }

    val history get() = shared

    /** Starts watching the clipboard. Call [stop] when the editor goes away. */
    fun start() {
        manager.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        manager.removePrimaryClipChangedListener(listener)
    }

    /** Adds what the clipboard holds now, if it is text or a picture and is not already on top of the list. */
    fun captureCurrent() {
        val clip = manager.primaryClip?.takeIf { it.itemCount > 0 } ?: return
        val item = clip.getItemAt(0)
        val uri = item.uri
        val text = item.text?.toString()
        when {
            uri != null && clip.description.hasMimeType("image/*") -> copyImage(uri)?.let(shared::add)
            !text.isNullOrBlank() -> shared.add(TextClip(text.take(MAX_TEXT_CHARS)))
        }
    }

    /** Remembers items copied inside the app, so they can be pasted again, on any page of the document. */
    fun remember(items: List<PageItem>) {
        if (items.isNotEmpty()) shared.add(ItemsClip.of(items))
    }

    /** A copy of the picture in the cache folder, because the app that offered it may take its permission back. */
    private fun copyImage(uri: Uri): ImageClip? {
        val folder = folder(app).apply { mkdirs() }
        val file = File(folder, "clip-${System.nanoTime()}")
        return try {
            val copied = app.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (copied == 0L || copied > MAX_IMAGE_BYTES) {
                file.delete()
                null
            } else {
                ImageClip(file.absolutePath, uri.toString())
            }
        } catch (_: java.io.IOException) {
            file.delete()
            null
        } catch (_: SecurityException) {
            file.delete()
            null
        }
    }

    private companion object {
        const val MAX_TEXT_CHARS = 20_000
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024

        /** One list for the whole process, so it survives going back to the library and opening another note. */
        val shared = ClipboardHistory { dropped -> (dropped as? ImageClip)?.let { File(it.path).delete() } }

        fun folder(context: Context) = File(context.cacheDir, "clipboard")
    }

    init {
        // The first tracker of a process clears what an earlier process left behind.
        if (!cleared) {
            cleared = true
            folder(app).deleteRecursively()
        }
    }
}

private var cleared = false

/** True when the clip description says the clip holds a picture. */
internal fun ClipData.holdsImage() = description.hasMimeType("image/*")
