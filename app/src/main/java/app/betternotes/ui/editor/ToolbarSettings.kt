package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import app.betternotes.core.tools.ToolbarEntries
import app.betternotes.core.tools.ToolbarGroups
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.betternotes.R
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.DarkEditorColors
import app.betternotes.ui.theme.EditorColors
import app.betternotes.ui.theme.LightEditorColors
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OPAQUE_ALPHA
import app.betternotes.ui.theme.RGB_MASK
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.Swatches
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors


/**
 * The editor colors with the toolbars painted [toolbar]. The icons and the lines in the toolbar follow the
 * color's lightness, so a white bar gets dark icons and a dark one light icons whatever the app's theme is.
 */
internal fun EditorColors.withToolbar(toolbar: Int?): EditorColors {
    if (toolbar == null) return this
    val opaque = toolbar or OPAQUE_ALPHA
    val light = ColorUtils.calculateLuminance(opaque) > LIGHT_LUMINANCE
    val edge = if (light) UiColors.Black else UiColors.White
    // A light bar takes the light theme's icons and a dark bar the dark theme's, whatever the app's theme is.
    val on = if (light) LightEditorColors else DarkEditorColors
    fun shade(amount: Float) = Color(ColorUtils.blendARGB(opaque, edge.toArgb(), amount))
    return copy(
        pill = Color(opaque),
        pillBorder = shade(0.10f),
        pillDivider = shade(0.22f),
        raisedButton = shade(0.10f),
        icon = on.icon,
        iconDisabled = on.iconDisabled,
        toolSelected = on.toolSelected,
        toolSelectedIcon = on.toolSelectedIcon,
        swatchEdge = on.swatchEdge,
    )
}

/** Paints the toolbars inside with the color the owner chose for them. Panels outside keep the theme's colors. */
@Composable
internal fun ToolbarPaint(state: EditorState, content: @Composable () -> Unit) {
    val base = LocalEditorColors.current
    val toolbar = state.toolbarColor
    val colors = remember(base, toolbar) { base.withToolbar(toolbar) }
    CompositionLocalProvider(LocalEditorColors provides colors, content = content)
}

/** The toolbar's settings, opened by the cog after the laser. Only the color works so far. */
@Composable
internal fun ToolbarSettingsPanel(state: EditorState, notices: Notices, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    PanelSheet(stringResource(R.string.toolbar_settings_title), onClose = { state.toolbarSettingsOpen = false }, modifier = modifier) {
        LabeledRow(R.string.toolbar_move) {
            PanelSwitch(state.moveToolbar, stringResource(R.string.toolbar_move), state::chooseMoveToolbar)
        }
        if (state.moveToolbar) {
            Text(
                stringResource(R.string.toolbar_move_hint),
                color = colors.label,
                fontSize = TextSize.S13,
                modifier = Modifier.padding(horizontal = PanelPadding).padding(bottom = Spacing.S8),
            )
        }
        GridRows(state)
        OrganizeRow(state)
        Box(Modifier.padding(horizontal = PanelPadding, vertical = Spacing.S6).fillMaxWidth().height(1.dp).background(colors.panelDivider))
        Text(
            stringResource(R.string.toolbar_color),
            color = colors.label,
            fontSize = TextSize.S14,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = PanelPadding, top = Spacing.S8, bottom = Spacing.S8),
        )
        ToolbarColorRow(state)
        HexField(state)
    }
}

/** The grid switch. Its two patterns show only while it is on, and either or both may be chosen. */
@Composable
private fun GridRows(state: EditorState) {
    val grid = state.grid
    LabeledRow(R.string.toolbar_grid) {
        PanelSwitch(grid.on, stringResource(R.string.toolbar_grid)) { on -> state.updateGrid { it.copy(on = on) } }
    }
    if (!grid.on) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S10),
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding, vertical = Spacing.S4),
    ) {
        GridChoice(EditorIcons.GridCross, R.string.toolbar_grid_cross, grid.cross) { state.updateGrid { it.copy(cross = !it.cross) } }
        GridChoice(EditorIcons.GridThirds, R.string.toolbar_grid_thirds, grid.thirds) { state.updateGrid { it.copy(thirds = !it.thirds) } }
    }
}

@Composable
private fun GridChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, on: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(AppShapes.Rounded14)
            .background(if (on) colors.accentContainer else colors.field)
            .border(if (on) 2.dp else 1.dp, if (on) colors.accent else colors.panelDivider, AppShapes.Rounded14)
            .clickable(role = Role.Checkbox, onClickLabel = stringResource(label), onClick = onClick),
    ) {
        androidx.compose.material3.Icon(icon, stringResource(label), tint = if (on) colors.accent else colors.icon, modifier = Modifier.size(26.dp))
    }
}

