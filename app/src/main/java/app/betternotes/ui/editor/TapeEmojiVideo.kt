package app.betternotes.ui.editor

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.betternotes.R
import app.betternotes.canvas.VideoPlayRequest
import app.betternotes.core.model.TapePattern
import app.betternotes.core.tools.TapeLineMode
import app.betternotes.core.tools.TapeOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/** The tape's row: three widths and its colors. It has no panel, as there is nothing more to it. */
@Composable
internal fun TapeRow(state: EditorState, options: TapeOptions, modifier: Modifier) {
    Pill(modifier) {
        // The row holds more than fits beside the other pills, so it scrolls, as the pen's row does. The warning tape is last.
        ToolStrip {
            // On the left: what is printed on the tape, and whether it is always a straight strip, straightens on hold, or never does.
            ToolbarIcon(patternIcon(options.pattern), stringResource(R.string.editor_tape_pattern)) {
                state.updateTape { it.copy(pattern = TapePattern.entries[(it.pattern.ordinal + 1) % TapePattern.entries.size]) }
            }
            ToolbarIcon(lineModeIcon(options.lineMode), stringResource(R.string.editor_tape_line_mode)) {
                state.updateTape { it.copy(lineMode = TapeLineMode.entries[(it.lineMode.ordinal + 1) % TapeLineMode.entries.size]) }
            }
            PillDivider()
            options.widths.forEachIndexed { slot, width ->
                ToolbarCellBox(onClick = { state.updateTape { it.copy(selectedWidth = slot) } }) {
                    WidthGlyph(width)
                    if (slot == options.selectedWidth) SelectedDot()
                }
            }
            PillDivider()
            options.colors.forEachIndexed { slot, color ->
                ToolbarCellBox(onClick = { state.updateTape { it.copy(selectedColor = slot) } }) {
                    ColorDot(color or OPAQUE, diameter = 24.dp)
                    if (slot == options.selectedColor) SelectedDot()
                }
            }
            ToolbarCellBox(onClick = { state.updateTape { it.copy(selectedColor = options.colors.size) } }) {
                WarningSwatch()
                if (options.selectedColor >= options.colors.size) SelectedDot()
            }
            PillDivider()
            ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
                state.panelOpen = !state.panelOpen
            }
        }
    }
}

private fun patternIcon(pattern: TapePattern) = when (pattern) {
    TapePattern.PLAIN -> EditorIcons.TapePlain
    TapePattern.DOTS -> EditorIcons.TapeDots
    TapePattern.LINES -> EditorIcons.TapeLines
    TapePattern.CROSSHATCH -> EditorIcons.TapeHatch
}

private fun lineModeIcon(mode: TapeLineMode) = when (mode) {
    TapeLineMode.ALWAYS -> EditorIcons.LineAlways
    TapeLineMode.HOLD -> EditorIcons.LineHold
    TapeLineMode.NEVER -> EditorIcons.LineNever
}

