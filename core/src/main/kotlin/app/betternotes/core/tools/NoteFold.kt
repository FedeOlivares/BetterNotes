package app.betternotes.core.tools

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.VideoItem
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Which items of a page fold into a post-it when it is folded. An item goes in when more than [SHARE_INSIDE] of it is inside the
 * post-it's edges; tape, which is meant to hold a post-it down, goes in when more than [TAPE_SHARE_INSIDE] is. Folded in, an item is kept
 * whole in the post-it, so when the post-it is opened again the parts that lay outside its edges are drawn again.
 */
object NoteFold {
    const val SHARE_INSIDE = 0.8f
    const val TAPE_SHARE_INSIDE = 0.05f

    /** A little room at the edge, so a line drawn to the border does not miss by a hair. */
    private const val SLACK_PT = 2f
    private const val MAX_SAMPLES = 64

    /** Whether [item] goes into [note] when it folds. Other post-its, recordings and videos never do, nor does a locked item. */
    fun collapses(item: PageItem, note: StickyNoteItem): Boolean {
        if (item.locked || item is StickyNoteItem || item is VideoItem || item is AudioItem) return false
        val share = shareInside(item, note)
        return if (item.isTape) share > TAPE_SHARE_INSIDE else share > SHARE_INSIDE
    }

    /** Tape drawn along the pen, or a straight strip of it. */
    private val PageItem.isTape get() = this is TapePathItem || this is ShapeItem && kind == ShapeKind.TAPE

    /** The share, 0 to 1, of [item] that lies inside the edges of [note] as it stands open (its turn included). */
    fun shareInside(item: PageItem, note: StickyNoteItem): Float {
        val frame = note.frame
        val turn = Math.toRadians(-frame.rotation.toDouble())
        val c = cos(turn).toFloat()
        val s = sin(turn).toFloat()
        // A page point in the note's own, un-turned, with the note's top-left at 0, 0.
        fun localX(x: Float, y: Float) = frame.centerX + (x - frame.centerX) * c - (y - frame.centerY) * s - frame.left
        fun localY(x: Float, y: Float) = frame.centerY + (x - frame.centerX) * s + (y - frame.centerY) * c - frame.top
        fun inside(x: Float, y: Float): Boolean {
            val lx = localX(x, y)
            val ly = localY(x, y)
            return lx >= -SLACK_PT && ly >= -SLACK_PT && lx <= frame.width + SLACK_PT && ly <= frame.height + SLACK_PT
        }
        return when (item) {
            is StrokeItem -> {
                val points = item.points
                val step = max(1, points.size / MAX_SAMPLES)
                var tested = 0
                var held = 0
                var i = 0
                while (i < points.size) {
                    tested++
                    if (inside(points.x(i), points.y(i))) held++
                    i += step
                }
                if (tested == 0) 0f else held.toFloat() / tested
            }
            is TapePathItem -> along(item.points, ::inside)
            // A straight strip is measured along its middle line, the long way.
            is ShapeItem -> if (item.kind == ShapeKind.TAPE) along(middleLine(item), ::inside) else boxShare(item.bounds, frame.width, frame.height, ::localX, ::localY)
            else -> boxShare(item.bounds, frame.width, frame.height, ::localX, ::localY)
        }
    }

    private fun middleLine(item: ShapeItem): FloatArray {
        val f = item.frame
        return if (f.width >= f.height) {
            floatArrayOf(f.worldX(0f, 0.5f), f.worldY(0f, 0.5f), f.worldX(1f, 0.5f), f.worldY(1f, 0.5f))
        } else {
            floatArrayOf(f.worldX(0.5f, 0f), f.worldY(0.5f, 0f), f.worldX(0.5f, 1f), f.worldY(0.5f, 1f))
        }
    }

    /** The share of the length of the polyline [xy] that satisfies [inside], by even steps along it. */
    private fun along(xy: FloatArray, inside: (Float, Float) -> Boolean): Float {
        if (xy.size < 4) return if (xy.size >= 2 && inside(xy[0], xy[1])) 1f else 0f
        var length = 0f
        for (i in 2 until xy.size step 2) length += hypot(xy[i] - xy[i - 2], xy[i + 1] - xy[i - 1])
        if (length <= 0f) return if (inside(xy[0], xy[1])) 1f else 0f
        val steps = MAX_SAMPLES * 2
        val every = length / steps
        var walked = 0f
        var next = 0f
        var held = 0
        var tested = 0
        for (i in 2 until xy.size step 2) {
            val ax = xy[i - 2]
            val ay = xy[i - 1]
            val bx = xy[i]
            val by = xy[i + 1]
            val piece = hypot(bx - ax, by - ay)
            while (next <= walked + piece && tested <= steps) {
                val t = if (piece > 0f) (next - walked) / piece else 0f
                tested++
                if (inside(ax + (bx - ax) * t, ay + (by - ay) * t)) held++
                next += every
            }
            walked += piece
        }
        return if (tested == 0) 0f else held.toFloat() / tested
    }

    /** The share of the area of [box] (a thin box counts by its length) inside the rectangle 0, 0, [w], [h] of the note. */
    private fun boxShare(box: Bounds, w: Float, h: Float, lx: (Float, Float) -> Float, ly: (Float, Float) -> Float): Float {
        // The box is only placed by its middle; its size is kept, which is exact for a note that is not turned.
        val cx = lx(box.centerX, box.centerY)
        val cy = ly(box.centerX, box.centerY)
        val left = cx - box.width / 2f
        val top = cy - box.height / 2f
        val overX = min(left + box.width, w + SLACK_PT) - max(left, -SLACK_PT)
        val overY = min(top + box.height, h + SLACK_PT) - max(top, -SLACK_PT)
        if (overX < 0f || overY < 0f) return 0f
        val shareX = if (box.width <= 0f) 1f else min(1f, overX / box.width)
        val shareY = if (box.height <= 0f) 1f else min(1f, overY / box.height)
        return shareX * shareY
    }
}
