package app.betternotes.core.search

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.Page
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

enum class TextSource { HANDWRITING, TYPED, PRINTED }

/**
 * Something searchable on a page. A typed text has one [readings]. Recognized handwriting has one for
 * each guess the recognizer made, because the right word is often not its first guess.
 */
class IndexedLine(val key: Long, val source: TextSource, val bounds: Bounds, val readings: List<String>) {
    internal val words: List<List<String>> by lazy(LazyThreadSafetyMode.NONE) { readings.map(::wordsOf) }
}

/**
 * What the index knows about one page. [stamp] is the page file's stamp when it was read, and
 * [complete] is false if some of its ink is still waiting to be recognized.
 */
class IndexedPage(val stamp: Long, val complete: Boolean, val lines: List<IndexedLine>)

/** A match. [exact] is false when handwriting only came close, as recognizers often misread a letter. */
data class SearchHit(
    val pageId: Long,
    val pageIndex: Int,
    val bounds: Bounds,
    val text: String,
    val source: TextSource,
    val exact: Boolean,
)

/**
 * The searchable text of one document. Searching ignores case and accents, so "ano" finds "año".
 * [engineId] names the recognizer that made the entries. Not thread safe.
 */
class SearchIndex(val engineId: String, pages: Map<Long, IndexedPage> = emptyMap()) {
    private val pages = HashMap(pages)

    val indexedPages: Map<Long, IndexedPage> get() = pages

    fun page(pageId: Long): IndexedPage? = pages[pageId]

    internal fun put(pageId: Long, page: IndexedPage) {
        pages[pageId] = page
    }

    internal fun retainOnly(pageIds: Set<Long>) {
        pages.keys.retainAll(pageIds)
    }

    /** Matches in page order, and top to bottom within a page. All the query's words must be on one line. */
    fun search(query: String, documentPages: List<Page>): List<SearchHit> {
        val terms = wordsOf(query)
        if (terms.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        documentPages.forEachIndexed { index, page ->
            val lines = pages[page.id]?.lines ?: return@forEachIndexed
            val start = hits.size
            for (line in lines) line.match(terms)?.let { (text, exact) ->
                hits += SearchHit(page.id, index, line.bounds, text, line.source, exact)
            }
            hits.subList(start, hits.size).sortWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        }
        return hits
    }
}

/** The best reading that holds every term, preferring one that needs no tolerance for errors. */
private fun IndexedLine.match(terms: List<String>): Pair<String, Boolean>? {
    var close: String? = null
    for (i in readings.indices) {
        val quality = terms.minOf { term -> words[i].maxOfOrNull { wordMatch(term, it, source) } ?: NO_MATCH }
        if (quality == EXACT_MATCH) return readings[i] to true
        if (quality == CLOSE_MATCH && close == null) close = readings[i]
    }
    return close?.let { it to false }
}

private const val NO_MATCH = 0
private const val CLOSE_MATCH = 1
private const val EXACT_MATCH = 2

/** Words at least this long tolerate one wrong letter. */
private const val MIN_WORD_FOR_ONE_ERROR = 4

/** Words at least this long tolerate two. */
private const val MIN_WORD_FOR_TWO_ERRORS = 8

/** A term inside a longer word counts, since recognizers often run words together. */
private fun wordMatch(term: String, word: String, source: TextSource): Int {
    if (word.contains(term)) return EXACT_MATCH
    if (source != TextSource.HANDWRITING) return NO_MATCH
    val allowed = when {
        term.length >= MIN_WORD_FOR_TWO_ERRORS -> 2
        term.length >= MIN_WORD_FOR_ONE_ERROR -> 1
        else -> 0
    }
    return if (allowed > 0 && editDistance(term, word, allowed) <= allowed) CLOSE_MATCH else NO_MATCH
}

/** Lowercases, removes accents, expands ligatures and splits on anything that is not a letter or digit. */
internal fun wordsOf(text: String): List<String> {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)
    return plain.split(NON_WORD).filter { it.isNotEmpty() }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/**
 * Edits between two words, counting a swap of neighbours as one, since that is a common misreading.
 * Gives up and returns [limit] + 1 as soon as the answer must be over [limit].
 */
internal fun editDistance(a: String, b: String, limit: Int): Int {
    if (abs(a.length - b.length) > limit) return limit + 1
    var beforePrevious = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var best = i
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            var cost = min(substitution, min(previous[j], current[j - 1]) + 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                cost = min(cost, beforePrevious[j - 2] + 1)
            }
            current[j] = cost
            best = min(best, cost)
        }
        if (best > limit) return limit + 1
        val recycled = beforePrevious
        beforePrevious = previous
        previous = current
        current = recycled
    }
    return previous[b.length]
}
