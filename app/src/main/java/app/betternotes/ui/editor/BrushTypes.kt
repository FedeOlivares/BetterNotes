package app.betternotes.ui.editor

import android.graphics.Matrix
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withMatrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.brush.InputToolType
import app.betternotes.R
import app.betternotes.canvas.InkBrushes
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.tools.InkPreset
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors

@StringRes
private fun BrushKind.label() = when (this) {
    BrushKind.PENCIL -> R.string.brush_pencil
    BrushKind.OIL -> R.string.brush_oil
    BrushKind.SPILL -> R.string.brush_spill
    BrushKind.WATERCOLOR -> R.string.brush_watercolor
    BrushKind.COMICS -> R.string.brush_comics
    BrushKind.STRIPES -> R.string.brush_stripes
    BrushKind.SINGLE_DOTS -> R.string.brush_single_dots
    BrushKind.DOTS -> R.string.brush_dots
    BrushKind.LEAVES -> R.string.brush_leaves
    BrushKind.CROSS -> R.string.brush_cross
    BrushKind.GLOW -> R.string.brush_glow
}

/** The creative brush's type: the one in use with a sample of its line, and a list of the others to pick from. */
@Composable
internal fun BrushTypeSelector(preset: InkPreset, screenDpi: Float, onSelect: (BrushKind) -> Unit) {
    val colors = LocalEditorColors.current
    var open by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.panel_brush_type),
        color = colors.label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = PanelPadding, top = 6.dp, bottom = 8.dp),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = PanelPadding)) {
        BrushRow(preset.brush, preset.color, screenDpi, selected = false, chevron = true) { open = !open }
        if (open) {
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.panelDivider, RoundedCornerShape(16.dp)),
            ) {
                for (kind in BrushKind.entries) {
                    BrushRow(kind, preset.color, screenDpi, selected = kind == preset.brush, chevron = false) {
                        open = false
                        onSelect(kind)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BrushRow(kind: BrushKind, color: Int, screenDpi: Float, selected: Boolean, chevron: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(if (chevron) Modifier.clip(RoundedCornerShape(27.dp)).border(1.dp, colors.panelDivider, RoundedCornerShape(27.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(kind.label()),
            color = if (selected) colors.accent else colors.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(96.dp),
        )
        BrushSample(kind, screenDpi, Modifier.weight(1f).height(36.dp))
        if (chevron) Icon(EditorIcons.ChevronDown, null, tint = colors.label, modifier = Modifier.padding(start = 10.dp).size(22.dp))
    }
}

/** A short stroke of the brush, drawn by the same renderer as the page, in white so that it shows on the panel. */
@Composable
private fun BrushSample(kind: BrushKind, screenDpi: Float, modifier: Modifier) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    val matrix = remember { Matrix() }
    val pxPerPt = screenDpi / POINTS_PER_INCH
    val stroke = remember(kind) { sampleLine(InkStyle(tool = app.betternotes.core.model.InkTool.PENCIL, color = 0xFFE6E7EA.toInt(), width = SAMPLE_WIDTH, brush = kind)) }
    Canvas(modifier) {
        matrix.setScale(pxPerPt, pxPerPt)
        matrix.postTranslate(0f, size.height / 2f)
        drawIntoCanvas { canvas -> canvas.nativeCanvas.withMatrix(matrix) { renderer.draw(this, stroke, matrix) } }
    }
}

private fun sampleLine(style: InkStyle): Stroke {
    val batch = MutableStrokeInputBatch()
    val points = 40
    for (i in 0..points) {
        val t = i.toFloat() / points
        val x = t * SAMPLE_LENGTH
        val y = kotlin.math.sin(t * 6.0).toFloat() * 3f
        batch.add(StrokeInput().apply { update(x, y, i * 8L, InputToolType.STYLUS, 1f / 0.3527778f, 0.6f) })
    }
    return Stroke(InkBrushes.brush(style), batch)
}

private const val POINTS_PER_INCH = 72f
private const val SAMPLE_WIDTH = 5f
private const val SAMPLE_LENGTH = 56f
