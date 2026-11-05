package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import app.betternotes.core.model.VideoItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.ImageShape
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.SHADOW_SOFTNESS
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TapePattern
import app.betternotes.core.tools.TapeOptions
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
internal class ItemPainter(
    private val image: (assetId: String) -> Bitmap? = { null },
    private val paintStroke: (Canvas, StrokeItem) -> Unit = { _, _ -> },
) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val box = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val stripes = Path()
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG)

    fun paint(canvas: Canvas, item: PageItem) {
        when (item) {
            is StrokeItem -> paintStroke(canvas, item)
            is ShapeItem -> inFrame(canvas, item.frame) { paintShape(canvas, item) }
            is TableItem -> inFrame(canvas, item.frame) { paintTable(canvas, item) }
            is TextItem -> inFrame(canvas, item.frame) {
                paintText(canvas, item.text, item.textSize, item.color, item.frame.width, item.frame.height, item.style)
            }
            is StickyNoteItem -> paintSticky(canvas, item)
            is ImageItem -> inFrame(canvas, item.frame) { paintImage(canvas, item) }
            is VideoItem -> inFrame(canvas, item.frame) { paintVideo(canvas, item) }
            is AudioItem -> Unit
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
        ShapePaths.build(path, item.kind, width, height, item.cornerRadius, item.tipLength, item.tipWidth)
        val alpha = (item.opacity * OPAQUE).roundToInt()
        if (item.kind == ShapeKind.TAPE) {
            paintTape(canvas, item)
            return
        }
        if (item.isFilled) {
            fill.color = item.fillColor
            fill.alpha = fill.alpha * alpha / OPAQUE.toInt()
            canvas.drawPath(path, fill)
        }
        if (item.strokeWidth <= 0f) return
        applyLine(item.strokeColor, item.strokeWidth, item.line)
        line.alpha = line.alpha * alpha / OPAQUE.toInt()
        canvas.drawPath(path, line)
        line.pathEffect = null
    }

    /** A strip of tape: its shadow, its color or warning stripes, and what is printed on it in white. */
    private fun paintTape(canvas: Canvas, item: ShapeItem) {
        val width = item.frame.width
        val height = item.frame.height
        val alpha = (item.opacity * OPAQUE).roundToInt()
        val shadow = item.shadow
        if (shadow.isVisible) {
            // The shadow's angle is on the page, so it is turned back by the strip's own rotation.
            val radians = Math.toRadians((shadow.angleDegrees - item.frame.rotation).toDouble())
            val dx = (shadow.distance * Math.cos(radians)).toFloat()
            val dy = (shadow.distance * Math.sin(radians)).toFloat()
            fill.color = 0xFF000000.toInt()
            fill.alpha = (shadow.opacity * alpha).roundToInt().coerceIn(0, 255)
            canvas.save()
            // Only what falls outside the strip shows, so see-through tape is not darkened by it.
            canvas.clipOutPath(path)
            canvas.translate(dx, dy)
            canvas.drawPath(path, fill)
            // A second, slightly fatter copy at lower strength softens the edge.
            line.pathEffect = null
            line.style = Paint.Style.STROKE
            line.color = 0xFF000000.toInt()
            line.strokeWidth = SHADOW_SOFTNESS
            line.alpha = (shadow.opacity * alpha * 0.4f).roundToInt().coerceIn(0, 255)
            canvas.drawPath(path, line)
            canvas.restore()
        }
        // The strip is drawn whole and then made as see-through as asked, so its stripes and marks do not show through each other.
        val layer = canvas.saveLayerAlpha(-SHADOW_SOFTNESS, -SHADOW_SOFTNESS, width + SHADOW_SOFTNESS, height + SHADOW_SOFTNESS, alpha)
        if (item.fillColor == TapeOptions.WARNING_FILL) {
            paintWarningTape(canvas, width, height)
        } else {
            fill.color = item.fillColor
            canvas.drawPath(path, fill)
        }
        if (item.pattern != TapePattern.PLAIN) {
            canvas.withClip(path) { paintPattern(this, item.pattern, width, height) }
        }
        canvas.restoreToCount(layer)
    }

    /** White marks over a strip: tiny dots, thin slanted lines, or two sets of lines crossing. */
    private fun paintPattern(canvas: Canvas, pattern: TapePattern, width: Float, height: Float) {
        mark.color = 0xFFFFFFFF.toInt()
        mark.alpha = PATTERN_ALPHA
        val gap = maxOf(height * PATTERN_GAP, MIN_PATTERN_GAP)
        when (pattern) {
            TapePattern.DOTS -> {
                mark.style = Paint.Style.FILL
                val radius = maxOf(gap * 0.14f, 0.5f)
                var row = 0
                var y = gap / 2f
                while (y < height) {
                    var x = if (row % 2 == 0) gap / 2f else gap
                    while (x < width) {
                        canvas.drawCircle(x, y, radius, mark)
                        x += gap
                    }
                    y += gap * 0.6f
                    row++
                }
            }
            TapePattern.LINES, TapePattern.CROSSHATCH -> {
                mark.style = Paint.Style.STROKE
                mark.strokeWidth = maxOf(gap * 0.09f, 0.4f)
                var x = -height
                while (x < width) {
                    canvas.drawLine(x, height, x + height, 0f, mark)
                    if (pattern == TapePattern.CROSSHATCH) canvas.drawLine(x, 0f, x + height, height, mark)
                    x += gap
                }
            }
            TapePattern.PLAIN -> Unit
        }
    }

    /** Yellow with red stripes slanted across it, like police tape, clipped to the torn strip. */
    private fun paintWarningTape(canvas: Canvas, width: Float, height: Float) {
        canvas.withClip(path) {
            fill.color = TapeOptions.WARNING_FILL
            drawRect(0f, 0f, width, height, fill)
            fill.color = TapeOptions.WARNING_STRIPE
            val stripe = height * WARNING_STRIPE_RATIO
            var x = -height
            while (x < width) {
                stripes.rewind()
                stripes.moveTo(x, height)
                stripes.lineTo(x + stripe, height)
                stripes.lineTo(x + stripe + height, 0f)
                stripes.lineTo(x + height, 0f)
                stripes.close()
                drawPath(stripes, fill)
                x += stripe * 2f
            }
        }
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

    /** The poster, with a play button in the middle that starts it. */
    private fun paintVideo(canvas: Canvas, item: VideoItem) {
        val width = item.frame.width
        val height = item.frame.height
        box.set(0f, 0f, width, height)
        val poster = image(item.posterAssetId)
        if (poster != null) {
            canvas.drawBitmap(poster, null, box, bitmapPaint)
        } else {
            fill.color = VIDEO_BACK
            canvas.drawRect(box, fill)
        }
        val radius = min(min(width, height) / 2f, VIDEO_BUTTON_PT)
        fill.color = VIDEO_BUTTON
        canvas.drawCircle(width / 2f, height / 2f, radius, fill)
        fill.color = VIDEO_TRIANGLE
        path.reset()
        val tip = radius * 0.5f
        path.moveTo(width / 2f - tip * 0.7f, height / 2f - tip)
        path.lineTo(width / 2f + tip, height / 2f)
        path.lineTo(width / 2f - tip * 0.7f, height / 2f + tip)
        path.close()
        canvas.drawPath(path, fill)
    }

    /** Shows a tint until the picture is decoded, which then repaints it. */
    private fun paintImage(canvas: Canvas, item: ImageItem) {
        val width = item.frame.width
        val height = item.frame.height
        box.set(0f, 0f, width, height)
        path.reset()
        if (item.shape == ImageShape.ELLIPSE) {
            path.addOval(box, Path.Direction.CW)
        } else {
            path.addRoundRect(box, item.cornerRadius, item.cornerRadius, Path.Direction.CW)
        }
        val bitmap = image(item.assetId)
        canvas.withClip(path) {
            if (bitmap != null) {
                drawBitmap(bitmap, null, box, bitmapPaint)
            } else {
                fill.color = IMAGE_PLACEHOLDER
                drawRect(box, fill)
            }
        }
        if (item.borderWidth > 0f) {
            applyLine(item.borderColor, item.borderWidth, LineStyle.Solid)
            canvas.drawPath(path, line)
        }
    }

    private companion object {
        const val OPAQUE = 255f
        const val WARNING_STRIPE_RATIO = 0.8f

        /** The spacing of a tape's printed marks as a part of its thickness, and the least spacing in points. */
        const val PATTERN_GAP = 0.3f
        const val MIN_PATTERN_GAP = 3f
        const val PATTERN_ALPHA = 210
        const val DOT_LENGTH = 0.01f
        const val CELL_PADDING = 3f
        const val TEXT_LAYOUT_SCALE = 8f
        const val FOLD_OF_NOTE = 0.12f
        const val FOLD_TIP = 0.2f
        const val FOLD_SHADE = 0x40FFFFFF
        const val ALPHA_SHIFT = 24
        const val VIDEO_BACK = 0xFF25282D.toInt()
        const val VIDEO_BUTTON = 0xB3000000.toInt()
        const val VIDEO_TRIANGLE = 0xFFFFFFFF.toInt()

        /** The largest the play button gets, and how far from the video's middle a tap starts it. */
        const val VIDEO_BUTTON_PT = 22f
        const val TABLE_HEADER = 0x55808080
        const val CELL_TEXT = 0xFF1E1F24.toInt()
        val CELL_STYLE = TextStyle()
        val ITALIC: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val BOLD_ITALIC: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        const val IMAGE_PLACEHOLDER = 0x22000000
    }
}
