package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RenderNode
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import app.betternotes.core.format.DocumentSession
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.model.StrokePointsBuilder
import java.util.IdentityHashMap

/** Every stroke counts one point as a real point on paper, so its shape never depends on the zoom it was drawn at. */
internal const val STROKE_UNIT_CM = 2.54f / 72f

/**
 * The ink meshes of the strokes in memory. A mesh is costly to build and a page redraws often, so it
 * lives as long as its item does. Items are looked up by identity because hashing a [StrokeItem] would
 * hash all of its samples. Use from one thread; meshes built elsewhere arrive through [put].
 */
internal class StrokeCache {
    private val meshes = IdentityHashMap<StrokeItem, Stroke>()
    private val opaqueMeshes = IdentityHashMap<StrokeItem, Stroke>()
    private var sweepAt = MIN_SWEEP_SIZE

    fun get(item: StrokeItem): Stroke = meshes.getOrPut(item) { item.toInkStroke() }

    /** The opaque mesh of a no-overlap highlighter stroke, see [InkBrushes.opaqueBrush]. */
    fun getOpaque(item: StrokeItem): Stroke = opaqueMeshes.getOrPut(item) { item.toInkStroke(opaque = true) }

    fun put(item: StrokeItem, stroke: Stroke) {
        meshes[item] = stroke
    }

    /** Forgets meshes of items no longer on a page in memory, once enough may have piled up. */
    fun sweep(session: DocumentSession) {
        if (meshes.size < sweepAt) return
        val live = IdentityHashMap<StrokeItem, Unit>()
        for (page in session.loadedContents) {
            for (i in 0 until page.size) {
                (page[i] as? StrokeItem)?.let { live[it] = Unit }
            }
        }
        meshes.keys.retainAll { it in live }
        opaqueMeshes.keys.retainAll { it in live }
        sweepAt = maxOf(MIN_SWEEP_SIZE, meshes.size * 2)
    }

    private companion object {
        const val MIN_SWEEP_SIZE = 1024
    }
}

/**
 * The ink renderer for one open document. The renderer keeps the GPU mesh of each stroke it draws, and
 * building one is the largest cost of a stroke's first draw (about 3 ms for a stamped brush), so tiles,
 * the lasso and [warm] share one renderer, and a page's meshes are built in the background before the
 * page is first drawn. The renderer is not thread safe, so every use takes its lock.
 */
internal class InkRenderer {
    private val renderer = CanvasStrokeRenderer.create(BrushTextures)
    private val scratch = RenderNode("ink-warm")

    /** When a tile or the lasso last drew. [warm] stays out of their way, since a lock would not favor them. */
    @Volatile
    private var lastDrawNanos = 0L

    fun draw(canvas: Canvas, stroke: Stroke, matrix: Matrix) {
        lastDrawNanos = System.nanoTime()
        synchronized(this) { renderer.draw(canvas, stroke, matrix) }
    }

    /** Like [draw], but a canvas that cannot stamp gets the plain look-alike. See [InkBrushes.draw]. */
    fun drawOrStandIn(canvas: Canvas, stroke: Stroke, matrix: Matrix) {
        lastDrawNanos = System.nanoTime()
        synchronized(this) { InkBrushes.draw(renderer, canvas, stroke, matrix) }
    }

    /** Builds the stroke's mesh by drawing it where nobody sees. Safe from any thread. */
    fun warm(strokes: Collection<Stroke>) {
        for (stroke in strokes) {
            while (System.nanoTime() - lastDrawNanos < DRAWING_QUIET_NANOS) Thread.sleep(1)
            // One stroke per lock, so drawing never waits for more than one mesh.
            synchronized(this) {
                val canvas = scratch.beginRecording(1, 1)
                try {
                    renderer.draw(canvas, stroke, IDENTITY)
                } finally {
                    scratch.endRecording()
                }
            }
        }
        synchronized(this) { scratch.discardDisplayList() }
    }

    private companion object {
        val IDENTITY = Matrix()

        /** Drawing that paused this recently is probably still under way. */
        const val DRAWING_QUIET_NANOS = 3_000_000L
    }
}

/** Meshes a saved stroke. Safe from any thread. */
internal fun StrokeItem.toInkStroke(opaque: Boolean = false): Stroke {
    val tool = if (points.pressure(0) == StrokePoints.UNKNOWN) InputToolType.TOUCH else InputToolType.STYLUS
    val batch = MutableStrokeInputBatch()
    for (i in 0 until points.size) {
        val x = points.x(i)
        val y = points.y(i)
        val timeMs = points.timeMs(i)
        // Ink refuses a repeated sample, and splitting a stroke at an existing sample can make one.
        if (i > 0 && x == points.x(i - 1) && y == points.y(i - 1) && timeMs == points.timeMs(i - 1)) continue
        batch.add(
            tool,
            x,
            y,
            timeMs.toLong(),
            STROKE_UNIT_CM,
            points.pressure(i),
            points.tilt(i),
            points.orientation(i),
        )
    }
    return Stroke(if (opaque) InkBrushes.opaqueBrush(style) else InkBrushes.brush(style), batch)
}

/** Meshes every stroke among [items], on all cores, since this is most of the time a page takes to open. */
internal fun meshAll(items: List<PageItem>): List<Pair<StrokeItem, Stroke>> =
    items.parallelStream()
        .filter { it is StrokeItem }
        .map { (it as StrokeItem).let { stroke -> stroke to stroke.toInkStroke() } }
        .toList()

/** Copies the samples ink kept for a finished stroke into their saved form. */
internal fun ImmutableStrokeInputBatch.toStrokePoints(): StrokePoints {
    val builder = StrokePointsBuilder(size)
    val input = StrokeInput()
    for (i in 0 until size) {
        populate(i, input)
        builder.add(
            input.x,
            input.y,
            input.pressure,
            input.tiltRadians,
            input.orientationRadians,
            input.elapsedTimeMillis.toFloat(),
        )
    }
    return builder.build()
}
