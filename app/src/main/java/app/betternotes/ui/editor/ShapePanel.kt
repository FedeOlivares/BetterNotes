package app.betternotes.ui.editor

import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.canvas.ShapePaths
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/** In the order of the screenshot's grid, six to a row. */
private val SHAPE_GRID = listOf(
    ShapeKind.ELLIPSE, ShapeKind.TRIANGLE, ShapeKind.RECTANGLE, ShapeKind.DIAMOND, ShapeKind.PARALLELOGRAM, ShapeKind.TRAPEZOID,
    ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.BLOCK_ARROW, ShapeKind.CURVE, ShapeKind.PENTAGON, ShapeKind.HEXAGON,
    ShapeKind.PIE, ShapeKind.ARC, ShapeKind.STAR, ShapeKind.SPHERE, ShapeKind.PYRAMID, ShapeKind.CYLINDER,
)
private const val SHAPES_PER_ROW = 6

/** The shapes' row: recent shapes, three widths, three border colors, the fill, and the way into the panel. */
@Composable
internal fun ShapeRow(state: EditorState, options: ShapeOptions, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Pill(modifier) {
        for (kind in options.recentKinds) {
            ToolbarCellBox(onClick = { state.updateShape { it.withKind(kind) } }) {
                ShapeGlyph(kind, colors.icon)
                if (kind == options.kind) SelectedDot()
            }
        }
        ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.panel_more_shapes)) { state.panelOpen = true }
        PillDivider()
        options.widths.forEachIndexed { slot, width ->
            ToolbarCellBox(onClick = { state.updateShape { it.copy(selectedWidth = slot) } }) {
                WidthGlyph(width)
                if (slot == options.selectedWidth) SelectedDot()
            }
        }
        PillDivider()
        options.colors.forEachIndexed { slot, color ->
            ToolbarCellBox(onClick = { state.updateShape { it.copy(selectedColor = slot) } }) {
                ColorRing(color)
                if (slot == options.selectedColor) SelectedDot()
            }
        }
        PillDivider()
        ToolbarCellBox(onClick = { state.tapShapeFill() }) { FillSwatch(options.fillColor) }
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

