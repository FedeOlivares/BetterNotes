package app.betternotes.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Page
import app.betternotes.core.search.SearchHit
import app.betternotes.core.search.SearchIndexer
import app.betternotes.core.search.TextSource
import app.betternotes.core.search.groupInkLines
import app.betternotes.perf.SyntheticInk
import com.google.mlkit.vision.digitalink.recognition.Ink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs real ML Kit recognition, if its models are on the device. Nothing here downloads them, so
 * without them the recognition tests are skipped rather than passed. To fetch them first, run
 * scripts/verify-handwriting-offline.ps1, or pass the runner argument downloadInk=true.
 */
@RunWith(AndroidJUnit4::class)
class HandwritingSearchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = File(instrumentation.targetContext.cacheDir, "search-test")
    private val page = Page(1, PageSize.A4)
    private lateinit var store: DocumentStore

    @Before
    fun setUp() {
        root.deleteRecursively()
        store = DocumentStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun assumeModels() {
        if (InstrumentationRegistry.getArguments().getString("downloadInk") == "true") InkModels.download()
        assumeTrue("Handwriting models are not on this device", InkModels.missing().isEmpty())
    }

    @Test
    fun inkKeepsOneClockAcrossAStroke() {
        val ink = groupInkLines(SyntheticInk.items("hola")).single().toInk()
        var last = -1L
        for (stroke in ink.strokes) {
            for (point in (stroke as Ink.Stroke).points) {
                val time = point.timestamp!!
                assertTrue("$time after $last", time >= last)
                last = time
            }
        }
    }

    @Test
    fun recognizesEnglishAndSpanish() {
        assumeModels()
        MlKitRecognizer().use { recognizer ->
            for (word in SyntheticInk.WORDS) {
                val guesses = recognizer.recognize(SyntheticInk.line(word))
                assertTrue("$word read as $guesses", guesses.any { it.lowercase().contains(word) })
            }
        }
    }

    @Test
    fun writtenWordIsFoundThroughTheIndex() {
        assumeModels()
        store.writePage(page.id, SyntheticInk.items("hola"))
        MlKitRecognizer().use { recognizer ->
            val indexer = SearchIndexer(store, recognizer)
            val index = indexer.load()
            assertEquals(0, indexer.update(index, listOf(page)).pendingLines)
            val hit = index.search("hola", listOf(page)).single()
            assertEquals(TextSource.HANDWRITING, hit.source)
            assertEquals(page.id, hit.pageId)
        }
    }

    @Test
    fun documentSearchIndexesInTheBackgroundAndReportsOnTheMainThread() {
        assumeModels()
        store.writePage(page.id, SyntheticInk.items("mundo"))
        val indexed = CountDownLatch(1)
        val found = CountDownLatch(1)
        var hits = emptyList<SearchHit>()
        lateinit var search: DocumentSearch
        instrumentation.runOnMainSync {
            search = DocumentSearch(store, MlKitRecognizer())
            search.onIndexed = { indexed.countDown() }
            search.refresh(listOf(page))
        }
        assertTrue(indexed.await(TIMEOUT_S, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            search.search("mundo", listOf(page)) {
                hits = it
                found.countDown()
            }
        }
        assertTrue(found.await(TIMEOUT_S, TimeUnit.SECONDS))
        assertEquals(1, hits.size)
        instrumentation.runOnMainSync { search.close() }
    }

    private companion object {
        const val TIMEOUT_S = 60L
    }
}
