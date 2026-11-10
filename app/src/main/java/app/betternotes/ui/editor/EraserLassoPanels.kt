package app.betternotes.ui.editor

import androidx.annotation.StringRes
import kotlinx.coroutines.delay
import app.betternotes.canvas.TapeLook
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.tools.EraserOptions
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.LassoOptions
import app.betternotes.core.tools.toggled
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/** The eraser's row: partial or whole, three sizes, clear page, and the way into its panel. */
@Composable
internal fun EraserRow(state: EditorState, options: EraserOptions, modifier: Modifier) {
    Pill(modifier) {
        ToolbarIcon(
            EditorIcons.EraseMode,
            stringResource(R.string.panel_erase_entire),
            selected = options.whole,
            onClick = { state.updateEraser { it.copy(whole = !it.whole) } },
        )
        PillDivider()
        options.sizes.forEachIndexed { slot, size ->
            ToolbarCellBox(onClick = { state.updateEraser { it.copy(selectedSize = slot) } }) {
                CheckerDot(size, selected = slot == options.selectedSize)
            }
        }
        ToolbarIcon(EditorIcons.Broom, stringResource(R.string.panel_clear_page), onClick = state::clearPage)
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

/** A see-through circle as big as the tip it stands for. */
@Composable
private fun CheckerDot(size: Int, selected: Boolean) {
    val colors = LocalEditorColors.current
    Canvas(Modifier.size(28.dp)) {
        val radius = (DOT_MIN_DP + size * DOT_DP_PER_SIZE).dp.toPx()
        drawChecker(center, radius)
        drawCircle(
            if (selected) colors.accent else colors.icon,
            radius,
            style = Stroke((if (selected) 2.dp else 1.dp).toPx()),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(middle: Offset, radius: Float) {
    val cell = radius / CHECKER_CELLS_PER_RADIUS
    drawCircle(CHECKER_LIGHT, radius, middle)
    val first = -(CHECKER_CELLS_PER_RADIUS)
    for (row in first until CHECKER_CELLS_PER_RADIUS) {
        for (column in first until CHECKER_CELLS_PER_RADIUS) {
            if ((row + column) % 2 != 0) continue
            val x = middle.x + column * cell
            val y = middle.y + row * cell
            val dx = x + cell / 2f - middle.x
            val dy = y + cell / 2f - middle.y
            if (dx * dx + dy * dy < radius * radius) {
                drawRect(CHECKER_DARK, Offset(x, y), Size(cell, cell))
            }
        }
    }
}

@Composable
internal fun EraserPanel(state: EditorState, options: EraserOptions, notices: Notices, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    PanelSheet(stringResource(R.string.tool_eraser), onClose = { state.panelOpen = false }, modifier = modifier) {
        DottedPreview {
            val radius = (options.size * PREVIEW_DP_PER_SIZE).dp.toPx()
            drawChecker(center, radius)
            drawCircle(colors.accent, radius, style = Stroke(2.5.dp.toPx()))
        }
        LabeledRow(R.string.panel_size) { ValuePill { ValueText(options.size.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - 6.dp)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) {
                state.updateEraser { it.withSize(it.size - 1) }
            }
            PanelSlider(
                fraction = (options.size - EraserOptions.MIN_SIZE).toFloat() / (EraserOptions.MAX_SIZE - EraserOptions.MIN_SIZE),
                onChange = { f ->
                    val range = EraserOptions.MAX_SIZE - EraserOptions.MIN_SIZE
                    state.updateEraser { it.withSize(EraserOptions.MIN_SIZE + (f * range).roundToInt()) }
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) {
                state.updateEraser { it.withSize(it.size + 1) }
            }
        }
        PanelDivider()
        LabeledRow(R.string.panel_erase_entire) {
            PanelSwitch(options.whole, stringResource(R.string.panel_erase_entire)) { on ->
                state.updateEraser { it.copy(whole = on) }
            }
        }
        LabeledRow(R.string.panel_erase_locked) {
            PanelSwitch(options.lockedToo, stringResource(R.string.panel_erase_locked)) { on ->
                state.updateEraser { it.copy(lockedToo = on) }
            }
        }
        LabeledRow(R.string.panel_auto_deselect, info = { notices.say(R.string.info_not_built_setting) }) {
            PanelSwitch(options.autoDeselect, stringResource(R.string.panel_auto_deselect)) { on ->
                state.updateEraser { it.copy(autoDeselect = on) }
            }
        }
        LabeledRow(R.string.panel_erase_following) {}
        KindFilters(options.kinds) { kind -> state.updateEraser { it.copy(kinds = it.kinds.toggled(kind)) } }
        PanelDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = state::clearPage)
                .padding(horizontal = PanelPadding, vertical = 14.dp),
        ) {
            Icon(EditorIcons.Broom, null, tint = CLEAR_COLOR, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.panel_clear_page),
                color = CLEAR_COLOR,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** The lasso's row: freeform or rectangle, locked items, screenshot, and the way into its panel. */
@Composable
internal fun LassoRow(state: EditorState, options: LassoOptions, modifier: Modifier) {
    Pill(modifier) {
        ToolbarIcon(
            EditorIcons.LassoFreeform,
            stringResource(R.string.panel_freeform),
            accent = !options.rectangle,
            onClick = { state.updateLasso { it.copy(rectangle = false) } },
        )
        ToolbarIcon(
            EditorIcons.LassoRectangle,
            stringResource(R.string.panel_rectangle),
            accent = options.rectangle,
            onClick = { state.updateLasso { it.copy(rectangle = true) } },
        )
        ToolbarIcon(
            EditorIcons.LassoLocked,
            stringResource(R.string.panel_select_locked),
            accent = options.lockedToo,
            onClick = { state.updateLasso { it.copy(lockedToo = !it.lockedToo) } },
        )
        ToolbarIcon(
            EditorIcons.LassoScreenshot,
            stringResource(R.string.panel_take_screenshot),
            accent = options.takeScreenshot,
            onClick = { state.updateLasso { it.copy(takeScreenshot = !it.takeScreenshot) } },
        )
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

@Composable
internal fun LassoPanel(state: EditorState, options: LassoOptions, notices: Notices, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    val curve = remember { PathParser().parsePathString("M0 24C8 16 17 16 26 21S42 30 50 24").toPath() }
    PanelSheet(stringResource(R.string.tool_lasso), onClose = { state.panelOpen = false }, modifier = modifier) {
        DottedPreview {
            val width = size.width * LASSO_PREVIEW_WIDTH
            val height = size.height * LASSO_PREVIEW_HEIGHT
            val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
            drawRect(
                colors.accent,
                topLeft,
                Size(width, height),
                style = Stroke(
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                ),
            )
            val unit = width * LASSO_CURVE_WIDTH / LASSO_CURVE_UNITS
            translateAndScale(center.x - unit * LASSO_CURVE_UNITS / 2f, center.y - unit * LASSO_CURVE_MIDDLE, unit) {
                drawPath(curve, colors.title, style = Stroke(LASSO_CURVE_STROKE, cap = StrokeCap.Round))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding),
        ) {
            ChoiceCard(EditorIcons.LassoFreeform, R.string.panel_freeform, !options.rectangle) {
                state.updateLasso { it.copy(rectangle = false) }
            }
            ChoiceCard(EditorIcons.LassoRectangle, R.string.panel_rectangle, options.rectangle) {
                state.updateLasso { it.copy(rectangle = true) }
            }
        }
        Spacer(Modifier.height(4.dp))
        LabeledRow(R.string.panel_select_locked, info = { notices.say(R.string.info_select_locked) }) {
            PanelSwitch(options.lockedToo, stringResource(R.string.panel_select_locked)) { on ->
                state.updateLasso { it.copy(lockedToo = on) }
            }
        }
        LabeledRow(R.string.panel_take_screenshot, info = { notices.say(R.string.info_not_built_setting) }) {
            PanelSwitch(options.takeScreenshot, stringResource(R.string.panel_take_screenshot)) { on ->
                state.updateLasso { it.copy(takeScreenshot = on) }
            }
        }
        LabeledRow(R.string.panel_full_selection, info = { notices.say(R.string.info_full_selection) }) {
            PanelSwitch(options.fullSelectionOnly, stringResource(R.string.panel_full_selection)) { on ->
                state.updateLasso { it.copy(fullSelectionOnly = on) }
            }
        }
        // With strips of tape selected, their thickness and opacity can be changed here.
        val tape by produceState<TapeLook?>(null) {
            while (true) {
                value = state.selectionTape()
                delay(250)
            }
        }
        var shown by remember { mutableStateOf<TapeLook?>(null) }
        (shown ?: tape)?.let { look ->
            PanelDivider()
            LabeledRow(R.string.tool_tape) {}
            TapeLookSliders(
                thickness = look.thickness,
                opacity = look.opacity,
                onThickness = { value ->
                    shown = look.copy(thickness = value)
                    state.adjustSelectionTape(value, null)
                },
                onOpacity = { value ->
                    shown = look.copy(opacity = value)
                    state.adjustSelectionTape(null, value)
                },
                onFinished = {
                    shown = null
                    state.finishAdjustSelectionTape()
                },
            )
            PanelDivider()
        }
        LabeledRow(R.string.panel_select_following) {}
        KindFilters(options.kinds) { kind -> state.updateLasso { it.copy(kinds = it.kinds.toggled(kind)) } }
        Spacer(Modifier.height(18.dp))
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translateAndScale(
    x: Float,
    y: Float,
    unit: Float,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    drawContext.canvas.save()
    drawContext.transform.translate(x, y)
    drawContext.transform.scale(unit, unit, Offset.Zero)
    draw()
    drawContext.canvas.restore()
}

/** The strip of item kinds under "Erase the following" and "Select the following". */
@Composable
private fun KindFilters(kinds: Set<ItemKind>, onToggle: (ItemKind) -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelPadding)
            .horizontalScroll(rememberScrollState()),
    ) {
        for ((kind, icon, label) in KIND_FILTERS) {
            val on = kind in kinds
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.field else Color.Transparent)
                    .clickable(role = Role.Checkbox, onClickLabel = stringResource(label)) { onToggle(kind) },
            ) {
                Icon(icon, stringResource(label), tint = if (on) colors.accent else colors.icon, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private val KIND_FILTERS: List<Triple<ItemKind, ImageVector, Int>> by lazy {
    listOf(
        Triple(ItemKind.PEN, EditorIcons.Pen, R.string.tool_pen),
        Triple(ItemKind.PENCIL, EditorIcons.Pencil, R.string.tool_pencil),
        Triple(ItemKind.HIGHLIGHTER, EditorIcons.Highlighter, R.string.tool_highlighter),
        Triple(ItemKind.TAPE, EditorIcons.Tape, R.string.tool_tape),
        Triple(ItemKind.SHAPE, EditorIcons.Shapes, R.string.tool_shapes),
        Triple(ItemKind.FILL, EditorIcons.Bucket, R.string.tool_fill),
        Triple(ItemKind.IMAGE, EditorIcons.Image, R.string.tool_image),
        Triple(ItemKind.VIDEO, EditorIcons.AddElement, R.string.tool_add_element),
        Triple(ItemKind.TABLE, EditorIcons.Table, R.string.tool_table),
        Triple(ItemKind.TEXT, EditorIcons.Text, R.string.tool_text),
        Triple(ItemKind.STICKY_NOTE, EditorIcons.StickyNote, R.string.tool_sticky),
    )
}

/** A card with an icon over a label, the kind the pen's types use. */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.ChoiceCard(
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

/** The dotted area at the top of each tool's panel, with a sample drawn on it. */
@Composable
internal fun DottedPreview(sample: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val colors = LocalEditorColors.current
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
        sample()
    }
}

@Composable
internal fun PanelDivider() {
    Spacer(Modifier.height(14.dp))
    Box(
        Modifier
            .padding(horizontal = PanelPadding)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalEditorColors.current.panelDivider),
    )
    Spacer(Modifier.height(6.dp))
}

private val CHECKER_LIGHT = Color(0xFFD9D9DC)
private val CHECKER_DARK = Color(0xFF8D8D93)
private val CLEAR_COLOR = Color(0xFFF2A49B)
private const val CHECKER_CELLS_PER_RADIUS = 2
private const val DOT_MIN_DP = 3f
private const val DOT_DP_PER_SIZE = 1f
private const val PREVIEW_DP_PER_SIZE = 3.5f
private const val LASSO_PREVIEW_WIDTH = 0.65f
private const val LASSO_PREVIEW_HEIGHT = 0.8f
private const val LASSO_CURVE_WIDTH = 0.85f
private const val LASSO_CURVE_UNITS = 50f
private const val LASSO_CURVE_MIDDLE = 22f
private const val LASSO_CURVE_STROKE = 3f
