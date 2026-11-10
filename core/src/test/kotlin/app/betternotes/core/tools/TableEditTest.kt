package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.TableItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TableEditTest {
    private fun table(columns: Int = 3, rows: Int = 2) = TableItem(
        id = 1,
        frame = Frame(100f, 50f, columns * 60f, rows * 20f),
        columnWeights = List(columns) { 1f },
        rowWeights = List(rows) { 1f },
        cells = List(columns * rows) { "c$it" },
    )

    @Test
    fun dividersSitBetweenTheCells() {
        val t = table()
        assertEquals(160f, t.columnDividerX(1), 0.001f)
        assertEquals(220f, t.columnDividerX(2), 0.001f)
        assertEquals(70f, t.rowDividerY(1), 0.001f)
        assertEquals(3, t.handles().size)
    }

    @Test
    fun draggingADividerSharesTheSizeOfTheTwoBesideIt() {
        val moved = table().withDividerAt(TableHandle.ColumnDivider(1), 190f, 0f)
        assertEquals(190f, moved.columnDividerX(1), 0.001f)
        // The line after it, and the table's size, are where they were.
        assertEquals(220f, moved.columnDividerX(2), 0.001f)
        assertEquals(180f, moved.frame.width, 0.001f)
    }

    @Test
    fun aDividerCannotCrushACell() {
        val moved = table().withDividerAt(TableHandle.ColumnDivider(2), 100f, 0f)
        val gap = moved.columnDividerX(2) - moved.columnDividerX(1)
        assertEquals(true, gap >= 0.07f * moved.frame.width)
        val rows = table().withDividerAt(TableHandle.RowDivider(1), 0f, 5000f)
        assertEquals(true, rows.frame.top + rows.frame.height - rows.rowDividerY(1) >= 0.07f * rows.frame.height)
    }

    @Test
    fun aColumnIsAddedAtTheEndWithTheOthersKeepingTheirSize() {
        val t = table()
        val added = t.withColumnAdded()
        assertEquals(4, added.columnWeights.size)
        assertEquals(8, added.cells.size)
        assertEquals(240f, added.frame.width, 0.001f)
        assertEquals(t.columnDividerX(1), added.columnDividerX(1), 0.001f)
        // Cells keep their text and the new column is empty.
        assertEquals(listOf("c0", "c1", "c2", "", "c3", "c4", "c5", ""), added.cells)
    }

    @Test
    fun aColumnIsTakenFromTheEnd() {
        val removed = table().withColumnRemoved()
        assertEquals(2, removed.columnWeights.size)
        assertEquals(listOf("c0", "c1", "c3", "c4"), removed.cells)
        assertEquals(120f, removed.frame.width, 0.001f)
        // The last column is never taken.
        val one = table(columns = 1).withColumnRemoved()
        assertEquals(1, one.columnWeights.size)
    }

    @Test
    fun aRowIsAddedAndTaken() {
        val t = table()
        val added = t.withRowAdded()
        assertEquals(3, added.rowWeights.size)
        assertEquals(9, added.cells.size)
        assertEquals(60f, added.frame.height, 0.001f)
        val back = added.withRowRemoved()
        assertEquals(t.cells, back.cells)
        assertEquals(t.frame.height, back.frame.height, 0.001f)
        assertEquals(1, table(rows = 1).withRowRemoved().rowWeights.size)
    }
}
