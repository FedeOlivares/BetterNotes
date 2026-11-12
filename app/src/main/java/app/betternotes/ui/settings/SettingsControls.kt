package app.betternotes.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import app.betternotes.R
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.DarkSettings
import app.betternotes.ui.theme.LightSettings
import app.betternotes.ui.theme.LocalSettingsColors
import app.betternotes.ui.theme.ReferenceScale
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import app.betternotes.ui.theme.opensFrom
import kotlin.math.min
import kotlin.math.roundToInt

/** Colors plus [ReferenceScale], so a measurement from a screenshot lands in the same place on the tablet. */
@Composable
fun SettingsTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dark) DarkSettings else LightSettings
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, surface = colors.menu, surfaceContainer = colors.menu, onSurface = colors.text)
    } else {
        lightColorScheme(primary = colors.accent, surface = colors.menu, surfaceContainer = colors.menu, onSurface = colors.text)
    }
    MaterialTheme(colorScheme = scheme) {
        ReferenceScale {
            CompositionLocalProvider(LocalSettingsColors provides colors, content = content)
        }
    }
}

/** Line icons drawn for this app on a 24 unit grid. */
private fun icon(name: String, path: String, width: Float = 1.6f) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(UiColors.IconMask),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

internal object SettingsIcons {
    val Back by lazy { icon("back", "M20 12H5M11 6l-6 6 6 6") }
    val Close by lazy { icon("close", "M6.5 6.5l11 11M17.5 6.5l-11 11", 2f) }
    val Down by lazy { icon("down", "M7 10l5 5 5-5", 2f) }
    val Up by lazy { icon("up", "M7 14l5-5 5 5", 2f) }
    val Forward by lazy { icon("forward", "M10 7l5 5-5 5") }
    val Check by lazy { icon("check", "M5.5 12.5l4 4 9-9") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Minus by lazy { icon("minus", "M5 12h14") }
    val Pen by lazy { icon("pen", "M4 20l1-4.5L16.5 4a2.1 2.1 0 0 1 3 3L8 18.8zM14.5 6l3 3") }
    val OneFinger by lazy {
        icon("oneFinger", "M9.5 11V5a1.5 1.5 0 0 1 3 0v6l4.8 1.3a2 2 0 0 1 1.5 2.3L18 20H9.8L6 15.2a1.5 1.5 0 0 1 2.2-2z")
    }
    val TwoFingers by lazy {
        icon(
            "twoFingers",
            "M3.5 8V3.5H8M3.5 3.5l5 5M11 12V8a1.4 1.4 0 0 1 2.8 0v4.5l3.8 1a2 2 0 0 1 1.5 2.3L18.3 21h-7l-3.2-4.2a1.4 1.4 0 0 1 2.1-1.9z",
        )
    }
    val Hand by lazy {
        icon(
            "hand",
            "M8 12V5.5a1.4 1.4 0 0 1 2.8 0V11M10.8 11V4.2a1.4 1.4 0 0 1 2.8 0V11M13.6 11V5.2a1.4 1.4 0 0 1 2.8 0V12" +
                "M16.4 12V8.2a1.4 1.4 0 0 1 2.8 0V15a6 6 0 0 1-6 6h-1.4a6 6 0 0 1-4.6-2.2L4 14.6a1.5 1.5 0 0 1 2.2-2L8 14.5",
        )
    }
    val Search by lazy { icon("search", "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 0 0 0-13zM15.4 15.4L20 20") }
    val Info by lazy { icon("info", "M12 3.2a8.8 8.8 0 1 0 0 17.6a8.8 8.8 0 0 0 0-17.6zM12 11v5.5M12 7.5v.2") }
}

/** The dark rounded sheet over the editor, with its title and round close button. */
@Composable
internal fun SettingsSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerHeight: Dp = 96.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSettingsColors.current
    val shape = AppShapes.Rounded32
    Column(modifier.opensFrom(OpenFrom.Center).shadow(16.dp, shape, clip = false).clip(shape).background(colors.sheet)) {
        Box(Modifier.fillMaxWidth().height(headerHeight)) {
            Text(title, color = colors.title, fontSize = TextSize.S27, modifier = Modifier.align(Alignment.Center))
            CloseButton(onClose, Modifier.align(Alignment.CenterEnd).padding(end = Spacing.S30))
        }
        content()
    }
}

@Composable
internal fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSettingsColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .clip(AppShapes.Circle)
            .background(colors.closeButton)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(SettingsIcons.Close, stringResource(R.string.settings_close), tint = colors.title, modifier = Modifier.size(22.dp))
    }
}

/** A rounded group on the settings screen. The defaults are the screen's, and [SheetCard] has the sheet's. */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    horizontal: Dp = 27.dp,
    top: Dp = 30.dp,
    bottom: Dp = 13.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSettingsColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.Rounded18)
            .background(colors.card)
            .padding(start = horizontal, end = horizontal, top = top, bottom = bottom),
        content = content,
    )
}

