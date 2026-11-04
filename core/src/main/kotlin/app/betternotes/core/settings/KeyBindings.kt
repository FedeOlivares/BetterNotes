package app.betternotes.core.settings

/** Everything a keyboard shortcut can do. [repeats] means holding the key keeps doing it. */
enum class EditorAction(val repeats: Boolean = false) {
    UNDO(repeats = true),
    REDO(repeats = true),
    TOGGLE_FINGER_DRAW,
    TOGGLE_VIEW_ONLY,
    TOGGLE_TOOLBAR,

    PEN,
    PENCIL,
    HIGHLIGHTER,
    LASSO,
    ERASER,
    SHAPE,
    IMAGE,
    CAMERA,
    STICKY_NOTE,
    TABLE,
    LOUPE,
    RECORDER,
    RULER,
    LASER_POINTER,

    OPEN_TOOL_PANEL,
    COLOR_1,
    COLOR_2,
    COLOR_3,
    COLOR_4,
    COLOR_5,
    WIDTH_1,
    WIDTH_2,
    WIDTH_3,
    TOGGLE_ERASE_MODE,
    CLEAR_PAGE,

    COPY,
    CUT,
    PASTE,
    DELETE_SELECTION,

    NEXT_PAGE(repeats = true),
    PREVIOUS_PAGE(repeats = true),
    FIRST_PAGE,
    LAST_PAGE,
    ADD_PAGE,
    PAGE_GRID,
    PAGE_SWITCHER,
    ZOOM_IN(repeats = true),
    ZOOM_OUT(repeats = true),
    ZOOM_REAL_SIZE,

    SEARCH,
    EXPORT,
    SETTINGS,
}

/** Key codes, with the values of Android's KeyEvent, so that this module needs no Android. */
object KeyCodes {
    const val KEY_0 = 7
    const val KEY_1 = 8
    const val KEY_2 = 9
    const val KEY_3 = 10
    const val KEY_4 = 11
    const val KEY_5 = 12
    const val C = 31
    const val E = 33
    const val F = 34
    const val G = 35
    const val H = 36
    const val I = 37
    const val K = 39
    const val L = 40
    const val N = 42
    const val O = 43
    const val P = 44
    const val Q = 45
    const val R = 46
    const val S = 47
    const val T = 48
    const val U = 49
    const val V = 50
    const val W = 51
    const val X = 52
    const val Y = 53
    const val Z = 54
    const val COMMA = 55
    const val TAB = 61
    const val DEL = 67
    const val MINUS = 69
    const val EQUALS = 70
    const val PAGE_UP = 92
    const val PAGE_DOWN = 93
    const val FORWARD_DEL = 112
    const val MOVE_HOME = 122
    const val MOVE_END = 123
}

/** A key with the modifiers held. [modifiers] is a sum of the masks in the companion. */
data class KeyChord(val keyCode: Int, val modifiers: Int = 0) {
    init {
        require(keyCode > 0) { "Bad key code $keyCode" }
        require(modifiers and ALL_MODIFIERS == modifiers) { "Bad modifiers $modifiers" }
    }

    companion object {
        const val CTRL = 1
        const val SHIFT = 2
        const val ALT = 4
        const val META = 8
        private const val ALL_MODIFIERS = CTRL or SHIFT or ALT or META
    }
}

/**
 * Which chord does what. An action has at most one chord and a chord at most one action. Only what
 * differs from the defaults is kept, so an action added in a later version gets its default chord.
 * Immutable: a change returns a new set.
 */
class KeyBindings private constructor(internal val overrides: Map<EditorAction, KeyChord?>) {
    private val chords = HashMap<EditorAction, KeyChord>()
    private val actions = HashMap<KeyChord, EditorAction>()

    init {
        val taken = overrides.values.filterNotNullTo(HashSet())
        for ((action, chord) in DEFAULTS) {
            // A default whose chord was given to another action stays unbound.
            if (action !in overrides && chord !in taken) put(action, chord)
        }
        for ((action, chord) in overrides) {
            if (chord != null) put(action, chord)
        }
    }

    private fun put(action: EditorAction, chord: KeyChord) {
        chords[action] = chord
        actions[chord] = action
    }

    fun chordFor(action: EditorAction): KeyChord? = chords[action]

    fun actionFor(chord: KeyChord): EditorAction? = actions[chord]

    /** The action [chord] would take over from, if it were given to [action]. */
    fun conflictFor(action: EditorAction, chord: KeyChord): EditorAction? =
        actions[chord]?.takeIf { it != action }

    /** Gives [chord] to [action]. Any action that had this chord loses it and stays unbound. */
    fun rebind(action: EditorAction, chord: KeyChord): KeyBindings {
        val next = overrides.toMutableMap()
        conflictFor(action, chord)?.let { next[it] = null }
        if (DEFAULTS[action] == chord) next.remove(action) else next[action] = chord
        return KeyBindings(next)
    }

