package app.betternotes.core.library

import app.betternotes.core.format.CorruptFileException
import app.betternotes.core.format.DocumentCodec
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

private fun folder(id: Long, parent: Long? = null, modified: Long = 0L, name: String = "Folder $id") =
    LibraryEntry(id, name, parent, modified)

private fun doc(id: Long, parent: Long? = null, modified: Long = 0L, name: String = "Doc $id") =
    LibraryEntry(id, name, parent, modified, document = DocumentInfo(DocumentKind.NOTE, 0, 0L))

class LibraryTest {
    private fun ids(entries: List<LibraryEntry>) = entries.map { it.id }

    @Test
    fun aFolderListsWhatIsDirectlyInsideIt() {
        val library = Library(listOf(folder(1), doc(2, parent = 1), folder(3, parent = 1), doc(4, parent = 3), doc(5)))
        assertEquals(listOf(1L, 5L).toSet(), ids(library.list(LibraryView.InFolder(null))).toSet())
        assertEquals(listOf(2L, 3L).toSet(), ids(library.list(LibraryView.InFolder(1))).toSet())
        assertEquals(2, library.itemCount(1))
    }

    @Test
    fun foldersAndDocumentsSortTogetherByLatestChange() {
        val library = Library(listOf(doc(1, modified = 30), folder(2, modified = 20), doc(3, modified = 10)))
        assertEquals(listOf(1L, 2L, 3L), ids(library.list(LibraryView.InFolder(null))))
    }

    @Test
    fun aFolderSortsByTheLatestChangeInsideIt() {
        val library = Library(
            listOf(folder(1, modified = 5), doc(2, parent = 1, modified = 50), doc(3, modified = 20)),
        )
        assertEquals(listOf(1L, 3L), ids(library.list(LibraryView.InFolder(null))))
        assertEquals(50L, library.lastActivityMs(library[1]!!))
    }

    @Test
    fun nameSortIgnoresCaseAndAccents() {
        val library = Library(listOf(doc(1, name = "Zeta"), doc(2, name = "álgebra"), doc(3, name = "Beta")))
        assertEquals(listOf(2L, 3L, 1L), ids(library.list(LibraryView.InFolder(null), LibrarySort.NAME)))
    }

    @Test
    fun searchIgnoresAccentsAndFindsItemsInFolders() {
        val library = Library(listOf(folder(1), doc(2, parent = 1, name = "Clínica Quirúrgica"), doc(3, name = "Otra")))
        assertEquals(listOf(2L), ids(library.list(LibraryView.Search("quirurgica"))))
        assertTrue(library.list(LibraryView.Search("   ")).isEmpty())
    }

    @Test
    fun starAndTagViewsSpanFoldersAndSkipTheBin() {
        val library = Library(listOf(folder(1), doc(2, parent = 1), doc(3)))
            .withStar(2, true).withStar(3, true)
            .withTags(2, setOf("anatomía")).withTags(3, setOf("anatomía", "cirugía"))
            .trashed(3, 100)
        assertEquals(listOf(2L), ids(library.list(LibraryView.Starred)))
        assertEquals(listOf(2L), ids(library.list(LibraryView.Tagged("anatomía"))))
        assertEquals(listOf("anatomía"), library.tags)
    }

    @Test
    fun aFolderCannotMoveInsideItself() {
        val library = Library(listOf(folder(1), folder(2, parent = 1), folder(3, parent = 2), doc(4)))
        assertFalse(library.canMove(1, 1))
        assertFalse(library.canMove(1, 3))
        assertTrue(library.canMove(3, 1))
        assertFalse(library.canMove(1, 4))
        assertThrows(IllegalArgumentException::class.java) { library.moved(1, 3) }
        assertEquals(1L, library.moved(4, 1)[4]!!.parentId)
        assertNull(library.moved(3, null)[3]!!.parentId)
    }

    @Test
    fun moveTargetsLeaveOutTheCurrentFolderAndTheFoldersInside() {
        val library = Library(listOf(folder(1), folder(2, parent = 1), folder(3), folder(4)))
            .trashed(4, 10)
        assertEquals(listOf(3L), ids(library.moveTargets(listOf(1L))))
        assertEquals(listOf(3L), ids(library.moveTargets(listOf(2L))))
        assertEquals(listOf(3L), ids(library.moveTargets(listOf(1L, 2L))))
    }

