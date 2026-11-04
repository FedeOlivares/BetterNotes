package app.betternotes.perf

import android.annotation.SuppressLint
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.ink.authoring.ExperimentalInkLatencyDataApi
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.latency.LatencyData
import androidx.ink.authoring.latency.LatencyDataCallback
import app.betternotes.canvas.CanvasObserver

/** A fixed buffer of durations in nanoseconds. Thread safe, because each metric arrives on its own thread. */
internal class Samples(capacity: Int = 16_384) {
    private val values = LongArray(capacity)
    private var count = 0

    @Synchronized
    fun add(value: Long) {
        if (count < values.size) values[count++] = value
    }

    /** Returns what was gathered, and starts over. */
    @Synchronized
    fun drain(): LongArray {
        val out = values.copyOf(count)
        out.sort()
        count = 0
        return out
    }
}

private fun LongArray.millisAt(fraction: Double): Double =
    if (isEmpty()) Double.NaN else this[((size - 1) * fraction).toInt()] / 1e6

private fun LongArray.describe(): String =
    if (isEmpty()) "n=0" else "n=$size p50=%.2f p95=%.2f p99=%.2f max=%.2f".format(
        millisAt(0.5),
        millisAt(0.95),
        millisAt(0.99),
        millisAt(1.0),
    )

/**
 * Measures what software can see of frame rate and input latency, and writes it to logcat under the
 * tag [TAG]. Only built into debug and profile builds. What it cannot see is the hardware on either
 * side: the touch panel's scan before the kernel stamps an event, and the display's own delay once a
 * buffer is latched. Measuring those takes a high-speed camera.
 */
@SuppressLint("RestrictedApi") // Ink's latency data is its own instrument. Release builds drop this class.
@OptIn(ExperimentalInkLatencyDataApi::class)
internal class PerfProbe(private val window: Window) : CanvasObserver, LatencyDataCallback {
    /** Called on the main thread with each summary, for the on-screen readout. */
    var onSummary: (String) -> Unit = {}

    private val metricsThread = HandlerThread("perf-frames").apply { start() }

    // Frame pacing and cost.
    private val frameIntervals = Samples()
    private val frameTotal = Samples()
    private val frameGpu = Samples()
    private val frameUi = Samples()
    private var lastVsyncNs = 0L
    private var frames = 0
    private var missedDeadlines = 0
    private val frameLock = Any()

    // Input.
    private val sampleIntervals = Samples()
    private val dispatchDelay = Samples()
    private var lastSampleNs = 0L
    private var lastTool = "none"
    private var contactSamples = 0
    private var contactNanos = 0L
    private var lastReportNs = System.nanoTime()

    // Ink and tiles.
    private val inkDrawCalls = Samples()
    private val inkPresent = Samples()
    private val tileRecords = Samples()
    private var loggedRawLatency = false

