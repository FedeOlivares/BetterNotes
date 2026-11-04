package app.betternotes.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.CustomLayoutBuilder
import app.betternotes.core.template.LayoutElement
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.bounds
import kotlin.math.min

private const val ELEMENT_COLOR = 0xFF55585F.toInt()
private const val DEFAULT_LINE_WIDTH = 0.75f
private const val DEFAULT_SPACING = 24f
private const val MIN_ELEMENT_PT = 12f
private const val TAP_TOLERANCE_PT = 12f
private val UNITS = listOf(LengthUnit.PX, LengthUnit.CM, LengthUnit.INCH)

/**
 * Builds a custom layout: a size in px, cm or inches, and lines, boxes, tables and ruled blocks to
 * drag into place. The owner asked for this, but no screenshot of it exists, so it follows the look
 * of the template picker rather than matching a reference.
 */
@Composable
fun CustomLayoutSheet(
    initial: CustomLayout?,
    onSave: (CustomLayout) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    val builder = remember { initial?.let(::CustomLayoutBuilder) ?: CustomLayoutBuilder("") }
    // The builder is not observable, so this counts its changes for Compose to redraw from.
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var name by remember { mutableStateOf(builder.name) }
    var widthText by remember { mutableStateOf(format(builder.width)) }
    var heightText by remember { mutableStateOf(format(builder.height)) }
    fun changed() {
        revision++
    }
    fun syncSizeFields() {
        widthText = format(builder.width)
        heightText = format(builder.height)
    }
    fun add(element: LayoutElement) {
        selected = builder.add(element)
        changed()
    }

    SettingsSheet(stringResource(R.string.layout_title), onClose, modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 41.dp).padding(bottom = 24.dp),
        ) {
            SettingRow(stringResource(R.string.layout_name)) { TextBox(name, 300.dp, KeyboardType.Text) { name = it } }
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                for (unit in UNITS) {
                    ChoiceChip(unitName(unit), builder.unit == unit) {
                        builder.unit = unit
                        syncSizeFields()
                        changed()
                    }
                }
            }
            SettingRow(stringResource(R.string.layout_size)) {
                TextBox(widthText, 120.dp, KeyboardType.Decimal) {
                    widthText = it
                    it.toFloatOrNull()?.let { value -> builder.setSize(value, builder.height) }
                    changed()
                }
                Text(" x ", color = colors.text, fontSize = 19.5.sp)
                TextBox(heightText, 120.dp, KeyboardType.Decimal) {
                    heightText = it
                    it.toFloatOrNull()?.let { value -> builder.setSize(builder.width, value) }
                    changed()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                val w = builder.size.widthPt
                val h = builder.size.heightPt
                ChoiceChip(stringResource(R.string.layout_add_line), false) {
                    add(LayoutElement.Line(w * 0.1f, h * 0.5f, w * 0.9f, h * 0.5f, DEFAULT_LINE_WIDTH, ELEMENT_COLOR))
                }
                ChoiceChip(stringResource(R.string.layout_add_box), false) {
                    add(LayoutElement.Rectangle(w * 0.25f, h * 0.25f, w * 0.5f, h * 0.25f, DEFAULT_LINE_WIDTH, ELEMENT_COLOR))
                }
                ChoiceChip(stringResource(R.string.layout_add_table), false) {
                    add(LayoutElement.Table(w * 0.15f, h * 0.2f, w * 0.7f, h * 0.3f, 3, 3, DEFAULT_LINE_WIDTH, ELEMENT_COLOR))
                }
                ChoiceChip(stringResource(R.string.layout_add_ruled), false) {
                    add(LayoutElement.Ruled(w * 0.1f, h * 0.1f, w * 0.8f, h * 0.3f, DEFAULT_SPACING, DEFAULT_LINE_WIDTH, ELEMENT_COLOR))
                }
            }

            // Reads the revision so that the preview redraws after the builder changes.
            val preview = remember(revision) { builder.build() }
            LayoutPreview(
                preview,
                selected,
                onTap = { x, y ->
                    selected = builder.elementAt(x, y, TAP_TOLERANCE_PT)
                    changed()
                },
                onDrag = { dx, dy ->
                    if (selected in builder.elements.indices) {
                        builder.moveBy(selected, dx, dy)
                        changed()
                    }
                },
            )

            if (selected in builder.elements.indices) {
                ElementControls(builder.elements[selected], builder.unit, onChange = {
                    builder.update(selected, it)
                    changed()
                }, onRemove = {
                    builder.remove(selected)
                    selected = -1
                    changed()
                })
            }

            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (initial != null) {
                    Text(
                        stringResource(R.string.layout_delete),
                        color = colors.danger,
                        fontSize = 19.5.sp,
                        modifier = Modifier.padding(16.dp).pointerInput(Unit) { detectTapGestures { onDelete(initial.name) } },
                    )
                    Spacer(Modifier.weight(1f))
                }
                val canSave = name.isNotBlank()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (canSave) colors.accent else colors.sliderIdle)
                        .pointerInput(canSave) {
                            if (!canSave) return@pointerInput
                            detectTapGestures {
                                builder.name = name.trim()
                                onSave(builder.build())
                            }
                        }
                        .padding(horizontal = 40.dp),
                ) {
                    Text(stringResource(R.string.layout_save), color = colors.onAccent, fontSize = 19.5.sp)
                }
            }
        }
    }
}

