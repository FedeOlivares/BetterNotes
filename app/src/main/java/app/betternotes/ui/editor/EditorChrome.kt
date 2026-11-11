package app.betternotes.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.LineKind
import app.betternotes.core.tools.InkPreset
import app.betternotes.core.tools.ToolbarEntries
import app.betternotes.core.tools.ToolbarGroups
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.isCompactScreen
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.opensFrom
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
    /** The binder's entries, read off the main thread: the notebook itself first, then its sources and linked notebooks. */
    val sources: () -> List<SourceEntry> = { emptyList() },
    val currentSourceId: () -> Long? = { null },
    val onOpenSource: (SourceEntry) -> Unit = {},
    val onRemoveSource: (SourceEntry) -> Unit = {},
    /** The library's notebooks that can be linked as sources: id and name. */
    val linkableNotebooks: () -> List<Pair<Long, String>> = { emptyList() },
    /** Splits the screen (or, when it is already split, closes this pane). */
    val onSplit: () -> Unit = {},
    val splitOpen: () -> Boolean = { false },
    /** Whether the panes are side by side now, and the switch between that and one above the other. */
    val splitSideBySide: () -> Boolean = { true },
    val onSplitFlip: () -> Unit = {},
)

private const val LEFT = "left"
private const val CENTER = "center"
private const val RIGHT = "right"
private const val TOOL_ROW = "row"
internal val EdgeMargin = 6.dp
internal val PillGap = 10.dp
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

/**
 * The second row. When the tool changes, its box grows or shrinks to the size of the new row's while the icons are
 * hidden, and they appear all at once when it has got there.
 */
@Composable
private fun ToolRowTransition(state: EditorState, recorder: RecorderState, sources: ImageSources) {
    val colors = LocalEditorColors.current
    val target = if (recorder.recording) null else state.shownTool
    var shown by remember { mutableStateOf(target) }
    // The tool whose icons may be seen. While it is not the one shown, the icons are hidden, with no frame in between.
    var revealedFor by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target != shown) {
            shown = target
            delay(BAR_RESIZE_MS.toLong())
            revealedFor = target
        }
    }
    val upright = LocalToolbarVertical.current
    Box(
        Modifier
            .then(if (upright) Modifier.width(PillHeight) else Modifier.height(PillHeight))
            .shadow(4.dp, PillShape, clip = false)
            .clip(PillShape)
            .background(colors.pill)
            .border(1.dp, colors.pillBorder, PillShape)
            .animateContentSize(tween(BAR_RESIZE_MS))
            .then(if (upright) Modifier.padding(vertical = 6.dp) else Modifier.padding(horizontal = 6.dp)),
    ) {
        CompositionLocalProvider(LocalInToolBar provides true) {
            // Nothing shows until the box has its new size, then the icons come in. The box clips them, so none is ever outside it.
            val revealed = revealedFor == shown
            val visible by animateFloatAsState(if (revealed) 1f else 0f, tween(if (revealed) ICONS_IN_MS else 0), label = "toolIcons")
            Box(Modifier.alpha(if (revealed) visible else 0f)) {
                val tool = shown
                if (tool == null) RecordingControls(state, recorder, Modifier) else ToolRowFor(tool, state, sources)
            }
        }
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
        tool == ActiveTool.LASSO && state.selection?.singleOrNull() is TextItem ->
            TextSelectionRow(state, state.selection!!.single() as TextItem, none)
        tool == ActiveTool.LASSO -> LassoRow(state, state.lasso, none)
        tool == ActiveTool.SHAPE -> ShapeRow(state, state.shape, none)
        tool == ActiveTool.STICKY && state.selectionIsNotes ->
            StickySelectionRow(state, state.selection!!.filterIsInstance<StickyNoteItem>(), none)
        tool == ActiveTool.STICKY || tool == ActiveTool.TEXT -> NoteRow(state, state.note, none, tool == ActiveTool.STICKY)
        tool == ActiveTool.TABLE -> TableRow(state, state.table, none)
        tool == ActiveTool.FILL -> FillRow(state, none)
        tool == ActiveTool.TAPE -> TapeRow(state, state.tape, none)
        tool == ActiveTool.EMOJI -> EmojiRow(state, none)
        tool == ActiveTool.IMAGE -> ImageRow(state, state.image, sources, none)
    }
}

/** How long the highlight on a tool takes to grow from its center. */
internal const val TOOL_CHANGE_MS = 250

