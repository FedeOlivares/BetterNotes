package app.betternotes.core.tools

/**
 * What the eraser is set to. [sizes] are the three sizes in the toolbar, each from [MIN_SIZE] to
 * [MAX_SIZE], and [whole] removes the entire item the tip touches instead of only the ink under it.
 */
data class EraserOptions(
    val sizes: List<Int> = listOf(2, 5, 9),
    val selectedSize: Int = 1,
    val whole: Boolean = false,
    val lockedToo: Boolean = false,
    val autoDeselect: Boolean = false,
    val kinds: Set<ItemKind> = DEFAULT_ERASABLE,
) {
    init {
        require(sizes.size == SIZE_SLOTS) { "${sizes.size} sizes" }
        require(selectedSize in sizes.indices) { "Size slot $selectedSize" }
    }

    val size get() = sizes[selectedSize]

    /** How far the tip reaches from its center, in points. */
    val radiusPt get() = size * POINTS_PER_SIZE

    val mode get() = if (whole) EraseMode.WHOLE else EraseMode.PARTIAL

    fun withSize(value: Int): EraserOptions {
        val next = sizes.toMutableList()
        next[selectedSize] = value.coerceIn(MIN_SIZE, MAX_SIZE)
        return copy(sizes = next)
    }

    companion object {
        const val SIZE_SLOTS = 3
        const val MIN_SIZE = 1
        const val MAX_SIZE = 10
        const val POINTS_PER_SIZE = 2f
    }
}

/** What the lasso is set to. [rectangle] drags out a box rather than following the pen. */
data class LassoOptions(
    val rectangle: Boolean = true,
    val lockedToo: Boolean = false,
    val takeScreenshot: Boolean = false,
    val fullSelectionOnly: Boolean = false,
    /** Selects anything with a part inside, not only what is mostly or wholly inside. */
    val anyPortion: Boolean = false,
    // Recordings have no place on the page to select them at.
    val kinds: Set<ItemKind> = ALL_ITEM_KINDS - ItemKind.AUDIO,
)

/** Adds a kind that is missing, or takes away one that is there. */
fun Set<ItemKind>.toggled(kind: ItemKind) = if (kind in this) this - kind else this + kind
