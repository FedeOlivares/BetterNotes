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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.foundation.Canvas
import app.betternotes.R
import app.betternotes.canvas.TextEditRequest
import app.betternotes.canvas.drawListMark
import app.betternotes.canvas.listMargin
import app.betternotes.core.model.ListMarkers
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListStyle
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.TextStyle
import app.betternotes.fonts.FontLibrary
import androidx.compose.ui.text.font.FontFamily
import app.betternotes.core.model.TextSpans
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import app.betternotes.core.model.VerticalAlign
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.ALPHA_SHIFT
import app.betternotes.ui.theme.OPAQUE_ALPHA
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.Swatches
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import androidx.compose.ui.graphics.toArgb
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
            ToolbarCellBox(onClick = { state.panelOpen = true }) { ValueText(state.shownTextSize(options).toString()) }
            val shown = state.shownTextStyle(options)
            StyleButtons(shown, state::toggleStyle, state::updateNoteStyle)
            if (!showColors) {
                // The colors of the letters; the background of the box is a setting of the tool, in its panel.
                PillDivider()
                val textColor = state.shownTextColor(options)
                for (color in Swatches.TextRow) {
                    ToolbarCellBox(onClick = { state.setTextColor(color) }) {
                        ColorDot(color, diameter = 24.dp)
                        if (color == (textColor or OPAQUE_ALPHA)) SelectedDot()
                    }
                }
                ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = ColorTarget.TEXT_COLOR }
            }
            PillDivider()
            ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
                state.panelOpen = !state.panelOpen
            }
        }
    }
}

/** Bold, italic, underline, strike and the two alignments, applied through [update]. */
@Composable
private fun StyleButtons(style: TextStyle, toggle: (StyleKind) -> Unit, update: (((TextStyle) -> TextStyle)) -> Unit) {
    StyleToggle(EditorIcons.Bold, R.string.style_bold, style.bold) { toggle(StyleKind.BOLD) }
    StyleToggle(EditorIcons.Italic, R.string.style_italic, style.italic) { toggle(StyleKind.ITALIC) }
    StyleToggle(EditorIcons.Underline, R.string.style_underline, style.underline) { toggle(StyleKind.UNDERLINE) }
    StyleToggle(EditorIcons.Strike, R.string.style_strike, style.strikethrough) { toggle(StyleKind.STRIKE) }
    ToolbarIcon(style.align.icon(), stringResource(R.string.panel_horizontal_alignment)) {
        update { it.copy(align = HorizontalAlign.entries[(it.align.ordinal + 1) % HorizontalAlign.entries.size]) }
    }
    ToolbarIcon(style.verticalAlign.icon(), stringResource(R.string.panel_vertical_alignment)) {
        update { it.copy(verticalAlign = VerticalAlign.entries[(it.verticalAlign.ordinal + 1) % VerticalAlign.entries.size]) }
    }
    ToolbarIcon(EditorIcons.List, stringResource(R.string.panel_list), accent = style.list != null) {
        update { it.copy(list = if (it.list == null) ListStyle() else null) }
    }
}

