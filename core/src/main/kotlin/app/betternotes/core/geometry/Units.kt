package app.betternotes.core.geometry

/**
 * World coordinates are PostScript points (1/72 inch), the native unit of PDF pages,
 * so PDF and note content share one coordinate space with no conversion.
 */
enum class LengthUnit(val ptPerUnit: Float) {
    PT(1f),
    INCH(72f),
    CM(72f / 2.54f),
    MM(72f / 25.4f),

    /** CSS reference pixels (96 per inch), so a custom layout is the same size on any device. */
    PX(72f / 96f);

    fun toPt(value: Float) = value * ptPerUnit
    fun fromPt(pt: Float) = pt / ptPerUnit
}

data class PageSize(val widthPt: Float, val heightPt: Float) {
    companion object {
        val A4 = PageSize(595.276f, 841.89f)
        val A5 = PageSize(419.528f, 595.276f)
        val Letter = PageSize(612f, 792f)
        val Legal = PageSize(612f, 1008f)

        fun of(width: Float, height: Float, unit: LengthUnit) =
            PageSize(unit.toPt(width), unit.toPt(height))
    }
}
