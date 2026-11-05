package app.betternotes.ui.editor

import android.graphics.RectF
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.core.model.LineKind
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors

internal val PillHeight = 48.dp
internal val ToolbarCell = 40.dp
private val PillShape = RoundedCornerShape(PillHeight / 2)

/** The dark capsule that every toolbar sits in. */
@Composable
internal fun Pill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .morphBar()
            .height(PillHeight)
            .shadow(4.dp, PillShape, clip = false)
            .clip(PillShape)
            .background(colors.pill)
            .border(1.dp, colors.pillBorder, PillShape)
            .padding(horizontal = 6.dp),
        content = content,
    )
}

/** Keeps wet ink out from under this control for as long as it is on screen. */
@Composable
internal fun Modifier.masksInk(state: EditorState, key: String): Modifier {
    DisposableEffect(state, key) {
        onDispose { state.reportOverlay(key, null) }
    }
    return onGloballyPositioned { coordinates ->
        val box = coordinates.boundsInRoot()
        state.reportOverlay(key, RectF(box.left, box.top, box.right, box.bottom))
    }
}

/** A touch target with an unbounded ripple, like the toolbars' icons. */
@Composable
internal fun Modifier.tappable(radius: Dp, enabled: Boolean = true, onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(bounded = false, radius = radius),
    enabled = enabled,
    role = Role.Button,
    onClick = onClick,
)

@Composable
internal fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Boolean = false,
    tintOverride: Color? = null,
    morphKey: String? = null,
    onClick: () -> Unit,
) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // The settings button stays where it is in every row, so it moves with the row.
            .morph(morphKey ?: if (icon === EditorIcons.Sliders) "settings" else null)
            .size(ToolbarCell, PillHeight)
            .tappable(radius = 20.dp, enabled = enabled, onClick = onClick),
    ) {
        // The circle behind the tool in use grows out from the middle, and shrinks back into it when another is chosen.
        val grow by animateFloatAsState(if (selected) 1f else 0f, tween(TOOL_CHANGE_MS), label = "toolSelected")
        if (grow > 0f) {
            Box(
                Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    }
                    .background(colors.toolSelected, CircleShape),
            )
        }
        Icon(
            icon,
            description,
            tint = when {
                tintOverride != null -> tintOverride
                !enabled -> colors.iconDisabled
                selected -> colors.toolSelectedIcon
                accent -> colors.accent
                else -> colors.icon
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun PillDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(2.dp, 28.dp)
            .background(LocalEditorColors.current.pillDivider),
    )
}

/** The small blue dot that marks the width and color in use. */
@Composable
internal fun BoxScope.SelectedDot() {
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(y = 15.dp)
            .size(4.dp)
            .background(LocalEditorColors.current.accent, CircleShape),
    )
}

/** A filled circle of [color], with a faint edge so that black still shows on a dark pill. */
@Composable
internal fun ColorDot(color: Int, diameter: Dp = 22.dp) {
    val edge = LocalEditorColors.current.swatchEdge
    Box(
        Modifier
            .size(diameter)
            .background(Color(color), CircleShape)
            .border(1.dp, edge, CircleShape),
    )
}

/** The curve that stands for a line type: solid, dashed or dotted. */
@Composable
internal fun LineTypeGlyph(kind: LineKind, tint: Color, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString("M3.5 20.5c6 0 5.5-17 13.5-17h3.5").toPath() }
    Canvas(modifier.size(24.dp)) {
        val unit = size.width / 24f
        scale(unit, pivot = Offset.Zero) {
            val effect = when (kind) {
                LineKind.SOLID -> null
                LineKind.DASHED -> PathEffect.dashPathEffect(floatArrayOf(3.5f, 3f))
                LineKind.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(0.1f, 3f))
            }
            drawPath(path, tint, style = Stroke(1.7f, cap = StrokeCap.Round, pathEffect = effect))
        }
    }
}

