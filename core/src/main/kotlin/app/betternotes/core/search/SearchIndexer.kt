package app.betternotes.core.search

import app.betternotes.core.format.ByteReader
import app.betternotes.core.format.ByteWriter
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.format.WireEnum
import app.betternotes.core.format.decodeFile
import app.betternotes.core.format.encodeFile
import app.betternotes.core.format.writeAtomically
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import java.io.File
import java.io.IOException

/**
 * Reads handwriting. This interface keeps the engine replaceable, so that a fully offline model could
 * take over from the current one without touching search.
 */
interface HandwritingRecognizer {
    /** Names the engine and its models. Entries made under another id get redone. */
    val engineId: String

    /** False while its models are missing. Ink then waits, and typed text is still searchable. */
    val isReady: Boolean

    /** Blocks. Returns the guesses for one line, best first, across every language it reads. */
    @Throws(IOException::class)
    fun recognize(line: InkLine): List<String>
}

/** How an update ended. [pendingLines] is the ink still unread, for lack of models or after a failure. */
data class IndexUpdate(val changedPages: Int, val pendingLines: Int, val cancelled: Boolean)

/**
 * Brings a [SearchIndex] up to date with a document's saved pages, on a background thread. Pages whose
 * files have not changed are skipped, and a changed page only sends the lines that changed to the
 * recognizer. So an update after an edit costs about one line, at some 100 ms each.
 */
class SearchIndexer(private val store: DocumentStore, private val recognizer: HandwritingRecognizer) {
    private val file = File(store.root, "search.bni")

    /** The index on disk, or an empty one if there is none, it is damaged, or another engine made it. */
    fun load(): SearchIndex {
        val saved = try {
            if (file.exists()) SearchIndexCodec.decode(file.readBytes()) else null
        } catch (_: IOException) {
            null
        }
        return saved?.takeIf { it.engineId == recognizer.engineId } ?: SearchIndex(recognizer.engineId)
    }

    fun save(index: SearchIndex) = writeAtomically(file, SearchIndexCodec.encode(index))

    /**
     * Indexes what changed. A page that cannot be read is left as it was and counted as pending, so a
     * later update tries again, and its failure does not stop the rest of the document.
     */
    fun update(index: SearchIndex, pages: List<Page>, cancelled: () -> Boolean = { false }): IndexUpdate {
        index.retainOnly(pages.mapTo(HashSet()) { it.id })
        var changed = 0
        var pending = 0
        for (page in pages) {
            if (cancelled()) return IndexUpdate(changed, pending, true)
            // Read before the page, so a save landing in between makes the stamp old rather than the text.
            val stamp = store.pageStamp(page.id)
            val known = index.page(page.id)
            if (known != null && known.complete && known.stamp == stamp) continue
            try {
                val items = store.readPage(page.id)
                val updated = indexPage(items, known, stamp, cancelled)
                index.put(page.id, updated.page)
                pending += updated.unread
                changed++
            } catch (_: IOException) {
                pending++
            }
        }
        return IndexUpdate(changed, pending, cancelled())
    }

    private class PageResult(val page: IndexedPage, val unread: Int)

    private fun indexPage(items: List<PageItem>, known: IndexedPage?, stamp: Long, cancelled: () -> Boolean): PageResult {
        val previous = known?.lines?.associateBy { it.key }.orEmpty()
        val lines = ArrayList<IndexedLine>()
        var unread = 0

        fun addInk(ink: List<PageItem>, shownAt: Bounds?) {
            for (line in groupInkLines(ink)) {
                val reused = previous[line.key]?.readings
                val readings = reused ?: tryRecognize(line, cancelled)
                if (readings == null) unread++
                // Unread ink is kept off the index and comes back here next time, as the page stays incomplete.
                if (!readings.isNullOrEmpty()) {
                    lines += IndexedLine(line.key, TextSource.HANDWRITING, shownAt ?: line.bounds, readings)
                }
            }
        }

        addInk(items, shownAt = null)
        for (item in items) {
            when (item) {
                is TextItem -> lines += typed(item.id, item.bounds, item.text)
                is TableItem -> item.cells.forEachIndexed { cell, text -> lines += typed(item.id + cell, item.bounds, text) }
                is StickyNoteItem -> {
                    // What is inside a note counts as being where the note is, open or closed.
                    addInk(item.items, shownAt = item.bounds)
                    item.items.filterIsInstance<TextItem>().forEach { lines += typed(it.id, item.bounds, it.text) }
                }
                else -> Unit
            }
        }
        return PageResult(IndexedPage(stamp, unread == 0, lines), unread)
    }

    /** Null means not read this time. An empty list means read, and nothing legible was there. */
    private fun tryRecognize(line: InkLine, cancelled: () -> Boolean): List<String>? {
        if (!recognizer.isReady || cancelled()) return null
        return try {
            recognizer.recognize(line)
        } catch (_: IOException) {
            null
        }
    }

    private fun typed(key: Long, bounds: Bounds, text: String) =
        IndexedLine(key, TextSource.TYPED, bounds, listOf(text))
}

private const val INDEX_MAGIC = "BNSI"
private val SOURCES = WireEnum(TextSource.HANDWRITING, TextSource.TYPED)

/** The index's file. It can always be rebuilt, so a damaged one just gets thrown away. */
internal object SearchIndexCodec {
    fun encode(index: SearchIndex): ByteArray = encodeFile(INDEX_MAGIC) {
        string(index.engineId)
        varint(index.indexedPages.size)
        for ((id, page) in index.indexedPages) {
            long(id)
            long(page.stamp)
            bool(page.complete)
            varint(page.lines.size)
            page.lines.forEach { line(it) }
        }
    }

    fun decode(bytes: ByteArray): SearchIndex = decodeFile(bytes, INDEX_MAGIC) {
        val engine = string()
        val pages = HashMap<Long, IndexedPage>()
        repeat(count(Long.SIZE_BYTES)) {
            val id = long()
            val stamp = long()
            val complete = bool()
            pages[id] = IndexedPage(stamp, complete, List(count(1)) { line() })
        }
        SearchIndex(engine, pages)
    }

    private fun ByteWriter.line(line: IndexedLine) {
        long(line.key)
        SOURCES.write(this, line.source)
        float(line.bounds.left)
        float(line.bounds.top)
        float(line.bounds.right)
        float(line.bounds.bottom)
        varint(line.readings.size)
        line.readings.forEach { string(it) }
    }

    private fun ByteReader.line(): IndexedLine {
        val key = long()
        val source = SOURCES.read(this)
        val bounds = Bounds(float(), float(), float(), float())
        return IndexedLine(key, source, bounds, List(count(1)) { string() })
    }
}
