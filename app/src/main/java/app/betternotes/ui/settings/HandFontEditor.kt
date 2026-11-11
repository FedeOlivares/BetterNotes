package app.betternotes.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.handfont.GlyphSpace
import app.betternotes.core.handfont.HandCharset
import app.betternotes.core.handfont.HandFont
import app.betternotes.core.handfont.HandGlyph
import app.betternotes.core.handfont.clampToSpace
import app.betternotes.core.handfont.simplifyStroke
import app.betternotes.core.text.FontFiles
import app.betternotes.fonts.FontLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PadSide = 255.dp
private val CellSize = 58.dp

/**
 * What a person has drawn so far. [initial] is the font being edited, or null for a new one. The back arrow asks before
 * it throws drawings away, which is what [asking] shows.
 */
@Stable
internal class HandFontDraft(initial: HandFont?) {
    val editing: String? = initial?.name
    var name by mutableStateOf(initial?.name.orEmpty())
    val glyphs = mutableStateMapOf<Char, HandGlyph>().apply { initial?.glyphs?.let(::putAll) }
    var selected by mutableStateOf(HandCharset.ALL.first())
    var dirty by mutableStateOf(false)
    var asking by mutableStateOf(false)

    val drawn get() = glyphs.count { !it.value.isEmpty }

    fun add(stroke: FloatArray) {
        val now = glyphs[selected]?.strokes.orEmpty()
        glyphs[selected] = HandGlyph(now + clampToSpace(simplifyStroke(stroke)))
        dirty = true
    }

    fun undo() {
        val now = glyphs[selected]?.strokes ?: return
        if (now.isEmpty()) return
        glyphs[selected] = HandGlyph(now.dropLast(1))
        dirty = true
    }

    fun clear() {
        if (glyphs.remove(selected) != null) dirty = true
    }

    fun step(by: Int) {
        val all = HandCharset.ALL
        selected = all[(all.indexOf(selected) + by).mod(all.length)]
    }
}

/**
 * Where a font is made: a name, a pad to draw the selected letter on, and the letters to choose from, each showing its
 * drawing. No Notewise screenshot shows this; it follows the settings cards. [onDone] runs after saving or leaving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HandFontEditor(draft: HandFontDraft, onDone: () -> Unit) {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val taken = draft.name != draft.editing && (draft.name in FontLibrary.names || FontLibrary.isBuiltIn(draft.name))
    val nameOk = FontFiles.idOf("${draft.name}.ttf") != null
    val problem = when {
        !nameOk -> R.string.font_make_need_name
        taken -> R.string.font_make_exists
        draft.drawn == 0 -> R.string.font_make_need_glyph
        else -> null
    }
    var saving by remember { mutableStateOf(false) }

    SettingsCard {
        CardTitle(stringResource(if (draft.editing == null) R.string.settings_fonts_create else R.string.font_make_edit_title))
        Text(
            stringResource(R.string.font_make_help),
            color = colors.secondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Text(stringResource(R.string.font_make_name), color = colors.text, fontSize = 19.5.sp, modifier = Modifier.padding(end = 16.dp))
            BasicTextField(
                draft.name,
                {
                    draft.name = it.take(MAX_NAME)
                    draft.dirty = true
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(),
                textStyle = TextStyle(color = colors.text, fontSize = 19.5.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(colors.inset).padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { field ->
                    if (draft.name.isEmpty()) Text(stringResource(R.string.font_make_name_hint), color = colors.secondary, fontSize = 19.5.sp)
                    field()
                },
            )
        }
    }

    SettingsCard {
        Text(
            stringResource(R.string.font_make_draw_char, draft.selected.toString()),
            color = colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            GlyphPad(draft, Modifier.size(PadSide))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(stringResource(R.string.font_make_undo)) { draft.undo() }
                ActionPill(stringResource(R.string.font_make_clear)) { draft.clear() }
                Spacer(Modifier.height(10.dp))
                ActionPill(stringResource(R.string.font_make_previous)) { draft.step(-1) }
                ActionPill(stringResource(R.string.font_make_next)) { draft.step(1) }
            }
        }
    }

    SettingsCard {
        val sections = listOf(
            R.string.font_make_lower to HandCharset.LOWER,
            R.string.font_make_upper to HandCharset.UPPER,
            R.string.font_make_digits to HandCharset.DIGITS,
            R.string.font_make_punctuation to HandCharset.PUNCTUATION,
            R.string.font_make_spanish to HandCharset.SPANISH,
        )
        for ((title, chars) in sections) {
            Text(stringResource(title), color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                for (c in chars) GlyphCell(c, draft.glyphs[c], c == draft.selected) { draft.selected = c }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.font_make_count, draft.drawn, HandCharset.ALL.length),
                color = colors.text,
                fontSize = 19.5.sp,
            )
            if (problem != null) Text(stringResource(problem), color = colors.secondary, fontSize = 15.sp)
        }
        val enabled = problem == null && !saving
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) colors.accent else colors.chipIdle)
                .clickable(enabled = enabled) {
                    saving = true
                    scope.launch {
                        val font = HandFont(draft.name.trim(), draft.glyphs.toMap())
                        val result = withContext(Dispatchers.IO) { FontLibrary.saveHand(font, draft.editing) }
                        saving = false
                        if (result is FontLibrary.AddResult.Added) {
                            draft.dirty = false
                            Toast.makeText(context, resources.getString(R.string.font_make_saved, result.name), Toast.LENGTH_SHORT).show()
                            onDone()
                        } else {
                            Toast.makeText(context, resources.getString(R.string.font_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text(
                stringResource(R.string.font_make_save),
                color = if (enabled) colors.onAccent else colors.chipIdleText,
                fontSize = 19.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    if (draft.asking) {
        AlertDialog(
            onDismissRequest = { draft.asking = false },
            title = { Text(stringResource(R.string.font_make_leave_title)) },
            text = { Text(stringResource(R.string.font_make_leave_text)) },
            confirmButton = {
                TextButton({
                    draft.asking = false
                    draft.dirty = false
                    onDone()
                }) { Text(stringResource(R.string.font_make_leave)) }
            },
            dismissButton = { TextButton({ draft.asking = false }) { Text(stringResource(R.string.font_make_keep)) } },
        )
    }
}

private const val MAX_NAME = 40

@Composable
private fun ActionPill(label: String, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    Box(
        Modifier
            .width(150.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.chipIdle)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { ShrinkText(label, colors.chipIdleText, 18.sp) }
}

/** One letter to choose: its drawing when there is one, and the plain letter, faint, while there is not. */
@Composable
private fun GlyphCell(char: Char, glyph: HandGlyph?, selected: Boolean, onSelect: () -> Unit) {
    val colors = LocalSettingsColors.current
    val measurer = rememberTextMeasurer()
    val ink = if (selected) colors.chipSelectedText else colors.text
    val faint = colors.secondary
    Canvas(
        Modifier
            .size(CellSize)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.chipSelected else colors.inset)
            .clickable(onClick = onSelect),
    ) {
        if (glyph == null || glyph.isEmpty) {
            val layout = measurer.measure(char.toString(), TextStyle(color = faint, fontSize = 22.sp))
            drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2))
        } else {
            val side = size.minDimension * 0.76f
            val offset = (size.minDimension - side) / 2
            drawStrokes(glyph.strokes, side / GlyphSpace.SIZE, Offset(offset, offset), ink, minWidth = 1.6f)
        }
    }
}

