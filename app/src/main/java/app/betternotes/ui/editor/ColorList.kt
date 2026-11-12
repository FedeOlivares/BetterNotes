package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.betternotes.R
import app.betternotes.core.tools.InkPreset
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.RGB_MASK
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import app.betternotes.ui.theme.opensFrom

/**
 * Every swatch of the tool's color row as a list, from the owner's screenshot: the color and its hex code,
 * a handle to drag it up or down, and a + to add one. Touching a row chooses that color.
 */
@Composable
internal fun ColorListSheet(state: EditorState, preset: InkPreset, notices: Notices) {
    val colors = LocalEditorColors.current
    val density = LocalDensity.current
    val rowStep = with(density) { (ROW_HEIGHT + ROW_GAP).toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .background(UiColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                state.colorListOpen = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .opensFrom(OpenFrom.Center)
                .width(SHEET_WIDTH)
                .height(SHEET_HEIGHT)
                .clip(AppShapes.Rounded28)
                .background(colors.panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = Spacing.S16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Spacing.S18)) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.panel_color), color = colors.title, fontSize = TextSize.S22)
                    Spacer(Modifier.width(Spacing.S8))
                    Box(
                        Modifier.size(30.dp).clip(AppShapes.Circle).clickable { notices.say(R.string.info_color_list) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EditorIcons.Info, null, tint = colors.label, modifier = Modifier.size(22.dp)) }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(AppShapes.Circle)
                        .background(colors.closeButton)
                        .clickable(role = Role.Button) { state.colorListOpen = false },
                    contentAlignment = Alignment.Center,
                ) { Icon(EditorIcons.Close, stringResource(R.string.panel_close), tint = colors.label, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(Spacing.S10))
            Box(
                Modifier
                    .size(54.dp)
                    .clip(AppShapes.Circle)
                    .background(colors.toolSelected)
                    .clickable(role = Role.Button) {
                        state.colorListOpen = false
                        state.colorTarget = ColorTarget.PALETTE
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(EditorIcons.Plus, stringResource(R.string.panel_add_swatch), tint = colors.toolSelectedIcon, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(Spacing.S18))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.S36),
            ) {
                itemsIndexed(preset.palette, key = { _, color -> color }) { index, color ->
                    var dragged by remember { mutableFloatStateOf(0f) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .clip(AppShapes.Rounded16)
                            .background(colors.field)
                            .clickable {
                                state.update { it.pickColor(color) }
                                state.colorListOpen = false
                            }
                            .padding(horizontal = Spacing.S16),
                    ) {
                        ColorDot(color, diameter = 32.dp)
                        Spacer(Modifier.width(Spacing.S18))
                        Text(
                            "#%06X".format(color and RGB_MASK),
                            color = colors.title,
                            fontSize = TextSize.S20,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            EditorIcons.Grip,
                            stringResource(R.string.color_list_drag),
                            tint = colors.label,
                            modifier = Modifier
                                .size(32.dp)
                                .pointerInput(preset.palette, index) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { dragged = 0f },
                                        onDragCancel = { dragged = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        dragged += amount
                                        val steps = (dragged / rowStep).toInt()
                                        val target = (index + steps).coerceIn(0, preset.palette.lastIndex)
                                        if (steps != 0 && target != index) {
                                            val moved = preset.palette.toMutableList().apply { add(target, removeAt(index)) }
                                            state.update { it.copy(palette = moved) }
                                            dragged = 0f
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

private val SHEET_WIDTH = 560.dp
private val SHEET_HEIGHT = 620.dp
private val ROW_HEIGHT = 58.dp
private val ROW_GAP = 8.dp
