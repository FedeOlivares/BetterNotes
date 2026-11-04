package app.betternotes.perf

import android.content.Intent
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.search.InkLine
import app.betternotes.core.search.groupInkLines
import app.betternotes.search.InkModels
import app.betternotes.search.MlKitRecognizer
import kotlin.concurrent.thread

/**
 * Answers the question left open by the choice of ML Kit: once its models are here, does recognition
 * really work with no network? Driven by scripts/verify-handwriting-offline.ps1, it logs to logcat.
 * Debug, profile and offline builds only.
 */
object HandwritingCheck {
    private const val TAG = "BetterNotesInk"
    private const val EXTRA_CHECK = "handwriting_check"
    private const val EXTRA_DOWNLOAD = "handwriting_download"

    fun runIfAsked(intent: Intent) {
        val download = intent.getBooleanExtra(EXTRA_DOWNLOAD, false)
        if (!download && !intent.getBooleanExtra(EXTRA_CHECK, false)) return
        thread(name = "handwriting-check") {
            try {
                run(download)
            } catch (e: Exception) {
                Log.e(TAG, "RESULT failed: $e", e)
            }
            Log.i(TAG, "RESULT finished")
        }
    }

    private fun run(download: Boolean) {
        Log.i(TAG, "missing before: ${InkModels.missing()}")
        if (download) {
            val start = System.nanoTime()
            InkModels.download()
            Log.i(TAG, "download took ${(System.nanoTime() - start) / 1_000_000} ms")
        }
        val missing = InkModels.missing()
        Log.i(TAG, "missing now: $missing")
        if (missing.isNotEmpty()) {
            Log.e(TAG, "RESULT models missing, not recognizing")
            return
        }
        val uid = Process.myUid()
        val sentBefore = TrafficStats.getUidTxBytes(uid)
        val receivedBefore = TrafficStats.getUidRxBytes(uid)
        MlKitRecognizer().use { recognizer ->
            for (word in SyntheticInk.WORDS) {
                val line = SyntheticInk.line(word)
                val start = System.nanoTime()
                val guesses = recognizer.recognize(line)
                val ms = (System.nanoTime() - start) / 1_000_000
                Log.i(TAG, "RESULT wrote '$word' read $guesses in $ms ms")
            }
        }
        val sent = TrafficStats.getUidTxBytes(uid) - sentBefore
        val received = TrafficStats.getUidRxBytes(uid) - receivedBefore
        Log.i(TAG, "RESULT traffic while recognizing: sent $sent bytes, received $received bytes")
    }
}

/**
 * Handwriting drawn by program, since a check with nobody at the device has no hand to use. Letters
 * are simple shapes in a 10 by 20 box, so this proves the pipeline works. It says nothing about how
 * well ML Kit reads a person's writing, which only real notes can show.
 */
internal object SyntheticInk {
    val WORDS = listOf("hello", "hola", "mundo")

    private const val ADVANCE = 13f
    private const val SCALE = 2f
    private const val MS_PER_POINT = 8f

    private val LETTERS: Map<Char, List<List<Pair<Float, Float>>>> = mapOf(
        'h' to listOf(listOf(0f to 0f, 0f to 20f), arch(0f, 10f, 10f)),
        'e' to listOf(
            listOf(1f to 15f, 9f to 15f, 9f to 12f, 7f to 10f, 3f to 10f, 0f to 13f, 0f to 17f, 3f to 20f, 7f to 20f, 10f to 18f),
        ),
        'l' to listOf(listOf(4f to 0f, 4f to 20f)),
        'o' to listOf(ring()),
        'a' to listOf(ring(), listOf(10f to 10f, 10f to 20f)),
        'm' to listOf(listOf(0f to 10f, 0f to 20f), arch(0f, 10f, 5f), arch(5f, 10f, 5f)),
        'u' to listOf(listOf(0f to 10f, 0f to 17f, 2f to 20f, 8f to 20f, 10f to 17f), listOf(10f to 10f, 10f to 20f)),
        'n' to listOf(listOf(0f to 10f, 0f to 20f), arch(0f, 10f, 10f)),
        'd' to listOf(ring(), listOf(10f to 0f, 10f to 20f)),
    )

    private fun ring() = listOf(
        8f to 11f, 5f to 10f, 2f to 11f, 0f to 15f, 2f to 19f, 5f to 20f, 8f to 19f, 10f to 15f, 8f to 11f,
    )

    private fun arch(x: Float, top: Float, width: Float) = listOf(
        x to top + 4f,
        x + width * 0.25f to top + 1f,
        x + width * 0.5f to top,
        x + width * 0.85f to top + 1f,
        x + width to top + 4f,
        x + width to 20f,
    )

    /** The strokes of [word], as a page would store them, with their top-left near (40, 40). */
    fun items(word: String, originX: Float = 40f, originY: Float = 40f): List<StrokeItem> {
        var id = word.hashCode().toLong() shl 16
        val items = ArrayList<StrokeItem>()
        word.forEachIndexed { index, letter ->
            val x = originX + index * ADVANCE * SCALE
            for (polyline in LETTERS.getValue(letter)) {
                val points = StrokePointsBuilder(polyline.size)
                densify(polyline).forEachIndexed { i, (px, py) ->
                    points.add(x + px * SCALE, originY + py * SCALE, 0.5f, timeMs = i * MS_PER_POINT)
                }
                items += StrokeItem(id++, InkStyle(), points.build())
            }
        }
        return items
    }

    fun line(word: String): InkLine = groupInkLines(items(word)).single()

    /** Real input has many points per stroke, and a recognizer can tell. */
    private fun densify(polyline: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        for (i in 0 until polyline.lastIndex) {
            val (x0, y0) = polyline[i]
            val (x1, y1) = polyline[i + 1]
            for (step in 0 until SEGMENT_STEPS) {
                val t = step.toFloat() / SEGMENT_STEPS
                out += x0 + (x1 - x0) * t to y0 + (y1 - y0) * t
            }
        }
        out += polyline.last()
        return out
    }

    private const val SEGMENT_STEPS = 6
}
