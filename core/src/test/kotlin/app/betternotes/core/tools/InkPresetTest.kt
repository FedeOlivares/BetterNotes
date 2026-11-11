package app.betternotes.core.tools

import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkPresetTest {
    private val pen = InkPreset.defaults(InkTool.PEN)
    private val magenta = 0xFFFF00FF.toInt()

    @Test
    fun everyToolStartsWithFiveColorsAndThreeWidths() {
        for (tool in InkTool.entries) {
            val preset = InkPreset.defaults(tool)
            assertEquals(5, preset.colors.size)
            assertEquals(3, preset.widths.size)
        }
    }

    @Test
    fun theStyleUsesTheSelectedSlots() {
        val style = pen.copy(selectedColor = 1, selectedWidth = 2, line = LineKind.DASHED, opacity = 0.5f).style()
        assertEquals(pen.colors[1], style.color)
        assertEquals(pen.widths[2] * InkPreset.POINTS_PER_THICKNESS_UNIT, style.width, 0f)
        assertEquals(LineKind.DASHED, style.line.kind)
        assertEquals(0.5f, style.opacity, 0f)
    }

    @Test
    fun aThicknessValueIsTenthsOfAMillimeter() {
        // 10 tenths is 1 mm, and 25.4 mm is 72 points.
        assertEquals(72f / 25.4f, pen.withThickness(10).style().width, 0.001f)
    }

    @Test
    fun fineTuningChangesOnlyTheSlotInUseAndStaysInRange() {
        val tuned = pen.copy(selectedWidth = 1).withThickness(500)
        assertEquals(listOf(pen.widths[0], InkPreset.MAX_THICKNESS, pen.widths[2]), tuned.widths)
        assertEquals(InkPreset.MIN_THICKNESS, pen.withThickness(-3).thickness)
    }

    @Test
    fun pickingAColorInTheRowSelectsItsSlot() {
        val picked = pen.pickColor(pen.colors[3])
        assertEquals(3, picked.selectedColor)
        assertEquals(pen.colors, picked.colors)
    }

    @Test
    fun pickingAColorOutsideTheRowChangesTheSlotInUse() {
        val picked = pen.copy(selectedColor = 2).pickColor(magenta)
        assertEquals(2, picked.selectedColor)
        assertEquals(magenta, picked.colors[2])
        assertEquals(5, picked.colors.size)
    }

    @Test
    fun thereIsOnlyEverOneCustomColor() {
        val once = pen.withCustomColor(magenta)
        assertEquals(6, once.colors.size)
        assertEquals(magenta, once.color)
        val twice = once.withCustomColor(0xFF00FFFF.toInt())
        assertEquals(6, twice.colors.size)
        assertEquals(0xFF00FFFF.toInt(), twice.color)
    }

    @Test
    fun turningHoldOffMeansNoDelay() {
        assertEquals(InkPreset.DEFAULT_HOLD_MS, pen.holdToLineMs)
        assertEquals(0, pen.copy(holdToLine = false).holdToLineMs)
    }

    @Test
    fun everyInkToolStartsWithTheFourHelpersOn() {
        for (tool in InkTool.entries) {
            val preset = InkPreset.defaults(tool)
            assertTrue("$tool circle to lasso", preset.circleToLasso)
            assertTrue("$tool pressure", preset.pressure)
            assertTrue("$tool scribble to erase", preset.scribbleToErase)
            assertTrue("$tool hold to draw", preset.holdToLine)
        }
    }

    @Test
    fun theBarTakesAddedColorsUpToItsLimitThenReplacesTheSlotInUse() {
        var pen = InkPreset.defaults(InkTool.PEN)
        for (i in 1..InkPreset.MAX_COLOR_SLOTS) pen = pen.withColorAdded(0xFF100000.toInt() + i)
        assertEquals(InkPreset.MAX_COLOR_SLOTS, pen.colors.size)
        val extra = 0xFF00FF00.toInt()
        val replaced = pen.withColorAdded(extra)
        assertEquals(InkPreset.MAX_COLOR_SLOTS, replaced.colors.size)
        assertEquals(extra, replaced.color)
        // A color that is already there is selected, not added again.
        assertEquals(pen.colors[2], pen.withColorAdded(pen.colors[2]).color)
    }

    @Test
    fun aReorderedOrShortenedBarKeepsTheColorInUseSelected() {
        val pen = InkPreset.defaults(InkTool.PEN).pickColor(InkPreset.defaults(InkTool.PEN).colors[3])
        val inUse = pen.color
        val reordered = pen.withColorList(pen.colors.reversed())
        assertEquals(inUse, reordered.color)
        val shortened = pen.withColorList(listOf(pen.colors[0], inUse))
        assertEquals(2, shortened.colors.size)
        assertEquals(inUse, shortened.color)
        // A list without the color in use selects the first, and an empty list changes nothing.
        assertEquals(pen.colors[1], pen.withColorList(listOf(pen.colors[1])).color)
        assertEquals(pen, pen.withColorList(emptyList()))
    }
}