@Composable
private fun unitName(unit: LengthUnit) = stringResource(
    when (unit) {
        LengthUnit.PX -> R.string.unit_px
        LengthUnit.CM -> R.string.unit_cm
        else -> R.string.unit_inch
    },
)

@Composable
private fun TextBox(value: String, width: androidx.compose.ui.unit.Dp, keyboard: KeyboardType, onChange: (String) -> Unit) {
    val colors = LocalSettingsColors.current
    BasicTextField(
        value,
        onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = TextStyle(color = colors.text, fontSize = 19.5.sp),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Draws the layout on a sheet of paper, with the selected element in the accent color. Drags move it. */
@Composable
private fun LayoutPreview(layout: CustomLayout, selected: Int, onTap: (Float, Float) -> Unit, onDrag: (Float, Float) -> Unit) {
    val colors = LocalSettingsColors.current
    val ratio = layout.size.widthPt / layout.size.heightPt
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val height = minOf(maxWidth / ratio, PREVIEW_MAX_HEIGHT)
        Canvas(
            Modifier
                .height(height)
                .aspectRatio(ratio)
                .background(Color.White)
                .pointerInput(layout.size) {
                    detectTapGestures { offset ->
                        val factor = layout.size.widthPt / size.width
                        onTap(offset.x * factor, offset.y * factor)
                    }
                }
                .pointerInput(layout.size) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val factor = layout.size.widthPt / size.width
                        onDrag(drag.x * factor, drag.y * factor)
                    }
                },
        ) {
            val factor = min(size.width / layout.size.widthPt, size.height / layout.size.heightPt)
            scale(factor, factor, Offset.Zero) {
                TemplatePainter.paint(this, PageTemplate.Custom(layout), layout.size)
            }
            if (selected in layout.elements.indices) {
                val box = layout.elements[selected].bounds
                drawRect(
                    colors.accent,
                    Offset(box.left * factor, box.top * factor),
                    Size(box.width * factor, box.height * factor),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
    }
}

private val PREVIEW_MAX_HEIGHT = 560.dp

@Composable
private fun ElementControls(element: LayoutElement, unit: LengthUnit, onChange: (LayoutElement) -> Unit, onRemove: () -> Unit) {
    val colors = LocalSettingsColors.current
    Column(Modifier.fillMaxWidth()) {
        when (element) {
            is LayoutElement.Line -> Unit
            is LayoutElement.Rectangle -> SizeRow(element.width, element.height, unit) { w, h -> onChange(element.copy(width = w, height = h)) }
            is LayoutElement.Table -> {
                SizeRow(element.width, element.height, unit) { w, h -> onChange(element.copy(width = w, height = h)) }
                CountRow(stringResource(R.string.layout_rows), element.rows) { onChange(element.copy(rows = it)) }
                CountRow(stringResource(R.string.layout_columns), element.columns) { onChange(element.copy(columns = it)) }
            }
            is LayoutElement.Ruled -> {
                SizeRow(element.width, element.height, unit) { w, h -> onChange(element.copy(width = w, height = h)) }
                SettingRow(stringResource(R.string.layout_spacing)) {
                    RoundIcon(SettingsIcons.Minus, 26.dp) { onChange(element.copy(spacing = (element.spacing - 2f).coerceAtLeast(PageTemplate.MIN_SPACING))) }
                    ValuePill(format(unit.fromPt(element.spacing)))
                    RoundIcon(SettingsIcons.Plus, 26.dp) { onChange(element.copy(spacing = element.spacing + 2f)) }
                }
            }
        }
        Text(
            stringResource(R.string.layout_remove_element),
            color = colors.danger,
            fontSize = 20.sp,
            modifier = Modifier.padding(vertical = 8.dp).pointerInput(Unit) { detectTapGestures { onRemove() } },
        )
    }
}

@Composable
private fun SizeRow(width: Float, height: Float, unit: LengthUnit, onChange: (Float, Float) -> Unit) {
    SettingRow(stringResource(R.string.layout_element_size)) {
        val step = unit.toPt(unit.fromPt(MIN_ELEMENT_PT))
        RoundIcon(SettingsIcons.Minus, 26.dp) { onChange((width - step).coerceAtLeast(MIN_ELEMENT_PT), (height - step).coerceAtLeast(MIN_ELEMENT_PT)) }
        ValuePill("${format(unit.fromPt(width))} x ${format(unit.fromPt(height))}")
        RoundIcon(SettingsIcons.Plus, 26.dp) { onChange(width + step, height + step) }
    }
}

@Composable
private fun CountRow(label: String, value: Int, onChange: (Int) -> Unit) {
    SettingRow(label) {
        RoundIcon(SettingsIcons.Minus, 26.dp) { onChange((value - 1).coerceAtLeast(1)) }
        ValuePill(value.toString())
        RoundIcon(SettingsIcons.Plus, 26.dp) { onChange(value + 1) }
    }
}

private fun format(value: Float): String {
    val rounded = Math.round(value * 100f) / 100f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}
