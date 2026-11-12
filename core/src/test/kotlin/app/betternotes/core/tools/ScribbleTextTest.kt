package app.betternotes.core.tools

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.ids
import app.betternotes.core.model.Document
import app.betternotes.core.model.TextItem
import app.betternotes.core.template.PageBackground
import app.betternotes.core.stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A scribble rubs out whole items along its way, and a text box that is not being edited is one of them. */
class ScribbleTextTest {
    private val document = Document.newNote("n", PageSize.A4, PageBackground(), 0L, CountingIds())
    private val pageId = document.pages[0].id
    private val content = FakeTarget(document).content(pageId)
    private val scribbleKinds = DEFAULT_ERASABLE + ItemKind.TEXT

    private fun session(kinds: Set<ItemKind>) = EraseSession(pageId, content, EraseMode.WHOLE, 4f, kinds = kinds)

    @Test
    fun `a scribble across a text box takes the whole box, and one beside it leaves it`() {
        content.splice(IntArray(0), intArrayOf(0, 1), listOf(TextItem(1, Frame(100f, 100f, 200f, 40f), "full of words", 14f), stroke(2, 500f, 500f)))
        val near = session(scribbleKinds)
        for (x in listOf(110f, 250f, 120f, 280f, 130f, 270f)) near.eraseTo(x, 120f)
        assertNotNull(near.finish())
        assertEquals(listOf(2L), content.ids())
    }

    @Test
    fun `a scribble beside the box takes nothing`() {
        content.splice(IntArray(0), intArrayOf(0), listOf(TextItem(1, Frame(100f, 100f, 200f, 40f), "full of words", 14f)))
        val away = session(scribbleKinds)
        for (x in listOf(110f, 250f, 120f, 280f)) away.eraseTo(x, 400f)
        assertNull(away.finish())
        assertEquals(listOf(1L), content.ids())
    }

    @Test
    fun `without the text kind a box is left, as the eraser leaves it`() {
        content.splice(IntArray(0), intArrayOf(0), listOf(TextItem(1, Frame(100f, 100f, 200f, 40f), "full of words", 14f)))
        val plain = session(DEFAULT_ERASABLE)
        for (x in listOf(110f, 250f, 120f, 280f)) plain.eraseTo(x, 120f)
        assertNull(plain.finish())
        assertEquals(listOf(1L), content.ids())
    }
}
