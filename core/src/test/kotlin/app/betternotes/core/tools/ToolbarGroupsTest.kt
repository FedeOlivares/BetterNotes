package app.betternotes.core.tools

import app.betternotes.core.tools.ToolbarGroups.Drop
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarGroupsTest {
    private val all = listOf("a", "b", "c", "d", "e")
    private fun singles(vararg keys: String): ToolbarEntries = keys.map { listOf(it) }

    @Test
    fun `completing drops what is unknown or twice and adds what is missing`() {
        val saved = listOf(listOf("c", "x"), listOf("a", "c"), listOf("b"))
        assertEquals(listOf(listOf("c"), listOf("a"), listOf("b"), listOf("d"), listOf("e")), ToolbarGroups.complete(saved, all))
    }

    @Test
    fun `the old saved order of single keys reads as single entries`() {
        assertEquals(singles("c", "a", "b"), ToolbarGroups.decode("c,a,b"))
    }

    @Test
    fun `encoding keeps groups`() {
        val entries = listOf(listOf("a"), listOf("b", "c", "d"), listOf("e"))
        assertEquals("a,b+c+d,e", ToolbarGroups.encode(entries))
        assertEquals(entries, ToolbarGroups.decode("a,b+c+d,e"))
    }

    @Test
    fun `a tool moves to a place by itself`() {
        // Places count with the tool taken out: without a, the list is b c d e, and place 2 is before d.
        assertEquals(singles("b", "c", "a", "d", "e"), ToolbarGroups.move(singles("a", "b", "c", "d", "e"), "a", Drop.Alone(2)))
        assertEquals(singles("e", "a", "b", "c", "d"), ToolbarGroups.move(singles("a", "b", "c", "d", "e"), "e", Drop.Alone(0)))
    }

    @Test
    fun `two tools dropped together become a group, the dragged one after the other`() {
        val result = ToolbarGroups.move(singles("a", "b", "c", "d", "e"), "a", Drop.With(2))
        assertEquals(listOf(listOf("b"), listOf("c"), listOf("d", "a"), listOf("e")), result)
    }

    @Test
    fun `a tool dropped into a group joins it at that place`() {
        val start = listOf(listOf("a"), listOf("b", "c"), listOf("d"), listOf("e"))
        assertEquals(listOf(listOf("a"), listOf("b", "d", "c"), listOf("e")), ToolbarGroups.move(start, "d", Drop.InGroup(1, 1)))
    }

    @Test
    fun `a tool dragged out of a group leaves it, and a group of one dissolves`() {
        val start = listOf(listOf("a"), listOf("b", "c"), listOf("d"))
        assertEquals(listOf(listOf("a"), listOf("c"), listOf("b"), listOf("d")), ToolbarGroups.move(start, "b", Drop.Alone(2)))
    }

    @Test
    fun `moving inside a group reorders it`() {
        val start = listOf(listOf("a", "b", "c"), listOf("d"))
        // Without a the group is b c, so place 1 puts a between them.
        assertEquals(listOf(listOf("b", "a", "c"), listOf("d")), ToolbarGroups.move(start, "a", Drop.InGroup(0, 1)))
    }
}
