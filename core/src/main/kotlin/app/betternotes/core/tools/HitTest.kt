package app.betternotes.core.tools

import app.betternotes.core.geometry.Capsule
import app.betternotes.core.geometry.Frame
import app.betternotes.core.geometry.polygonContains
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.PageContent
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ELLIPSE_OUTLINE_SEGMENTS = 24

/** The index of the topmost item at a point, or -1. [tolerance] grows the target for a fingertip. */
fun PageContent.topItemAt(x: Float, y: Float, tolerance: Float = 0f): Int {
    val probe = Capsule(x, y, x, y, tolerance)
    var top = -1
    forEachIntersecting(probe.bounds) { index, item ->
        if (item.touches(probe)) top = index
    }
    return top
}

/** True if the path of [probe] crosses what this item actually paints, not merely its bounds. */
internal fun PageItem.touches(probe: Capsule): Boolean = when (this) {
    is StrokeItem -> points.touches(probe, style.width / 2f)
    is ShapeItem -> touches(probe)
    is ImageItem -> frame.touches(probe)
    is TextItem -> frame.touches(probe)
    is TableItem -> frame.touches(probe)
    is AudioItem -> frame.touches(probe)
    is StickyNoteItem -> visibleFrame.touches(probe)
}

private fun StrokePoints.touches(probe: Capsule, margin: Float): Boolean {
    if (size == 1) return probe.clip(x(0), y(0), x(0), y(0), margin)
    for (i in 0 until size - 1) {
        if (probe.clip(x(i), y(i), x(i + 1), y(i + 1), margin)) return true
    }
    return false
}

private fun Frame.touches(probe: Capsule) =
    contains(probe.ax, probe.ay, probe.radius) || contains(probe.bx, probe.by, probe.radius)

private fun ShapeItem.touches(probe: Capsule): Boolean {
    val outline = outline()
    val isClosed = kind != ShapeKind.LINE && kind != ShapeKind.ARROW
    val margin = strokeWidth / 2f
    val lastStart = if (isClosed) outline.size - 2 else outline.size - 4
    var i = 0
    while (i <= lastStart) {
        val next = (i + 2) % outline.size
        if (probe.clip(outline[i], outline[i + 1], outline[next], outline[next + 1], margin)) return true
        i += 2
    }
    return isFilled && polygonContains(outline, probe.ax, probe.ay)
}

/** Packed x,y world points along the shape. Corner radius is ignored, which is close enough to aim at. */
private fun ShapeItem.outline(): FloatArray {
    val unit: FloatArray = when (kind) {
        ShapeKind.LINE, ShapeKind.ARROW -> floatArrayOf(0f, 0.5f, 1f, 0.5f)
        ShapeKind.RECTANGLE -> floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        ShapeKind.TRIANGLE -> floatArrayOf(0.5f, 0f, 1f, 1f, 0f, 1f)
        ShapeKind.ELLIPSE -> FloatArray(ELLIPSE_OUTLINE_SEGMENTS * 2) { i ->
            val angle = 2.0 * PI * (i / 2) / ELLIPSE_OUTLINE_SEGMENTS
            val wave = if (i % 2 == 0) cos(angle) else sin(angle)
            0.5f + 0.5f * wave.toFloat()
        }
    }
    return FloatArray(unit.size) { i ->
        val pair = i - i % 2
        if (i % 2 == 0) frame.worldX(unit[pair], unit[pair + 1]) else frame.worldY(unit[pair], unit[pair + 1])
    }
}