/** The way into the menu that sets where the toolbars sit and in what order their tools come. */
@Composable
private fun OrganizeRow(state: EditorState) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(49.dp)
            .clickable(role = Role.Button) {
                state.toolbarSettingsOpen = false
                state.customizeOpen = true
            }
            .padding(horizontal = PanelPadding),
    ) {
        Text(stringResource(R.string.toolbar_organize_icons), color = colors.title, fontSize = TextSize.S15, modifier = Modifier.weight(1f))
        androidx.compose.material3.Icon(EditorIcons.ChevronRight, null, tint = colors.label, modifier = Modifier.size(20.dp))
    }
}

/** The Customize toolbar menu: where the toolbars sit, and the tools in order, each of which can be hidden. */
@Composable
internal fun CustomizeToolbarSheet(state: EditorState, modifier: Modifier = Modifier) {
    // The list takes what is left of the sheet and scrolls in it, so the sheet itself does not.
    PanelSheet(stringResource(R.string.toolbar_customize_title), onClose = { state.customizeOpen = false }, modifier = modifier, scrolls = false, width = CustomizeWidth) {
        PositionRow(state)
        ReorderRows(state, Modifier.weight(1f))
        val colors = LocalEditorColors.current
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = PanelPadding)
                .padding(bottom = Spacing.S14)
                .fillMaxWidth()
                .height(48.dp)
                .clip(AppShapes.Rounded24)
                .background(colors.accentContainer)
                .clickable(role = Role.Button, onClick = state::resetToolbarOrganization),
        ) {
            Text(stringResource(R.string.toolbar_reset), color = colors.accent, fontSize = TextSize.S15, fontWeight = FontWeight.Medium)
        }
    }
}

