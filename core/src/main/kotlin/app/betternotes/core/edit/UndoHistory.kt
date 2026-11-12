package app.betternotes.core.edit

import app.betternotes.core.model.StickyNoteItem

/** What an edit is to undo and redo: a step of its own, or the opening or closing of a post-it, which is not one. */
enum class StepKind {
    /** A step of undo and redo of its own. */
    STEP,

    /** A post-it opening, or closing with nothing folded into it: only its open flag changes. Undo and redo pass over it, and keep the flag as it is. */
    OPEN_CLOSE,

    /** A post-it closing with items folded into it. Items change places, so it is taken back with the step before it, and the post-it opens. */
    FOLD,
}

/**
 * Undo and redo stacks. Once [limit] edits are held, the oldest is forgotten.
 *
 * Undo and redo are for what was drawn and changed, not for what was looked at. An edit that is not a [StepKind.STEP] is made like any other
 * and is kept in the stack, because later edits count on the page being as it left it, but it is not a step of its own: undo takes back the last
 * step and the edits that are not steps made after it, and redo puts them back together. A post-it that was opened or closed since stays as it
 * is, however the steps around it go back and forth.
 */
class UndoHistory(private val limit: Int = DEFAULT_LIMIT) {
    private class Entry(val edit: Edit, val kind: StepKind)

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    /** Whether there is a step to take back. */
    val canUndo get() = undoStack.any { it.kind == StepKind.STEP }
    val canRedo get() = redoStack.any { it.kind == StepKind.STEP }

    /** Applies [edit] and records it. */
    fun perform(edit: Edit, target: EditTarget, kind: StepKind = StepKind.STEP) {
        edit.apply(target)
        record(edit, kind)
    }

    /** Records an edit whose changes are already on the page, as a finished eraser drag's are. */
    fun record(edit: Edit, kind: StepKind = StepKind.STEP) {
        redoStack.clear()
        undoStack.addLast(Entry(edit, kind))
        if (undoStack.size > limit) undoStack.removeFirst()
    }

    /** Returns false if there was nothing to undo: no step. */
    fun undo(target: EditTarget): Boolean {
        if (!canUndo) return false
        val group = ArrayList<Entry>()
        do {
            val entry = undoStack.removeLast()
            group += entry
        } while (entry.kind != StepKind.STEP)
        val step = group.last().edit
        val states = if (group.any { it.kind == StepKind.FOLD }) null else OpenStates.of(step, target)
        for (entry in group) {
            if (entry.kind != StepKind.OPEN_CLOSE) entry.edit.revert(target)
            redoStack.addLast(entry)
        }
        states?.restore(target)
        return true
    }

    /** Returns false if there was nothing to redo. */
    fun redo(target: EditTarget): Boolean {
        if (!canRedo) return false
        // What was taken back with a step comes back with it, and the redo that follows starts with the step itself.
        val group = ArrayList<Entry>()
        do {
            group += redoStack.removeLast()
        } while (group.last().kind != StepKind.STEP)
        while (redoStack.lastOrNull()?.kind.let { it != null && it != StepKind.STEP }) group += redoStack.removeLast()
        val step = group.first { it.kind == StepKind.STEP }.edit
        val states = if (group.any { it.kind == StepKind.FOLD }) null else OpenStates.of(step, target)
        for (entry in group) {
            if (entry.kind != StepKind.OPEN_CLOSE) entry.edit.apply(target)
            undoStack.addLast(entry)
        }
        states?.restore(target)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /** Whether each post-it of a page is open, as it was before a step went back or forth, to leave them so after it. */
    private class OpenStates(private val pageId: Long, private val open: Map<Long, Boolean>) {
        fun restore(target: EditTarget) {
            val content = target.content(pageId)
            for (i in 0 until content.size) {
                val note = content[i] as? StickyNoteItem ?: continue
                val was = open[note.id] ?: continue
                if (note.isOpen != was) content.splice(intArrayOf(i), intArrayOf(i), listOf(note.copy(isOpen = was)))
            }
        }

        companion object {
            fun of(step: Edit, target: EditTarget): OpenStates? {
                if (step !is ItemsEdit) return null
                val content = target.content(step.pageId)
                val open = HashMap<Long, Boolean>()
                for (i in 0 until content.size) (content[i] as? StickyNoteItem)?.let { open[it.id] = it.isOpen }
                return if (open.isEmpty()) null else OpenStates(step.pageId, open)
            }
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
    }
}
