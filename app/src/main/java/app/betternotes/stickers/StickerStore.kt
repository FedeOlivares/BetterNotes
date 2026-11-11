package app.betternotes.stickers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.betternotes.core.geometry.Frame
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import app.betternotes.canvas.InkRenderer
import app.betternotes.canvas.ItemPainter
import app.betternotes.canvas.StrokeCache
import app.betternotes.core.format.PageCodec
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.VideoItem
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max

/** Whether a selection can become a sticker: video and recordings live in their document's files and do not travel. */
fun canBeSticker(items: List<PageItem>) = items.isNotEmpty() && items.none { it is VideoItem || it is AudioItem }

/**
 * The stickers the owner saved from selections: one folder each under `stickers/`, holding the selected items (in the page file format,
 * moved so that their top left corner is the origin) and the pictures they use. Nothing leaves the device. Calls block on the disk.
 */
class StickerStore(context: Context) {
    private val root = File(context.filesDir, "stickers")

    /** The saved stickers, newest first. */
    fun list(): List<Long> = root.listFiles().orEmpty()
        .mapNotNull { dir -> dir.name.toLongOrNull()?.takeIf { File(dir, ITEMS).exists() } }
        .sortedDescending()

    /**
     * Saves [items] as a new sticker and returns its id, or null if it could not be written. [assetFile] finds the file of a picture
     * the items use, which is copied along.
     */
    fun save(items: List<PageItem>, assetFile: (String) -> File): Long? {
        if (!canBeSticker(items)) return null
        val box = items.map { it.bounds }.reduce(Bounds::union)
        val moved = items.map { it.transformed(Affine.translate(-box.left, -box.top)) }
        val id = System.currentTimeMillis()
        val dir = File(root, id.toString())
        return try {
            dir.mkdirs()
            for (item in moved) {
                if (item is ImageItem) assetFile(item.assetId).takeIf { it.exists() }?.copyTo(File(dir, item.assetId), overwrite = true)
            }
            File(dir, ITEMS).writeBytes(PageCodec.encode(moved))
            id
        } catch (e: IOException) {
            Log.e(TAG, "Could not save a sticker", e)
            dir.deleteRecursively()
            null
        }
    }

    /** The name of the pack sticker [id] was imported with, or null for one saved from a selection. */
    fun packOf(id: Long): String? = File(File(root, id.toString()), PACK).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /** Puts sticker [id] in the set named [pack], or takes it out of its set when [pack] is null. A set exists while a sticker is in it. */
    fun setPack(id: Long, pack: String?): Boolean {
        val file = File(File(root, id.toString()), PACK)
        return try {
            if (pack == null) file.delete() || !file.exists() else {
                file.writeText(pack)
                true
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not change the set of sticker $id", e)
            false
        }
    }

    /**
     * Imports every picture in the folder [tree] (a folder the owner picked) as a sticker of a pack named after the folder, and returns the
     * pack's name and how many were added, or null if the folder cannot be read. Pictures with a see-through background keep it. Blocks.
     */
    fun importPack(resolver: ContentResolver, tree: Uri): Pair<String, Int>? {
        val treeId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val packName = queryName(resolver, folder) ?: treeId.substringAfterLast(':').substringAfterLast('/')
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val pictures = ArrayList<Pair<Uri, String>>()
        try {
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1)?.startsWith("image/") == true) {
                        pictures += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)) to cursor.getString(2).orEmpty()
                    }
                }
            } ?: return null
        } catch (e: SecurityException) {
            return null
        }
        var added = 0
        var id = System.currentTimeMillis()
        for ((uri, _) in pictures) {
            while (File(root, id.toString()).exists()) id++
            if (importPicture(resolver, uri, id, packName)) added++
            id++
        }
        return packName to added
    }

    private fun importPicture(resolver: ContentResolver, uri: Uri, id: Long, pack: String): Boolean {
        val dir = File(root, id.toString())
        return try {
            dir.mkdirs()
            val assetId = UUID.randomUUID().toString()
            val file = File(dir, assetId)
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("No picture")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a picture" }
            val fit = PACK_STICKER_PT / max(bounds.outWidth, bounds.outHeight)
            val item = ImageItem(id, Frame(0f, 0f, bounds.outWidth * fit, bounds.outHeight * fit), assetId)
            File(dir, ITEMS).writeBytes(PageCodec.encode(listOf(item)))
            File(dir, PACK).writeText(pack)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not import a picture", e)
            dir.deleteRecursively()
            false
        }
    }

    private fun queryName(resolver: ContentResolver, document: Uri): String? = try {
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** The items of sticker [id], with their top left corner at the origin, or null if it cannot be read. */
    fun read(id: Long): List<PageItem>? = try {
        PageCodec.decode(File(File(root, id.toString()), ITEMS).readBytes())
    } catch (e: IOException) {
        Log.e(TAG, "Could not read sticker $id", e)
        null
    }

    /** The file of a picture a sticker uses. */
    fun assetFile(id: Long, assetId: String) = File(File(root, id.toString()), assetId)

    fun delete(id: Long) {
        File(root, id.toString()).deleteRecursively()
        thumbnails.remove(id)
    }

    private val thumbnails = LruCache<Long, Bitmap>(THUMBNAILS_KEPT)
    private val strokes = StrokeCache()
    private val ink = InkRenderer()

    /** A small picture of sticker [id], drawn once and kept. Call off the main thread. */
    fun thumbnail(id: Long): Bitmap? {
        thumbnails.get(id)?.let { return it }
        val bitmap = render(id, THUMBNAIL_PX, 4) ?: return null
        thumbnails.put(id, bitmap)
        return bitmap
    }

    /** Writes sticker [id] to [out] as a PNG with a see-through background, its longer side [EXPORT_PX] pixels. Blocks. */
    fun exportPng(id: Long, out: OutputStream): Boolean {
        val bitmap = render(id, EXPORT_PX, 1) ?: return false
        return try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } catch (e: IOException) {
            Log.e(TAG, "Could not export sticker $id", e)
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** Draws sticker [id] on a see-through bitmap whose longer side is [longSide] pixels; [sample] shrinks the pictures it decodes. */
    private fun render(id: Long, longSide: Float, sample: Int): Bitmap? {
        val items = read(id)?.takeIf { it.isNotEmpty() } ?: return null
        val box = items.map { it.bounds }.reduce(Bounds::union)
        val scale = longSide / max(max(box.width, box.height), 1f)
        val bitmap = createBitmap(
            (box.width * scale).toInt().coerceAtLeast(1),
            (box.height * scale).toInt().coerceAtLeast(1),
        )
        val canvas = Canvas(bitmap)
        val matrix = Matrix().apply { setScale(scale, scale) }
        val images = HashMap<String, Bitmap?>()
        val painter = ItemPainter(
            image = { assetId ->
                images.getOrPut(assetId) {
                    BitmapFactory.decodeFile(assetFile(id, assetId).path, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            },
            paintStroke = { c, stroke -> ink.draw(c, strokes.get(stroke), matrix) },
        )
        canvas.withMatrix(matrix) { for (item in items) painter.paint(this, item) }
        return bitmap
    }

    private companion object {
        const val TAG = "StickerStore"
        const val ITEMS = "items.bnp"
        const val PACK = "pack.txt"
        const val PACK_STICKER_PT = 160f
        const val THUMBNAIL_PX = 160f
        const val EXPORT_PX = 1536f
        const val THUMBNAILS_KEPT = 60
    }
}
