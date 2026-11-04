package app.betternotes.core.edit

/** Undo and redo stacks. Once [limit] edits are held, the oldest is forgotten. */
class UndoHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Applies [edit] and records it. */
    fun perform(edit: Edit, target: EditTarget) {
        edit.apply(target)
        record(edit)
    }

    /** Records an edit whose changes are already on the page, as a finished eraser drag's are. */
    fun record(edit: Edit) {
        redoStack.clear()
        undoStack.addLast(edit)
        if (undoStack.size > limit) undoStack.removeFirst()
    }

    /** Returns false if there was nothing to undo. */
    fun undo(target: EditTarget): Boolean {
        val edit = undoStack.removeLastOrNull() ?: return false
        edit.revert(target)
        redoStack.addLast(edit)
        return true
    }

    /** Returns false if there was nothing to redo. */
    fun redo(target: EditTarget): Boolean {
        val edit = redoStack.removeLastOrNull() ?: return false
        edit.apply(target)
        undoStack.addLast(edit)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
    }
}
