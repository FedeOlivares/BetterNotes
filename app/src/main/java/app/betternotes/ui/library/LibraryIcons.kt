package app.betternotes.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val STROKE = 1.5f
private const val STAR = "M12 3.4l2.6 5.5 6 .8-4.4 4.2 1.1 6-5.3-2.9-5.3 2.9 1.1-6-4.4-4.2 6-.8z"

/** Line icons drawn for this app on a 24 unit grid. Tint them with Icon's color. */
private fun icon(name: String, outline: String? = null, filled: String? = null) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        if (outline != null) {
            addPath(
                pathData = addPathNodes(outline),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        if (filled != null) addPath(pathData = addPathNodes(filled), fill = SolidColor(Color.Black))
    }.build()

private fun dot(x: Float, y: Float, radius: Float) =
    "M${x - radius} ${y}a$radius $radius 0 1 0 ${radius * 2} 0a$radius $radius 0 1 0 ${-radius * 2} 0z"

internal object LibraryIcons {
    val Star = icon("star", outline = STAR)
    val StarFilled = icon("starFilled", filled = STAR)
    val Tag = icon(
        "tag",
        outline = "M20 11.3V5.5A1.5 1.5 0 0 0 18.5 4h-5.8a1.5 1.5 0 0 0-1.06.44l-7.2 7.2a1.5 1.5 0 0 0 0 2.12l5.8 5.8" +
            "a1.5 1.5 0 0 0 2.12 0l7.2-7.2A1.5 1.5 0 0 0 20 11.3z",
        filled = dot(16.2f, 7.8f, 1.2f),
    )
    val Bin = icon("bin", outline = "M5 6.5h14M9.5 6.5V4.3h5v2.2M6.5 6.5l1 13.2h9l1-13.2M10 10v6.5M14 10v6.5")
    val Search = icon("search", outline = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 0 0 0-13zM15.4 15.4L20 20")
    val Home = icon("home", outline = "M4 10.8l8-6.8 8 6.8v8.2a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19zM9.5 20.5v-6.2h5v6.2")
    val Plus = icon("plus", outline = "M12 5v14M5 12h14")
    val Back = icon("back", outline = "M19.5 12H5M11 6l-6 6 6 6")
    val Close = icon("close", outline = "M6.5 6.5l11 11M17.5 6.5l-11 11")
    val TextCursor = icon("text", outline = "M4.5 6.5V5h11.500v1.5M10.200 5v14M8 19h4.400M19.500 3.500v17")
    val Canvas = icon(
        "canvas",
        outline = "M5 4.5h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z" +
            "M7.500 16c1.500-3.500 3.500-7.500 4.500-7c.8.4-.5 3.500-1.500 6.500c1.500-2 4.500-7 5.500-6.500c.8.4-1 4-1 6.500",
    )
    val Microphone = icon(
        "microphone",
        outline = "M12 3a3 3 0 0 1 3 3v5.5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3M9 21h6",
    )
    val Document = icon(
        "document",
        outline = "M13.5 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.5zM13.5 3v5.5H19M8.5 12.5h7M8.5 16h7",
    )
    val Camera = icon(
        "camera",
        outline = "M9 5.5l1.2-1.7h3.6L15 5.5h4a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-10a2 2 0 0 1 2-2z" +
            "M12 9a3.5 3.5 0 1 0 0 7a3.5 3.5 0 0 0 0-7z",
    )
    val Scan = icon(
        "scan",
        outline = "M3.5 8V5.5a2 2 0 0 1 2-2H8M16 3.5h2.5a2 2 0 0 1 2 2V8M20.5 16v2.5a2 2 0 0 1-2 2H16M8 20.5H5.5a2 2 0 0 1-2-2V16" +
            "M8.5 7.5h4.5l2.5 2.5v6.5h-7zM13 7.5V10h2.5",
    )
    val Photos = icon(
        "photos",
        outline = "M5.5 3.5h13a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z" +
            "M3.5 16.5l5-5 4 4 2.5-2.5 5.5 5.5M15 7a1.6 1.6 0 1 0 0 3.200a1.6 1.6 0 0 0 0-3.200z",
    )
    val Folder = icon(
        "folder",
        outline = "M3.5 7a2 2 0 0 1 2-2h4.2l2.3 2.5h6.500a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z",
    )
    val CheckCircle = icon("checkCircle", outline = "M12 3.200a8.800 8.800 0 1 0 0 17.600a8.800 8.800 0 0 0 0-17.600zM8 12.300l2.800 2.800 5.200-5.600")
    val EmptyCircle = icon("emptyCircle", outline = "M12 3.200a8.800 8.800 0 1 0 0 17.600a8.800 8.800 0 0 0 0-17.600z")
    val Check = icon("check", outline = "M4.500 12.800l5 5 10-11")
    val GridView = icon("grid", outline = "M4.500 5h15a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM3.500 12h17M9.300 5v14M14.700 5v14")
    val ListView = icon("list", outline = "M8.500 6h12M8.500 12h12M8.500 18h12", filled = dot(4.500f, 6f, 1.200f) + dot(4.500f, 12f, 1.200f) + dot(4.500f, 18f, 1.200f))
    val SortDown = icon("sortDown", outline = "M12 4v16M6.500 14.500L12 20l5.500-5.500")
    val Settings = icon("settings", outline = "M12 2.800l8 4.600v9.200l-8 4.600-8-4.600V7.400zM12 8.800a3.200 3.200 0 1 0 0 6.400a3.200 3.200 0 0 0 0-6.400z")
    val Chevron = icon("chevron", outline = "M9.500 6l6 6-6 6")
    val MoreCircled = icon(
        "more",
        outline = "M12 3.2a8.8 8.8 0 1 0 0 17.6a8.8 8.8 0 0 0 0-17.6z",
        filled = dot(7.6f, 12f, 1.2f) + dot(12f, 12f, 1.2f) + dot(16.4f, 12f, 1.2f),
    )
}