/** The rounded field that shows a setting's value in the panel. */
@Composable
internal fun ValuePill(onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalEditorColors.current
    val shape = RoundedCornerShape(18.dp)
    var modifier = Modifier
        .size(71.dp, 35.dp)
        .clip(shape)
        .background(colors.field)
    if (onClick != null) modifier = modifier.clickable(role = Role.Button, onClick = onClick)
    Box(contentAlignment = Alignment.Center, modifier = modifier, content = content)
}

@Composable
internal fun ValueText(text: String) {
    Text(text, color = LocalEditorColors.current.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

/** An on or off switch, drawn to the screenshot's proportions. */
@Composable
internal fun PanelSwitch(checked: Boolean, description: String, onChange: (Boolean) -> Unit) {
    val colors = LocalEditorColors.current
    val thumbOffset by animateDpAsState(if (checked) 28.dp else 4.dp, label = "switch")
    Box(
        Modifier
            .size(58.dp, 35.dp)
            .clip(CircleShape)
            .background(if (checked) colors.switchOn else colors.switchOff)
            .clickable(role = Role.Switch, onClickLabel = description) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(26.dp)
                .background(if (checked) colors.switchOnThumb else colors.switchOffThumb, CircleShape),
        )
    }
}

/**
 * A slider like the screenshot's: a short bright part, a bar for the thumb with a gap on each side, a
 * dim track, and a stop dot at the far end. [fraction] runs from 0 to 1.
 */
@Composable
internal fun PanelSlider(
    fraction: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
) {
    val colors = LocalEditorColors.current
    val latestChange by rememberUpdatedState(onChange)
    val latestFinish by rememberUpdatedState(onChangeFinished)
    Canvas(
        modifier
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    latestChange((down.position.x / size.width).coerceIn(0f, 1f))
                    down.consume()
                    drag(down.id) { change ->
                        latestChange((change.position.x / size.width).coerceIn(0f, 1f))
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                    latestFinish()
                }
            },
    ) {
        val trackHeight = 6.dp.toPx()
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = 20.dp.toPx()
        val gap = 4.dp.toPx()
        val radius = CornerRadius(trackHeight / 2f)
        val centerY = size.height / 2f
        val thumbX = thumbWidth / 2f + (size.width - thumbWidth) * fraction.coerceIn(0f, 1f)
        val trackTop = centerY - trackHeight / 2f

        val activeEnd = thumbX - thumbWidth / 2f - gap
        if (activeEnd > 0f) {
            drawRoundRect(colors.accent, Offset(0f, trackTop), Size(activeEnd, trackHeight), radius)
        }
        val inactiveStart = thumbX + thumbWidth / 2f + gap
        if (inactiveStart < size.width) {
            drawRoundRect(
                colors.sliderTrack,
                Offset(inactiveStart, trackTop),
                Size(size.width - inactiveStart, trackHeight),
                radius,
            )
            drawCircle(colors.accent, 2.dp.toPx(), Offset(size.width - trackHeight / 2f, centerY))
        }
        drawRoundRect(
            colors.accent,
            Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
            Size(thumbWidth, thumbHeight),
            CornerRadius(thumbWidth / 2f),
        )
    }
}

/** Says plainly that a control has no behavior yet, or explains one. */
internal class Notices(private val show: (Int) -> Unit) {
    fun notBuilt() = show(app.betternotes.R.string.editor_not_built)

    fun say(@StringRes text: Int) = show(text)
}

@Composable
internal fun rememberNotices(): Notices {
    val context = LocalContext.current
    return remember(context) { Notices { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
}

/** A fixed-width cell for a toolbar's glyphs. */
@Composable
internal fun ToolbarCellBox(morphKey: String? = null, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .morph(morphKey)
            .width(ToolbarCell)
            .height(PillHeight)
            .tappable(radius = 20.dp, onClick = onClick),
        content = content,
    )
}
