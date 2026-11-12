package app.betternotes.core.color

/** Colors written as hex codes, as a person types them: "#RRGGBB" or "RRGGBB", or the short "#RGB" and "RGB". */
object HexColor {
    /** The longest a code can be typed: "#" and six digits. */
    const val MAX_LENGTH = 7

    private const val OPAQUE = 0xFF000000.toInt()

    /** The opaque color [text] stands for, as ARGB, or null when it is not a whole code. Surrounding spaces are ignored. */
    fun parse(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.any { Character.digit(it, 16) < 0 }) return null
        return when (digits.length) {
            6 -> digits.toInt(16) or OPAQUE
            3 -> {
                var rgb = 0
                for (c in digits) rgb = (rgb shl 8) or (Character.digit(c, 16) * 0x11)
                rgb or OPAQUE
            }
            else -> null
        }
    }

    /** [argb] without its alpha, as "#RRGGBB" in capitals. */
    fun format(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** What a person has typed, kept to what a code can hold: a leading "#" and hex digits, no more than [MAX_LENGTH] characters. */
    fun filter(typed: String): String {
        val kept = typed.filterIndexed { i, c -> (i == 0 && c == '#') || Character.digit(c, 16) >= 0 }
        return kept.take(MAX_LENGTH).uppercase()
    }
}
