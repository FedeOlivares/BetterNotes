package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import app.betternotes.R
import app.betternotes.canvas.TextEditRequest
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VerticalAlign
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/** The sticky note's row, and the text tool's, which has no reference of its own and so has no colors. */
@Composable
internal fun NoteRow(state: EditorState, options: NoteOptions, modifier: Modifier, showColors: Boolean) {
    Pill(modifier) {
        ToolStrip {
            if (showColors) {
                options.colors.forEachIndexed { slot, color ->
                    ToolbarCellBox(onClick = { state.updateNote { it.copy(selectedColor = slot) } }) {
                        ColorDot(color, diameter = 24.dp)
                        if (slot == options.selectedColor) SelectedDot()
                    }
                }
                ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) {
                    state.colorTarget = ColorTarget.NOTE
                }
                PillDivider()
            }
            ToolbarCellBox(onClick = { state.panelOpen = true }) { ValueText(options.textSize.toString()) }
            val style = options.style
            StyleToggle(EditorIcons.Bold, R.string.style_bold, style.bold) { state.updateNoteStyle { it.copy(bold = !it.bold) } }
            StyleToggle(EditorIcons.Italic, R.string.style_italic, style.italic) { state.updateNoteStyle { it.copy(italic = !it.italic) } }
            StyleToggle(EditorIcons.Underline, R.string.style_underline, style.underline) {
                state.updateNoteStyle { it.copy(underline = !it.underline) }
            }
            StyleToggle(EditorIcons.Strike, R.string.style_strike, style.strikethrough) {
                state.updateNoteStyle { it.copy(strikethrough = !it.strikethrough) }
            }
            ToolbarIcon(style.align.icon(), stringResource(R.string.panel_horizontal_alignment)) {
                state.updateNoteStyle { it.copy(align = HorizontalAlign.entries[(it.align.ordinal + 1) % HorizontalAlign.entries.size]) }
            }
            ToolbarIcon(style.verticalAlign.icon(), stringResource(R.string.panel_vertical_alignment)) {
                state.updateNoteStyle {
                    it.copy(verticalAlign = VerticalAlign.entries[(it.verticalAlign.ordinal + 1) % VerticalAlign.entries.size])
                }
            }
            PillDivider()
            ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
                state.panelOpen = !state.panelOpen
            }
        }
    }
}

@Composable
private fun StyleToggle(icon: ImageVector, label: Int, on: Boolean, onClick: () -> Unit) {
    ToolbarIcon(icon, stringResource(label), accent = on, onClick = onClick)
}

private fun HorizontalAlign.icon() = when (this) {
    HorizontalAlign.START -> EditorIcons.AlignStart
    HorizontalAlign.CENTER -> EditorIcons.AlignCenter
    HorizontalAlign.END -> EditorIcons.AlignEnd
}

private fun VerticalAlign.icon() = when (this) {
    VerticalAlign.TOP -> EditorIcons.AlignTop
    VerticalAlign.MIDDLE -> EditorIcons.AlignMiddle
    VerticalAlign.BOTTOM -> EditorIcons.AlignBottom
}

