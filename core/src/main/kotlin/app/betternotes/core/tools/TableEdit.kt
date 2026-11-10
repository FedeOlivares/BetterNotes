package app.betternotes.core.tools

import app.betternotes.core.model.TableItem

/** A handle of a table in editing: the line between two columns, or between two rows. [index] is the first column or row after it. */
sealed interface TableHandle {
    data class ColumnDivider(val index: Int) : TableHandle

    data class RowDivider(val index: Int) : TableHandle
}

/** The columns and rows keep at least this share of the table, so no cell can be dragged away to nothing. */
private const val MIN_SHARE = 0.08f

/** The page x of the line that starts column [index] (1 up to the last one). */
fun TableItem.columnDividerX(index: Int): Float = frame.left + columnWeights.take(index).sum() / columnWeights.sum() * frame.width

/** The page y of the line that starts row [index]. */
fun TableItem.rowDividerY(index: Int): Float = frame.top + rowWeights.take(index).sum() / rowWeights.sum() * frame.height

/** Every divider the table has, for drawing and hit testing. */
fun TableItem.handles(): List<TableHandle> =
    (1 until columnWeights.size).map { TableHandle.ColumnDivider(it) } + (1 until rowWeights.size).map { TableHandle.RowDivider(it) }

/**
 * The table with [handle] moved to page point ([x], [y]): the two columns or rows beside it share their combined size differently, and
 * the rest of the table stays as it was. Neither may get narrower than a small share of the table.
 */
fun TableItem.withDividerAt(handle: TableHandle, x: Float, y: Float): TableItem = when (handle) {
    is TableHandle.ColumnDivider -> copy(
        columnWeights = moved(columnWeights, handle.index, (x - frame.left) / frame.width),
    )
    is TableHandle.RowDivider -> copy(
        rowWeights = moved(rowWeights, handle.index, (y - frame.top) / frame.height),
    )
}

/** Weights with the line before [index] moved to [fraction] of the whole. */
private fun moved(weights: List<Float>, index: Int, fraction: Float): List<Float> {
    val total = weights.sum()
    val before = weights.take(index - 1).sum() / total
    val pair = (weights[index - 1] + weights[index]) / total
    val min = minOf(MIN_SHARE, pair / 2f)
    val first = (fraction - before).coerceIn(min, pair - min)
    return weights.mapIndexed { i, weight ->
        when (i) {
            index - 1 -> first * total
            index -> (pair - first) * total
            else -> weight
        }
    }
}

/** A column added at the end, as wide as the average one. The table grows by that much, so the others keep their size. */
fun TableItem.withColumnAdded(): TableItem {
    val columns = columnWeights.size
    val weight = columnWeights.sum() / columns
    val grown = (columnWeights.sum() + weight) / columnWeights.sum()
    return copy(
        frame = frame.copy(width = frame.width * grown),
        columnWeights = columnWeights + weight,
        cells = List(rowWeights.size) { row -> cells.subList(row * columns, (row + 1) * columns) + "" }.flatten(),
    )
}

/** The last column taken away, and its width with it. A table keeps at least one column. */
fun TableItem.withColumnRemoved(): TableItem {
    val columns = columnWeights.size
    if (columns <= 1) return this
    val shrunk = (columnWeights.sum() - columnWeights.last()) / columnWeights.sum()
    return copy(
        frame = frame.copy(width = frame.width * shrunk),
        columnWeights = columnWeights.dropLast(1),
        cells = List(rowWeights.size) { row -> cells.subList(row * columns, (row + 1) * columns - 1) }.flatten(),
    )
}

/** A row added at the bottom, as tall as the average one. */
fun TableItem.withRowAdded(): TableItem {
    val weight = rowWeights.sum() / rowWeights.size
    val grown = (rowWeights.sum() + weight) / rowWeights.sum()
    return copy(
        frame = frame.copy(height = frame.height * grown),
        rowWeights = rowWeights + weight,
        cells = cells + List(columnWeights.size) { "" },
    )
}

/** The bottom row taken away, and its height with it. A table keeps at least one row. */
fun TableItem.withRowRemoved(): TableItem {
    if (rowWeights.size <= 1) return this
    val shrunk = (rowWeights.sum() - rowWeights.last()) / rowWeights.sum()
    return copy(
        frame = frame.copy(height = frame.height * shrunk),
        rowWeights = rowWeights.dropLast(1),
        cells = cells.dropLast(columnWeights.size),
    )
}
