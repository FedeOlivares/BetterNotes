package app.betternotes.ui.editor

import app.betternotes.core.edit.LayerOps
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
import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StickyNoteItem
import app.betternotes.core.model.TableItem
import app.betternotes.core.model.withLocked
import app.betternotes.core.model.TextSpans
import app.betternotes.core.model.TextStyle
import app.betternotes.core.tools.ALL_ITEM_KINDS
import app.betternotes.core.tools.EraserOptions
import app.betternotes.core.tools.ImageOptions
import app.betternotes.core.tools.InkPreset
import app.betternotes.core.tools.ToolbarGroups
import app.betternotes.core.tools.LassoOptions
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListStyle
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ReturnTarget
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.canvas.TapeLook
import app.betternotes.core.clipboard.ClipEntry
import app.betternotes.core.clipboard.ImageClip
import app.betternotes.core.clipboard.ItemsClip
import app.betternotes.core.clipboard.TextClip
import app.betternotes.core.tools.TapeOptions
import app.betternotes.ui.theme.ALPHA_SHIFT
import app.betternotes.ui.theme.OPAQUE_ALPHA
import app.betternotes.ui.theme.Swatches
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

/** The tools that put items on the page; a selection let go with one of them in hand gives back the tool before it. */
private val TOOLS_THAT_MAKE_ITEMS = setOf(ActiveTool.SHAPE, ActiveTool.STICKY, ActiveTool.TABLE, ActiveTool.IMAGE, ActiveTool.EMOJI)

