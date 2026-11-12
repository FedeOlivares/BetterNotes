package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import app.betternotes.core.model.FillItem
import app.betternotes.core.tools.PatternMarks
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.ImageShape
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.LineStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.SHADOW_SOFTNESS
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TapePathItem
import app.betternotes.core.model.TapePattern
import app.betternotes.core.tools.TapeOptions
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextSpan
import app.betternotes.fonts.FontLibrary
import app.betternotes.ui.theme.ALPHA_SHIFT
import app.betternotes.ui.theme.CanvasColors
import app.betternotes.ui.theme.RGB_MASK
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

    /** The item whose text is being typed in a box over the page, which is not drawn under that box. Set from the main thread, read by the tile thread. */
    @Volatile
    var hiddenItemId = 0L

    fun paint(canvas: Canvas, item: PageItem) {
        if (item.id == hiddenItemId && item.id != 0L) return
        when (item) {
            is StrokeItem -> paintStroke(canvas, item)
            is ShapeItem -> inFrame(canvas, item.frame) { paintShape(canvas, item) }
            is TapePathItem -> paintTapePath(canvas, item)
            is TableItem -> inFrame(canvas, item.frame) { paintTable(canvas, item) }
            is TextItem -> inFrame(canvas, item.frame) {
                if (item.style.hasBackground) {
                    fill.color = item.style.backgroundColor
                    canvas.drawRect(0f, 0f, item.frame.width, item.frame.height, fill)
                }
                paintText(canvas, item.text, item.textSize, item.color, item.frame.width, item.frame.height, item.style, item.spans)
            }
            is StickyNoteItem -> paintSticky(canvas, item)
            is ImageItem -> inFrame(canvas, item.frame) { paintImage(canvas, item) }
            is VideoItem -> inFrame(canvas, item.frame) { paintVideo(canvas, item) }
            is AudioItem -> Unit
            is FillItem -> paintFill(canvas, item)
            is PathShapeItem -> paintPathShape(canvas, item)
        }
    }

    /** Corners joined by straight sides, or a smooth curve through the points; a closed one may be filled. */
    private fun paintPathShape(canvas: Canvas, item: PathShapeItem) {
        val outline = item.outline()
        path.rewind()
        path.moveTo(outline[0], outline[1])
        for (i in 2 until outline.size step 2) path.lineTo(outline[i], outline[i + 1])
        if (item.closed) path.close()
        val alpha = (item.opacity * OPAQUE).roundToInt()
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

    // A fill ends at the middle of the line beside it, which the line covers, so it needs nothing around it.
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPath = Path().apply { fillType = Path.FillType.EVEN_ODD }

    private val patternMarks = PatternMarks()
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
    private val clipArea = android.graphics.Rect()

    /**
     * A paint bucket fill: its loops as one path, filled with the even-odd rule so that a loop inside another is a hole. A pattern is drawn
     * over the color, clipped to the loops, from a grid that belongs to the page and only over what the canvas shows, so every tile,
     * thumbnail and export draws the same marks and a mark that reaches into the next tile is drawn in both.
     */
    private fun paintFill(canvas: Canvas, item: FillItem) {
        fillPath.rewind()
        for (loop in item.loops) {
            fillPath.moveTo(loop[0], loop[1])
            for (i in 2 until loop.size step 2) fillPath.lineTo(loop[i], loop[i + 1])
            fillPath.close()
        }
        fillPaint.color = item.color
        fillPaint.alpha = (Color.alpha(item.color) * item.opacity).toInt().coerceIn(0, 255)
        canvas.drawPath(fillPath, fillPaint)
        if (!item.pattern.isSolid) paintPattern(canvas, item)
    }

    private fun paintPattern(canvas: Canvas, item: FillItem) {
        val pattern = item.pattern
        canvas.withClip(fillPath) {
            // What can be seen: the fill's own box and what the canvas clips to, here the tile or the page.
            val box = item.bounds
            if (!getClipBounds(clipArea)) return@withClip
            val left = maxOf(box.left, clipArea.left.toFloat())
            val top = maxOf(box.top, clipArea.top.toFloat())
            val right = minOf(box.right, clipArea.right.toFloat())
            val bottom = minOf(box.bottom, clipArea.bottom.toFloat())
            if (right < left || bottom < top) return@withClip
            patternMarks.make(pattern, left, top, right, bottom)
            markPaint.color = pattern.color
            markPaint.alpha = (Color.alpha(pattern.color) * item.opacity).toInt().coerceIn(0, 255)
            markPaint.strokeWidth = patternMarks.thickness
            if (patternMarks.dotCount > 0) {
                markPaint.strokeCap = Paint.Cap.ROUND
                drawPoints(patternMarks.dots, 0, patternMarks.dotCount, markPaint)
            }
            if (patternMarks.lineCount > 0) {
                markPaint.strokeCap = Paint.Cap.BUTT
                drawLines(patternMarks.lines, 0, patternMarks.lineCount, markPaint)
            }
        }
    }

    /** Runs [draw] with the frame's top-left corner at the origin, turned by its rotation. */
    private inline fun inFrame(canvas: Canvas, frame: Frame, draw: () -> Unit) {
        canvas.withRotation(frame.rotation, frame.centerX, frame.centerY) {
            withTranslation(frame.left, frame.top) { draw() }
        }
    }

    private val mirror = android.graphics.Matrix()

    private fun paintShape(canvas: Canvas, item: ShapeItem) {
        val width = item.frame.width
        val height = item.frame.height
        ShapePaths.build(path, item.kind, width, height, item.cornerRadius, item.tipLength, item.tipWidth)
        if (item.mirrored && item.kind != ShapeKind.TAPE) {
            mirror.setScale(-1f, 1f, width / 2f, 0f)
            path.transform(mirror)
        }
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
            fill.color = CanvasColors.ITEM_SHADOW
            fill.alpha = (shadow.opacity * alpha).roundToInt().coerceIn(0, 255)
            canvas.save()
            // Only what falls outside the strip shows, so see-through tape is not darkened by it.
            canvas.clipOutPath(path)
            canvas.translate(dx, dy)
            canvas.drawPath(path, fill)
            // A second, slightly fatter copy at lower strength softens the edge.
            line.pathEffect = null
            line.style = Paint.Style.STROKE
            line.color = CanvasColors.ITEM_SHADOW
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

    private val geometry = TapePathGeometry()
    private val spot = FloatArray(2)
    private val strip = Path()
    private val band = Path()

    /** Tape laid along a path: its shadow, color or warning stripes, and printed marks, all following the strip's own direction. */
    private fun paintTapePath(canvas: Canvas, item: TapePathItem) {
        geometry.build(item.points)
        if (geometry.count < 2) return
        val half = item.width / 2f
        stripOutline(half)
        val alpha = (item.opacity * OPAQUE).roundToInt()
        val shadow = item.shadow
        if (shadow.isVisible) {
            val radians = Math.toRadians(shadow.angleDegrees.toDouble())
            fill.color = CanvasColors.ITEM_SHADOW
            fill.alpha = (shadow.opacity * alpha).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.clipOutPath(strip)
            canvas.translate((shadow.distance * Math.cos(radians)).toFloat(), (shadow.distance * Math.sin(radians)).toFloat())
            canvas.drawPath(strip, fill)
            line.pathEffect = null
            line.style = Paint.Style.STROKE
            line.color = CanvasColors.ITEM_SHADOW
            line.strokeWidth = SHADOW_SOFTNESS
            line.alpha = (shadow.opacity * alpha * 0.4f).roundToInt().coerceIn(0, 255)
            canvas.drawPath(strip, line)
            canvas.restore()
        }
        val box = item.bounds
        val layer = canvas.saveLayerAlpha(box.left, box.top, box.right, box.bottom, alpha)
        canvas.withClip(strip) {
            if (item.fillColor == TapeOptions.WARNING_FILL) {
                fill.color = TapeOptions.WARNING_FILL
                drawPath(strip, fill)
                fill.color = TapeOptions.WARNING_STRIPE
                val stripe = item.width * WARNING_STRIPE_RATIO
                var a = -item.width
                while (a < geometry.length) {
                    // A black band slanted across the strip, bent with it: oblique to the direction of travel at that place.
                    band.rewind()
                    for (k in 0..BAND_STEPS) {
                        val u = -half + item.width * k / BAND_STEPS
                        geometry.place(a + (u + half), u, spot)
                        if (k == 0) band.moveTo(spot[0], spot[1]) else band.lineTo(spot[0], spot[1])
                    }
                    for (k in BAND_STEPS downTo 0) {
                        val u = -half + item.width * k / BAND_STEPS
                        geometry.place(a + stripe + (u + half), u, spot)
                        band.lineTo(spot[0], spot[1])
                    }
                    band.close()
                    drawPath(band, fill)
                    a += 2f * stripe
                }
            } else {
                fill.color = item.fillColor
                drawPath(strip, fill)
            }
            if (item.pattern != TapePattern.PLAIN) paintPathPattern(this, item.pattern, item.width)
        }
        canvas.restoreToCount(layer)
    }

    /** The outline of the strip: along one side and back along the other, with both ends torn in a zigzag like straight tape. */
    private fun stripOutline(half: Float) {
        strip.rewind()
        val length = geometry.length
        val width = half * 2f
        val teeth = TAPE_TEETH
        val bite = minOf(width / teeth, length / 4f)
        var s = 0f
        while (true) {
            geometry.place(s, half, spot)
            if (s == 0f) strip.moveTo(spot[0], spot[1]) else strip.lineTo(spot[0], spot[1])
            if (s >= length) break
            s = minOf(s + OUTLINE_STEP, length)
        }
        // The far end, torn: every other point is pulled back along the strip.
        for (i in 1..teeth * 2) {
            geometry.place(if (i % 2 == 1) length - bite else length, half - width * i / (teeth * 2), spot)
            strip.lineTo(spot[0], spot[1])
        }
        while (true) {
            geometry.place(s, -half, spot)
            strip.lineTo(spot[0], spot[1])
            if (s <= 0f) break
            s = maxOf(s - OUTLINE_STEP, 0f)
        }
        // The near end, torn the same way.
        for (i in 1..teeth * 2) {
            geometry.place(if (i % 2 == 1) bite else 0f, -half + width * i / (teeth * 2), spot)
            strip.lineTo(spot[0], spot[1])
        }
        strip.close()
    }

    /** White marks laid out along the strip, in its own frame, so they bend with it. */
    private fun paintPathPattern(canvas: Canvas, pattern: TapePattern, width: Float) {
        mark.color = CanvasColors.TAPE_PATTERN
        mark.alpha = PATTERN_ALPHA
        val half = width / 2f
        val gap = maxOf(width * PATTERN_GAP, MIN_PATTERN_GAP)
        val length = geometry.length
        when (pattern) {
            TapePattern.DOTS -> {
                mark.style = Paint.Style.FILL
                val radius = maxOf(gap * 0.14f, 0.5f)
                var row = 0
                var u = -half + gap / 2f
                while (u < half) {
                    var s = if (row % 2 == 0) gap / 2f else gap
                    while (s < length) {
                        geometry.place(s, u, spot)
                        canvas.drawCircle(spot[0], spot[1], radius, mark)
                        s += gap
                    }
                    u += gap * 0.6f
                    row++
                }
            }
            TapePattern.LINES, TapePattern.CROSSHATCH -> {
                mark.style = Paint.Style.STROKE
                mark.strokeWidth = maxOf(gap * 0.09f, 0.4f)
                var a = -width
                while (a < length) {
                    obliqueLine(canvas, a, half, forward = true)
                    if (pattern == TapePattern.CROSSHATCH) obliqueLine(canvas, a, half, forward = false)
                    a += gap
                }
            }
            TapePattern.PLAIN -> Unit
        }
    }

    /** A line slanted at 45 degrees across the strip from distance [a], bent with the strip. */
    private fun obliqueLine(canvas: Canvas, a: Float, half: Float, forward: Boolean) {
        band.rewind()
        for (k in 0..BAND_STEPS) {
            val u = -half + 2f * half * k / BAND_STEPS
            val s = if (forward) a + (u + half) else a + (half - u)
            geometry.place(s, u, spot)
            if (k == 0) band.moveTo(spot[0], spot[1]) else band.lineTo(spot[0], spot[1])
        }
        canvas.drawPath(band, mark)
    }

    /** White marks over a strip: tiny dots, thin slanted lines, or two sets of lines crossing. */
    private fun paintPattern(canvas: Canvas, pattern: TapePattern, width: Float, height: Float) {
        mark.color = CanvasColors.TAPE_PATTERN
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
        fill.color = if (item.headerColor != 0) item.headerColor else CanvasColors.TABLE_HEADER
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
                            CanvasColors.TABLE_CELL_TEXT,
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

    private fun paintText(
        canvas: Canvas, value: String, size: Float, color: Int, width: Float, height: Float, style: TextStyle,
        spans: List<TextSpan> = emptyList(),
    ) {
        if (value.isEmpty() || width <= 0f) return
        // Laid out at a larger size and scaled down, since text hints badly at sizes as small as points.
        text.color = color
        text.textSize = size * TEXT_LAYOUT_SCALE
        // Parts that look different are marked on the text, which then needs a plain paint.
        val spanned = spans.isNotEmpty()
        text.isUnderlineText = !spanned && style.underline
        text.isStrikeThruText = !spanned && style.strikethrough
        text.typeface = when {
            spanned -> Typeface.DEFAULT
            else -> FontLibrary.face(style.font, style.bold, style.italic) ?: when {
                style.bold && style.italic -> BOLD_ITALIC
                style.bold -> Typeface.DEFAULT_BOLD
                style.italic -> ITALIC
                else -> Typeface.DEFAULT
            }
        }
        val wide = (width * TEXT_LAYOUT_SCALE).roundToInt().coerceAtLeast(1)
        val plain = if (spanned) spannedText(value, size * TEXT_LAYOUT_SCALE, style, spans.map { it.scaled(TEXT_LAYOUT_SCALE) }) else value
        val shown = withListMarks(plain, style, color, size * TEXT_LAYOUT_SCALE, text, wide)
        val layout = StaticLayout.Builder
            .obtain(shown, 0, value.length, text, wide)
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

    /** The fold's shade, as see-through as the note it is on. */
    private fun shadeOver(note: Int): Int {
        val alpha = (CanvasColors.NOTE_FOLD_SHADE ushr ALPHA_SHIFT) * (note ushr ALPHA_SHIFT) / 255
        return (alpha shl ALPHA_SHIFT) or (CanvasColors.NOTE_FOLD_SHADE and RGB_MASK)
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
            fill.color = shadeOver(item.color)
            canvas.drawPath(path, fill)
            if (item.isOpen) {
                // What is in a note is drawn whole, also where a line or tape runs past the edge. See StickyNoteItem.paintBounds.
                for (inner in item.items) paint(canvas, inner)
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
            bitmapPaint.alpha = OPAQUE.toInt()
            canvas.drawBitmap(poster, null, box, bitmapPaint)
        } else {
            fill.color = CanvasColors.VIDEO_BACK
            canvas.drawRect(box, fill)
        }
        val radius = min(min(width, height) / 2f, VIDEO_BUTTON_PT)
        fill.color = CanvasColors.VIDEO_BUTTON
        canvas.drawCircle(width / 2f, height / 2f, radius, fill)
        fill.color = CanvasColors.VIDEO_TRIANGLE
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
        bitmapPaint.alpha = (item.opacity.coerceIn(0f, 1f) * OPAQUE + 0.5f).toInt()
        canvas.withClip(path) {
            if (bitmap != null) {
                drawBitmap(bitmap, null, box, bitmapPaint)
            } else {
                fill.color = CanvasColors.IMAGE_PLACEHOLDER
                drawRect(box, fill)
            }
        }
        if (item.borderWidth > 0f) {
            applyLine(item.borderColor, item.borderWidth, LineStyle.Solid)
            line.alpha = (line.alpha * item.opacity.coerceIn(0f, 1f) + 0.5f).toInt()
            canvas.drawPath(path, line)
        }
    }

    internal companion object {
        const val OPAQUE = 255f
        const val WARNING_STRIPE_RATIO = 0.8f

        /** The spacing of a tape's printed marks as a part of its thickness, and the least spacing in points. */
        const val PATTERN_GAP = 0.3f
        const val MIN_PATTERN_GAP = 3f
        const val PATTERN_ALPHA = 210
        const val BAND_STEPS = 6
        const val TAPE_TEETH = 5
        const val OUTLINE_STEP = 1.5f
        const val DOT_LENGTH = 0.01f
        const val CELL_PADDING = TableItem.CELL_PADDING
        const val TEXT_LAYOUT_SCALE = 8f
        const val FOLD_OF_NOTE = 0.12f
        const val FOLD_TIP = 0.2f

        /** The largest the play button gets, and how far from the video's middle a tap starts it. */
        const val VIDEO_BUTTON_PT = 22f
        val CELL_STYLE = TextStyle()
        val ITALIC: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val BOLD_ITALIC: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
    }
}


internal fun TextSpan.scaled(by: Float) = look.size?.let { copy(look = look.copy(size = it * by)) } ?: this
