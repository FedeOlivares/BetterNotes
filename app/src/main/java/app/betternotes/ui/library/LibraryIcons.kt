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
    val MoreCircled = icon(
        "more",
        outline = "M12 3.2a8.8 8.8 0 1 0 0 17.6a8.8 8.8 0 0 0 0-17.6z",
        filled = dot(7.6f, 12f, 1.2f) + dot(12f, 12f, 1.2f) + dot(16.4f, 12f, 1.2f),
    )
}