/** What the color wheel's result is for. */
internal enum class ColorTarget { FILL_PATTERN, TAPE, CUSTOM_SLOT, SELECTED_SLOT, PALETTE, SHAPE_BORDER, SHAPE_FILL, NOTE, TABLE_BORDER, TABLE_FILL, TABLE_HEADER, SELECTED_TABLE_LINE, SELECTED_TABLE_FILL, SELECTED_TABLE_HEADER, LASER, TOOLBAR, SELECTED_NOTE, FILL, LIST_DOT, TEXT_COLOR, TEXT_BACKGROUND, PLANNER_ELEMENT }

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

    /** Writes sticker [id] as a PNG to the file the owner picked, and says whether it worked. */
    fun exportStickerPng(id: Long, target: Uri) {
        val resolver = canvas.context.contentResolver
        stickerWork.execute {
            val done = try {
                resolver.openOutputStream(target)?.use { stickerStore.exportPng(id, it) } == true
            } catch (e: java.io.IOException) {
                false
            }
            canvas.post {
                if (done) {
                    val context = canvas.context
                    android.widget.Toast.makeText(context, context.getString(R.string.notice_sticker_saved_to, savedName(target), savedFolder(target)), android.widget.Toast.LENGTH_LONG).show()
                } else {
                    say(R.string.notice_sticker_export_failed)
                }
            }
        }
    }

    /** The file name the owner gave in the save picker, or the last part of its address. */
    private fun savedName(target: Uri): String {
        val resolver = canvas.context.contentResolver
        val named = try {
            resolver.query(target, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: RuntimeException) {
            null
        }
        return named ?: (target.lastPathSegment ?: "").substringAfterLast('/')
    }

    /** The folder of a file in the device's own storage, like "Pictures" or "Download/Stickers"; the provider's name for any other. */
    private fun savedFolder(target: Uri): String {
        val id = try {
            android.provider.DocumentsContract.getDocumentId(target)
        } catch (e: IllegalArgumentException) {
            return target.authority.orEmpty()
        }
        val path = id.substringAfter(':', "").substringBeforeLast('/', "")
        return if (target.authority == EXTERNAL_STORAGE_AUTHORITY) path.ifEmpty { canvas.context.getString(R.string.sticker_folder_storage) } else (target.authority ?: "")
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

    /** The Layers menu, opened by the layers button. */
    var layersOpen by mutableStateOf(false)

    /** The items and layers of the page in view, or null while the document is not open. Read it again after [historyVersion] changes. */
    fun layersContent() = canvas.currentContent()

    fun toggleLayers() {
        panelOpen = false
        toolbarSettingsOpen = false
        clipboardOpen = false
        layersOpen = !layersOpen
    }

    fun selectLayer(id: Long) = canvas.selectLayer(id)

    fun addLayer() = canvas.addLayer()

    fun renameLayer(id: Long, name: String) = canvas.editLayers { page, content -> LayerOps.rename(page, content, id, name) }

    fun setLayerVisible(id: Long, visible: Boolean) = canvas.setLayerVisible(id, visible)

    /** [to] counts from the bottom layer, which is 0. */
    fun moveLayer(id: Long, to: Int) = canvas.editLayers { page, content -> LayerOps.move(page, content, id, to) }

    fun mergeLayerDown(id: Long) = canvas.editLayers { page, content -> LayerOps.mergeDown(page, content, id) }

    fun deleteLayer(id: Long) = canvas.editLayers { page, content -> LayerOps.delete(page, content, id) }

    fun moveSelectionToLayer(id: Long) = canvas.moveSelectionToLayer(id)

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

    /** What the bucket draws over its color: marks of this kind, color, spacing and weight. Solid, the default, draws none. */
    var fillPattern by mutableStateOf(store.fillPattern())
        private set

    /** True when the bucket draws [fillPattern] over its color; off, the fill is the plain color and the choices are hidden. */
    var fillPatternOn by mutableStateOf(store.fillPatternOn())
        private set

    fun chooseFillPatternOn(on: Boolean) {
        fillPatternOn = on
        store.saveFillPatternOn(on)
        applyTool()
    }

    fun updateFillPattern(change: (FillPattern) -> FillPattern) {
        fillPattern = change(fillPattern)
        store.saveFillPattern(fillPattern)
        applyTool()
    }

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
        val opaque = color or OPAQUE_ALPHA
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

    /**
     * Whether a shape, note, table or picture just put down gives back the tool in use before, and the delay in tenths of a second
     * within which the creating tool is kept, so that several can be made in a row. The new item is not selected.
     */
    var createReturn by mutableStateOf(store.createReturn())
        private set
    var createReturnDelay by mutableIntStateOf(store.createReturnDelay())
        private set

    fun chooseCreateReturn(on: Boolean) {
        createReturn = on
        store.saveCreateReturn(createReturn, createReturnDelay)
        if (!on) canvas.removeCallbacks(createReturnTimer)
    }

    fun chooseCreateReturnDelay(tenths: Int) {
        createReturnDelay = tenths.coerceIn(MIN_FILL_RETURN_TENTHS, MAX_FILL_RETURN_TENTHS)
        store.saveCreateReturn(createReturn, createReturnDelay)
    }

    /**
     * Whether the highlighter, eraser, lasso, text, tape and sticker tools give the tool in use before back once they have been used, and the delay
     * in tenths of a second, each on its own. The use that ends it is a stroke, an erase, a selection let go, the text left, a sticker put down.
     */
    private val returns = mutableStateMapOf<ActiveTool, ToolReturn>().apply {
        for (tool in RETURNING_TOOLS) put(tool, store.toolReturn(tool))
    }

    fun toolReturn(tool: ActiveTool): ToolReturn = returns[tool] ?: ToolReturn()

    fun chooseToolReturn(tool: ActiveTool, on: Boolean) {
        val choice = toolReturn(tool).copy(on = on)
        returns[tool] = choice
        store.saveToolReturn(tool, choice)
        if (!on) canvas.removeCallbacks(toolReturnTimer)
    }

    fun chooseToolReturnDelay(tool: ActiveTool, tenths: Int) {
        val choice = toolReturn(tool).copy(tenths = tenths.coerceIn(MIN_FILL_RETURN_TENTHS, MAX_FILL_RETURN_TENTHS))
        returns[tool] = choice
        store.saveToolReturn(tool, choice)
    }

    private var timedTool = ActiveTool.PEN
    private val toolReturnTimer = Runnable { if (activeTool == timedTool) returnToPreviousTool() }

    /** [tool] was used: if it is in hand and gives the tool before back, that happens once its delay has passed, and every use starts the delay again. */
    private fun toolUsed(tool: ActiveTool) {
        if (activeTool != tool) return
        val choice = returns[tool] ?: return
        canvas.removeCallbacks(toolReturnTimer)
        if (!choice.on) return
        timedTool = tool
        canvas.postDelayed(toolReturnTimer, choice.tenths * 100L)
    }

    private var createdTool: ActiveTool? = null
    private var createdAtMs = 0L

    /** An item was just put down by the tool in hand, and its selection is about to take the lasso's place. */
    private fun itemCreated() {
        createdTool = activeTool.takeIf { it == ActiveTool.SHAPE || it == ActiveTool.STICKY || it == ActiveTool.TABLE || it == ActiveTool.IMAGE || it == ActiveTool.EMOJI }
        createdAtMs = android.os.SystemClock.uptimeMillis()
        // A new item is left unselected and the tool in use before comes back once the delay has passed. Making another keeps it in hand.
        val made = createdTool ?: return
        canvas.removeCallbacks(createReturnTimer)
        val choice = if (made == ActiveTool.EMOJI) toolReturn(made) else ToolReturn(createReturn, createReturnDelay)
        if (!choice.on) return
        timedCreated = made
        canvas.postDelayed(createReturnTimer, choice.tenths * 100L)
    }

    private var timedCreated = ActiveTool.PEN
    private val createReturnTimer = Runnable { if (activeTool == timedCreated) returnToPreviousTool() }

    /** The tool to give back when a selection is let go: the one the lasso was lent from, or, for a new item, the one before it. */
    private fun toolAfterSelection(lentFrom: ActiveTool?): ActiveTool? {
        val made = createdTool
        createdTool = null
        if (lentFrom == null || made != lentFrom) return lentFrom
        val choice = if (made == ActiveTool.EMOJI) toolReturn(made) else ToolReturn(createReturn, createReturnDelay)
        if (!choice.on) return lentFrom
        val kept = android.os.SystemClock.uptimeMillis() - createdAtMs < choice.tenths * 100L
        return if (kept) lentFrom else toolToReturnTo()
    }

    /** A fill was made: the delay starts again, so that using the bucket again keeps it in hand. */
    private fun fillUsed() {
        canvas.removeCallbacks(fillReturnTimer)
        if (fillReturn) canvas.postDelayed(fillReturnTimer, fillReturnDelay * 100L)
    }

    fun chooseFillColor(color: Int) {
        fillColor = color or OPAQUE_ALPHA
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

    val plannerColors get() = canvas.plannerColors

    fun setPlannerColors(colors: List<Int>) = canvas.setPlannerColors(colors)

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
    /** The generated elements the lasso picked on a planner page (locked content: they can only be restyled), or none. */
    var plannerPicked by mutableStateOf<List<app.betternotes.core.planner.PlannerElement>>(emptyList())
        private set

    /** Whether a restyle goes to every element like the picked ones, on every page. */
    var plannerSimilar by mutableStateOf(false)

    /** The part of the picked elements whose color the wheel is changing. */
    var plannerSlot by mutableStateOf<app.betternotes.core.planner.RoleSlot?>(null)

    /** Whether the restyle bar's tab of less used parts is unfolded. */
    var plannerMoreOpen by mutableStateOf(false)

    /** Whether the font list of the restyle bar is open. */
    var plannerFontOpen by mutableStateOf(false)

    fun plannerSlots() = canvas.plannerPickSlots()

    fun plannerAnchor() = canvas.plannerPickAnchor()

    fun plannerFont() = canvas.plannerPickFont()

    fun plannerColorOf(slot: app.betternotes.core.planner.RoleSlot) = canvas.plannerPickColor(slot)

    /** Opens the color wheel for [slot] of the picked planner elements. */
    fun pickPlannerColor(slot: app.betternotes.core.planner.RoleSlot) {
        plannerSlot = slot
        colorTarget = ColorTarget.PLANNER_ELEMENT
    }

    fun restylePlanner(change: app.betternotes.core.planner.PlannerPages.StyleChange) = canvas.restylePlanner(change, plannerSimilar)

    /** Turns "similar" on or off. Turned on, it carries what was already changed on the picked elements to every one like them. */
    fun togglePlannerSimilar() {
        plannerSimilar = !plannerSimilar
        if (plannerSimilar) canvas.spreadPlanner()
    }

    fun clearPlannerPick() = canvas.clearPlannerPick()

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
            // A picture just put down is selected with the lasso lent too, when the picture tool is to give the tool before back.
            val pictureMade = activeTool == ActiveTool.IMAGE && createdTool == ActiveTool.IMAGE && createReturn
            if ((activeTool != ActiveTool.IMAGE || pictureMade) && activeTool != ActiveTool.LASSO) {
                // From the text tool the lasso gives back the tool that was in hand before it.
                toolBeforeLasso = if (activeTool == ActiveTool.TEXT) previousTool else activeTool
                lendingTool = true
                select(ActiveTool.LASSO)
                lendingTool = false
            }
        }
        // The lasso that a circle or a placed item asked for is lent: it goes back once the selection is let go.
        canvas.onTextToolRequested = {
            if (activeTool != ActiveTool.TEXT) {
                // A lasso lent from another tool leaves that tool as the one to come back to.
                val lent = activeTool == ActiveTool.LASSO && toolBeforeLasso != null
                if (lent) {
                    previousTool = toolBeforeLasso ?: previousTool
                    lendingTool = true
                }
                select(ActiveTool.TEXT)
                lendingTool = false
            }
        }
        canvas.onTextToolLeft = { toolUsed(ActiveTool.TEXT) }
        canvas.onInkDrawn = { toolUsed(ActiveTool.HIGHLIGHTER) }
        canvas.onEraseDone = { toolUsed(ActiveTool.ERASER) }
        canvas.onTapeLaid = { toolUsed(ActiveTool.TAPE) }
        canvas.onItemCreated = ::itemCreated
        canvas.onSelectionLeft = {
            // A tool that makes items gives way, once its selection is let go, to the tool in hand before it.
            if (activeTool in TOOLS_THAT_MAKE_ITEMS && toolToReturnTo() != activeTool) {
                returnToPreviousTool()
                true
            } else {
                false
            }
        }
        canvas.onSelectionDropped = {
            val back = toolAfterSelection(toolBeforeLasso)
            val lassoUsed = toolBeforeLasso == null && lassoPicked
            lassoPicked = false
            // The lasso chosen from the strip, not lent: once its selection is let go it has been used. An edit clears and restores the selection, so look again.
            if (lassoUsed) canvas.post { if (!canvas.hasSelection) toolUsed(ActiveTool.LASSO) }
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
            if (it != null && activeTool == ActiveTool.LASSO) lassoPicked = true
            // An edit clears the selection for a moment and puts it back, so wait to see whether it is really gone.
            if (it == null) canvas.post { if (!canvas.hasSelection) closeSelectionMenu() }
        }
        canvas.onPlannerPick = {
            plannerPicked = it
            if (it.isEmpty()) {
                plannerFontOpen = false
                plannerMoreOpen = false
                if (colorTarget == ColorTarget.PLANNER_ELEMENT) colorTarget = null
            }
        }
        canvas.onSelectionMenu = { setSelectionMenu(!selectionMenuOpen) }
        canvas.onItemsCopied = clipboard::remember
        canvas.setGrid(grid.on && grid.cross, grid.on && grid.thirds)
        canvas.onSaveSticker = ::saveAsSticker
        canvas.onFillFailed = { say(R.string.notice_fill_open) }
        canvas.onWritingModelsMissing = { say(R.string.notice_writing_models) }
        canvas.onReturnTool = { toolUsed(ActiveTool.EMOJI) }
        canvas.onFillDone = ::fillUsed
        canvas.onTextEdit = { request ->
            textEdit = request
            // Writing again before the text tool has handed back keeps it in hand.
            if (request != null) canvas.removeCallbacks(toolReturnTimer)
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

    /** True once the lasso in hand has picked something up, so that letting it go counts as using it. */
    private var lassoPicked = false

    /** The tool in use before the one in use now, which a note or sticker made by holding and dragging gives back. */
    private var previousTool: ActiveTool? = null

    /** The tool a tool that gives the one before back goes to now: the one before it, or the pen when there is none. */
    private fun toolToReturnTo() = ReturnTarget.target(previousTool, activeTool, ActiveTool.PEN)

    /** True while a tool is lent for a moment, as the lasso is to move a sticker just put down: it is not the tool to come back to. */
    private var lendingTool = false

    fun returnToPreviousTool() {
        val back = toolToReturnTo()
        panelOpen = false
        if (back != activeTool) select(back)
    }

    /** A sticker being dragged out of the list: which one, and where the finger is in the window. */
    var stickerDragId by mutableStateOf<Long?>(null)
        private set
    var stickerDragPoint by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)

    /** Where the stickers and the set headings of the open list are in the window, for dropping a sticker onto one. The keys are a sticker's id or a set's name, or [LOOSE_STICKERS]. */
    val stickerDropTargets = HashMap<Any, androidx.compose.ui.geometry.Rect>()

    /** Where the open sticker panel is in the window. A sticker let go over it, on nothing in the list, is not put on the page. */
    var stickerPanelBounds: androidx.compose.ui.geometry.Rect? = null

    /** What the sticker being dragged is over in the list: another sticker's id, a set's name or [LOOSE_STICKERS], or null. */
    fun stickerDropKey(): Any? {
        val at = stickerDragPoint ?: return null
        val dragged = stickerDragId
        return stickerDropTargets.entries.firstOrNull { (key, box) -> key != dragged && box.contains(at) }?.key
    }

    /**
     * Joins sticker [id] into the set of [target]: a sticker that has a set brings [id] into it, one that has none makes a new set of both,
     * a set's heading brings it in, and [LOOSE_STICKERS] takes it out of its set.
     */
    private fun joinStickers(id: Long, target: Any) {
        stickerWork.execute {
            val done = when {
                target == LOOSE_STICKERS -> stickerStore.setPack(id, null)
                target is String -> stickerStore.setPack(id, target)
                target is Long -> {
                    val existing = stickerStore.packOf(target)
                    if (existing != null) {
                        stickerStore.setPack(id, existing)
                    } else {
                        val taken = stickerStore.list().mapNotNull { stickerStore.packOf(it) }.toSet()
                        val name = generateSequence(1) { it + 1 }.map { canvas.context.getString(R.string.sticker_set_name, it) }.first { it !in taken }
                        stickerStore.setPack(target, name) && stickerStore.setPack(id, name)
                    }
                }
                else -> false
            }
            canvas.post {
                packs = stickers.associateWith { stickerStore.packOf(it) }
                if (!done) say(R.string.notice_sticker_failed)
            }
        }
    }

    fun beginStickerDrag(id: Long, at: androidx.compose.ui.geometry.Offset) {
        stickerDragId = id
        stickerDragPoint = at
    }

    /** Lets go of the sticker over the page: it goes there at its own size, and the tool in use before comes back. */
    fun dropStickerDrag() {
        val id = stickerDragId
        val at = stickerDragPoint
        val over = stickerDropKey()
        stickerDragId = null
        stickerDragPoint = null
        if (id == null || at == null) return
        // Let go over another sticker or a set's heading in the list: they go into one set, and nothing is put on the page.
        if (over != null) {
            joinStickers(id, over)
            return
        }
        if (panelOpen && activeTool == ActiveTool.EMOJI && stickerPanelBounds?.contains(at) == true) return
        val items = stickerStore.read(id) ?: return
        val where = IntArray(2)
        canvas.getLocationInWindow(where)
        val placed = canvas.dropSticker(at.x - where[0], at.y - where[1], CanvasTool.Sticker(items) { assetId -> stickerStore.assetFile(id, assetId) })
        if (placed) toolUsed(ActiveTool.EMOJI)
    }

    fun cancelStickerDrag() {
        stickerDragId = null
        stickerDragPoint = null
    }

    fun select(tool: ActiveTool) {
        if (tool != ActiveTool.LASSO) toolBeforeLasso = null
        canvas.removeCallbacks(fillReturnTimer)
        canvas.removeCallbacks(toolReturnTimer)
        canvas.removeCallbacks(createReturnTimer)
        if (tool != activeTool && !lendingTool) previousTool = activeTool
        if (tool == ActiveTool.EMOJI && stickerTab && sticker == null) stickers.firstOrNull()?.let { chooseSticker(it) }
        if (tool == activeTool) {
            if (tool.hasPanel) panelOpen = !panelOpen
            return
        }
        canvas.endTextEdit()
        toolbarSettingsOpen = false
        clipboardOpen = false
        layersOpen = false
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

    /** The open text box with letters selected in it, which the text row buttons then change instead of the whole text. */
    /** The text box selected on its own, which the text row and panel then show and change. */
    val selectedText: TextItem? get() = if (activeTool == ActiveTool.LASSO) selection?.singleOrNull() as? TextItem else null

    private fun selectedLetters() = textEdit?.takeIf { activeTool == ActiveTool.TEXT && it.hasSelection }

    /** The look the text row shows: that of the selected letters when some are selected, else the look of the whole text. */
    fun shownTextStyle(options: NoteOptions): TextStyle {
        selectedText?.let { return it.style }
        val request = selectedLetters() ?: return options.style
        return options.style.copy(
            bold = request.selectionAll { look, base -> look.bold ?: base.bold },
            italic = request.selectionAll { look, base -> look.italic ?: base.italic },
            underline = request.selectionAll { look, base -> look.underline ?: base.underline },
            strikethrough = request.selectionAll { look, base -> look.strikethrough ?: base.strikethrough },
        )
    }

    /** The color the text row shows: that of the text box selected, else the color new text is written in. */
    fun shownTextColor(options: NoteOptions): Int = selectedText?.color ?: selectedLetters()?.let { textEdit?.shownColor() } ?: options.textColor

    /**
     * Sets a color of text. For a text box selected it is the color of all of it, whatever colors single letters had. In a box being typed in it
     * is the color of the letters selected, or of all the text when all of it is, or of an empty box; with nothing selected it is the color
     * of what is typed next. Otherwise it is the color new text boxes are written in.
     */
    fun setTextColor(color: Int) {
        if (selectedText != null) {
            changeSelectedText { it.copy(color = color, spans = it.spans.map { span -> span.copy(look = span.look.copy(color = null)) }.filter { span -> !span.look.isPlain }) }
            return
        }
        val request = textEdit?.takeIf { activeTool == ActiveTool.TEXT }
        when {
            request == null -> Unit
            request.selectionIsAll || (!request.hasSelection && request.text.isEmpty()) -> request.recolorAll(color)
            request.hasSelection -> request.colorSelection(color)
            else -> request.typingColor = color.takeIf { it != request.color }
        }
        // The tool keeps the color for the boxes it makes next, unless only some letters of a box were colored.
        if (request == null || !request.hasSelection || request.selectionIsAll) updateNote { it.copy(textColor = color) }
    }

    fun shownTextSize(options: NoteOptions): Int = selectedText?.textSize?.roundToInt() ?: selectedLetters()?.shownSize()?.roundToInt() ?: options.textSize

    /** Switches bold, italic, underline or strike for the selected letters, or for the whole text when none are selected. */
    fun toggleStyle(kind: StyleKind) {
        if (selectedText != null) {
            changeSelectedText { it.copy(style = it.style.toggled(kind)) }
            return
        }
        val request = selectedLetters()
        if (request == null) {
            updateNoteStyle { it.toggled(kind) }
            return
        }
        val on = !shownTextStyle(note).has(kind)
        request.styleSelection {
            when (kind) {
                StyleKind.BOLD -> it.copy(bold = on)
                StyleKind.ITALIC -> it.copy(italic = on)
                StyleKind.UNDERLINE -> it.copy(underline = on)
                StyleKind.STRIKE -> it.copy(strikethrough = on)
            }
        }
    }

    /** The font the text row shows: that of the first selected letter when some are selected, else the whole text's. */
    fun shownFont(options: NoteOptions): String {
        selectedText?.let { return it.style.font }
        val request = selectedLetters() ?: return options.style.font
        return TextSpans.looks(request.spans, request.text.length)[request.selectionStart].font ?: request.style.font
    }

    /** Sets the font of the selected letters, or of the whole text when none are selected. */
    fun setFont(name: String) {
        if (selectedText != null) {
            changeSelectedText { it.copy(style = it.style.copy(font = name)) }
            return
        }
        val request = selectedLetters()
        if (request == null) updateNoteStyle { it.copy(font = name) } else request.styleSelection { it.copy(font = name) }
    }

    /** Sets the size of the selected letters, or of the whole text when none are selected. */
    fun setTextSize(size: Int) {
        if (selectedText != null) {
            changeSelectedText { it.copy(textSize = size.toFloat()) }
            return
        }
        val request = selectedLetters()
        if (request == null) updateNote { it.copy(textSize = size) } else request.styleSelection { it.copy(size = size.toFloat()) }
    }

    /** Changes the selected text box, as one step that can be undone. */
    fun changeSelectedText(change: (TextItem) -> TextItem) = canvas.changeSelection { if (it is TextItem) change(it) else it }

    /** Changes the style of the text box selected, or else of what the text tool writes next. */
    fun updateNoteStyle(change: (TextStyle) -> TextStyle) {
        if (selectedText != null) changeSelectedText { it.copy(style = change(it.style)) } else updateNote { it.copy(style = change(it.style)) }
    }

    fun updateTable(change: (TableOptions) -> TableOptions) {
        table = change(table)
        applyTool()
    }

    private fun selectedTables() = selection?.filterIsInstance<TableItem>().orEmpty()

    /** Changes the line, fill or header color of the selected tables, as one step that can be undone. */
    fun recolorSelectedTables(change: (TableItem) -> TableItem) = canvas.changeSelection { if (it is TableItem) change(it) else it }

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

    /** Groups the selected items so they are selected and moved as one, or splits them again. */
    fun groupSelection(grouped: Boolean) = canvas.groupSelection(grouped)

    /** Moves the selection to the front, middle or back. */
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
    fun mirrorSelectedShapes(on: Boolean) = canvas.changeSelection { if (it is ShapeItem && it.kind != ShapeKind.TAPE) it.copy(mirrored = on) else it }

    fun editSelectedNoteText() = canvas.editSelectedNoteText()

    fun recolorSelectedNotes(color: Int) = canvas.changeSelection { if (it is StickyNoteItem) it.copy(color = color) else it }

    /** Gives the selected post-its a color from the palette and leaves each one as see-through as it was. */
    fun pickSelectedNotesColor(rgb: Int) =
        canvas.changeSelection { if (it is StickyNoteItem) it.copy(color = (it.color and NoteOptions.RGB_MASK.inv()) or (rgb and NoteOptions.RGB_MASK)) else it }

    /** Moves the selected post-its' opacity as a slider does. [finishSelectionAdjust] records it as one step. */
    fun adjustSelectedNotesOpacity(opacity: Float) =
        canvas.adjustSelection { if (it is StickyNoteItem) it.copy(color = NoteOptions.argb(it.color, opacity)) else it }

    /** The tool the toolbars show: the post-it tool while post-its are selected, since that is where their color and opacity are, else the one in use. */
    val shownTool get() = if (activeTool == ActiveTool.LASSO && selectionIsNotes) ActiveTool.STICKY else activeTool

    /** Whether the lasso holds post-its and nothing else, which is when the second toolbar offers their color and opacity. */
    val selectionIsNotes get() = selection?.let { it.isNotEmpty() && it.all { item -> item is StickyNoteItem } } == true

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
            ColorTarget.TABLE_HEADER -> DEFAULT_FILL
            ColorTarget.SELECTED_TABLE_LINE -> selectedTables().firstOrNull()?.lineColor ?: DEFAULT_FILL
            ColorTarget.SELECTED_TABLE_FILL -> selectedTables().firstOrNull()?.fillColor?.takeIf { it ushr ALPHA_SHIFT != 0 } ?: DEFAULT_FILL
            ColorTarget.SELECTED_TABLE_HEADER -> selectedTables().firstOrNull()?.headerColor?.takeIf { it != 0 } ?: DEFAULT_FILL
            ColorTarget.LASER -> laserColor
            ColorTarget.FILL -> fillColor
            ColorTarget.FILL_PATTERN -> fillPattern.color
            ColorTarget.TOOLBAR -> toolbarColor ?: DEFAULT_FILL
            ColorTarget.LIST_DOT -> shownTextStyle(note).list?.dotColor ?: ListStyle.DEFAULT_DOT_COLOR
            ColorTarget.TEXT_COLOR -> shownTextColor(note)
            ColorTarget.TEXT_BACKGROUND -> shownTextStyle(note).backgroundColor.takeIf { it ushr ALPHA_SHIFT != 0 } ?: DEFAULT_FILL
            ColorTarget.SELECTED_NOTE -> selection?.filterIsInstance<StickyNoteItem>()?.firstOrNull()?.color ?: DEFAULT_FILL
            ColorTarget.PLANNER_ELEMENT -> plannerSlot?.let { canvas.plannerPickColor(it) } ?: DEFAULT_FILL
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

    /** How opaque new post-its are unless their color has an opacity of its own: a setting. */
    val stickyOpacity get() = canvas.stickyOpacity

    /** Saves a new general post-it opacity in the settings, which every pane follows. */
    var onStickyOpacity: (Float) -> Unit = {}

    fun setStickyOpacity(opacity: Float) = onStickyOpacity(opacity.coerceIn(NoteOptions.MIN_OPACITY, 1f))

    /** Whether the wheel offers an opacity: it does for a post-it's color, for the tool and for the post-its selected. */
    val colorHasOpacity get() = colorTarget == ColorTarget.NOTE || colorTarget == ColorTarget.SELECTED_NOTE

    /** The opacity the wheel opens on. */
    val opacityStart: Float
        get() = when (colorTarget) {
            ColorTarget.NOTE -> note.opacityOf(note.color, stickyOpacity)
            ColorTarget.SELECTED_NOTE -> NoteOptions.alphaOf(colorStart)
            else -> 1f
        }

    /** Whether the color the wheel opens on has no opacity of its own. A post-it already placed has no such thing: it keeps what it was given. */
    val followsGeneralStart: Boolean
        get() = colorTarget == ColorTarget.NOTE && (note.color and NoteOptions.RGB_MASK) !in note.colorOpacity

    /** [opacity] is null for a color that follows the general opacity. */
    fun applyColor(color: Int, opacity: Float? = null) {
        when (colorTarget) {
            ColorTarget.CUSTOM_SLOT -> update { it.withCustomColor(color) }
            ColorTarget.SELECTED_SLOT -> update { it.withSlotColor(color) }
            ColorTarget.PALETTE -> update { it.withPaletteColor(color).pickColor(color) }
            ColorTarget.TAPE -> updateTape { it.withColor(color) }
            ColorTarget.SHAPE_BORDER -> updateShape { it.withColor(color) }
            ColorTarget.SHAPE_FILL -> updateShape { it.copy(fillColor = color) }
            ColorTarget.NOTE -> updateNote { it.withColor(color).withOpacity(color, opacity) }
            ColorTarget.TABLE_BORDER -> updateTable { it.withColor(color) }
            ColorTarget.TABLE_FILL -> updateTable { it.copy(fillColor = color) }
            ColorTarget.TABLE_HEADER -> updateTable { it.copy(headerColor = color) }
            ColorTarget.SELECTED_TABLE_LINE -> recolorSelectedTables { it.copy(lineColor = color or OPAQUE_ALPHA) }
            ColorTarget.SELECTED_TABLE_FILL -> recolorSelectedTables { it.copy(fillColor = color) }
            ColorTarget.SELECTED_TABLE_HEADER -> recolorSelectedTables { it.copy(headerColor = color) }
            ColorTarget.LASER -> selectLaserColor(color)
            ColorTarget.FILL -> addFillColor(color)
            ColorTarget.FILL_PATTERN -> updateFillPattern { it.copy(color = color or OPAQUE_ALPHA) }
            ColorTarget.SELECTED_NOTE -> recolorSelectedNotes(NoteOptions.argb(color, opacity ?: stickyOpacity))
            ColorTarget.TOOLBAR -> chooseToolbarColor(color or OPAQUE_ALPHA)
            ColorTarget.LIST_DOT -> updateNoteStyle { it.copy(list = (it.list ?: ListStyle(ListKind.DOT)).copy(dotColor = color or OPAQUE_ALPHA)) }
            ColorTarget.TEXT_COLOR -> setTextColor(color or OPAQUE_ALPHA)
            ColorTarget.TEXT_BACKGROUND -> updateNoteStyle { it.copy(backgroundColor = color or OPAQUE_ALPHA) }
            ColorTarget.PLANNER_ELEMENT -> plannerSlot?.let { restylePlanner(app.betternotes.core.planner.PlannerPages.StyleChange(it, color or OPAQUE_ALPHA)) }
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
            activeTool == ActiveTool.FILL -> CanvasTool.Fill(fillColor, fillOpacity, if (fillPatternOn) fillPattern else FillPattern.SOLID)
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
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val MIN_LASER_LENGTH = 1
        val RETURNING_TOOLS = listOf(ActiveTool.HIGHLIGHTER, ActiveTool.ERASER, ActiveTool.LASSO, ActiveTool.TEXT, ActiveTool.EMOJI, ActiveTool.TAPE)
        const val MIN_FILL_RETURN_TENTHS = 0
        const val MAX_FILL_RETURN_TENTHS = 100
        const val MAX_LASER_LENGTH = 5
        const val MIN_EMOJI = 12
        const val MAX_EMOJI = 96
        val LASER_SCALES = floatArrayOf(0.5f, 1f, 1.6f)
        private const val RECENT_EMOJI = 5
        private const val DEFAULT_EMOJI = 28
        const val MIN_FILL_OPACITY = 0.1f
        private const val AFTER_GESTURE_MS = 1000L
        private const val AFTER_TAP_MS = 4000L
        const val DEFAULT_FILL = Swatches.SHAPE_FILL_DEFAULT
    }
}

/** The four switches of a text's look that can differ from one part of it to another. */
enum class StyleKind { BOLD, ITALIC, UNDERLINE, STRIKE }

internal fun TextStyle.has(kind: StyleKind) = when (kind) {
    StyleKind.BOLD -> bold
    StyleKind.ITALIC -> italic
    StyleKind.UNDERLINE -> underline
    StyleKind.STRIKE -> strikethrough
}

internal fun TextStyle.toggled(kind: StyleKind) = when (kind) {
    StyleKind.BOLD -> copy(bold = !bold)
    StyleKind.ITALIC -> copy(italic = !italic)
    StyleKind.UNDERLINE -> copy(underline = !underline)
    StyleKind.STRIKE -> copy(strikethrough = !strikethrough)
}

/** The key of the sticker list's heading for stickers that are in no set, as a place to drop a sticker to take it out of its set. */
internal const val LOOSE_STICKERS = "\u0000loose"
