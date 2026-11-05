package app.betternotes.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import kotlin.math.abs
import kotlin.math.min

private const val ELEMENT_COLOR = 0xFF55585F.toInt()
private const val DEFAULT_LINE_WIDTH = 0.75f
private const val DEFAULT_SPACING = 24f
private const val MIN_ELEMENT_PT = 12f
private const val TAP_TOLERANCE_PT = 12f
private val UNITS = listOf(LengthUnit.PX, LengthUnit.CM, LengthUnit.INCH)

private enum class LayoutTool { SELECT, LINE, BOX, STICKY, TABLE, RULED }

private const val STICKY_COLOR = 0xFFFFE680.toInt()

/** The element a drag from (x0, y0) to (x1, y1) makes with this tool, or null when the drag is too small. */
private fun LayoutTool.draw(x0: Float, y0: Float, x1: Float, y1: Float): LayoutElement? {
    val left = min(x0, x1)
    val top = min(y0, y1)
    val width = abs(x1 - x0)
    val height = abs(y1 - y0)
    if (this == LayoutTool.LINE) {
        return if (width + height < MIN_ELEMENT_PT) null else LayoutElement.Line(x0, y0, x1, y1, DEFAULT_LINE_WIDTH, ELEMENT_COLOR)
    }
    if (width < MIN_ELEMENT_PT || height < MIN_ELEMENT_PT) return null
    return when (this) {
        LayoutTool.BOX -> LayoutElement.Rectangle(left, top, width, height, DEFAULT_LINE_WIDTH, ELEMENT_COLOR)
        LayoutTool.STICKY -> LayoutElement.Sticky(left, top, width, height, STICKY_COLOR)
        LayoutTool.TABLE -> LayoutElement.Table(left, top, width, height, 3, 3, DEFAULT_LINE_WIDTH, ELEMENT_COLOR)
        LayoutTool.RULED -> LayoutElement.Ruled(left, top, width, height, DEFAULT_SPACING, DEFAULT_LINE_WIDTH, ELEMENT_COLOR)
        else -> null
    }
}

/**
 * A full-screen canvas for building a custom template: pick a tool, drag on the page to draw a line, box,
 * sticky note, table or ruled block, then select, move or adjust it. No Notewise screenshot of this screen
 * exists, so it follows the template picker's look rather than matching a reference.
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
    var tool by remember { mutableStateOf(LayoutTool.SELECT) }
    var name by remember { mutableStateOf(builder.name) }
    var widthText by remember { mutableStateOf(format(builder.width)) }
    var heightText by remember { mutableStateOf(format(builder.height)) }
    fun changed() {
        revision++
    }

    var askDiscard by remember { mutableStateOf(false) }
    // Leaving with nothing changed needs no question; otherwise ask before dropping the work.
    fun discard() {
        if (revision == 0 && name == (initial?.name ?: "")) onClose() else askDiscard = true
    }

    BackHandler(onBack = ::discard)
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(colors.sheet)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            ChoiceChip(stringResource(R.string.layout_discard), false, ::discard)
            TextBox(name, 260.dp, KeyboardType.Text) { name = it }
            for (unit in UNITS) {
                ChoiceChip(unitName(unit), builder.unit == unit) {
                    builder.unit = unit
                    widthText = format(builder.width)
                    heightText = format(builder.height)
                    changed()
                }
            }
            TextBox(widthText, 90.dp, KeyboardType.Decimal) {
                widthText = it
                it.toFloatOrNull()?.let { value -> builder.setSize(value, builder.height) }
                changed()
            }
            Text("x", color = colors.text, fontSize = 19.5.sp)
            TextBox(heightText, 90.dp, KeyboardType.Decimal) {
                heightText = it
                it.toFloatOrNull()?.let { value -> builder.setSize(builder.width, value) }
                changed()
            }
            Spacer(Modifier.weight(1f))
            if (initial != null) {
                Text(
                    stringResource(R.string.layout_delete),
                    color = colors.danger,
                    fontSize = 19.5.sp,
                    modifier = Modifier.padding(12.dp).pointerInput(Unit) { detectTapGestures { onDelete(initial.name) } },
                )
            }
            val canSave = name.isNotBlank()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (canSave) colors.accent else colors.sliderIdle)
                    .pointerInput(canSave) {
                        if (!canSave) return@pointerInput
                        detectTapGestures {
                            builder.name = name.trim()
                            onSave(builder.build())
                        }
                    }
                    .padding(horizontal = 32.dp),
            ) {
                Text(stringResource(R.string.layout_confirm), color = colors.onAccent, fontSize = 19.5.sp)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp).width(150.dp),
            ) {
                for ((entry, label) in toolLabels()) {
                    ChoiceChip(label, tool == entry) { tool = entry }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Reads the revision so that the page redraws after the builder changes.
                val preview = remember(revision) { builder.build() }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LayoutPreview(
                        preview,
                        selected,
                        drawing = tool != LayoutTool.SELECT,
                        onTap = { x, y ->
                            selected = if (tool == LayoutTool.SELECT) builder.elementAt(x, y, TAP_TOLERANCE_PT) else -1
                            changed()
                        },
                        onDraw = { x0, y0, x1, y1 ->
                            tool.draw(x0, y0, x1, y1)?.let {
                                selected = builder.add(it)
                                tool = LayoutTool.SELECT
                                changed()
                            }
                        },
                        onDrag = { dx, dy ->
                            if (selected in builder.elements.indices) {
                                builder.moveBy(selected, dx, dy)
                                changed()
                            }
                        },
                        onPress = { x, y ->
                            val hit = builder.elementAt(x, y, TAP_TOLERANCE_PT)
                            if (hit >= 0 && hit != selected) {
                                selected = hit
                                changed()
                            }
                        },
                    )
                }
                if (selected in builder.elements.indices) {
                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 220.dp)) {
                        ElementControls(builder.elements[selected], builder.unit, onChange = {
                            builder.update(selected, it)
                            changed()
                        }, onRemove = {
                            builder.remove(selected)
                            selected = -1
                            changed()
                        })
                    }
                }
            }
        }
    }
    if (askDiscard) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.width(420.dp).clip(RoundedCornerShape(24.dp)).background(colors.card).padding(28.dp),
            ) {
                Text(stringResource(R.string.layout_discard_title), color = colors.text, fontSize = 22.sp)
                Text(stringResource(R.string.layout_discard_message), color = colors.text, fontSize = 19.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    ChoiceChip(stringResource(R.string.layout_keep_editing), true) { askDiscard = false }
                    ChoiceChip(stringResource(R.string.layout_discard), false, onClose)
                }
            }
        }
    }
    }
}



@Composable
private fun toolLabels() = listOf(
    LayoutTool.SELECT to stringResource(R.string.layout_tool_select),
    LayoutTool.LINE to stringResource(R.string.layout_add_line),
    LayoutTool.BOX to stringResource(R.string.layout_add_box),
    LayoutTool.STICKY to stringResource(R.string.layout_add_sticky),
    LayoutTool.TABLE to stringResource(R.string.layout_add_table),
    LayoutTool.RULED to stringResource(R.string.layout_add_ruled),
)

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

/**
 * Draws the layout on a sheet of paper, with the selected element in the accent color. With [drawing] off a
 * drag moves the element under the finger, with it on a drag draws a new element from where it began to
 * where it ended.
 */
