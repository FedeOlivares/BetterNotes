package app.betternotes.core.tools

import app.betternotes.core.edit.BatchEdit
import app.betternotes.core.edit.Edit
import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.Capsule
import app.betternotes.core.geometry.ConvexClipper
import app.betternotes.core.geometry.RectClipper
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.StrokePointsBuilder

enum class EraseMode {
    /** Removes only the parts of strokes under the tip, splitting them as needed. */
    PARTIAL,

    /** Removes any stroke or shape the tip touches, whole. */
    WHOLE,
}

/**
 * One drag of the eraser. Each move changes the page at once so the ink disappears under the tip,
 * and [finish] returns the whole drag as a single edit for the undo history to [record].
 *
 * Images, text, tables, sticky notes and recordings are left alone, as a stray eraser pass should
 * not destroy them. The lasso and "clear page" remove those.
 */
class EraseSession(
    private val pageId: Long,
    private val content: PageContent,
    private val mode: EraseMode,
    private val radius: Float,
    private val ids: IdSource = IdSource.Random,
) {
    private val steps = ArrayList<Edit>()
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    /** Moves the tip to a point, erasing along the way from the previous one. */
    fun eraseTo(x: Float, y: Float) {
        val startX = if (lastX.isNaN()) x else lastX
        val startY = if (lastY.isNaN()) y else lastY
        step(Capsule(startX, startY, x, y, radius))
        lastX = x
        lastY = y
    }

    /** The drag as one edit, or null if it erased nothing. */
    fun finish(): Edit? = when (steps.size) {
        0 -> null
        1 -> steps[0]
        else -> BatchEdit(steps.toList())
    }

    private fun step(tip: Capsule) {
        val changes = ChangeList()
        content.forEachIntersecting(tip.bounds) { index, item ->
            when (mode) {
                EraseMode.PARTIAL -> if (item is StrokeItem) {
                    cutStroke(item.points, tip, item.style.width / 2f)?.let { pieces ->
                        changes.replace(index, item, pieces.map { item.copy(id = ids.next(), points = it) })
                    }
                }
                EraseMode.WHOLE -> if ((item is StrokeItem || item is ShapeItem) && item.touches(tip)) {
                    changes.replace(index, item, emptyList())
                }
            }
        }
        changes.toEdit(pageId)?.let { edit ->
            content.splice(edit.removedAt, edit.addedAt, edit.added)
            steps += edit
        }
    }
}

/**
 * The edit for "clear page". With a null [region] it removes everything. With a region, as on an
 * infinite canvas, it clears only that area: items inside go, strokes crossing the edge are cut,
 * and other items crossing the edge stay, because only ink can be cut in two. Returns null if there
 * was nothing to clear.
 */
fun clearEdit(pageId: Long, content: PageContent, region: Bounds?, ids: IdSource = IdSource.Random): ItemsEdit? {
    val changes = ChangeList()
    if (region == null) {
        for (i in 0 until content.size) changes.replace(i, content[i], emptyList())
    } else {
        val clipper = RectClipper(region)
        content.forEachIntersecting(region) { index, item ->
            if (region.contains(item.bounds)) {
                changes.replace(index, item, emptyList())
            } else if (item is StrokeItem) {
                cutStroke(item.points, clipper, item.style.width / 2f)?.let { pieces ->
                    changes.replace(index, item, pieces.map { item.copy(id = ids.next(), points = it) })
                }
            }
        }
    }
    return changes.toEdit(pageId)
}

/**
 * Removes the parts of a stroke inside [clipper] grown by [margin], and returns what is left, or
 * null if the stroke is untouched. Passing half the stroke's width as the margin means a round cap
 * on a cut end only just touches the eraser, so no stub is left poking into the erased area.
 */
internal fun cutStroke(points: StrokePoints, clipper: ConvexClipper, margin: Float): List<StrokePoints>? {
    val count = points.size
    if (count == 1) {
        val hit = clipper.clip(points.x(0), points.y(0), points.x(0), points.y(0), margin)
        return if (hit) emptyList() else null
    }
    val firstHit = (0 until count - 1).firstOrNull { points.segmentHit(it, clipper, margin) } ?: return null

    val pieces = ArrayList<StrokePoints>(2)
    val current = StrokePointsBuilder(count)
    for (i in 0..firstHit) current.add(points, i)

    var open = true
    for (i in firstHit until count - 1) {
        if (!points.segmentHit(i, clipper, margin)) {
            if (!open) {
                current.add(points, i)
                open = true
            }
            current.add(points, i + 1)
            continue
        }
        // The clipper's fields hold this segment's result until the next clip call.
        val enter = clipper.enter
        val exit = clipper.exit
        if (open) {
            if (enter > 0f) current.addBetween(points, i, enter)
            current.flushTo(pieces)
            open = false
        }
        if (exit < 1f) {
            current.addBetween(points, i, exit)
            current.add(points, i + 1)
            open = true
        }
    }
    if (open) current.flushTo(pieces)
    return pieces
}

private fun StrokePoints.segmentHit(i: Int, clipper: ConvexClipper, margin: Float) =
    clipper.clip(x(i), y(i), x(i + 1), y(i + 1), margin)

/** A piece needs two points to be a line. A lone point here is what a cut left behind, not a dot. */
private fun StrokePointsBuilder.flushTo(pieces: MutableList<StrokePoints>) {
    if (size >= 2) pieces += build()
    clear()
}

/** Gathers replacements in ascending order, tracking where each new item lands after earlier changes. */
private class ChangeList {
    private val removedAt = ArrayList<Int>()
    private val removed = ArrayList<PageItem>()
    private val addedAt = ArrayList<Int>()
    private val added = ArrayList<PageItem>()

    fun replace(index: Int, item: PageItem, replacements: List<PageItem>) {
        val shift = added.size - removed.size
        removedAt += index
        removed += item
        replacements.forEachIndexed { k, replacement ->
            addedAt += index + shift + k
            added += replacement
        }
    }

    fun toEdit(pageId: Long): ItemsEdit? {
        if (removed.isEmpty()) return null
        return ItemsEdit(pageId, removedAt.toIntArray(), removed, addedAt.toIntArray(), added)
    }
}