/** The row of post-its that are selected: their color and how see-through they are, which change those post-its. */
@Composable
internal fun StickySelectionRow(state: EditorState, notes: List<StickyNoteItem>, modifier: Modifier) {
    Pill(modifier) {
        ToolStrip {
            for (color in NoteOptions.STICKY_PALETTE) {
                val chosen = notes.all { (it.color and NoteOptions.RGB_MASK) == (color and NoteOptions.RGB_MASK) }
                ToolbarCellBox(onClick = { state.pickSelectedNotesColor(color) }) {
                    ColorDot(color, diameter = 24.dp)
                    if (chosen) SelectedDot()
                }
            }
            ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) {
                state.colorTarget = ColorTarget.SELECTED_NOTE
            }
            PillDivider()
            // The slider shows the first post-it's opacity, and moves them all to the one it is set to.
            var shown by remember { mutableStateOf<Float?>(null) }
            val opacity = shown ?: NoteOptions.alphaOf(notes.first().color)
            ValueText("${(opacity * 100f).roundToInt()}%")
            StepperSlider(
                fraction = (opacity - NoteOptions.MIN_OPACITY) / (1f - NoteOptions.MIN_OPACITY),
                onChange = { f ->
                    val next = opacityAt(f)
                    shown = next
                    state.adjustSelectedNotesOpacity(next)
                },
                steps = OPACITY_STEPS,
                onChangeFinished = {
                    shown = null
                    state.finishSelectionAdjust()
                },
                modifier = Modifier.width(240.dp),
            )
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
                drawRect(Color(NoteOptions.argb(options.color, options.opacityOf(options.color, state.stickyOpacity))), Offset(left, top), Size(side, side))
                val cut = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left + side - fold, top + side)
                    lineTo(left + side, top + side - fold / 2f)
                    lineTo(left + side, top + side)
                    close()
                }
                drawPath(cut, colors.panel)
            }
        }
        if (!sticky) FontSelector(state.shownFont(options), state::setFont)
        val shownSize = state.shownTextSize(options)
        LabeledRow(R.string.panel_font_size) { ValuePill { ValueText(shownSize.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - Spacing.S6)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) {
                state.setTextSize((shownSize - 1).coerceAtLeast(NoteOptions.MIN_TEXT))
            }
            PanelSlider(
                fraction = (shownSize - NoteOptions.MIN_TEXT).toFloat() / (NoteOptions.MAX_TEXT - NoteOptions.MIN_TEXT),
                onChange = { f ->
                    val range = NoteOptions.MAX_TEXT - NoteOptions.MIN_TEXT
                    state.setTextSize(NoteOptions.MIN_TEXT + (f * range).roundToInt())
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) {
                state.setTextSize((shownSize + 1).coerceAtMost(NoteOptions.MAX_TEXT))
            }
        }
        PanelDivider()
        val style = state.shownTextStyle(options)
        LabeledRow(R.string.panel_style) {
            Segments(
                listOf(
                    Segment(EditorIcons.Bold, R.string.style_bold, style.bold) { state.toggleStyle(StyleKind.BOLD) },
                    Segment(EditorIcons.Italic, R.string.style_italic, style.italic) { state.toggleStyle(StyleKind.ITALIC) },
                    Segment(EditorIcons.Underline, R.string.style_underline, style.underline) { state.toggleStyle(StyleKind.UNDERLINE) },
                    Segment(EditorIcons.Strike, R.string.style_strike, style.strikethrough) { state.toggleStyle(StyleKind.STRIKE) },
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
        ListSection(state, style)
        if (!sticky) {
            TextColorSection(state, R.string.panel_text_stroke_color, state.shownTextColor(options), ColorTarget.TEXT_COLOR, withNone = false) { color ->
                state.setTextColor(color)
            }
            TextColorSection(state, R.string.panel_text_background_color, style.backgroundColor, ColorTarget.TEXT_BACKGROUND, withNone = true) { color ->
                state.updateNoteStyle { it.copy(backgroundColor = color) }
            }
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
                    .padding(start = PanelPadding - Spacing.S8, end = PanelPadding - Spacing.S8, bottom = Spacing.S16)
                    .horizontalScroll(rememberScrollState()),
            ) {
                for (color in NoteOptions.STICKY_PALETTE) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).tappable(radius = 20.dp) { state.updateNote { it.withColor(color) } },
                    ) {
                        if (color == options.color) Box(Modifier.size(35.dp).border(5.dp, colors.swatchRing, AppShapes.Circle))
                        ColorDot(color)
                    }
                }
                ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = ColorTarget.NOTE }
            }
            // How see-through new post-its are, unless a color has an opacity of its own, which the color wheel sets.
            val general = state.stickyOpacity
            LabeledRow(R.string.settings_sticky_opacity, info = { notices.say(R.string.settings_sticky_opacity_detail) }) {
                ValuePill { ValueText("${(general * 100f).roundToInt()}%") }
            }
            StepperSlider(
                fraction = (general - NoteOptions.MIN_OPACITY) / (1f - NoteOptions.MIN_OPACITY),
                onChange = { f -> state.setStickyOpacity(opacityAt(f)) },
                steps = OPACITY_STEPS,
                modifier = Modifier.fillMaxWidth(),
            )
            CreateReturnRows(state)
            Spacer(Modifier.height(Spacing.S10))
        } else {
            // The return-to-tool choices belong to the text tool, not to a text box that is selected.
            if (state.selectedText == null) ToolReturnRows(state, ActiveTool.TEXT)
            Spacer(Modifier.height(Spacing.S10))
        }
    }
}