/** A rounded group on the stylus and finger sheet. */
@Composable
internal fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    SettingsCard(horizontal = 34.dp, top = 24.dp, bottom = 12.dp, content = content)
}

@Composable
internal fun CardTitle(text: String) {
    Text(
        text,
        color = LocalSettingsColors.current.text,
        fontSize = TextSize.S30,
        fontWeight = FontWeight.Bold,
    )
}

/** The icon and heading on a card of the stylus and finger sheet. */
@Composable
internal fun IconCardTitle(icon: ImageVector, text: String) {
    val colors = LocalSettingsColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.S12)) {
        Icon(icon, null, tint = colors.text, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(Spacing.S18))
        Text(text, color = colors.text, fontSize = TextSize.S22, fontWeight = FontWeight.Medium)
    }
}

/** Label and an optional line under it, with a control at the end. */
@Composable
internal fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    end: @Composable () -> Unit = {},
) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(66.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.text, fontSize = TextSize.S19_5)
            if (detail != null) Text(detail, color = colors.secondary, fontSize = TextSize.S15, lineHeight = TextSize.S18)
        }
        end()
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(label, detail = detail, onClick = { onChange(!checked) }) { SettingsSwitch(checked, label, onChange) }
}

/** A row that leads to another screen. */
@Composable
internal fun LinkRow(label: String, detail: String? = null, onClick: () -> Unit) {
    SettingRow(label, detail = detail, onClick = onClick) {
        Icon(SettingsIcons.Forward, null, tint = LocalSettingsColors.current.text, modifier = Modifier.size(26.dp))
    }
}

/** A setting chosen from a short list. The current value shows at the end, with a chevron. */
@Composable
internal fun <T> DropdownRow(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    detail: String? = null,
    onChange: (T) -> Unit,
) {
    val colors = LocalSettingsColors.current
    var open by remember { mutableStateOf(false) }
    SettingRow(label, detail = detail, onClick = { open = true }) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(optionLabel(value), color = colors.text, fontSize = TextSize.S19_5, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(Spacing.S8))
                Icon(SettingsIcons.Down, null, tint = colors.text, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(open, { open = false }) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), fontSize = TextSize.S20) },
                        onClick = {
                            open = false
                            onChange(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(55.dp).clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(start = Spacing.S8).size(27.dp).border(2.dp, colors.accent, AppShapes.Circle),
        ) {
            if (selected) Box(Modifier.size(16.dp).background(colors.accent, AppShapes.Circle))
        }
        Spacer(Modifier.width(Spacing.S9))
        Text(label, color = colors.text, fontSize = TextSize.S19_5)
    }
}

/** The reference's switch: 72 by 44, with a dark thumb on the right when on, and a small grey one when off. */
@Composable
internal fun SettingsSwitch(checked: Boolean, description: String, onChange: (Boolean) -> Unit) {
    val colors = LocalSettingsColors.current
    val thumbX by animateDpAsState(if (checked) 34.dp else 8.dp, label = "switch")
    val thumbSize = if (checked) 32.dp else 22.dp
    Box(
        Modifier
            .size(72.dp, 44.dp)
            .clip(AppShapes.Circle)
            .background(if (checked) colors.accent else colors.switchOffTrack)
            .then(if (checked) Modifier else Modifier.border(2.dp, colors.switchOff, AppShapes.Circle))
            .clickable(role = Role.Switch, onClickLabel = description) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbX.roundToPx(), 0) }
                .size(thumbSize)
                .background(if (checked) colors.onAccent else colors.switchOff, AppShapes.Circle),
        )
    }
}

/** A value in a dark pill, as the panels show numbers. */
@Composable
internal fun ValuePill(text: String, modifier: Modifier = Modifier) {
    val colors = LocalSettingsColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.widthIn(min = 60.dp).height(44.dp).clip(AppShapes.Circle).background(colors.sheet).padding(horizontal = Spacing.S14),
    ) {
        Text(text, color = colors.text, fontSize = TextSize.S20)
    }
}

/**
 * The template picker's stepped slider: a filled part and an empty part with a dot for each step, and
 * a tall bar between them. Steps run from 1 to [steps].
 */
@Composable
internal fun StepSlider(value: Int, steps: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalSettingsColors.current
    val latest by rememberUpdatedState(onChange)
    fun stepAt(x: Float, width: Int) = ((x / width) * steps + 0.5f).roundToInt().coerceIn(1, steps)
    Canvas(
        modifier
            .width(237.dp)
            .height(42.dp)
            .pointerInput(enabled, steps) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    latest(stepAt(down.position.x, size.width))
                    down.consume()
                    drag(down.id) { change ->
                        latest(stepAt(change.position.x, size.width))
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                }
            },
    ) {
        val barHeight = 22.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val centerY = size.height / 2f
        val handleWidth = 5.dp.toPx()
        val gap = 6.dp.toPx()
        val cell = size.width / steps
        val handleX = cell * value - cell / 2f
        val on = if (enabled) colors.accent else colors.sliderIdle
        val radius = CornerRadius(barHeight / 2f)
        val onEnd = handleX - handleWidth / 2f - gap
        if (onEnd > 0f) drawRoundRect(on, Offset(0f, top), Size(onEnd, barHeight), radius)
        val offStart = handleX + handleWidth / 2f + gap
        if (offStart < size.width) {
            drawRoundRect(colors.sliderIdle, Offset(offStart, top), Size(size.width - offStart, barHeight), radius)
        }
        for (step in 1..steps) {
            if (step == value) continue
            val x = cell * step - cell / 2f
            drawCircle(if (step < value) colors.sliderDotOn else colors.sliderDotOff, 3.dp.toPx(), Offset(x, centerY))
        }
        drawRoundRect(on, Offset(handleX - handleWidth / 2f, 0f), Size(handleWidth, size.height), CornerRadius(handleWidth / 2f))
    }
}