/** The second bar resizes within this long after the tool changes, and its icons then come in within the next [ICONS_IN_MS]. */
private const val BAR_RESIZE_MS = 80
private const val ICONS_IN_MS = 30

@Composable
private fun EditorControls(state: EditorState, actions: EditorActions, notices: Notices, preset: InkPreset?) {
    val sources = rememberImageSources(state)
    val canvasContext = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { RecorderState(canvasContext, state.canvasForRecorder) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    state.say = notices::say
    DisposableEffect(state) {
        state.clipboard.start()
        onDispose { state.clipboard.stop() }
    }
    Box(Modifier.fillMaxSize()) {
        // A touch outside an open panel closes it. It sits under the toolbars, so they still work.
        if (state.panelOpen || state.colorTarget != null || state.toolbarSettingsOpen || state.customizeOpen || state.clipboardOpen || state.layersOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            state.panelOpen = false
                            state.colorTarget = null
                            state.toolbarSettingsOpen = false
                            state.customizeOpen = false
                            state.clipboardOpen = false
                            state.layersOpen = false
                        }
                    },
            )
        }
        // Before the toolbars, so they cover it while it slides out from under them.
        AddCoverButton(state)
        val screen = remember { floatArrayOf(0f, 0f) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val slide = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val heldAt = remember { floatArrayOf(0f, 0f, 0f) }
        val settle = remember { arrayOfNulls<Offset>(1) }
        // A phone has the toolbars along the bottom, and they do not move.
        val compact = isCompactScreen()
        val dragPoint = if (compact) null else state.toolbarDragPoint
        // While the tool strip is held it takes the edge its finger is by; otherwise the one it was left on.
        val edge = if (compact) ToolbarEdge.BOTTOM else state.toolbarLiveEdge ?: state.toolbarEdge
        val vertical = edge.vertical
        // Held, the strip shrinks a little and lifts, as if picked up.
        val lift by animateFloatAsState(if (dragPoint != null) 1f else 0f, tween(LIFT_MS), label = "toolbarLift")
        Layout(
            content = {
                // The back and eye pills stay where they are. Only the tool strip, and the row of the tool in use beside it, move.
                ToolbarPaint(state) { LeftPill(state, actions, notices, Modifier.layoutId(LEFT).masksInk(state, LEFT)) }
                if (!state.viewOnly) {
                    // The box stays when the strip turns between flat and upright, which rebuilds the pill inside it. The hold that is
                    // moving the strip lives on the box, so it is not lost at the turn.
                    Box(
                        Modifier
                            .layoutId(CENTER)
                            .then(if (compact) Modifier else Modifier.toolbarMover(state, { screen[0] }, { screen[1] }))
                            .graphicsLayer {
                                scaleX = 1f - LIFT_SHRINK * lift
                                scaleY = 1f - LIFT_SHRINK * lift
                                shadowElevation = LIFT_SHADOW_DP.dp.toPx() * lift
                                shape = PillShape
                            }
                            .masksInk(state, CENTER),
                    ) {
                        CompositionLocalProvider(LocalToolbarVertical provides vertical) {
                            ToolbarPaint(state) { CenterPill(state, recorder, sources, actions, notices, Modifier) }
                        }
                    }
                }
                ToolbarPaint(state) { RightPill(state, recorder, actions, Modifier.layoutId(RIGHT).masksInk(state, RIGHT)) }
                if (!state.viewOnly && (recorder.recording || !state.toolRowCollapsed)) {
                    // The row of the tool in use turns into the next one's when the tool changes: its box grows or shrinks to the new
                    // row's size while one row gives way to the other. Each row is built from the tool it belongs to, so the one
                    // leaving keeps showing its own tool.
                    CompositionLocalProvider(LocalToolbarVertical provides vertical) {
                        Box(Modifier.layoutId(TOOL_ROW).masksInk(state, TOOL_ROW)) {
                            ToolbarPaint(state) { ToolRowTransition(state, recorder, sources) }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = EdgeMargin.roundToPx()
            val gap = PillGap.roundToPx()
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            screen[0] = width.toFloat()
            screen[1] = height.toFloat()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            fun slot(id: String) = measurables.firstOrNull { it.layoutId == id }
            val left = slot(LEFT)!!.measure(loose)
            val right = slot(RIGHT)!!.measure(loose)
            // The tool strip. Held by the finger it goes anywhere; otherwise it sits on its edge.
            val free = dragPoint != null
            val atTop = !free && edge == ToolbarEdge.TOP || free && !vertical && dragPoint!!.y < height / 2f
            val topReserve = margin + left.height + gap
            val center = slot(CENTER)?.measure(
                if (vertical) {
                    Constraints(maxWidth = width, maxHeight = (height - topReserve - margin).coerceAtLeast(0))
                } else {
                    val room = if (atTop && !free) width - left.width - right.width - 2 * margin - 2 * gap else width - 2 * margin
                    Constraints(maxWidth = room.coerceAtLeast(0), maxHeight = height)
                },
            )
            val cw = center?.width ?: 0
            val ch = center?.height ?: 0
            val restX: Int
            val restY: Int
            if (free) {
                restX = (dragPoint!!.x.roundToInt() - cw / 2).coerceIn(0, (width - cw).coerceAtLeast(0))
                restY = (dragPoint.y.roundToInt() - ch / 2).coerceIn(0, (height - ch).coerceAtLeast(0))
            } else if (vertical) {
                restX = if (edge == ToolbarEdge.LEFT) margin else width - margin - cw
                restY = ((height - ch) / 2).coerceIn(topReserve, (height - margin - ch).coerceAtLeast(topReserve))
            } else if (atTop) {
                // In the middle of the screen when there is room, and otherwise between its neighbors.
                val low = left.width + margin + gap
                val high = (width - right.width - margin - gap - cw).coerceAtLeast(low)
                restX = low + (high - low) / 2
                restY = margin
            } else {
                restX = ((width - cw) / 2).coerceAtLeast(margin)
                restY = height - margin - ch
            }
            // Let go, it slides from where it was held to its resting place: the offset it left from is kept and eased out.
            if (free) {
                heldAt[0] = restX.toFloat()
                heldAt[1] = restY.toFloat()
                heldAt[2] = 1f
            } else if (heldAt[2] == 1f) {
                heldAt[2] = 0f
                val from = Offset(heldAt[0] - restX, heldAt[1] - restY)
                scope.launch {
                    slide.snapTo(from)
                    slide.animateTo(Offset.Zero, tween(SETTLE_MS))
                }
                settle[0] = from
            }
            // Read on every pass, so that the layout is measured again as the slide moves.
            val sliding = slide.value
            val slideNow = if (free) Offset.Zero else settle[0] ?: sliding
            settle[0] = null
            val cx = restX + slideNow.x.roundToInt()
            val cy = restY + slideNow.y.roundToInt()
            val row = slot(TOOL_ROW)?.measure(
                if (vertical) {
                    Constraints(maxWidth = (width - cw - 2 * margin - gap).coerceAtLeast(0), maxHeight = (height - 2 * margin).coerceAtLeast(0))
                } else {
                    val sides = if (!free && edge == ToolbarEdge.BOTTOM && !compact) SideItemReserve.roundToPx() else margin
                    Constraints(maxWidth = (width - 2 * sides).coerceAtLeast(0), maxHeight = height)
                },
            )
            layout(width, height) {
                left.place(margin, margin)
                right.place(width - right.width - margin, margin)
                center?.place(cx, cy)
                if (row != null) {
                    if (vertical) {
                        // Upright beside the strip, centered on it, on the side with room.
                        val toRight = cx + cw / 2 < width / 2
                        val rx = if (toRight) cx + cw + gap else cx - gap - row.width
                        row.place(rx.coerceAtLeast(0), (cy + ch / 2 - row.height / 2).coerceIn(0, (height - row.height).coerceAtLeast(0)))
                    } else {
                        val rowCenter = if (center != null) cx + cw / 2 else width / 2
                        val rowStart = (rowCenter - row.width / 2).coerceIn(margin, (width - row.width - margin).coerceAtLeast(margin))
                        val below = cy + ch / 2 < height / 2
                        row.place(rowStart, if (below) cy + ch + gap else (cy - gap - row.height).coerceAtLeast(0))
                    }
                }
            }
        }

        if (state.loupeOpen && !state.viewOnly) LoupeBar(state, Modifier.align(Alignment.BottomCenter))
        ToolbarPaint(state) {
            SelectionBar(state)
            PlannerRecolorBar(state)
            // Where the map of an infinite canvas goes, which a page of fixed size has none of.
            // In the bottom corner only when the toolbar is along the bottom, as the map is; otherwise in the middle, as before.
            val badgeAt = if (state.toolbarEdge == ToolbarEdge.BOTTOM || compact) {
                Modifier.align(Alignment.BottomEnd).padding(end = SIDE_ITEM_MARGIN, bottom = bandBottom(state, compact))
            } else {
                Modifier.align(Alignment.BottomCenter).padding(bottom = bandBottom(state, compact))
            }
            RotationBadge(state, badgeAt.masksInk(state, "rotationBadge"))
        }
        PositionControls(state)
        AddPageButton(state)
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
        if (state.coverOpen) {
            val theme = app.betternotes.library.LibraryPreferences.get(androidx.compose.ui.platform.LocalContext.current).theme
            app.betternotes.ui.theme.AppTheme(theme) {
                androidx.activity.compose.BackHandler { state.coverOpen = false }
                app.betternotes.ui.library.NewNotebookScreen(
                    initialTitle = "",
                    coverOnly = true,
                    initialCover = state.coverId,
                    initialLandscape = state.coverLandscape,
                    onCancel = { state.coverOpen = false },
                    onEditDesign = { choice ->
                        state.coverOpen = false
                        state.setCover(choice.coverId, choice.landscape, editable = true, recolor = choice.recolor)
                    },
                    onCreate = { choice ->
                        state.coverOpen = false
                        state.setCover(choice.coverId, choice.landscape, recolor = choice.recolor)
                    },
                )
            }
        }
        state.plannerMenu?.let { PlannerMenus(state, it) }
        if (state.quickSwitchOpen) QuickSwitch(state, actions, notices)

        val panelStart = if (compact) PanelEndMargin else 0.dp
        val panelTop = EdgeMargin + PillHeight + PillGap + PillHeight + PanelGap
        val side = EdgeMargin + PillHeight + PanelGap
        val panelModifier = when (edge) {
            ToolbarEdge.TOP -> Modifier.align(Alignment.TopEnd).padding(top = panelTop, end = PanelEndMargin, bottom = PanelEndMargin)
            ToolbarEdge.BOTTOM -> Modifier.align(Alignment.BottomEnd).padding(start = panelStart, top = if (compact) EdgeMargin + PillHeight + PanelGap else PanelEndMargin, end = PanelEndMargin, bottom = panelTop)
            ToolbarEdge.LEFT -> Modifier.align(Alignment.TopStart).padding(top = side, start = side, bottom = PanelEndMargin)
            ToolbarEdge.RIGHT -> Modifier.align(Alignment.TopEnd).padding(top = side, end = side, bottom = PanelEndMargin)
        }
        if (state.colorTarget != null) {
            ColorWheelPanel(state, panelModifier.masksInk(state, "wheel"))
        } else if (state.customizeOpen) {
            CustomizeToolbarSheet(state, Modifier.align(Alignment.Center).fillMaxHeight(CUSTOMIZE_HEIGHT).masksInk(state, "customize"))
        } else if (state.toolbarSettingsOpen) {
            ToolbarSettingsPanel(state, notices, panelModifier.masksInk(state, "toolbarSettings"))
        } else if (state.clipboardOpen) {
            ClipboardSheet(state, panelModifier.masksInk(state, "clipboard"))
        } else if (state.layersOpen) {
            LayersSheet(state, panelModifier.masksInk(state, "layers"))
        } else if (state.panelOpen) {
            val sheet = panelModifier.masksInk(state, "panel")
            when {
                preset != null -> InkPanel(state, preset, notices, sheet)
                state.activeTool == ActiveTool.ERASER -> EraserPanel(state, state.eraser, notices, sheet)
                state.activeTool == ActiveTool.LASSO -> LassoPanel(state, state.lasso, notices, sheet)
                state.activeTool == ActiveTool.SHAPE -> ShapePanel(state, state.shape, sheet)
                state.activeTool == ActiveTool.TAPE -> TapePanel(state, state.tape, sheet)
                state.activeTool == ActiveTool.STICKY -> NotePanel(state, notices, state.note, sheet, sticky = true)
                state.activeTool == ActiveTool.TEXT -> NotePanel(state, notices, state.note, sheet, sticky = false)
                state.activeTool == ActiveTool.TABLE -> TablePanel(state, state.table, sheet)
                state.activeTool == ActiveTool.IMAGE -> ImagePanel(state, sources, notices, sheet)
                // Stays open while a sticker is dragged out of it: it is the place to drop one sticker on another.
                state.activeTool == ActiveTool.FILL -> FillPanel(state, sheet)
                state.activeTool == ActiveTool.EMOJI -> EmojiPanel(state, sheet.onGloballyPositioned { state.stickerPanelBounds = it.boundsInWindow() })
                state.activeTool == ActiveTool.LASER -> LaserPanel(state, sheet)
            }
        }
        // The sticker being dragged out of the list follows the finger, over the panel.
        val dragged = state.stickerDragId
        val draggedAt = state.stickerDragPoint
        if (dragged != null && draggedAt != null) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((draggedAt.x - 48.dp.toPx()).roundToInt(), (draggedAt.y - 48.dp.toPx()).roundToInt()) }
                    .graphicsLayer { alpha = 0.9f },
            ) { StickerThumb(state, dragged, 96.dp) }
        }
    }
}

