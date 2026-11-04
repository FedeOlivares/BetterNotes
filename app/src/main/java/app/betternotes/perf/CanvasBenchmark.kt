package app.betternotes.perf

import android.util.Log
import android.view.Choreographer
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.meshAll
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A repeatable load: a page dense with handwriting, moved by script rather than by hand, so that frame
 * times can be compared between builds without anyone touching the screen.
 */
internal object CanvasBenchmark {
    private const val SAMPLES_PER_STROKE = 40
    private const val ROWS = 40
    private const val SCENARIO_FRAMES = 700

    /** Writes a one-page A4 note holding [strokeCount] handwriting-sized strokes. */
    fun writeDocument(store: DocumentStore, strokeCount: Int): Document {
        val background = PageBackground(template = PageTemplate.Dotted())
        val document = Document.newNote("Benchmark", PageSize.A4, background, System.currentTimeMillis())
        store.writeDocument(document)
        store.writePage(document.pages[0].id, handwriting(strokeCount))
        return document
    }

    /** Times meshing alone, which is the larger part of opening a page. */
    fun logMeshCost(store: DocumentStore, document: Document) {
        val startRead = System.nanoTime()
        val items = store.readPage(document.pages[0].id)
        val startMesh = System.nanoTime()
        val meshes = meshAll(items)
        val end = System.nanoTime()
        Log.i(
            PerfProbe.TAG,
            "page strokes=${meshes.size} read_ms=%.1f mesh_ms=%.1f".format(
                (startMesh - startRead) / 1e6,
                (end - startMesh) / 1e6,
            ),
        )
    }

    /** Runs each scenario in turn, logging its numbers through [probe], then calls [onDone]. */
    fun run(canvas: CanvasView, probe: PerfProbe, onDone: () -> Unit) {
        val scenarios = listOf<Pair<String, (Int) -> Unit>>(
            "idle" to { _ -> canvas.invalidate() },
            "pan_zoom_100" to { frame -> pan(canvas, frame) },
            "pinch_100_to_400" to { frame -> pinch(canvas, frame) },
            "pan_zoom_400" to { frame -> pan(canvas, frame) },
            "pan_zoom_1500" to { frame -> pan(canvas, frame) },
            "pan_zoom_30" to { frame -> pan(canvas, frame) },
        )
        val zooms = listOf(1f, 1f, 1f, 4f, 15f, 0.3f)
        runScenario(canvas, probe, scenarios, zooms, 0, onDone)
    }

    private fun runScenario(
        canvas: CanvasView,
        probe: PerfProbe,
        scenarios: List<Pair<String, (Int) -> Unit>>,
        zooms: List<Float>,
        index: Int,
        onDone: () -> Unit,
    ) {
        if (index == scenarios.size) {
            Log.i(PerfProbe.TAG, "benchmark finished")
            onDone()
            return
        }
        val (name, step) = scenarios[index]
        canvas.zoomTo(zooms[index])
        // The first frames after a change of zoom are not what a scenario is measuring.
        var frame = -SETTLE_FRAMES
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (frame == 0) probe.report("discard")
                if (frame >= 0) step(frame)
                frame++
                if (frame < SCENARIO_FRAMES) {
                    choreographer.postFrameCallback(this)
                } else {
                    canvas.onTransformEnd()
                    probe.report(name)
                    runScenario(canvas, probe, scenarios, zooms, index + 1, onDone)
                }
            }
        })
    }

    private const val SETTLE_FRAMES = 30

    /** Steady scrolling down the page and back, at about the speed of a flick. */
    private fun pan(canvas: CanvasView, frame: Int) {
        val direction = if ((frame / 120) % 2 == 0) -1f else 1f
        canvas.onTransform(0f, direction * 24f, 1f, 0f, 0f)
    }

    /** A slow pinch from the start zoom out to 4x and back, about the middle of the view. */
    private fun pinch(canvas: CanvasView, frame: Int) {
        val phase = frame.toFloat() / SCENARIO_FRAMES
        val target = 1f + 3f * sin(phase * PI.toFloat())
        val scale = target / canvas.viewport.zoom
        canvas.onTransform(0f, 0f, scale, canvas.width / 2f, canvas.height / 2f)
    }

    private fun handwriting(strokeCount: Int): List<PageItem> {
        val random = Random(42)
        val size = PageSize.A4
        val style = InkStyle(width = 1.6f)
        val perRow = strokeCount / ROWS + 1
        val rowHeight = size.heightPt / ROWS
        val stepX = size.widthPt / perRow
        return List(strokeCount) { i ->
            val x = (i % perRow) * stepX
            val y = (i / perRow) * rowHeight + rowHeight / 2f
            val builder = StrokePointsBuilder(SAMPLES_PER_STROKE)
            val wave = random.nextFloat() * 2f * PI.toFloat()
            for (s in 0 until SAMPLES_PER_STROKE) {
                val t = s.toFloat() / (SAMPLES_PER_STROKE - 1)
                builder.add(
                    x + t * stepX * 0.9f + sin(wave + t * 9f) * 2f,
                    y + sin(wave + t * 13f) * rowHeight * 0.3f,
                    pressure = 0.4f + 0.3f * sin(t * PI.toFloat()),
                    timeMs = s * 4f,
                )
            }
            StrokeItem(IdSource.Random.next(), style, builder.build())
        }
    }
}
