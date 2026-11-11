package app.betternotes.core.model

import app.betternotes.core.geometry.Bounds

/**
 * The items of one page, in paint order, bottom first.
 *
 * The bounds of every item sit in one flat array in that same order. Finding what is on screen is
 * a sequential scan with no pointer chasing, and the results come out already in paint order,
 * which a spatial index could not give without an extra sort.
 *
 * Not thread safe. The items are immutable, so a [toList] snapshot can go to a background thread.
 */
class PageContent(initial: List<PageItem> = emptyList(), layers: PageLayers = PageLayers.Single) {
    @PublishedApi
    internal var items = arrayOfNulls<PageItem>(maxOf(initial.size, MIN_CAPACITY))

    @PublishedApi
    internal var boxes = FloatArray(items.size * BOX_STRIDE)

    @PublishedApi
    internal var paintBoxes = FloatArray(items.size * BOX_STRIDE)

    var size = 0
        private set

    /** Changes on every mutation, so caches and autosave can tell when they are stale. */
    var version = 0L
        private set

    private var dirty: Bounds? = null

    /** The layers of the page, bottom first. Items are kept sorted by the rank of their layer. Changes go through [restructure]. */
    var layers: PageLayers = layers
        private set

    /** The layer that new items go on and that the tools can change. Not saved: a page opens on its top layer. */
    var activeLayer: Long = layers.top.id
        set(value) {
            field = if (this.layers.rank(value) >= 0) value else this.layers.top.id
        }

    init {
        initial.forEachIndexed { i, item -> put(i, item) }
        size = initial.size
    }

    operator fun get(index: Int): PageItem = items[checkIndex(index)]!!

    fun toList(): List<PageItem> = List(size) { items[it]!! }

    /**
     * Calls [action] for each item whose bounds touch [region], in paint order. The action must not
     * change this page: collect what to change, then call [splice].
     */
    inline fun forEachIntersecting(region: Bounds, action: (index: Int, item: PageItem) -> Unit) {
        val box = boxes
        for (i in 0 until size) {
            val offset = i * 4
            if (box[offset] <= region.right && region.left <= box[offset + 2] &&
                box[offset + 1] <= region.bottom && region.top <= box[offset + 3]
            ) {
                action(i, items[i]!!)
            }
        }
    }

    /** Calls [action] for each item that can paint inside [region], in paint order. See [PageItem.paintBounds]. */
    inline fun forEachPaintingIn(region: Bounds, action: (index: Int, item: PageItem) -> Unit) {
        val box = paintBoxes
        for (i in 0 until size) {
            val offset = i * 4
            if (box[offset] <= region.right && region.left <= box[offset + 2] &&
                box[offset + 1] <= region.bottom && region.top <= box[offset + 3]
            ) {
                action(i, items[i]!!)
            }
        }
    }

    /**
     * Removes the items at [removeAt], then inserts [added] so that each ends up at the matching
     * position in [insertAt]. Both index arrays must be strictly ascending. Removal indices refer to
     * the page before the call, and insert indices to the page after it.
     *
     * Costs one pass over the items after the first change, however many items change, so deleting
     * a large selection stays linear. Appending costs nothing beyond the new item.
     */
    fun splice(removeAt: IntArray, insertAt: IntArray, added: List<PageItem>) {
        require(insertAt.size == added.size) { "${insertAt.size} positions for ${added.size} items" }
        requireAscending(removeAt, size)
        requireAscending(insertAt, size - removeAt.size + insertAt.size)
        removeAll(removeAt)
        insertAll(insertAt, added)
        version++
    }

    /** The place for a new item of [layer]: after everything on that layer and the layers below it. */
    fun insertionIndex(layer: Long): Int {
        val rank = layers.rank(layer)
        var i = size
        while (i > 0 && layers.rank(items[i - 1]!!.layer) > rank) i--
        return i
    }

    /** The index where [layer] starts: how many items lie on the layers below it. */
    fun layerStart(layer: Long): Int {
        val rank = layers.rank(layer)
        var i = 0
        while (i < size && layers.rank(items[i]!!.layer) < rank) i++
        return i
    }

    /** The indices of the items on [layer], ascending. */
    fun indicesOn(layer: Long): IntArray {
        var count = 0
        for (i in 0 until size) if (items[i]!!.layer == layer) count++
        val out = IntArray(count)
        var k = 0
        for (i in 0 until size) if (items[i]!!.layer == layer) out[k++] = i
        return out
    }

