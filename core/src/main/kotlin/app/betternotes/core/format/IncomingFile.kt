package app.betternotes.core.format

/** What another app hands over when the user picks BetterNotes under "Open with", told apart by its type and name. */
enum class IncomingFile {
    PDF,
    NOTEBOOK,
    ;

    companion object {
        const val PDF_TYPE = "application/pdf"

        /**
         * The kind of file to import, or null when it is neither. A notebook is known by its extension, because file managers give
         * it any type from a generic stream to a zip, or none; a PDF by its type or its extension, as some managers send no type.
         * A notebook that is also called `.pdf` is still taken as a PDF, since the type says so.
         */
        fun of(mimeType: String?, fileName: String?): IncomingFile? {
            val name = fileName.orEmpty().lowercase()
            return when {
                mimeType.equals(PDF_TYPE, ignoreCase = true) -> PDF
                name.endsWith("." + NotebookArchive.EXTENSION) -> NOTEBOOK
                name.endsWith(".pdf") -> PDF
                else -> null
            }
        }
    }
}
