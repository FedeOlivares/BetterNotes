package app.betternotes.ui.editor

import android.content.ClipboardManager
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.betternotes.R
import app.betternotes.stickers.StickerStore
import app.betternotes.canvas.CanvasTool
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.LaserMode
import app.betternotes.canvas.TextEditRequest
import app.betternotes.canvas.VideoPlayRequest
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.model.TextItem
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.withLocked
import app.betternotes.core.model.TextStyle
import app.betternotes.core.tools.ALL_ITEM_KINDS
import app.betternotes.core.tools.EraserOptions
import app.betternotes.core.tools.ImageOptions
import app.betternotes.core.tools.InkPreset
import app.betternotes.core.tools.ToolbarGroups
import app.betternotes.core.tools.LassoOptions
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.canvas.TapeLook
import app.betternotes.core.clipboard.ClipEntry
import app.betternotes.core.clipboard.ImageClip
import app.betternotes.core.clipboard.ItemsClip
import app.betternotes.core.clipboard.TextClip
import app.betternotes.core.tools.TapeOptions
import kotlin.math.roundToInt

/** The tools the strip can put in the pen's hand so far. */
internal enum class ActiveTool(val ink: InkTool?) {
    PEN(InkTool.PEN),
    PENCIL(InkTool.PENCIL),
    HIGHLIGHTER(InkTool.HIGHLIGHTER),
    ERASER(null),
    LASER(null),
    LASSO(null),
    SHAPE(null),
    STICKY(null),
    TEXT(null),
    TABLE(null),
    FILL(null),
    IMAGE(null),
    TAPE(null),
    EMOJI(null),
}

private val ActiveTool.hasPanel get() = true

/** What the color wheel's result is for. */
internal enum class ColorTarget { TAPE, CUSTOM_SLOT, SELECTED_SLOT, PALETTE, SHAPE_BORDER, SHAPE_FILL, NOTE, TABLE_BORDER, TABLE_FILL, LASER, TOOLBAR, SELECTED_NOTE, FILL }

/** What the toolbars show, and the one place that passes their choices on to the canvas. */
@Stable
internal class EditorState(private val canvas: CanvasView, private val store: ToolStore) {
    private val presets = mutableStateMapOf<InkTool, InkPreset>().apply {
        for (tool in InkTool.entries) put(tool, store.preset(tool))
    }
    private val overlays = HashMap<String, RectF>()
    private var drawingMode = InputMode.STYLUS_DRAW

    var activeTool by mutableStateOf(ActiveTool.PEN)
        private set
    var eraser by mutableStateOf(store.eraser())
        private set
    var lasso by mutableStateOf(store.lasso())
        private set
    var shape by mutableStateOf(store.shape())
        private set
    var note by mutableStateOf(store.note())
        private set
    var table by mutableStateOf(store.table())
        private set
    var textEdit by mutableStateOf<TextEditRequest?>(null)
        private set
    var image by mutableStateOf(store.image())
        private set
    var loupeOpen by mutableStateOf(false)
        private set

    /** The loupe window's height as a share of the screen, which its handle changes. */
    var loupeShare by mutableFloatStateOf(LOUPE_DEFAULT_SHARE)

    /** True while the window shrinks away after being pulled too small. */
    var loupeClosing by mutableStateOf(false)

    /** Closes the loupe after it has shrunk away, and gives it its first size for the next time. */
    fun closeLoupe() {
        if (loupeOpen) toggleLoupe()
        loupeClosing = false
        loupeShare = LOUPE_DEFAULT_SHARE
    }
    var tape by mutableStateOf(store.tape())
        private set
    var emoji by mutableStateOf(EMOJI.first())
        private set
    var recentEmoji by mutableStateOf(EMOJI.take(RECENT_EMOJI))
        private set

    /** The stickers saved from selections, newest first, and whether the sticker tool shows them instead of the emoji. */
    val stickerStore = StickerStore(canvas.context)
    var stickers by mutableStateOf(stickerStore.list())
        private set
    var stickerTab by mutableStateOf(true)
    var sticker by mutableStateOf<Long?>(null)
        private set
    private var stickerItems: List<PageItem>? = null
    private val stickerWork = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Saves the selected items as a sticker, off the main thread, and says so when it is done. */
    fun saveAsSticker(items: List<PageItem>) {
        val files = { assetId: String -> canvas.assetFile(assetId) ?: java.io.File("") }
        stickerWork.execute {
            val saved = stickerStore.save(items, files)
            canvas.post {
                stickers = stickerStore.list()
                packs = stickers.associateWith { stickerStore.packOf(it) }
                say(if (saved != null) R.string.notice_sticker_saved else R.string.notice_sticker_failed)
            }
        }
    }

    /** Imports the pictures of a folder the owner picked as a pack of stickers, and says how many it added. */
    fun importStickerPack(tree: Uri) {
        val resolver = canvas.context.contentResolver
        stickerWork.execute {
            val result = stickerStore.importPack(resolver, tree)
            canvas.post {
                stickers = stickerStore.list()
                packs = stickers.associateWith { stickerStore.packOf(it) }
                when {
                    result == null -> say(R.string.notice_pack_failed)
                    result.second == 0 -> say(R.string.notice_pack_empty)
                    else -> {
                        stickerTab = true
                        say(R.string.notice_pack_added)
                    }
                }
            }
        }
    }