    /** True if the item is on a layer the tools can change. Items on other layers are skipped like locked ones. */
    fun isEditable(item: PageItem) = item.layer == activeLayer

    /** True if the item's layer is shown. */
    fun isShown(item: PageItem) = layers[item.layer]?.visible != false

    /**
     * Swaps everything at once: the items, which must be sorted by the rank of their layer in [newLayers], and the layers. This is how layers
     * are added, taken away, reordered, hidden and renamed. The whole page is marked changed, since an item can paint
     * differently from a layer being hidden or moved, and any tile might hold it.
     */
    fun restructure(newItems: List<PageItem>, newLayers: PageLayers) {
        for (i in 0 until size) markDirty(items[i]!!)
        for (item in newItems) markDirty(item)
        layers = newLayers
        activeLayer = activeLayer
        items.fill(null)
        if (items.size < newItems.size) {
            items = arrayOfNulls(newItems.size)
            boxes = FloatArray(items.size * BOX_STRIDE)
            paintBoxes = FloatArray(items.size * BOX_STRIDE)
        }
        newItems.forEachIndexed { i, item -> put(i, item) }
        size = newItems.size
        version++
    }

    /** The area changed since the last call, or null if nothing changed. */
    fun takeDirtyBounds(): Bounds? = dirty.also { dirty = null }

    private fun removeAll(removeAt: IntArray) {
        if (removeAt.isEmpty()) return
        var write = removeAt[0]
        for (k in removeAt.indices) {
            markDirty(items[removeAt[k]]!!)
            val runStart = removeAt[k] + 1
            val runEnd = if (k + 1 < removeAt.size) removeAt[k + 1] else size
            move(runStart, write, runEnd - runStart)
            write += runEnd - runStart
        }
        items.fill(null, write, size)
        size = write
    }

    private fun insertAll(insertAt: IntArray, added: List<PageItem>) {
        if (insertAt.isEmpty()) return
        ensureCapacity(size + insertAt.size)
        var unmovedEnd = size
        for (k in insertAt.indices.reversed()) {
            // k inserted items land before this one, so the old items before it number insertAt[k] - k.
            val oldStart = insertAt[k] - k
            move(oldStart, insertAt[k] + 1, unmovedEnd - oldStart)
            put(insertAt[k], added[k])
            markDirty(added[k])
            unmovedEnd = oldStart
        }
        size += insertAt.size
    }

    private fun put(index: Int, item: PageItem) {
        items[index] = item
        val box = item.bounds
        val offset = index * BOX_STRIDE
        boxes[offset] = box.left
        boxes[offset + 1] = box.top
        boxes[offset + 2] = box.right
        boxes[offset + 3] = box.bottom
        val painted = item.paintBounds
        paintBoxes[offset] = painted.left
        paintBoxes[offset + 1] = painted.top
        paintBoxes[offset + 2] = painted.right
        paintBoxes[offset + 3] = painted.bottom
    }

    private fun move(from: Int, to: Int, count: Int) {
        if (count == 0 || from == to) return
        System.arraycopy(items, from, items, to, count)
        System.arraycopy(boxes, from * BOX_STRIDE, boxes, to * BOX_STRIDE, count * BOX_STRIDE)
        System.arraycopy(paintBoxes, from * BOX_STRIDE, paintBoxes, to * BOX_STRIDE, count * BOX_STRIDE)
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= items.size) return
        val capacity = maxOf(needed, items.size * 2)
        items = items.copyOf(capacity)
        boxes = boxes.copyOf(capacity * BOX_STRIDE)
        paintBoxes = paintBoxes.copyOf(capacity * BOX_STRIDE)
    }

    private fun markDirty(item: PageItem) {
        dirty = dirty?.union(item.paintBounds) ?: item.paintBounds
    }

    private fun checkIndex(index: Int): Int {
        if (index !in 0 until size) throw IndexOutOfBoundsException("$index of $size")
        return index
    }

    private fun requireAscending(indices: IntArray, limit: Int) {
        for (k in indices.indices) {
            require(indices[k] in 0 until limit) { "Index ${indices[k]} outside $limit" }
            require(k == 0 || indices[k - 1] < indices[k]) { "Indices must ascend" }
        }
    }

    private companion object {
        const val MIN_CAPACITY = 16
        const val BOX_STRIDE = 4
    }
}
