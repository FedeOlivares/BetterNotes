package app.betternotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.betternotes.R
import app.betternotes.core.model.Layer
import app.betternotes.core.model.PageContent
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import kotlin.math.roundToInt

private val LayerRowHeight = 48.dp
private val LayerRowGap = 6.dp
private const val MAX_LAYER_NAME = 40
private val LayersWidth = PanelWidth * 1.3f

/**
 * The Layers menu, built like the one that rearranges the toolbar's icons: a row for each layer, the top layer first, held and dragged to
 * reorder, with an eye to hide it. A tap on a row makes it the layer being worked on, and nothing on the others can be changed.
 */
@Composable
internal fun LayersSheet(state: EditorState, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    // Read so that the menu follows the edits, undo and the page in view. The page content is changed in place, so it is also passed down as
    // an argument: without it the sheet's body and the rows are skipped as unchanged and keep showing the old layers.
    val tick = state.historyVersion + state.viewMoves
    val content = state.layersContent()
    PanelSheet(stringResource(R.string.layers_title), onClose = { state.layersOpen = false }, modifier = modifier, scrolls = false, width = LayersWidth) {
        if (content == null || tick < 0) return@PanelSheet
        val layers = content.layers
        val active = content.activeLayer
        var renaming by remember(active) { mutableStateOf(false) }
        LayerRows(state, content, layers.all.toList(), active, tick)
        if (renaming) {
            RenameRow(layers[active]?.name.orEmpty()) { name ->
                renaming = false
                if (name != null) state.renameLayer(active, name)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.panelDivider))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S8),
        ) {
            LayerAction(EditorIcons.Plus, R.string.layers_add, true) { state.addLayer() }
            LayerAction(EditorIcons.Pencil, R.string.layers_rename, true) { renaming = !renaming }
            LayerAction(EditorIcons.ChevronsUpDown, R.string.layers_merge, layers.size > 1) { state.mergeLayerDown(active) }
            LayerAction(EditorIcons.Trash, R.string.layers_delete, layers.size > 1) { state.deleteLayer(active) }
        }
    }
}

/** The rows, top layer first. Held for a moment, a row follows the finger and lands where it is let go. */
@Composable
private fun LayerRows(state: EditorState, content: PageContent, layers: List<Layer>, active: Long, @Suppress("UNUSED_PARAMETER") version: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val step = with(density) { (LayerRowHeight + LayerRowGap).toPx() }
    val shownTopFirst = layers.asReversed()
    var dragging by remember { mutableStateOf<Long?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabbedAt by remember { mutableFloatStateOf(0f) }
    val held = dragging
    val slot = (pointerY / step).toInt().coerceIn(0, shownTopFirst.size - 1)
    val order = if (held == null) {
        shownTopFirst
    } else {
        shownTopFirst.filter { it.id != held }.toMutableList().apply { add(slot, shownTopFirst.first { it.id == held }) }
    }
    Box(
        Modifier
            .padding(horizontal = PanelPadding)
            .padding(top = Spacing.S6, bottom = Spacing.S10)
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LayerRowGap),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(shownTopFirst) {
                    val eyeFrom = size.width - with(density) { 88.dp.toPx() }
                    val eyeTo = size.width - with(density) { 44.dp.toPx() }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { at ->
                            val row = (at.y / step).toInt()
                            if (row in shownTopFirst.indices && at.x !in eyeFrom..eyeTo) {
                                dragging = shownTopFirst[row].id
                                pointerY = at.y
                                grabbedAt = at.y - row * step
                            }
                        },
                        onDragEnd = {
                            val id = dragging
                            if (id != null) {
                                // The rows count from the top, and layers from the bottom.
                                val row = (pointerY / step).toInt().coerceIn(0, shownTopFirst.size - 1)
                                state.moveLayer(id, shownTopFirst.size - 1 - row)
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
            for (layer in order) {
                androidx.compose.runtime.key(layer.id) {
                    LayerRow(state, content, layer, layer.id == active, layers.indexOfFirst { it.id == layer.id }, placeholder = layer.id == held, floating = false)
                }
            }
        }
        val inHand = held?.let { id -> shownTopFirst.firstOrNull { it.id == id } }
        if (inHand != null) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(0, (pointerY - grabbedAt).roundToInt()) }
                    .zIndex(1f),
            ) { LayerRow(state, content, inHand, inHand.id == active, layers.indexOfFirst { it.id == inHand.id }, placeholder = false, floating = true) }
        }
    }
}

