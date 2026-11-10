package app.betternotes.core.clipboard

import app.betternotes.core.format.PageCodec
import app.betternotes.core.model.PageItem

/** One thing that was copied. The same entries can hold stickers later, since items are stored as a page's bytes are. */
sealed interface ClipEntry {
    /** What two copies of the same thing have in common, so a repeat moves the entry to the top instead of adding one. */
    val key: String
}

data class TextClip(val text: String) : ClipEntry {
    override val key get() = "t:$text"
}

/** A picture, kept as a file the app made a copy of. [path] is where, and [label] is for the list. */
data class ImageClip(val path: String, val source: String, val label: String = "") : ClipEntry {
    override val key get() = "i:$source"
}

/** Items copied inside the app, as the bytes of a page file, so they paste on any page. */
class ItemsClip(val bytes: ByteArray, val count: Int) : ClipEntry {
    override val key get() = "p:${bytes.contentHashCode()}:${bytes.size}"

    /** The items again, with the ids they had. Give them new ones before putting them on a page. */
    fun items(): List<PageItem> = PageCodec.decode(bytes)

    override fun equals(other: Any?) = other is ItemsClip && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()

    companion object {
        fun of(items: List<PageItem>) = ItemsClip(PageCodec.encode(items), items.size)
    }
}

/**
 * What was copied lately, newest first. It lives in memory only and holds at most [capacity] entries.
 * Entries that fall off the end are handed to [onDropped], so a file behind one can be deleted.
 */
class ClipboardHistory(private val capacity: Int = DEFAULT_CAPACITY, private val onDropped: (ClipEntry) -> Unit = {}) {
    private val list = ArrayList<ClipEntry>()

    val entries: List<ClipEntry> get() = list

    /** Puts [entry] on top. A copy of something already listed moves it up, and the older entry is dropped. */
    fun add(entry: ClipEntry) {
        val same = list.indexOfFirst { it.key == entry.key }
        if (same >= 0) {
            val old = list.removeAt(same)
            if (old != entry) onDropped(old)
        }
        list.add(0, entry)
        while (list.size > capacity) onDropped(list.removeAt(list.lastIndex))
    }

    fun remove(entry: ClipEntry) {
        if (list.remove(entry)) onDropped(entry)
    }

    fun clear() {
        val all = list.toList()
        list.clear()
        all.forEach(onDropped)
    }

    companion object {
        const val DEFAULT_CAPACITY = 20
    }
}
