package app.betternotes.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The editor's icons, drawn for this app as line art on a 24-unit grid. None come from Notewise. */
internal object EditorIcons {
    val Back by lazy { line("back", "M20 12H4.5 M11 5l-7 7 7 7") }
    val Sidebar by lazy { line("sidebar", rrect(3f, 5f, 18f, 14f, 2.5f) + "M9.5 5v14") }
    val PageGrid by lazy {
        line(
            "page-grid",
            rrect(3.5f, 3.5f, 7f, 7f, 1.5f) + rrect(13.5f, 3.5f, 7f, 7f, 1.5f) +
                rrect(3.5f, 13.5f, 7f, 7f, 1.5f) + rrect(13.5f, 13.5f, 7f, 7f, 1.5f),
        )
    }
    val PageStack by lazy { line("page-stack", rrect(4f, 8f, 16f, 12.5f, 2.5f) + "M6 5.5h12 M8.5 3h7") }
    val ChevronsUpDown by lazy { line("chevrons", "M7 9.5l5-5 5 5 M7 14.5l5 5 5-5") }
    val ChevronUp by lazy { line("chevron-up", "M7 14.5l5-5 5 5") }
    val ChevronDown by lazy { line("chevron-down", "M7 9.5l5 5 5-5") }
    val Undo by lazy { line("undo", "M8.5 5L4.5 9l4 4 M4.5 9h10a5 5 0 0 1 0 10h-3") }
    val Redo by lazy { line("redo", "M15.5 5l4 4-4 4 M19.5 9h-10a5 5 0 0 0 0 10h3") }
    val FingerMode by lazy {
        line(
            "finger-mode",
            "M11 11V6a1.5 1.5 0 0 1 3 0v6l4.2.9a2 2 0 0 1 1.6 2.3l-.6 3.4a2.5 2.5 0 0 1-2.5 2.1h-3.6" +
                "a3 3 0 0 1-2.3-1.1L7 16a1.4 1.4 0 0 1 2.1-1.8L11 16z " +
                "M5 10V3 M3 5l2-2 2 2 M3 8l2 2 2-2",
        )
    }
    val Pen by lazy { line("pen", "M4 20l1.2-4.6L16.6 4a2 2 0 0 1 2.8 0l.6.6a2 2 0 0 1 0 2.8L8.6 18.8z") }
    val Pencil by lazy {
        line("pencil", "M12 11.5L18.2 4a1.8 1.8 0 0 1 2.6 2.6L13.5 13 M12 11.5l1.5 1.5c.4 3-2.4 6.5-9.5 7 1.6-1.6 2-3.2 2.2-4.6.3-2.3 3.2-4.6 5.8-3.9z")
    }
    val Highlighter by lazy {
        line("highlighter", "M5.5 14.5L16 4a2 2 0 0 1 2.8 0l1.2 1.2a2 2 0 0 1 0 2.8L9.5 18.5z M5.5 14.5L4 20l5.5-1.5 M13.5 6.5l4 4")
    }
    val Eraser by lazy {
        line("eraser", "M3.9 15l10.6-10.6a2 2 0 0 1 2.8 0l2.8 2.8a2 2 0 0 1 0 2.8L9.5 20.6a2 2 0 0 1-2.8 0l-2.8-2.8a2 2 0 0 1 0-2.8z M8.5 10.5l5.5 5.5")
    }
    val Lasso by lazy {
        line("lasso", dashedArc() + "M8.5 15.5a2.6 2.6 0 1 1-2.7-2.4c1 0 2.2.5 2.7 2.4zM8.5 15.5c.3 1.9-.6 3.8-2.5 5")
    }
    val Shapes by lazy {
        line("shapes", rrect(3.5f, 8f, 9.5f, 9.5f, 1.5f) + "M12.5 3a5 5 0 0 1 5 5 M13 16.5L17 9.5l4.5 11h-9z")
    }
    /** The tagged strip between the eraser and the shapes. What it does is unconfirmed. */
    val Tape by lazy { line("tape", "M4.2 13.8L13.8 4.2l2 2 1.5-1 1.5 1.5-1 1.5 2 2-9.6 9.6-2-2-1.5 1-1.5-1.5 1-1.5z") }
    val Text by lazy { line("text", rrect(3.5f, 3.5f, 17f, 17f, 2.5f) + "M8 8.5h8 M12 8.5v8 M10.3 16.5h3.4") }
    val Sticker by lazy {
        line("sticker", "M12 3a9 9 0 1 0 9 9c-1 3-3 5-5.5 6.5 M8 14.5c1 2.2 5 3 7.5 0 M9 9.5v.2 M15 9.5v.2 M21 12c-2.5-.5-5 .5-5.5 6.5")
    }
    val AddElement by lazy { line("add-element", rrect(3.5f, 4f, 9f, 7f, 1.5f) + rrect(3.5f, 14f, 17f, 6f, 1.5f) + "M18 4.5v6 M15 7.5h6") }
    val EraseMode by lazy { line("erase-mode", "M9 4.2A9 9 0 1 0 19.8 15 M14.5 3.5l6 6-5 5-6-6z M8 13.5v.2 M5.5 9.5v.2") }
    val Broom by lazy { line("broom", "M20.5 3.5L13 11 M9.5 9l5.5 5.5c-1.5 3-4 5.5-8 6.5l-1-3-1.5 2L3 19c1.5-1 3-3 2-4l-1-.5c2.500-.5 4-2.500 5.500-5.500z") }
    val LassoFreeform by lazy { line("lasso-freeform", dashedCircle() + "M16.5 6.500l1.500 1.500-6 6-2 .5.5-2z") }
    val LassoRectangle by lazy { line("lasso-rectangle", "M3 7V4.500A1.500 1.500 0 0 1 4.500 3H7 M11 3h3 M21 11v3 M21 18v1.500a1.500 1.500 0 0 1-1.500 1.500H17 M13 21h-3 M6 21H4.500A1.500 1.500 0 0 1 3 19.500V18 M3 14v-3 M17.500 5.500l2 2-7 7-2.500.5.500-2.500z") }
    val LassoLocked by lazy { line("lasso-locked", dashedCircle() + "M8.500 11h7v5.500h-7z M10 11V9.500a2 2 0 0 1 4 0V11") }
    val LassoScreenshot by lazy { line("lasso-screenshot", dashedCircle() + "M8 10h1.500l1-1.500h3l1 1.500H16v6H8z M12 11a1.700 1.700 0 1 0 .01 0") }
    val Bold by lazy { line("bold", "M8 5h5a3.500 3.500 0 0 1 0 7H8z M8 12h6a3.500 3.500 0 0 1 0 7H8z", width = 2.4f) }
    val Italic by lazy { line("italic", "M10 5h7 M7 19h7 M14 5l-4 14") }
    val Underline by lazy { line("underline", "M7 4v8a5 5 0 0 0 10 0V4 M6 20h12") }
    val Strike by lazy { line("strike", "M17 7c-1-1.800-3-2.500-5-2.500-2.800 0-5 1.300-5 3.800 0 1.700 1 2.900 3 3.700 M4 12h16 M15.500 15c.5.600.8 1.300.8 2 0 2.300-2 3.500-4.800 3.500-2.300 0-4.200-.8-5.200-2.500") }
    val AlignStart by lazy { line("align-start", "M4 6h16 M4 12h10 M4 18h13") }
    val AlignCenter by lazy { line("align-center", "M4 6h16 M7 12h10 M5.500 18h13") }
    val AlignEnd by lazy { line("align-end", "M4 6h16 M10 12h10 M7 18h13") }
    val AlignTop by lazy { line("align-top", "M5 4h14 M12 20V8 M7.500 12.500L12 8l4.500 4.500") }
    val AlignMiddle by lazy { line("align-middle", "M5 12h14 M12 3v5 M9.500 6L12 8.500 14.500 6 M12 21v-5 M9.500 18l2.500-2.500 2.500 2.500") }
    val AlignBottom by lazy { line("align-bottom", "M5 20h14 M12 4v12 M7.500 11.500L12 16l4.500-4.500") }
    val Search by lazy { line("search", "M10.5 4a6.5 6.5 0 1 0 .01 0 M15.5 15.5L21 21") }
    val Insert by lazy { line("insert", rrect(4f, 4f, 16f, 16f, 2.5f) + "M12 8v8 M8 12h8") }
    val Image by lazy { line("image", rrect(3f, 4.5f, 18f, 15f, 2.5f) + "M3.5 17l5-5 4 4 2.5-2.5 5 5 M15.5 9.5a1.2 1.2 0 1 0 .01 0") }
    val Camera by lazy { line("camera", "M4 7.5h3.5L9 5h6l1.5 2.5H20a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 18V9A1.5 1.5 0 0 1 4 7.5z M12 9.8a3.5 3.5 0 1 0 .01 0") }
    val StickyNote by lazy { line("sticky", "M6 3.5h12A2.5 2.5 0 0 1 20.5 6v8l-6.5 6.5H6A2.5 2.5 0 0 1 3.5 18V6A2.5 2.5 0 0 1 6 3.5z M20.5 14H16a2 2 0 0 0-2 2v4.5") }
    val Table by lazy { line("table", rrect(3f, 5f, 18f, 14f, 2.5f) + "M3 10h18 M3 14.5h18 M12 10v9") }
    val Tray by lazy { line("tray", rrect(3f, 6.5f, 18f, 13.5f, 3f) + "M6 6.5V5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1.5 M8 13c1.5-3 2.5-3 3 0s2 3 3 0 1.5-2 2-1") }
    val StickyAdd by lazy { line("sticky-add", rrect(4f, 4f, 9f, 7f, 1.5f) + rrect(4f, 14f, 16f, 6f, 1.5f) + "M18 4v6 M15 7h6") }
    val Loupe by lazy { line("loupe", rrect(3f, 4f, 17f, 15.5f, 2f) + "M11.5 7.5a3.8 3.8 0 1 0 .01 0 M11.5 9.6v3.4 M9.8 11.3h3.4 M14.3 14l6 6.5") }
    val Ruler by lazy { line("ruler", "M3.7 16L16 3.7a1.5 1.5 0 0 1 2.1 0l2.2 2.2a1.5 1.5 0 0 1 0 2.1L8 20.3a1.5 1.5 0 0 1-2.1 0l-2.2-2.2a1.5 1.5 0 0 1 0-2.1z M8 11.7l2 2 M11 8.7l2 2 M14 5.7l2 2") }
    val Mic by lazy { line("mic", rrect(9f, 3f, 6f, 11f, 3f) + "M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21 M9 21h6") }
    val Laser by lazy { line("laser", "M3.5 20.5L11 13 M15 3v3 M15 12v3 M21 9h-3 M12 9H9 M19.2 4.8l-2 2 M12.8 11.2l-1 1 M19.2 13.2l-2-2 M12.8 6.8l-2-2") }
    val Settings by lazy { line("settings", gear() + "M12 9a3 3 0 1 0 .01 0") }
    val Eye by lazy { line("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z M12 9.2a2.8 2.8 0 1 0 .01 0") }
    val More by lazy { filled("more", "M5.5 10.2a1.8 1.8 0 1 0 .01 0z M12 10.2a1.8 1.8 0 1 0 .01 0z M18.5 10.2a1.8 1.8 0 1 0 .01 0z") }
    val AddColor by lazy { line("add-color", dashedCircle() + "M12 8v8 M8 12h8") }
    val EditColor by lazy { line("edit-color", "M14.5 4.5l3.5 3.5L8.5 17.5H5v-3.5z M5 21h14") }
    val Sliders by lazy { line("sliders", "M3 8h10 M19 8h2 M3 16h2 M11 16h10 M16 5a3 3 0 1 0 .01 0 M8 13a3 3 0 1 0 .01 0") }
    val Close by lazy { line("close", "M6 6l12 12 M18 6L6 18") }
    val Minus by lazy { line("minus", "M5 12h14") }
    val Plus by lazy { line("plus", "M12 5v14 M5 12h14") }
    val Info by lazy { line("info", "M12 3a9 9 0 1 0 .01 0 M12 11v5.5 M12 7.6v.2") }
    val List by lazy { line("list", "M4 5.5h16 M4 10h16 M4 14.5h16 M4 19h16", width = 2.2f) }
    val NaturalNib by lazy { line("natural", "M12.5 20L5 13l8-9 7 6.5-7.5 9.5z M5 13l-1.5 7.5L12.5 20") }
    val BallNib by lazy { line("ball", "M9 15l-2-2a3 3 0 0 1 0-4.2l4.8-4.8a3 3 0 0 1 4.2 0l3 3a3 3 0 0 1 0 4.2L14.2 16a3 3 0 0 1-4.2 0z M9 15l-4.5 5.5 M13 7l4 4") }
    val CalligraphyNib by lazy { line("calligraphy", "M14 3l7 7-4 3-6-6z M11 7l-6 2-2 12 12-2 2-6 M3 21l6.5-6.5 M11 13.5a1.5 1.5 0 1 0 .01 0") }

