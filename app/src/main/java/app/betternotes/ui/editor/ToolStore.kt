package app.betternotes.ui.editor

import app.betternotes.core.model.FillPattern
import app.betternotes.core.model.FillPatternKind
import android.content.Context
import androidx.core.content.edit
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.PenType
import app.betternotes.core.model.HorizontalAlign
import app.betternotes.core.model.ImageShape
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.TextStyle
import app.betternotes.core.model.VerticalAlign
import app.betternotes.core.model.TipShape
import app.betternotes.core.tools.EraserOptions
import app.betternotes.core.tools.ImageOptions
import app.betternotes.core.tools.InkPreset
import app.betternotes.core.tools.ToolbarEntries
import app.betternotes.core.tools.ToolbarGroups
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.LassoOptions
import app.betternotes.core.model.ListKind
import app.betternotes.core.model.ListStyle
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.core.model.TapePattern
import app.betternotes.core.tools.TapeLineMode
import app.betternotes.core.tools.TapeOptions
import app.betternotes.ui.theme.Swatches
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Whether a tool gives the tool in use before back once it has been used, and after how many tenths of a second. */
internal data class ToolReturn(val on: Boolean = true, val tenths: Int = 0)

/** Remembers how each tool was left, and the theme, between launches. */
internal class ToolStore(context: Context) {
    private val prefs = context.getSharedPreferences("editor_tools", Context.MODE_PRIVATE)

    init {
        addFillKind()
        turnHelpersOn()
    }

    /**
     * Circle to lasso, pressure and scribble to erase became on by default, but every saved ink tool carries all three values, so an
     * untouched one cannot be told from one switched off. Once, a tool with all three off, as the old defaults were, gets them on.
     * A tool with any of them on was changed by the owner and stays as it is, and after this the owner's choices stand.
     */
    private fun turnHelpersOn() {
        if (prefs.getBoolean(KEY_HELPERS_ON, false)) return
        prefs.edit {
            for (tool in InkTool.entries) {
                val saved = prefs.getString(tool.name, null) ?: continue
                try {
                    val json = JSONObject(saved)
                    if (!json.optBoolean("circleToLasso") && !json.optBoolean("scribbleToErase") && !json.optBoolean("pressure")) {
                        json.put("circleToLasso", true).put("scribbleToErase", true).put("pressure", true)
                        putString(tool.name, json.toString())
                    }
                } catch (_: JSONException) {
                    // A tool that cannot be read is replaced by the default, which has them on.
                }
            }
            putBoolean(KEY_HELPERS_ON, true)
        }
    }

    /**
     * Paint bucket fills arrived after the eraser and the lasso had saved which kinds of item they act on, so those lists lack them, and a
     * lasso round a drawing left its colors behind. Once, the fill is added to both lists. After that the owner's choice stands.
     */
    private fun addFillKind() {
        if (prefs.getBoolean(KEY_FILL_KIND_ADDED, false)) return
        prefs.edit {
            for (key in listOf(KEY_ERASER, KEY_LASSO)) {
                val saved = prefs.getString(key, null) ?: continue
                try {
                    val json = JSONObject(saved)
                    val kinds = json.getJSONArray("kinds")
                    if ((0 until kinds.length()).none { kinds.getString(it) == ItemKind.FILL.name }) {
                        kinds.put(ItemKind.FILL.name)
                        putString(key, json.toString())
                    }
                } catch (_: JSONException) {
                    // A list that cannot be read is replaced by the default, which has the fill.
                }
            }
            putBoolean(KEY_FILL_KIND_ADDED, true)
        }
    }

    fun preset(tool: InkTool): InkPreset {
        val saved = prefs.getString(tool.name, null) ?: return InkPreset.defaults(tool)
        return try {
            decode(tool, JSONObject(saved))
        } catch (_: JSONException) {
            InkPreset.defaults(tool)
        } catch (_: IllegalArgumentException) {
            InkPreset.defaults(tool)
        }
    }

    fun save(preset: InkPreset) {
        prefs.edit { putString(preset.tool.name, encode(preset).toString()) }
    }

    /** The color of the toolbars, or null when the owner has not chosen one. */
    fun toolbarColor(): Int? = if (prefs.contains(KEY_TOOLBAR_COLOR)) prefs.getInt(KEY_TOOLBAR_COLOR, 0) else null

