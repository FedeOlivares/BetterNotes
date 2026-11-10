package app.betternotes.core.format

import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BinderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun owner() = DocumentStore(folder.newFolder("owner")).also {
        it.writeDocument(Document.newNote("Owner", PageSize.A4, PageBackground(), 1L))
    }

    @Test
    fun `sources are listed oldest first and removed with their folder`() {
        val binder = Binder(owner())
        val second = binder.add { store -> Document.newCanvas("Second", PageBackground(), 20L).also { store.writeDocument(it) } }
        binder.add { store -> Document.newCanvas("First", PageBackground(), 10L).also { store.writeDocument(it) } }
        assertEquals(listOf("First", "Second"), binder.children().map { it.second.title })
        binder.remove(second)
        assertEquals(listOf("First"), binder.children().map { it.second.title })
        assertFalse(second.root.exists())
    }

    @Test
    fun `a failed add leaves no folder, and links keep their order without repeats`() {
        val binder = Binder(owner())
        try {
            binder.add { throw java.io.IOException("no") }
        } catch (_: java.io.IOException) {
        }
        assertEquals(emptyList<Any>(), binder.children())
        assertEquals(0, binder.directory.listFiles()?.size ?: 0)
        binder.link(7)
        binder.link(3)
        binder.link(7)
        assertEquals(listOf(7L, 3L), binder.links())
        binder.unlink(7)
        assertEquals(listOf(3L), binder.links())
    }
}
