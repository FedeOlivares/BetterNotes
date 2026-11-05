package app.betternotes.ui.editor

import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.betternotes.canvas.CanvasView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.model.LineKind
import app.betternotes.core.tools.InkPreset
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** What the toolbars ask of whoever hosts the editor. */
internal class EditorActions(
    val onBack: () -> Unit,
    val onZoomToRealSize: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** The library's id of the document being edited, or null for a scratch note. */
    val documentId: () -> Long?,
    val onOpenDocument: (Long) -> Unit,
    val onToggleTheme: () -> Unit,
    /** Stand-ins until the library opens documents: not part of the design. */
    val onToggleScratchKind: () -> Unit,
    val onTogglePerfReadout: (() -> Unit)?,
    /** The open document's title, page count and dates, for the menu's card. */
    val documentInfo: () -> DocumentInfo?,
    val onOpenOverlay: (app.betternotes.ui.settings.SettingsOverlay) -> Unit,
    val onRename: (String) -> Unit,
)

private const val LEFT = "left"
private const val CENTER = "center"
private const val RIGHT = "right"
private const val TOOL_ROW = "row"
private val EdgeMargin = 6.dp
private val PillGap = 10.dp
private val PanelEndMargin = 8.dp
private val PanelGap = 7.dp
private const val SAVE_DELAY_MS = 400L
private val RECORDER_MENU_END = 70.dp

/**
 * The sizes in this package were read off screenshots at 2.5 pixels per unit, and the tablet those
 * screenshots came from has 2.75 pixels per dp. This makes them come out the same size on it.
 */
private const val SCREENSHOT_SCALE = 2.5f / 2.75f

/**
 * The floating controls over the canvas, laid out from the owner's Notewise screenshots: the left,
 * center and right pills in a row, and the active tool's row centered under the center pill.
 */
@Composable
internal fun EditorChrome(state: EditorState, actions: EditorActions) {
    val notices = rememberNotices()
    val preset = state.preset

    LaunchedEffect(preset, state.eraser, state.lasso, state.shape, state.note, state.table, state.image, state.tape) {
        delay(SAVE_DELAY_MS)
        state.saveTools()
    }

    val density = LocalDensity.current
    val designDensity = Density(density.density * SCREENSHOT_SCALE, density.fontScale)
    CompositionLocalProvider(LocalDensity provides designDensity) {
        EditorControls(state, actions, notices, preset)
    }
}

/** The second row for [tool]. */
@Composable
private fun ToolRowFor(tool: ActiveTool, state: EditorState, sources: ImageSources) {
    val none = Modifier
    val preset = state.presetFor(tool)
    when {
        preset != null -> ToolRow(state, preset, none)
        tool == ActiveTool.LASER -> LaserRow(state, none)
        tool == ActiveTool.ERASER -> EraserRow(state, state.eraser, none)
        tool == ActiveTool.LASSO -> LassoRow(state, state.lasso, none)
        tool == ActiveTool.SHAPE -> ShapeRow(state, state.shape, none)
        tool == ActiveTool.STICKY || tool == ActiveTool.TEXT -> NoteRow(state, state.note, none, tool == ActiveTool.STICKY)
        tool == ActiveTool.TABLE -> TableRow(state, state.table, none)
        tool == ActiveTool.TAPE -> TapeRow(state, state.tape, none)
        tool == ActiveTool.EMOJI -> EmojiRow(state, none)
        tool == ActiveTool.IMAGE -> ImageRow(state, state.image, sources, none)
    }
}

/** How long a change of tool takes to show: the row turning into the next one and the selection circle growing, together. */
internal const val TOOL_CHANGE_MS = 250