@Composable
private fun LeftPill(state: EditorState, actions: EditorActions, notices: Notices, modifier: Modifier) {
    PillBox(modifier) {
        ToolbarIcon(EditorIcons.Back, stringResource(R.string.editor_back), onClick = actions.onBack)
        ToolbarIcon(EditorIcons.Sidebar, stringResource(R.string.editor_sidebar), onClick = { state.sourcesOpen = true })
        ToolbarIcon(EditorIcons.PageGrid, stringResource(R.string.editor_page_grid), onClick = { state.pagesOpen = true })
        ToolbarIcon(EditorIcons.PageStack, stringResource(R.string.editor_page_stack)) {
            state.quickSwitchOpen = true
        }
        // Not built yet, so a phone's narrower row goes without it.
        if (!isCompactScreen()) ToolbarIcon(EditorIcons.ChevronsUpDown, stringResource(R.string.editor_chevrons), onClick = notices::notBuilt)
    }
}

/** One button in the strip that scrolls. A tool without a reference screenshot only says so for now. [key] is what the saved order is made of. */
internal class StripItem(val key: String, val icon: ImageVector, val label: Int, val tool: ActiveTool? = null)

/** In the order of the owner's screenshots, which is where the strip starts out. */
internal val StripItems by lazy {
    listOf(
        StripItem("lasso", EditorIcons.Lasso, R.string.tool_lasso, ActiveTool.LASSO),
        StripItem("pen", EditorIcons.Pen, R.string.tool_pen, ActiveTool.PEN),
        StripItem("pencil", EditorIcons.Brush, R.string.tool_pencil, ActiveTool.PENCIL),
        StripItem("highlighter", EditorIcons.Highlighter, R.string.tool_highlighter, ActiveTool.HIGHLIGHTER),
        StripItem("eraser", EditorIcons.Eraser, R.string.tool_eraser, ActiveTool.ERASER),
        StripItem("tape", EditorIcons.Tape, R.string.tool_tape, ActiveTool.TAPE),
        StripItem("shapes", EditorIcons.Shapes, R.string.tool_shapes, ActiveTool.SHAPE),
        StripItem("image", EditorIcons.Image, R.string.tool_image, ActiveTool.IMAGE),
        StripItem("text", EditorIcons.Text, R.string.tool_text, ActiveTool.TEXT),
        StripItem("sticky", EditorIcons.StickyNote, R.string.tool_sticky, ActiveTool.STICKY),
        StripItem("sticker", EditorIcons.Sticker, R.string.tool_sticker, ActiveTool.EMOJI),
        StripItem("table", EditorIcons.Table, R.string.tool_table, ActiveTool.TABLE),
        StripItem("clipboard", EditorIcons.Tray, R.string.tool_clipboard),
        StripItem("add_element", EditorIcons.AddElement, R.string.tool_add_element),
        StripItem("loupe", EditorIcons.Loupe, R.string.tool_loupe),
        StripItem("ruler", EditorIcons.Ruler, R.string.tool_ruler),
        StripItem("recorder", EditorIcons.Mic, R.string.tool_recorder),
        StripItem("laser", EditorIcons.Laser, R.string.tool_laser, ActiveTool.LASER),
        StripItem("fill", EditorIcons.Bucket, R.string.tool_fill, ActiveTool.FILL),
        StripItem("layers", EditorIcons.Layers, R.string.tool_layers),
        StripItem("settings", EditorIcons.Settings, R.string.tool_settings),
    )
}

