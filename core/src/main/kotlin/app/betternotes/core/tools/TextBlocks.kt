package app.betternotes.core.tools

import app.betternotes.core.edit.ItemsEdit
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.TextItem

/**
 * A text box is written in blocks, and each block is a step of undo and redo of its own: undo takes away the last block written, and the
 * text of the blocks before it stays. A block ends when the writing pauses, and again when the box is left. A box that holds one block
 * has one step, and undoing it takes the whole box away, as there is nothing left behind.
 */
object TextBlocks {
    /**
     * The edit that turns the text box with the id of [after], as the page has it now ([before], null when it is not on the page), into
     * [after]: it adds the box when it is new, replaces it when it changed, removes it when it is left with no text, and is null when nothing
     * changed.
     */
    fun between(pageId: Long, content: PageContent, before: TextItem?, after: TextItem): ItemsEdit? {
        val at = if (before == null) -1 else (0 until content.size).firstOrNull { content[it].id == before.id } ?: -1
        return when {
            at < 0 -> if (after.text.isEmpty()) null else ItemsEdit.addOnTop(pageId, content, after)
            after.text.isEmpty() -> ItemsEdit.remove(pageId, content, intArrayOf(at))
            before == after -> null
            else -> ItemsEdit.replaceOnTop(pageId, content, intArrayOf(at), listOf(after))
        }
    }
}