@Composable
private fun EditorControls(state: EditorState, actions: EditorActions, notices: Notices, preset: InkPreset?) {
    val sources = rememberImageSources(state)
    val canvasContext = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { RecorderState(canvasContext, state.canvasForRecorder) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    state.say = notices::say
    Box(Modifier.fillMaxSize()) {
        // A touch outside an open panel closes it. It sits under the toolbars, so they still work.
        if (state.panelOpen || state.colorTarget != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            state.panelOpen = false
                            state.colorTarget = null
                        }
                    },
            )
        }
        Layout(
            content = {
                LeftPill(state, actions, notices, Modifier.layoutId(LEFT).masksInk(state, LEFT))
                if (!state.viewOnly) {
                    CenterPill(state, recorder, sources, actions, notices, Modifier.layoutId(CENTER).masksInk(state, CENTER))
                }
                RightPill(state, recorder, actions, Modifier.layoutId(RIGHT).masksInk(state, RIGHT))
                if (!state.viewOnly && (recorder.recording || !state.toolRowCollapsed)) {
                    // The row of the tool in use turns into the next one's when the tool changes: its box grows or shrinks to the new
                    // row's size while one row gives way to the other. Each row is built from the tool it belongs to, so the one
                    // leaving keeps showing its own tool.
                    Box(Modifier.layoutId(TOOL_ROW).masksInk(state, TOOL_ROW)) {
                        AnimatedContent(
                            targetState = if (recorder.recording) null else state.activeTool,
                            transitionSpec = {
                                (fadeIn(tween(TOOL_CHANGE_MS)) togetherWith fadeOut(tween(TOOL_CHANGE_MS)))
                                    .using(SizeTransform(clip = false) { _, _ -> tween(TOOL_CHANGE_MS) })
                            },
                            label = "toolRow",
                        ) { tool ->
                            if (tool == null) RecordingControls(state, recorder, Modifier) else ToolRowFor(tool, state, sources)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = EdgeMargin.roundToPx()
            val gap = PillGap.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            fun slot(id: String) = measurables.firstOrNull { it.layoutId == id }
            val left = slot(LEFT)!!.measure(loose)
            val right = slot(RIGHT)!!.measure(loose)
            val centerMax = (constraints.maxWidth - left.width - right.width - 2 * margin - 2 * gap).coerceAtLeast(0)
            val center = slot(CENTER)?.measure(Constraints(maxWidth = centerMax, maxHeight = constraints.maxHeight))
            val centerStart = center?.let {
                // In the middle of the screen when there is room, and otherwise between its neighbors.
                ((constraints.maxWidth - it.width) / 2).coerceIn(
                    left.width + margin + gap,
                    (constraints.maxWidth - right.width - margin - gap - it.width).coerceAtLeast(left.width + margin + gap),
                )
            } ?: 0
            val row = slot(TOOL_ROW)?.measure(Constraints(maxWidth = constraints.maxWidth - 2 * margin, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                left.place(margin, margin)
                right.place(constraints.maxWidth - right.width - margin, margin)
                center?.place(centerStart, margin)
                if (row != null) {
                    val rowCenter = if (center != null) centerStart + center.width / 2 else constraints.maxWidth / 2
                    val rowStart = (rowCenter - row.width / 2).coerceIn(margin, (constraints.maxWidth - row.width - margin).coerceAtLeast(margin))
                    row.place(rowStart, margin + left.height + gap)
                }
            }
        }

        if (state.loupeOpen && !state.viewOnly) LoupeBar(state, Modifier.align(Alignment.BottomCenter))
        PositionControls(state)
        state.textEdit?.let { request -> TextEditOverlay(request) { request.finish(it) } }
        if (recorder.menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        recorder.menuOpen = false
                    },
            )
            RecorderMenu(
                state,
                recorder,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = EdgeMargin + PillHeight + PanelGap, end = RECORDER_MENU_END)
                    .masksInk(state, "recorder"),
            )
        }
        state.playingVideo?.let { request -> VideoPlayer(request) { state.playingVideo = null } }
        if (state.moreMenuOpen) EditorMenu(state, actions) { state.moreMenuOpen = false }
        if (state.colorListOpen && preset != null) ColorListSheet(state, preset, notices)
        if (state.sourcesOpen) SourcesColumn(state, actions, sources, recorder, notices) { state.sourcesOpen = false }
        state.scanPhoto?.let { ScanCrop(state, it) }
        if (state.pagesOpen) AllPagesPanel(state, notices)
        if (state.quickSwitchOpen) QuickSwitch(state, actions, notices)

        val panelTop = EdgeMargin + PillHeight + PillGap + PillHeight + PanelGap
        val panelModifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = panelTop, end = PanelEndMargin, bottom = PanelEndMargin)
        if (state.colorTarget != null) {
            ColorWheelPanel(state, panelModifier.masksInk(state, "wheel"))
        } else if (state.panelOpen) {
            val sheet = panelModifier.masksInk(state, "panel")
            when {
                preset != null -> InkPanel(state, preset, notices, sheet)
                state.activeTool == ActiveTool.ERASER -> EraserPanel(state, state.eraser, notices, sheet)
                state.activeTool == ActiveTool.LASSO -> LassoPanel(state, state.lasso, notices, sheet)
                state.activeTool == ActiveTool.SHAPE -> ShapePanel(state, state.shape, sheet)
                state.activeTool == ActiveTool.TAPE -> TapePanel(state, state.tape, sheet)
                state.activeTool == ActiveTool.STICKY -> NotePanel(state, state.note, sheet, sticky = true)
                state.activeTool == ActiveTool.TEXT -> NotePanel(state, state.note, sheet, sticky = false)
                state.activeTool == ActiveTool.TABLE -> TablePanel(state, state.table, sheet)
                state.activeTool == ActiveTool.IMAGE -> ImagePanel(state, sources, notices, sheet)
                state.activeTool == ActiveTool.EMOJI -> EmojiPanel(state, sheet)
                state.activeTool == ActiveTool.LASER -> LaserPanel(state, sheet)
            }
        }
    }
}

