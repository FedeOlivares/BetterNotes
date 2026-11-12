package app.betternotes.ui.theme

/**
 * The colors the canvas and its overlays paint with, as ARGB ints for `android.graphics.Paint`. They are the same in
 * both themes unless a pair says otherwise. The Compose screens take theirs from [AppColors], [EditorColors],
 * [SettingsColors] and [UiColors].
 */
internal object CanvasColors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** Behind a PDF page and a thumbnail, where the page has no paper color of its own. */
    const val PAPER = WHITE

    /** Selection: the lasso's marching ants, the loupe's box and the box a note or sticker is dragged out in. */
    const val SELECTION = 0xFF4A9BF5.toInt()
    const val SELECTION_FILL = 0x224A9BF5
    const val SELECTION_HANDLE = WHITE
    const val SNAP_GUIDE = 0xFFE5408F.toInt()
    const val SHAPE_PARAMETER_HANDLE = 0xFFF28B30.toInt()

    /** The round buttons next to a selection. */
    const val ACTION_GLYPH = WHITE
    const val ACTION_DELETE = 0xFFE5483F.toInt()
    const val ACTION_COPY = 0xFF3F7FE5.toInt()
    const val ACTION_MORE = 0xFF5B6270.toInt()
    const val ACTION_STICKER = 0xFF8E63D9.toInt()

    /** The outline around the planner part being picked for recoloring. */
    const val PLANNER_PICK_OUTLINE = 0xFF3D6AF2.toInt()

    const val ANGLE_GUIDE = 0xFFE53935.toInt()
    const val PAGE_GRID = 0xFF8E9096.toInt()

    const val LASER = 0xFFFF1A1A.toInt()
    const val LASER_CORE = WHITE

    /** Measured from the screenshot, where the band lies over both the cream page and a blue note. */
    const val RULER_BODY = 0x8F5B5B5B.toInt()
    const val RULER_MARK = 0xCCFFFFFF.toInt()

    const val LOUPE_EDGE = 0x33000000
    const val LOUPE_BACKDROP = 0xFFE3E4E8.toInt()

    const val SEARCH_HIGHLIGHT = 0xFFFFE680.toInt()
    const val SEARCH_HIGHLIGHT_CURRENT = 0xFFFFA726.toInt()

    /** Tape and sticky notes. */
    const val ITEM_SHADOW = BLACK
    const val TAPE_PATTERN = WHITE
    const val NOTE_FOLD_SHADE = 0x40FFFFFF

    const val VIDEO_BACK = 0xFF25282D.toInt()
    const val VIDEO_BUTTON = 0xB3000000.toInt()
    const val VIDEO_TRIANGLE = WHITE

    const val TABLE_HEADER = 0x55808080
    const val TABLE_CELL_TEXT = 0xFF1E1F24.toInt()
    const val IMAGE_PLACEHOLDER = 0x22000000

    /** A recording's bar on the page. */
    const val AUDIO_ACCENT = 0xFF3B6FF5.toInt()
    const val AUDIO_GLYPH = WHITE
    const val AUDIO_FILL_LIGHT = 0xFFF4F5F8.toInt()
    const val AUDIO_FILL_DARK = 0xFF2B2E36.toInt()
    const val AUDIO_EDGE_LIGHT = 0xFFC9CDD6.toInt()
    const val AUDIO_EDGE_DARK = 0xFF4D525E.toInt()
    const val AUDIO_ARROW_LIGHT = 0xFF6B7280.toInt()
    const val AUDIO_ARROW_DARK = 0xFFB4BAC6.toInt()
    const val AUDIO_WAVE_IDLE_LIGHT = 0xFFB6BBC7.toInt()
    const val AUDIO_WAVE_IDLE_DARK = 0xFF6B7180.toInt()

    /** The bar between two documents in split screen, and its grip. */
    const val SPLIT_BAR = 0xFF1C1E22.toInt()
    const val SPLIT_HANDLE = 0xFF8A8F98.toInt()
}
