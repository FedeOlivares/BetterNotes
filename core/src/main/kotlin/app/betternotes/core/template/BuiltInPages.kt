package app.betternotes.core.template

import app.betternotes.core.geometry.PageSize

/**
 * The ink of the built-in pages that have words on them. It is picked from the paper, so the page reads on
 * light paper and on dark paper alike.
 */
internal class BuiltInInk(paper: Int) {
    private val dark = luminance(paper) < DARK_PAPER_LUMINANCE

    val accent = if (dark) 0xFF8FA0F0.toInt() else 0xFF5B6EC7.toInt()
    val rule = if (dark) 0xFF6E6E6E.toInt() else 0xFFC4C4C4.toInt()
    val text = if (dark) 0xFFE8E8E8.toInt() else 0xFF2B2B2B.toInt()
    val faint = if (dark) 0xFF8C8C8C.toInt() else 0xFFB3B1A6.toInt()
    val sunday = if (dark) 0xFFFF6B6B.toInt() else 0xFFE0242B.toInt()

    /** Fills are see-through, so they work on any paper color. */
    val tint = withAlpha(accent, TINT_ALPHA)
    val strongTint = withAlpha(accent, STRONG_TINT_ALPHA)
    val shade = withAlpha(text, SHADE_ALPHA)

    private companion object {
        const val DARK_PAPER_LUMINANCE = 0.5f
        const val TINT_ALPHA = 0x16
        const val STRONG_TINT_ALPHA = 0x38
        const val SHADE_ALPHA = 0x12

        fun luminance(argb: Int) =
            (0.299f * (argb shr 16 and 0xFF) + 0.587f * (argb shr 8 and 0xFF) + 0.114f * (argb and 0xFF)) / 255f

        fun withAlpha(argb: Int, alpha: Int) = (alpha shl 24) or (argb and 0xFFFFFF)
    }
}

/**
 * Draws in the coordinates of the reference image: the paper is [width] by [height] reference pixels with its top-left
 * at ([left], [top]), and is stretched to whatever page it lands on. Text is placed by the center of its line.
 */
internal class Layer(
    page: PageSize,
    private val ink: BuiltInInk,
    private val sink: TemplateSink,
    private val left: Float,
    private val top: Float,
    width: Float,
    height: Float,
    /** Told of every tappable area, in page points, as the page is drawn. */
    private val onLink: app.betternotes.core.planner.LinkOut? = null,
) {
    private val sx = page.widthPt / width
    private val sy = page.heightPt / height
    private val spanish = sink.language == "es"

    private fun x(px: Float) = (px - left) * sx
    private fun y(py: Float) = (py - top) * sy

    fun pick(english: String, spanish: String) = if (this.spanish) spanish else english

    /** Marks a box as a tap target for [action]. It draws nothing. */
    fun link(x0: Float, y0: Float, x1: Float, y1: Float, action: app.betternotes.core.planner.PlannerAction) =
        onLink?.link(x(x0), y(y0), x(x1), y(y1), action)

    /** Text turned by [degrees] clockwise about its center point. */
    fun turnedText(text: String, cx: Float, cy: Float, size: Float, degrees: Float, color: Int = ink.text, style: TextStyle = TextStyle.LABEL) =
        sink.turnedText(text, x(cx), y(cy), size * sx, degrees, color, style)

    fun hline(x0: Float, x1: Float, y: Float, color: Int, weight: Float = RULE_PX) = sink.line(x(x0), y(y), x(x1), y(y), weight * sx, color)

    fun vline(x: Float, y0: Float, y1: Float, color: Int, weight: Float = RULE_PX) = sink.line(x(x), y(y0), x(x), y(y1), weight * sx, color)

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, weight: Float = RULE_PX) =
        sink.line(x(x0), y(y0), x(x1), y(y1), weight * sx, color)

    fun dot(cx: Float, cy: Float, radius: Float, color: Int) = sink.dot(x(cx), y(cy), radius * sx, color)

    fun fill(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) = sink.fill(x(x0), y(y0), (x1 - x0) * sx, (y1 - y0) * sy, color)

    fun box(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, weight: Float = RULE_PX) {
        hline(x0, x1, y0, color, weight)
        hline(x0, x1, y1, color, weight)
        vline(x0, y0, y1, color, weight)
        vline(x1, y0, y1, color, weight)
    }

    fun text(
        text: String,
        x: Float,
        centerY: Float,
        size: Float,
        color: Int = ink.text,
        style: TextStyle = TextStyle.LABEL,
        align: TextAlign = TextAlign.START,
    ) = sink.text(text, x(x), y(centerY + size * CAP_CENTER), size * sx, color, style, align)

    private companion object {
        const val RULE_PX = 2f

        /** How far below a line's center its baseline is, as a share of the font size. */
        const val CAP_CENTER = 0.36f
    }
}