@Composable
private fun LeftPill(state: EditorState, actions: EditorActions, notices: Notices, modifier: Modifier) {
    Pill(modifier) {
        ToolbarIcon(EditorIcons.Back, stringResource(R.string.editor_back), onClick = actions.onBack)
        ToolbarIcon(EditorIcons.Sidebar, stringResource(R.string.editor_sidebar), onClick = { state.sourcesOpen = true })
        ToolbarIcon(EditorIcons.PageGrid, stringResource(R.string.editor_page_grid), onClick = { state.pagesOpen = true })
        ToolbarIcon(EditorIcons.PageStack, stringResource(R.string.editor_page_stack)) {
            state.quickSwitchOpen = true
        }
        ToolbarIcon(EditorIcons.ChevronsUpDown, stringResource(R.string.editor_chevrons), onClick = notices::notBuilt)
    }
}

/** One button in the strip that scrolls. A tool without a reference screenshot only says so for now. */
private class StripItem(val icon: ImageVector, val label: Int, val tool: ActiveTool? = null)

/** In the order of the owner's screenshots. */
private val StripItems by lazy {
    listOf(
        StripItem(EditorIcons.Lasso, R.string.tool_lasso, ActiveTool.LASSO),
        StripItem(EditorIcons.Pen, R.string.tool_pen, ActiveTool.PEN),
        StripItem(EditorIcons.Brush, R.string.tool_pencil, ActiveTool.PENCIL),
        StripItem(EditorIcons.Highlighter, R.string.tool_highlighter, ActiveTool.HIGHLIGHTER),
        StripItem(EditorIcons.Eraser, R.string.tool_eraser, ActiveTool.ERASER),
        StripItem(EditorIcons.Tape, R.string.tool_tape, ActiveTool.TAPE),
        StripItem(EditorIcons.Shapes, R.string.tool_shapes, ActiveTool.SHAPE),
        StripItem(EditorIcons.Image, R.string.tool_image, ActiveTool.IMAGE),
        StripItem(EditorIcons.Text, R.string.tool_text, ActiveTool.TEXT),
        StripItem(EditorIcons.StickyNote, R.string.tool_sticky, ActiveTool.STICKY),
        StripItem(EditorIcons.Sticker, R.string.tool_sticker, ActiveTool.EMOJI),
        StripItem(EditorIcons.Table, R.string.tool_table, ActiveTool.TABLE),
        StripItem(EditorIcons.Tray, R.string.tool_clipboard),
        StripItem(EditorIcons.AddElement, R.string.tool_add_element),
        StripItem(EditorIcons.Loupe, R.string.tool_loupe),
        StripItem(EditorIcons.Ruler, R.string.tool_ruler),
        StripItem(EditorIcons.Mic, R.string.tool_recorder),
        StripItem(EditorIcons.Laser, R.string.tool_laser, ActiveTool.LASER),
        StripItem(EditorIcons.Settings, R.string.tool_settings),
    )
}

