package app.betternotes.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ReturnTargetTest {
    @Test
    fun `a tool goes back to the one before it`() {
        assertEquals("brush", ReturnTarget.target("brush", "tape", "pen"))
    }

    @Test
    fun `with none before it, or itself before it, it goes back to the pen`() {
        assertEquals("pen", ReturnTarget.target(null, "tape", "pen"))
        assertEquals("pen", ReturnTarget.target("tape", "tape", "pen"))
    }

    @Test
    fun `the pen has the pen to go back to`() {
        assertEquals("pen", ReturnTarget.target(null, "pen", "pen"))
    }
}
