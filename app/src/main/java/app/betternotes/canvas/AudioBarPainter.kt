package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.betternotes.core.model.AudioBar
import app.betternotes.core.model.AudioItem
import app.betternotes.ui.theme.CanvasColors

/** Plays, stops and seeks the recordings on the page. */
internal interface AudioPlayback {
    fun isPlaying(id: Long): Boolean

    /** How far through the playing recording is, from 0 to 1, or 0 when it is not playing. */
    fun fraction(id: Long): Float

    /** Starts the recording, or stops it if it is the one playing. */
    fun toggle(item: AudioItem)

    /** Moves the recording to [fraction] of its length, starting it if it was stopped. */
    fun seek(item: AudioItem, fraction: Float)
}

/** Draws a recording as a bar over the page: play button, waveform that fills as it plays, and the fold control. */
internal class AudioBarPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CanvasColors.AUDIO_FILL_LIGHT }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CanvasColors.AUDIO_EDGE_LIGHT
    }
    private val button = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CanvasColors.AUDIO_GLYPH }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CanvasColors.AUDIO_ARROW_LIGHT
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    /** [left] and [top] are where the bar starts on screen, and [scale] is pixels per point. */
    fun draw(canvas: Canvas, item: AudioItem, left: Float, top: Float, scale: Float, playing: Boolean, fraction: Float, wave: FloatArray?, dark: Boolean) {
        fill.color = if (dark) CanvasColors.AUDIO_FILL_DARK else CanvasColors.AUDIO_FILL_LIGHT
        edge.color = if (dark) CanvasColors.AUDIO_EDGE_DARK else CanvasColors.AUDIO_EDGE_LIGHT
        arrow.color = if (dark) CanvasColors.AUDIO_ARROW_DARK else CanvasColors.AUDIO_ARROW_LIGHT
        val frame = item.frame
        val width = frame.width * scale
        val height = frame.height * scale
        val radius = height / 2f
        rect.set(left, top, left + width, top + height)
        edge.strokeWidth = maxOf(1f, scale * 0.8f)
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, edge)

        // The play or stop button.
        val cx = left + radius
        val cy = top + radius
        canvas.drawCircle(cx, cy, radius * 0.8f, button)
        val g = radius * 0.32f
        if (playing) {
            canvas.drawRect(cx - g, cy - g, cx + g, cy + g, glyph)
        } else {
            path.reset()
            path.moveTo(cx - g * 0.7f, cy - g * 1.1f)
            path.lineTo(cx + g * 1.2f, cy)
            path.lineTo(cx - g * 0.7f, cy + g * 1.1f)
            path.close()
            canvas.drawPath(path, glyph)
        }

        arrow.strokeWidth = maxOf(1.5f, scale * 1.4f)
        if (!AudioBar.isOpen(frame)) {
            // A ">" beside the button.
            val ax = left + height + (width - height) / 2f
            val a = height * 0.14f
            path.reset()
            path.moveTo(ax - a * 0.6f, cy - a)
            path.lineTo(ax + a * 0.6f, cy)
            path.lineTo(ax - a * 0.6f, cy + a)
            canvas.drawPath(path, arrow)
            return
        }

        // A "v" in the top right corner.
        val corner = AudioBar.CORNER * scale
        val vx = left + width - corner / 2f - radius * 0.25f
        val vy = top + corner / 2f
        val a = corner * 0.2f
        path.reset()
        path.moveTo(vx - a, vy - a * 0.5f)
        path.lineTo(vx, vy + a * 0.6f)
        path.lineTo(vx + a, vy - a * 0.5f)
        canvas.drawPath(path, arrow)

        // The waveform, with what has played in the accent color.
        val start = left + height
        val end = left + width - AudioBar.WAVE_PADDING * scale
        val count = wave?.size ?: FLAT_BARS
        val step = (end - start) / count
        bar.strokeWidth = step * 0.55f
        val maxHalf = height * 0.34f
        for (i in 0 until count) {
            val x = start + (i + 0.5f) * step
            val level = wave?.get(i) ?: FLAT_LEVEL
            bar.color = if ((i + 0.5f) / count <= fraction) ACCENT else if (dark) CanvasColors.AUDIO_WAVE_IDLE_DARK else CanvasColors.AUDIO_WAVE_IDLE_LIGHT
            canvas.drawLine(x, cy - level * maxHalf, x, cy + level * maxHalf, bar)
        }
    }

    private companion object {
        const val ACCENT = CanvasColors.AUDIO_ACCENT
        const val FLAT_BARS = 48
        const val FLAT_LEVEL = 0.08f
    }
}