    /** The pack each sticker came from, if any, for the grid's headings. */
    var packs by mutableStateOf(stickers.associateWith { stickerStore.packOf(it) })
        private set

    fun chooseSticker(id: Long) {
        val items = stickerStore.read(id) ?: return
        sticker = id
        stickerItems = items
        stickerTab = true
        applyTool()
    }

    fun deleteSticker(id: Long) {
        stickerWork.execute {
            stickerStore.delete(id)
            canvas.post {
                stickers = stickerStore.list()
                if (sticker == id) {
                    sticker = null
                    stickerItems = null
                    applyTool()
                }
            }
        }
    }

    fun showStickerTab(on: Boolean) {
        stickerTab = on
        applyTool()
    }
    var emojiSize by mutableIntStateOf(DEFAULT_EMOJI)
        private set
    var playingVideo by mutableStateOf<VideoPlayRequest?>(null)
    var pagesOpen by mutableStateOf(false)
    var colorListOpen by mutableStateOf(false)
    var sourcesOpen by mutableStateOf(false)
    var sourcesAddOpen by mutableStateOf(false)

    /** Adds a source to the binder. Set by the activity, which owns the notebooks. */
    var onAddSource: ((SourceRequest) -> Unit)? = null

    /** Bumped when the binder changes, so its list is read again. */
    var sourcesVersion by mutableIntStateOf(0)

    /** The picker for a notebook to link is open. */
    var notebookPickerOpen by mutableStateOf(false)

    /** A photo taken in scan mode, waiting for its corners to be set. */
    var scanPhoto by mutableStateOf<Uri?>(null)
    var moreMenuOpen by mutableStateOf(false)

    /** How far the page is turned on screen, in degrees clockwise, and whether two fingers are turning it now. */
    var rotationDegrees by mutableFloatStateOf(0f)
        private set
    var rotating by mutableStateOf(false)
        private set

    /** Set by a tap where the indicator sits, which brings it up for a few seconds. */
    var rotationBadgeShown by mutableStateOf(false)

    /** How long the indicator stays before it fades: a second after the fingers lift, longer after a tap. */
    var rotationBadgeHoldMs = 0L
        private set

    fun resetRotation() {
        canvas.resetRotation()
        rotationBadgeShown = false
    }

    /** The list of what was copied lately, opened by the tray button. */
    var clipboardOpen by mutableStateOf(false)
    val clipboard = ClipboardTracker(canvas.context)

    /** The toolbar settings, opened by the cog after the laser. */
    var toolbarSettingsOpen by mutableStateOf(false)

    /** The Customize toolbar menu, reached from the toolbar settings: its position and the order of its tools. */
    var customizeOpen by mutableStateOf(false)

    /** The color of the toolbars, or null for the theme's own. */
    var toolbarColor by mutableStateOf(store.toolbarColor())
        private set

    /** The screen edge the toolbars sit on, whether a long press may move them, and how far they are dragged now. */
    var toolbarEdge by mutableStateOf(store.toolbarEdge())
        private set
    var moveToolbar by mutableStateOf(store.moveToolbar())
        private set
    /** While the toolbar is held and dragged: where the finger is on the screen, and the edge the toolbar has turned to for it. */
    var toolbarDragPoint by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var toolbarLiveEdge by mutableStateOf<ToolbarEdge?>(null)

    /** The guide lines over the page. */
    var grid by mutableStateOf(store.grid())
        private set

    fun chooseToolbarEdge(edge: ToolbarEdge) {
        toolbarEdge = edge
        store.saveToolbarEdge(edge)
    }

    /** The strip's tools as the owner arranged them, groups and all, and the ones hidden. */
    var toolbarLayout by mutableStateOf(completeToolbar(store.toolLayout()))
        private set
    var hiddenTools by mutableStateOf(store.hiddenTools())
        private set

    /** Moves the tool [key] to where [drop] says, which can make it part of a group or take it out of one. */
    fun moveToolbarTool(key: String, drop: ToolbarGroups.Drop) {
        toolbarLayout = ToolbarGroups.move(toolbarLayout, key, drop)
        store.saveToolLayout(toolbarLayout)
    }

    /** Puts the tools back as they came: in their own order, none in a group, none hidden. */
    fun resetToolbarOrganization() {
        toolbarLayout = completeToolbar(emptyList())
        hiddenTools = emptySet()
        store.saveToolLayout(toolbarLayout)
        store.saveHiddenTools(hiddenTools)
    }

    fun toggleToolHidden(key: String) {
        if (key in ALWAYS_SHOWN_TOOLS) return
        hiddenTools = if (key in hiddenTools) hiddenTools - key else hiddenTools + key
        store.saveHiddenTools(hiddenTools)
    }

    fun chooseMoveToolbar(on: Boolean) {
        moveToolbar = on
        store.saveMoveToolbar(on)
    }

    fun updateGrid(change: (GridOptions) -> GridOptions) {
        grid = change(grid)
        store.saveGrid(grid)
        canvas.setGrid(grid.on && grid.cross, grid.on && grid.thirds)
    }

