package app.betternotes.core.format

import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A whole notebook in one file, so that someone else can open it in their copy of the app: its document and pages (every item
 * stays editable), its assets, its template language and its sources. A source made from the panel is a folder inside the
 * notebook and travels as it is; a notebook linked as a source is copied in as one, since the other person has no such link to
 * follow. It is a zip, with the marker file first so that a stranger's file is refused before anything is written.
 */
object NotebookArchive {
    const val EXTENSION = "bnote"
    private const val MANIFEST = "bn-archive"
    private const val MANIFEST_TEXT = "BNARCHIVE 1\n"
    private const val MAX_ENTRIES = 200_000

    /** The folder's files that stay on this device: where the reader was, and the links, which another device cannot follow. */
    private fun isLocal(name: String) = name == "last-page" || name == "source-links" || name.startsWith(".") || name.endsWith(".tmp")

    /**
     * Writes [root] and the folders of [linked] notebooks to [out], which the caller closes. Blocks on the disk.
     * Pictures, PDFs and sound are already compressed, so a light level keeps this fast.
     */
    @Throws(IOException::class)
    fun write(root: File, out: OutputStream, linked: List<File> = emptyList()) {
        val zip = ZipOutputStream(out.buffered())
        zip.setLevel(Deflater.BEST_SPEED)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(MANIFEST_TEXT.toByteArray())
        zip.closeEntry()
        addFolder(zip, root, "")
        val taken = HashSet<String>()
        root.resolve("sources").list()?.let { taken.addAll(it) }
        for (folder in linked) {
            val id = try {
                DocumentStore(folder).readDocument().id
            } catch (_: IOException) {
                continue
            }
            val name = java.lang.Long.toHexString(id)
            if (taken.add(name)) addFolder(zip, folder, "sources/$name/")
        }
        zip.finish()
        zip.flush()
    }

    private fun addFolder(zip: ZipOutputStream, folder: File, prefix: String) {
        val files = folder.listFiles()?.sortedBy { it.name } ?: return
        for (file in files) {
            if (isLocal(file.name)) continue
            if (file.isDirectory) {
                addFolder(zip, file, prefix + file.name + "/")
            } else {
                zip.putNextEntry(ZipEntry(prefix + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Unpacks a notebook file into the empty folder [into] and gives it a new id, so opening the same file twice, or one this
     * device made, adds a copy instead of replacing anything. Returns the notebook. A file that is not one of ours, is damaged,
     * or was made by a newer app is refused, and what was unpacked is deleted. Blocks on the disk.
     */
    @Throws(IOException::class)
    fun read(input: InputStream, into: File): Document {
        try {
            unpack(input, into)
            val store = DocumentStore(into)
            val document = store.readDocument()
            val copy = Document(IdSource.Random.next(), document.title, document.kind, document.createdAtMs, document.modifiedAtMs, document.pages)
            store.writeDocument(copy)
            return copy
        } catch (e: IOException) {
            into.deleteRecursively()
            throw e
        } catch (e: RuntimeException) {
            into.deleteRecursively()
            throw CorruptFileException("Not a notebook file", e)
        }
    }

    private fun unpack(input: InputStream, into: File) {
        into.mkdirs()
        val base = into.canonicalFile
        val zip = ZipInputStream(input.buffered())
        val first = zip.nextEntry
        if (first == null || first.name != MANIFEST || !String(zip.readNBytes(MANIFEST_TEXT.length)).startsWith("BNARCHIVE")) {
            throw CorruptFileException("Not a notebook file")
        }
        var count = 0
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++count > MAX_ENTRIES) throw CorruptFileException("Too many files")
            // A name that climbs out of the folder is how a hostile archive overwrites other files.
            val target = File(base, entry.name).canonicalFile
            if (target != base && !target.path.startsWith(base.path + File.separator)) throw CorruptFileException("Bad file name")
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
        }
        if (!File(base, "document.bnd").isFile) throw CorruptFileException("No notebook in the file")
    }
}