    private val metricsListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val vsync = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = metrics.getMetric(FrameMetrics.DEADLINE)
        synchronized(frameLock) {
            if (lastVsyncNs != 0L) frameIntervals.add(vsync - lastVsyncNs)
            lastVsyncNs = vsync
            frames++
            if (total > deadline) missedDeadlines++
        }
        frameTotal.add(total)
        frameGpu.add(metrics.getMetric(FrameMetrics.GPU_DURATION))
        frameUi.add(
            metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
                metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                metrics.getMetric(FrameMetrics.DRAW_DURATION) +
                metrics.getMetric(FrameMetrics.SYNC_DURATION),
        )
    }

    init {
        window.addOnFrameMetricsAvailableListener(metricsListener, Handler(metricsThread.looper))
    }

    fun close() {
        window.removeOnFrameMetricsAvailableListener(metricsListener)
        metricsThread.quitSafely()
    }

    fun logDisplay(display: Display, dpi: Float) {
        val modes = display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@%.0f".format(it.refreshRate) }
        Log.i(TAG, "display refresh=%.1f modes=[%s] dpi=%.1f".format(display.refreshRate, modes, dpi))
    }

    /** Logs everything gathered since the last call, under a label, and starts over. */
    fun report(label: String) {
        val intervals = frameIntervals.drain()
        val (frameCount, missed) = synchronized(frameLock) {
            (frames to missedDeadlines).also {
                frames = 0
                missedDeadlines = 0
                lastVsyncNs = 0L
            }
        }
        val fps = if (intervals.isEmpty()) Double.NaN else 1000.0 / intervals.millisAt(0.5)
        val text = buildString {
            append("[$label] frames=$frameCount missed_deadline=$missed fps_p50=%.1f\n".format(fps))
            append("  frame_interval_ms ${intervals.describe()}\n")
            append("  frame_total_ms ${frameTotal.drain().describe()}\n")
            append("  frame_ui_ms ${frameUi.drain().describe()}\n")
            append("  frame_gpu_ms ${frameGpu.drain().describe()}\n")
            append("  tile_record_ms ${tileRecords.drain().describe()}\n")
            if (contactNanos > 0L) {
                val rate = contactSamples * 1e9 / contactNanos
                append("  input tool=$lastTool samples=$contactSamples rate_hz=%.1f\n".format(rate))
            }
            append("  input_interval_ms ${sampleIntervals.drain().describe()}\n")
            append("  input_to_app_ms ${dispatchDelay.drain().describe()}\n")
            append("  ink_event_to_drawn_ms ${inkDrawCalls.drain().describe()}\n")
            append("  ink_event_to_presented_est_ms ${inkPresent.drain().describe()}")
        }
        contactSamples = 0
        contactNanos = 0L
        lastReportNs = System.nanoTime()
        text.lineSequence().forEach { Log.i(TAG, it) }
        onSummary(text)
    }

    override fun onWetInkReady(view: InProgressStrokesView) {
        view.setLatencyDataCallback(this)
    }

    /** Runs on ink's render thread, and the object is reused, so it reads the values right away. */
    override fun onLatencyData(latency: LatencyData) {
        if (latency.strokeAction != LatencyData.StrokeAction.ADD || !latency.isOsDetectsEventSet) return
        val detected = latency.osDetectsEvent
        // Ink leaves a stamp at a sentinel value when the render path it took does not report it.
        val drawn = latency.strokesViewFinishesDrawCalls
        if (drawn > detected && drawn != Long.MAX_VALUE) inkDrawCalls.add(drawn - detected)
        val presented = latency.estimatedPixelPresentationTime
        if (presented > detected && presented != Long.MAX_VALUE) inkPresent.add(presented - detected)
        if (!loggedRawLatency) {
            loggedRawLatency = true
            Log.i(TAG, "raw latency sample: $latency")
        }
    }

    override fun onTileRecorded(nanos: Long) = tileRecords.add(nanos)

    override fun onTouch(event: MotionEvent) {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            val tool = toolName(event.getToolType(0))
            if (tool != lastTool && contactNanos > 0L) report("touch")
            lastTool = tool
            lastSampleNs = 0L
        }
        dispatchDelay.add(System.nanoTime() - event.eventTimeNanos)
        if (action == MotionEvent.ACTION_MOVE) {
            for (h in 0 until event.historySize) sampleAt(event.getHistoricalEventTimeNanos(h))
        }
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_DOWN) sampleAt(event.eventTimeNanos)
        val longEnough = System.nanoTime() - lastReportNs > REPORT_INTERVAL_NS
        if (longEnough && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) report("touch")
    }

    private fun sampleAt(timeNs: Long) {
        if (lastSampleNs != 0L) {
            sampleIntervals.add(timeNs - lastSampleNs)
            contactSamples++
            contactNanos += timeNs - lastSampleNs
        }
        lastSampleNs = timeNs
    }

    private fun toolName(toolType: Int) = when (toolType) {
        MotionEvent.TOOL_TYPE_STYLUS -> "stylus"
        MotionEvent.TOOL_TYPE_ERASER -> "eraser"
        MotionEvent.TOOL_TYPE_FINGER -> "finger"
        MotionEvent.TOOL_TYPE_MOUSE -> "mouse"
        else -> "other"
    }

    companion object {
        const val TAG = "BetterNotesPerf"
        private const val REPORT_INTERVAL_NS = 5_000_000_000L
    }
}
