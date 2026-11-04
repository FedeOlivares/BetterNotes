package app.betternotes.library

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.Page
import app.betternotes.core.template.PageBackground
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** How a picture sits on its new page. */
enum class PhotoLayout {
    /** On an A4 page, inside its margins. */
    ON_PAGE,

    /** The page takes the picture's proportions, and the picture fills it, as a scanned sheet does. */
    FILLS_PAGE,
}

/**
 * Starts documents from pictures and sound. Nothing here needs the network. Calls block on the disk,
 * so make them off the main thread. Each returns the document, written to [store]'s folder.
 */
internal object MediaImport {
    /** Twice the Pad 7's long side, which keeps a photo sharp at high zoom without using all the memory. */
    private const val MAX_IMAGE_SIDE = 6400
    private const val JPEG_QUALITY = 92
    private const val PAGE_MARGIN_PT = 36f
    private const val AUDIO_WIDTH_PT = 220f
    private const val AUDIO_HEIGHT_PT = 44f

    /** One page per picture, in order. */
    fun photos(
        resolver: ContentResolver,
        uris: List<Uri>,
        store: DocumentStore,
        title: String,
        layout: PhotoLayout,
        background: PageBackground,
        nowMs: Long,
        ids: IdSource = IdSource.Random,
    ): Document {
        val pages = uris.map { uri ->
            val assetId = "image-" + java.lang.Long.toHexString(ids.next())
            val size = copyUpright(resolver, uri, store.assetFile(assetId))
            val pageSize = when (layout) {
                PhotoLayout.ON_PAGE -> PageSize.A4
                PhotoLayout.FILLS_PAGE -> PageSize(PageSize.A4.widthPt, PageSize.A4.widthPt * size.height / size.width)
            }
            val page = Page(ids.next(), pageSize, background)
            val frame = frameFor(size, pageSize, layout)
            store.writePage(page.id, listOf(ImageItem(ids.next(), frame, assetId)))
            page
        }
        return write(store, Document(ids.next(), title, DocumentKind.NOTE, nowMs, nowMs, pages))
    }

    /** A note with the recording on its page. */
    fun audio(
        resolver: ContentResolver,
        uri: Uri,
        store: DocumentStore,
        title: String,
        background: PageBackground,
        nowMs: Long,
        ids: IdSource = IdSource.Random,
    ): Document {
        val assetId = "audio-" + java.lang.Long.toHexString(ids.next())
        val file = store.assetFile(assetId)
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
        input.use { source -> FileOutputStream(file).use { source.copyTo(it) } }
        val frame = Frame(PAGE_MARGIN_PT, PAGE_MARGIN_PT, AUDIO_WIDTH_PT, AUDIO_HEIGHT_PT)
        val page = Page(ids.next(), PageSize.A4, background)
        store.writePage(page.id, listOf(AudioItem(ids.next(), frame, assetId, durationMs(file))))
        return write(store, Document(ids.next(), title, DocumentKind.NOTE, nowMs, nowMs, listOf(page)))
    }

    private fun write(store: DocumentStore, document: Document): Document {
        store.writeDocument(document)
        return document
    }

    private fun frameFor(image: Size, page: PageSize, layout: PhotoLayout): Frame {
        if (layout == PhotoLayout.FILLS_PAGE) return Frame(0f, 0f, page.widthPt, page.heightPt)
        val room = page.widthPt - PAGE_MARGIN_PT * 2
        val fit = min(room / image.width, (page.heightPt - PAGE_MARGIN_PT * 2) / image.height)
        val width = image.width * fit
        return Frame((page.widthPt - width) / 2f, PAGE_MARGIN_PT, width, image.height * fit)
    }

    /**
     * Stores a picture already turned the way its EXIF data says, so that nothing drawing it later has
     * to know about EXIF. Returns its size.
     */
    private fun copyUpright(resolver: ContentResolver, uri: Uri, target: File): Size {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE) {
                decoder.setTargetSize(
                    info.size.width * MAX_IMAGE_SIDE / longest,
                    info.size.height * MAX_IMAGE_SIDE / longest,
                )
            }
        }
        FileOutputStream(target).use { out ->
            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            bitmap.compress(format, JPEG_QUALITY, out)
        }
        return Size(bitmap.width, bitmap.height).also { bitmap.recycle() }
    }

    /** Zero if the file does not say, which the player can correct when it opens the file. */
    private fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }
}
