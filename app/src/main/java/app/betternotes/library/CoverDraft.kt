package app.betternotes.library

import android.content.Context
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.DocumentKind
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.Page
import app.betternotes.core.model.PageItem
import app.betternotes.core.template.CoverDesign
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.toItems
import java.io.File

/**
 * The cover being edited before its notebook exists: a one-page document of its own, in the app's private files, that the editor
 * opens so every part of the cover can be edited. The New Notebook dialog then puts what is left on the cover of the notebook it
 * makes. There is only one draft at a time; starting another replaces it.
 */
internal object CoverDraft {
    private fun folder(context: Context) = File(context.filesDir, "cover-draft")

    fun store(context: Context) = DocumentStore(folder(context))

    /** Replaces the draft with [design] as items to edit, with [recolor] applied, on a cover page of [size]. */
    fun prepare(context: Context, design: CoverDesign, size: PageSize, recolor: Map<Int, Int>): DocumentStore {
        folder(context).deleteRecursively()
        val store = store(context)
        val now = System.currentTimeMillis()
        val page = Page(IdSource.Random.next(), size, PageBackground(), cover = CoverDesign.CUSTOM_ID)
        store.writeDocument(Document(IdSource.Random.next(), "Cover", DocumentKind.NOTE, now, now, listOf(page)))
        store.writePage(page.id, design.toItems(size.widthPt, size.heightPt, recolor = recolor))
        return store
    }

    fun discard(context: Context) {
        folder(context).deleteRecursively()
    }

    /** What the cover holds now, bottom first. */
    fun items(store: DocumentStore): List<PageItem> = store.readPage(store.readDocument().pages[0].id)
}
