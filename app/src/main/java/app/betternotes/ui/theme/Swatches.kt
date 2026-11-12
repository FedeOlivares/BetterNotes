package app.betternotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colors the owner picks from, and the ones a tool starts with, as ARGB ints. They end up in notes and settings,
 * so changing one changes what new content looks like, not only the screens.
 */
internal object Swatches {
    /** Offered after a tool's own colors in the "More colors" list. */
    val MoreFavorites = listOf(
        0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(),
        0xFFB71C1C.toInt(), 0xFFE53935.toInt(), 0xFFFF7043.toInt(), 0xFFFFA000.toInt(),
        0xFFFDD835.toInt(), 0xFFC0CA33.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(),
        0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF3949AB.toInt(), 0xFF8E24AA.toInt(),
        0xFFD81B60.toInt(), 0xFF6D4C41.toInt(),
    )

    val Laser = intArrayOf(
        0xFF000000.toInt(), 0xFFCC0000.toInt(), 0xFF000099.toInt(), 0xFF00CCFF.toInt(),
        0xFF00CC00.toInt(), 0xFF009900.toInt(), 0xFFFFCC00.toInt(), 0xFF8B5A2B.toInt(),
    )

    /** The ready-made colors for the toolbar: white, dark and a few pastels. */
    val Toolbar = listOf(
        0xFFFFFFFF.toInt(),
        0xFF25282D.toInt(),
        0xFFFFD9E0.toInt(),
        0xFFFFE7C7.toInt(),
        0xFFFFF6BF.toInt(),
        0xFFD6F2D3.toInt(),
        0xFFD0EAFB.toInt(),
        0xFFE3D9F7.toInt(),
    )

    /** The bullet colors a list item can take. */
    /** The colors of text offered in the text row: black (the text's usual color), blue and white. */
    val TextRow = listOf(0xFF1E1F24.toInt(), 0xFF2F5BEA.toInt(), 0xFFFFFFFF.toInt())

    val ListDots = listOf(
        0xFF1E1F24.toInt(), 0xFFD10101.toInt(), 0xFFF28C28.toInt(), 0xFFF5C400.toInt(),
        0xFF2E9E4F.toInt(), 0xFF04CEFF.toInt(), 0xFF2F5BEA.toInt(), 0xFF8E44AD.toInt(),
    )

    /** A pattern fill's first color, and the black and white offered before the bucket's own colors. */
    const val PATTERN_DEFAULT = 0xFF000000.toInt()
    val PatternBase = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())

    /** The fill bucket's first color and its starting list. */
    const val FILL_DEFAULT = 0xFFFFD54F.toInt()
    val Fill = listOf(
        0xFFE57373.toInt(),
        0xFFFFB74D.toInt(),
        0xFFFFD54F.toInt(),
        0xFF81C784.toInt(),
        0xFF64B5F6.toInt(),
        0xFFBA68C8.toInt(),
        0xFF90A4AE.toInt(),
        0xFF25282D.toInt(),
    )

    /** What a shape, table or the toolbar shows as its color before one is chosen. */
    const val SHAPE_FILL_DEFAULT = 0xFF5F9FFB.toInt()

    /** A folder's colors, without alpha. The library adds the default (blue) folder in front of them. */
    val Folder = listOf(0xE57373, 0xFFB74D, 0xFFD54F, 0x81C784, 0x4DB6AC, 0x64B5F6, 0xBA68C8, 0xF06292, 0x90A4AE, 0x3C4048)

    /** The colors on offer for a cover: neutrals, then each hue from light to dark. */
    val Cover = listOf(
        0xFFFFFFFF, 0xFFF2F2F2, 0xFFBDBDBD, 0xFF757575, 0xFF363A43, 0xFF000000,
        0xFFFDE0DC, 0xFFF28B82, 0xFFE53935, 0xFFB71C1C, 0xFF7F0000, 0xFFF8BBD0,
        0xFFFFE0B2, 0xFFFFB74D, 0xFFF57C00, 0xFFE65100, 0xFFBF360C, 0xFFD7B899,
        0xFFFFF9C4, 0xFFFFF176, 0xFFFDD835, 0xFFE3C849, 0xFFB28704, 0xFFC4B497,
        0xFFDCEDC8, 0xFFAED581, 0xFF7BA76B, 0xFF388E3C, 0xFF1B5E20, 0xFF8EC4B5,
        0xFFB2EBF2, 0xFF4DD0E1, 0xFF00ACC1, 0xFF00838F, 0xFF5A9EA6, 0xFFA3C9F0,
        0xFFBBDEFB, 0xFF64B5F6, 0xFF1E88E5, 0xFF104A87, 0xFF0D2C54, 0xFF27324F,
        0xFFE1BEE7, 0xFFBA68C8, 0xFF8E24AA, 0xFF4A148C, 0xFFD1C4E9, 0xFF7E57C2,
    ).map { it.toInt() }

    /** What the custom layout builder draws lines, boxes, tables and ruled areas in, and its highlighter and sticky note. */
    const val LAYOUT_ELEMENT = 0xFF55585F.toInt()
    const val LAYOUT_HIGHLIGHTER = 0x66FFEB3B
    const val LAYOUT_STICKY = 0xFFFFE680.toInt()

    /** The brush samples in the brush list are drawn in this, to show on the dark panel. */
    const val BRUSH_SAMPLE = 0xFFE6E7EA.toInt()

    /** The color wheel's hues, all the way round. */
    val Hues = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000),
    )
}
