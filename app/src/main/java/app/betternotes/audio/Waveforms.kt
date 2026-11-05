package app.betternotes.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Loudness along a recording, as [BUCKETS] values from 0 to 1, for drawing its bar. Each file is decoded once,
 * off the main thread, and the result is kept for the life of the process.
 */
internal object Waveforms {
    const val BUCKETS = 80
    private const val TAG = "Waveforms"
    private const val TIMEOUT_US = 10_000L
    private const val SAMPLE_STEP = 8
    private const val FLOOR = 0.06f

    private val cache = ConcurrentHashMap<String, FloatArray>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun get(assetId: String): FloatArray? = cache[assetId]

    /** Computes the waveform in the background, once per asset, then calls [done] on that thread. */
    fun request(assetId: String, file: File, done: () -> Unit) {
        if (cache.containsKey(assetId) || !pending.add(assetId)) return
        Thread {
            val result = try {
                decode(file)
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Could not read a recording's loudness", e)
                null
            } catch (e: RuntimeException) {
                Log.e(TAG, "Could not read a recording's loudness", e)
                null
            }
            // A recording that cannot be read still gets a flat line, so it is not tried again.
            cache[assetId] = result ?: FloatArray(BUCKETS) { FLOOR }
            pending.remove(assetId)
            done()
        }.apply { name = "waveform"; isDaemon = true }.start()
    }

    private fun decode(file: File): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs <= 0L) return null
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return null)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return run(codec, extractor, durationUs, format)
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun run(codec: MediaCodec, extractor: MediaExtractor, durationUs: Long, input: MediaFormat): FloatArray {
        val sum = FloatArray(BUCKETS)
        val count = IntArray(BUCKETS)
        val info = MediaCodec.BufferInfo()
        var rate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val changed = codec.outputFormat
                rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (out >= 0) {
                val samples = codec.getOutputBuffer(out)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                val total = info.size / 2
                var i = 0
                while (i < total) {
                    val timeUs = info.presentationTimeUs + i.toLong() * 1_000_000L / (rate.toLong() * channels)
                    val bucket = (timeUs * BUCKETS / durationUs).toInt().coerceIn(0, BUCKETS - 1)
                    sum[bucket] += abs(samples.get(info.offset / 2 + i).toInt()) / 32768f
                    count[bucket]++
                    i += SAMPLE_STEP
                }
                codec.releaseOutputBuffer(out, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        val levels = FloatArray(BUCKETS) { if (count[it] == 0) 0f else sum[it] / count[it] }
        val loudest = levels.max().coerceAtLeast(1e-4f)
        return FloatArray(BUCKETS) { (levels[it] / loudest).coerceAtLeast(FLOOR) }
    }
}
