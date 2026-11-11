package app.betternotes.core.edit

import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PageLayers

/**
 * Swaps a page's whole item list and its layers. Every layer change is this one edit: the lists are references to immutable items,
 * so keeping both versions costs little, and undo puts back exactly the old order.
 */
class RestructureLayers(
    val pageId: Long,
    val beforeItems: List<PageItem>,
    val before: PageLayers,
    val afterItems: List<PageItem>,
    val after: PageLayers,
) : Edit {
    override fun apply(target: EditTarget) = target.content(pageId).restructure(afterItems, after)
    override fun revert(target: EditTarget) = target.content(pageId).restructure(beforeItems, before)
}

/** The layer changes the Layers menu offers, as edits. Each returns null when it would change nothing. */
object LayerOps {
    private fun edit(pageId: Long, content: PageContent, items: List<PageItem>, layers: PageLayers): RestructureLayers? {
        if (layers == content.layers && items == content.toList()) return null
        return RestructureLayers(pageId, content.toList(), content.layers, items, layers)
    }

    /** Puts items in the order of their layers, keeping the order within each layer. */
    private fun sorted(items: List<PageItem>, layers: PageLayers) = items.sortedBy { layers.rank(it.layer) }

    fun add(pageId: Long, content: PageContent): RestructureLayers =
        RestructureLayers(pageId, content.toList(), content.layers, content.toList(), content.layers.withNewLayerAbove(content.activeLayer))

    fun rename(pageId: Long, content: PageContent, id: Long, name: String): RestructureLayers? {
        val layer = content.layers[id] ?: return null
        return edit(pageId, content, content.toList(), content.layers.withLayer(layer.copy(name = name.trim())))
    }

    fun setVisible(pageId: Long, content: PageContent, id: Long, visible: Boolean): RestructureLayers? {
        val layer = content.layers[id] ?: return null
        return edit(pageId, content, content.toList(), content.layers.withLayer(layer.copy(visible = visible)))
    }

    /** Moves layer [id] to place [to], 0 being the bottom. The items follow it. */
    fun move(pageId: Long, content: PageContent, id: Long, to: Int): RestructureLayers? {
        val layers = content.layers.moved(id, to)
        return edit(pageId, content, sorted(content.toList(), layers), layers)
    }

    /** Takes layer [id] away with its items. The last layer cannot be taken away. */
    fun delete(pageId: Long, content: PageContent, id: Long): RestructureLayers? {
        if (content.layers.size < 2 || content.layers[id] == null) return null
        return edit(pageId, content, content.toList().filter { it.layer != id }, content.layers.without(id))
    }

    /** Puts the items of layer [id] on the layer below, or above for the bottom one, and takes [id] away. */
    fun mergeDown(pageId: Long, content: PageContent, id: Long): RestructureLayers? {
        val layers = content.layers
        val rank = layers.rank(id)
        if (layers.size < 2 || rank < 0) return null
        val into = layers.all[if (rank == 0) 1 else rank - 1].id
        return edit(pageId, content, content.toList().map { it.onLayer(if (it.layer == id) into else it.layer) }, layers.without(id))
    }

    /** Moves the items at [indices] to layer [target], where they go on top of what is already there. */
    fun moveItems(pageId: Long, content: PageContent, indices: IntArray, target: Long): RestructureLayers? {
        if (content.layers[target] == null || indices.isEmpty()) return null
        val moving = indices.toHashSet()
        val items = content.toList()
        val rearranged = items.filterIndexed { i, _ -> i !in moving } + indices.map { items[it].onLayer(target) }
        return edit(pageId, content, sorted(rearranged, content.layers), content.layers)
    }
}

/** Makes [layers] the table of what [items] reads: items on a layer it lacks go to the bottom one, and the items are put in layer order. */
fun settleLayers(items: List<PageItem>, layers: PageLayers): List<PageItem> =
    items.map { it.onLayer(layers.idOrBottom(it.layer)) }.sortedBy { layers.rank(it.layer) }

