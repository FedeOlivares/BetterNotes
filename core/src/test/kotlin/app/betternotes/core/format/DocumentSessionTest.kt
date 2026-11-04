package app.betternotes.core.format

import app.betternotes.core.CountingIds
import app.betternotes.core.edit.InsertPage
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.edit.RemovePage
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.ids
import app.betternotes.core.model.Document
import app.betternotes.core.model.Page
import app.betternotes.core.stroke
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocumentSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clockMs = 1000L
    private val store by lazy { DocumentStore(folder.newFolder("doc")) }
    private val document = Document.newNote("Note", PageSize.A4, PageBackground(), 500L, CountingIds())
    private val firstPage = document.pages[0].id

    private fun newSession() = DocumentSession.create(store, document, clock = { clockMs })
    private fun reopen() = DocumentSession.open(store, clock = { clockMs })
    private fun pageFiles() = File(store.root, "pages").listFiles().orEmpty().map { it.name }

    private fun DocumentSession.draw(pageId: Long, id: Long) {
        val content = content(pageId)
        history.perform(ItemsEdit.addOnTop(pageId, content, stroke(id, 0f, 0f, 10f, 10f)), this)
    }

    @Test
    fun `saved ink and pages come back`() {
        val session = newSession()
        session.draw(firstPage, 1)
        session.history.perform(InsertPage(1, Page(77, PageSize.A5)), session)
        session.draw(77, 2)
        session.save()

        val opened = reopen()
        assertEquals(listOf(firstPage, 77L), opened.document.pages.map { it.id })
        assertEquals(listOf(1L), opened.content(firstPage).ids())
        assertEquals(listOf(2L), opened.content(77).ids())
        assertFalse(opened.hasUnsavedChanges)
    }

    @Test
    fun `a saved document records when it changed`() {
        val session = newSession()
        session.draw(firstPage, 1)
        clockMs = 4242
        session.save()
        assertEquals(4242L, reopen().document.modifiedAtMs)
    }

    @Test
    fun `saving with no changes writes nothing`() {
        val session = newSession()
        session.save()
        assertEquals(500L, reopen().document.modifiedAtMs)
        assertTrue(pageFiles().isEmpty())
    }

    @Test
    fun `only changed pages are written`() {
        val session = newSession()
        session.history.perform(InsertPage(1, Page(77, PageSize.A5)), session)
        session.draw(77, 1)
        session.save()
        assertEquals("the untouched first page needs no file", listOf("4d.bnp"), pageFiles())

        session.content(firstPage)
        val modified = File(store.root, "pages/4d.bnp").lastModified()
        session.draw(firstPage, 2)
        session.save()
        assertEquals(modified, File(store.root, "pages/4d.bnp").lastModified())
        assertEquals(2, pageFiles().size)
    }

    @Test
    fun `tracks unsaved changes`() {
        val session = newSession()
        assertFalse(session.hasUnsavedChanges)
        session.draw(firstPage, 1)
        assertTrue(session.hasUnsavedChanges)
        session.save()
        assertFalse(session.hasUnsavedChanges)
        session.document.title = "Renamed"
        assertTrue(session.hasUnsavedChanges)
        session.save()
        assertEquals("Renamed", reopen().document.title)
    }

    @Test
    fun `undo can still reach an evicted page`() {
        val session = newSession()
        session.draw(firstPage, 1)
        session.draw(firstPage, 2)
        session.save()
        session.retainPages(emptySet())
        assertEquals(null, session.loadedContent(firstPage))

        assertTrue(session.history.undo(session))
        assertEquals(listOf(1L), session.content(firstPage).ids())
        session.save()
        assertEquals(listOf(1L), reopen().content(firstPage).ids())
    }

    @Test
    fun `a page with unsaved changes is not evicted`() {
        val session = newSession()
        session.draw(firstPage, 1)
        val page = session.content(firstPage)
        session.retainPages(emptySet())
        assertTrue(page === session.loadedContent(firstPage))
        assertTrue(pageFiles().isEmpty())
    }

    @Test
    fun `a save split across threads keeps edits made after the snapshot`() {
        val session = newSession()
        session.draw(firstPage, 1)
        val pending = session.prepareSave()!!
        session.draw(firstPage, 2)
        pending.write()
        session.finishSave(pending)

        assertTrue(session.hasUnsavedChanges)
        assertEquals(listOf(1L), reopen().content(firstPage).ids())
        session.save()
        assertFalse(session.hasUnsavedChanges)
        assertEquals(listOf(1L, 2L), reopen().content(firstPage).ids())
    }

    @Test
    fun `there is nothing to prepare without changes`() {
        assertEquals(null, newSession().prepareSave())
    }

    @Test
    fun `installing a page hands over items read elsewhere`() {
        val session = newSession()
        assertTrue(session.install(firstPage, listOf(stroke(5, 0f, 0f, 1f, 1f))))
        assertEquals(listOf(5L), session.loadedContent(firstPage)!!.ids())
        assertFalse(session.hasUnsavedChanges)
    }

    @Test
    fun `installing a page already in memory changes nothing`() {
        val session = newSession()
        session.draw(firstPage, 1)
        assertFalse(session.install(firstPage, emptyList()))
        assertEquals(listOf(1L), session.content(firstPage).ids())
    }

    @Test
    fun `retained pages stay in memory`() {
        val session = newSession()
        session.draw(firstPage, 1)
        val page = session.content(firstPage)
        session.retainPages(setOf(firstPage))
        assertTrue(page === session.content(firstPage))
    }

    @Test
    fun `an undone page removal gets its ink back`() {
        val session = newSession()
        session.history.perform(InsertPage(1, Page(77, PageSize.A5)), session)
        session.draw(77, 1)
        session.history.perform(RemovePage(1, session.document.pages[1]), session)
        session.retainPages(emptySet())
        session.save()

        session.history.undo(session)
        assertEquals(listOf(1L), session.content(77).ids())
    }

    @Test
    fun `opening clears files from removed pages and crashes`() {
        val session = newSession()
        session.history.perform(InsertPage(1, Page(77, PageSize.A5)), session)
        session.draw(77, 1)
        session.draw(firstPage, 2)
        session.save()
        session.history.perform(RemovePage(1, session.document.pages[1]), session)
        session.save()
        assertEquals("kept for undo while open", 2, pageFiles().size)
        File(store.root, "pages/old.bnp.tmp").writeBytes(byteArrayOf(1, 2, 3))

        val opened = reopen()
        assertEquals(1, pageFiles().size)
        assertEquals(listOf(2L), opened.content(firstPage).ids())
    }

    @Test
    fun `a page saved over leaves no temp file`() {
        val session = newSession()
        session.draw(firstPage, 1)
        session.save()
        session.draw(firstPage, 2)
        session.save()
        assertTrue(pageFiles().none { it.endsWith(".tmp") })
        assertEquals(listOf(1L, 2L), reopen().content(firstPage).ids())
    }

    @Test
    fun `a damaged page reports corruption rather than an empty page`() {
        val session = newSession()
        session.draw(firstPage, 1)
        session.save()
        val file = File(store.root, "pages").listFiles()!!.single()
        file.writeBytes(file.readBytes().copyOf(10))
        assertThrows(CorruptFileException::class.java) { reopen().content(firstPage) }
    }

    @Test
    fun `asset ids cannot escape the assets folder`() {
        assertTrue(store.assetFile("a.png").parentFile!!.isDirectory)
        assertThrows(IllegalArgumentException::class.java) { store.assetFile("../document.bnd") }
        assertThrows(IllegalArgumentException::class.java) { store.assetFile("..") }
    }

    @Test
    fun `negative page ids make valid file names`() {
        val session = newSession()
        session.history.perform(InsertPage(1, Page(-5L, PageSize.A5)), session)
        session.draw(-5L, 1)
        session.save()
        assertEquals(listOf(1L), reopen().content(-5L).ids())
    }
}