/** A plain continuous slider with minus and plus, as on the reference's table panel. */
@Composable
internal fun ValueSlider(
    fraction: Float,
    onChange: (Float) -> Unit,
    onStep: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    val latest by rememberUpdatedState(onChange)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        RoundIcon(SettingsIcons.Minus, 30.dp) { onStep(-1) }
        Canvas(
            Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = Spacing.S12)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        latest((down.position.x / size.width).coerceIn(0f, 1f))
                        down.consume()
                        drag(down.id) { change ->
                            latest((change.position.x / size.width).coerceIn(0f, 1f))
                            if (change.positionChange() != Offset.Zero) change.consume()
                        }
                    }
                },
        ) {
            val track = 9.dp.toPx()
            val handleWidth = 5.dp.toPx()
            val gap = 5.dp.toPx()
            val centerY = size.height / 2f
            val x = fraction.coerceIn(0f, 1f) * size.width
            val radius = CornerRadius(track / 2f)
            if (x - gap > 0f) {
                drawRoundRect(colors.accent, Offset(0f, centerY - track / 2f), Size(x - gap, track), radius)
            }
            if (x + gap < size.width) {
                drawRoundRect(colors.chipSelected, Offset(x + gap, centerY - track / 2f), Size(size.width - x - gap, track), radius)
            }
            drawRoundRect(colors.accent, Offset(x - handleWidth / 2f, 5.dp.toPx()), Size(handleWidth, size.height - 10.dp.toPx()), CornerRadius(handleWidth / 2f))
        }
        RoundIcon(SettingsIcons.Plus, 30.dp) { onStep(1) }
    }
}

@Composable
internal fun RoundIcon(icon: ImageVector, size: Dp, description: String? = null, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size + 14.dp).clip(AppShapes.Circle).clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, description, tint = LocalSettingsColors.current.text, modifier = Modifier.size(size))
    }
}

internal fun lineIcon(name: String, path: String) = icon(name, path)

/** A slider with a bar for the handle and a gap each side, and no steps or buttons, as the export sheet shows. */
@Composable
internal fun PlainSlider(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSettingsColors.current
    val latest by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .height(44.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    latest((down.position.x / size.width).coerceIn(0f, 1f))
                    down.consume()
                    drag(down.id) { change ->
                        latest((change.position.x / size.width).coerceIn(0f, 1f))
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                }
            },
    ) {
        val track = 9.dp.toPx()
        val handleWidth = 5.dp.toPx()
        val gap = 6.dp.toPx()
        val centerY = size.height / 2f
        val x = fraction.coerceIn(0f, 1f) * size.width
        val radius = CornerRadius(track / 2f)
        if (x - gap > 0f) drawRoundRect(colors.accent, Offset(0f, centerY - track / 2f), Size(x - gap, track), radius)
        if (x + gap < size.width) {
            drawRoundRect(colors.chipSelected, Offset(x + gap, centerY - track / 2f), Size(size.width - x - gap, track), radius)
            drawCircle(colors.accent, 2.5.dp.toPx(), Offset(size.width - track / 2f, centerY))
        }
        drawRoundRect(colors.accent, Offset(x - handleWidth / 2f, 4.dp.toPx()), Size(handleWidth, size.height - 8.dp.toPx()), CornerRadius(handleWidth / 2f))
    }
}

/** Text that stays on one line: when it does not fit, the font shrinks to the largest size that does. */
@Composable
internal fun ShrinkText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    Text(
        text,
        color = color,
        fontSize = size,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        modifier = modifier,
        onTextLayout = { if (it.didOverflowWidth && size.value > MIN_SHRUNK_SP) size = (size.value * SHRINK_STEP).sp },
    )
}

private const val SHRINK_STEP = 0.93f
private const val MIN_SHRUNK_SP = 8f

/** A selectable pill such as Built-in and Custom. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(44.dp)
            .clip(AppShapes.Rounded10)
            .background(if (selected) colors.chipSelected else colors.chipIdle)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(start = if (selected) 16.dp else 22.dp, end = 22.dp),
    ) {
        if (selected) {
            Icon(SettingsIcons.Check, null, tint = colors.chipSelectedText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.S8))
        }
        ShrinkText(label, if (selected) colors.chipSelectedText else colors.chipIdleText, TextSize.S19_5)
    }
}