/** Whether the text is a list, and what marks each paragraph: a character, a number or a letter, or a dot of a color, size and opacity. */
@Composable
private fun ListSection(state: EditorState, style: TextStyle) {
    val colors = LocalEditorColors.current
    val list = style.list
    LabeledRow(R.string.panel_list) {
        PanelSwitch(list != null, stringResource(R.string.panel_list)) { on ->
            state.updateNoteStyle { it.copy(list = if (on) (it.list ?: ListStyle()) else null) }
        }
    }
    if (list == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PanelPadding - Spacing.S8, end = PanelPadding - Spacing.S8, bottom = Spacing.S8)
            .horizontalScroll(rememberScrollState()),
    ) {
        for (kind in ListKind.entries) {
            val chosen = kind == list.kind
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = Spacing.S2)
                    .size(width = 48.dp, height = 40.dp)
                    .clip(AppShapes.Rounded12)
                    .background(if (chosen) colors.toolSelected else Color.Transparent)
                    .clickable(role = Role.Button) { state.updateNoteStyle { it.copy(list = list.copy(kind = kind)) } },
            ) {
                val sample = ListMarkers.label(kind, 0)
                if (sample != null) {
                    Text(sample, color = colors.title, fontSize = TextSize.S16)
                } else {
                    Box(Modifier.size(10.dp).clip(AppShapes.Circle).background(Color(list.dotArgb)))
                }
            }
        }
    }
    if (list.kind != ListKind.DOT) return
    LabeledRow(R.string.panel_list_dot_color) {}
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PanelPadding - Spacing.S8, end = PanelPadding - Spacing.S8, bottom = Spacing.S8)
            .horizontalScroll(rememberScrollState()),
    ) {
        for (color in LIST_DOT_COLORS) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).tappable(radius = 20.dp) {
                    state.updateNoteStyle { it.copy(list = list.copy(dotColor = color)) }
                },
            ) {
                if (color == (list.dotColor or OPAQUE_ALPHA)) Box(Modifier.size(35.dp).border(5.dp, colors.swatchRing, AppShapes.Circle))
                ColorDot(color)
            }
        }
        ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = ColorTarget.LIST_DOT }
    }
    LabeledRow(R.string.panel_list_dot_size) { ValuePill { ValueText("${(list.dotSize * 100f).roundToInt()}%") } }
    PanelSlider(
        fraction = (list.dotSize - ListStyle.MIN_DOT_SIZE) / (ListStyle.MAX_DOT_SIZE - ListStyle.MIN_DOT_SIZE),
        onChange = { f ->
            val size = ListStyle.MIN_DOT_SIZE + f * (ListStyle.MAX_DOT_SIZE - ListStyle.MIN_DOT_SIZE)
            state.updateNoteStyle { it.copy(list = list.copy(dotSize = size).clamped()) }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding),
    )
    LabeledRow(R.string.panel_list_dot_opacity) { ValuePill { ValueText("${(list.dotOpacity * 100f).roundToInt()}%") } }
    PanelSlider(
        fraction = list.dotOpacity,
        onChange = { f -> state.updateNoteStyle { it.copy(list = list.copy(dotOpacity = f).clamped()) } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding),
    )
}

/** A color of the text for [label]: none, a few colors, and the wheel. [set] gets 0 for none. */
@Composable
private fun TextColorSection(state: EditorState, label: Int, chosen: Int, target: ColorTarget, withNone: Boolean, set: (Int) -> Unit) {
    val colors = LocalEditorColors.current
    LabeledRow(label) {}
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PanelPadding - Spacing.S8, end = PanelPadding - Spacing.S8, bottom = Spacing.S8)
            .horizontalScroll(rememberScrollState()),
    ) {
        val none = chosen ushr ALPHA_SHIFT == 0
        if (withNone) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).tappable(radius = 20.dp) { set(0) },
            ) {
                if (none) Box(Modifier.size(35.dp).border(5.dp, colors.swatchRing, AppShapes.Circle))
                FillSwatch(0)
            }
        }
        for (color in LIST_DOT_COLORS + UiColors.White.toArgb()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).tappable(radius = 20.dp) { set(color) },
            ) {
                if (!none && (chosen or OPAQUE_ALPHA) == color) Box(Modifier.size(35.dp).border(5.dp, colors.swatchRing, AppShapes.Circle))
                ColorDot(color)
            }
        }
        ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.colorTarget = target }
    }
}

private val LIST_DOT_COLORS = Swatches.ListDots

private class Segment(val icon: ImageVector, val label: Int, val on: Boolean, val onClick: () -> Unit)

