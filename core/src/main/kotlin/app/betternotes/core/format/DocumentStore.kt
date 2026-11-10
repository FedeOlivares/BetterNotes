package app.betternotes.core.format

import app.betternotes.core.model.Document
import app.betternotes.core.model.PageItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A document's folder on disk:
 *
 * ```
 * document.bnd        the description and page order
 * pages/<id>.bnp      one file per page, so a long document loads only what is on screen
 * assets/<assetId>    images, recordings and imported PDFs
 * last-page           the page to continue from
 * ```
 *
 * Every write is atomic: the new bytes reach the disk under a temporary name and only then replace
 * the old file, so a crash leaves either the old version or the new one, never half of each.
 * Calls block on the disk. The class holds no state, so it is safe from any thread as long as two
 * threads do not write the same file.
 */
class DocumentStore(val root: File) {
    private val documentFile = File(root, "document.bnd")
    private val pagesDir = File(root, "pages")
    private val assetsDir = File(root, "assets")
    private val lastPageFile = File(root, "last-page")
    private val templateLanguageFile = File(root, "template-language")

    /** True if a document has been written here. */
    fun exists() = documentFile.exists()

    fun readDocument(): Document = DocumentCodec.decode(documentFile.readBytes())

    /** When the description was last written, or 0 if it never was. Any save rewrites it. */
    fun descriptionModifiedMs() = documentFile.lastModified()

    fun writeDocument(document: Document) = writeDocumentBytes(DocumentCodec.encode(document))

    /** Writes a description already encoded, so the document can keep changing on another thread. */
    fun writeDocumentBytes(bytes: ByteArray) = writeAtomically(documentFile, bytes)

    /** A page with no file has simply never been drawn on, so it reads as empty. */
    fun readPage(pageId: Long): List<PageItem> {
        val file = pageFile(pageId)
        return if (file.exists()) PageCodec.decode(file.readBytes()) else emptyList()
    }

    fun writePage(pageId: Long, items: List<PageItem>) =
        writeAtomically(pageFile(pageId), PageCodec.encode(items))

    /** The page last looked at, to continue from, or null if none was saved. */
    fun readLastPage(): Long? = try {
        if (lastPageFile.exists()) lastPageFile.readText().toLongOrNull() else null
    } catch (_: java.io.IOException) {
        null
    }

    /**
     * The language of the words on this notebook's built-in templates, "en" or "es", or null before one was chosen. It lives
     * beside the document, not in it, so it needs no format change; it moves into the file with the next format.
     */
    fun readTemplateLanguage(): String? = try {
        templateLanguageFile.readText().trim().takeIf { it == "en" || it == "es" }
    } catch (_: IOException) {
        null
    }

    /** [touch] marks the document as changed, so its picture on the home screen is drawn again. */
    fun writeTemplateLanguage(code: String, touch: Boolean) {
        writeAtomically(templateLanguageFile, code.toByteArray())
        if (touch) documentFile.setLastModified(System.currentTimeMillis())
    }

    fun writeLastPage(pageId: Long) = writeAtomically(lastPageFile, pageId.toString().toByteArray())

    /** Where an asset lives. The folder exists after this returns, so the caller can write to it. */
    fun assetFile(assetId: String): File {
        require(assetId.any { it != '.' } && assetId.none { it == '/' || it == '\\' }) {
            "Bad asset id: $assetId"
        }
        assetsDir.mkdirs()
        return File(assetsDir, assetId)
    }

    /**
     * Deletes page files the document no longer lists. A removed page keeps its file while undo can
     * still restore it, so this runs when a document opens, when no undo history exists. It also
     * clears temporary files left by a crash.
     */
    fun deleteOrphanPages(document: Document) {
        val live = document.pages.mapTo(HashSet()) { pageFile(it.id).name }
        pagesDir.listFiles()?.forEach { file ->
            if (file.name !in live) file.delete()
        }
    }

    /**
     * Changes whenever the page's file does, so a reader can tell if what it derived from the page is
     * stale without decoding it. A page with no file has stamp 0.
     */
    fun pageStamp(pageId: Long): Long {
        val file = pageFile(pageId)
        return file.lastModified() * 31 + file.length()
    }

    private fun pageFile(pageId: Long) = File(pagesDir, java.lang.Long.toHexString(pageId) + ".bnp")
}

/** The new bytes reach the disk under a temporary name and only then replace the old file. */
internal fun writeAtomically(file: File, bytes: ByteArray) {
    file.parentFile.mkdirs()
    val temp = File(file.parentFile, file.name + ".tmp")
    FileOutputStream(temp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
