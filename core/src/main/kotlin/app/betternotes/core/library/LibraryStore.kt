package app.betternotes.core.library

import app.betternotes.core.format.ByteReader
import app.betternotes.core.format.ByteWriter
import app.betternotes.core.format.DOCUMENT_KINDS
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.format.LIBRARY_FORMAT_VERSION
import app.betternotes.core.format.decodeFile
import app.betternotes.core.format.encodeFile
import app.betternotes.core.format.writeAtomically
import java.io.File

private const val LIBRARY_MAGIC = "BNLB"

/** Id, an empty name, parent flag, time, star flag, tag count, bin flag and document flag. */
private const val MIN_ENTRY_BYTES = Long.SIZE_BYTES * 2 + 5

/** The file holding how the library is organized. */
object LibraryCodec {
    fun encode(library: Library, version: Int = LIBRARY_FORMAT_VERSION): ByteArray = encodeFile(LIBRARY_MAGIC, version) {
        varint(library.entries.size)
        library.entries.forEach { entry(it) }
    }

    fun decode(bytes: ByteArray): Library = decodeFile(bytes, LIBRARY_MAGIC, LIBRARY_FORMAT_VERSION) {
        Library(List(count(MIN_ENTRY_BYTES)) { entry() })
    }
}

private fun ByteWriter.entry(entry: LibraryEntry) {
    long(entry.id)
    string(entry.name)
    optionalLong(entry.parentId)
    long(entry.modifiedAtMs)
    bool(entry.starred)
    varint(entry.tags.size)
    entry.tags.forEach { string(it) }
    optionalLong(entry.trashedAtMs)
    bool(entry.document != null)
    entry.document?.let {
        DOCUMENT_KINDS.write(this, it.kind)
        int(it.paperColor)
        long(it.savedAtMs)
    }
    if (version >= 2) {
        int(entry.style.color)
        float(entry.style.opacity)
        bool(entry.style.previews)
    }
}

private fun ByteReader.entry(): LibraryEntry {
    val id = long()
    val name = string()
    val parentId = optionalLong()
    val modifiedAtMs = long()
    val starred = bool()
    val tags = List(count(1)) { string() }.toSet()
    val trashedAtMs = optionalLong()
    val document = if (bool()) DocumentInfo(DOCUMENT_KINDS.read(this), int(), long()) else null
    val style = if (version >= 2) FolderStyle(int(), float().coerceIn(0f, 1f), bool()) else FolderStyle.DEFAULT
    return LibraryEntry(id, name, parentId, modifiedAtMs, starred, tags, trashedAtMs, document, style)
}

private fun ByteWriter.optionalLong(value: Long?) {
    bool(value != null)
    value?.let { long(it) }
}

private fun ByteReader.optionalLong(): Long? = if (bool()) long() else null

/**
 * Where the library lives on the disk:
 *
 * ```
 * library.bnl           how things are organized: folders, stars, tags, the bin
 * documents/<hex id>/   one document, laid out by DocumentStore
 * ```
 *
 * A document's folder is the truth about that document. The library file only caches its name and
 * dates, so opening the home screen reads one small file instead of every document, and [scan] tells
 * what to correct. Calls block on the disk.
 */
class LibraryStore(val root: File) {
    private val libraryFile = File(root, "library.bnl")

    val documentsDir = File(root, "documents")

    /** The saved library, or an empty one if nothing has been saved yet. */
    fun read(): Library = if (libraryFile.exists()) LibraryCodec.decode(libraryFile.readBytes()) else Library()

    fun write(library: Library) = writeAtomically(libraryFile, LibraryCodec.encode(library))

    fun documentStore(id: Long) = DocumentStore(File(documentsDir, java.lang.Long.toHexString(id)))

    /** When each document on the disk was last saved. Costs one file listing plus one look per document. */
    fun scan(): Map<Long, Long> {
        val found = HashMap<Long, Long>()
        for (folder in documentsDir.listFiles().orEmpty()) {
            val id = folder.name.toDocumentIdOrNull() ?: continue
            val savedAtMs = DocumentStore(folder).descriptionModifiedMs()
            // A folder with no description is a document whose creation never finished.
            if (savedAtMs != 0L) found[id] = savedAtMs
        }
        return found
    }

    /** Reads what the library needs to know about a document. */
    fun readDocument(id: Long, savedAtMs: Long) = ScannedDocument(documentStore(id).readDocument(), savedAtMs)

    /** Removes a document's files for good. */
    fun deleteDocument(id: Long) {
        documentStore(id).root.deleteRecursively()
    }

    private fun String.toDocumentIdOrNull(): Long? = try {
        java.lang.Long.parseUnsignedLong(this, 16)
    } catch (_: NumberFormatException) {
        null
    }
}