@Composable
private fun CenterPill(
    state: EditorState,
    recorder: RecorderState,
    sources: ImageSources,
    actions: EditorActions,
    notices: Notices,
    modifier: Modifier,
) {
    Pill(modifier) {
        ToolbarIcon(EditorIcons.Undo, stringResource(R.string.editor_undo), enabled = state.canUndo, onClick = state::undo)
        ToolbarIcon(EditorIcons.Redo, stringResource(R.string.editor_redo), enabled = state.canRedo, onClick = state::redo)
        ToolbarIcon(
            // The icon shows the mode in use, so there is no highlight: a hand with arrows scrolls, a hand drawing a line draws.
            if (state.fingerDraws) EditorIcons.FingerDraw else EditorIcons.FingerMode,
            stringResource(R.string.editor_finger_mode),
            onClick = state::toggleFingerDraw,
        )
        PillDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState()),
        ) {
            for (item in StripItems) {
                if (item.label == R.string.tool_recorder && recorder.recording) {
                    StopRecordingButton(recorder)
                    continue
                }
                val tool = item.tool
                ToolbarIcon(
                    item.icon,
                    stringResource(item.label),
                    selected = when (item.label) {
                        R.string.tool_ruler -> state.rulerOn
                        R.string.tool_loupe -> state.loupeOpen
                        else -> tool == state.activeTool
                    },
                    onClick = {
                        when {
                            tool != null -> state.select(tool)
                            item.label == R.string.tool_ruler -> state.toggleRuler()
                            item.label == R.string.tool_clipboard -> state.paste()
                            item.label == R.string.tool_loupe -> state.toggleLoupe()
                            item.label == R.string.tool_settings -> actions.onOpenSettings()
                            item.label == R.string.tool_add_element -> sources.pickVideo()
                            item.label == R.string.tool_recorder -> recorder.menuOpen = !recorder.menuOpen
                            else -> notices.notBuilt()
                        }
                    },
                )
            }
        }
        PillDivider()
        ToolbarIcon(
            if (state.toolRowCollapsed) EditorIcons.ChevronDown else EditorIcons.ChevronUp,
            stringResource(R.string.editor_collapse),
            onClick = { state.toolRowCollapsed = !state.toolRowCollapsed },
        )
    }
}