@Composable
private fun LayerRow(state: EditorState, content: PageContent, layer: Layer, isActive: Boolean, rank: Int, placeholder: Boolean, floating: Boolean) {
    val colors = LocalEditorColors.current
    val name = layer.name.ifEmpty { stringResource(R.string.layer_default_name, rank + 1) }
    val count = remember(content.version) { content.indicesOn(layer.id).size }
    val shape = AppShapes.Rounded16
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(LayerRowHeight)
            .graphicsLayer { alpha = if (placeholder) 0.35f else 1f }
            .clip(shape)
            .background(if (floating) colors.raisedButton else if (isActive) colors.accentContainer else colors.field)
            .then(if (isActive) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .clickable(enabled = !floating, role = Role.RadioButton, onClickLabel = stringResource(R.string.layers_work_on)) { state.selectLayer(layer.id) }
            .padding(start = Spacing.S12),
    ) {
        androidx.compose.material3.Icon(
            EditorIcons.Layers,
            null,
            tint = if (!layer.visible) colors.iconDisabled else if (isActive) colors.accent else colors.icon,
            modifier = Modifier.size(24.dp),
        )
        Text(
            name,
            color = if (layer.visible) colors.title else colors.iconDisabled,
            fontSize = TextSize.S15,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.S12),
        )
        // With something selected, the other layers offer to take it. The selection is on the layer being worked on.
        if (!isActive && !floating && !state.selection.isNullOrEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.layers_move_here)) { state.moveSelectionToLayer(layer.id) },
            ) {
                androidx.compose.material3.Icon(EditorIcons.AddElement, stringResource(R.string.layers_move_here), tint = colors.accent, modifier = Modifier.size(22.dp))
            }
        }
        Text(count.toString(), color = colors.label, fontSize = TextSize.S13, modifier = Modifier.padding(horizontal = Spacing.S6))
        // Every layer can be hidden and shown, the one being worked on too: hiding it moves the work to a layer that is still shown.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = !floating, role = Role.Button) { state.setLayerVisible(layer.id, !layer.visible) },
        ) {
            androidx.compose.material3.Icon(
                if (layer.visible) EditorIcons.Eye else EditorIcons.EyeOff,
                stringResource(if (layer.visible) R.string.layers_hide else R.string.layers_show),
                tint = colors.icon,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
            androidx.compose.material3.Icon(EditorIcons.Grip, null, tint = colors.icon, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun LayerAction(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(AppShapes.Rounded14)
            .background(colors.field)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(label), onClick = onClick),
    ) {
        androidx.compose.material3.Icon(icon, stringResource(label), tint = if (enabled) colors.icon else colors.iconDisabled, modifier = Modifier.size(26.dp))
    }
}

/** A line to type a layer's new name in. [onDone] gets the name. */
@Composable
private fun RenameRow(current: String, onDone: (String?) -> Unit) {
    val colors = LocalEditorColors.current
    var text by remember { mutableStateOf(current) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PanelPadding, vertical = Spacing.S6),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(AppShapes.Rounded12)
                .background(colors.field)
                .border(1.dp, colors.accent, AppShapes.Rounded12)
                .padding(horizontal = Spacing.S12),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_LAYER_NAME) },
                singleLine = true,
                textStyle = TextStyle(color = colors.title, fontSize = TextSize.S15),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = Spacing.S10)
                .size(40.dp)
                .clip(AppShapes.Rounded20)
                .background(colors.accentContainer)
                .clickable(role = Role.Button) { onDone(text) },
        ) {
            androidx.compose.material3.Icon(EditorIcons.Check, stringResource(R.string.layers_rename), tint = colors.accent, modifier = Modifier.size(22.dp))
        }
    }
}