@Composable
internal fun ShapePanel(state: EditorState, options: ShapeOptions, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val path = remember { Path() }
    PanelSheet(stringResource(R.string.tool_shape), onClose = { state.panelOpen = false }, modifier = modifier) {
        DottedPreview {
            val side = PREVIEW_SIZE_DP.dp.toPx()
            val strokeWidth = (options.width * PREVIEW_DP_PER_WIDTH).dp.toPx().coerceAtMost(side / 4f)
            ShapePaths.build(path, options.kind, side, side, options.cornerRadius * PREVIEW_DP_PER_RADIUS * density)
            translate(center.x - side / 2f, center.y - side / 2f) {
                val outline = path.asComposePath()
                if (options.fillColor ushr 24 != 0) drawPath(outline, Color(options.fillColor), style = Fill)
                drawPath(
                    outline,
                    Color(options.color),
                    style = Stroke(
                        strokeWidth,
                        cap = if (options.line == LineKind.DOTTED) StrokeCap.Round else StrokeCap.Butt,
                        join = StrokeJoin.Miter,
                        pathEffect = when (options.line) {
                            LineKind.SOLID -> null
                            LineKind.DASHED -> PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 3f, strokeWidth * 2f))
                            LineKind.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(0.1f, strokeWidth * 2f))
                        },
                    ),
                )
            }
        }
        for (row in SHAPE_GRID.chunked(SHAPES_PER_ROW)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 8.dp, vertical = 2.dp),
            ) {
                for (kind in row) {
                    val selected = kind == options.kind
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.accentContainer else Color.Transparent)
                            .clickable(role = Role.RadioButton) { state.updateShape { it.withKind(kind) } },
                    ) {
                        ShapeGlyph(kind, if (selected) colors.accent else colors.icon)
                    }
                }
            }
        }
        LabeledRow(R.string.panel_border_width) { ValuePill { ValueText(options.width.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - 6.dp)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) {
                state.updateShape { it.withWidth(it.width - 1) }
            }
            PanelSlider(
                fraction = (options.width - ShapeOptions.MIN_WIDTH).toFloat() / (ShapeOptions.MAX_WIDTH - ShapeOptions.MIN_WIDTH),
                onChange = { f ->
                    val range = ShapeOptions.MAX_WIDTH - ShapeOptions.MIN_WIDTH
                    state.updateShape { it.withWidth(ShapeOptions.MIN_WIDTH + (f * range).roundToInt()) }
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) {
                state.updateShape { it.withWidth(it.width + 1) }
            }
        }
        PanelDivider()
        LabeledRow(R.string.panel_line_type) {
            ValuePill(onClick = { state.updateShape { it.copy(line = it.line.next()) } }) {
                LineTypeGlyph(options.line, colors.icon)
            }
        }
        if (options.line != LineKind.SOLID) {
            LabeledRow(R.string.panel_dash_size) { ValuePill { ValueText(options.dashLength.roundToInt().toString()) } }
            PanelSlider(
                fraction = (options.dashLength - ShapeOptions.MIN_DASH) / (ShapeOptions.MAX_DASH - ShapeOptions.MIN_DASH),
                onChange = { f ->
                    state.updateShape { it.copy(dashLength = ShapeOptions.MIN_DASH + f * (ShapeOptions.MAX_DASH - ShapeOptions.MIN_DASH)) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 6.dp),
            )
        }
        LabeledRow(R.string.panel_corner_radius) {
            ValuePill(onClick = {
                state.updateShape { it.copy(cornerRadius = (it.cornerRadius + 1) % (ShapeOptions.MAX_CORNER_RADIUS + 1)) }
            }) { ValueText(options.cornerRadius.toString()) }
        }
        LabeledRow(R.string.panel_border_color) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { state.colorTarget = ColorTarget.SHAPE_BORDER }, Alignment.Center) {
                ColorRing(options.color)
            }
        }
        LabeledRow(R.string.panel_fill_color) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { state.tapShapeFill() }, Alignment.Center) {
                FillSwatch(options.fillColor)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** A shape's own outline, so the toolbar always shows exactly what will be drawn. */
@Composable
internal fun ShapeGlyph(kind: ShapeKind, tint: Color, size: Dp = 22.dp) {
    val path = remember { Path() }
    Canvas(Modifier.size(size)) {
        val inset = 2.dp.toPx()
        ShapePaths.build(path, kind, this.size.width - inset * 2f, this.size.height - inset * 2f, 2.dp.toPx())
        translate(inset, inset) {
            drawPath(path.asComposePath(), tint, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A border color: a thick ring around nothing. */
@Composable
internal fun ColorRing(color: Int) {
    val edge = LocalEditorColors.current.swatchEdge
    Canvas(Modifier.size(24.dp)) {
        val thick = 4.dp.toPx()
        drawCircle(edge, size.minDimension / 2f, style = Stroke(1.dp.toPx()))
        drawCircle(Color(color), size.minDimension / 2f - thick / 2f, style = Stroke(thick))
    }
}

/** A fill color, or a struck-through dashed circle for none. */
@Composable
internal fun FillSwatch(color: Int) {
    if (color ushr 24 != 0) {
        ColorDot(color, 24.dp)
        return
    }
    val tint = LocalEditorColors.current.icon
    Canvas(Modifier.size(24.dp)) {
        val radius = size.minDimension / 2f - 1.dp.toPx()
        drawCircle(
            tint,
            radius,
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
        val reach = radius * 0.6f
        drawLine(tint, Offset(center.x - reach, center.y - reach), Offset(center.x + reach, center.y + reach), 1.5.dp.toPx())
    }
}

private const val PREVIEW_SIZE_DP = 80f
private const val PREVIEW_DP_PER_WIDTH = 0.5f
private const val PREVIEW_DP_PER_RADIUS = 2f
