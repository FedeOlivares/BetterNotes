package app.betternotes.core.text

/** Naming for the fonts a person adds: a font is known by its file's name, and the notes that use it store that name. */
object FontFiles {
    val EXTENSIONS = listOf("ttf", "otf")

    private const val MAX_NAME = 64
    private val FORBIDDEN = setOf('<', '>', ':', '"', '|', '?', '*')

    /** The extension of [fileName] if it is one of a font's, in lower case. */
    fun extensionOf(fileName: String): String? =
        fileName.substringAfterLast('.', "").lowercase().takeIf { it in EXTENSIONS }

    /**
     * The name a font file is known by: its file name without the extension, with anything a file name cannot hold
     * or that would look like a folder taken out. Null when the file is not a font's or nothing is left of the name.
     */
    fun idOf(fileName: String): String? {
        extensionOf(fileName) ?: return null
        val base = fileName.substringBeforeLast('.').substringAfterLast('/').substringAfterLast('\\')
        val clean = base.filter { it >= ' ' && it !in FORBIDDEN }.trim().trimStart('.').take(MAX_NAME).trim()
        return clean.ifEmpty { null }
    }
}
