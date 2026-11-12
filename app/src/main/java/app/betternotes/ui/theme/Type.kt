package app.betternotes.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Every text size the screens use, smallest first. Like [Radius], the steps come from the reference screenshots, so
 * a redesign can map them onto a shorter scale. The system's font scale still applies to all of them.
 */
internal object TextSize {
    val S10 = 10.sp
    val S11 = 11.sp
    val S12 = 12.sp
    val S13 = 13.sp
    val S14 = 14.sp
    val S15 = 15.sp
    val S16 = 16.sp
    val S16_5 = 16.5.sp
    val S17 = 17.sp
    val S17_5 = 17.5.sp
    val S18 = 18.sp
    val S19 = 19.sp
    val S19_5 = 19.5.sp
    val S20 = 20.sp
    val S21 = 21.sp
    val S22 = 22.sp
    val S23 = 23.sp
    val S24 = 24.sp
    val S24_5 = 24.5.sp
    val S26 = 26.sp
    val S27 = 27.sp
    val S28 = 28.sp
    val S30 = 30.sp
    val S34 = 34.sp
    val S41 = 41.sp
    val S44 = 44.sp
    val S56 = 56.sp
}

/** Letter spacing for the small capitals-style labels over panel sections. */
internal object LetterSpacing {
    val Label = 0.4.sp
    val Hint = 0.5.sp

    /** Material's body text is spaced wider than the reference's. */
    val None = 0.sp
}

/** The app's typefaces. Text in notes uses the fonts in FontLibrary instead. */
internal object AppFonts {
    /** The library greeting, a typed note's title and the sources column's heading. */
    val Display: FontFamily = FontFamily.Serif

    /** The developer performance readout. */
    val Mono: FontFamily = FontFamily.Monospace
}
