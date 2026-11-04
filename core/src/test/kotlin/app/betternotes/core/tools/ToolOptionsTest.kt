package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOptionsTest {
    @Test
    fun anEraserSizeChangesOnlyTheSlotInUseAndStaysInRange() {
        val options = EraserOptions().withSize(99)
        assertEquals(listOf(2, EraserOptions.MAX_SIZE, 9), options.sizes)
        assertEquals(1, options.withSize(-1).size)
    }

    @Test
    fun theEraserModeFollowsTheEntireObjectSwitch() {
        assertEquals(EraseMode.PARTIAL, EraserOptions().mode)
        assertEquals(EraseMode.WHOLE, EraserOptions(whole = true).mode)
    }

    @Test
    fun aKindTogglesInAndOut() {
        val without = ALL_ITEM_KINDS.toggled(ItemKind.IMAGE)
        assertFalse(ItemKind.IMAGE in without)
        assertTrue(ItemKind.IMAGE in without.toggled(ItemKind.IMAGE))
    }
}
