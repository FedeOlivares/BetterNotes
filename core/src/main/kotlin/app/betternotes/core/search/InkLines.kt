package app.betternotes.core.search

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import kotlin.math.abs
import kotlin.math.max

/**
 * Strokes that read as one line of writing, in the order they were drawn. [key] changes when the
 * strokes do, so a line that survived an edit need not be recognized again. Moving it keeps the key,
 * because moving ink does not change what it says.
 */
class InkLine(val strokes: List<StrokePoints>, val bounds: Bounds, val key: Long)

/** A stroke this much shorter than the median is a dot, accent or comma, and joins a line rather than starting one. */
private const val SMALL_STROKE_RATIO = 0.4f

/** A stroke this much taller than the median is a drawing or a bracket rather than writing. */
private const val TALL_STROKE_RATIO = 5f

/** How far from a line's center a stroke's center may sit, in line heights. */
private const val LINE_REACH = 0.6f

/** How far above or below a line a dot or accent may sit, in line heights. */
private const val SMALL_STROKE_REACH = 1.2f

/** A gap this wide, in line heights, separates two columns or two notes on the same level. */
private const val COLUMN_GAP = 4f

private class Candidate(val item: StrokeItem, val box: Bounds, val order: Int)

private class Cluster(first: Candidate) {
    val members = arrayListOf(first)
    var left = first.box.left
    var right = first.box.right
    private var centerSum = first.box.centerY
    private var heightSum = first.box.height

    val center get() = centerSum / members.size
    val meanHeight get() = heightSum / members.size

    fun add(stroke: Candidate, countsForShape: Boolean = true) {
        members += stroke
        left = minOf(left, stroke.box.left)
        right = maxOf(right, stroke.box.right)
        if (countsForShape) {
            centerSum += stroke.box.centerY
            heightSum += stroke.box.height
        } else {
            // Keeps the mean over the same count, so an accent does not drag the line upward.
            centerSum += center
            heightSum += meanHeight
        }
    }

    fun joins(stroke: Candidate): Boolean {
        val height = max(meanHeight, stroke.box.height)
        val gap = max(stroke.box.left - right, left - stroke.box.right)
        return abs(stroke.box.centerY - center) <= height * LINE_REACH && gap <= height * COLUMN_GAP
    }
}

/**
 * Groups a page's strokes into lines of writing, top to bottom, since a recognizer reads one line at
 * a time. Highlighter strokes are left out because they mark text rather than write it.
 *
 * Uses simple rules, not a layout model, and it will misread writing on a slant or in a diagram. That
 * only costs search some hits, and never touches the page.
 */
fun groupInkLines(items: List<PageItem>): List<InkLine> {
    val strokes = items.asSequence()
        .filterIsInstance<StrokeItem>()
        .filter { it.style.tool != InkTool.HIGHLIGHTER }
        .mapIndexed { order, item -> Candidate(item, item.points.bounds(), order) }
        .toList()
    if (strokes.isEmpty()) return emptyList()

    val median = strokes.map { it.box.height }.sorted()[strokes.size / 2]
    val clusters = ArrayList<Cluster>()
    val small = ArrayList<Candidate>()
    for (stroke in strokes) {
        when {
            stroke.box.height < median * SMALL_STROKE_RATIO -> small += stroke
            median > 0f && stroke.box.height > median * TALL_STROKE_RATIO -> Unit
            else -> {
                val cluster = clusters.filter { it.joins(stroke) }.minByOrNull { abs(it.center - stroke.box.centerY) }
                if (cluster != null) cluster.add(stroke) else clusters += Cluster(stroke)
            }
        }
    }
    for (stroke in small) nearestFor(stroke, clusters)?.add(stroke, countsForShape = false)
    return clusters.map(::toLine).sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
}

private fun nearestFor(stroke: Candidate, clusters: List<Cluster>): Cluster? = clusters
    .filter { cluster ->
        val height = cluster.meanHeight
        abs(stroke.box.centerY - cluster.center) <= height * SMALL_STROKE_REACH &&
            stroke.box.right >= cluster.left - height && stroke.box.left <= cluster.right + height
    }
    .minByOrNull { abs(it.center - stroke.box.centerY) }

private fun toLine(cluster: Cluster): InkLine {
    val members = cluster.members.sortedBy { it.order }
    var key = FNV_OFFSET
    for (id in members.map { it.item.id to it.item.points.size }.sortedBy { it.first }) {
        key = (key xor id.first) * FNV_PRIME
        key = (key xor id.second.toLong()) * FNV_PRIME
    }
    return InkLine(members.map { it.item.points }, members.map { it.box }.reduce(Bounds::union), key)
}

private const val FNV_OFFSET = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
