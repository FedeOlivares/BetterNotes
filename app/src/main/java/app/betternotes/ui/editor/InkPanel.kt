package app.betternotes.ui.editor

import android.graphics.Matrix
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withMatrix
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import app.betternotes.R
import app.betternotes.canvas.InkBrushes
import app.betternotes.canvas.STROKE_UNIT_CM
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.PenType
import app.betternotes.core.model.TipShape
import app.betternotes.core.tools.InkPreset
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal val PanelWidth = 380.dp
internal val PanelShape = RoundedCornerShape(22.dp)
internal val PanelPadding = 18.dp
private val RowSpacing = 49.dp

/** The dark rounded sheet that holds a tool's full settings. */
@Composable
internal fun PanelSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEditorColors.current
    Column(
        modifier
            .width(PanelWidth)
            .shadow(12.dp, PanelShape, clip = false)
            .clip(PanelShape)
            .background(colors.panel)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth().height(54.dp)) {
            Text(
                title,
                color = colors.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(colors.closeButton)
                    .clickable(role = Role.Button, onClick = onClose),
            ) {
                Icon(
                    EditorIcons.Close,
                    stringResource(R.string.panel_close),
                    tint = colors.label,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        content()
    }
}

/**
 * The panel from the owner's screenshot, for the pen, pencil and highlighter alike. The opacity, tip and
 * hold time rows are the owner's requirements and are not in the screenshot, so they copy its rows.
 */
@Composable
internal fun InkPanel(state: EditorState, preset: InkPreset, notices: Notices, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    var holdOptionsOpen by remember { mutableStateOf(false) }
    val title = when (preset.tool) {
        InkTool.PEN -> R.string.tool_pen
        InkTool.PENCIL -> R.string.tool_pencil
        InkTool.HIGHLIGHTER -> R.string.tool_highlighter
    }
    PanelSheet(stringResource(title), onClose = { state.panelOpen = false }, modifier = modifier) {
        StrokePreview(preset.style(), state.screenDpi)
        if (preset.tool == InkTool.PEN) {
            PenTypeCards(preset.penType) { type -> state.update { it.copy(penType = type) } }
        }
        LabeledRow(R.string.panel_thickness) { ValuePill { ValueText(preset.thickness.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - 6.dp)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) {
                state.update { it.withThickness(it.thickness - 1) }
            }
            PanelSlider(
                fraction = (preset.thickness - InkPreset.MIN_THICKNESS).toFloat() /
                    (InkPreset.MAX_THICKNESS - InkPreset.MIN_THICKNESS),
                onChange = { f ->
                    val range = InkPreset.MAX_THICKNESS - InkPreset.MIN_THICKNESS
                    state.update { it.withThickness(InkPreset.MIN_THICKNESS + (f * range).roundToInt()) }
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) {
                state.update { it.withThickness(it.thickness + 1) }
            }
        }
        LabeledRow(R.string.panel_opacity) { ValuePill { ValueText("${(preset.opacity * 100).roundToInt()}%") } }
        PanelSlider(
            fraction = preset.opacity,
            onChange = { f -> state.update { it.copy(opacity = f.coerceAtLeast(MIN_OPACITY)) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = PanelPadding).fillMaxWidth().height(1.dp).background(colors.panelDivider))
        Spacer(Modifier.height(10.dp))
        LabeledRow(R.string.panel_line_type) {
            ValuePill(onClick = { state.update { it.copy(line = it.line.next()) } }) {
                LineTypeGlyph(preset.line, colors.icon)
            }
        }
        LabeledRow(R.string.panel_tip) {
            ValuePill(onClick = { state.update { it.copy(tip = it.tip.next()) } }) { TipGlyph(preset.tip) }
        }
        LabeledRow(
            R.string.panel_hold_shape,
            info = { notices.say(R.string.info_hold_shape) },
            extra = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .tappable(radius = 16.dp) { holdOptionsOpen = !holdOptionsOpen },
                ) {
                    Icon(
                        EditorIcons.More,
                        stringResource(R.string.panel_hold_options),
                        tint = colors.label,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        ) {
            PanelSwitch(preset.holdToLine, stringResource(R.string.panel_hold_shape)) { on ->
                state.update { it.copy(holdToLine = on) }
            }
        }
        if (holdOptionsOpen) {
            LabeledRow(R.string.panel_hold_time) {
                ValuePill { ValueText("%.1f s".format(preset.holdDelayMs / 1000f)) }
            }
            PanelSlider(
                fraction = (preset.holdDelayMs - InkPreset.MIN_HOLD_MS).toFloat() /
                    (InkPreset.MAX_HOLD_MS - InkPreset.MIN_HOLD_MS),
                onChange = { f ->
                    val range = InkPreset.MAX_HOLD_MS - InkPreset.MIN_HOLD_MS
                    val ms = InkPreset.MIN_HOLD_MS + (f * range / HOLD_STEP_MS).roundToInt() * HOLD_STEP_MS
                    state.update { it.copy(holdDelayMs = ms) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 6.dp),
            )
        }
        LabeledRow(R.string.panel_scribble, info = { notices.say(R.string.info_scribble) }) {
            PanelSwitch(preset.scribbleToErase, stringResource(R.string.panel_scribble)) { on ->
                state.update { it.copy(scribbleToErase = on) }
            }
        }
        LabeledRow(R.string.panel_circle, info = { notices.say(R.string.info_circle) }) {
            PanelSwitch(preset.circleToLasso, stringResource(R.string.panel_circle)) { on ->
                state.update { it.copy(circleToLasso = on) }
            }
        }
        LabeledRow(R.string.panel_stabilization, info = { notices.say(R.string.info_stabilization) }) {
            ValuePill { ValueText(preset.stabilization.toString()) }
        }
        PanelSlider(
            fraction = (preset.stabilization - 1).toFloat() / (InkPreset.MAX_STABILIZATION - 1),
            onChange = { f ->
                val level = 1 + (f * (InkPreset.MAX_STABILIZATION - 1)).roundToInt()
                state.update { it.copy(stabilization = level) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 6.dp),
        )
        ColorSection(state, preset, notices)
    }
}

@Composable
internal fun LabeledRow(
    @StringRes label: Int,
    info: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowSpacing)
            .padding(horizontal = PanelPadding),
    ) {
        Text(
            stringResource(label),
            color = colors.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        )
        if (info != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(30.dp)
                    .tappable(radius = 15.dp, onClick = info),
            ) {
                Icon(
                    EditorIcons.Info,
                    stringResource(R.string.panel_info),
                    tint = colors.label,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        extra?.invoke()
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun PanelIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).tappable(radius = 18.dp, onClick = onClick),
    ) {
        Icon(icon, description, tint = LocalEditorColors.current.icon, modifier = Modifier.size(22.dp))
    }
}

private fun TipShape.next() = if (this == TipShape.ROUND) TipShape.SQUARE else TipShape.ROUND

@Composable
private fun TipGlyph(tip: TipShape) {
    val colors = LocalEditorColors.current
    val shape = if (tip == TipShape.ROUND) CircleShape else RoundedCornerShape(1.dp)
    Box(Modifier.size(14.dp).background(colors.icon, shape))
}

@Composable
private fun PenTypeCards(selected: PenType, onSelect: (PenType) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding),
    ) {
        PenTypeCard(EditorIcons.NaturalNib, R.string.pen_natural, selected == PenType.NATURAL) { onSelect(PenType.NATURAL) }
        PenTypeCard(EditorIcons.BallNib, R.string.pen_ball, selected == PenType.BALL) { onSelect(PenType.BALL) }
        PenTypeCard(EditorIcons.CalligraphyNib, R.string.pen_calligraphy, selected == PenType.CALLIGRAPHY) {
            onSelect(PenType.CALLIGRAPHY)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PenTypeCard(
    icon: ImageVector,
    @StringRes label: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(16.dp)
    val tint = if (selected) colors.accent else colors.title
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .height(67.dp)
            .clip(shape)
            .background(if (selected) colors.accentContainer else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.panelDivider, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(label), color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** A sample of the real brush at its real size, drawn by the same renderer as the page. */
@Composable
private fun StrokePreview(style: InkStyle, screenDpi: Float) {
    val colors = LocalEditorColors.current
    val renderer = remember { CanvasStrokeRenderer.create() }
    val matrix = remember { Matrix() }
    val pxPerPt = screenDpi / POINTS_PER_INCH
    val stroke = remember(style) { sampleStroke(style) }
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val spacing = 45.dp.toPx()
        val dot = 1.dp.toPx()
        var row = 0
        var y = 8.dp.toPx()
        while (y < size.height) {
            var x = if (row % 2 == 0) spacing / 2f else spacing
            while (x < size.width) {
                drawCircle(colors.previewDot, dot, Offset(x, y))
                x += spacing
            }
            y += spacing
            row++
        }
        matrix.setScale(pxPerPt, pxPerPt)
        matrix.postTranslate(
            size.width / 2f - SAMPLE_WIDTH_PT * pxPerPt / 2f,
            size.height / 2f - SAMPLE_HEIGHT_PT * pxPerPt / 2f,
        )
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.withMatrix(matrix) { renderer.draw(this, stroke, matrix) }
        }
    }
}

/** The curve from the screenshot: up to a peak, down, and a small tail, with pressure that varies. */
private fun sampleStroke(style: InkStyle): Stroke {
    val batch = MutableStrokeInputBatch()
    val segments = SAMPLE_CURVE.size / SAMPLE_SEGMENT_FLOATS
    val perSegment = SAMPLE_POINTS / segments
    var time = 0L
    for (segment in 0 until segments) {
        val c = segment * SAMPLE_SEGMENT_FLOATS
        for (i in (if (segment == 0) 0 else 1)..perSegment) {
            val t = i.toFloat() / perSegment
            val progress = (segment + t) / segments
            batch.add(
                InputToolType.STYLUS,
                cubic(t, SAMPLE_CURVE[c], SAMPLE_CURVE[c + 2], SAMPLE_CURVE[c + 4], SAMPLE_CURVE[c + 6]) * SAMPLE_WIDTH_PT,
                cubic(t, SAMPLE_CURVE[c + 1], SAMPLE_CURVE[c + 3], SAMPLE_CURVE[c + 5], SAMPLE_CURVE[c + 7]) * SAMPLE_HEIGHT_PT,
                time,
                STROKE_UNIT_CM,
                0.35f + 0.5f * sin(progress * PI).toFloat(),
            )
            time += SAMPLE_STEP_MS
        }
    }
    return Stroke(InkBrushes.brush(style), batch)
}

private fun cubic(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val u = 1f - t
    return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
}

@Composable
private fun ColorSection(state: EditorState, preset: InkPreset, notices: Notices) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(RowSpacing).padding(start = PanelPadding, end = PanelPadding - 6.dp),
    ) {
        Text(
            stringResource(R.string.panel_color),
            color = colors.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        )
        Spacer(Modifier.weight(1f))
        PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_add_swatch)) {
            state.colorTarget = ColorTarget.PALETTE
        }
        PanelIconButton(EditorIcons.List, stringResource(R.string.panel_swatch_list), notices::notBuilt)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = PanelPadding - 8.dp, end = PanelPadding - 8.dp, bottom = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        ) {
            for (color in preset.palette) {
                PaletteSwatch(color, selected = color == preset.color) { state.update { it.pickColor(color) } }
            }
        }
        Box(Modifier.padding(horizontal = 4.dp).size(2.dp, 28.dp).background(colors.panelDivider))
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(HueStops))
                .clickable(role = Role.Button) { state.colorTarget = ColorTarget.SELECTED_SLOT },
        )
    }
}