/** The tools that cannot be hidden: without the cog there would be no way back to the list. */
internal val ALWAYS_SHOWN_TOOLS = setOf("settings")

/** The saved [layout] cleaned up, with any tool it does not name, such as one added since, after the rest. */
internal fun completeToolbar(layout: ToolbarEntries): ToolbarEntries = ToolbarGroups.complete(layout, StripItems.map { it.key })

@Composable
private fun CenterPill(
    state: EditorState,
    recorder: RecorderState,
    sources: ImageSources,
    actions: EditorActions,
    notices: Notices,
    modifier: Modifier,
) {
    if (LocalToolbarVertical.current) {
        PillColumn(modifier) {
            CenterHead(state)
            PillDivider()
            Column(Modifier.weight(1f, fill = false).heightIn(max = PillHeight * visibleTools(ToolbarEdge.LEFT)).verticalScroll(rememberScrollState())) {
                StripButtons(state, recorder, sources, notices)
            }
            PillDivider()
            CollapseButton(state)
        }
        return
    }
    PillRow(modifier) {
        CenterHead(state)
        PillDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = ToolbarCell * if (isCompactScreen()) COMPACT_VISIBLE_TOOLS else visibleTools(state.toolbarLiveEdge ?: state.toolbarEdge))
                .horizontalScroll(rememberScrollState()),
        ) {
            StripButtons(state, recorder, sources, notices)
        }
        PillDivider()
        CollapseButton(state)
    }
}

