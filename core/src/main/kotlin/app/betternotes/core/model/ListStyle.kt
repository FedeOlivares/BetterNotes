package app.betternotes.core.model

/** How the paragraphs of a text box are marked when it is a list. */
enum class ListKind {
    /** - */
    DASH,

    /** * */
    STAR,

    /** • */
    BULLET,

    /** 1. 2. 3. */
    NUMBER_DOT,

    /** 1) 2) 3) */
    NUMBER_PAREN,

    /** A) B) C) */
    UPPER_PAREN,

    /** a) b) c) */
    LOWER_PAREN,

    /** i) ii) iii) */
    ROMAN_PAREN,

    /** → */
    ARROW,

    /** A round dot of its own color, size and opacity. */
    DOT,
}

/**
 * A text box that is a list: every paragraph (every line break starts one) gets a marker in a margin of its own, and a long paragraph
 * wraps under its text, not under its marker. [dotColor], [dotSize] and [dotOpacity] are for [ListKind.DOT] only.
 */
data class ListStyle(
    val kind: ListKind = ListKind.BULLET,
    /** The dot's color, whose alpha is ignored: see [dotOpacity]. */
    val dotColor: Int = DEFAULT_DOT_COLOR,
    /** The dot's width as a share of the text size. */
    val dotSize: Float = DEFAULT_DOT_SIZE,
    val dotOpacity: Float = 1f,
) {
    /** What the dot is painted with. */
    val dotArgb: Int
        get() = ((dotOpacity.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (dotColor and 0xFFFFFF)

    /** The same, with a dot size and opacity inside their limits. */
    fun clamped() = copy(dotSize = dotSize.coerceIn(MIN_DOT_SIZE, MAX_DOT_SIZE), dotOpacity = dotOpacity.coerceIn(0f, 1f))

    companion object {
        const val DEFAULT_DOT_COLOR = 0xFF1E1F24.toInt()
        const val DEFAULT_DOT_SIZE = 0.4f
        const val MIN_DOT_SIZE = 0.15f
        const val MAX_DOT_SIZE = 1f
    }
}

/** The text of list markers. */
object ListMarkers {
    /** The marker of paragraph [index] (counted from 0), or null for a kind that is drawn, not written. */
    fun label(kind: ListKind, index: Int): String? = when (kind) {
        ListKind.DASH -> "-"
        ListKind.STAR -> "*"
        ListKind.BULLET -> "•"
        ListKind.NUMBER_DOT -> "${index + 1}."
        ListKind.NUMBER_PAREN -> "${index + 1})"
        ListKind.UPPER_PAREN -> letters(index) + ")"
        ListKind.LOWER_PAREN -> letters(index).lowercase() + ")"
        ListKind.ROMAN_PAREN -> roman(index + 1).lowercase() + ")"
        ListKind.ARROW -> "→"
        ListKind.DOT -> null
    }

    /** A, B, ... Z, AA, AB, ... as a spreadsheet counts its columns. */
    fun letters(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            n--
            out.append('A' + n % 26)
            n /= 26
        }
        return out.reverse().toString()
    }

    /** Upper-case Roman numerals; a number past 3999 or below 1 is written in digits. */
    fun roman(number: Int): String {
        if (number !in 1..3999) return number.toString()
        var n = number
        val out = StringBuilder()
        for ((value, symbol) in ROMAN) while (n >= value) {
            out.append(symbol)
            n -= value
        }
        return out.toString()
    }

    /** The number of paragraphs in [text]: a text always has one, even when empty. */
    fun paragraphs(text: CharSequence): Int = 1 + text.count { it == '\n' }

    private val ROMAN = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
}