@Composable
private fun PaletteSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val ring = LocalEditorColors.current.swatchRing
    SwatchCell(40.dp, onClick) {
        if (selected) Box(Modifier.size(35.dp).border(5.dp, ring, CircleShape))
        ColorDot(color)
    }
}

@Composable
private fun SwatchCell(width: Dp, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(width, 40.dp).tappable(radius = 20.dp, onClick = onClick),
        content = content,
    )
}

internal val HueStops = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

private const val MIN_OPACITY = 0.05f
private const val HOLD_STEP_MS = 100
private const val POINTS_PER_INCH = 72f
private const val SAMPLE_POINTS = 48
private const val SAMPLE_STEP_MS = 8L
private const val SAMPLE_SEGMENT_FLOATS = 8

/** Three cubic Beziers, as x and y pairs from 0 to 1. */
private val SAMPLE_CURVE = floatArrayOf(
    0f, 1f, 0.25f, 0.7f, 0.4f, 0f, 0.58f, 0f,
    0.58f, 0f, 0.68f, 0f, 0.66f, 0.95f, 0.8f, 1f,
    0.8f, 1f, 0.87f, 1.02f, 0.93f, 0.9f, 1f, 0.8f,
)
private const val SAMPLE_WIDTH_PT = 42f
private const val SAMPLE_HEIGHT_PT = 16f