    fun saveToolbarColor(color: Int?) {
        prefs.edit { if (color == null) remove(KEY_TOOLBAR_COLOR) else putInt(KEY_TOOLBAR_COLOR, color) }
    }

    /** Which screen edge the toolbars sit on. */
    fun toolbarEdge(): ToolbarEdge = ToolbarEdge.entries.firstOrNull { it.name == prefs.getString(KEY_TOOLBAR_EDGE, null) } ?: ToolbarEdge.TOP

    fun saveToolbarEdge(edge: ToolbarEdge) {
        prefs.edit { putString(KEY_TOOLBAR_EDGE, edge.name) }
    }

    /** The arrangement of the strip's tools, as their keys with groups in it, and the keys of the ones the owner hid. Empty until chosen. */
    fun toolLayout(): ToolbarEntries = ToolbarGroups.decode(prefs.getString(KEY_TOOL_ORDER, null))

    fun saveToolLayout(layout: ToolbarEntries) {
        prefs.edit { putString(KEY_TOOL_ORDER, ToolbarGroups.encode(layout)) }
    }

    fun hiddenTools(): Set<String> = prefs.getString(KEY_TOOLS_HIDDEN, null).orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    fun saveHiddenTools(hidden: Set<String>) {
        prefs.edit { putString(KEY_TOOLS_HIDDEN, hidden.joinToString(",")) }
    }

    fun moveToolbar(): Boolean = prefs.getBoolean(KEY_MOVE_TOOLBAR, false)

    fun saveMoveToolbar(on: Boolean) {
        prefs.edit { putBoolean(KEY_MOVE_TOOLBAR, on) }
    }

    /** The grid overlay: whether it is on, and which of its two patterns show. */
    fun grid(): GridOptions = GridOptions(
        on = prefs.getBoolean(KEY_GRID_ON, false),
        cross = prefs.getBoolean(KEY_GRID_CROSS, true),
        thirds = prefs.getBoolean(KEY_GRID_THIRDS, false),
    )

    fun saveGrid(grid: GridOptions) {
        prefs.edit {
            putBoolean(KEY_GRID_ON, grid.on)
            putBoolean(KEY_GRID_CROSS, grid.cross)
            putBoolean(KEY_GRID_THIRDS, grid.thirds)
        }
    }

    /** The paint bucket's color and opacity. */
    fun fillColor(): Int = prefs.getInt(KEY_FILL_COLOR, DEFAULT_FILL_COLOR)

    fun fillOpacity(): Float = prefs.getFloat(KEY_FILL_OPACITY, 1f)

    /** The pattern the paint bucket draws over its color; solid, the default, is the color alone. */
    fun fillPattern(): FillPattern = FillPattern(
        kind = FillPatternKind.entries.firstOrNull { it.name == prefs.getString(KEY_PATTERN_KIND, null) } ?.takeIf { it != FillPatternKind.SOLID } ?: FillPatternKind.DOTS,
        color = prefs.getInt(KEY_PATTERN_COLOR, DEFAULT_PATTERN_COLOR),
        spacing = prefs.getFloat(KEY_PATTERN_SPACING, FillPattern.DEFAULT_SPACING).coerceIn(FillPattern.MIN_SPACING, FillPattern.MAX_SPACING),
        weight = prefs.getFloat(KEY_PATTERN_WEIGHT, FillPattern.DEFAULT_WEIGHT).coerceIn(FillPattern.MIN_WEIGHT, FillPattern.MAX_WEIGHT),
    )

    /** Whether the paint bucket draws a pattern at all. Off, the default, the fill is the plain color and the pattern choices stay hidden. */
    fun fillPatternOn(): Boolean = prefs.getBoolean(KEY_PATTERN_ON, false)

    fun saveFillPatternOn(on: Boolean) {
        prefs.edit { putBoolean(KEY_PATTERN_ON, on) }
    }

    fun saveFillPattern(pattern: FillPattern) {
        prefs.edit {
            putString(KEY_PATTERN_KIND, pattern.kind.name)
            putInt(KEY_PATTERN_COLOR, pattern.color)
            putFloat(KEY_PATTERN_SPACING, pattern.spacing)
            putFloat(KEY_PATTERN_WEIGHT, pattern.weight)
        }
    }

    /** Whether the paint bucket gives the tool in use before back after a while, and how long that is, in tenths of a second. */
    fun fillReturn(): Boolean = prefs.getBoolean(KEY_FILL_RETURN, true)

