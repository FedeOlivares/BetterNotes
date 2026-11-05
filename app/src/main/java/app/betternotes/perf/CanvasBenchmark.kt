package app.betternotes.perf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import android.view.Choreographer
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.meshAll
import app.betternotes.core.format.DocumentStore
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.model.Document
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.pdf.PdfImportResult
import app.betternotes.pdf.PdfImporter
import java.io.File
import java.io.FileOutputStream
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
    private const val PDF_PAGES = 6
    private const val SCENARIO_FRAMES = 700

    /** Writes a one-page A4 note holding [strokeCount] handwriting-sized strokes. */
    fun writeDocument(store: DocumentStore, strokeCount: Int): Document {
        val background = PageBackground(template = PageTemplate.Dotted())
        val document = Document.newNote("Benchmark", PageSize.A4, background, System.currentTimeMillis())
        store.writeDocument(document)
        store.writePage(document.pages[0].id, handwriting(strokeCount))
        return document
    }

    /**
     * A PDF of [PDF_PAGES] pages, text and vector art, with ink of every costly kind over it: the stamped
     * brushes, plain pens and shapes. It is the load a heavy study document puts on the screen.
     */
    fun writeHeavyDocument(context: Context, root: File): DocumentStore {
        root.deleteRecursively()
        root.mkdirs()
        val pdfFile = File(root, "heavy.pdf")
        writePdf(pdfFile)
        val imported = PdfImporter.import(context.contentResolver, Uri.fromFile(pdfFile), File(root, "documents")) as PdfImportResult.Imported
        for ((index, page) in imported.document.pages.withIndex()) {
            val size = page.size ?: continue
            imported.store.writePage(page.id, heavyInk(size.widthPt, size.heightPt, Random(index)))
        }
        return imported.store
    }

    private fun writePdf(file: File) {
        val pdf = PdfDocument()
        val text = Paint().apply { color = Color.DKGRAY; textSize = 10f }
        val line = Paint().apply { color = Color.GRAY; strokeWidth = 0.5f }
        // Every other page is a scan: one large picture of noisy "print", which is what a scanned book costs PDFium.
        val scan = android.graphics.Bitmap.createBitmap(1650, 2200, android.graphics.Bitmap.Config.ARGB_8888)
        val noise = Random(7)
        val pixels = IntArray(scan.width * scan.height) { val v = 200 + noise.nextInt(56); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        for (row in 0 until 120) for (x in 100 until 1500) pixels[(150 + row * 16) * scan.width + x] = 0xFF202020.toInt()
        scan.setPixels(pixels, 0, scan.width, 0, 0, scan.width, scan.height)
        repeat(PDF_PAGES) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            if (index % 2 == 1) {
                page.canvas.drawBitmap(scan, null, android.graphics.RectF(0f, 0f, 595f, 842f), null)
                pdf.finishPage(page)
                return@repeat
            }
            for (row in 0 until 60) page.canvas.drawText("lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor", 40f, 60f + row * 12f, text)
            for (i in 0 until 800) {
                val x = (i * 37 % 500).toFloat() + 40f
                page.canvas.drawLine(x, 300f + i % 400, x + 30f, 330f + i % 400, line)
            }
            pdf.finishPage(page)
        }
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()
    }

    private fun heavyInk(width: Float, height: Float, random: Random): List<PageItem> {
        val items = ArrayList<PageItem>()
        fun strokes(count: Int, style: InkStyle, lengthPt: Float) {
            repeat(count) {
                val x = random.nextFloat() * (width - lengthPt)
                val y = random.nextFloat() * height
                val builder = StrokePointsBuilder(SAMPLES_PER_STROKE)
                val wave = random.nextFloat() * 6.28f
                for (s in 0 until SAMPLES_PER_STROKE) {
                    val t = s.toFloat() / (SAMPLES_PER_STROKE - 1)
                    builder.add(x + t * lengthPt, y + sin(wave + t * 7f) * 12f, pressure = 0.4f + 0.3f * sin(t * PI.toFloat()), timeMs = s * 4f)
                }
                items += StrokeItem(IdSource.Random.next(), style, builder.build())
            }
        }
        strokes(120, InkStyle(width = 1.6f), 90f)
        for ((kind, count) in listOf(BrushKind.PENCIL to 80, BrushKind.WATERCOLOR to 40, BrushKind.LEAVES to 40, BrushKind.OIL to 40, BrushKind.GLOW to 10, BrushKind.SCRATCHES to 20)) {
            strokes(count, InkStyle(tool = InkTool.PENCIL, brush = kind, width = if (kind == BrushKind.GLOW) 3f else 6f, color = 0xFF2A5CAA.toInt()), 150f)
        }
        strokes(10, InkStyle(tool = InkTool.HIGHLIGHTER, width = 14f, color = 0xFFFFE680.toInt(), opacity = 0.5f), 200f)
        val kinds = listOf(ShapeKind.RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.STAR, ShapeKind.ARROW, ShapeKind.CUBE, ShapeKind.HEART)
        repeat(60) {
            val x = random.nextFloat() * (width - 80f)
            val y = random.nextFloat() * (height - 60f)
            items += ShapeItem(IdSource.Random.next(), Frame(x, y, 70f, 50f, 0f), kinds[it % kinds.size], fillColor = if (it % 2 == 0) 0x40FFAA00 else 0)
        }
        return items
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
            "lasso_move_100" to { frame -> canvas.benchmarkDrag(frame) },
        )
        val zooms = listOf(1f, 1f, 1f, 4f, 15f, 0.3f, 1f)
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
                    if (name.startsWith("lasso")) canvas.benchmarkDrag(-1)
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
