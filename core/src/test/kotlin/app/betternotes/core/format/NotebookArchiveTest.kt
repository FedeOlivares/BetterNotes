package app.betternotes.core.format

import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.geometry.Frame
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NotebookArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun notebook(name: String, title: String): DocumentStore {
        val store = DocumentStore(folder.newFolder(name))
        val document = Document.newNote(title, PageSize.A4, PageBackground(), 5L)
        store.writeDocument(document)
        val items: List<PageItem> = listOf(TextItem(1, Frame(0f, 0f, 100f, 20f), "hello"))
        store.writePage(document.pages[0].id, items)
        store.assetFile("image-1").writeBytes(byteArrayOf(1, 2, 3))
        store.writeTemplateLanguage("es", touch = false)
        store.writeLastPage(document.pages[0].id)
        return store
    }

    @Test
    fun `a notebook with its sources and a linked notebook opens as a new copy that keeps its items`() {
        val owner = notebook("owner", "Owner")
        val binder = Binder(owner)
        binder.add { store -> Document.newCanvas("Source", PageBackground(), 9L).also { store.writeDocument(it) } }
        val linked = notebook("linked", "Linked")
        val bytes = ByteArrayOutputStream().also { NotebookArchive.write(owner.root, it, listOf(linked.root)) }.toByteArray()

        val target = folder.newFolder("target")
        val copy = NotebookArchive.read(ByteArrayInputStream(bytes), target)
        val store = DocumentStore(target)
        assertEquals("Owner", copy.title)
        assertNotEquals(owner.readDocument().id, copy.id)
        assertEquals(copy.id, store.readDocument().id)
        assertEquals(1, store.readPage(copy.pages[0].id).size)
        assertArrayEquals(byteArrayOf(1, 2, 3), store.assetFile("image-1").readBytes())
        assertEquals("es", store.readTemplateLanguage())
        // The reader's place stays behind, the source comes along and the linked notebook is copied in as a source.
        assertFalse(File(target, "last-page").exists())
        assertEquals(listOf("Source", "Linked").sorted(), Binder(store).children().map { it.second.title }.sorted())
        assertTrue(Binder(store).links().isEmpty())
    }

    @Test
    fun `a file that is not a notebook, or climbs out of its folder, is refused and leaves nothing`() {
        val plain = ByteArrayOutputStream().also { ZipOutputStream(it).apply { putNextEntry(ZipEntry("a.txt")); write(1); close() } }.toByteArray()
        val target = folder.newFolder("t1")
        assertThrows(CorruptFileException::class.java) { NotebookArchive.read(ByteArrayInputStream(plain), target) }
        assertFalse(target.exists())

        val hostile = ByteArrayOutputStream().also {
            ZipOutputStream(it).apply {
                putNextEntry(ZipEntry("bn-archive")); write("BNARCHIVE 1\n".toByteArray()); closeEntry()
                putNextEntry(ZipEntry("../escape.txt")); write(1); closeEntry()
                close()
            }
        }.toByteArray()
        val target2 = folder.newFolder("t2")
        assertThrows(CorruptFileException::class.java) { NotebookArchive.read(ByteArrayInputStream(hostile), target2) }
        assertFalse(File(target2.parentFile, "escape.txt").exists())
    }

    private fun File(parent: java.io.File, name: String) = java.io.File(parent, name)
}
