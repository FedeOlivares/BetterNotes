package app.betternotes.library

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.library.Library
import app.betternotes.core.library.LibraryEntry
import app.betternotes.core.library.LibraryStore
import app.betternotes.core.library.ScannedDocument
import app.betternotes.core.model.Document
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

/**
 * Fills the debug app's library with made-up content in the reference screenshot's arrangement, so the
 * real screen can be compared with it. It replaces what is there, so it only runs when asked:
 *
 * adb shell am instrument -w -e seedDemo true -e class app.betternotes.library.LibraryDemoSeed \
 *   app.betternotes.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class LibraryDemoSeed {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LibraryStore(context.filesDir)
    private val now = System.currentTimeMillis()

    @Test
    fun seed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedDemo") == "true")
        store.documentsDir.deleteRecursively()
        File(context.cacheDir, "thumbnails").deleteRecursively()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putString("user_name", "Federico") }

        val folder = LibraryEntry(1L, "Topics in surgery", null, now - days(55))
        val entries = listOf(
            pdf("Summary_of_Pathology_and_Anatomy_Partial", 6, "Pathology: summary"),
            pdf("Surgical slides", 12, "CLINICAL SURGERY"),
            note("MED LOCAL Firemen Care Notes", 50, strokes = 90),
            note("Surgery", 53, strokes = 0),
            folder,
            pdf("35601_Test_application (1)", 119, null),
            canvas("Medical pathology", 125),
            pdf("Popliteal aneurysm prototype", 126, null),
            pdf("Diabetic foot", 126, "Diabetic foot"),
            pdf("AAA rupture", 127, "AAA rupture"),
            pdf("Lower limb ischemia", 127, "Lower limb ischemia"),
            pdf("Emergency guidelines", 127, "Emergency guidelines"),
        )
        store.write(Library(entries))
    }

    private fun days(count: Long) = count * 24 * 60 * 60 * 1000

    private fun note(title: String, age: Long, strokes: Int): LibraryEntry {
        val document = Document.newNote(title, PageSize.A4, PageBackground(template = PageTemplate.Dotted()), now - days(age))
        return save(document) { writePage(document.pages[0].id, handwriting(strokes, 60f, 60f)) }
    }

    private fun canvas(title: String, age: Long): LibraryEntry {
        val document = Document.newCanvas(title, PageBackground(), now - days(age))
        return save(document) {
            writePage(document.pages[0].id, handwriting(30, 0f, 0f) + handwriting(30, 900f, 500f))
        }
    }

    private fun pdf(title: String, age: Long, heading: String?): LibraryEntry {
        val probe = store.documentStore(title.hashCode().toLong())
        val asset = probe.assetFile("source.pdf")
        PdfDocument().apply {
            val page = startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            if (heading != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(30, 50, 100)
                    textSize = 30f
                    isFakeBoldText = true
                }
                page.canvas.drawText(heading, 70f, 120f, paint)
                paint.apply {
                    color = Color.DKGRAY
                    textSize = 11f
                    isFakeBoldText = false
                }
                for (line in 0 until 30) page.canvas.drawText("Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod", 70f, 170f + line * 18, paint)
            }
            finishPage(page)
            FileOutputStream(asset).use { writeTo(it) }
            close()
        }
        val document = Document.fromPdf(title, "source.pdf", listOf(PageSize(595f, 842f)), now - days(age))
        val entry = saveTo(probe, document)
        val final = store.documentStore(document.id).root
        probe.root.renameTo(final)
        return entry.copy(document = entry.document!!.copy(savedAtMs = DocumentStore(final).descriptionModifiedMs()))
    }

    private fun save(document: Document, write: DocumentStore.() -> Unit): LibraryEntry =
        saveTo(store.documentStore(document.id).apply(write), document)

    private fun saveTo(documents: DocumentStore, document: Document): LibraryEntry {
        documents.writeDocument(document)
        val scanned = ScannedDocument(document, documents.descriptionModifiedMs())
        return LibraryEntry(scanned.id, scanned.name, null, scanned.modifiedAtMs, document = scanned.info)
    }

    private fun handwriting(count: Int, left: Float, top: Float): List<StrokeItem> = List(count) { i ->
        val x = left + (i % 9) * 52f
        val y = top + (i / 9) * 30f
        val points = StrokePointsBuilder().apply {
            for (step in 0..24) {
                add(x + step * 1.8f, y + 8f * sin(step * 0.9f + i), 0.6f, timeMs = step * 8f)
            }
        }.build()
        StrokeItem(i.toLong() + 1 + left.toLong() * 1000, InkStyle(color = Color.BLACK, width = 1.2f), points)
    }
}
