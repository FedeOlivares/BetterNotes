package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TapePathItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFoldTest {
    private val note = StickyNoteItem(1, Frame(100f, 100f, 200f, 200f), color = 0, isOpen = true)

    private fun box(x: Float, y: Float, w: Float, h: Float) =
        ShapeItem(5, Frame(x, y, w, h), ShapeKind.RECTANGLE, strokeColor = 0, strokeWidth = 1f, fillColor = 0)

    private fun tape(vararg xy: Float) = TapePathItem(6, xy, width = 10f, fillColor = 0)

    @Test
    fun `an item more than 80 percent inside folds in, one with less stays`() {
        assertTrue(NoteFold.collapses(box(110f, 110f, 100f, 100f), note))
        // 85 percent inside: 15 of 100 wide hangs out on the left.
        assertTrue(NoteFold.collapses(box(85f, 150f, 100f, 50f), note))
        // 70 percent inside.
        assertFalse(NoteFold.collapses(box(70f, 150f, 100f, 50f), note))
        assertFalse(NoteFold.collapses(box(400f, 400f, 50f, 50f), note))
    }

    @Test
    fun `tape folds in when more than 5 percent of it is on the post-it`() {
        // 200 long, 100 of it (half) on the post-it.
        assertTrue(NoteFold.collapses(tape(0f, 150f, 200f, 150f), note))
        // About 1 percent: 2 of 200 on the post-it, none counting the slack.
        assertFalse(NoteFold.collapses(tape(-200f, 150f, 99f, 150f), note))
        assertFalse(NoteFold.collapses(tape(0f, 10f, 50f, 10f), note))
    }

    @Test
    fun `a straight strip of tape folds in by the same 5 percent`() {
        // 200 long, about 20 of it over the post-it's left edge: an item would need 80 percent, tape does not.
        val strip = ShapeItem(7, Frame(-80f, 145f, 200f, 10f), ShapeKind.TAPE, strokeColor = 0, strokeWidth = 0f, fillColor = 0)
        assertTrue(NoteFold.collapses(strip, note))
        assertFalse(NoteFold.collapses(strip.copy(frame = Frame(-300f, 145f, 200f, 10f)), note))
    }

    @Test
    fun `locked items and other post-its never fold in`() {
        assertFalse(NoteFold.collapses(box(110f, 110f, 50f, 50f).copy(locked = true), note))
        assertFalse(NoteFold.collapses(note.copy(id = 9), note))
    }

    @Test
    fun `a turned post-it is measured on its own edges`() {
        val turned = note.copy(frame = Frame(100f, 100f, 200f, 200f, rotation = 45f))
        // The corner of the unturned square lies outside a post-it turned by 45 degrees.
        assertFalse(NoteFold.collapses(box(100f, 100f, 20f, 20f), turned))
        assertTrue(NoteFold.collapses(box(190f, 190f, 20f, 20f), turned))
    }
}