    private fun line(name: String, path: String, width: Float = STROKE_WIDTH) = ImageVector.Builder(
        name,
        ICON_DP.dp,
        ICON_DP.dp,
        ICON_DP,
        ICON_DP,
    ).addPath(
        PathParser().parsePathString(path).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    private fun filled(name: String, path: String) = ImageVector.Builder(
        name,
        ICON_DP.dp,
        ICON_DP.dp,
        ICON_DP,
        ICON_DP,
    ).addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
        val innerW = w - 2 * r
        val innerH = h - 2 * r
        return "M${x + r} ${y}h${innerW}a$r $r 0 0 1 $r ${r}v${innerH}a$r $r 0 0 1 -$r ${r}h-${innerW}" +
            "a$r $r 0 0 1 -$r -${r}v-${innerH}a$r $r 0 0 1 $r -${r}z "
    }

    private fun dashedCircle(): String = buildString {
        for (k in 0 until DASHES) {
            val from = (k * 360f / DASHES + DASH_GAP_DEGREES) * DEGREES_TO_RADIANS
            val to = ((k + 1) * 360f / DASHES - DASH_GAP_DEGREES) * DEGREES_TO_RADIANS
            append("M${12f + 9f * cos(from)} ${12f + 9f * sin(from)}")
            append("A9 9 0 0 1 ${12f + 9f * cos(to)} ${12f + 9f * sin(to)} ")
        }
    }

