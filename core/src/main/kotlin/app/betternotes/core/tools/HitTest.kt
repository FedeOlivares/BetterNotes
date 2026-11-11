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
import app.betternotes.core.model.FillItem
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.VideoItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ELLIPSE_OUTLINE_SEGMENTS = 24

/**
 * The index of the topmost item at a point, or -1. [tolerance] grows the target for a fingertip. Locked items are a layer below the
 * others, so one that is not locked wins over a locked one whatever their order.
 */
fun PageContent.topItemAt(x: Float, y: Float, tolerance: Float = 0f): Int {
    val probe = Capsule(x, y, x, y, tolerance)
    var top = -1
    var topLocked = -1
    forEachIntersecting(probe.bounds) { index, item ->
        if (isEditable(item) && item.touches(probe)) {
            if (item.locked) topLocked = index else top = index
        }
    }
    return if (top >= 0) top else topLocked
}

/** True if the path of [probe] crosses what this item actually paints, not merely its bounds. */
internal fun PageItem.touches(probe: Capsule): Boolean = when (this) {
    is StrokeItem -> points.touches(probe, style.width / 2f)
    is ShapeItem -> touches(probe)
    is ImageItem -> frame.touches(probe)
    is VideoItem -> frame.touches(probe)
    is TextItem -> frame.touches(probe)
    is TableItem -> frame.touches(probe)
    is AudioItem -> frame.touches(probe)
    is StickyNoteItem -> visibleFrame.touches(probe)
    is TapePathItem -> touchesPath(probe)
    is FillItem -> contains(probe.ax, probe.ay) || contains(probe.bx, probe.by)
    is PathShapeItem -> touchesPath(probe)
}

private fun PathShapeItem.touchesPath(probe: Capsule): Boolean {
    val line = outline()
    val margin = strokeWidth / 2f
    val last = if (closed) line.size else line.size - 2
    var i = 0
    while (i < last) {
        val next = (i + 2) % line.size
        if (probe.clip(line[i], line[i + 1], line[next], line[next + 1], margin)) return true
        i += 2
    }
    return isFilled && polygonContains(line, probe.ax, probe.ay)
}

/** A strip of tape is touched where the probe comes within half its width of the line along its middle. */
private fun TapePathItem.touchesPath(probe: Capsule): Boolean {
    val margin = width / 2f
    if (pointCount == 1) return probe.clip(points[0], points[1], points[0], points[1], margin)
    for (i in 0 until pointCount - 1) {
        if (probe.clip(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3], margin)) return true
    }
    return false
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
    val isClosed = kind != ShapeKind.LINE && kind != ShapeKind.ARROW && kind != ShapeKind.CURVE
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
        ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.CURVE -> floatArrayOf(0f, 0.5f, 1f, 0.5f)
        ShapeKind.RECTANGLE, ShapeKind.BLOCK_ARROW, ShapeKind.PYRAMID, ShapeKind.CYLINDER, ShapeKind.CUBE,
        ShapeKind.TETRAHEDRON, ShapeKind.PRISM, ShapeKind.DISCS, ShapeKind.SINE, ShapeKind.COSINE, ShapeKind.AXES,
        ShapeKind.PARABOLA, ShapeKind.CUBIC, ShapeKind.BRACE, ShapeKind.HEART, ShapeKind.SPEECH_BOX,
        ShapeKind.SPEECH_ROUND, ShapeKind.THOUGHT, ShapeKind.TAPE,
        ->
            floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        ShapeKind.TRIANGLE -> floatArrayOf(0.5f, 0f, 1f, 1f, 0f, 1f)
        ShapeKind.DIAMOND -> floatArrayOf(0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f)
        ShapeKind.PARALLELOGRAM -> floatArrayOf(0.22f, 0f, 1f, 0f, 0.78f, 1f, 0f, 1f)
        ShapeKind.TRAPEZOID -> floatArrayOf(0.22f, 0f, 0.78f, 0f, 1f, 1f, 0f, 1f)
        ShapeKind.ELLIPSE, ShapeKind.SPHERE, ShapeKind.PENTAGON, ShapeKind.HEXAGON, ShapeKind.PIE, ShapeKind.ARC, ShapeKind.STAR ->
            FloatArray(ELLIPSE_OUTLINE_SEGMENTS * 2) { i ->
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
