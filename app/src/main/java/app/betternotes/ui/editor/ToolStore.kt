package app.betternotes.ui.editor

import android.content.Context
import app.betternotes.core.model.InkTool
import app.betternotes.core.model.LineKind
import app.betternotes.core.model.PenType
import app.betternotes.core.model.TipShape
import app.betternotes.core.tools.InkPreset
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
        prefs.edit().putString(preset.tool.name, encode(preset).toString()).apply()
    }

    var theme: ThemeMode
        get() = if (prefs.getString(KEY_THEME, null) == ThemeMode.LIGHT.name) ThemeMode.LIGHT else ThemeMode.DARK
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
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

    private companion object {
        const val KEY_THEME = "theme"
    }
}
