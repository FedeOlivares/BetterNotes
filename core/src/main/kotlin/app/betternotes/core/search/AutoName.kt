package app.betternotes.core.search

import app.betternotes.core.model.Page

/** The most words a suggested name takes from the page. */
private const val NAME_MAX_WORDS = 6

/** The longest a suggested name may be, in characters, so that it fits on a library card's two lines. */
private const val NAME_MAX_CHARS = 40

/** A reading shorter than this is more likely a stray mark than a word. */
private const val MIN_NAME_CHARS = 3

/**
 * A name for a note, taken from the first words written on it: the topmost line of the first page that
 * has text. Typed text counts as written, and handwriting uses the recognizer's best guess. Printed text
 * of an imported PDF doesn't, as such a document already has the file's name. Returns null if there is
 * nothing to take a name from yet.
 */
fun suggestTitle(index: SearchIndex, pages: List<Page>): String? {
    for (page in pages) {
        val lines = index.page(page.id)?.lines.orEmpty()
            .filter { it.source != TextSource.PRINTED && it.readings.isNotEmpty() }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        for (line in lines) {
            val name = tidy(line.readings[0])
            if (name != null) return name
        }
    }
    return null
}

private fun tidy(reading: String): String? {
    val words = reading.trim().split(WHITESPACE).filter { it.isNotEmpty() }.take(NAME_MAX_WORDS)
    var name = words.joinToString(" ").trim(*TRIMMED)
    if (name.length > NAME_MAX_CHARS) {
        // Cut at a word, so the name doesn't end partway through one.
        name = name.take(NAME_MAX_CHARS).substringBeforeLast(' ', name.take(NAME_MAX_CHARS)).trim(*TRIMMED)
    }
    if (name.count { it.isLetterOrDigit() } < MIN_NAME_CHARS) return null
    return name.replaceFirstChar { it.uppercase() }
}

private val WHITESPACE = Regex("\\s+")
private val TRIMMED = charArrayOf(' ', '.', ',', ';', ':', '-', '_', '–', '—')

/**
 * Whether a title is still one the app gave, so that it may be replaced. A title the owner typed is
 * never touched. [defaults] are the titles a new note starts with, such as "New note 2026-11-04".
 */
fun isPlaceholderTitle(title: String, defaults: Collection<String>): Boolean {
    val trimmed = title.trim()
    return trimmed.isEmpty() || defaults.any { it.trim().equals(trimmed, ignoreCase = true) }
}
