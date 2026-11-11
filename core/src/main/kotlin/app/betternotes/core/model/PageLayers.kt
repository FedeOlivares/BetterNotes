package app.betternotes.core.model

/** One layer of a page. An empty [name] means the app's own name for it, "Layer" and its place from the bottom. */
data class Layer(val id: Long, val name: String = "", val visible: Boolean = true)

/**
 * The layers of one page, bottom first. A page always has at least one. Every item sits on exactly one layer,
 * and [PageContent] keeps the items sorted by layer, so painting in item order paints the bottom layer first.
 */
class PageLayers(val all: List<Layer>) {
    init {
        require(all.isNotEmpty()) { "A page needs a layer" }
        require(all.map { it.id }.toSet().size == all.size) { "Duplicate layer id" }
    }

    val size get() = all.size
    val top get() = all.last()

    /** True for what every page has before layers are used. Such a page is saved without a layer table. */
    val isDefault get() = all.size == 1 && all[0] == Layer(FIRST_LAYER_ID)

    fun rank(id: Long): Int = all.indexOfFirst { it.id == id }

    operator fun get(id: Long): Layer? = all.firstOrNull { it.id == id }

    /** The layer an item of an unknown layer is put on: the bottom one. */
    fun idOrBottom(id: Long): Long = if (rank(id) >= 0) id else all[0].id

    val hiddenIds: Set<Long> get() = all.filter { !it.visible }.mapTo(HashSet()) { it.id }

    /** A new empty layer just above the layer [above]. */
    fun withNewLayerAbove(above: Long): PageLayers {
        val newId = all.maxOf { it.id } + 1
        val at = (rank(above) + 1).coerceIn(0, all.size)
        return PageLayers(all.toMutableList().apply { add(at, Layer(newId)) })
    }

    fun withLayer(layer: Layer) = PageLayers(all.map { if (it.id == layer.id) layer else it })

    fun without(id: Long) = PageLayers(all.filter { it.id != id })

    /** The layers with [id] moved to the place [to] (0 is the bottom). */
    fun moved(id: Long, to: Int): PageLayers {
        val rest = all.filter { it.id != id }.toMutableList()
        val moving = this[id] ?: return this
        rest.add(to.coerceIn(0, rest.size), moving)
        return PageLayers(rest)
    }

    override fun equals(other: Any?) = other is PageLayers && all == other.all
    override fun hashCode() = all.hashCode()

    companion object {
        const val FIRST_LAYER_ID = 0L
        val Single = PageLayers(listOf(Layer(FIRST_LAYER_ID)))
    }
}
