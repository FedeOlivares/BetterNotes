package app.betternotes.core.template

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TextItem

/**
 * The design as items the owner can select, move, recolor and delete: fills become rectangles, dots ellipses, and lines and
 * waves paths. A cover page made this way holds them as its own content, so the drawn design is turned off with it (see
 * [CoverDesign.CUSTOM_ID]). Items come back in the order they were drawn, bottom first.
 */
fun CoverDesign.toItems(width: Float, height: Float, ids: IdSource = IdSource.Random): List<PageItem> {
    val sink = ItemSink(ids)
    draw(sink, width, height)
    return sink.items
}

private class ItemSink(private val ids: IdSource) : TemplateSink {
    val items = ArrayList<PageItem>()

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) =
        polyline(floatArrayOf(x0, y0, x1, y1), width, color)

    override fun polyline(points: FloatArray, width: Float, color: Int) {
        items += PathShapeItem(ids.next(), points, closed = false, smooth = false, strokeColor = color, strokeWidth = width)
    }

    override fun dot(x: Float, y: Float, radius: Float, color: Int) {
        items += ShapeItem(ids.next(), Frame(x - radius, y - radius, radius * 2f, radius * 2f), ShapeKind.ELLIPSE, strokeColor = 0, strokeWidth = 0f, fillColor = color)
    }

    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) {
        items += ShapeItem(ids.next(), Frame(x, y, width, height), ShapeKind.RECTANGLE, strokeColor = 0, strokeWidth = 0f, fillColor = color)
    }

    override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) {
        val width = size * 0.8f * text.length + size
        val left = if (align == TextAlign.CENTER) x - width / 2f else x
        items += TextItem(ids.next(), Frame(left, baseline - size, width, size * 1.4f), text, size, color)
    }
}
