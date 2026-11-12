package app.betternotes.core.tools

import app.betternotes.core.CountingIds
import app.betternotes.core.FakeTarget
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.template.PageBackground
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverBackgroundTest {
    private val size = PageSize.A4
    private val document = Document.newNote("n", size, PageBackground(), 0L, CountingIds())
    private val pageId = document.pages[0].id
    private val page = FakeTarget(document).content(pageId)

    private fun box(id: Long, x: Float, y: Float, w: Float, h: Float, locked: Boolean = false) =
        ShapeItem(id, Frame(x, y, w, h), ShapeKind.RECTANGLE, strokeColor = 0, strokeWidth = 1f, fillColor = 0, locked = locked)

    private val everything = floatArrayOf(-50f, -50f, size.widthPt + 50f, -50f, size.widthPt + 50f, size.heightPt + 50f, -50f, size.heightPt + 50f)

    @Test
    fun `a shape that reaches the edges of the page, or all but a sliver of them, is the background`() {
        assertTrue(box(1, 0f, 0f, size.widthPt, size.heightPt).isCoverBackground(size))
        assertTrue(box(2, 2f, 3f, size.widthPt - 4f, size.heightPt - 5f).isCoverBackground(size))
        assertFalse(box(3, 20f, 0f, size.widthPt - 40f, size.heightPt).isCoverBackground(size))
        assertFalse(box(4, 0f, 0f, size.widthPt, size.heightPt / 2f).isCoverBackground(size))
    }

    @Test
    fun `a loop never takes the background of a cover, locked or not, whatever it takes of the lasso's settings`() {
        val background = box(1, 0f, 0f, size.widthPt, size.heightPt, locked = true)
        val title = box(2, 100f, 100f, 80f, 30f)
        page.splice(IntArray(0), intArrayOf(0, 1), listOf(background, title))
        for (locked in listOf(false, true)) {
            val picked = selectWithLasso(pageId, page, everything, includeLocked = locked, anyPortion = true, coverSize = size)
            assertFalse(picked.items.any { it.id == 1L })
            assertTrue(picked.items.any { it.id == 2L })
        }
        // Without a cover size, as on an ordinary page, a locked item is taken only with the setting.
        assertTrue(selectWithLasso(pageId, page, everything, includeLocked = true).items.any { it.id == 1L })
        assertFalse(selectWithLasso(pageId, page, everything, includeLocked = false).items.any { it.id == 1L })
    }
}
