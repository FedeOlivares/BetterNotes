package app.betternotes.core.format

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An early build of page format 16 wrote a colored text box as tag 41 with an outline color before the background color. Pages saved that way
 * are on a device, so they must keep opening: reading them with the newer layout threw "Layer table does not match 1 items" and the page never loaded.
 */
class OutlinedTextPageTest {
    private val frame = Frame(1f, 2f, 300f, 40f)

    @Test
    fun `a page saved with the outline layout opens with its text and background`() {
        val background = 0x80F5C401.toInt()
        val item = TextItem(7, frame, "Cover title", 30f, style = TextStyle(backgroundColor = background))
        val current = PageCodec.encode(listOf<PageItem>(item))
        assertEquals(42, current[6].toInt())

        val pattern = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(background).array()
        val at = (0..current.size - 4).first { i -> pattern.indices.all { current[i + it] == pattern[it] } }
        val body = current.copyOfRange(0, current.size - 4)
        val old = body.copyOfRange(0, at) + ByteArray(4) { 0x11 } + body.copyOfRange(at, body.size)
        old[6] = 41
        val crc = CRC32().apply { update(old) }.value.toInt()
        val bytes = old + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc).array()

        assertEquals(listOf<PageItem>(item), PageCodec.decode(bytes))
    }
}