/** Joined buttons in one outlined capsule, as the sticky note's panel has for style and alignment. */
@Composable
private fun Segments(segments: List<Segment>) {
    val colors = LocalEditorColors.current
    val shape = AppShapes.Rounded18
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
                    val fill = if (isHeader) UiColors.TableHeaderPreview else if (options.fillColor ushr 24 != 0) Color(options.fillColor) else Color.Transparent
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - Spacing.S6)) {
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
            Box(Modifier.size(36.dp).clip(AppShapes.Circle).clickable { state.colorTarget = ColorTarget.TABLE_BORDER }, Alignment.Center) {
                ColorRing(options.color)
            }
        }
        LabeledRow(R.string.panel_fill_color) {
            Box(Modifier.size(36.dp).clip(AppShapes.Circle).clickable { state.tapTableFill() }, Alignment.Center) {
                FillSwatch(options.fillColor)
            }
        }
        LabeledRow(R.string.panel_header_color) {
            Box(Modifier.size(36.dp).clip(AppShapes.Circle).clickable { state.colorTarget = ColorTarget.TABLE_HEADER }, Alignment.Center) {
                FillSwatch(options.headerColor)
            }
        }
        CreateReturnRows(state)
        Spacer(Modifier.height(Spacing.S10))
    }
}

/** How long the writing rests for a block of it to be done, in milliseconds. */
private const val BLOCK_PAUSE_MS = 1500L

