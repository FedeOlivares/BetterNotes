package app.betternotes.core.edit

import app.betternotes.core.format.PageCodec
import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.Layer
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageLayers
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayersTest {
    private fun box(id: Long, x: Float, layer: Long = 0) =
        ShapeItem(id, Frame(x, 0f, 10f, 10f, 0f), ShapeKind.RECTANGLE, layer = layer)

    private fun target(content: PageContent) = object : EditTarget {
        override val document get() = error("no document")
        override fun content(pageId: Long) = content
    }

    private fun ids(content: PageContent) = content.toList().map { it.id }

    @Test
    fun `a page with no layer table has one default layer and keeps its old bytes`() {
        val items = listOf(box(1, 0f), box(2, 20f))
        val plain = PageCodec.encode(items)
        val withDefault = PageCodec.encode(items, layers = PageLayers.Single)
        assertTrue(plain.contentEquals(withDefault))
        val read = PageCodec.decodePage(plain)
        assertTrue(read.layers.isDefault)
        assertEquals(items, read.items)
    }

    @Test
    fun `layers survive a round trip, with items on their layers`() {
        val layers = PageLayers(listOf(Layer(0), Layer(5, "Ink", visible = false), Layer(7)))
        val items = listOf(box(1, 0f, 0), box(2, 20f, 0), box(3, 40f, 5), box(4, 60f, 7))
        val read = PageCodec.decodePage(PageCodec.encode(items, layers = layers))
        assertEquals(layers, read.layers)
        assertEquals(items, read.items)
        assertEquals(listOf(0L, 0L, 5L, 7L), read.items.map { it.layer })
    }

    @Test
    fun `a renamed single layer is saved`() {
        val layers = PageLayers(listOf(Layer(0, "Sketch")))
        val read = PageCodec.decodePage(PageCodec.encode(listOf(box(1, 0f)), layers = layers))
        assertEquals("Sketch", read.layers.all[0].name)
    }

    @Test
    fun `new items go on top of the active layer`() {
        val content = PageContent(
            listOf(box(1, 0f, 0), box(2, 20f, 0), box(3, 40f, 1)),
            PageLayers(listOf(Layer(0), Layer(1))),
        )
        content.activeLayer = 0
        val history = UndoHistory()
        history.perform(ItemsEdit.addOnTop(1, content, box(9, 60f)), target(content))
        assertEquals(listOf(1L, 2L, 9L, 3L), ids(content))
        assertEquals(0L, content[2].layer)
        history.undo(target(content))
        assertEquals(listOf(1L, 2L, 3L), ids(content))
    }

    @Test
    fun `replacing an item keeps its layer`() {
        val content = PageContent(listOf(box(1, 0f, 0), box(2, 20f, 1)), PageLayers(listOf(Layer(0), Layer(1))))
        val edit = ItemsEdit.replace(1, content, intArrayOf(1), listOf(box(2, 25f, 0)))
        edit.apply(target(content))
        assertEquals(1L, content[1].layer)
    }

    @Test
    fun `front and back stay inside the layer`() {
        val content = PageContent(
            listOf(box(1, 0f, 0), box(2, 20f, 0), box(3, 40f, 0), box(4, 60f, 1)),
            PageLayers(listOf(Layer(0), Layer(1))),
        )
        val moved = rearrange(1, content, intArrayOf(0), ZPlace.FRONT)!!
        moved.edit.apply(target(content))
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(content))
        assertNull(rearrange(1, content, intArrayOf(3), ZPlace.FRONT))
    }

    @Test
    fun `reordering layers re-sorts the items and undoes`() {
        val layers = PageLayers(listOf(Layer(0), Layer(1)))
        val content = PageContent(settleLayers(listOf(box(1, 0f, 0), box(2, 20f, 1), box(3, 40f, 0)), layers), layers)
        assertEquals(listOf(1L, 3L, 2L), ids(content))
        val edit = LayerOps.move(1, content, id = 1, to = 0)!!
        edit.apply(target(content))
        assertEquals(listOf(2L, 1L, 3L), ids(content))
        assertEquals(listOf(1L, 0L), content.layers.all.map { it.id })
        edit.revert(target(content))
        assertEquals(listOf(1L, 3L, 2L), ids(content))
    }

    @Test
    fun `any layer can be hidden and shown again, the top one and the working one too`() {
        val layers = PageLayers(listOf(Layer(0), Layer(1)))
        val content = PageContent(listOf(box(1, 0f, 0), box(2, 100f, 1)), layers)
        assertEquals(1L, content.activeLayer)
        for (id in longArrayOf(1, 0)) {
            LayerOps.setVisible(1, content, id, false)!!.apply(target(content))
            assertFalse(content.layers[id]!!.visible)
            LayerOps.setVisible(1, content, id, true)!!.apply(target(content))
            assertTrue(content.layers[id]!!.visible)
        }
    }

    @Test
    fun `every layer change marks every item dirty, down to one that crosses a tile edge by a pixel`() {
        val layers = PageLayers(listOf(Layer(0), Layer(1)))
        val crossing = ShapeItem(1, Frame(507f, 0f, 6.5f, 10f, 0f), ShapeKind.RECTANGLE, layer = 0)
        val content = PageContent(listOf(crossing, box(2, 3000f, 1)), layers)
        content.takeDirtyBounds()
        LayerOps.setVisible(1, content, 0, false)!!.apply(target(content))
        val dirty = content.takeDirtyBounds()!!
        for (item in content.toList()) {
            val p = item.paintBounds
            assertTrue("$p not inside $dirty", dirty.left <= p.left && dirty.right >= p.right && dirty.top <= p.top && dirty.bottom >= p.bottom)
        }
        assertFalse(content.isShown(content[0]))
        assertTrue(content.isShown(content[1]))
    }

    @Test
    fun `deleting merging and moving items between layers`() {
        val layers = PageLayers(listOf(Layer(0), Layer(1), Layer(2)))
        val items = listOf(box(1, 0f, 0), box(2, 20f, 1), box(3, 40f, 2), box(4, 60f, 1))
        val content = PageContent(settleLayers(items, layers), layers)
        assertEquals(listOf(1L, 2L, 4L, 3L), ids(content))

        val moveEdit = LayerOps.moveItems(1, content, intArrayOf(0), target = 2)!!
        moveEdit.apply(target(content))
        assertEquals(listOf(2L, 4L, 3L, 1L), ids(content))
        assertEquals(2L, content.toList().last().layer)
        moveEdit.revert(target(content))

        val merged = LayerOps.mergeDown(1, content, id = 1)!!
        merged.apply(target(content))
        assertEquals(listOf(0L, 2L), content.layers.all.map { it.id })
        assertEquals(listOf(1L, 2L, 4L, 3L), ids(content))
        assertEquals(listOf(0L, 0L, 0L, 2L), content.toList().map { it.layer })
        merged.revert(target(content))

        val deleted = LayerOps.delete(1, content, id = 1)!!
        deleted.apply(target(content))
        assertEquals(listOf(1L, 3L), ids(content))
        deleted.revert(target(content))
        assertEquals(4, content.size)
        assertNull(LayerOps.delete(1, PageContent(listOf(box(1, 0f))), id = 0))
    }

    @Test
    fun `the active layer falls back to the top when its layer goes`() {
        val layers = PageLayers(listOf(Layer(0), Layer(1)))
        val content = PageContent(emptyList(), layers)
        content.activeLayer = 0
        assertTrue(content.isEditable(box(1, 0f, 0)))
        assertFalse(content.isEditable(box(1, 0f, 1)))
        LayerOps.delete(1, content, id = 0)!!.apply(target(content))
        assertEquals(1L, content.activeLayer)
    }

    @Test
    fun `an item of an unknown layer settles on the bottom layer`() {
        val layers = PageLayers(listOf(Layer(3), Layer(4)))
        val settled = settleLayers(listOf(box(1, 0f, 4), box(2, 20f, 99)), layers)
        assertEquals(listOf(2L, 1L), settled.map { it.id })
        assertEquals(3L, settled[0].layer)
        val item: PageItem = box(5, 0f, 4)
        assertSame(item, item.onLayer(4))
    }

    @Test
    fun `an added item is dirty where it was added`() {
        val layers = PageLayers(listOf(Layer(0), Layer(1)))
        val content = PageContent(listOf(box(1, 0f, 0), box(2, 20f, 1)), layers)
        content.takeDirtyBounds()
        content.activeLayer = 0
        ItemsEdit.addOnTop(1, content, box(3, 1000f)).apply(target(content))
        assertTrue(content.takeDirtyBounds()!!.right >= 1010f)
        assertArrayEquals(intArrayOf(0, 1), content.indicesOn(0))
    }
}