    fun fillReturnDelay(): Int = prefs.getInt(KEY_FILL_RETURN_DELAY, DEFAULT_FILL_RETURN_DELAY)

    fun saveFillReturn(on: Boolean, delayTenths: Int) {
        prefs.edit {
            putBoolean(KEY_FILL_RETURN, on)
            putInt(KEY_FILL_RETURN_DELAY, delayTenths)
        }
    }

    /** Whether shapes, notes, tables and pictures give the tool in use before back once one is put down, and after how long, in tenths of a second. */
    fun createReturn(): Boolean = prefs.getBoolean(KEY_CREATE_RETURN, true)

    fun createReturnDelay(): Int = prefs.getInt(KEY_CREATE_RETURN_DELAY, 0)

    fun saveCreateReturn(on: Boolean, delayTenths: Int) {
        prefs.edit {
            putBoolean(KEY_CREATE_RETURN, on)
            putInt(KEY_CREATE_RETURN_DELAY, delayTenths)
        }
    }

    /** The same for the highlighter, eraser, lasso, text, tape and sticker tools, each on its own. Untouched, it is on and immediate, except the highlighter and the eraser, which are off, and the tape, which waits a second. */
    fun toolReturn(tool: ActiveTool) = ToolReturn(
        prefs.getBoolean("return_${tool.name}", tool != ActiveTool.HIGHLIGHTER && tool != ActiveTool.ERASER),
        prefs.getInt("return_delay_${tool.name}", if (tool == ActiveTool.TAPE) TAPE_RETURN_TENTHS else 0),
    )

    fun saveToolReturn(tool: ActiveTool, choice: ToolReturn) {
        prefs.edit {
            putBoolean("return_${tool.name}", choice.on)
            putInt("return_delay_${tool.name}", choice.tenths)
        }
    }