/** A text box placed exactly over what it edits, in that text's own size and color. */
@Composable
internal fun TextEditOverlay(request: TextEditRequest, onDone: (String) -> Unit) {
    val density = LocalDensity.current
    var value by remember(request) { mutableStateOf(TextFieldValue(request.text, TextRange(request.text.length))) }
    // Words read from handwriting arrive in the request, and the cursor goes to the end after them.
    LaunchedEffect(request) {
        androidx.compose.runtime.snapshotFlow { request.text }.collect { text ->
            if (text != value.text) {
                val before = value.text
                request.spans = TextSpans.retext(request.spans, value.text, text)
                value = TextFieldValue(text, TextRange(text.length))
                request.colorTyped(before)
            }
        }
    }
    // The app reads handwriting itself. Android's own stylus writing in a text field would take the pen over when it writes near the box.
    val hostView = androidx.compose.ui.platform.LocalView.current
    SideEffect { hostView.setAutoHandwritingEnabled(false) }
    // A block of writing ends when it pauses: the box is put on the page then as one step of undo, and goes on.
    LaunchedEffect(request, request.text, request.spans, request.style, request.color) {
        kotlinx.coroutines.delay(BLOCK_PAUSE_MS)
        request.finishBlock()
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
    val spans = request.spans
    // Read so that the text follows a font being added or removed.
    val fontNames = FontLibrary.names
    val wholeFont = FontLibrary.family(style.font, style.bold, style.italic)
    val pxPerPt = request.textSizePx / request.sizePt.coerceAtLeast(1f)
    val transformation = remember(spans, style, pxPerPt, density, fontNames) {
        VisualTransformation { text ->
            val styles = spans.mapNotNull { span ->
                val end = span.end.coerceAtMost(text.length)
                if (span.start >= end) return@mapNotNull null
                val look = span.look
                val lines = listOfNotNull(
                    TextDecoration.Underline.takeIf { look.underline ?: style.underline },
                    TextDecoration.LineThrough.takeIf { look.strikethrough ?: style.strikethrough },
                )
                val bold = look.bold ?: style.bold
                val italic = look.italic ?: style.italic
                val added = FontLibrary.family(look.font ?: style.font, bold, italic)
                AnnotatedString.Range(
                    SpanStyle(
                        color = look.color?.let { Color(it) } ?: Color.Unspecified,
                        // An added font is given its bold and italic face whole, so Compose must not style it again.
                        fontWeight = if (added != null) FontWeight.Normal else look.bold?.let { if (it) FontWeight.Bold else FontWeight.Normal },
                        fontStyle = if (added != null) FontStyle.Normal else look.italic?.let { if (it) FontStyle.Italic else FontStyle.Normal },
                        fontFamily = added ?: if ((look.font ?: style.font).isEmpty()) FontFamily.Default else null,
                        textDecoration = if (look.underline != null || look.strikethrough != null) TextDecoration.combine(lines) else null,
                        fontSize = look.size?.let { with(density) { (it * pxPerPt).toSp() } } ?: TextUnit.Unspecified,
                    ),
                    span.start,
                    end,
                )
            }
            TransformedText(AnnotatedString(text.text, styles), OffsetMapping.Identity)
        }
    }
    // A list keeps a margin on every paragraph, and the markers are drawn in it from where each paragraph starts.
    val list = style.list
    var textLayout by remember(request) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val listFace = remember(style.font, style.bold, style.italic) {
        FontLibrary.face(style.font, style.bold, style.italic)
    }
    // The scribble that rubs out typed letters asks where each one lies.
    SideEffect {
        request.charBox = { index ->
            textLayout?.takeIf { index < it.layoutInput.text.length }?.getBoundingBox(index)?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) }
        }
    }
    val marginPx = list?.let { listMargin(it, ListMarkers.paragraphs(value.text), request.textSizePx, listFace) } ?: 0
    val markInk = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val decoration = listOfNotNull(
        TextDecoration.Underline.takeIf { style.underline },
        TextDecoration.LineThrough.takeIf { style.strikethrough },
    )
    // Android's stylus writing in a text field starts when the pen moves on or just outside the field, which Compose
    // allows for through a margin around it. The field sits in a box of its own size, so the margin lies outside and
    // those touches reach the page, where the app's handwriting reads them. On the field the slop is never reached.
    val viewConfiguration = LocalViewConfiguration.current
    val noStylusWriting = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val handwritingSlop: Float get() = Float.MAX_VALUE
        }
    }
    // The box keeps its place and size on the page when zoomed in past the screen: its parent would hold it to the screen's size, so the
    // box is measured without that limit, from the top left, and only then moved to its place.
    Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart, unbounded = true)) {
    Box(
        modifier = Modifier
            .offset { IntOffset(request.left.roundToInt(), request.top.roundToInt()) }
            // As tall as the box, and taller when the text runs past it, so no line is hidden while the saved copy is.
            .width(with(density) { request.width.toDp() })
            .heightIn(min = with(density) { request.height.toDp() })
            // Turned with the page, about the middle of the screen.
            .graphicsLayer {
                rotationZ = request.turnDegrees
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    (request.pivotX - request.left) / size.width,
                    (request.pivotY - request.top) / size.height,
                )
            }
            .then(if (style.hasBackground) Modifier.background(Color(style.backgroundColor)) else Modifier)
            // The area the text stays in, so that it is plain where typing will go.
            .background(LocalEditorColors.current.accent.copy(alpha = 0.08f))
            .border(1.dp, LocalEditorColors.current.accent.copy(alpha = 0.6f)),
    ) {
    CompositionLocalProvider(LocalViewConfiguration provides noStylusWriting) {
    BasicTextField(
        value = value,
        onValueChange = {
            val before = request.text
            if (it.text != value.text) request.spans = TextSpans.retext(request.spans, value.text, it.text)
            value = it
            request.text = it.text
            request.colorTyped(before)
            request.selectionStart = it.selection.min
            request.selectionEnd = it.selection.max
        },
        visualTransformation = transformation,
        onTextLayout = { layout ->
            textLayout = layout
            val end = layout.getCursorRect(layout.layoutInput.text.length)
            request.endX = end.left
            request.endY = end.top
            request.lineHeight = end.height
            request.contentHeightPx = layout.size.height.toFloat()
        },
        cursorBrush = SolidColor(LocalEditorColors.current.accent),
        textStyle = ComposeTextStyle(
            color = Color(request.color),
            fontSize = with(density) { request.textSizePx.toSp() },
            fontWeight = if (wholeFont != null) FontWeight.Normal else if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (wholeFont != null) FontStyle.Normal else if (style.italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = wholeFont,
            textIndent = if (list != null) {
                val margin = with(density) { marginPx.toSp() }
                androidx.compose.ui.text.style.TextIndent(margin, margin)
            } else {
                null
            },
            textDecoration = TextDecoration.combine(decoration),
            textAlign = when (style.align) {
                HorizontalAlign.START -> TextAlign.Start
                HorizontalAlign.CENTER -> TextAlign.Center
                HorizontalAlign.END -> TextAlign.End
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .drawBehind {
                val layout = textLayout
                if (list == null || layout == null) return@drawBehind
                val text = layout.layoutInput.text.text
                drawIntoCanvas { canvas ->
                    var index = 0
                    var from = 0
                    while (from <= text.length) {
                        val line = layout.getLineForOffset(from.coerceAtMost(text.length))
                        drawListMark(
                            canvas.nativeCanvas, markInk, list, request.color, request.textSizePx, listFace, index,
                            0f, marginPx, layout.getLineBaseline(line),
                        )
                        val next = text.indexOf('\n', from)
                        if (next < 0) break
                        from = next + 1
                        index++
                    }
                }
            },
    )
    }
    }
    }
}

private const val PREVIEW_TABLE_WIDTH = 0.6f
private const val PREVIEW_TABLE_HEIGHT = 0.7f
private const val PREVIEW_BORDER_DP = 0.3f