/** The tape's thickness and opacity, for the next strip laid down. */
@Composable
internal fun TapePanel(state: EditorState, options: TapeOptions, modifier: Modifier) {
    PanelSheet(stringResource(R.string.tool_tape), onClose = { state.panelOpen = false }, modifier = modifier) {
        TapeColorSection(state, options)
        TapeLookSliders(
            thickness = options.width.toFloat(),
            opacity = options.opacity,
            onThickness = { value -> state.updateTape { it.withWidth(value.roundToInt()) } },
            onOpacity = { value -> state.updateTape { it.copy(opacity = value) } },
            onFinished = {},
        )
        PanelDivider()
        // The shadow's settings stay folded away until asked for, behind a switch that turns the shadow on or off.
        var shadowOpen by remember { mutableStateOf(false) }
        LabeledRow(R.string.panel_shadow) {
            PanelSwitch(options.shadowOn, stringResource(R.string.panel_shadow)) { on -> state.updateTape { it.copy(shadowOn = on) } }
            PanelIconButton(
                if (shadowOpen) EditorIcons.ChevronUp else EditorIcons.ChevronDown,
                stringResource(R.string.panel_shadow),
            ) { shadowOpen = !shadowOpen }
        }
        if (shadowOpen) {
            LabeledRow(R.string.panel_shadow_distance) { ValuePill { ValueText("%.1f".format(options.shadowDistance)) } }
            StepperSlider(
                fraction = options.shadowDistance / TapeOptions.MAX_SHADOW_DISTANCE,
                onChange = { f -> state.updateTape { it.copy(shadowDistance = f * TapeOptions.MAX_SHADOW_DISTANCE) } },
                steps = 16,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledRow(R.string.panel_shadow_angle) { ValuePill { ValueText("${options.shadowAngle.roundToInt()}°") } }
            StepperSlider(
                fraction = options.shadowAngle / 360f,
                onChange = { f -> state.updateTape { it.copy(shadowAngle = f * 360f) } },
                steps = 36,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledRow(R.string.panel_shadow_opacity) { ValuePill { ValueText("${(options.shadowOpacity * 100).roundToInt()}%") } }
            StepperSlider(
                fraction = options.shadowOpacity,
                onChange = { f -> state.updateTape { it.copy(shadowOpacity = f) } },
                steps = 20,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PanelDivider()
        LabeledRow(R.string.panel_hold_time) { ValuePill { ValueText("%.1f s".format(options.holdMs / 1000f)) } }
        StepperSlider(
            fraction = (options.holdMs - TapeOptions.MIN_HOLD_MS).toFloat() / (TapeOptions.MAX_HOLD_MS - TapeOptions.MIN_HOLD_MS),
            onChange = { f ->
                state.updateTape { it.copy(holdMs = TapeOptions.MIN_HOLD_MS + (f * (TapeOptions.MAX_HOLD_MS - TapeOptions.MIN_HOLD_MS)).roundToInt()) }
            },
            steps = (TapeOptions.MAX_HOLD_MS - TapeOptions.MIN_HOLD_MS) / 100,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
    }
}

/** Two sliders, thickness in points and opacity, shared by the tape tool's panel and the lasso's selected tape. */
@Composable
internal fun ColumnScope.TapeLookSliders(
    thickness: Float,
    opacity: Float,
    onThickness: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val thicknessRange = TapeOptions.MAX_THICKNESS - TapeOptions.MIN_THICKNESS
    LabeledRow(R.string.panel_thickness) { ValuePill { ValueText(thickness.roundToInt().toString()) } }
    StepperSlider(
        fraction = ((thickness - TapeOptions.MIN_THICKNESS) / thicknessRange).coerceIn(0f, 1f),
        onChange = { f -> onThickness(TapeOptions.MIN_THICKNESS + f * thicknessRange) },
        steps = thicknessRange,
        onChangeFinished = onFinished,
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledRow(R.string.panel_opacity) { ValuePill { ValueText("${(opacity * 100).roundToInt()}%") } }
    StepperSlider(
        fraction = ((opacity - TapeOptions.MIN_OPACITY) / (1f - TapeOptions.MIN_OPACITY)).coerceIn(0f, 1f),
        onChange = { f -> onOpacity(TapeOptions.MIN_OPACITY + f * (1f - TapeOptions.MIN_OPACITY)) },
        steps = 9,
        onChangeFinished = onFinished,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The stickers' row: the emoji in use, its size, and the way to the rest. */
@Composable
internal fun EmojiRow(state: EditorState, modifier: Modifier) {
    Pill(modifier) {
        if (state.stickerTab) {
            for (id in state.stickers.take(RECENT_STICKERS)) {
                ToolbarCellBox(onClick = { state.chooseSticker(id) }, modifier = Modifier.stickerDragSource(state, id, enabled = true)) {
                    StickerThumb(state, id, 30.dp)
                    if (id == state.sticker) SelectedDot()
                }
            }
            if (state.stickers.isEmpty()) {
                Text(
                    stringResource(R.string.sticker_empty_short),
                    color = LocalEditorColors.current.label,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
        for (emoji in if (state.stickerTab) emptyList() else state.recentEmoji) {
            ToolbarCellBox(onClick = { state.chooseEmoji(emoji) }) {
                Text(emoji, fontSize = 20.sp)
                if (emoji == state.emoji) SelectedDot()
            }
        }
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

@Composable
internal fun EmojiPanel(state: EditorState, modifier: Modifier) {
    val colors = LocalEditorColors.current
    PanelSheet(stringResource(R.string.tool_sticker), onClose = { state.panelOpen = false }, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding, vertical = 6.dp),
        ) {
            // The stickers come first, and the emoji are the second choice.
            StickerTab(R.string.sticker_tab_stickers, state.stickerTab, Modifier.weight(1f)) { state.showStickerTab(true) }
            StickerTab(R.string.sticker_tab_emoji, !state.stickerTab, Modifier.weight(1f)) { state.showStickerTab(false) }
        }
        if (state.stickerTab) {
            StickerGrid(state)
            return@PanelSheet
        }
        LabeledRow(R.string.panel_size) { ValuePill { ValueText(state.emojiSize.toString()) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PanelPadding - 6.dp)) {
            PanelIconButton(EditorIcons.Minus, stringResource(R.string.panel_thinner)) { state.resizeEmoji(state.emojiSize - 2) }
            PanelSlider(
                fraction = (state.emojiSize - EditorState.MIN_EMOJI).toFloat() / (EditorState.MAX_EMOJI - EditorState.MIN_EMOJI),
                onChange = { f ->
                    state.resizeEmoji(EditorState.MIN_EMOJI + (f * (EditorState.MAX_EMOJI - EditorState.MIN_EMOJI)).roundToInt())
                },
                modifier = Modifier.weight(1f),
            )
            PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_thicker)) { state.resizeEmoji(state.emojiSize + 2) }
        }
        PanelDivider()
        for (row in EMOJI.chunked(EMOJI_PER_ROW)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = PanelPadding - 4.dp)) {
                for (emoji in row) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (emoji == state.emoji) colors.accentContainer else Color.Transparent)
                            .clickable(role = Role.Button) { state.chooseEmoji(emoji) },
                    ) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
                repeat(EMOJI_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** A video playing where it sits on the page, with the platform's own controls. */
@Composable
internal fun VideoPlayer(request: VideoPlayRequest, onClose: () -> Unit) {
    val density = LocalDensity.current
    val colors = LocalEditorColors.current
    Box(
        Modifier
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
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(request.file.path)
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    start()
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(27.dp)
                .clip(CircleShape)
                .background(colors.closeButton)
                .clickable(role = Role.Button, onClick = onClose),
        ) {
            Icon(EditorIcons.Close, stringResource(R.string.panel_close), tint = colors.label, modifier = Modifier.size(18.dp))
        }
    }
}

internal val EMOJI: List<String> = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
    "😊", "😇", "🙂", "😉", "😍", "🥰", "😘", "😎",
    "🤓", "🧐", "🤔", "🤨", "😐", "😴", "😷", "🤒",
    "🤕", "😢", "😭", "😱", "😡", "🤯", "🥳", "🤩",
    "👍", "👎", "👌", "✌️", "🤞", "👏", "🙌", "🙏",
    "💪", "👀", "🧠", "🫀", "🫁", "🦴", "🦷", "🩸",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
    "⭐", "🌟", "✨", "🔥", "💡", "📌", "📍", "📎",
    "✅", "❌", "❓", "❗", "⚠️", "🚩", "🔔", "⏰",
    "📚", "📖", "📝", "✏️", "🖊️", "📐", "📏", "🔬",
    "💊", "💉", "🩺", "🩹", "🏥", "🚑", "🧪", "🧬",
    "☕", "🍎", "🍕", "🎉", "🎓", "🏆", "🌱", "🌈",
)

private const val EMOJI_PER_ROW = 8
private const val OPAQUE = 0xFF000000.toInt()

/** A round swatch striped yellow and black, for the warning tape. */
@Composable
private fun WarningSwatch() {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp).clip(CircleShape)) {
        drawRect(Color(TapeOptions.WARNING_FILL))
        val stripe = size.width / 4f
        var x = -size.height
        while (x < size.width) {
            val slant = Path().apply {
                moveTo(x, size.height)
                lineTo(x + stripe, size.height)
                lineTo(x + stripe + size.height, 0f)
                lineTo(x + size.height, 0f)
                close()
            }
            drawPath(slant, Color(TapeOptions.WARNING_STRIPE))
            x += stripe * 2f
        }
    }
}

/** The tape's colors as swatches, with the way to add one, laid out like the ink tools' color section. */
@Composable
private fun ColumnScope.TapeColorSection(state: EditorState, options: TapeOptions) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(49.dp).padding(start = PanelPadding, end = PanelPadding - 6.dp),
    ) {
        Text(
            stringResource(R.string.panel_color),
            color = colors.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        )
        Spacer(Modifier.weight(1f))
        PanelIconButton(EditorIcons.Plus, stringResource(R.string.panel_add_swatch)) { state.colorTarget = ColorTarget.TAPE }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PanelPadding - 8.dp, end = PanelPadding - 8.dp, bottom = 10.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        options.colors.forEachIndexed { slot, color ->
            ToolbarCellBox(onClick = { state.updateTape { it.copy(selectedColor = slot) } }) {
                ColorDot(color or OPAQUE, diameter = 26.dp)
                if (slot == options.selectedColor) SelectedDot()
            }
        }
        ToolbarCellBox(onClick = { state.updateTape { it.copy(selectedColor = options.colors.size) } }) {
            WarningSwatch()
            if (options.selectedColor >= options.colors.size) SelectedDot()
        }
    }
}

private const val RECENT_STICKERS = 5
private const val STICKERS_PER_ROW = 3

@Composable
private fun StickerTab(label: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.accentContainer else colors.field)
            .clickable(role = Role.Tab, onClick = onClick),
    ) {
        Text(
            stringResource(label),
            color = if (selected) colors.accent else colors.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The saved stickers as a grid of pictures. Pressing one chooses it, and the pen then puts it on the page. */
@Composable
private fun StickerGrid(state: EditorState) {
    val colors = LocalEditorColors.current
    var removing by remember { mutableStateOf(false) }
    val pickPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree -> if (tree != null) state.importStickerPack(tree) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { pickPack.launch(null) }
            .padding(horizontal = PanelPadding, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.sticker_add_pack), color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    if (state.stickers.isEmpty()) {
        Text(
            stringResource(R.string.sticker_empty),
            color = colors.label,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = PanelPadding, vertical = 8.dp),
        )
        return
    }
    // Stickers saved from selections come first, then each pack under its name.
    val groups = state.stickers.groupBy { state.packs[it] }.toList().sortedBy { it.first != null }
    for ((pack, ids) in groups) {
        if (pack != null) {
            Text(pack, color = colors.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = PanelPadding, vertical = 6.dp))
        }
        for (row in ids.chunked(STICKERS_PER_ROW)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = PanelPadding - 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in row) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (id == state.sticker) colors.accentContainer else colors.field)
                            .clickable(role = Role.Button) { if (removing) state.deleteSticker(id) else state.chooseSticker(id) }
                            .stickerDragSource(state, id, enabled = !removing)
                            .padding(8.dp),
                    ) {
                        StickerThumb(state, id, 72.dp)
                        if (removing) {
                            Box(Modifier.align(Alignment.TopEnd).size(22.dp).background(Color(0xFFE5483F), CircleShape), contentAlignment = Alignment.Center) {
                                Text("x", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
                repeat(STICKERS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { removing = !removing }
            .padding(horizontal = PanelPadding, vertical = 14.dp),
    ) {
        Text(
            stringResource(if (removing) R.string.sticker_done_removing else R.string.sticker_remove),
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** A sticker's picture, drawn off the main thread the first time and kept. */
@Composable
internal fun StickerThumb(state: EditorState, id: Long, size: androidx.compose.ui.unit.Dp) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { state.stickerStore.thumbnail(id)?.asImageBitmap() }
    }
    val image = bitmap ?: return
    androidx.compose.foundation.Image(
        image,
        contentDescription = null,
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF4F1E8)).padding(3.dp),
    )
}

/**
 * Holding a sticker in the list and dragging it picks it up: it follows the finger over the page, and where it is let go it is put down at
 * its own size. A plain press still chooses it.
 */
@Composable
private fun Modifier.stickerDragSource(state: EditorState, id: Long, enabled: Boolean): Modifier {
    if (!enabled) return this
    val coordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { start -> coordinates[0]?.localToWindow(start)?.let { state.beginStickerDrag(id, it) } },
                onDragEnd = state::dropStickerDrag,
                onDragCancel = state::cancelStickerDrag,
            ) { change, drag ->
                change.consume()
                state.stickerDragPoint = state.stickerDragPoint?.plus(drag)
            }
        }
}
