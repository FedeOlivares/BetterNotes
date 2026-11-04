package app.betternotes.core.model

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.ids
import app.betternotes.core.stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PageContentTest {
    private val none = IntArray(0)

    private fun item(id: Long, x: Float = id.toFloat()) = stroke(id, x, 0f, x + 1f, 0f, width = 0f)

    @Test
    fun `query returns matches in paint order`() {
        val content = PageContent(listOf(item(1, 0f), item(2, 100f), item(3, 0f)))
        val seen = mutableListOf<Long>()
        content.forEachIntersecting(Bounds(0f, -1f, 10f, 1f)) { _, it -> seen += it.id }
        assertEquals(listOf(1L, 3L), seen)
    }

    @Test
    fun `splice removes and inserts by final position`() {
        val content = PageContent((1L..5L).map { item(it) })
        content.splice(intArrayOf(1, 3), intArrayOf(0, 4), listOf(item(10), item(11)))
        assertEquals(listOf(10L, 1L, 3L, 5L, 11L), content.ids())
    }

    @Test
    fun `splice inserting contiguous positions`() {
        val content = PageContent(listOf(item(1), item(2)))
        content.splice(none, intArrayOf(1, 2, 3), listOf(item(7), item(8), item(9)))
        assertEquals(listOf(1L, 7L, 8L, 9L, 2L), content.ids())
    }

    @Test
    fun `bounds move with their items`() {
        val content = PageContent(listOf(item(1, 0f), item(2, 100f), item(3, 200f)))
        content.splice(intArrayOf(0), none, emptyList())
        val seen = mutableListOf<Long>()
        content.forEachIntersecting(Bounds(99f, -1f, 102f, 1f)) { i, it ->
            assertEquals(0, i)
            seen += it.id
        }
        assertEquals(listOf(2L), seen)
    }

    @Test
    fun `dirty bounds cover removed and added items and reset`() {
        val content = PageContent(listOf(item(1, 0f), item(2, 100f)))
        assertNull(content.takeDirtyBounds())
        content.splice(intArrayOf(0), intArrayOf(1), listOf(item(3, 50f)))
        val dirty = content.takeDirtyBounds()!!
        assertEquals(0f, dirty.left, 0f)
        assertEquals(51f, dirty.right, 0f)
        assertNull(content.takeDirtyBounds())
    }

    @Test
    fun `version changes on every mutation`() {
        val content = PageContent()
        val before = content.version
        content.splice(none, intArrayOf(0), listOf(item(1)))
        assertNotEquals(before, content.version)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unordered indices are rejected`() {
        PageContent(listOf(item(1), item(2))).splice(intArrayOf(1, 0), none, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `insert past the end is rejected`() {
        PageContent().splice(none, intArrayOf(1), listOf(item(1)))
    }

    /** Compares random splices against a plain list, and undoes them all to check the round trip. */
    @Test
    fun `random splices match a plain list and revert cleanly`() {
        val random = Random(42)
        var nextId = 1L
        val start = List(40) { item(nextId++, random.nextFloat() * 1000f) }
        val content = PageContent(start)
        val model = start.toMutableList()
        val undo = ArrayDeque<() -> Unit>()

        repeat(500) {
            val removeAt = model.indices.filter { random.nextInt(8) == 0 }.toIntArray()
            val removed = removeAt.map { model[it] }
            removeAt.reversed().forEach { model.removeAt(it) }

            val finalSize = model.size + random.nextInt(6)
            val insertAt = (0 until finalSize).shuffled(random)
                .take(finalSize - model.size)
                .sorted()
                .toIntArray()
            val added = insertAt.map { item(nextId++, random.nextFloat() * 1000f) }
            insertAt.forEachIndexed { k, position -> model.add(position, added[k]) }

            content.splice(removeAt, insertAt, added)
            assertEquals(model, content.toList())
            undo.addLast { content.splice(insertAt, removeAt, removed) }
        }

        undo.reversed().forEach { it() }
        assertEquals(start, content.toList())

        // The flat bounds array must have survived all of that too.
        val seen = mutableListOf<PageItem>()
        content.forEachIntersecting(Bounds(-1f, -1f, 2000f, 1f)) { _, it -> seen += it }
        assertEquals(start, seen)
        assertTrue(seen.isNotEmpty())
    }
}