private fun two(n: Int) = n.toString().padStart(2, '0')

/** Semester overview, from the owner's reference. Reference paper: 1357 x 1753. */
internal object SemesterPage {
    private const val ROWS = 15
    private const val HOURS = 16
    private const val DAYS = 7
    private const val FIRST_HOUR = 7

    fun draw(page: PageSize, ink: BuiltInInk, sink: TemplateSink) {
        val c = Layer(page, ink, sink, 61f, 79f, 1357f, 1753f)
        c.text(c.pick("Semester Overview", "Resumen del semestre"), 142f, 175f, 44f, style = TextStyle.TITLE)
        c.text(c.pick("Start Date:", "Fecha de inicio:"), 758f, 177f, 20f)
        c.text(c.pick("End Date:", "Fecha de fin:"), 1049f, 177f, 20f)
        c.hline(758f, 1337f, 199f, ink.accent)

        c.text(c.pick("WEEKLY BREAKDOWN", "DESGLOSE SEMANAL"), 142f, 265f, 22f, style = TextStyle.HEADING)
        numberedRows(c, ink, 142f, 502f, 298f, 1758f, ROWS, 345f - 298f)

        c.text(c.pick("MY SCHEDULE", "MI HORARIO"), 553f, 265f, 22f, style = TextStyle.HEADING)
        schedule(c, ink)

        c.text(c.pick("GOALS", "METAS"), 553f, 1337f, 22f, style = TextStyle.HEADING)
        numberedRows(c, ink, 553f, 921f, 1363f, 1758f, 3, 1432f - 1363f)

        c.text(c.pick("NOTES", "NOTAS"), 978f, 1337f, 22f, style = TextStyle.HEADING)
        c.box(977f, 1363f, 1337f, 1758f, ink.accent)
    }

    /** A blue rule on top, then [rows] numbered rows each closed by a gray rule, the last by a blue one. */
    private fun numberedRows(c: Layer, ink: BuiltInInk, x0: Float, x1: Float, top: Float, bottom: Float, rows: Int, numberY: Float) {
        c.hline(x0, x1, top, ink.accent)
        val step = (bottom - top) / rows
        for (i in 0 until rows) {
            c.text(two(i + 1), x0, top + step * i + numberY, 20f)
            c.hline(x0, x1, top + step * (i + 1), if (i == rows - 1) ink.accent else ink.rule)
        }
    }

    private fun schedule(c: Layer, ink: BuiltInInk) {
        val left = 553f
        val gutter = 607f
        val right = 1337f
        val top = 298f
        val headBottom = 341f
        val bottom = 1280f
        c.fill(left, top, right, headBottom, ink.shade)
        c.fill(left, headBottom, gutter, bottom, ink.shade)
        val hourStep = (bottom - headBottom) / HOURS
        for (i in 0 until HOURS) {
            val rowTop = headBottom + hourStep * i
            c.text(two((FIRST_HOUR + i - 1) % 12 + 1), (left + gutter) / 2f, rowTop + hourStep / 2f, 18f, align = TextAlign.CENTER)
            c.hline(left, right, rowTop, ink.rule)
        }
        val dayStep = (right - gutter) / DAYS
        val days = c.pick("Mon,Tue,Wed,Thu,Fri,Sat,Sun", "Lun,Mar,Mié,Jue,Vie,Sáb,Dom").split(',')
        for (d in 0 until DAYS) {
            c.vline(gutter + dayStep * d, top, bottom, ink.rule)
            c.text(days[d], gutter + dayStep * (d + 0.5f), (top + headBottom) / 2f, 18f, align = TextAlign.CENTER)
        }
        c.vline(right, top, bottom, ink.rule)
        c.hline(left, right, top, ink.accent)
        c.hline(left, right, bottom, ink.accent)
    }
}

/** Mind map, from the owner's reference. Reference paper: 1320 x 1705. */
internal object MindMapPage {
    private const val BOX_WIDTH = 240f
    private const val BOX_HEIGHT = 227f
    private const val BOX_HEAD = 54f
    private const val SUB_WIDTH = 262f
    private const val SUB_HEIGHT = 106f

    /** Top-left corners of the twelve boxes around the map. */
    private val boxes = listOf(
        171f to 273f, 629f to 273f, 1098f to 273f,
        171f to 580f, 171f to 878f, 171f to 1190f,
        1096f to 572f, 1096f to 878f, 1096f to 1190f,
        171f to 1504f, 629f to 1504f, 1096f to 1504f,
    )
    private val subTopics = listOf(618f to 591f, 430f to 737f, 815f to 1136f, 618f to 1308f)

