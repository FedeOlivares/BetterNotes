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
    val Pen by lazy { line("pen", "M4 20l1.5-5.5L16 4a2.1 2.1 0 0 1 3 0l1 1a2.1 2.1 0 0 1 0 3L9.5 18.5z M14 6l4 4") }
    val Pencil by lazy {
        line("pencil", "M4 20l1.2-5L15.5 4.7a1.8 1.8 0 0 1 2.5 0l1.8 1.8a1.8 1.8 0 0 1 0 2.5L9.5 19.2z M5.2 15l4.3 4.2 M13.5 6.7l3.8 3.8")
    }
    val Highlighter by lazy { line("highlighter", "M9 13l6.5-9.5 5 3.5L14 16.5 M9 13l5 3.5-2.5 2.5-4.5-3z M7 16l-3 4h5l1.5-1.5") }
    val Eraser by lazy { line("eraser", "M8.5 20L3.8 15.3a1.8 1.8 0 0 1 0-2.6L12.5 4a1.8 1.8 0 0 1 2.6 0l5 5a1.8 1.8 0 0 1 0 2.6L11.7 20z M8 8.5l7.5 7.5 M8.5 20H21") }
    val Lasso by lazy { line("lasso", "M6 15C3.5 13.5 3 11.5 3 10c0-3.9 4-6.5 9-6.5s9 2.6 9 6.5-4 6.5-9 6.5c-1 0-2-.1-3-.3 M6 15a2.2 2.2 0 1 0 3 1.2 M7.2 17.5c0 1.5-.5 2.5-1.7 3.5") }
    val Shapes by lazy { line("shapes", rrect(3.5f, 11.5f, 9f, 9f, 1.5f) + "M17 3.5l4 7h-8z M16.5 14.5a3 3 0 1 0 .01 0") }
    val Image by lazy { line("image", rrect(3f, 4.5f, 18f, 15f, 2.5f) + "M3.5 17l5-5 4 4 2.5-2.5 5 5 M15.5 9.5a1.2 1.2 0 1 0 .01 0") }
    val Camera by lazy { line("camera", "M4 7.5h3.5L9 5h6l1.5 2.5H20a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 18V9A1.5 1.5 0 0 1 4 7.5z M12 9.8a3.5 3.5 0 1 0 .01 0") }
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
    private const val GEAR_TEETH = 8
    private const val GEAR_INNER = 7.2f
    private const val GEAR_OUTER = 9.4f
    private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
}
