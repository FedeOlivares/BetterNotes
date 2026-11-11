package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import app.betternotes.core.model.Page
import app.betternotes.core.planner.ElementKey
import app.betternotes.core.planner.PlannerElement
import app.betternotes.core.planner.RoleSlot
import app.betternotes.core.planner.plannerElements
import app.betternotes.core.template.PageTemplate

/**
 * What a lasso picked on a planner page when it caught none of the owner's own ink: the page's generated elements. They are locked
 * content, so this selection can only be restyled, never moved, copied, cut, deleted or edited.
 */
internal class PlannerPick(private val redraw: () -> Unit) {
    var pageIndex = -1
        private set
    var pageId = 0L
        private set
    var elements: List<PlannerElement> = emptyList()
        private set

    val active get() = elements.isNotEmpty()

    /** Called when the picked elements change, with them (empty: none). */
    var onChanged: (List<PlannerElement>) -> Unit = {}

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OUTLINE_COLOR
        strokeWidth = OUTLINE_WIDTH
    }
    private val points = FloatArray(8)

    /**
     * Picks the elements of [page] whose centers the loop [polygon] (x, y pairs, in the page's points) surrounds. Returns whether it found any.
     * A page that is not a planner's, and the cover, have none.
     */
    fun pick(index: Int, page: Page, polygon: FloatArray): Boolean {
        val found = find(page, polygon)
        if (found.isEmpty()) return false
        pageIndex = index
        pageId = page.id
        elements = found
        onChanged(found)
        redraw()
        return true
    }

    /** The elements of [page] with the middle or a line or word start inside the loop [polygon]; none for a page that is not a planner's. */
    fun find(page: Page, polygon: FloatArray): List<PlannerElement> {
        val template = page.background.template as? PageTemplate.Planner ?: return emptyList()
        val size = page.size ?: return emptyList()
        return plannerElements(template.page, template.setup, size).filter { e -> e.surrounded { x, y -> contains(polygon, x, y) } }
    }

    fun clear() {
        if (elements.isEmpty()) return
        elements = emptyList()
        pageIndex = -1
        onChanged(elements)
        redraw()
    }

    /** The keys of the picked elements. */
    fun keys(): List<ElementKey> = elements.map { it.key }.distinct()

    /** The parts of an element that any of the picked ones has. */
    fun slots(): List<RoleSlot> {
        val all = RoleSlot.entries
        return all.filter { slot -> elements.any { slot in it.slots } }
    }

    /** Where the bar goes: the bottom-right of what is picked, in the page's points. */
    fun anchor(): FloatArray? {
        if (elements.isEmpty()) return null
        return floatArrayOf(elements.maxOf { it.bounds.right }, elements.maxOf { it.bounds.bottom })
    }

    /** Outlines the picked elements, over the page. [pageToScreen] takes the page's points to the view. */
    fun draw(canvas: Canvas, pageToScreen: Matrix) {
        for (e in elements) {
            val b = e.bounds
            points[0] = b.left
            points[1] = b.top
            points[2] = b.right
            points[3] = b.top
            points[4] = b.right
            points[5] = b.bottom
            points[6] = b.left
            points[7] = b.bottom
            pageToScreen.mapPoints(points)
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                canvas.drawLine(points[2 * i], points[2 * i + 1], points[2 * j], points[2 * j + 1], outline)
            }
        }
    }

    private fun contains(polygon: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = polygon.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = polygon[2 * i]
            val yi = polygon[2 * i + 1]
            val xj = polygon[2 * j]
            val yj = polygon[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private companion object {
        const val OUTLINE_COLOR = 0xFF3D6AF2.toInt()
        const val OUTLINE_WIDTH = 3f
    }
}
