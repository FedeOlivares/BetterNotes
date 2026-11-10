package app.betternotes.ui.editor

import android.net.Uri
import app.betternotes.core.format.DocumentStore

enum class SourceKind { OWNER, NOTE, CANVAS, PDF, LINKED }

/**
 * One line of the binder: the notebook itself, a source made from the panel, or a notebook linked as a source.
 * [store] is where it is kept, and [libraryId] is set for a linked notebook, which lives on the home screen.
 */
internal class SourceEntry(
    val id: Long,
    val title: String,
    val pageCount: Int,
    val kind: SourceKind,
    val store: DocumentStore,
    val libraryId: Long? = null,
)

/** What the Sources panel's "+" can add. Each one becomes an entry of the binder, never an item on the open canvas. */
internal sealed interface SourceRequest {
    data object Canvas : SourceRequest

    /** A picture, which gets a canvas of its own sized to it. */
    class Image(val uri: Uri) : SourceRequest

    /** A file from the picker: a PDF, a recording or a picture. */
    class File(val uri: Uri) : SourceRequest

    /** A notebook from the library, linked rather than copied. */
    class Notebook(val libraryId: Long) : SourceRequest
}
