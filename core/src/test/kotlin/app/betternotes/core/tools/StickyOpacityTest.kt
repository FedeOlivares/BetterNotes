package app.betternotes.core.tools

import app.betternotes.core.format.PageCodec
import app.betternotes.core.model.IdSource
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.CanvasSettings
import app.betternotes.core.settings.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class StickyOpacityTest {
    private val ids = IdSource { 1L }
    private val colors = NoteOptions.STICKY_PALETTE

    private fun options(selected: Int) = NoteOptions(colors = colors.subList(0, 3), selectedColor = selected)

    private fun alpha(argb: Int) = argb ushr 24

    @Test
    fun `a color with no opacity of its own follows the general one`() {
        assertEquals(128, alpha(options(1).newSticky(0f, 0f, ids, general = 0.5f).color))
        assertEquals(255, alpha(options(1).newSticky(0f, 0f, ids).color))
    }

    @Test
    fun `one color at 10 percent while the others follow a general 50 percent`() {
        val withTenth = options(0).withOpacity(colors[0], 0.1f)
        assertEquals(26, alpha(withTenth.newSticky(0f, 0f, ids, general = 0.5f).color))
        assertEquals(128, alpha(withTenth.copy(selectedColor = 1).newSticky(0f, 0f, ids, general = 0.5f).color))
    }

    @Test
    fun `and one color at the general value while the others are at 10 percent`() {
        val solid = options(2).withOpacity(colors[2], 1f)
        assertEquals(255, alpha(solid.newSticky(0f, 0f, ids, general = 0.1f).color))
        assertEquals(26, alpha(solid.copy(selectedColor = 0).newSticky(0f, 0f, ids, general = 0.1f).color))
    }

    @Test
    fun `an opacity belongs to the color, whatever slot it is in, and following the general one clears it`() {
        val tenth = options(0).withOpacity(colors[0], 0.1f)
        val moved = tenth.copy(colors = listOf(colors[4], colors[5], colors[0]), selectedColor = 2)
        assertEquals(0.1f, moved.opacityOf(moved.color, 0.8f), 0f)
        assertEquals(0.8f, moved.withOpacity(colors[0], null).opacityOf(moved.color, 0.8f), 0f)
    }

    @Test
    fun `a note is never made invisible, and the rgb stays as it was`() {
        val note = options(1).withOpacity(colors[1], 0f).newSticky(0f, 0f, ids)
        assertEquals((NoteOptions.MIN_OPACITY * 255f + 0.5f).toInt(), alpha(note.color))
        assertEquals(colors[1] and 0xFFFFFF, note.color and 0xFFFFFF)
    }

    @Test
    fun `a see-through note and the general opacity survive a save`() {
        val note = options(1).newSticky(0f, 0f, ids, general = 0.3f)
        assertEquals(listOf(note), PageCodec.decode(PageCodec.encode(listOf(note))))
        val settings = AppSettings(canvas = CanvasSettings(stickyOpacity = 0.3f))
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        assertEquals(AppSettings(), SettingsCodec.decode(SettingsCodec.encode(AppSettings())))
    }
}
