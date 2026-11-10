package app.betternotes.core.format

import app.betternotes.core.model.Document
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The binder of a notebook: the sources added from its Sources panel. Each source is a small document of its own (a PDF, a
 * canvas, a picture on its own page, a recording) kept in the owner's folder under `sources`, so it opens like any notebook
 * and needs no format of its own. A notebook can also be linked as a source: only its library id is kept here.
 * Nothing here is seen by the library's scan, which only reads the top-level documents.
 */
class Binder(private val owner: DocumentStore) {
    /** Where the sources' folders are. Made on demand. */
    val directory = File(owner.root, "sources")
    private val linksFile = File(owner.root, "source-links")

    /** The sources that are documents of their own, oldest first. A folder that does not read is left out. */
    fun children(): List<Pair<DocumentStore, Document>> {
        val folders = directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") } ?: return emptyList()
        return folders.mapNotNull { folder ->
            val store = DocumentStore(folder)
            try {
                store to store.readDocument()
            } catch (_: IOException) {
                null
            }
        }.sortedBy { it.second.createdAtMs }
    }

    /**
     * Builds a source in a temporary folder and renames it to its id at the end, so a failed or interrupted add leaves nothing
     * behind. Blocks on the disk: call it off the main thread.
     */
    @Throws(IOException::class)
    fun add(build: (DocumentStore) -> Document): DocumentStore {
        directory.mkdirs()
        val temp = File(directory, ".add-" + System.nanoTime())
        try {
            val document = build(DocumentStore(temp))
            val folder = File(directory, java.lang.Long.toHexString(document.id))
            Files.move(temp.toPath(), folder.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return DocumentStore(folder)
        } catch (e: IOException) {
            temp.deleteRecursively()
            throw e
        }
    }

    fun remove(source: DocumentStore) {
        source.root.deleteRecursively()
    }

    /** The library ids of the notebooks linked as sources, in the order they were linked. */
    fun links(): List<Long> = try {
        linksFile.readLines().mapNotNull { it.trim().toLongOrNull() }
    } catch (_: IOException) {
        emptyList()
    }

    fun link(documentId: Long) {
        if (documentId in links()) return
        writeLinks(links() + documentId)
    }

    fun unlink(documentId: Long) = writeLinks(links() - documentId)

    private fun writeLinks(ids: List<Long>) = writeAtomically(linksFile, ids.joinToString("\n").toByteArray())
}