/** Where the toolbars sit: the same four edges the toolbar can be dragged to. */
@Composable
private fun PositionRow(state: EditorState) {
    val colors = LocalEditorColors.current
    var open by remember { mutableStateOf(false) }
    val edge = state.toolbarEdge
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(49.dp).padding(horizontal = PanelPadding),
    ) {
        Text(stringResource(R.string.toolbar_position), color = colors.title, fontSize = TextSize.S15, modifier = Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(AppShapes.Rounded12).clickable(role = Role.DropdownList) { open = true }.padding(horizontal = Spacing.S8, vertical = Spacing.S8),
            ) {
                Text(stringResource(edgeName(edge)), color = colors.title, fontSize = TextSize.S15)
                androidx.compose.material3.Icon(EditorIcons.ChevronDown, null, tint = colors.title, modifier = Modifier.padding(start = Spacing.S6).size(18.dp))
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (choice in ToolbarEdge.entries) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(edgeName(choice))) },
                        onClick = {
                            state.chooseToolbarEdge(choice)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

private fun edgeName(edge: ToolbarEdge) = when (edge) {
    ToolbarEdge.TOP -> R.string.toolbar_edge_top
    ToolbarEdge.BOTTOM -> R.string.toolbar_edge_bottom
    ToolbarEdge.LEFT -> R.string.toolbar_edge_left
    ToolbarEdge.RIGHT -> R.string.toolbar_edge_right
}

private val ReorderRowHeight = 48.dp
private val ReorderRowGap = 8.dp
private val ReorderIndent = 18.dp
/** The Customize toolbar menu is about 30% wider than the other panels. */
private val CustomizeWidth = PanelWidth * 1.3f

/** A row of the list: a tool, and where it is in the arrangement. */
private class ReorderSpec(val key: String, val entry: Int, val member: Int, val size: Int)

private fun flatten(entries: ToolbarEntries): List<ReorderSpec> =
    entries.flatMapIndexed { e, keys -> keys.mapIndexed { m, key -> ReorderSpec(key, e, m, keys.size) } }

/**
 * Where a tool held at [y], measured down the list, would go, with the rows as they are without it. The middle of a row is for joining it,
 * its top and bottom edges are for going in before or after it.
 */
private fun dropAt(rest: ToolbarEntries, y: Float, step: Float, row: Float): ToolbarGroups.Drop {
    val rows = flatten(rest)
    if (y < 0f) return ToolbarGroups.Drop.Alone(0)
    val slot = (y / step).toInt()
    if (slot >= rows.size) return ToolbarGroups.Drop.Alone(rest.size)
    val spec = rows[slot]
    val within = (y - slot * step) / row
    val grouped = spec.size > 1
    return when {
        within < EDGE_OF_ROW -> if (grouped && spec.member > 0) ToolbarGroups.Drop.InGroup(spec.entry, spec.member) else ToolbarGroups.Drop.Alone(spec.entry)
        within > 1f - EDGE_OF_ROW ->
            if (grouped && spec.member < spec.size - 1) ToolbarGroups.Drop.InGroup(spec.entry, spec.member + 1) else ToolbarGroups.Drop.Alone(spec.entry + 1)
        grouped -> ToolbarGroups.Drop.InGroup(spec.entry, spec.member)
        else -> ToolbarGroups.Drop.With(spec.entry)
    }
}

private const val EDGE_OF_ROW = 0.3f

/**
 * Every tool of the strip as a row. Holding a row anywhere but its eye picks it up, and the others move to make room as it is dragged. Over
 * the middle of another row it makes a group with it, shown as rows set in; dragged out from among a group's rows it leaves the group.
 */
@Composable
private fun ReorderRows(state: EditorState, modifier: Modifier) {
    val colors = LocalEditorColors.current
    val byKey = remember { StripItems.associateBy { it.key } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowPx = with(density) { ReorderRowHeight.toPx() }
    val step = with(density) { (ReorderRowHeight + ReorderRowGap).toPx() }
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabbedAt by remember { mutableFloatStateOf(0f) }
    val layout = state.toolbarLayout
    val held = dragging
    val drop = if (held != null) dropAt(ToolbarGroups.without(layout, held), pointerY, step, rowPx) else null
    val shown = if (held != null && drop != null) ToolbarGroups.move(layout, held, drop) else layout

    // Near the top or bottom of the list it scrolls itself, so a tool can be taken a long way.
    LaunchedEffect(held) {
        if (held == null) return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        while (true) {
            val seen = pointerY - scroll.value
            val delta = when {
                seen < edge -> -(edge - seen).coerceAtMost(edge) / edge * 24f
                seen > viewport - edge -> (seen - (viewport - edge)).coerceAtMost(edge) / edge * 24f
                else -> 0f
            }
            if (delta != 0f) pointerY += scroll.scrollBy(delta)
            kotlinx.coroutines.delay(16)
        }
    }

    Text(
        stringResource(R.string.toolbar_reorder),
        color = colors.title,
        fontSize = TextSize.S15,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = PanelPadding).padding(top = Spacing.S6, bottom = Spacing.S10),
    )
    Box(
        modifier
            .padding(horizontal = PanelPadding)
            .padding(bottom = Spacing.S10)
            .fillMaxWidth()
            .onSizeChanged { viewport = it.height }
            .verticalScroll(scroll),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ReorderRowGap),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    val eyeFrom = size.width - with(density) { 88.dp.toPx() }
                    val eyeTo = size.width - with(density) { 44.dp.toPx() }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { at ->
                            val rows = flatten(state.toolbarLayout)
                            val slot = (at.y / step).toInt()
                            // The eye is for hiding, not for holding.
                            if (slot in rows.indices && at.x !in eyeFrom..eyeTo) {
                                dragging = rows[slot].key
                                pointerY = at.y
                                grabbedAt = at.y - slot * step
                            }
                        },
                        onDragEnd = {
                            val key = dragging
                            if (key != null) {
                                state.moveToolbarTool(key, dropAt(ToolbarGroups.without(state.toolbarLayout, key), pointerY, step, rowPx))
                            }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, drag ->
                        if (dragging != null) {
                            change.consume()
                            pointerY += drag.y
                        }
                    }
                },
        ) {
            for (spec in flatten(shown)) {
                val item = byKey[spec.key] ?: continue
                androidx.compose.runtime.key(spec.key) {
                    ReorderRow(item, state, grouped = spec.size > 1, placeholder = spec.key == held, floating = false)
                }
            }
        }
        // The tool in hand, following the finger over the rows.
        val inHand = held?.let { byKey[it] }
        if (inHand != null) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(0, (pointerY - grabbedAt).roundToInt()) }
                    .zIndex(1f),
            ) { ReorderRow(inHand, state, grouped = false, placeholder = false, floating = true) }
        }
    }
}

