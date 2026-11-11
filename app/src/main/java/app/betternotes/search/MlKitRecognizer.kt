package app.betternotes.search

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.model.StrokePoints
import app.betternotes.core.search.HandwritingRecognizer
import app.betternotes.core.search.InkLine
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import android.content.Context
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The handwriting models, about 20 MB per language. They ship inside the app and [install] puts them where ML Kit
 * expects them, so nothing is downloaded. [download] is only a way out if that fails, for example if ML Kit
 * deletes the files while the app runs, and it runs only when a person asks. Search says the models are gone through
 * [missing] rather than quietly fetching them again.
 *
 * Calls block, so run them on a background thread.
 */
object InkModels {
    val LANGUAGE_TAGS = listOf("en-US", "es-ES")

    private val models by lazy {
        LANGUAGE_TAGS.associateWith { tag ->
            val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
                ?: error("ML Kit no longer offers $tag")
            DigitalInkRecognitionModel.builder(id).build()
        }
    }

    private val seeding = AtomicReference<Future<*>>()

    /** Starts restoring the models that ship with the app. Call once, as early as the app starts. */
    fun install(context: Context) {
        val app = context.applicationContext
        val task = FutureTask { InkModelSeeder.seed(app) }
        if (seeding.compareAndSet(null, task)) Thread(task, "ink-model-seeder").start()
    }

    private fun awaitSeeding() {
        try {
            seeding.get()?.get()
        } catch (e: ExecutionException) {
            throw IOException("Restoring the handwriting models failed", e.cause ?: e)
        }
    }

    /** The languages whose models are not on the device. */
    @Throws(IOException::class)
    fun missing(): List<String> {
        awaitSeeding()
        val manager = RemoteModelManager.getInstance()
        return LANGUAGE_TAGS.filterNot { await(manager.isModelDownloaded(models.getValue(it))) }
    }

    /** Downloads what is missing, which is the one thing in the app that uses the network, and only the debug and profile builds can. */
    @Throws(IOException::class)
    fun download() {
        awaitSeeding()
        val manager = RemoteModelManager.getInstance()
        val conditions = DownloadConditions.Builder().build()
        for (tag in missing()) {
            await(manager.download(models.getValue(tag), conditions), DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        }
    }

    internal fun model(tag: String): DigitalInkRecognitionModel = models.getValue(tag)

    private const val DOWNLOAD_TIMEOUT_MINUTES = 10L
}

internal fun <T> await(task: Task<T>, timeout: Long = 30, unit: TimeUnit = TimeUnit.SECONDS): T = try {
    Tasks.await(task, timeout, unit)
} catch (e: ExecutionException) {
    throw IOException("ML Kit failed: ${e.cause?.message}", e.cause ?: e)
} catch (e: TimeoutException) {
    throw IOException("ML Kit timed out", e)
}

/**
 * Reads ink with ML Kit, in English and Spanish at once, and keeps the top guesses from both. Search
 * only has to find a word in one of them, so there is no need to work out each line's language.
 * Recognition itself runs on the device. Use from one background thread, and [close] when done.
 */
class MlKitRecognizer : HandwritingRecognizer, AutoCloseable {
    private var clients: List<DigitalInkRecognizer>? = null

    override val engineId = ENGINE_ID

    /** Checks each time, as models may be deleted while the app runs, and a failed check reads as not ready. */
    override val isReady: Boolean
        get() = try {
            InkModels.missing().isEmpty()
        } catch (_: IOException) {
            false
        }

    override fun recognize(line: InkLine): List<String> {
        val ink = line.toInk()
        val context = RecognitionContext.builder()
            .setPreContext("")
            .setWritingArea(WritingArea(line.bounds.widthOrMin(), line.bounds.heightOrMin()))
            .build()
        val guesses = LinkedHashSet<String>()
        for (client in clients()) {
            val result = try {
                await(client.recognize(ink, context))
            } catch (e: MlKitException) {
                throw IOException("Recognition failed", e)
            }
            result.candidates.take(GUESSES_PER_LANGUAGE).mapTo(guesses) { it.text }
        }
        return guesses.toList()
    }

    /**
     * Reads one written word, or a few, with the best guess of the two languages. [preContext] is the text before it.
     * Null if nothing was read. Scores of ML Kit are lower for a likelier guess; this assumes they compare across languages.
     */
    fun readWord(strokes: List<WordStroke>, preContext: String): String? {
        val ink = Ink.builder()
        var offsetMs = 0L
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (stroke in strokes) {
            val builder = Ink.Stroke.builder()
            var lastMs = offsetMs
            for (i in stroke.x.indices) {
                lastMs = offsetMs + stroke.timeMs[i]
                builder.addPoint(Ink.Point.create(stroke.x[i], stroke.y[i], lastMs))
                left = minOf(left, stroke.x[i])
                right = maxOf(right, stroke.x[i])
                top = minOf(top, stroke.y[i])
                bottom = maxOf(bottom, stroke.y[i])
            }
            ink.addStroke(builder.build())
            offsetMs = lastMs + PAUSE_BETWEEN_STROKES_MS
        }
        val context = RecognitionContext.builder()
            .setPreContext(preContext)
            .setWritingArea(WritingArea((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)))
            .build()
        val built = ink.build()
        var best: String? = null
        var bestScore = Float.MAX_VALUE
        for (client in clients()) {
            val result = try {
                await(client.recognize(built, context))
            } catch (e: MlKitException) {
                throw IOException("Recognition failed", e)
            }
            val top = result.candidates.firstOrNull() ?: continue
            val score = top.score ?: Float.MAX_VALUE
            if (best == null || score < bestScore) {
                best = top.text
                bestScore = score
            }
        }
        return best?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun clients() = clients ?: InkModels.LANGUAGE_TAGS.map { tag ->
        DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(InkModels.model(tag)).build())
    }.also { clients = it }

    override fun close() {
        clients?.forEach { it.close() }
        clients = null
    }

    private fun Bounds.widthOrMin() = width.coerceAtLeast(1f)
    private fun Bounds.heightOrMin() = height.coerceAtLeast(1f)

    private companion object {
        const val ENGINE_ID = "mlkit-ink-19/en-US+es-ES"

        /** More of these find more misread words. Past three they rarely add a word that is not noise. */
        const val GUESSES_PER_LANGUAGE = 3
    }
}

/** One stroke of a word being written: where the pen was, in the order it was, and when, from the stroke's start. */
class WordStroke(val x: FloatArray, val y: FloatArray, val timeMs: LongArray)

/**
 * Builds ML Kit's ink. Saved times count from the start of each stroke, while ML Kit expects one clock
 * across the line, so the strokes are laid out one after another with a typical pause between them.
 */
internal fun InkLine.toInk(): Ink {
    val ink = Ink.builder()
    var offsetMs = 0L
    for (stroke in strokes) {
        val builder = Ink.Stroke.builder()
        var lastMs = offsetMs
        for (i in 0 until stroke.size) {
            lastMs = offsetMs + stroke.timeOf(i)
            builder.addPoint(Ink.Point.create(stroke.x(i), stroke.y(i), lastMs))
        }
        ink.addStroke(builder.build())
        offsetMs = lastMs + PAUSE_BETWEEN_STROKES_MS
    }
    return ink.build()
}

private fun StrokePoints.timeOf(i: Int) = timeMs(i).toLong()

private const val PAUSE_BETWEEN_STROKES_MS = 100L
