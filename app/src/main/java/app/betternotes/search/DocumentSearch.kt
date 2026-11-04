package app.betternotes.search

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.model.Page
import app.betternotes.core.search.HandwritingRecognizer
import app.betternotes.core.search.IndexUpdate
import app.betternotes.core.search.SearchHit
import app.betternotes.core.search.SearchIndex
import app.betternotes.core.search.SearchIndexer
import app.betternotes.pdf.PdfPrintedText
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Search in one open document. The index is built on a low-priority thread, one page at a time, so it
 * never competes with drawing. It reads saved files, so call [refresh] after each save. Calls and
 * callbacks are on the main thread.
 */
class DocumentSearch(
    store: DocumentStore,
    private val recognizer: HandwritingRecognizer,
) {
    /** Reports each finished update. Pending lines above zero mean models are missing or recognition failed. */
    var onIndexed: (IndexUpdate) -> Unit = {}

    /** Lines of ink the last update could not read. Zero when everything written is searchable. */
    var pendingLines = 0
        private set

    private val printedText = PdfPrintedText(store)
    private val indexer = SearchIndexer(store, recognizer, printedText)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "search-index")
    }
    private val closed = AtomicBoolean(false)
    private val refreshQueued = AtomicBoolean(false)
    private var index: SearchIndex? = null

    @Volatile
    private var latestPages: List<Page> = emptyList()

    /** Queues an update with this page list. Calls made while one is waiting share it. */
    fun refresh(pages: List<Page>) {
        latestPages = pages.toList()
        if (closed.get() || !refreshQueued.compareAndSet(false, true)) return
        worker.execute {
            refreshQueued.set(false)
            runUpdate()
        }
    }

    /** Runs [query] against what is indexed so far and reports the hits on the main thread. */
    fun search(query: String, pages: List<Page>, onResult: (List<SearchHit>) -> Unit) {
        val snapshot = pages.toList()
        if (closed.get()) return
        worker.execute {
            val hits = indexOrLoad().search(query, snapshot)
            main.post { if (!closed.get()) onResult(hits) }
        }
    }

    /** The name the first words of the document suggest, or null if nothing is readable yet. */
    fun suggestTitle(pages: List<Page>, onResult: (String?) -> Unit) {
        val snapshot = pages.toList()
        if (closed.get()) return
        worker.execute {
            val title = app.betternotes.core.search.suggestTitle(indexOrLoad(), snapshot)
            main.post { if (!closed.get()) onResult(title) }
        }
    }

    /** Stops after the line in progress and keeps what was indexed, so the work is not lost. */
    fun close() {
        closed.set(true)
        worker.execute {
            // An update that stopped early has its pages in memory only.
            index?.let { current ->
                try {
                    indexer.save(current)
                } catch (e: IOException) {
                    Log.e(TAG, "Could not save the search index", e)
                }
            }
            (recognizer as? AutoCloseable)?.close()
            printedText.close()
        }
        worker.shutdown()
    }

    private fun runUpdate() {
        val current = indexOrLoad()
        val update = indexer.update(current, latestPages) { closed.get() }
        if (update.changedPages > 0) {
            try {
                indexer.save(current)
            } catch (e: IOException) {
                Log.e(TAG, "Could not save the search index", e)
            }
        }
        main.post {
            if (closed.get()) return@post
            pendingLines = update.pendingLines
            onIndexed(update)
        }
    }

    private fun indexOrLoad() = index ?: indexer.load().also { index = it }

    private companion object {
        const val TAG = "DocumentSearch"
    }
}
