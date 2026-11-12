package app.betternotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors of the Compose screens that are the same in both themes: the blue buttons, scrims, status dots and the like.
 * Everything that changes with the theme is in [AppColors], [EditorColors] or [SettingsColors].
 */
internal object UiColors {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    /** Text and icons on [ActionBlue], [SystemBlue], [ScanButton] and the other filled buttons. */
    val OnFilled = White

    /** Icon vectors are drawn in this and tinted where they are shown, so the value never appears. */
    val IconMask = Black

    /** The round add-page and add-cover buttons. */
    val ActionBlue = Color(0xFF2F6FE0)

    /** The selection blue of the new-notebook screen and the planner menus. */
    val SystemBlue = Color(0xFF0A84FF)

    /** A filled tile or row that is chosen with [SystemBlue] at this strength. */
    const val SYSTEM_BLUE_TINT_ALPHA = 0.16f

    val Scrim = Color(0x66000000)
    val ScrimStrong = Color(0x99000000)
    val ScrimLight = Color(0x54000000)
    val ScanBackdrop = Color(0xF2000000)

    /** The red disc with a white bar that removes a color, and the red badge that removes a sticker. */
    val RemoveRed = Color(0xFFE53935)
    val DeleteBadge = Color(0xFFE5483F)

    /** The recorder: the play button, the stop square, the recording disc and the "microphone on" dot. */
    val RecorderPlay = Color(0xFF3B6FF5)
    val RecorderStop = Color(0xFF25282D)
    val RecorderRecording = Color(0xFFFFB0A6)
    val MicrophoneOn = Color(0xFF1DD05D)

    /** The document scanner's buttons and the outline and corners of its crop. */
    val ScanButton = Color(0xFF1B5E8C)
    val ScanOutline = Color(0xFF3FA9F5)

    /** The eraser's "clear page" action and the checkerboard behind a see-through color. */
    val ClearPage = Color(0xFFF2A49B)
    val CheckerLight = Color(0xFFD9D9DC)
    val CheckerDark = Color(0xFF8D8D93)

    /** The header row of a table's preview. */
    val TableHeaderPreview = Color(0xFF55565B)

    /** Behind a sticker in the sticker list, and behind a video before it plays. */
    val StickerTile = Color(0xFFF4F1E8)
    val VideoBack = Black

    /** A thin line around a preview or swatch that might otherwise melt into its background. */
    val HairlineDark = Color(0x22000000)
    val SwatchEdge = Color(0x33000000)

    /** The swatch for a folder left in the default color, which the library draws in its theme's folder blue. */
    val DefaultFolder = Color(0xFF719EF9)

    /** Text on a folder light enough to need dark ink. */
    val FolderInkDark = Color(0xFF1F2328)
    val FolderInkLight = White

    /** The developer performance readout. */
    val PerfText = Color(0xFFB8F0C0)
    val PerfBack = Color(0xE01C1E22)
}
