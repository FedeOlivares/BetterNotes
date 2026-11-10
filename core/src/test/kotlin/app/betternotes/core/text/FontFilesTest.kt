package app.betternotes.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontFilesTest {
    @Test
    fun `a font is named by its file without the extension`() {
        assertEquals("Lobster-Regular", FontFiles.idOf("Lobster-Regular.ttf"))
        assertEquals("My Font", FontFiles.idOf("My Font.OTF"))
    }

    @Test
    fun `folders and characters a file name cannot hold are taken out`() {
        assertEquals("evil", FontFiles.idOf("../../evil.ttf"))
        assertEquals("ab", FontFiles.idOf("a:b?.ttf"))
        assertEquals("hidden", FontFiles.idOf(".hidden.ttf"))
    }

    @Test
    fun `other files and empty names are not fonts`() {
        assertNull(FontFiles.idOf("notes.pdf"))
        assertNull(FontFiles.idOf("noextension"))
        assertNull(FontFiles.idOf(".ttf"))
        assertNull(FontFiles.idOf("???.ttf"))
    }

    @Test
    fun `a long name is cut`() {
        assertEquals(64, FontFiles.idOf("x".repeat(200) + ".ttf")!!.length)
    }
}