@Composable
private fun CenterHead(state: EditorState) {
    ToolbarIcon(EditorIcons.Undo, stringResource(R.string.editor_undo), enabled = state.canUndo, onClick = state::undo)
    ToolbarIcon(EditorIcons.Redo, stringResource(R.string.editor_redo), enabled = state.canRedo, onClick = state::redo)
    ToolbarIcon(
        // The icon shows the mode in use, so there is no highlight: a hand with arrows scrolls, a hand drawing a line draws.
        if (state.fingerDraws) EditorIcons.FingerDraw else EditorIcons.FingerMode,
        stringResource(R.string.editor_finger_mode),
        onClick = state::toggleFingerDraw,
    )
}

@Composable
private fun CollapseButton(state: EditorState) {
    // The arrow points the way the second toolbar goes: toward the screen's edge to fold it away, and back toward the middle to bring it out.
    val edge = state.toolbarLiveEdge ?: state.toolbarEdge
    val icon = when {
        !LocalToolbarVertical.current -> if (state.toolRowCollapsed) EditorIcons.ChevronDown else EditorIcons.ChevronUp
        (edge == ToolbarEdge.LEFT) != state.toolRowCollapsed -> EditorIcons.ChevronLeft
        else -> EditorIcons.ChevronRight
    }
    ToolbarIcon(
        icon,
        stringResource(R.string.editor_collapse),
        onClick = { state.toolRowCollapsed = !state.toolRowCollapsed },
    )
}