@Composable
private fun LayoutPreview(
    layout: CustomLayout,
    selected: Int,
    drawing: Boolean,
    onTap: (Float, Float) -> Unit,
    onPress: (Float, Float) -> Unit,
    onDraw: (Float, Float, Float, Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val colors = LocalSettingsColors.current
    val ratio = layout.size.widthPt / layout.size.heightPt
    var start by remember { mutableStateOf(Offset.Zero) }
    var end by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitted = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
        Canvas(
            Modifier
                .width(fitted)
                .aspectRatio(ratio)
                .background(Color.White)
                .pointerInput(layout.size) {
                    detectTapGestures { offset ->
                        val factor = layout.size.widthPt / size.width
                        onTap(offset.x * factor, offset.y * factor)
                    }
                }
                .pointerInput(layout.size, drawing) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val factor = layout.size.widthPt / size.width
                            start = offset
                            end = offset
                            if (!drawing) onPress(offset.x * factor, offset.y * factor)
                        },
                        onDragEnd = {
                            val factor = layout.size.widthPt / size.width
                            val last = end
                            if (drawing && last != null) onDraw(start.x * factor, start.y * factor, last.x * factor, last.y * factor)
                            end = null
                        },
                        onDragCancel = { end = null },
                    ) { change, drag ->
                        change.consume()
                        val factor = layout.size.widthPt / size.width
                        if (drawing) end = change.position else onDrag(drag.x * factor, drag.y * factor)
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
            end?.takeIf { drawing }?.let {
                drawRect(
                    colors.accent,
                    Offset(min(start.x, it.x), min(start.y, it.y)),
                    Size(abs(it.x - start.x), abs(it.y - start.y)),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun ElementControls(element: LayoutElement, unit: LengthUnit, onChange: (LayoutElement) -> Unit, onRemove: () -> Unit) {
    val colors = LocalSettingsColors.current
    Column(Modifier.fillMaxWidth()) {
        when (element) {
            is LayoutElement.Line -> Unit
            is LayoutElement.Sticky -> SizeRow(element.width, element.height, unit) { w, h -> onChange(element.copy(width = w, height = h)) }
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
