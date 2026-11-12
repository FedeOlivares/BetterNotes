package app.betternotes.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.betternotes.core.color.HexColor
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.Swatches
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Color as AndroidColor

/**
 * What a color picker is showing: a hue, a saturation and a value, and the hex code of that color. The wheel and the slider change the
 * color and the code follows; a code typed in whole changes the color and leaves what is typed as it is. Every picker in the app,
 * in the editor and in the library, is built on this and the pieces below, so they pick, and read a hex code, the same way.
 */
@Stable
internal class ColorPickerState(initial: Int) {
    private val start = FloatArray(3).also { AndroidColor.colorToHSV(initial, it) }
    var hue by mutableFloatStateOf(start[0])
        private set
    var saturation by mutableFloatStateOf(start[1])
        private set
    var value by mutableFloatStateOf(start[2])
        private set

    /** The hex code as shown in its field: whatever is being typed. */
    var hexText by mutableStateOf(HexColor.format(initial))
        private set

    /** Whether what is typed is not a whole code yet. */
    val hexInvalid get() = HexColor.parse(hexText) == null

    /** The color picked, opaque, as ARGB. */
    val argb: Int get() = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    fun pickHueSaturation(hue: Float, saturation: Float) {
        this.hue = hue
        this.saturation = saturation
        hexText = HexColor.format(argb)
    }

    fun pickValue(value: Float) {
        this.value = value
        hexText = HexColor.format(argb)
    }

    /** Takes what was typed in the hex field. A whole code moves the wheel and the slider to its color. */
    fun typeHex(typed: String) {
        hexText = HexColor.filter(typed)
        val color = HexColor.parse(hexText) ?: return
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
        // A gray, or black, has no hue of its own: the wheel keeps where it was instead of jumping to red.
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        if (hsv[2] > 0f) saturation = hsv[1]
        value = hsv[2]
    }
}

/** The hue and saturation wheel of [state], [size] across. */
@Composable
internal fun HueSaturationWheel(state: ColorPickerState, thumbColor: Color, modifier: Modifier = Modifier, size: Dp = WHEEL_SIZE) {
    val latest by rememberUpdatedState(state)
    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                fun pick(at: Offset) {
                    val radius = min(this.size.width, this.size.height) / 2f
                    val dx = at.x - this.size.width / 2f
                    val dy = at.y - this.size.height / 2f
                    val degrees = (atan2(dy, dx) * RADIANS_TO_DEGREES + 360f) % 360f
                    latest.pickHueSaturation(degrees, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        pick(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val radius = this.size.minDimension / 2f
        drawCircle(Brush.sweepGradient(Swatches.Hues, center), radius)
        drawCircle(Brush.radialGradient(listOf(UiColors.White, Color.Transparent), center, radius), radius)
        val angle = state.hue / RADIANS_TO_DEGREES
        val thumb = Offset(
            center.x + cos(angle) * state.saturation * radius,
            center.y + sin(angle) * state.saturation * radius,
        )
        drawCircle(thumbColor, THUMB_RADIUS.toPx(), thumb, style = Stroke(THUMB_STROKE.toPx()))
    }
}

/**
 * The field for the hex code of [state]: "#RRGGBB" or "RRGGBB", centered and only as wide as a code needs, outlined in [errorColor] while what
 * is typed is not a whole code. [modifier] is for the row it sits in, which is as wide as its parent.
 */
@Composable
internal fun HexField(state: ColorPickerState, textColor: Color, borderColor: Color, errorColor: Color, modifier: Modifier = Modifier) {
    val outline = if (state.hexInvalid) errorColor else borderColor
    // The text is held with its cursor, so letters typed quickly are not lost between one recomposition and the next.
    var field by remember { mutableStateOf(TextFieldValue(state.hexText, TextRange(state.hexText.length))) }
    LaunchedEffect(state.hexText) {
        if (field.text != state.hexText) field = TextFieldValue(state.hexText, TextRange(state.hexText.length))
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(HEX_FIELD_WIDTH)
                .clip(AppShapes.Rounded12)
                .border(1.dp, outline, AppShapes.Rounded12)
                .padding(horizontal = Spacing.S12, vertical = Spacing.S8),
        ) {
            BasicTextField(
                value = field,
                onValueChange = { typed ->
                    val kept = HexColor.filter(typed.text)
                    field = typed.copy(text = kept, selection = TextRange(typed.selection.end.coerceAtMost(kept.length)))
                    state.typeHex(kept)
                },
                singleLine = true,
                textStyle = TextStyle(color = textColor, fontSize = TextSize.S16, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(textColor),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            )
        }
    }
}

/**
 * The circle with a plus that sits at the end of every set of colors to pick from: it opens the color wheel, with its hex code, for a
 * color of one's own. It takes the place of a swatch, so it is [size] across, as the swatches beside it are.
 */
@Composable
internal fun AddColorCircle(description: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = ADD_CIRCLE_SIZE) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(AppShapes.Circle)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(EditorIcons.AddColor, description, tint = tint, modifier = Modifier.size(size - ADD_CIRCLE_INSET))
    }
}

private val WHEEL_SIZE = 240.dp

/** Wide enough for "#RRGGBB" and the padding around it, and no wider. */
private val HEX_FIELD_WIDTH = 112.dp
private val THUMB_RADIUS = 9.dp
private val THUMB_STROKE = 2.dp
private val ADD_CIRCLE_SIZE = 40.dp
private val ADD_CIRCLE_INSET = 8.dp
private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()