@Composable
private fun StripButtons(state: EditorState, recorder: RecorderState, sources: ImageSources, notices: Notices) {
    val byKey = StripItems.associateBy { it.key }
    val hidden = state.hiddenTools.mapNotNull { byKey[it] }
    for (entry in state.toolbarLayout) {
        // With tools hidden, an eye stands before the cog and shows them.
        if (hidden.isNotEmpty() && entry.any { it in ALWAYS_SHOWN_TOOLS }) HiddenToolsButton(hidden, state, recorder, sources, notices)
        val shown = entry.mapNotNull { byKey[it] }.filter { item ->
            item.key !in state.hiddenTools || (item.label == R.string.tool_recorder && recorder.recording)
        }
        when {
            shown.isEmpty() -> Unit
            shown.size == 1 -> StripButton(shown[0], state, recorder, sources, notices)
            else -> GroupButton(shown, state, recorder, sources, notices)
        }
    }
}

/** The eye before the cog: it opens a list of the tools that are hidden from the strip, to be used from there. */
@Composable
private fun HiddenToolsButton(hidden: List<StripItem>, state: EditorState, recorder: RecorderState, sources: ImageSources, notices: Notices) {
    val colors = LocalEditorColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIcon(EditorIcons.HiddenTools, stringResource(R.string.toolbar_hidden_tools), selected = open) { open = !open }
        if (open) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { (PillHeight + 8.dp).roundToPx() }),
                onDismissRequest = { open = false },
            ) {
                Column(
                    Modifier
                        .width(220.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp), clip = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.panel)
                        .padding(vertical = 8.dp),
                ) {
                    for (item in hidden) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(role = Role.Button) {
                                    open = false
                                    stripClick(item, state, recorder, sources, notices)
                                }
                                .padding(horizontal = 16.dp),
                        ) {
                            Icon(item.icon, null, tint = colors.icon, modifier = Modifier.size(22.dp))
                            Text(stringResource(item.label), color = colors.title, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun stripSelected(item: StripItem, state: EditorState) = when (item.label) {
    R.string.tool_ruler -> state.rulerOn
    R.string.tool_loupe -> state.loupeOpen
    R.string.tool_layers -> state.layersOpen
    else -> item.tool == state.shownTool
}

private fun stripClick(item: StripItem, state: EditorState, recorder: RecorderState, sources: ImageSources, notices: Notices) {
    val tool = item.tool
    when {
        tool != null -> state.select(tool)
        item.label == R.string.tool_ruler -> state.toggleRuler()
        item.label == R.string.tool_clipboard -> state.openClipboard()
        item.label == R.string.tool_layers -> state.toggleLayers()
        item.label == R.string.tool_loupe -> state.toggleLoupe()
        item.label == R.string.tool_settings -> {
            state.panelOpen = false
            state.clipboardOpen = false
            state.layersOpen = false
            state.toolbarSettingsOpen = !state.toolbarSettingsOpen
        }
        item.label == R.string.tool_add_element -> sources.pickVideo()
        item.label == R.string.tool_recorder -> recorder.menuOpen = !recorder.menuOpen
        else -> notices.notBuilt()
    }
}

@Composable
private fun StripButton(item: StripItem, state: EditorState, recorder: RecorderState, sources: ImageSources, notices: Notices) {
    if (item.label == R.string.tool_recorder && recorder.recording) {
        StopRecordingButton(recorder)
        return
    }
    ToolbarIcon(
        item.icon,
        stringResource(item.label),
        selected = stripSelected(item, state),
        onClick = { stripClick(item, state, recorder, sources, notices) },
    )
}

/**
 * Tools grouped into one button of the strip, shown as the first three of them in a small stack, as simple solid shapes. A tap opens the
 * list of the group's tools to choose from.
 */
@Composable
private fun GroupButton(items: List<StripItem>, state: EditorState, recorder: RecorderState, sources: ImageSources, notices: Notices) {
    val colors = LocalEditorColors.current
    var open by remember { mutableStateOf(false) }
    val selected = items.any { stripSelected(it, state) }
    val tint = if (selected) colors.toolSelectedIcon else colors.icon
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ToolbarCell, PillHeight)
                .tappable(radius = 20.dp) { open = !open },
        ) {
            if (selected) Box(Modifier.size(42.dp).background(colors.toolSelected, CircleShape))
            val shown = items.take(3)
            // A triangle: the first on top, the second and third below it, side by side.
            val spots = listOf(0.dp to (-8).dp, (-7).dp to 6.dp, 7.dp to 6.dp)
            for ((i, item) in shown.withIndex()) {
                Icon(
                    EditorIcons.mini(item.key),
                    null,
                    tint = tint,
                    modifier = Modifier.offset(spots[i].first, spots[i].second).size(if (shown.size == 2 && i == 0) 14.dp else 15.dp),
                )
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (item in items) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(item.label)) },
                    leadingIcon = { Icon(item.icon, null, modifier = Modifier.size(22.dp)) },
                    onClick = {
                        open = false
                        stripClick(item, state, recorder, sources, notices)
                    },
                )
            }
        }
    }
}