/** Draws [strokes] as ink the pen's width wide, at [scale] pixels to a unit of the drawing space. */
private fun DrawScope.drawStrokes(strokes: List<FloatArray>, scale: Float, origin: Offset, color: Color, minWidth: Float = 0f) {
    val width = (GlyphSpace.PEN * scale).coerceAtLeast(minWidth)
    for (s in strokes) {
        if (s.size < 2) continue
        if (s.size == 2) {
            drawCircle(color, width / 2, Offset(origin.x + s[0] * scale, origin.y + s[1] * scale))
            continue
        }
        val path = Path().apply {
            moveTo(origin.x + s[0] * scale, origin.y + s[1] * scale)
            for (i in 1 until s.size / 2) lineTo(origin.x + s[2 * i] * scale, origin.y + s[2 * i + 1] * scale)
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The square the selected letter is drawn in: guide lines for the capital height, the small letters' height and the
 * baseline, with the letter faint behind them. A finger, a pen or a mouse all draw.
 */
@Composable
private fun GlyphPad(draft: HandFontDraft, modifier: Modifier) {
    val colors = LocalSettingsColors.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var live by remember { mutableStateOf(FloatArray(0)) }
    val commit = rememberUpdatedState { stroke: FloatArray -> draft.add(stroke) }
    val side = with(density) { PadSide.toPx() }
    Canvas(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.inset)
            .pointerInput(side) {
                val unit = GlyphSpace.SIZE / side
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var points = floatArrayOf(down.position.x * unit, down.position.y * unit)
                    live = points
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        for (h in change.historical) points += floatArrayOf(h.position.x * unit, h.position.y * unit)
                        points += floatArrayOf(change.position.x * unit, change.position.y * unit)
                        live = points
                        change.consume()
                        if (!change.pressed) break
                    }
                    commit.value(points)
                    live = FloatArray(0)
                }
            },
    ) {
        val scale = size.width / GlyphSpace.SIZE
        val guide = colors.secondary.copy(alpha = 0.45f)
        for (y in listOf(GlyphSpace.CAP_LINE, GlyphSpace.X_LINE)) {
            drawLine(guide, Offset(0f, y * scale), Offset(size.width, y * scale), 1.5f)
        }
        drawLine(colors.secondary, Offset(0f, GlyphSpace.BASELINE * scale), Offset(size.width, GlyphSpace.BASELINE * scale), 3f)
        val capHeight = (GlyphSpace.BASELINE - GlyphSpace.CAP_LINE) * scale
        val layout = measurer.measure(
            draft.selected.toString(),
            TextStyle(color = colors.secondary.copy(alpha = 0.22f), fontSize = (capHeight / CAP_HEIGHT_EM).toSp()),
        )
        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, GlyphSpace.BASELINE * scale - layout.firstBaseline))
        drawStrokes(draft.glyphs[draft.selected]?.strokes.orEmpty(), scale, Offset.Zero, colors.text)
        if (live.isNotEmpty()) drawStrokes(listOf(clampToSpace(live)), scale, Offset.Zero, colors.text)
    }
}

/** A capital's height as a share of the font size in the default font, to size the faint letter behind the pad. */
private const val CAP_HEIGHT_EM = 0.72f