    /** A circle of dashes, open at the lower left for the lasso's loop. */
    private fun dashedArc(): String = buildString {
        for (k in 0 until LASSO_DASHES) {
            val from = (LASSO_START_DEGREES + k * LASSO_STEP_DEGREES) * DEGREES_TO_RADIANS
            val to = (LASSO_START_DEGREES + (k + 1) * LASSO_STEP_DEGREES - DASH_GAP_DEGREES) * DEGREES_TO_RADIANS
            append("M${13f + 8f * cos(from)} ${10.5f + 8f * sin(from)}")
            append("A8 8 0 0 1 ${13f + 8f * cos(to)} ${10.5f + 8f * sin(to)} ")
        }
    }

    private fun gear(): String = buildString {
        val step = 360f / GEAR_TEETH
        for (k in 0 until GEAR_TEETH) {
            val a = k * step
            point(if (k == 0) 'M' else 'L', GEAR_INNER, a - step * 0.28f)
            point('L', GEAR_OUTER, a - step * 0.14f)
            point('L', GEAR_OUTER, a + step * 0.14f)
            point('L', GEAR_INNER, a + step * 0.28f)
        }
        append("z ")
    }

    private fun StringBuilder.point(command: Char, radius: Float, degrees: Float) {
        val a = degrees * DEGREES_TO_RADIANS
        append("$command${12f + radius * cos(a)} ${12f + radius * sin(a)}")
    }

    private const val ICON_DP = 24f
    private const val STROKE_WIDTH = 1.7f
    private const val DASHES = 8
    private const val DASH_GAP_DEGREES = 8f
    private const val LASSO_DASHES = 5
    private const val LASSO_START_DEGREES = 170f
    private const val LASSO_STEP_DEGREES = 58f
    private const val GEAR_TEETH = 8
    private const val GEAR_INNER = 7.2f
    private const val GEAR_OUTER = 9.4f
    private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
}