    /** The colors on the paint bucket's bar, in the order the owner arranged them. */
    fun fillColors(): List<Int> = prefs.getString(KEY_FILL_COLORS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_FILL_COLORS

    fun saveFillColors(colors: List<Int>) {
        prefs.edit { putString(KEY_FILL_COLORS, colors.joinToString(",")) }
    }

    fun saveFill(color: Int, opacity: Float) {
        prefs.edit {
            putInt(KEY_FILL_COLOR, color)
            putFloat(KEY_FILL_OPACITY, opacity)
        }
    }

    fun eraser(): EraserOptions = read(KEY_ERASER, EraserOptions()) { json ->
        EraserOptions(
            sizes = json.getJSONArray("sizes").ints(),
            selectedSize = json.getInt("selectedSize"),
            whole = json.getBoolean("whole"),
            lockedToo = json.getBoolean("lockedToo"),
            autoDeselect = json.getBoolean("autoDeselect"),
            kinds = json.getJSONArray("kinds").kinds(),
        )
    }

    fun save(options: EraserOptions) {
        val json = JSONObject().apply {
            put("sizes", JSONArray(options.sizes))
            put("selectedSize", options.selectedSize)
            put("whole", options.whole)
            put("lockedToo", options.lockedToo)
            put("autoDeselect", options.autoDeselect)
            put("kinds", JSONArray(options.kinds.map { it.name }))
        }
        prefs.edit { putString(KEY_ERASER, json.toString()) }
    }

    fun lasso(): LassoOptions = read(KEY_LASSO, LassoOptions()) { json ->
        LassoOptions(
            rectangle = json.getBoolean("rectangle"),
            lockedToo = json.getBoolean("lockedToo"),
            takeScreenshot = json.getBoolean("takeScreenshot"),
            fullSelectionOnly = json.getBoolean("fullSelectionOnly"),
            anyPortion = json.optBoolean("anyPortion", false),
            kinds = json.getJSONArray("kinds").kinds(),
        )
    }

    fun save(options: LassoOptions) {
        val json = JSONObject().apply {
            put("rectangle", options.rectangle)
            put("lockedToo", options.lockedToo)
            put("takeScreenshot", options.takeScreenshot)
            put("fullSelectionOnly", options.fullSelectionOnly)
            put("anyPortion", options.anyPortion)
            put("kinds", JSONArray(options.kinds.map { it.name }))
        }
        prefs.edit { putString(KEY_LASSO, json.toString()) }
    }

    fun shape(): ShapeOptions = read(KEY_SHAPE, ShapeOptions()) { json ->
        ShapeOptions(
            kind = ShapeKind.valueOf(json.getString("kind")),
            recentKinds = json.getJSONArray("recentKinds").let { a -> List(a.length()) { ShapeKind.valueOf(a.getString(it)) } },
            widths = json.getJSONArray("widths").ints(),
            selectedWidth = json.getInt("selectedWidth"),
            colors = json.getJSONArray("colors").ints(),
            selectedColor = json.getInt("selectedColor"),
            fillColor = json.getInt("fillColor"),
            line = LineKind.valueOf(json.getString("line")),
            dashLength = json.getDouble("dashLength").toFloat(),
            cornerRadius = json.getInt("cornerRadius"),
        )
    }

    fun save(options: ShapeOptions) {
        val json = JSONObject().apply {
            put("kind", options.kind.name)
            put("recentKinds", JSONArray(options.recentKinds.map { it.name }))
            put("widths", JSONArray(options.widths))
            put("selectedWidth", options.selectedWidth)
            put("colors", JSONArray(options.colors))
            put("selectedColor", options.selectedColor)
            put("fillColor", options.fillColor)
            put("line", options.line.name)
            put("dashLength", options.dashLength.toDouble())
            put("cornerRadius", options.cornerRadius)
        }
        prefs.edit { putString(KEY_SHAPE, json.toString()) }
    }

    fun note(): NoteOptions = read(KEY_NOTE, NoteOptions()) { json ->
        NoteOptions(
            colors = json.getJSONArray("colors").ints(),
            selectedColor = json.getInt("selectedColor"),
            textSize = json.getInt("textSize"),
            style = TextStyle(
                bold = json.getBoolean("bold"),
                italic = json.getBoolean("italic"),
                underline = json.getBoolean("underline"),
                strikethrough = json.getBoolean("strike"),
                align = HorizontalAlign.valueOf(json.getString("align")),
                verticalAlign = VerticalAlign.valueOf(json.getString("verticalAlign")),
                backgroundColor = json.optInt("backgroundColor", 0),
                list = json.optJSONObject("list")?.let { list ->
                    ListStyle(
                        kind = runCatching { ListKind.valueOf(list.getString("kind")) }.getOrDefault(ListKind.BULLET),
                        dotColor = list.optInt("dotColor", ListStyle.DEFAULT_DOT_COLOR),
                        dotSize = list.optDouble("dotSize", ListStyle.DEFAULT_DOT_SIZE.toDouble()).toFloat(),
                        dotOpacity = list.optDouble("dotOpacity", 1.0).toFloat(),
                    ).clamped()
                },
            ),
            textColor = json.optInt("textColor", NoteOptions.TEXT_COLOR),
            keepOpen = json.optBoolean("keepOpen", false),
            colorOpacity = json.optJSONArray("colorOpacity")?.let { pairs ->
                (0 until pairs.length()).associate { pairs.getJSONArray(it).let { pair -> pair.getInt(0) to pair.getDouble(1).toFloat() } }
            }.orEmpty(),
        )
    }

    fun save(options: NoteOptions) {
        val json = JSONObject().apply {
            put("colors", JSONArray(options.colors))
            put("selectedColor", options.selectedColor)
            put("textSize", options.textSize)
            put("bold", options.style.bold)
            put("italic", options.style.italic)
            put("underline", options.style.underline)
            put("strike", options.style.strikethrough)
            put("align", options.style.align.name)
            put("verticalAlign", options.style.verticalAlign.name)
            put("keepOpen", options.keepOpen)
            put("textColor", options.textColor)
            put("backgroundColor", options.style.backgroundColor)
            options.style.list?.let { list ->
                put(
                    "list",
                    JSONObject().put("kind", list.kind.name).put("dotColor", list.dotColor)
                        .put("dotSize", list.dotSize.toDouble()).put("dotOpacity", list.dotOpacity.toDouble()),
                )
            }
            if (options.colorOpacity.isNotEmpty()) {
                put("colorOpacity", JSONArray(options.colorOpacity.map { (rgb, opacity) -> JSONArray().put(rgb).put(opacity.toDouble()) }))
            }
        }
        prefs.edit { putString(KEY_NOTE, json.toString()) }
    }

    fun table(): TableOptions = read(KEY_TABLE, TableOptions()) { json ->
        TableOptions(
            columns = json.getInt("columns"),
            rows = json.getInt("rows"),
            headerColumn = json.getBoolean("headerColumn"),
            headerRow = json.getBoolean("headerRow"),
            borderWidth = json.getInt("borderWidth"),
            colors = json.getJSONArray("colors").ints(),
            selectedColor = json.getInt("selectedColor"),
            fillColor = json.getInt("fillColor"),
            headerColor = json.optInt("headerColor", 0),
        )
    }

    fun save(options: TableOptions) {
        val json = JSONObject().apply {
            put("columns", options.columns)
            put("rows", options.rows)
            put("headerColumn", options.headerColumn)
            put("headerRow", options.headerRow)
            put("borderWidth", options.borderWidth)
            put("colors", JSONArray(options.colors))
            put("selectedColor", options.selectedColor)
            put("fillColor", options.fillColor)
            put("headerColor", options.headerColor)
        }
        prefs.edit { putString(KEY_TABLE, json.toString()) }
    }

    fun image(): ImageOptions = read(KEY_IMAGE, ImageOptions()) { json ->
        ImageOptions(
            shape = ImageShape.valueOf(json.getString("shape")),
            cornerRadius = json.getInt("cornerRadius"),
            borderWidth = json.getInt("borderWidth"),
            borderColor = json.getInt("borderColor"),
        )
    }

    fun save(options: ImageOptions) {
        val json = JSONObject().apply {
            put("shape", options.shape.name)
            put("cornerRadius", options.cornerRadius)
            put("borderWidth", options.borderWidth)
            put("borderColor", options.borderColor)
        }
        prefs.edit { putString(KEY_IMAGE, json.toString()) }
    }

    fun tape(): TapeOptions = read(KEY_TAPE, TapeOptions()) { json ->
        TapeOptions(
            widths = json.getJSONArray("widths").ints(),
            selectedWidth = json.getInt("selectedWidth"),
            colors = json.getJSONArray("colors").ints(),
            selectedColor = json.getInt("selectedColor"),
            opacity = json.optDouble("opacity", 1.0).toFloat(),
            pattern = TapePattern.entries.firstOrNull { it.name == json.optString("pattern") } ?: TapePattern.PLAIN,
            shadowOn = json.optBoolean("shadowOn", true),
            shadowDistance = json.optDouble("shadowDistance", TapeOptions.DEFAULT_SHADOW_DISTANCE.toDouble()).toFloat(),
            shadowAngle = json.optDouble("shadowAngle", TapeOptions.DEFAULT_SHADOW_ANGLE.toDouble()).toFloat(),
            shadowOpacity = json.optDouble("shadowOpacity", TapeOptions.DEFAULT_SHADOW_OPACITY.toDouble()).toFloat(),
            lineMode = TapeLineMode.entries.firstOrNull { it.name == json.optString("lineMode") } ?: TapeLineMode.ALWAYS,
            holdMs = json.optInt("holdMs", TapeOptions.DEFAULT_HOLD_MS),
        )
    }

    fun save(options: TapeOptions) {
        val json = JSONObject().apply {
            put("widths", JSONArray(options.widths))
            put("selectedWidth", options.selectedWidth)
            put("colors", JSONArray(options.colors))
            put("selectedColor", options.selectedColor)
            put("opacity", options.opacity.toDouble())
            put("pattern", options.pattern.name)
            put("shadowOn", options.shadowOn)
            put("shadowDistance", options.shadowDistance.toDouble())
            put("shadowAngle", options.shadowAngle.toDouble())
            put("shadowOpacity", options.shadowOpacity.toDouble())
            put("lineMode", options.lineMode.name)
            put("holdMs", options.holdMs)
        }
        prefs.edit { putString(KEY_TAPE, json.toString()) }
    }

    /** Anything saved by an older build that no longer reads back gives way to [fallback]. */
    private inline fun <T> read(key: String, fallback: T, decode: (JSONObject) -> T): T {
        val saved = prefs.getString(key, null) ?: return fallback
        return try {
            decode(JSONObject(saved))
        } catch (_: JSONException) {
            fallback
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    private fun encode(preset: InkPreset) = JSONObject().apply {
        put("colors", JSONArray(preset.colors))
        put("widths", JSONArray(preset.widths))
        put("palette", JSONArray(preset.palette))
        put("selectedColor", preset.selectedColor)
        put("selectedWidth", preset.selectedWidth)
        put("penType", preset.penType.name)
        put("tip", preset.tip.name)
        put("opacity", preset.opacity.toDouble())
        put("line", preset.line.name)
        put("holdToLine", preset.holdToLine)
        put("holdDelayMs", preset.holdDelayMs)
        put("scribbleToErase", preset.scribbleToErase)
        put("circleToLasso", preset.circleToLasso)
        put("stabilizationLevel", preset.stabilization)
        put("brush", preset.brush.name)
        put("noOverlap", preset.noOverlap)
        put("pressure", preset.pressure)
    }

    private fun decode(tool: InkTool, json: JSONObject) = InkPreset(
        tool = tool,
        colors = json.getJSONArray("colors").ints(),
        widths = json.getJSONArray("widths").ints(),
        palette = json.getJSONArray("palette").ints(),
        selectedColor = json.getInt("selectedColor"),
        selectedWidth = json.getInt("selectedWidth"),
        penType = PenType.valueOf(json.getString("penType")),
        tip = TipShape.valueOf(json.getString("tip")),
        opacity = json.getDouble("opacity").toFloat(),
        line = LineKind.valueOf(json.getString("line")),
        holdToLine = json.getBoolean("holdToLine"),
        holdDelayMs = json.getInt("holdDelayMs"),
        scribbleToErase = json.getBoolean("scribbleToErase"),
        circleToLasso = json.getBoolean("circleToLasso"),
        // Saved tools from before the 0 to 10 scale have the old 1 to 5 under "stabilization".
        stabilization = if (json.has("stabilizationLevel")) json.getInt("stabilizationLevel") else (json.optInt("stabilization", 1) - 1) * 2,
        brush = BrushKind.entries.firstOrNull { it.name == json.optString("brush") } ?: BrushKind.PENCIL,
        noOverlap = json.optBoolean("noOverlap", false),
        pressure = json.optBoolean("pressure", InkPreset.defaults(tool).pressure),
    )

    private fun JSONArray.ints() = List(length()) { getInt(it) }

    private fun JSONArray.kinds() = List(length()) { ItemKind.valueOf(getString(it)) }.toSet()

    private companion object {
        /** The tape waits a second, in tenths, before the tool in use before comes back. */
        const val TAPE_RETURN_TENTHS = 10

        const val KEY_ERASER = "eraser"
        const val KEY_LASSO = "lasso"
        const val KEY_SHAPE = "shape"
        const val KEY_NOTE = "note"
        const val KEY_IMAGE = "image"
        const val KEY_TAPE = "tape"
        const val KEY_TABLE = "table"
        const val KEY_TOOLBAR_COLOR = "toolbar_color"
        const val KEY_FILL_KIND_ADDED = "fill_kind_added"
        const val KEY_HELPERS_ON = "helpers_on"
        const val KEY_FILL_COLOR = "fill_color"
        const val KEY_FILL_COLORS = "fill_colors"
        const val KEY_FILL_OPACITY = "fill_opacity"
        const val KEY_PATTERN_ON = "fill_pattern_on"
        const val KEY_PATTERN_KIND = "fill_pattern_kind"
        const val KEY_PATTERN_COLOR = "fill_pattern_color"
        const val KEY_PATTERN_SPACING = "fill_pattern_spacing"
        const val KEY_PATTERN_WEIGHT = "fill_pattern_weight"
        val DEFAULT_PATTERN_COLOR = Swatches.PATTERN_DEFAULT
        val DEFAULT_FILL_COLOR = Swatches.FILL_DEFAULT
        val DEFAULT_FILL_COLORS = Swatches.Fill
        const val KEY_TOOLBAR_EDGE = "toolbar_edge"
        const val KEY_FILL_RETURN = "fill_return"
        const val KEY_FILL_RETURN_DELAY = "fill_return_delay"
        const val KEY_CREATE_RETURN = "create_return"
        const val KEY_CREATE_RETURN_DELAY = "create_return_delay"
        const val DEFAULT_FILL_RETURN_DELAY = 20
        const val KEY_TOOL_ORDER = "tool_order"
        const val KEY_TOOLS_HIDDEN = "tools_hidden"
        const val KEY_MOVE_TOOLBAR = "toolbar_move"
        const val KEY_GRID_ON = "grid_on"
        const val KEY_GRID_CROSS = "grid_cross"
        const val KEY_GRID_THIRDS = "grid_thirds"
    }
}
