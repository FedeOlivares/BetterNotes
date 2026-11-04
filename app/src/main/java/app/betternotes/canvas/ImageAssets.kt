package app.betternotes.canvas

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.util.LruCache
import app.betternotes.core.format.DocumentStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** An imported picture, sized in pixels. */
internal class ImportedImage(val assetId: String, val width: Int, val height: Int)

/**
 * The document's pictures: copying them in, and decoding them for painting. Decoded pictures are held
 * in a cache limited by size, because a page of photographs could otherwise fill the heap. Everything
 * but the cache lookup runs on the background thread that the caller supplies.
 */
internal class ImageAssets(private val store: DocumentStore) {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val missing = HashSet<String>()

    fun cached(assetId: String): Bitmap? = cache.get(assetId)

    fun install(assetId: String, bitmap: Bitmap) {
        cache.put(assetId, bitmap)
    }

    /** Remembers a picture that could not be read, so a broken file is not retried on every paint. */
    fun markMissing(assetId: String) {
        missing += assetId
    }

    fun isMissing(assetId: String) = assetId in missing

    /** Decodes small enough for the screen. Returns null if the file cannot be read. */
    fun decode(assetId: String): Bitmap? {
        val file = store.assetFile(assetId)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODED_SIDE) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Copies a picture into the document. Photographs store which way up they are in a tag that a plain
     * decode ignores, so one that is turned is rewritten upright. Returns null if it cannot be read.
     */
    fun import(resolver: ContentResolver, uri: Uri): ImportedImage? = try {
        val assetId = UUID.randomUUID().toString()
        val file = store.assetFile(assetId)
        val opened = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        opened.use { input -> file.outputStream().use { input.copyTo(it) } }
        makeUpright(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) {
            file.delete()
            null
        } else {
            ImportedImage(assetId, bounds.outWidth, bounds.outHeight)
        }
    } catch (e: IOException) {
        Log.e(TAG, "Could not import a picture", e)
        null
    }

    private fun makeUpright(file: File) {
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            return
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return
        }
        val original = BitmapFactory.decodeFile(file.path) ?: return
        val turned = Matrix().apply { setRotate(degrees) }
        val upright = Bitmap.createBitmap(original, 0, 0, original.width, original.height, turned, true)
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    }

    private companion object {
        const val TAG = "ImageAssets"
        const val CACHE_BYTES = 192 * 1024 * 1024
        const val MAX_DECODED_SIDE = 2048
        const val JPEG_QUALITY = 92
    }
}