    @Test
    fun aFoldersPathStartsAtTheTop() {
        val library = Library(listOf(folder(1), folder(2, parent = 1), folder(3, parent = 2)))
        assertEquals(listOf(1L, 2L, 3L), ids(library.pathTo(3)))
        assertTrue(library.pathTo(99).isEmpty())
    }

    @Test
    fun aFolderInTheBinHidesWhatIsInsideIt() {
        val library = Library(listOf(folder(1), doc(2, parent = 1))).withStar(2, true).trashed(1, 10)
        assertTrue(library.list(LibraryView.InFolder(null)).isEmpty())
        assertTrue(library.list(LibraryView.Starred).isEmpty())
        assertEquals(listOf(1L), ids(library.list(LibraryView.Bin)))
        assertEquals(listOf(2L), ids(library.restored(1).list(LibraryView.InFolder(1))))
    }

    @Test
    fun restoringGoesToTheTopWhenTheFolderIsInTheBinToo() {
        val library = Library(listOf(folder(1), doc(2, parent = 1))).trashed(2, 10).trashed(1, 20)
        assertEquals(listOf(1L), ids(library.list(LibraryView.Bin)))
        val restored = library.restored(2)
        assertNull(restored[2]!!.parentId)
        assertNull(restored[2]!!.trashedAtMs)
    }

    @Test
    fun deletingAFolderTakesWhatIsInsideIt() {
        val library = Library(listOf(folder(1), doc(2, parent = 1), folder(3, parent = 1), doc(4, parent = 3), doc(5)))
        assertEquals(setOf(2L, 4L), library.documentsWithin(1).toSet())
        assertEquals(listOf(5L), library.deleted(1).entries.map { it.id })
    }

    @Test
    fun aScanCorrectsDocumentsAndKeepsHowTheyAreOrganized() {
        val library = Library(listOf(folder(1), doc(2, parent = 1), doc(3))).withStar(2, true)
        val onDisk = mapOf(2L to 99L, 7L to 5L)
        assertEquals(setOf(2L, 7L), library.staleDocuments(onDisk).toSet())
        assertEquals(setOf(3L), library.missingDocuments(onDisk))

        val updated = library.afterScan(
            listOf(
                ScannedDocument(2, "Renamed", 40, DocumentInfo(DocumentKind.NOTE, 1, 99)),
                ScannedDocument(7, "Found", 50, DocumentInfo(DocumentKind.CANVAS, 2, 5)),
            ),
            library.missingDocuments(onDisk),
        )
        val kept = updated[2]!!
        assertEquals("Renamed", kept.name)
        assertEquals(40L, kept.modifiedAtMs)
        assertEquals(1L, kept.parentId)
        assertTrue(kept.starred)
        assertNull(updated[3])
        assertNull(updated[7]!!.parentId)
        assertTrue(updated.staleDocuments(onDisk).isEmpty())
    }

    @Test
    fun aDocumentsDescriptionFillsItsEntry() {
        val note = Document.newNote("Note", PageSize.A4, PageBackground(color = 0x11223344), 7L)
        val scanned = ScannedDocument(note, savedAtMs = 9L)
        assertEquals(note.id, scanned.id)
        assertEquals(DocumentInfo(DocumentKind.NOTE, 0x11223344, 9L), scanned.info)

        val text = Document.newText("Text", PageSize.A4, PageBackground(), 7L)
        val decoded = DocumentCodec.decode(DocumentCodec.encode(text))
        assertEquals(DocumentKind.TEXT, decoded.kind)
        assertFalse(decoded.canAddPages)

        val pdf = Document.fromPdf("Pdf", "a", listOf(PageSize.A4), 7L)
        assertEquals(0xFFFFFFFF.toInt(), ScannedDocument(pdf, 9L).info.paperColor)
    }
}

class LibraryStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val everything = Library(
        listOf(
            LibraryEntry(
                id = -5L,
                name = "Carpeta ñ",
                parentId = null,
                modifiedAtMs = 1000L,
                starred = true,
                tags = setOf("uno", "dos"),
            ),
            LibraryEntry(
                id = 6L,
                name = "Nota",
                parentId = -5L,
                modifiedAtMs = 2000L,
                trashedAtMs = 3000L,
                document = DocumentInfo(DocumentKind.PDF, 0x7F010203, 4000L),
            ),
        ),
    )

    @Test
    fun aLibraryRoundTrips() {
        val decoded = LibraryCodec.decode(LibraryCodec.encode(everything))
        assertEquals(everything.entries.toList(), decoded.entries.toList())
    }

    /** Pins format version 1. To change it, bump the version and add a migration. */
    @Test
    fun theBytesOfVersionOneAreStable() {
        val expected = Base64.getDecoder().decode(GOLDEN)
        assertArrayEquals(expected, LibraryCodec.encode(everything, version = 1))
        assertEquals(everything.entries.toList(), LibraryCodec.decode(expected).entries.toList())
    }

    @Test
    fun aFolderKeepsItsLookInVersionTwo() {
        val styled = everything.withStyle(-5L, FolderStyle(0xFF336699.toInt(), 0.4f, previews = true))
        val decoded = LibraryCodec.decode(LibraryCodec.encode(styled))
        assertEquals(FolderStyle(0xFF336699.toInt(), 0.4f, true), decoded[-5L]?.style)
        // A version 1 file has no look, so its folders open with the default one.
        assertEquals(FolderStyle.DEFAULT, LibraryCodec.decode(LibraryCodec.encode(styled, version = 1))[-5L]?.style)
    }

    @Test
    fun placingAnItemMovesItAndSetsTheManualOrder() {
        val a = LibraryEntry(1, "A", null, 1)
        val b = LibraryEntry(2, "B", null, 1)
        val c = LibraryEntry(3, "C", null, 1)
        val folder = LibraryEntry(4, "F", null, 1)
        val library = Library(listOf(a, b, c, folder))
        fun names(l: Library, folder: Long? = null) = l.list(LibraryView.InFolder(folder), LibrarySort.MANUAL).map { it.name }
        assertEquals(listOf("C", "A", "B", "F"), names(library.placed(3, null, 1)))
        assertEquals(listOf("A", "B", "F", "C"), names(library.placed(3, null, null)))
        val inside = library.placed(2, 4, null)
        assertEquals(listOf("A", "C", "F"), names(inside))
        assertEquals(listOf("B"), names(inside, 4))
        assertEquals(listOf("A", "B", "C", "F"), names(inside.placed(2, null, 3)))
        // Items shown in another order (by name, say) are first put in that order, so a drop lands where it was seen.
        assertEquals(listOf("C", "B", "F", "A"), names(library.placed(1, null, null, listOf(3, 2, 1))))
        assertEquals(listOf("C", "A", "B", "F"), names(library.placed(1, null, 2, listOf(3, 2, 1))))
        // A folder cannot go into itself, and a sibling must really be in the folder.
        assertEquals(library.entries.toList(), library.placed(4, 4, null).entries.toList())
        assertEquals(library.entries.toList(), library.placed(1, 4, 3).entries.toList())
    }

    @Test
    fun aDamagedFileIsRefused() {
        val bytes = LibraryCodec.encode(everything)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        assertThrows(CorruptFileException::class.java) { LibraryCodec.decode(bytes) }
    }

    @Test
    fun nothingSavedReadsAsAnEmptyLibrary() {
        assertTrue(LibraryStore(temp.root).read().entries.isEmpty())
    }

    @Test
    fun whatIsWrittenCanBeRead() {
        val store = LibraryStore(temp.root)
        store.write(everything)
        assertEquals(everything.entries.toList(), store.read().entries.toList())
    }

    @Test
    fun aScanFindsSavedDocumentsAndSkipsAnythingElse() {
        val store = LibraryStore(temp.root)
        val note = Document.newNote("Note", PageSize.A4, PageBackground(), 7L)
        val documents = store.documentStore(note.id)
        documents.writeDocument(note)
        // Not named after an id, and a document whose creation never finished.
        java.io.File(store.documentsDir, "scratch-note").mkdirs()
        store.documentStore(1234L).root.mkdirs()

        val found = store.scan()
        assertEquals(setOf(note.id), found.keys)
        assertEquals(documents.descriptionModifiedMs(), found[note.id])
        assertEquals("Note", store.readDocument(note.id, found[note.id]!!).name)

        store.deleteDocument(note.id)
        assertTrue(store.scan().isEmpty())
    }

    private companion object {
        const val GOLDEN = "Qk5MQgEC+/////////8KQ2FycGV0YSDDsQDoAwAAAAAAAAECA3VubwNkb3MAAAYAAAAAAAAABE5vdGEB+//////////QBwAAAAAAAAAAAbgLAAAAAAAAAQIDAgF/oA8AAAAAAAAlHwHT"
    }
}
