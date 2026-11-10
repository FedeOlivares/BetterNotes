package app.betternotes.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingFileTest {
    @Test
    fun pdfByType() = assertEquals(IncomingFile.PDF, IncomingFile.of("application/pdf", "scan"))

    @Test
    fun pdfTypeIsNotCaseSensitive() = assertEquals(IncomingFile.PDF, IncomingFile.of("Application/PDF", null))

    @Test
    fun pdfByNameWhenNoType() {
        assertEquals(IncomingFile.PDF, IncomingFile.of(null, "Tablitas quirúrgica (1).PDF"))
        assertEquals(IncomingFile.PDF, IncomingFile.of("application/octet-stream", "a.pdf"))
    }

    @Test
    fun notebookByNameWhateverTheType() {
        assertEquals(IncomingFile.NOTEBOOK, IncomingFile.of("application/octet-stream", "Anatomy.bnote"))
        assertEquals(IncomingFile.NOTEBOOK, IncomingFile.of("application/zip", "Anatomy.BNOTE"))
        assertEquals(IncomingFile.NOTEBOOK, IncomingFile.of(null, "a.b.bnote"))
    }

    @Test
    fun typePdfWinsOverNotebookName() = assertEquals(IncomingFile.PDF, IncomingFile.of("application/pdf", "x.bnote"))

    @Test
    fun otherFilesAreRefused() {
        assertNull(IncomingFile.of("image/png", "a.png"))
        assertNull(IncomingFile.of("application/zip", "notes.zip"))
        assertNull(IncomingFile.of(null, null))
        assertNull(IncomingFile.of(null, "bnote"))
    }
}