@Composable
private fun RightPill(state: EditorState, recorder: RecorderState, actions: EditorActions, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Pill(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (state.viewOnly) colors.accentContainer else colors.raisedButton)
                .tappable(radius = 21.dp, onClick = state::toggleViewOnly),
        ) {
            Icon(
                EditorIcons.Eye,
                stringResource(R.string.editor_view_only),
                tint = if (state.viewOnly) colors.accent else colors.icon,
                modifier = Modifier.size(24.dp),
            )
        }
        Box {
            ToolbarIcon(EditorIcons.More, stringResource(R.string.editor_more), onClick = { state.moreMenuOpen = true })
            if (recorder.recording) RecordingDot(Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The laser's row: its color, three sizes, and the way into its options. */
@Composable
private fun LaserRow(state: EditorState, modifier: Modifier) {
    Pill(modifier) {
        ToolbarCellBox(onClick = { state.panelOpen = !state.panelOpen }) { ColorDot(state.laserColor, diameter = 24.dp) }
        for (slot in 0..2) {
            ToolbarCellBox(onClick = { state.selectLaserWidth(slot) }) {
                if (slot == 0) {
                    Box(Modifier.size(5.dp).background(LocalEditorColors.current.icon, CircleShape))
                } else {
                    WidthGlyph(if (slot == 1) LASER_MIDDLE_GLYPH else LASER_THICK_GLYPH)
                }
                if (slot == state.laserWidthSlot) SelectedDot()
            }
        }
        PillDivider()
        ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
            state.panelOpen = !state.panelOpen
        }
    }
}

/**
 * The page and zoom readout at the lower left and the page buttons at the right edge. They show while the
 * page moves and go away soon after, as in the screenshots, where they are only sometimes there.
 */
@Composable
private fun BoxScope.PositionControls(state: EditorState) {
    val colors = LocalEditorColors.current
    var visible by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    val moves = state.viewMoves
    LaunchedEffect(moves, entering) {
        if (entering) {
            visible = true
            return@LaunchedEffect
        }
        if (moves <= 1) return@LaunchedEffect
        visible = true
        delay(POSITION_VISIBLE_MS)
        visible = false
    }
    if (!visible) return
    if (entering) PageEntry(state) { entering = false }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 9.dp, bottom = 12.dp)
            .masksInk(state, "readout")
            .height(PillHeight)
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.pill)
            .border(1.dp, colors.pillBorder, CircleShape)
            .tappable(radius = 60.dp, onClick = state::zoomToRealSize)
            .padding(horizontal = 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(46.dp).clickable(role = Role.Button) { entering = true },
        ) {
            Text("${state.currentPage + 1}", color = colors.title, fontSize = 12.sp, lineHeight = 14.sp)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.pillDivider))
            Text("${state.pageCount}", color = colors.label, fontSize = 12.sp, lineHeight = 14.sp)
        }
        PillDivider()
        Text(
            "${(state.zoom * 100).roundToInt()}%",
            color = colors.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    val fraction = state.scrollFraction
    val travel = remember { mutableFloatStateOf(1f) }
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(end = 2.dp)
            // The buttons ride along the edge with the page's position, as a scroll handle does.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                val room = (constraints.maxHeight - placeable.height).coerceAtLeast(0)
                travel.floatValue = room.toFloat()
                val top = if (fraction == null) room / 2 else (room * fraction).roundToInt()
                layout(placeable.width, constraints.maxHeight) { placeable.place(0, top) }
            }
            .masksInk(state, "page-buttons")
            .pointerInput(Unit) {
                // Dragging the pair along the edge scrolls the document to match, the way a scrubber does.
                // The pair moves under the finger, so each step is only what the finger moved over it.
                var at = 0f
                detectDragGestures(
                    onDragStart = { at = state.scrollFraction ?: 0f },
                    onDragEnd = { state.scrubTo(at, true) },
                    onDragCancel = { state.scrubTo(at, true) },
                ) { change, drag ->
                    change.consume()
                    at = (at + drag.y / travel.floatValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    state.scrubTo(at, false)
                }
            }
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.pill)
            .border(1.dp, colors.pillBorder, CircleShape),
    ) {
        PageButton(EditorIcons.ChevronUp, stringResource(R.string.editor_previous_page), state::previousPage)
        PageButton(EditorIcons.ChevronDown, stringResource(R.string.editor_next_page), state::nextPage)
    }
    val map = remember(moves) { state.minimap() }
    if (map != null) MinimapView(map, Modifier.align(Alignment.BottomEnd).masksInk(state, "minimap"))
}

/** A small map of an infinite page, shown while it scrolls: where things are written, and what is in view. */
@Composable
private fun MinimapView(map: CanvasView.Minimap, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Canvas(
        modifier
            .padding(end = 12.dp, bottom = 12.dp)
            .size(MINIMAP_WIDTH, MINIMAP_HEIGHT)
            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.pill)
            .border(1.dp, colors.pillBorder, RoundedCornerShape(12.dp)),
    ) {
        var left = map.viewLeft
        var top = map.viewTop
        var right = map.viewRight
        var bottom = map.viewBottom
        var i = 0
        while (i < map.items.size) {
            left = minOf(left, map.items[i])
            top = minOf(top, map.items[i + 1])
            right = maxOf(right, map.items[i + 2])
            bottom = maxOf(bottom, map.items[i + 3])
            i += 4
        }
        val inset = 8.dp.toPx()
        val scale = minOf((size.width - 2 * inset) / (right - left).coerceAtLeast(1f), (size.height - 2 * inset) / (bottom - top).coerceAtLeast(1f))
        val offsetX = (size.width - (right - left) * scale) / 2f
        val offsetY = (size.height - (bottom - top) * scale) / 2f
        fun x(value: Float) = offsetX + (value - left) * scale
        fun y(value: Float) = offsetY + (value - top) * scale
        i = 0
        while (i < map.items.size) {
            val w = ((map.items[i + 2] - map.items[i]) * scale).coerceAtLeast(MIN_MARK_DP.dp.toPx())
            val h = ((map.items[i + 3] - map.items[i + 1]) * scale).coerceAtLeast(MIN_MARK_DP.dp.toPx())
            drawRect(colors.label.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(x(map.items[i]), y(map.items[i + 1])), androidx.compose.ui.geometry.Size(w, h))
            i += 4
        }
        val view = androidx.compose.ui.geometry.Offset(x(map.viewLeft), y(map.viewTop))
        val viewSize = androidx.compose.ui.geometry.Size((map.viewRight - map.viewLeft) * scale, (map.viewBottom - map.viewTop) * scale)
        drawRect(colors.accent.copy(alpha = 0.18f), view, viewSize)
        drawRect(colors.accent, view, viewSize, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}

private val MINIMAP_WIDTH = 150.dp
private val MINIMAP_HEIGHT = 110.dp
private const val MIN_MARK_DP = 1.5f

@Composable
private fun PageButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(22.dp, 28.dp).tappable(radius = 14.dp, onClick = onClick),
    ) {
        Icon(icon, description, tint = LocalEditorColors.current.icon, modifier = Modifier.size(20.dp))
    }
}