    /** The paint bucket's color and how see-through it is. */
    var fillColor by mutableIntStateOf(store.fillColor())
        private set
    var fillOpacity by mutableFloatStateOf(store.fillOpacity())
        private set

    /** The colors on the bucket's bar, which the owner can rearrange, remove from, add to and sample from the page. */
    var fillColors by mutableStateOf(store.fillColors())
        private set
    var fillDraft by mutableStateOf<List<Int>?>(null)

    /** True while the next touch on the page takes the color under it for the bucket. */
    var pickingColor by mutableStateOf(false)
        private set

    fun acceptFillDraft() {
        val draft = fillDraft
        fillDraft = null
        if (draft != null && draft.isNotEmpty()) {
            fillColors = draft
            store.saveFillColors(draft)
            if (fillColor !in draft) chooseFillColor(draft.first())
        }
    }

    /** Puts a color on the bar, unless it is there, and chooses it. */
    fun addFillColor(color: Int) {
        val opaque = color or OPAQUE
        if (opaque !in fillColors) {
            fillColors = fillColors + opaque
            store.saveFillColors(fillColors)
        }
        chooseFillColor(opaque)
    }

    /** The next touch on the page picks the color under it. */
    fun startPickingColor() {
        pickingColor = true
        say(R.string.notice_pick_color)
        applyTool()
    }

    private fun stopPickingColor() {
        pickingColor = false
        applyTool()
    }

