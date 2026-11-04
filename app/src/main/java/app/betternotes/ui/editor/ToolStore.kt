package app.betternotes.ui.editor

import android.content.Context
import androidx.core.content.edit
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
import app.betternotes.core.tools.ItemKind
import app.betternotes.core.tools.LassoOptions
import app.betternotes.core.tools.NoteOptions
import app.betternotes.core.tools.ShapeOptions
import app.betternotes.core.tools.TableOptions
import app.betternotes.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Remembers how each tool was left, and the theme, between launches. */
internal class ToolStore(context: Context) {
    private val prefs = context.getSharedPreferences("editor_tools", Context.MODE_PRIVATE)

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
            kinds = json.getJSONArray("kinds").kinds(),
        )
    }

    fun save(options: LassoOptions) {
        val json = JSONObject().apply {
            put("rectangle", options.rectangle)
            put("lockedToo", options.lockedToo)
            put("takeScreenshot", options.takeScreenshot)
            put("fullSelectionOnly", options.fullSelectionOnly)
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
            ),
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

    var theme: ThemeMode
        get() = if (prefs.getString(KEY_THEME, null) == ThemeMode.LIGHT.name) ThemeMode.LIGHT else ThemeMode.DARK
        set(value) {
            prefs.edit { putString(KEY_THEME, value.name) }
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
        put("stabilization", preset.stabilization)
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
        stabilization = json.getInt("stabilization"),
    )

    private fun JSONArray.ints() = List(length()) { getInt(it) }

    private fun JSONArray.kinds() = List(length()) { ItemKind.valueOf(getString(it)) }.toSet()

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ERASER = "eraser"
        const val KEY_LASSO = "lasso"
        const val KEY_SHAPE = "shape"
        const val KEY_NOTE = "note"
        const val KEY_IMAGE = "image"
        const val KEY_TABLE = "table"
    }
}