@Composable
internal fun NotePanel(state: EditorState, notices: Notices, options: NoteOptions, modifier: Modifier, sticky: Boolean) {
    val colors = LocalEditorColors.current
    val title = if (sticky) R.string.tool_sticky else R.string.tool_text
    PanelSheet(stringResource(title), onClose = { state.panelOpen = false }, modifier = modifier) {
        if (sticky) {
            DottedPreview {
                val side = 86.dp.toPx()
                val fold = 16.dp.toPx()
                val left = center.x - side / 2f
                val top = center.y - side / 2f
                drawRect(Color(options.color), Offset(left, top), Size(side, side))
                val cut = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left + side - fold, top + side)
                    lineTo(left + side, top + side - fold / 2f)
                    lineTo(left + side, top + side)
                    close()
                }
                drawPath(cut, colors.panel)
            }
        }
        LabeledRow(R.string.panel_font_size) { ValuePill { ValueText(options.textSize.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - 6.dp)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) {
                state.updateNote { it.copy(textSize = (it.textSize - 1).coerceAtLeast(NoteOptions.MIN_TEXT)) }
            }
            PanelSlider(
                fraction = (options.textSize - NoteOptions.MIN_TEXT).toFloat() / (NoteOptions.MAX_TEXT - NoteOptions.MIN_TEXT),
                onChange = { f ->
                    val range = NoteOptions.MAX_TEXT - NoteOptions.MIN_TEXT
                    state.updateNote { it.copy(textSize = NoteOptions.MIN_TEXT + (f * range).roundToInt()) }
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) {
                state.updateNote { it.copy(textSize = (it.textSize + 1).coerceAtMost(NoteOptions.MAX_TEXT)) }
            }
        }
        PanelDivider()
        val style = options.style
        LabeledRow(R.string.panel_style) {
            Segments(
                listOf(
                    Segment(EditorIcons.Bold, R.string.style_bold, style.bold) { state.updateNoteStyle { it.copy(bold = !it.bold) } },
                    Segment(EditorIcons.Italic, R.string.style_italic, style.italic) { state.updateNoteStyle { it.copy(italic = !it.italic) } },
                    Segment(EditorIcons.Underline, R.string.style_underline, style.underline) {
                        state.updateNoteStyle { it.copy(underline = !it.underline) }
                    },
                    Segment(EditorIcons.Strike, R.string.style_strike, style.strikethrough) {
                        state.updateNoteStyle { it.copy(strikethrough = !it.strikethrough) }
                    },
                ),
            )
        }
        LabeledRow(R.string.panel_horizontal_alignment) {
            Segments(
                HorizontalAlign.entries.map { align ->
                    Segment(align.icon(), R.string.panel_horizontal_alignment, style.align == align) {
                        state.updateNoteStyle { it.copy(align = align) }
                    }
                },
            )
        }
        LabeledRow(R.string.panel_vertical_alignment) {
            Segments(
                VerticalAlign.entries.map { align ->
                    Segment(align.icon(), R.string.panel_vertical_alignment, style.verticalAlign == align) {
                        state.updateNoteStyle { it.copy(verticalAlign = align) }
                    }
                },
            )
        }
        if (sticky) {
            LabeledRow(R.string.panel_keep_open, info = { notices.say(R.string.info_keep_open) }) {
                PanelSwitch(options.keepOpen, stringResource(R.string.panel_keep_open)) { on ->
                    state.updateNote { it.copy(keepOpen = on) }
                }
            }
            LabeledRow(R.string.panel_fill_color) {}
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PanelPadding - 8.dp, end = PanelPadding - 8.dp, bottom = 16.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                for (color in NoteOptions.STICKY_PALETTE) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).tappable(radius = 20.dp) { state.updateNote { it.withColor(color) } },
                    ) {
                        if (color == options.color) Box(Modifier.size(35.dp).border(5.dp, colors.swatchRing, CircleShape))
                        ColorDot(color)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

private class Segment(val icon: ImageVector, val label: Int, val on: Boolean, val onClick: () -> Unit)

/** Joined buttons in one outlined capsule, as the sticky note's panel has for style and alignment. */
@Composable
private fun Segments(segments: List<Segment>) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .border(1.dp, colors.label, shape),
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(colors.label))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(if (segment.on) colors.toolSelected else Color.Transparent)
                    .clickable(role = Role.Button, onClick = segment.onClick),
            ) {
                Icon(segment.icon, stringResource(segment.label), tint = colors.icon, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The table's row: its panel, three border colors, a way to change them, a new table, and options. */
@Composable
internal fun TableRow(state: EditorState, options: TableOptions, modifier: Modifier) {
    Pill(modifier) {
        ToolbarIcon(EditorIcons.List, stringResource(R.string.editor_tool_options)) { state.panelOpen = true }
        PillDivider()
        options.colors.forEachIndexed { slot, color ->
            ToolbarCellBox(onClick = { state.updateTable { it.copy(selectedColor = slot) } }) {
                ColorRing(color)
                if (slot == options.selectedColor) SelectedDot()
            }
        }
        ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) {
            state.colorTarget = ColorTarget.TABLE_BORDER
        }
        ToolbarIcon(EditorIcons.EditColor, stringResource(R.string.editor_edit_color)) {
            state.colorTarget = ColorTarget.TABLE_BORDER
        }
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

@Composable
internal fun TablePanel(state: EditorState, options: TableOptions, modifier: Modifier) {
    val colors = LocalEditorColors.current
    PanelSheet(stringResource(R.string.tool_table), onClose = { state.panelOpen = false }, modifier = modifier) {
        DottedPreview {
            val width = size.width * PREVIEW_TABLE_WIDTH
            val height = size.height * PREVIEW_TABLE_HEIGHT
            val left = center.x - width / 2f
            val top = center.y - height / 2f
            val cellW = width / options.columns
            val cellH = height / options.rows
            for (row in 0 until options.rows) {
                for (column in 0 until options.columns) {
                    val isHeader = (options.headerRow && row == 0) || (options.headerColumn && column == 0)
                    val fill = if (isHeader) HEADER_PREVIEW else if (options.fillColor ushr 24 != 0) Color(options.fillColor) else Color.Transparent
                    drawRect(fill, Offset(left + column * cellW, top + row * cellH), Size(cellW, cellH))
                }
            }
            val line = Color(options.color)
            val weight = (options.borderWidth * PREVIEW_BORDER_DP).dp.toPx()
            for (column in 0..options.columns) {
                val x = left + column * cellW
                drawLine(line, Offset(x, top), Offset(x, top + height), weight)
            }
            for (row in 0..options.rows) {
                val y = top + row * cellH
                drawLine(line, Offset(left, y), Offset(left + width, y), weight)
            }
        }
        LabeledRow(R.string.panel_header_column) {
            PanelSwitch(options.headerColumn, stringResource(R.string.panel_header_column)) { on ->
                state.updateTable { it.copy(headerColumn = on) }
            }
        }
        LabeledRow(R.string.panel_header_row) {
            PanelSwitch(options.headerRow, stringResource(R.string.panel_header_row)) { on ->
                state.updateTable { it.copy(headerRow = on) }
            }
        }
        LabeledRow(R.string.panel_column_count) {
            ValuePill(onClick = { state.updateTable { it.copy(columns = it.columns % TableOptions.MAX_CELLS_PER_SIDE + 1) } }) {
                ValueText(options.columns.toString())
            }
        }
        LabeledRow(R.string.panel_row_count) {
            ValuePill(onClick = { state.updateTable { it.copy(rows = it.rows % TableOptions.MAX_CELLS_PER_SIDE + 1) } }) {
                ValueText(options.rows.toString())
            }
        }
        PanelDivider()
        LabeledRow(R.string.panel_border_width) { ValuePill { ValueText(options.borderWidth.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - 6.dp)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) {
                state.updateTable { it.copy(borderWidth = (it.borderWidth - 1).coerceAtLeast(TableOptions.MIN_BORDER)) }
            }
            PanelSlider(
                fraction = (options.borderWidth - TableOptions.MIN_BORDER).toFloat() / (TableOptions.MAX_BORDER - TableOptions.MIN_BORDER),
                onChange = { f ->
                    val range = TableOptions.MAX_BORDER - TableOptions.MIN_BORDER
                    state.updateTable { it.copy(borderWidth = TableOptions.MIN_BORDER + (f * range).roundToInt()) }
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) {
                state.updateTable { it.copy(borderWidth = (it.borderWidth + 1).coerceAtMost(TableOptions.MAX_BORDER)) }
            }
        }
        LabeledRow(R.string.panel_border_color) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { state.colorTarget = ColorTarget.TABLE_BORDER }, Alignment.Center) {
                ColorRing(options.color)
            }
        }
        LabeledRow(R.string.panel_fill_color) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { state.tapTableFill() }, Alignment.Center) {
                FillSwatch(options.fillColor)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** A text box placed exactly over what it edits, in that text's own size and color. */
@Composable
internal fun TextEditOverlay(request: TextEditRequest, onDone: (String) -> Unit) {
    val density = LocalDensity.current
    var value by remember(request) { mutableStateOf(TextFieldValue(request.text, TextRange(request.text.length))) }
    // Words read from handwriting arrive in the request, and the cursor goes to the end after them.
    LaunchedEffect(request) {
        androidx.compose.runtime.snapshotFlow { request.text }.collect { text ->
            if (text != value.text) value = TextFieldValue(text, TextRange(text.length))
        }
    }
    val focus = remember(request) { FocusRequester() }
    val latest by androidx.compose.runtime.rememberUpdatedState(value.text)
    val latestDone by androidx.compose.runtime.rememberUpdatedState(onDone)
    LaunchedEffect(request) { focus.requestFocus() }
    DisposableEffect(request) {
        request.commitNow = { latestDone(latest) }
        onDispose { request.commitNow = {} }
    }
    val style = request.style
    val decoration = listOfNotNull(
        TextDecoration.Underline.takeIf { style.underline },
        TextDecoration.LineThrough.takeIf { style.strikethrough },
    )
    BasicTextField(
        value = value,
        onValueChange = {
            value = it
            request.text = it.text
        },
        onTextLayout = { layout ->
            val end = layout.getCursorRect(layout.layoutInput.text.length)
            request.endX = end.left
            request.endY = end.top
            request.lineHeight = end.height
        },
        cursorBrush = SolidColor(LocalEditorColors.current.accent),
        textStyle = ComposeTextStyle(
            color = Color(request.color),
            fontSize = with(density) { request.textSizePx.toSp() },
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = TextDecoration.combine(decoration),
            textAlign = when (style.align) {
                HorizontalAlign.START -> TextAlign.Start
                HorizontalAlign.CENTER -> TextAlign.Center
                HorizontalAlign.END -> TextAlign.End
            },
        ),
        modifier = Modifier
            .offset { IntOffset(request.left.roundToInt(), request.top.roundToInt()) }
            .size(with(density) { request.width.toDp() }, with(density) { request.height.toDp() })
            // Turned with the page, about the middle of the screen.
            .graphicsLayer {
                rotationZ = request.turnDegrees
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    (request.pivotX - request.left) / size.width,
                    (request.pivotY - request.top) / size.height,
                )
            }
            // The area the text stays in, so that it is plain where typing will go.
            .background(LocalEditorColors.current.accent.copy(alpha = 0.08f))
            .border(1.dp, LocalEditorColors.current.accent.copy(alpha = 0.6f))
            .focusRequester(focus),
    )
}

private val HEADER_PREVIEW = Color(0xFF55565B)
private const val PREVIEW_TABLE_WIDTH = 0.6f
private const val PREVIEW_TABLE_HEIGHT = 0.7f
private const val PREVIEW_BORDER_DP = 0.3f
