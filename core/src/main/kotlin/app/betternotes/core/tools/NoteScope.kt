package app.betternotes.core.tools

import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.geometry.Affine
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.StickyNoteItem

/**
 * The items of one open post-it laid out as a page of their own, in the page's points, so that the lasso can select, move, change and
 * delete them where they are, inside the post-it. [content] is what the lasso works on; each change it makes becomes one edit of the
 * post-it on the page ([commit]), so undo takes it back like any other. A post-it's items sit at their place minus its top-left corner,
 * as the pen and the eraser put them there.
 */
class NoteScope private constructor(
    val pageId: Long,
    val noteId: Long,
    /** The post-it as the undo history last saw it. */
    private var base: StickyNoteItem,
    val content: PageContent,
    /** The layer each item had in the post-it, by id: the lasso sees them all on the post-it's layer. */
    private val layers: Map<Long, Long>,
) {
    private val toNote = Affine.translate(-base.frame.left, -base.frame.top)

    /** The post-it as [content] has it now. */
    fun current(): StickyNoteItem = base.copy(items = content.toList().map { item -> item.transformed(toNote).let { it.onLayer(layers[it.id] ?: it.layer) } })

    /** Shows what [content] holds now in the post-it on [page], without recording it, while a change is still under way. */
    fun mirror(page: PageContent) {
        val at = indexIn(page)
        if (at < 0) return
        val now = current()
        if (page[at] != now) page.splice(intArrayOf(at), intArrayOf(at), listOf(now))
    }

    /**
     * The edit that turns the post-it on [page] from what the history last saw into what [content] holds now, or null when nothing
     * changed or the post-it is gone. The page is put back to what the history saw first, so the edit can be performed on it.
     */
    fun commit(page: PageContent): ItemsEdit? {
        val at = indexIn(page)
        if (at < 0) return null
        if (page[at] != base) page.splice(intArrayOf(at), intArrayOf(at), listOf(base))
        val now = current()
        if (now == base) return null
        val edit = ItemsEdit(pageId, intArrayOf(at), listOf(base), intArrayOf(at), listOf(now))
        base = now
        return edit
    }

    /**
     * Puts the post-it on [page] back to what the history saw, when it shows a change of this scope's that was never recorded. A post-it
     * that the history itself changed since (an undo) is left as it is.
     */
    fun restore(page: PageContent) {
        val at = indexIn(page)
        if (at < 0 || page[at] == base) return
        if (page[at] == current()) page.splice(intArrayOf(at), intArrayOf(at), listOf(base))
    }

    private fun indexIn(page: PageContent): Int {
        for (i in page.size - 1 downTo 0) if (page[i].id == noteId) return i
        return -1
    }

    companion object {
        /** The items of the open post-it at [at] on [page], ready for the lasso, or null when that is not an open post-it. */
        fun open(pageId: Long, page: PageContent, at: Int): NoteScope? {
            val note = page[at] as? StickyNoteItem ?: return null
            if (!note.isOpen) return null
            val toPage = Affine.translate(note.frame.left, note.frame.top)
            val items = note.items.map { it.transformed(toPage).onLayer(note.layer) }
            val content = PageContent(items, page.layers).also { it.activeLayer = note.layer }
            return NoteScope(pageId, note.id, note, content, note.items.associate { it.id to it.layer })
        }

        /**
         * What a loop [polygon] on [page] works on when it lies wholly inside the drawn edges of an open post-it: that post-it's items, or
         * null when the loop is on the page. The lasso, the selection rectangle and a circle drawn with the pen all ask this, so they agree.
         */
        fun forLoop(pageId: Long, page: PageContent, polygon: FloatArray): NoteScope? {
            val at = noteHolding(page, polygon)
            return if (at < 0) null else open(pageId, page, at)
        }

        /**
         * The top open post-it of [page] that the loop [polygon] lies wholly inside, or -1. A loop that crosses the edge of the post-it, or lies
         * outside it, still belongs to it when it lies wholly inside an item of the post-it that hangs past its edge: the part that hangs out
         * is as much the post-it's as the rest, and a loop round only that part grabs the item.
         */
        fun noteHolding(page: PageContent, polygon: FloatArray): Int {
            for (i in page.size - 1 downTo 0) {
                val note = page[i] as? StickyNoteItem ?: continue
                if (!note.isOpen || !page.isEditable(note) || !page.isShown(note)) continue
                if (polygonWithin(polygon, note.frame) || withinOverhang(note, polygon)) return i
            }
            return -1
        }

        /** How far past an item's box a loop round its hanging part may reach and still be round that item, in points. */
        private const val OVERHANG_SLACK = 6f

        /**
         * Whether [polygon] is a loop round the part of an item of [note] that hangs past the post-it's edge: every point of it lies in the post-it
         * or in the box of a hanging item (a little enlarged), and some point lies outside the post-it. A turned post-it has none.
         */
        private fun withinOverhang(note: StickyNoteItem, polygon: FloatArray): Boolean {
            if (note.frame.rotation != 0f || polygon.size < 2) return false
            val edges = note.frame.bounds
            val hanging = note.items.mapNotNull { item ->
                val box = item.paintBounds
                val inPage = Bounds(box.left + note.frame.left, box.top + note.frame.top, box.right + note.frame.left, box.bottom + note.frame.top)
                val hangs = inPage.left < edges.left || inPage.top < edges.top || inPage.right > edges.right || inPage.bottom > edges.bottom
                if (hangs) Bounds(inPage.left - OVERHANG_SLACK, inPage.top - OVERHANG_SLACK, inPage.right + OVERHANG_SLACK, inPage.bottom + OVERHANG_SLACK) else null
            }
            if (hanging.isEmpty()) return false
            var outside = false
            var i = 0
            while (i + 1 < polygon.size) {
                val x = polygon[i]
                val y = polygon[i + 1]
                val inFrame = x > edges.left && x < edges.right && y > edges.top && y < edges.bottom
                if (!inFrame) {
                    outside = true
                    if (hanging.none { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) return false
                }
                i += 2
            }
            return outside
        }
    }
}