@Composable
private fun RightPill(state: EditorState, recorder: RecorderState, actions: EditorActions, modifier: Modifier) {
    val colors = LocalEditorColors.current
    PillBox(modifier) {
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
            .padding(start = 9.dp, bottom = bandBottom(state, isCompactScreen()))
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
            // The cover is page 0, so a notebook with one still counts its pages from 1.
            Text("${state.currentPage + 1 - state.coverOffset}", color = colors.title, fontSize = 12.sp, lineHeight = 14.sp)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.pillDivider))
            Text("${state.pageCount - state.coverOffset}", color = colors.label, fontSize = 12.sp, lineHeight = 14.sp)
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
    if (map != null) MinimapView(map, Modifier.align(Alignment.BottomEnd).padding(end = SIDE_ITEM_MARGIN, bottom = bandBottom(state, isCompactScreen())).masksInk(state, "minimap"))
}

/** A small map of an infinite page, shown while it scrolls: where things are written, and what is in view. */
@Composable
private fun MinimapView(map: CanvasView.Minimap, modifier: Modifier) {
    val colors = LocalEditorColors.current
    Canvas(
        modifier
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

private val SIDE_ITEM_MARGIN = 12.dp

/**
 * How far above the screen's bottom the page index, the map and the rotation badge sit. Level with the second toolbar when the toolbars are
 * at the bottom, so that they stand at the sides of it, and low down otherwise.
 */
private fun bandBottom(state: EditorState, compact: Boolean) = when {
    // The two bars take the width of a phone, so these stand above them.
    compact -> EdgeMargin + PillHeight * 2f + PillGap * 2f
    state.toolbarEdge == ToolbarEdge.BOTTOM -> EdgeMargin + PillHeight + PillGap
    else -> SIDE_ITEM_MARGIN
}

/** What the page index and the map or rotation badge take at each side of the second toolbar when it is at the bottom. */
private val SideItemReserve = 172.dp

/** How many tools the strip shows before it scrolls: ten on an upright strip, and fewer along the top and bottom, where the pills share the row. */
private fun visibleTools(edge: ToolbarEdge) = when (edge) {
    ToolbarEdge.TOP -> 8
    ToolbarEdge.BOTTOM -> 9
    else -> 10
}
/** On a phone the strip shows this many tools before it scrolls. */
private const val COMPACT_VISIBLE_TOOLS = 5
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
        ToolStrip {
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
                ColorEditor(
                    draft,
                    setDraft = { state.colorDraft = it },
                    accept = state::acceptColorDraft,
                    discard = { state.colorDraft = null },
                )
            } else {
                preset.colors.forEachIndexed { slot, color ->
                    ToolbarCellBox(onClick = { state.update { it.copy(selectedColor = slot) } }) {
                        ColorDot(color)
                        if (slot == preset.selectedColor) SelectedDot()
                    }
                }
                Box {
                    ToolbarIcon(EditorIcons.AddColor, stringResource(R.string.editor_add_color)) { state.moreColorsOpen = true }
                    if (state.moreColorsOpen) {
                        FavoriteColors(
                            favorites = (preset.palette + MORE_FAVORITES).distinct(),
                            onBar = preset.colors,
                            onPick = { color -> state.update { it.withColorAdded(color) } },
                            onWheel = { state.colorTarget = ColorTarget.CUSTOM_SLOT },
                            onDismiss = { state.moreColorsOpen = false },
                        )
                    }
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
internal fun FavoriteColors(
    favorites: List<Int>,
    onBar: List<Int>,
    onPick: (Int) -> Unit,
    onWheel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalEditorColors.current
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
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
                                onPick(color)
                                onDismiss()
                            },
                        ) {
                            ColorDot(color, diameter = 28.dp)
                            if (color in onBar) SelectedDot()
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
                            onDismiss()
                            onWheel()
                        },
                )
            }
        }
    }
}

