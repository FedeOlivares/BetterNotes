package app.betternotes.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VerticalAlign
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints every item except ink, in page coordinates: the canvas is already scaled to the page. Ink goes
 * through its renderer. Images and notes' ink are added by [paint]'s callers as those tools arrive.
 */
internal class ItemPainter(private val paintStroke: (Canvas, StrokeItem) -> Unit = { _, _ -> }) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun paint(canvas: Canvas, item: PageItem) {
        when (item) {
            is StrokeItem -> paintStroke(canvas, item)
            is ShapeItem -> inFrame(canvas, item.frame) { paintShape(canvas, item) }
            is TableItem -> inFrame(canvas, item.frame) { paintTable(canvas, item) }
            is TextItem -> inFrame(canvas, item.frame) {
                paintText(canvas, item.text, item.textSize, item.color, item.frame.width, item.frame.height, item.style)
            }
            is StickyNoteItem -> paintSticky(canvas, item)
            is ImageItem -> inFrame(canvas, item.frame) { paintImagePlaceholder(canvas, item) }
            is AudioItem -> inFrame(canvas, item.frame) { paintAudio(canvas, item) }
        }
    }

    /** Runs [draw] with the frame's top-left corner at the origin, turned by its rotation. */
    private inline fun inFrame(canvas: Canvas, frame: Frame, draw: () -> Unit) {
        canvas.withRotation(frame.rotation, frame.centerX, frame.centerY) {
            withTranslation(frame.left, frame.top) { draw() }
        }
    }

    private fun paintShape(canvas: Canvas, item: ShapeItem) {
        val width = item.frame.width
        val height = item.frame.height
        ShapePaths.build(path, item.kind, width, height, item.cornerRadius)
        val alpha = (item.opacity * OPAQUE).roundToInt()
        if (item.isFilled) {
            fill.color = item.fillColor
            fill.alpha = fill.alpha * alpha / OPAQUE.toInt()
            canvas.drawPath(path, fill)
        }
        applyLine(item.strokeColor, item.strokeWidth, item.line)
        line.alpha = line.alpha * alpha / OPAQUE.toInt()
        canvas.drawPath(path, line)
        line.pathEffect = null
    }

    private fun applyLine(color: Int, width: Float, style: LineStyle) {
        line.color = color
        line.strokeWidth = width
        line.strokeCap = if (style.kind == LineKind.DOTTED) Paint.Cap.ROUND else Paint.Cap.BUTT
        line.pathEffect = when (style.kind) {
            LineKind.SOLID -> null
            LineKind.DASHED -> DashPathEffect(floatArrayOf(style.dashLength, style.gapLength), 0f)
            // A round cap on a dash of almost nothing is a dot as wide as the line.
            LineKind.DOTTED -> DashPathEffect(floatArrayOf(DOT_LENGTH, style.gapLength + width), 0f)
        }
    }

    private fun paintTable(canvas: Canvas, item: TableItem) {
        val width = item.frame.width
        val height = item.frame.height
        val columnTotal = item.columnWeights.sum()
        val rowTotal = item.rowWeights.sum()
        if (item.fillColor ushr ALPHA_SHIFT != 0) {
            fill.color = item.fillColor
            canvas.drawRect(0f, 0f, width, height, fill)
        }
        fill.color = TABLE_HEADER
        if (item.headerRow) canvas.drawRect(0f, 0f, width, item.rowWeights[0] / rowTotal * height, fill)
        if (item.headerColumn) canvas.drawRect(0f, 0f, item.columnWeights[0] / columnTotal * width, height, fill)
        applyLine(item.lineColor, item.lineWidth, LineStyle.Solid)
        canvas.drawRect(0f, 0f, width, height, line)
        var y = 0f
        for ((row, rowWeight) in item.rowWeights.withIndex()) {
            val rowHeight = rowWeight / rowTotal * height
            var x = 0f
            for ((column, columnWeight) in item.columnWeights.withIndex()) {
                val columnWidth = columnWeight / columnTotal * width
                val cell = item.cells[row * item.columnWeights.size + column]
                if (cell.isNotEmpty()) {
                    canvas.withTranslation(x + CELL_PADDING, y + CELL_PADDING) {
                        paintText(
                            this,
                            cell,
                            item.textSize,
                            CELL_TEXT,
                            columnWidth - 2 * CELL_PADDING,
                            rowHeight - 2 * CELL_PADDING,
                            CELL_STYLE,
                        )
                    }
                }
                x += columnWidth
                if (column < item.columnWeights.lastIndex) canvas.drawLine(x, 0f, x, height, line)
            }
            y += rowHeight
            if (row < item.rowWeights.lastIndex) canvas.drawLine(0f, y, width, y, line)
        }
    }

    private fun paintText(canvas: Canvas, value: String, size: Float, color: Int, width: Float, height: Float, style: TextStyle) {
        if (value.isEmpty() || width <= 0f) return
        // Laid out at a larger size and scaled down, since text hints badly at sizes as small as points.
        text.color = color
        text.textSize = size * TEXT_LAYOUT_SCALE
        text.isUnderlineText = style.underline
        text.isStrikeThruText = style.strikethrough
        text.typeface = when {
            style.bold && style.italic -> BOLD_ITALIC
            style.bold -> Typeface.DEFAULT_BOLD
            style.italic -> ITALIC
            else -> Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder
            .obtain(value, 0, value.length, text, (width * TEXT_LAYOUT_SCALE).roundToInt().coerceAtLeast(1))
            .setAlignment(
                when (style.align) {
                    HorizontalAlign.START -> Layout.Alignment.ALIGN_NORMAL
                    HorizontalAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    HorizontalAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
                },
            )
            .build()
        val laidOut = layout.height / TEXT_LAYOUT_SCALE
        val top = when (style.verticalAlign) {
            VerticalAlign.TOP -> 0f
            VerticalAlign.MIDDLE -> (height - laidOut) / 2f
            VerticalAlign.BOTTOM -> height - laidOut
        }
        canvas.withTranslation(0f, top) {
            scale(1f / TEXT_LAYOUT_SCALE, 1f / TEXT_LAYOUT_SCALE)
            layout.draw(this)
        }
    }

    private fun paintSticky(canvas: Canvas, item: StickyNoteItem) {
        val frame = item.visibleFrame
        inFrame(canvas, frame) {
            // A square with its lower right corner folded over.
            val fold = min(frame.width, frame.height) * FOLD_OF_NOTE
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(frame.width, 0f)
            path.lineTo(frame.width, frame.height - fold / 2f)
            path.lineTo(frame.width - fold, frame.height)
            path.lineTo(0f, frame.height)
            path.close()
            fill.color = item.color
            canvas.drawPath(path, fill)
            path.reset()
            path.moveTo(frame.width, frame.height - fold / 2f)
            path.lineTo(frame.width - fold, frame.height)
            path.lineTo(frame.width - fold * FOLD_TIP, frame.height - fold)
            path.close()
            fill.color = FOLD_SHADE
            canvas.drawPath(path, fill)
            if (item.isOpen) {
                // Everything in a note stays in it.
                canvas.withClip(0f, 0f, frame.width, frame.height) {
                    for (inner in item.items) paint(this, inner)
                }
            }
        }
    }

    private fun paintImagePlaceholder(canvas: Canvas, item: ImageItem) {
        fill.color = IMAGE_PLACEHOLDER
        canvas.drawRect(0f, 0f, item.frame.width, item.frame.height, fill)
    }

    private fun paintAudio(canvas: Canvas, item: AudioItem) {
        fill.color = AUDIO_COLOR
        val radius = minOf(item.frame.width, item.frame.height) / 2f
        canvas.drawCircle(item.frame.width / 2f, item.frame.height / 2f, radius, fill)
    }

    private companion object {
        const val OPAQUE = 255f
        const val DOT_LENGTH = 0.01f
        const val CELL_PADDING = 3f
        const val TEXT_LAYOUT_SCALE = 8f
        const val FOLD_OF_NOTE = 0.05f
        const val FOLD_TIP = 0.2f
        const val FOLD_SHADE = 0x40FFFFFF
        const val ALPHA_SHIFT = 24
        const val TABLE_HEADER = 0x55808080
        const val CELL_TEXT = 0xFF1E1F24.toInt()
        val CELL_STYLE = TextStyle()
        val ITALIC: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val BOLD_ITALIC: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        const val IMAGE_PLACEHOLDER = 0x22000000
        const val AUDIO_COLOR = 0xFF5F9FFB.toInt()
    }
}