/** The second row: line type, three widths, the colors, and the way into the panel. */
@Composable
private fun ToolRow(state: EditorState, preset: InkPreset, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Pill(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ToolbarCellBox(onClick = { state.update { it.copy(line = it.line.next()) } }) {
                LineTypeGlyph(preset.line, colors.icon)
            }
            PillDivider()
            preset.widths.forEachIndexed { slot, thickness ->
                ToolbarCellBox(onClick = { state.update { it.copy(selectedWidth = slot) } }) {
                    WidthGlyph(thickness)
                    if (slot == preset.selectedWidth) SelectedDot()
                }
            }
            PillDivider()
            val draft = state.colorDraft
            if (draft != null) {
                ColorEditor(state, draft)
            } else {
                preset.colors.forEachIndexed { slot, color ->
                    ToolbarCellBox(onClick = { state.update { it.copy(selectedColor = slot) } }) {
                        ColorDot(color)
                        if (slot == preset.selectedColor) SelectedDot()
                    }
                }
                Box {
                    ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.moreColorsOpen = true }
                    if (state.moreColorsOpen) MoreColors(state, preset)
                }
                ToolbarIcon(EditorIcons.EditColor, stringResource(R.string.editor_edit_color)) {
                    state.colorDraft = preset.colors
                }
            }
            if (state.colorDraft == null) {
                PillDivider()
                ToolbarIcon(EditorIcons.Sliders, stringResource(R.string.editor_tool_options)) {
                    state.panelOpen = !state.panelOpen
                }
            }
        }
    }
}

/** Favorite colors beyond the five on the bar: the pen's own swatches, then a few more. Tapping one puts it on the bar. */
@Composable
private fun MoreColors(state: EditorState, preset: InkPreset) {
    val colors = LocalEditorColors.current
    val favorites = (preset.palette + MORE_FAVORITES).distinct()
    DropdownMenu(expanded = true, onDismissRequest = { state.moreColorsOpen = false }) {
        Text(
            stringResource(R.string.editor_more_colors),
            color = colors.label,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Column(Modifier.padding(horizontal = 8.dp).width((MORE_COLORS_COLUMNS * 44).dp)) {
            favorites.chunked(MORE_COLORS_COLUMNS).forEach { line ->
                Row {
                    for (color in line) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp).tappable(radius = 20.dp) {
                                state.update { it.withColorAdded(color) }
                                state.moreColorsOpen = false
                            },
                        ) {
                            ColorDot(color, diameter = 28.dp)
                            if (color in preset.colors) SelectedDot()
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.sweepGradient(HueStops))
                        .tappable(radius = 20.dp) {
                            state.moreColorsOpen = false
                            state.colorTarget = ColorTarget.CUSTOM_SLOT
                        },
                )
            }
        }
    }
}

private const val MORE_COLORS_COLUMNS = 6

private val MORE_FAVORITES = listOf(
    0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(),
    0xFFB71C1C.toInt(), 0xFFE53935.toInt(), 0xFFFF7043.toInt(), 0xFFFFA000.toInt(),
    0xFFFDD835.toInt(), 0xFFC0CA33.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(),
    0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF3949AB.toInt(), 0xFF8E24AA.toInt(),
    0xFFD81B60.toInt(), 0xFF6D4C41.toInt(),
)