@Composable
private fun ReorderRow(item: StripItem, state: EditorState, grouped: Boolean, placeholder: Boolean, floating: Boolean) {
    val colors = LocalEditorColors.current
    val hidden = item.key in state.hiddenTools
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = if (grouped) ReorderIndent else 0.dp)
            .fillMaxWidth()
            .height(ReorderRowHeight)
            .graphicsLayer { alpha = if (placeholder) 0.35f else 1f }
            .clip(AppShapes.Rounded16)
            .background(if (floating) colors.raisedButton else colors.field)
            .then(if (grouped) Modifier.border(1.dp, colors.accent.copy(alpha = 0.5f), AppShapes.Rounded16) else Modifier)
            .padding(start = Spacing.S12),
    ) {
        androidx.compose.material3.Icon(item.icon, null, tint = if (hidden) colors.iconDisabled else colors.icon, modifier = Modifier.size(24.dp))
        Text(
            stringResource(item.label),
            color = if (hidden) colors.iconDisabled else colors.title,
            fontSize = TextSize.S15,
            modifier = Modifier.weight(1f).padding(start = Spacing.S12),
        )
        val canHide = item.key !in ALWAYS_SHOWN_TOOLS
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = canHide && !floating, role = Role.Button) { state.toggleToolHidden(item.key) },
        ) {
            androidx.compose.material3.Icon(
                if (hidden) EditorIcons.Eye else EditorIcons.EyeOff,
                stringResource(if (hidden) R.string.toolbar_tool_show else R.string.toolbar_tool_hide),
                tint = if (canHide) colors.icon else colors.iconDisabled,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
            androidx.compose.material3.Icon(EditorIcons.Grip, null, tint = colors.icon, modifier = Modifier.size(22.dp))
        }
    }
}

/** The theme's own color, the presets and a custom one chosen on the wheel. */
@Composable
private fun ToolbarColorRow(state: EditorState) {
    val colors = LocalEditorColors.current
    val current = state.toolbarColor
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S10),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PanelPadding, vertical = Spacing.S4),
    ) {
        Swatch(null, current == null, stringResource(R.string.toolbar_color_default), colors.raisedButton) { state.chooseToolbarColor(null) }
        for (preset in Swatches.Toolbar) {
            Swatch(preset, current == preset, hex(preset), Color(preset)) { state.chooseToolbarColor(preset) }
        }
        val custom = current != null && current !in Swatches.Toolbar
        Swatch(
            if (custom) current else null,
            custom,
            stringResource(R.string.toolbar_color_custom),
            colors.field,
            label = "+",
        ) { state.colorTarget = ColorTarget.TOOLBAR }
    }
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, description: String, fill: Color, label: String? = null, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(AppShapes.Circle)
            .background(color?.let { Color(it) } ?: fill)
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.swatchRing, AppShapes.Circle)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
    ) {
        if (label != null && color == null) Text(label, color = colors.label, fontSize = TextSize.S20)
    }
}

/** Types the color as hex: six digits for red, green and blue, optionally after a #. */
@Composable
private fun HexField(state: EditorState) {
    val colors = LocalEditorColors.current
    var text by remember(state.toolbarColor) { mutableStateOf(state.toolbarColor?.let(::hex).orEmpty()) }
    val parsed = parseHex(text)
    fun commit() = parsed?.let(state::chooseToolbarColor)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding, vertical = Spacing.S10),
    ) {
        Text(stringResource(R.string.toolbar_color_hex), color = colors.label, fontSize = TextSize.S14, modifier = Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .size(width = 130.dp, height = 40.dp)
                .clip(AppShapes.Rounded12)
                .background(colors.field)
                .border(1.dp, if (text.isEmpty() || parsed != null) colors.panelDivider else colors.accent, AppShapes.Rounded12)
                .padding(horizontal = Spacing.S12),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_HEX_CHARS) },
                singleLine = true,
                textStyle = TextStyle(color = colors.title, fontSize = TextSize.S15),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = Spacing.S10)
                .height(40.dp)
                .clip(AppShapes.Rounded20)
                .background(if (parsed != null) colors.accentContainer else colors.field)
                .clickable(enabled = parsed != null, role = Role.Button) { commit() }
                .padding(horizontal = Spacing.S16),
        ) {
            Text(
                stringResource(R.string.toolbar_color_apply),
                color = if (parsed != null) colors.accent else colors.iconDisabled,
                fontSize = TextSize.S14,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun hex(color: Int) = "#%06X".format(color and RGB_MASK)

/** Reads "#RRGGBB" or "RRGGBB" as an opaque color, or null when it is not one. */
internal fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != HEX_DIGITS || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return digits.toInt(HEX_RADIX) or OPAQUE_ALPHA
}

private const val HEX_DIGITS = 6
private const val HEX_RADIX = 16
private const val MAX_HEX_CHARS = 7
private const val LIGHT_LUMINANCE = 0.5
