package app.betternotes.pdf

import android.content.ContentResolver
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface PdfImportResult {
    /** The document is on disk, in its own folder. Open it with `DocumentSession.open(store)`. */
    class Imported(val store: DocumentStore, val document: Document) : PdfImportResult

    /** The file is encrypted, and no password or a wrong one was given. */
    data object NeedsPassword : PdfImportResult

    class Failed(val cause: Exception) : PdfImportResult
}

/**
 * Copies a PDF into the app's storage as a new document. The original file is never touched, and the
 * app no longer depends on it.
 *
 * A document's page sizes are the PDF's own, read here once. They are stored with the document, so
 * opening it later needs no parsing, and [Document] refuses to change them.
 */
object PdfImporter {
    /**
     * Creates the document in `documentsDir/<id in hex>`. Everything is built in a temporary folder
     * that is renamed at the end, so a failed or interrupted import leaves no document behind.
     * Blocks on the disk and on parsing, so call it off the main thread.
     *
     * An encrypted file is stored decrypted, so the password is never kept or asked for again.
     */
    fun import(
        resolver: ContentResolver,
        uri: Uri,
        documentsDir: File,
        password: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): PdfImportResult {
        val temp = File(documentsDir, ".import-${System.nanoTime()}")
        return try {
            val store = DocumentStore(temp)
            val asset = store.assetFile(ASSET_ID)
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
            input.use { source -> FileOutputStream(asset).use { out -> source.copyTo(out) } }

            val sizes = readPageSizes(asset, password)
            val document = Document.fromPdf(displayName(resolver, uri), ASSET_ID, sizes, nowMs)
            store.writeDocument(document)

            val finalDir = File(documentsDir, java.lang.Long.toHexString(document.id))
            Files.move(temp.toPath(), finalDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            PdfImportResult.Imported(DocumentStore(finalDir), document)
        } catch (_: SecurityException) {
            // PdfRenderer's way of saying the file is encrypted.
            temp.deleteRecursively()
            PdfImportResult.NeedsPassword
        } catch (e: IOException) {
            temp.deleteRecursively()
            PdfImportResult.Failed(e)
        } catch (e: IllegalArgumentException) {
            // A PDF with no pages, which a document cannot represent.
            temp.deleteRecursively()
            PdfImportResult.Failed(e)
        }
    }

    /** Opening a page is the only way the platform offers to learn its size. */
    private fun readPageSizes(asset: File, password: String?): List<PageSize> {
        val decrypted = File(asset.parentFile, asset.name + ".decrypted")
        val sizes = open(asset, password).use { renderer ->
            if (password != null) {
                ParcelFileDescriptor.open(decrypted, WRITE_MODE).use { renderer.write(it, true) }
            }
            List(renderer.pageCount) { index ->
                renderer.openPage(index).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
            }
        }
        if (password != null) {
            Files.move(decrypted.toPath(), asset.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return sizes
    }

    private fun open(file: File, password: String?): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return if (password == null) {
            PdfRenderer(descriptor)
        } else {
            PdfRenderer(descriptor, LoadParams.Builder().setPassword(password).build())
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: DEFAULT_TITLE
        return name.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { DEFAULT_TITLE }
    }

    private const val ASSET_ID = "source.pdf"
    private const val DEFAULT_TITLE = "PDF"
    private const val WRITE_MODE = ParcelFileDescriptor.MODE_WRITE_ONLY or
        ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
}