/**
 * The bar while its colors are edited: each color has a red minus to remove it, holding and dragging one moves it,
 * and a cross discards the changes while a tick keeps them.
 */
@Composable
private fun ColorEditor(state: EditorState, draft: List<Int>) {
    val colors = LocalEditorColors.current
    val cell = ToolbarCell
    val density = androidx.compose.ui.platform.LocalDensity.current
    draft.forEachIndexed { index, color ->
        var dragged by remember(color) { mutableStateOf(0f) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(cell)
                .height(PillHeight)
                .offset { androidx.compose.ui.unit.IntOffset(dragged.roundToInt(), 0) }
                .pointerInput(color, draft.size) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { dragged = 0f },
                        onDragCancel = { dragged = 0f },
                    ) { change, drag ->
                        change.consume()
                        dragged += drag.x
                        val step = with(density) { cell.toPx() }
                        // Past half a cell the color trades places with its neighbour.
                        val current = state.colorDraft ?: return@detectDragGesturesAfterLongPress
                        val at = current.indexOf(color)
                        val target = (at + if (dragged > step / 2f) 1 else if (dragged < -step / 2f) -1 else 0).coerceIn(0, current.lastIndex)
                        if (target != at) {
                            val moved = current.toMutableList().apply { add(target, removeAt(at)) }
                            state.colorDraft = moved
                            dragged -= (target - at) * step
                        }
                    }
                },
        ) {
            ColorDot(color)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .tappable(radius = 12.dp) {
                        val current = state.colorDraft ?: return@tappable
                        if (current.size > 1) state.colorDraft = current - color
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp, 2.dp).background(Color.White))
            }
        }
    }
    PillDivider()
    ToolbarIcon(EditorIcons.Close, stringResource(R.string.editor_colors_discard)) { state.colorDraft = null }
    ToolbarIcon(EditorIcons.Check, stringResource(R.string.editor_colors_accept)) { state.acceptColorDraft() }
}

internal fun LineKind.next() = when (this) {
    LineKind.SOLID -> LineKind.DASHED
    LineKind.DASHED -> LineKind.DOTTED
    LineKind.DOTTED -> LineKind.SOLID
}

/** A short bar as thick as the width slot, scaled the way the screenshot's bars are. */
@Composable
internal fun WidthGlyph(thickness: Int) {
    val tint = LocalEditorColors.current.icon
    Canvas(Modifier.size(26.dp, 12.dp)) {
        val stroke = (thickness * WIDTH_GLYPH_DP_PER_UNIT).dp.toPx().coerceIn(2.dp.toPx(), size.height)
        val inset = stroke / 2f
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(inset, size.height / 2f),
            androidx.compose.ui.geometry.Offset(size.width - inset, size.height / 2f),
            stroke,
            StrokeCap.Round,
        )
    }
}

private const val WIDTH_GLYPH_DP_PER_UNIT = 0.34f
private const val LASER_MIDDLE_GLYPH = 6
private const val LASER_THICK_GLYPH = 12
private const val POSITION_VISIBLE_MS = 2000L


/** A small box to type a page number into, opened by tapping the page readout. */
@Composable
private fun PageEntry(state: EditorState, close: () -> Unit) {
    val colors = LocalEditorColors.current
    var text by remember { mutableStateOf(TextFieldValue("${state.currentPage + 1}", TextRange(0, "${state.currentPage + 1}".length))) }
    val focus = remember { FocusRequester() }
    val go = {
        text.text.toIntOrNull()?.let { state.goToPageNumber(it.coerceIn(1, state.pageCount.coerceAtLeast(1))) }
        close()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = close) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(colors.pill)
                .border(1.dp, colors.pillBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { v -> if (v.text.length <= 5 && v.text.all(Char::isDigit)) text = v },
                singleLine = true,
                textStyle = TextStyle(color = colors.title, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                modifier = Modifier.width(72.dp).focusRequester(focus),
            )
            Text("/ ${state.pageCount}", color = colors.label, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp, end = 14.dp))
            Text("Go", color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(role = Role.Button) { go() }.padding(8.dp))
        }
    }
}
