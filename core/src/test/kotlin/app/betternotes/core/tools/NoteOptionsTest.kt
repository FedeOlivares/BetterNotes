package app.betternotes.core.tools

import app.betternotes.core.format.PageCodec
import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VerticalAlign
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteOptionsTest {
    private val ids = IdSource { 5L }

    @Test
    fun aNewTableIsSizedByItsCellsAndCarriesItsOptions() {
        val table = TableOptions(columns = 4, rows = 2, headerRow = true, headerColumn = false).newTable(10f, 20f, ids)
        assertEquals(4 * TableOptions.CELL_WIDTH, table.frame.width, 0f)
        assertEquals(2 * TableOptions.CELL_HEIGHT, table.frame.height, 0f)
        assertEquals(8, table.cells.size)
        assertEquals(true, table.headerRow)
        assertEquals(false, table.headerColumn)
    }

    @Test
    fun aPointFindsItsCell() {
        val table = TableOptions(columns = 3, rows = 2).newTable(0f, 0f, ids)
        assertEquals(0, table.cellAt(1f, 1f))
        assertEquals(2, table.cellAt(table.frame.width - 1f, 1f))
        assertEquals(4, table.cellAt(table.frame.width / 2f, table.frame.height - 1f))
        assertEquals(-1, table.cellAt(-1f, 1f))
        assertEquals("x", table.withCell(4, "x").cells[4])
    }

    @Test
    fun aNoteKeepsOneTextAndItsInk() {
        val note = NoteOptions().newSticky(0f, 0f, ids)
        val first = note.withText("one", 9f, TextStyle(), ids)
        assertEquals("one", first.typedText)
        val second = first.withText("two", 9f, TextStyle(bold = true), ids)
        assertEquals("two", second.typedText)
        assertEquals(1, second.items.size)
    }

    @Test
    fun styledTextAndTablesSurviveAFile() {
        val items: List<PageItem> = listOf(
            TextItem(
                1L,
                Frame(0f, 0f, 10f, 10f),
                "hi",
                style = TextStyle(bold = true, strikethrough = true, align = HorizontalAlign.END, verticalAlign = VerticalAlign.MIDDLE),
            ),
            TableItem(
                2L,
                Frame(0f, 0f, 10f, 10f),
                listOf(1f),
                listOf(1f),
                listOf("cell"),
                headerRow = true,
                fillColor = 0xFF112233.toInt(),
            ),
        )
        assertEquals(items, PageCodec.decode(PageCodec.encode(items)))
    }

    @Test
    fun plainTextAndTablesKeepTheirOriginalBytes() {
        val plain = TextItem(1L, Frame(0f, 0f, 10f, 10f), "hi")
        val styled = plain.copy(style = TextStyle(italic = true))
        assert(PageCodec.encode(listOf(plain)).size < PageCodec.encode(listOf(styled)).size)
    }
}