private const val MORE_COLORS_COLUMNS = 6

internal val MORE_FAVORITES = listOf(
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
internal fun ColorEditor(draft: List<Int>, setDraft: (List<Int>) -> Unit, accept: () -> Unit, discard: () -> Unit) {
    val latest by androidx.compose.runtime.rememberUpdatedState(draft)
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
                        val current = latest
                        val at = current.indexOf(color)
                        val target = (at + if (dragged > step / 2f) 1 else if (dragged < -step / 2f) -1 else 0).coerceIn(0, current.lastIndex)
                        if (target != at) {
                            val moved = current.toMutableList().apply { add(target, removeAt(at)) }
                            setDraft(moved)
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
                        if (draft.size > 1) setDraft(draft - color)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp, 2.dp).background(Color.White))
            }
        }
    }
    PillDivider()
    ToolbarIcon(EditorIcons.Close, stringResource(R.string.editor_colors_discard)) { discard() }
    ToolbarIcon(EditorIcons.Check, stringResource(R.string.editor_colors_accept)) { accept() }
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
    val shown = state.currentPage + 1 - state.coverOffset
    val last = state.pageCount - state.coverOffset
    var text by remember { mutableStateOf(TextFieldValue("$shown", TextRange(0, "$shown".length))) }
    val focus = remember { FocusRequester() }
    val go = {
        text.text.toIntOrNull()?.let { state.goToPageNumber(it.coerceIn(1 - state.coverOffset, last.coerceAtLeast(1))) }
        close()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = close) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .opensFrom(OpenFrom.Center)
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
            Text("/ $last", color = colors.label, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp, end = 14.dp))
            Text("Go", color = colors.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(role = Role.Button) { go() }.padding(8.dp))
        }
    }
}

/** The strip picked up: how long it takes, how much smaller it gets, and how high its shadow. */
private const val LIFT_MS = 120

/** How long the strip takes to slide into its resting place once let go. */
private const val SETTLE_MS = 80

/** How much of the screen's height the Customize toolbar menu takes. */
private const val CUSTOMIZE_HEIGHT = 0.92f
private const val LIFT_SHRINK = 0.08f
private const val LIFT_SHADOW_DP = 12f
