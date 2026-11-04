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
import app.betternotes.canvas.CanvasTool
import app.betternotes.canvas.CanvasView
import app.betternotes.canvas.LaserMode
import app.betternotes.canvas.TextEditRequest
import app.betternotes.canvas.VideoPlayRequest
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.model.AudioItem
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.TextStyle
import app.betternotes.core.tools.ALL_ITEM_KINDS
import app.betternotes.core.tools.EraserOptions
import app.betternotes.core.tools.ImageOptions
import app.betternotes.core.tools.InkPreset
import app.betternotes.core.tools.LassoOptions
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.core.tools.TapeOptions

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
    IMAGE(null),
    TAPE(null),
    EMOJI(null),
}

private val ActiveTool.hasPanel get() = ink != null || this != ActiveTool.TAPE

/** What the color wheel's result is for. */
internal enum class ColorTarget { CUSTOM_SLOT, SELECTED_SLOT, PALETTE, SHAPE_BORDER, SHAPE_FILL, NOTE, TABLE_BORDER, TABLE_FILL, LASER }

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
    var tape by mutableStateOf(store.tape())
        private set
    var emoji by mutableStateOf(EMOJI.first())
        private set
    var recentEmoji by mutableStateOf(EMOJI.take(RECENT_EMOJI))
        private set
    var emojiSize by mutableIntStateOf(DEFAULT_EMOJI)
        private set
    var playingVideo by mutableStateOf<VideoPlayRequest?>(null)
    var pagesOpen by mutableStateOf(false)
    var sidebarMenuOpen by mutableStateOf(false)

    /** A photo taken in scan mode, waiting for its corners to be set. */
    var scanPhoto by mutableStateOf<Uri?>(null)
    var moreMenuOpen by mutableStateOf(false)
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
    var zoom by mutableFloatStateOf(1f)
        private set

    /** Bumped each time the page moves, which shows the indicator for a while. */
    var viewMoves by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** The settings of the ink tool in use, or null if the tool in use lays no ink. */
    val preset: InkPreset? get() = activeTool.ink?.let { presets[it] }

    val screenDpi get() = canvas.viewport.screenDpi

    val canvasForRecorder get() = canvas

    init {
        canvas.onHistoryChanged = {
            canUndo = canvas.canUndo
            canRedo = canvas.canRedo
            historyVersion++
            pageCount = canvas.pageCount
        }
        canUndo = canvas.canUndo
        canRedo = canvas.canRedo
        canvas.onLassoRequested = { if (activeTool != ActiveTool.IMAGE && activeTool != ActiveTool.LASSO) select(ActiveTool.LASSO) }
        canvas.onTextEdit = { textEdit = it }
        canvas.onPlayVideo = { playingVideo = it }
        canvas.onViewMoved = ::refreshPosition
        canvas.onZoomChanged = { refreshPosition() }
        refreshPosition()
        applyTool()
    }

    fun select(tool: ActiveTool) {
        if (tool == activeTool) {
            if (tool.hasPanel) panelOpen = !panelOpen
            return
        }
        canvas.endTextEdit()
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
    }

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

    fun importImage(uri: Uri) = canvas.importImage(uri, image) { say(R.string.notice_image_failed) }

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

    fun pages() = canvas.pages

    fun addPage(after: Int) = canvas.addPage(after)

    fun deletePage(index: Int) = canvas.deletePage(index)

    fun movePage(from: Int, to: Int) = canvas.movePage(from, to)

    fun thumbnail(pageId: Long, maxPx: Int) = canvas.thumbnail(pageId, maxPx)

    fun goToPage(index: Int) {
        canvas.goToPage(index)
        pagesOpen = false
    }

    fun recordings() = canvas.recordings()

    fun assetFile(assetId: String) = canvas.assetFile(assetId)

    fun importAudio(uri: Uri) = canvas.importAudio(uri) { say(R.string.notice_audio_failed) }

    fun deleteRecording(item: AudioItem) = canvas.deleteAudio(item)

    /** The color the wheel opens on. */
    val colorStart: Int
        get() = when (colorTarget) {
            ColorTarget.SHAPE_BORDER -> shape.color
            ColorTarget.SHAPE_FILL, ColorTarget.TABLE_FILL -> DEFAULT_FILL
            ColorTarget.NOTE -> note.color
            ColorTarget.TABLE_BORDER -> table.color
            ColorTarget.LASER -> laserColor
            else -> preset?.color ?: DEFAULT_FILL
        }

    fun clearPage() = canvas.clearPage()

    fun deleteSelection() = canvas.deleteSelection()

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
            ColorTarget.SHAPE_BORDER -> updateShape { it.withColor(color) }
            ColorTarget.SHAPE_FILL -> updateShape { it.copy(fillColor = color) }
            ColorTarget.NOTE -> updateNote { it.withColor(color) }
            ColorTarget.TABLE_BORDER -> updateTable { it.withColor(color) }
            ColorTarget.TABLE_FILL -> updateTable { it.copy(fillColor = color) }
            ColorTarget.LASER -> selectLaserColor(color)
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

    fun previousPage() = canvas.goToPage(canvas.currentPage - 1)

    fun nextPage() = canvas.goToPage(canvas.currentPage + 1)

    fun zoomToRealSize() = canvas.zoomTo(1f)

    /** The document under the canvas changed, so the page count may have too. */
    fun refreshPosition() {
        currentPage = canvas.currentPage
        pageCount = canvas.pageCount
        zoom = canvas.viewport.zoom
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
        val current = preset
        canvas.tool = when {
            current != null -> CanvasTool.Ink(current.style(), current.holdToLineMs, current.scribbleToErase, current.circleToLasso, current.stabilization)
            activeTool == ActiveTool.LASER -> CanvasTool.Laser
            activeTool == ActiveTool.LASSO -> CanvasTool.Lasso(lasso.rectangle, lasso.kinds, lasso.fullSelectionOnly)
            activeTool == ActiveTool.SHAPE -> CanvasTool.Shape(shape)
            activeTool == ActiveTool.STICKY -> CanvasTool.Sticky(note)
            activeTool == ActiveTool.TEXT -> CanvasTool.Text(note)
            activeTool == ActiveTool.TABLE -> CanvasTool.Table(table)
            activeTool == ActiveTool.TAPE -> CanvasTool.Tape(tape)
            activeTool == ActiveTool.EMOJI -> CanvasTool.Emoji(emoji, emojiSize)
            activeTool == ActiveTool.IMAGE -> CanvasTool.Lasso(rectangle = true, ALL_ITEM_KINDS, fullOnly = false)
            else -> CanvasTool.Eraser(eraser.mode, eraser.radiusPt, eraser.kinds)
        }
    }

    companion object {
        const val MIN_LASER_LENGTH = 1
        const val MAX_LASER_LENGTH = 5
        const val MIN_EMOJI = 12
        const val MAX_EMOJI = 96
        val LASER_SCALES = floatArrayOf(0.5f, 1f, 1.6f)
        private const val RECENT_EMOJI = 5
        private const val DEFAULT_EMOJI = 28
        const val ALPHA_SHIFT = 24
        const val DEFAULT_FILL = 0xFF5F9FFB.toInt()
    }
}