    fun unbind(action: EditorAction) = KeyBindings(overrides + (action to null))

    /** Puts one action back on its default chord, taking it from whoever holds it now. */
    fun reset(action: EditorAction): KeyBindings = rebind(action, DEFAULTS.getValue(action))

    override fun equals(other: Any?) = other is KeyBindings && chords == other.chords
    override fun hashCode() = chords.hashCode()
    override fun toString() = "KeyBindings(${overrides.size} changed)"

    companion object {
        /** No reference exists for these, so they follow common desktop habits. */
        val DEFAULTS: Map<EditorAction, KeyChord> = buildMap {
            fun key(action: EditorAction, keyCode: Int, modifiers: Int = 0) = put(action, KeyChord(keyCode, modifiers))
            val ctrl = KeyChord.CTRL
            val shift = KeyChord.SHIFT

            key(EditorAction.UNDO, KeyCodes.Z, ctrl)
            key(EditorAction.REDO, KeyCodes.Y, ctrl)
            key(EditorAction.TOGGLE_FINGER_DRAW, KeyCodes.F)
            key(EditorAction.TOGGLE_VIEW_ONLY, KeyCodes.V)
            key(EditorAction.TOGGLE_TOOLBAR, KeyCodes.TAB)

            key(EditorAction.PEN, KeyCodes.P)
            key(EditorAction.PENCIL, KeyCodes.N)
            key(EditorAction.HIGHLIGHTER, KeyCodes.H)
            key(EditorAction.LASSO, KeyCodes.L)
            key(EditorAction.ERASER, KeyCodes.E)
            key(EditorAction.SHAPE, KeyCodes.S)
            key(EditorAction.IMAGE, KeyCodes.I)
            key(EditorAction.CAMERA, KeyCodes.C)
            key(EditorAction.STICKY_NOTE, KeyCodes.K)
            key(EditorAction.TABLE, KeyCodes.T)
            key(EditorAction.LOUPE, KeyCodes.O)
            key(EditorAction.RECORDER, KeyCodes.R)
            key(EditorAction.RULER, KeyCodes.U)
            key(EditorAction.LASER_POINTER, KeyCodes.W)

            key(EditorAction.OPEN_TOOL_PANEL, KeyCodes.Q)
            key(EditorAction.COLOR_1, KeyCodes.KEY_1)
            key(EditorAction.COLOR_2, KeyCodes.KEY_2)
            key(EditorAction.COLOR_3, KeyCodes.KEY_3)
            key(EditorAction.COLOR_4, KeyCodes.KEY_4)
            key(EditorAction.COLOR_5, KeyCodes.KEY_5)
            key(EditorAction.WIDTH_1, KeyCodes.KEY_1, shift)
            key(EditorAction.WIDTH_2, KeyCodes.KEY_2, shift)
            key(EditorAction.WIDTH_3, KeyCodes.KEY_3, shift)
            key(EditorAction.TOGGLE_ERASE_MODE, KeyCodes.E, shift)
            key(EditorAction.CLEAR_PAGE, KeyCodes.DEL, ctrl)

            key(EditorAction.COPY, KeyCodes.C, ctrl)
            key(EditorAction.CUT, KeyCodes.X, ctrl)
            key(EditorAction.PASTE, KeyCodes.V, ctrl)
            key(EditorAction.DELETE_SELECTION, KeyCodes.FORWARD_DEL)

            key(EditorAction.NEXT_PAGE, KeyCodes.PAGE_DOWN)
            key(EditorAction.PREVIOUS_PAGE, KeyCodes.PAGE_UP)
            key(EditorAction.FIRST_PAGE, KeyCodes.MOVE_HOME)
            key(EditorAction.LAST_PAGE, KeyCodes.MOVE_END)
            key(EditorAction.ADD_PAGE, KeyCodes.N, ctrl)
            key(EditorAction.PAGE_GRID, KeyCodes.G)
            key(EditorAction.PAGE_SWITCHER, KeyCodes.G, shift)
            key(EditorAction.ZOOM_IN, KeyCodes.EQUALS, ctrl)
            key(EditorAction.ZOOM_OUT, KeyCodes.MINUS, ctrl)
            key(EditorAction.ZOOM_REAL_SIZE, KeyCodes.KEY_0, ctrl)

            key(EditorAction.SEARCH, KeyCodes.F, ctrl)
            key(EditorAction.EXPORT, KeyCodes.E, ctrl)
            key(EditorAction.SETTINGS, KeyCodes.COMMA, ctrl)
        }

        val Default = KeyBindings(emptyMap())

        /** Rebuilds a saved set. */
        internal fun ofOverrides(overrides: Map<EditorAction, KeyChord?>) = KeyBindings(overrides)
    }
}
