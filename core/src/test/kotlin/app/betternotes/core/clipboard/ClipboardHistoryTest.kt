package app.betternotes.core.clipboard

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.TextItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {
    @Test
    fun newestComesFirst() {
        val history = ClipboardHistory()
        history.add(TextClip("a"))
        history.add(TextClip("b"))
        assertEquals(listOf(TextClip("b"), TextClip("a")), history.entries)
    }

    @Test
    fun copyingSomethingAgainMovesItUpInsteadOfAddingIt() {
        val history = ClipboardHistory()
        history.add(TextClip("a"))
        history.add(TextClip("b"))
        history.add(TextClip("a"))
        assertEquals(listOf(TextClip("a"), TextClip("b")), history.entries)
    }

    @Test
    fun theOldestEntryFallsOffAndIsHandedBack() {
        val dropped = ArrayList<ClipEntry>()
        val history = ClipboardHistory(capacity = 2) { dropped += it }
        history.add(TextClip("a"))
        history.add(TextClip("b"))
        history.add(TextClip("c"))
        assertEquals(listOf(TextClip("c"), TextClip("b")), history.entries)
        assertEquals(listOf<ClipEntry>(TextClip("a")), dropped)
    }

    @Test
    fun anImageCopiedAgainKeepsTheNewFileAndDropsTheOld() {
        val dropped = ArrayList<ClipEntry>()
        val history = ClipboardHistory { dropped += it }
        history.add(ImageClip("/cache/1", "content://x/1"))
        history.add(ImageClip("/cache/2", "content://x/1"))
        assertEquals(listOf<ClipEntry>(ImageClip("/cache/2", "content://x/1")), history.entries)
        assertEquals(listOf<ClipEntry>(ImageClip("/cache/1", "content://x/1")), dropped)
    }

    @Test
    fun itemsComeBackFromTheirBytes() {
        val item = TextItem(7L, Frame(1f, 2f, 30f, 10f), "hello")
        val clip = ItemsClip.of(listOf(item))
        assertEquals(1, clip.count)
        assertEquals(listOf(item), clip.items())
        assertArrayEquals(clip.bytes, ItemsClip.of(listOf(item)).bytes)
        assertTrue(clip == ItemsClip.of(listOf(item)))
    }

    @Test
    fun clearHandsBackEverything() {
        val dropped = ArrayList<ClipEntry>()
        val history = ClipboardHistory { dropped += it }
        history.add(TextClip("a"))
        history.add(TextClip("b"))
        history.clear()
        assertTrue(history.entries.isEmpty())
        assertEquals(2, dropped.size)
    }
}
