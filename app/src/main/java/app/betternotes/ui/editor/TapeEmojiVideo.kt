package app.betternotes.ui.editor

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import app.betternotes.core.tools.TapeOptions
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt

/** The tape's row: three widths and its colors. It has no panel, as there is nothing more to it. */
@Composable
internal fun TapeRow(state: EditorState, options: TapeOptions, modifier: Modifier) {
    Pill(modifier) {
        // The row holds more than fits beside the other pills, so it scrolls, as the pen's row does. The warning tape is last.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            options.widths.forEachIndexed { slot, width ->
                ToolbarCellBox(onClick = { state.updateTape { it.copy(selectedWidth = slot, thickness = 0) } }) {
                    WidthGlyph(width)
                    if (options.thickness == 0 && slot == options.selectedWidth) SelectedDot()
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

/** The tape's thickness and opacity, for the next strip laid down. */
@Composable
internal fun TapePanel(state: EditorState, options: TapeOptions, modifier: Modifier) {
    PanelSheet(stringResource(R.string.tool_tape), onClose = { state.panelOpen = false }, modifier = modifier) {
        TapeColorSection(state, options)
        TapeLookSliders(
            thickness = options.width.toFloat(),
            opacity = options.opacity,
            onThickness = { value -> state.updateTape { it.copy(thickness = value.roundToInt()) } },
            onOpacity = { value -> state.updateTape { it.copy(opacity = value) } },
            onFinished = {},
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
    PanelSlider(
        fraction = ((thickness - TapeOptions.MIN_THICKNESS) / thicknessRange).coerceIn(0f, 1f),
        onChange = { f -> onThickness(TapeOptions.MIN_THICKNESS + f * thicknessRange) },
        onChangeFinished = onFinished,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 6.dp),
    )
    LabeledRow(R.string.panel_opacity) { ValuePill { ValueText("${(opacity * 100).roundToInt()}%") } }
    PanelSlider(
        fraction = ((opacity - TapeOptions.MIN_OPACITY) / (1f - TapeOptions.MIN_OPACITY)).coerceIn(0f, 1f),
        onChange = { f -> onOpacity(TapeOptions.MIN_OPACITY + f * (1f - TapeOptions.MIN_OPACITY)) },
        onChangeFinished = onFinished,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding + 6.dp),
    )
}

/** The stickers' row: the emoji in use, its size, and the way to the rest. */
@Composable
internal fun EmojiRow(state: EditorState, modifier: Modifier) {
    Pill(modifier) {
        for (emoji in state.recentEmoji) {
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