    private fun pickColorAt(x: Float, y: Float) {
        val activity = generateSequence(canvas.context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
        if (activity == null) {
            stopPickingColor()
            return
        }
        val at = IntArray(2)
        canvas.getLocationInWindow(at)
        val left = (at[0] + x).toInt().coerceAtLeast(0)
        val top = (at[1] + y).toInt().coerceAtLeast(0)
        val pixel = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        android.view.PixelCopy.request(
            activity.window,
            android.graphics.Rect(left, top, left + 1, top + 1),
            pixel,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) addFillColor(pixel.getPixel(0, 0))
                stopPickingColor()
            },
            canvas.handler ?: android.os.Handler(android.os.Looper.getMainLooper()),
        )
    }

    /** Whether the paint bucket gives the tool in use before back once it has been left alone for a while, and that while in tenths of a second. */
    var fillReturn by mutableStateOf(store.fillReturn())
        private set
    var fillReturnDelay by mutableIntStateOf(store.fillReturnDelay())
        private set

    private val fillReturnTimer = Runnable { if (activeTool == ActiveTool.FILL && fillReturn) returnToPreviousTool() }

    fun chooseFillReturn(on: Boolean) {
        fillReturn = on
        store.saveFillReturn(fillReturn, fillReturnDelay)
        if (!on) canvas.removeCallbacks(fillReturnTimer)
    }

    fun chooseFillReturnDelay(tenths: Int) {
        fillReturnDelay = tenths.coerceIn(MIN_FILL_RETURN_TENTHS, MAX_FILL_RETURN_TENTHS)
        store.saveFillReturn(fillReturn, fillReturnDelay)
    }

    /** A fill was made: the delay starts again, so that using the bucket again keeps it in hand. */
    private fun fillUsed() {
        canvas.removeCallbacks(fillReturnTimer)
        if (fillReturn) canvas.postDelayed(fillReturnTimer, fillReturnDelay * 100L)
    }

    fun chooseFillColor(color: Int) {
        fillColor = color or OPAQUE
        store.saveFill(fillColor, fillOpacity)
        applyTool()
    }

    fun chooseFillOpacity(opacity: Float) {
        fillOpacity = opacity.coerceIn(MIN_FILL_OPACITY, 1f)
        store.saveFill(fillColor, fillOpacity)
        applyTool()
    }

    var quickSwitchOpen by mutableStateOf(false)

    /** Bumped by every edit, so lists drawn from the document know to look again. */
    var historyVersion by mutableIntStateOf(0)
        private set

    /** Says something short to the user. Set by the controls, which have the means to. */
    var say: (Int) -> Unit = {}
    var panelOpen by mutableStateOf(false)
    var toolRowCollapsed by mutableStateOf(false)
    var colorTarget by mutableStateOf<ColorTarget?>(null)
    var viewOnly by mutableStateOf(false)
        private set
    var fingerDraws by mutableStateOf(false)
        private set
    var rulerOn by mutableStateOf(false)
        private set
    var laserWidthSlot by mutableIntStateOf(1)
    var laserMode by mutableStateOf(LaserMode.LINE)
        private set
    var laserLength by mutableIntStateOf(canvas.laserLength)
        private set
    var laserColor by mutableIntStateOf(canvas.laserColor)
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var pageCount by mutableIntStateOf(0)
        private set

    /** 1 when the first page is the cover, which is numbered 0, so that the other pages count from 1 as before. */
    var coverOffset by mutableIntStateOf(0)
        private set

    /** The cover chooser is open. */
    var coverOpen by mutableStateOf(false)

    /** The planner's weekday list or years chooser, while one is open. */
    var plannerMenu by mutableStateOf<PlannerMenu?>(null)

    /** The dates of the open planner, or null when it is not one. */
    val plannerSetup get() = canvas.plannerSetup

    val plannerTitle get() = canvas.plannerTitle

    val plannerSemesters get() = canvas.plannerSemesters

    fun setSemesterDate(semester: Int, end: Boolean, epochDay: Int?) = canvas.setSemesterDate(semester, end, epochDay)

    fun setPlannerTitle(title: String) = canvas.setPlannerTitle(title)

    fun setPlannerSetup(setup: app.betternotes.core.planner.PlannerSetup, stayOn: app.betternotes.core.planner.PlannerPage?) =
        canvas.setPlannerSetup(setup, stayOn)

    /** The cover's design id, or null when the notebook has none. Read when the chooser opens. */
    val coverId get() = canvas.pages.firstOrNull()?.cover

    /** Whether a cover can be added: not on an infinite canvas or a typed notebook. */
    val canHaveCover get() = canvas.canHaveCover

    /** The open notebook's template language, "en" or "es". */
    val templateLanguage get() = app.betternotes.ui.theme.TemplateText.code()

    fun setTemplateLanguage(code: String) = canvas.setTemplateLanguage(code)

    /** An infinite canvas: no edges, so no cover. */
    val isInfinite get() = canvas.pages.firstOrNull()?.size == null

    /** Whether the cover is, or a new cover would be, wider than tall: the first page's orientation. */
    val coverLandscape get() = canvas.pages.firstOrNull()?.size?.let { it.widthPt > it.heightPt } == true

    fun setCover(id: String?, landscape: Boolean? = null, editable: Boolean = false, recolor: Map<Int, Int> = emptyMap()) =
        canvas.setCover(id, landscape, editable, recolor)

    var zoom by mutableFloatStateOf(1f)
        private set

    /** Bumped each time the page moves, which shows the indicator for a while. */
    /** See [CanvasView.scrollFraction]. */
    var scrollFraction by mutableStateOf<Float?>(null)

    var viewMoves by mutableIntStateOf(0)
        private set
    /** Whether the "..." menu of the selection is open, which holds lock, translucency and a sticky note's options. */
    var selectionMenuOpen by mutableStateOf(false)

    private fun closeSelectionMenu() = setSelectionMenu(false)

    private fun setSelectionMenu(open: Boolean) {
        selectionMenuOpen = open
        canvas.selectionMenuOpen = open
    }

    /** Where the options bar goes, as the right edge and top of its place on the screen, or null. */
    fun selectionMenuAnchor() = canvas.selectionMenuAnchor()

    /** What the lasso has selected, or null. Locked items can be selected but not moved. */
    var selection by mutableStateOf<List<PageItem>?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** The settings of the ink tool in use, or null if the tool in use lays no ink. */
    val preset: InkPreset? get() = activeTool.ink?.let { presets[it] }

    /** The settings of [tool], for a row that may be showing a tool other than the one in use while it fades. */
    fun presetFor(tool: ActiveTool): InkPreset? = tool.ink?.let { presets[it] }

    val screenDpi get() = canvas.viewport.screenDpi

    val canvasForRecorder get() = canvas

    init {
        canvas.onHistoryChanged = {
            canUndo = canvas.canUndo
            canRedo = canvas.canRedo
            historyVersion++
            pageCount = canvas.pageCount
            coverOffset = if (coverId != null) 1 else 0
        }
        canUndo = canvas.canUndo
        canRedo = canvas.canRedo
        canvas.onLassoRequested = {
            if (activeTool != ActiveTool.IMAGE && activeTool != ActiveTool.LASSO) {
                toolBeforeLasso = activeTool
                lendingTool = true
                select(ActiveTool.LASSO)
                lendingTool = false
            }
        }
        // The lasso that a circle or a placed item asked for is lent: it goes back once the selection is let go.
        canvas.onTextToolRequested = { if (activeTool != ActiveTool.TEXT) select(ActiveTool.TEXT) }
        canvas.onSelectionDropped = {
            val back = toolBeforeLasso
            toolBeforeLasso = null
            if (back != null && activeTool == ActiveTool.LASSO) {
                lendingTool = true
                select(back)
                lendingTool = false
            }
        }
        canvas.onRotationChanged = { degrees, turning ->
            rotationDegrees = degrees
            if (rotating && !turning) {
                rotationBadgeHoldMs = AFTER_GESTURE_MS
                rotationBadgeShown = true
            }
            rotating = turning
        }
        canvas.onPlannerAction = { action, page ->
            when (action) {
                is app.betternotes.core.planner.PlannerAction.PickWeekday -> plannerMenu = PlannerMenu.Weekday(action.dayIndex, page)
                app.betternotes.core.planner.PlannerAction.PickYears -> plannerMenu = PlannerMenu.Years
                app.betternotes.core.planner.PlannerAction.EditTitle -> plannerMenu = PlannerMenu.Title
                is app.betternotes.core.planner.PlannerAction.PickDate -> plannerMenu = PlannerMenu.Date(action.semester, action.end)
                is app.betternotes.core.planner.PlannerAction.Go -> Unit
            }
        }
        canvas.onRotationBadgeTapped = {
            rotationBadgeHoldMs = AFTER_TAP_MS
            rotationBadgeShown = true
        }
        rotationDegrees = canvas.rotationDegrees
        canvas.onSelectionChanged = {
            selection = it
            // An edit clears the selection for a moment and puts it back, so wait to see whether it is really gone.
            if (it == null) canvas.post { if (!canvas.hasSelection) closeSelectionMenu() }
        }
        canvas.onSelectionMenu = { setSelectionMenu(!selectionMenuOpen) }
        canvas.onItemsCopied = clipboard::remember
        canvas.setGrid(grid.on && grid.cross, grid.on && grid.thirds)
        canvas.onSaveSticker = ::saveAsSticker
        canvas.onFillFailed = { say(R.string.notice_fill_open) }
        canvas.onWritingModelsMissing = { say(R.string.notice_writing_models) }
        canvas.onReturnTool = ::returnToPreviousTool
        canvas.onFillDone = ::fillUsed
        canvas.onTextEdit = { request ->
            textEdit = request
            // Opening a text that was written before shows its own look in the toolbar, so its buttons change that text.
            if (request != null && activeTool == ActiveTool.TEXT) {
                note = note.copy(style = request.style, textSize = request.sizePt.roundToInt().coerceIn(NoteOptions.MIN_TEXT, NoteOptions.MAX_TEXT))
            }
        }
        canvas.onPlayVideo = { playingVideo = it }
        canvas.onViewMoved = ::refreshPosition
        canvas.onZoomChanged = { refreshPosition() }
        refreshPosition()
        applyTool()
    }

    /** The tool that was in use when the lasso was brought up by a circle or a placed item. */
    private var toolBeforeLasso: ActiveTool? = null

    /** The tool in use before the one in use now, which a note or sticker made by holding and dragging gives back. */
    private var previousTool = ActiveTool.PEN

    /** True while a tool is lent for a moment, as the lasso is to move a sticker just put down: it is not the tool to come back to. */
    private var lendingTool = false

    fun returnToPreviousTool() {
        val back = previousTool
        panelOpen = false
        if (back != activeTool) select(back)
    }

    /** A sticker being dragged out of the list: which one, and where the finger is in the window. */
    var stickerDragId by mutableStateOf<Long?>(null)
        private set
    var stickerDragPoint by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)

    fun beginStickerDrag(id: Long, at: androidx.compose.ui.geometry.Offset) {
        stickerDragId = id
        stickerDragPoint = at
    }

    /** Lets go of the sticker over the page: it goes there at its own size, and the tool in use before comes back. */
    fun dropStickerDrag() {
        val id = stickerDragId
        val at = stickerDragPoint
        stickerDragId = null
        stickerDragPoint = null
        if (id == null || at == null) return
        val items = stickerStore.read(id) ?: return
        val where = IntArray(2)
        canvas.getLocationInWindow(where)
        val placed = canvas.dropSticker(at.x - where[0], at.y - where[1], CanvasTool.Sticker(items) { assetId -> stickerStore.assetFile(id, assetId) })
        if (placed) returnToPreviousTool()
    }

    fun cancelStickerDrag() {
        stickerDragId = null
        stickerDragPoint = null
    }

    fun select(tool: ActiveTool) {
        if (tool != ActiveTool.LASSO) toolBeforeLasso = null
        canvas.removeCallbacks(fillReturnTimer)
        if (tool != activeTool && !lendingTool) previousTool = activeTool
        if (tool == ActiveTool.EMOJI && stickerTab && sticker == null) stickers.firstOrNull()?.let { chooseSticker(it) }
        if (tool == activeTool) {
            if (tool.hasPanel) panelOpen = !panelOpen
            return
        }
        canvas.endTextEdit()
        toolbarSettingsOpen = false
        clipboardOpen = false
        activeTool = tool
        colorTarget = null
        if (!tool.hasPanel) panelOpen = false
        applyTool()
    }

    /** Changes the ink tool in use. Saving is left to [saveTools], so a dragged slider stays cheap. */
    fun update(change: (InkPreset) -> InkPreset) {
        val tool = activeTool.ink ?: return
        presets[tool] = change(presets.getValue(tool))
        applyTool()
    }

    fun updateEraser(change: (EraserOptions) -> EraserOptions) {
        eraser = change(eraser)
        applyTool()
    }

    fun updateLasso(change: (LassoOptions) -> LassoOptions) {
        lasso = change(lasso)
        applyTool()
    }

    fun updateShape(change: (ShapeOptions) -> ShapeOptions) {
        shape = change(shape)
        applyTool()
    }

    /** A fill of none opens the wheel to choose one, and an existing fill goes back to none. */
    fun tapShapeFill() {
        if (shape.fillColor ushr ALPHA_SHIFT != 0) {
            updateShape { it.copy(fillColor = 0) }
        } else {
            colorTarget = ColorTarget.SHAPE_FILL
        }
    }

    fun updateNote(change: (NoteOptions) -> NoteOptions) {
        note = change(note)
        applyTool()
        // A text box that is open takes the new size and style at once, and keeps them when it ends.
        if (activeTool == ActiveTool.TEXT) canvas.restyleTextEdit(note.style, note.textSize.toFloat())
    }

    /** Changes the selected text box, as one step that can be undone. */
    fun changeSelectedText(change: (TextItem) -> TextItem) = canvas.changeSelection { if (it is TextItem) change(it) else it }

    fun updateNoteStyle(change: (TextStyle) -> TextStyle) = updateNote { it.copy(style = change(it.style)) }

    fun updateTable(change: (TableOptions) -> TableOptions) {
        table = change(table)
        applyTool()
    }

    fun tapTableFill() {
        if (table.fillColor ushr ALPHA_SHIFT != 0) {
            updateTable { it.copy(fillColor = 0) }
        } else {
            colorTarget = ColorTarget.TABLE_FILL
        }
    }

    fun updateImage(change: (ImageOptions) -> ImageOptions) {
        image = change(image)
    }

    /** The bar's colors while they are being edited, or null when they are not. Accepting it keeps them; discarding drops it. */
    var colorDraft by mutableStateOf<List<Int>?>(null)

    /** Whether the menu of more favorite colors is open. */
    var moreColorsOpen by mutableStateOf(false)

    fun acceptColorDraft() {
        val draft = colorDraft
        colorDraft = null
        if (draft != null) update { it.withColorList(draft) }
    }

    fun setSelectionLocked(locked: Boolean) = canvas.changeSelection { it.withLocked(locked) }

    /** Moves the selection to the front, middle or back. Post-its keep to the bottom, except on the cover. */
    fun arrangeSelected(place: app.betternotes.core.edit.ZPlace) = canvas.arrangeSelection(place)

    /** Whether the selection is on the cover page, where post-its can be raised too. */
    /** The canvas of the other pane of a split screen, if there is one: what is selected can be copied into it. */
    var otherPane: CanvasView? by mutableStateOf(null)

    /** Copies the selection onto the page in view in the other pane, leaving the original where it is. */
    fun copySelectionToOtherPane() {
        val target = otherPane ?: return
        val items = selection?.takeIf { it.isNotEmpty() } ?: return
        target.pasteFrom(items, canvas::assetFile)
    }

    val selectionOnCover get() = canvas.selectionOnCover()

    /** Recolors the selected sticky notes, keeping their text and place. */
    fun recolorSelectedNotes(color: Int) = canvas.changeSelection { if (it is StickyNoteItem) it.copy(color = color) else it }

    fun setSelectedNotesKeepOpen(keepOpen: Boolean) =
        canvas.changeSelection { if (it is StickyNoteItem) it.copy(keepOpen = keepOpen) else it }

    /** Moves the selected pictures' opacity as a slider does, straight on the page. [finishSelectionAdjust] records it as one step. */
    fun adjustSelectedImageOpacity(opacity: Float) =
        canvas.adjustSelection { if (it is ImageItem) it.copy(opacity = opacity) else it }

    fun finishSelectionAdjust() = canvas.finishAdjustSelection()

    fun selectionTape(): TapeLook? = canvas.selectedTape()

    fun adjustSelectionTape(thickness: Float?, opacity: Float?) = canvas.adjustSelectedTape(thickness, opacity)

    fun finishAdjustSelectionTape() = canvas.finishAdjustSelectedTape()

    fun updateTape(change: (TapeOptions) -> TapeOptions) {
        tape = change(tape)
        applyTool()
    }

    fun chooseEmoji(next: String) {
        emoji = next
        if (next !in recentEmoji) recentEmoji = (listOf(next) + recentEmoji).take(RECENT_EMOJI)
        applyTool()
    }

    fun resizeEmoji(size: Int) {
        emojiSize = size.coerceIn(MIN_EMOJI, MAX_EMOJI)
        applyTool()
    }

    fun importVideo(uri: Uri) = canvas.importVideo(uri) { say(R.string.notice_video_failed) }

    /** A file from the picker: a PDF adds its pages, audio becomes a recording, a picture a picture. */
    fun importDocument(uri: Uri) {
        val type = canvas.context.contentResolver.getType(uri).orEmpty()
        when {
            type == "application/pdf" -> canvas.importPdfPages(uri, { say(R.string.notice_pdf_unsupported) }) { say(R.string.notice_pdf_failed) }
            type.startsWith("audio/") -> importAudio(uri, place = true)
            type.startsWith("image/") -> importImage(uri)
            else -> say(R.string.notice_file_unsupported)
        }
    }

    fun importImage(uri: Uri) = canvas.importImage(uri, image) { say(R.string.notice_image_failed) }

    /** Opens the list of what was copied lately, after adding what the system clipboard holds now. */
    fun openClipboard() {
        panelOpen = false
        toolbarSettingsOpen = false
        clipboard.captureCurrent()
        clipboardOpen = !clipboardOpen
    }

    /** Pastes one entry of the clipboard list. */
    fun pasteEntry(entry: ClipEntry) {
        clipboardOpen = false
        when (entry) {
            is TextClip -> canvas.pasteText(entry.text, note)
            is ImageClip -> importImage(Uri.fromFile(java.io.File(entry.path)))
            is ItemsClip -> canvas.pasteItems(entry.items())
        }
    }

    /** Pastes a picture or text from outside the app, or says there is nothing to paste. */
    fun paste() {
        val clipboard = canvas.context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri
        val text = item?.text?.toString()
        when {
            uri != null && clip.description.hasMimeType("image/*") -> importImage(uri)
            !text.isNullOrEmpty() -> canvas.pasteText(text, note)
            else -> say(R.string.notice_nothing_to_paste)
        }
    }

    val canAddPages get() = canvas.canAddPages

    /** Whether the pages run side by side, so that the end of the document is to the right (or left) and not below. */
    val scrollsSideways get() = canvas.scrollDirection != app.betternotes.core.layout.ScrollDirection.VERTICAL

    /** Whether the pages run right to left. */
    val scrollsLeft get() = canvas.scrollDirection == app.betternotes.core.layout.ScrollDirection.RIGHT_TO_LEFT

    /** Where the add-page button goes, or null when there is no room for it. */
    fun addPageSpot() = canvas.addPageSpot()

    fun addCoverSpot() = canvas.addCoverSpot()

    /** Adds a page at the end, where the button sits. */
    fun addPageAtEnd() = canvas.addPage(canvas.pageCount - 1)

    fun pages() = canvas.pages

    fun addPage(after: Int) = canvas.addPage(after)

    fun deletePage(index: Int) = canvas.deletePage(index)

    fun movePage(from: Int, to: Int) = canvas.movePage(from, to)

    fun thumbnail(pageId: Long, maxPx: Int) = canvas.thumbnail(pageId, maxPx)

    suspend fun pdfThumbnail(pageId: Long, widthPx: Int) = canvas.pdfThumbnail(pageId, widthPx)

    fun goToPage(index: Int) {
        canvas.goToPage(index)
        pagesOpen = false
    }

    fun recordings() = canvas.recordings()

    fun assetFile(assetId: String) = canvas.assetFile(assetId)

    fun importAudio(uri: Uri, place: Boolean = false) = canvas.importAudio(uri, place) { say(R.string.notice_audio_failed) }

    fun deleteRecording(item: AudioItem) = canvas.deleteAudio(item)

    /** The color the wheel opens on. */
    val colorStart: Int
        get() = when (colorTarget) {
            ColorTarget.SHAPE_BORDER -> shape.color
            ColorTarget.SHAPE_FILL, ColorTarget.TABLE_FILL -> DEFAULT_FILL
            ColorTarget.NOTE -> note.color
            ColorTarget.TABLE_BORDER -> table.color
            ColorTarget.LASER -> laserColor
            ColorTarget.FILL -> fillColor
            ColorTarget.TOOLBAR -> toolbarColor ?: DEFAULT_FILL
            ColorTarget.SELECTED_NOTE -> selection?.filterIsInstance<StickyNoteItem>()?.firstOrNull()?.color ?: DEFAULT_FILL
            else -> preset?.color ?: DEFAULT_FILL
        }

    fun clearPage() = canvas.clearPage()

    fun deleteSelection() = canvas.deleteSelection()

    fun chooseToolbarColor(color: Int?) {
        toolbarColor = color
        store.saveToolbarColor(color)
    }

    fun saveTools() {
        presets.values.forEach(store::save)
        store.save(eraser)
        store.save(lasso)
        store.save(shape)
        store.save(note)
        store.save(table)
        store.save(image)
        store.save(tape)
    }

    fun applyColor(color: Int) {
        when (colorTarget) {
            ColorTarget.CUSTOM_SLOT -> update { it.withCustomColor(color) }
            ColorTarget.SELECTED_SLOT -> update { it.withSlotColor(color) }
            ColorTarget.PALETTE -> update { it.withPaletteColor(color).pickColor(color) }
            ColorTarget.TAPE -> updateTape { it.withColor(color) }
            ColorTarget.SHAPE_BORDER -> updateShape { it.withColor(color) }
            ColorTarget.SHAPE_FILL -> updateShape { it.copy(fillColor = color) }
            ColorTarget.NOTE -> updateNote { it.withColor(color) }
            ColorTarget.TABLE_BORDER -> updateTable { it.withColor(color) }
            ColorTarget.TABLE_FILL -> updateTable { it.copy(fillColor = color) }
            ColorTarget.LASER -> selectLaserColor(color)
            ColorTarget.FILL -> addFillColor(color)
            ColorTarget.SELECTED_NOTE -> recolorSelectedNotes(color or OPAQUE)
            ColorTarget.TOOLBAR -> chooseToolbarColor(color or OPAQUE)
            null -> Unit
        }
        colorTarget = null
    }

    fun toggleFingerDraw() {
        fingerDraws = !fingerDraws
        drawingMode = if (fingerDraws) InputMode.FINGER_DRAW else InputMode.STYLUS_DRAW
        if (!viewOnly) canvas.inputMode = drawingMode
    }

    fun toggleViewOnly() {
        viewOnly = !viewOnly
        canvas.inputMode = if (viewOnly) InputMode.VIEW_ONLY else drawingMode
        if (viewOnly) {
            panelOpen = false
            colorTarget = null
        }
    }

    fun toggleRuler() {
        rulerOn = !rulerOn
        canvas.rulerVisible = rulerOn
    }

    fun toggleLoupe() {
        loupeOpen = !loupeOpen
        canvas.loupeOpen = loupeOpen
        if (loupeOpen) panelOpen = false
    }

    fun setLoupeArea(left: Float, top: Float, right: Float, bottom: Float) = canvas.setLoupeArea(left, top, right, bottom)

    fun recenterLoupe() = canvas.recenterLoupe()

    fun stepLoupe(forward: Boolean) = canvas.stepLoupe(forward)

    fun selectLaserWidth(slot: Int) {
        laserWidthSlot = slot
        canvas.laserScale = LASER_SCALES[slot]
    }

    fun selectLaserMode(mode: LaserMode) {
        laserMode = mode
        canvas.laserMode = mode
    }

    fun selectLaserLength(length: Int) {
        laserLength = length.coerceIn(MIN_LASER_LENGTH, MAX_LASER_LENGTH)
        canvas.laserLength = laserLength
    }

    fun selectLaserColor(color: Int) {
        laserColor = color
        canvas.laserColor = color
    }

    fun scrubTo(fraction: Float, last: Boolean) = canvas.scrollToFraction(fraction, last)

    /** [number] is as shown: the cover is 0 and the first page after it is 1. */
    fun goToPageNumber(number: Int) = canvas.goToPage(number - 1 + coverOffset)

    fun previousPage() = canvas.goToPage(canvas.currentPage - 1)

    fun nextPage() = canvas.goToPage(canvas.currentPage + 1)

    fun zoomToRealSize() = canvas.zoomTo(1f)

    /** The document under the canvas changed, so the page count may have too. */
    fun refreshPosition() {
        currentPage = canvas.currentPage
        pageCount = canvas.pageCount
        coverOffset = if (coverId != null) 1 else 0
        zoom = canvas.viewport.zoom
        scrollFraction = canvas.scrollFraction
        viewMoves++
    }

    fun minimap() = canvas.minimap()

    fun undo() = canvas.undo()

    fun redo() = canvas.redo()

    /** Tells the canvas where a control sits, or that it has gone, so that wet ink stays out from under it. */
    fun reportOverlay(key: String, bounds: RectF?) {
        val changed = if (bounds == null) overlays.remove(key) != null else overlays.put(key, bounds) != bounds
        if (changed) canvas.setOverlayBounds(overlays.values.toList())
    }

    private fun applyTool() {
        canvas.circleLasso = CanvasTool.Lasso(lasso.rectangle, lasso.kinds, lasso.fullSelectionOnly, !lasso.fullSelectionOnly, lasso.lockedToo)
        val current = preset
        canvas.tool = when {
            current != null -> CanvasTool.Ink(current.style(), current.holdToLineMs, current.scribbleToErase, current.circleToLasso, current.stabilization)
            activeTool == ActiveTool.LASER -> CanvasTool.Laser
            activeTool == ActiveTool.LASSO -> CanvasTool.Lasso(lasso.rectangle, lasso.kinds, lasso.fullSelectionOnly, !lasso.fullSelectionOnly, lasso.lockedToo)
            activeTool == ActiveTool.SHAPE -> CanvasTool.Shape(shape)
            activeTool == ActiveTool.STICKY -> CanvasTool.Sticky(note)
            activeTool == ActiveTool.TEXT -> CanvasTool.Text(note)
            activeTool == ActiveTool.TABLE -> CanvasTool.Table(table)
            activeTool == ActiveTool.FILL && pickingColor -> CanvasTool.Pick(::pickColorAt)
            activeTool == ActiveTool.FILL -> CanvasTool.Fill(fillColor, fillOpacity)
            activeTool == ActiveTool.TAPE -> CanvasTool.Tape(tape)
            activeTool == ActiveTool.EMOJI && stickerTab && stickerItems != null -> {
                val chosen = sticker
                CanvasTool.Sticker(stickerItems.orEmpty()) { assetId -> chosen?.let { stickerStore.assetFile(it, assetId) } }
            }
            activeTool == ActiveTool.EMOJI -> CanvasTool.Emoji(emoji, emojiSize)
            activeTool == ActiveTool.IMAGE -> CanvasTool.Lasso(rectangle = true, ALL_ITEM_KINDS, fullOnly = false, lockedToo = lasso.lockedToo)
            else -> CanvasTool.Eraser(eraser.mode, eraser.radiusPt, eraser.kinds, eraser.lockedToo)
        }
    }

    companion object {
        const val MIN_LASER_LENGTH = 1
        const val MIN_FILL_RETURN_TENTHS = 0
        const val MAX_FILL_RETURN_TENTHS = 100
        const val MAX_LASER_LENGTH = 5
        const val MIN_EMOJI = 12
        const val MAX_EMOJI = 96
        val LASER_SCALES = floatArrayOf(0.5f, 1f, 1.6f)
        private const val RECENT_EMOJI = 5
        private const val DEFAULT_EMOJI = 28
        const val ALPHA_SHIFT = 24
        const val MIN_FILL_OPACITY = 0.1f
        private const val AFTER_GESTURE_MS = 1000L
        private const val AFTER_TAP_MS = 4000L
        private val OPAQUE = 0xFF000000.toInt()
        const val DEFAULT_FILL = 0xFF5F9FFB.toInt()
    }
}