    /** Connector lines as x0, y0, x1, y1. */
    private val connectors = floatArrayOf(
        // Top row down to the first sub-topic.
        294f, 500f, 294f, 550f, 294f, 550f, 1223f, 550f, 1223f, 500f, 1223f, 550f, 749f, 500f, 749f, 591f,
        // First sub-topic to the main topic.
        752f, 697f, 752f, 907f,
        // The left sub-topic, its boxes and the main topic.
        413f, 659f, 482f, 659f, 482f, 659f, 482f, 737f, 482f, 843f, 482f, 1351f, 413f, 978f, 482f, 978f,
        413f, 1351f, 482f, 1351f, 590f, 843f, 590f, 907f,
        // The right sub-topic, its boxes and the main topic.
        1029f, 659f, 1096f, 659f, 1029f, 659f, 1029f, 1136f, 1029f, 978f, 1096f, 978f, 1029f, 1242f, 1029f, 1351f,
        1029f, 1351f, 1096f, 1351f, 908f, 1042f, 908f, 1136f,
        // Main topic down to the last sub-topic, and its row of boxes.
        749f, 1042f, 749f, 1308f, 749f, 1414f, 749f, 1504f, 281f, 1459f, 1223f, 1459f, 281f, 1459f, 281f, 1504f,
        1223f, 1459f, 1223f, 1504f,
    )

    fun draw(page: PageSize, ink: BuiltInInk, sink: TemplateSink) {
        val c = Layer(page, ink, sink, 94f, 101f, 1320f, 1705f)
        c.text(c.pick("Mind Map", "Mapa mental"), 172f, 199f, 46f, style = TextStyle.TITLE)
        c.text(c.pick("Assignment:", "Tarea:"), 772f, 196f, 20f)
        c.hline(772f, 1337f, 217f, ink.accent)

        for (i in connectors.indices step 4) c.line(connectors[i], connectors[i + 1], connectors[i + 2], connectors[i + 3], ink.accent)
        for ((x, y) in boxes) {
            c.fill(x, y, x + BOX_WIDTH, y + BOX_HEAD, ink.tint)
            c.box(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, ink.rule)
            c.hline(x, x + BOX_WIDTH, y + BOX_HEAD, ink.rule)
        }
        val sub = c.pick("Sub-topic", "Subtema")
        for ((x, y) in subTopics) {
            c.fill(x, y, x + SUB_WIDTH, y + SUB_HEIGHT, ink.tint)
            c.box(x, y, x + SUB_WIDTH, y + SUB_HEIGHT, ink.accent)
            c.text(sub, x + 14f, y + 23f, 19f)
        }
        c.fill(590f, 907f, 908f, 1042f, ink.strongTint)
        c.box(590f, 907f, 908f, 1042f, ink.accent)
        c.text(c.pick("MAIN TOPIC", "TEMA PRINCIPAL"), 749f, 938f, 21f, style = TextStyle.HEADING, align = TextAlign.CENTER)
    }
}

/** Week planner, from the owner's reference: seven day rows, each with a ruler of 24 hours. Reference paper: 1394 x 1800. */
internal object WeekPlannerPage {
    private const val DAYS = 7
    private const val HOURS = 24
    private const val TOP = 179f
    private const val BOTTOM = 1827f
    private const val LEFT = 87f
    private const val RIGHT = 1378f
    private const val DIVIDER_X = 258f
    private const val FIRST_HOUR_X = 277f
    private const val HOUR_STEP = 46.26f

    fun draw(page: PageSize, ink: BuiltInInk, sink: TemplateSink) {
        val c = Layer(page, ink, sink, 36f, 40f, 1394f, 1800f)
        val names = c.pick("MON,TUE,WED,THR,FRI,SAT,SUN", "LUN,MAR,MIÉ,JUE,VIE,SÁB,DOM").split(',')
        val step = (BOTTOM - TOP) / DAYS
        c.hline(LEFT, RIGHT, TOP, ink.text, 3f)
        c.hline(LEFT, RIGHT, BOTTOM, ink.text, 3f)
        for (d in 0 until DAYS) {
            val top = TOP + step * d
            if (d > 0) c.hline(LEFT, RIGHT, top, ink.rule)
            c.text(names[d], 109f, top + 32f, 33f, if (d == DAYS - 1) ink.sunday else ink.text, TextStyle.HEADING)
            c.line(142f, top + 154f, 190f, top + 106f, ink.text, 1.5f)
            c.vline(DIVIDER_X, top + 10f, top + step - 8f, ink.rule)
            for (h in 0 until HOURS) {
                val x = FIRST_HOUR_X + HOUR_STEP * h
                c.text((if (h % 12 == 0) 12 else h % 12).toString(), x, top + 31f, 17f, ink.faint, align = TextAlign.CENTER)
                c.vline(x, top + 50f, top + 62f, ink.rule, 1.5f)
                c.vline(x + HOUR_STEP / 2f, top + 53f, top + 59f, ink.rule, 1.5f)
            }
        }
    }
}
