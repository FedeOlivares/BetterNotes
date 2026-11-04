package app.betternotes.core.export

/**
 * Pages typed as a range, such as "2-5, 8". Counts from 1 as people do, and gives back indices from 0.
 * Returns null if the text isn't a range, or names a page the document doesn't have. Blank text is no
 * pages at all, so that a half-typed range never selects everything by accident.
 */
fun parsePageRange(text: String, pageCount: Int): List<Int>? {
    val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return if (text.isBlank()) emptyList() else null
    val pages = LinkedHashSet<Int>()
    for (part in parts) {
        val ends = part.split('-', '–').map { it.trim() }
        val first = ends[0].toIntOrNull() ?: return null
        val last = when (ends.size) {
            1 -> first
            2 -> ends[1].toIntOrNull() ?: return null
            else -> return null
        }
        if (first < 1 || last < first || last > pageCount) return null
        for (page in first..last) pages += page - 1
    }
    return pages.sorted()
}

/** The shortest text [parsePageRange] reads back to the same pages. Indices count from 0. */
fun formatPageRange(indices: Collection<Int>): String {
    val sorted = indices.distinct().sorted()
    val parts = ArrayList<String>()
    var i = 0
    while (i < sorted.size) {
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
        parts += if (j == i) "${sorted[i] + 1}" else "${sorted[i] + 1}-${sorted[j] + 1}"
        i = j + 1
    }
    return parts.joinToString(", ")
}
