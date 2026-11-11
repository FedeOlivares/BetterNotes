package app.betternotes.core.planner

import app.betternotes.core.geometry.Bounds
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Told by the drawing where an element of a page begins and ends. All sizes are in the page's points; the key is in reference units. */
internal interface ElementHost {
    fun begin(role: PlannerRole, keyX: Int, keyY: Int, x0: Float, y0: Float, x1: Float, y1: Float, radius: Float)

    fun end()
}

/**
 * Sits between the drawing of a planner page and the sink that paints it. It styles what is drawn from the owner's [styles], and, when
 * [found] is given, lists the elements drawn (what the lasso can pick). With no styles and no list it is not used at all, so a planner the
 * owner has not changed is drawn exactly as before.
 *
 * An element is what the drawing wrapped in [begin] and [end] (a button, a block of rows), or else any single thing drawn outside one
 * (a text, a rule, a fill), whose role follows from what it is. [sx] and [sy] are page points per reference unit.
 */
internal class PlannerRoleSink(
    private val inner: TemplateSink,
    private val styles: PlannerRoleStyles,
    private val sx: Float,
    private val sy: Float,
    private val found: MutableList<PlannerElement>? = null,
) : TemplateSink, ElementHost {
    private class Scope(val style: RoleStyle?, val element: PlannerElement?, val plain: Boolean)

    private val parents = ArrayList<Scope?>()
    private var scope: Scope? = null

    override val language: String get() = inner.language

    // ---- Elements ------------------------------------------------------------------------------------------------

    override fun begin(role: PlannerRole, keyX: Int, keyY: Int, x0: Float, y0: Float, x1: Float, y1: Float, radius: Float) {
        parents.add(scope)
        if (role == PlannerRole.NONE) {
            scope = PLAIN
            return
        }
        val style = if (styles.isEmpty) null else styles.styleOf(role, keyX, keyY)
        val element = found?.let { list -> PlannerElement(role, ElementKey(role, keyX, keyY), Bounds(x0, y0, x1, y1)).also { list.add(it) } }
        scope = Scope(style, element, false)
        style?.background?.let { background(it, x0, y0, x1, y1, radius) }
    }

    override fun end() {
        scope = parents.removeAt(parents.lastIndex)
    }

    /** A solid color behind an element, with round corners of [radius]. */
    private fun background(color: Int, x0: Float, y0: Float, x1: Float, y1: Float, radius: Float) {
        val w = x1 - x0
        val h = y1 - y0
        val r = min(radius, min(w, h) / 2f)
        if (r <= 0f) {
            inner.fill(x0, y0, w, h, color)
            return
        }
        inner.fill(x0 + r, y0, w - 2f * r, h, color)
        inner.fill(x0, y0 + r, r, h - 2f * r, color)
        inner.fill(x1 - r, y0 + r, r, h - 2f * r, color)
        inner.dot(x0 + r, y0 + r, r, color)
        inner.dot(x1 - r, y0 + r, r, color)
        inner.dot(x1 - r, y1 - r, r, color)
        inner.dot(x0 + r, y1 - r, r, color)
    }

    /**
     * The style of something drawn at [l], [t] to [r], [b]: its element's when it is inside one, else a single-thing element of [role] is
     * made for it. Null when it is left as drawn.
     */
    private fun resolve(role: PlannerRole, l: Float, t: Float, r: Float, b: Float, text: Boolean, line: Boolean, shade: Boolean): RoleStyle? {
        val s = scope
        if (s != null) {
            if (s.plain) return null
            s.element?.let {
                if (text) it.hasText = true
                if (line) it.hasLine = true
                if (shade) it.hasShade = true
                // Text is picked by where it starts, a line or fill by its middle.
                if (text) it.addAnchor(l, (t + b) / 2f) else it.addAnchor((l + r) / 2f, (t + b) / 2f)
            }
            return s.style
        }
        if (role == PlannerRole.NONE) return null
        val kx = (l / sx).roundToInt()
        val ky = (t / sy).roundToInt()
        found?.let { list ->
            list.add(PlannerElement(role, ElementKey(role, kx, ky), Bounds(l, t, r, b)).also {
                it.hasText = text
                it.hasLine = line
                it.hasShade = shade
            })
        }
        return if (styles.isEmpty) null else styles.styleOf(role, kx, ky)
    }

    // ---- What is drawn -------------------------------------------------------------------------------------------

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val half = width / 2f
        val style = resolve(PlannerRole.RULE, min(x0, x1) - half, min(y0, y1) - half, max(x0, x1) + half, max(y0, y1) + half, text = false, line = true, shade = false)
        inner.line(x0, y0, x1, y1, width, style?.line ?: color)
    }

    override fun polyline(points: FloatArray, width: Float, color: Int) {
        val style = if (scope == null && points.size >= 2) resolve(PlannerRole.RULE, points[0], points[1], points[0], points[1], text = false, line = true, shade = false) else scopeStyle(line = true)
        inner.polyline(points, width, style?.line ?: color)
    }

    override fun dot(x: Float, y: Float, radius: Float, color: Int) {
        val style = scopeStyle(line = false)
        inner.dot(x, y, radius, style?.background ?: color)
    }

    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) {
        val seeThrough = (color ushr 24) != 0xFF
        val style = resolve(PlannerRole.SHADE, x, y, x + width, y + height, text = false, line = false, shade = seeThrough)
        inner.fill(x, y, width, height, (if (seeThrough) style?.shading else style?.background) ?: color)
    }

    override fun gradient(x: Float, y: Float, width: Float, height: Float, top: Int, bottom: Int) = inner.gradient(x, y, width, height, top, bottom)

    override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) {
        val w = text.length * size * (if (style == TextStyle.HEADING) HEADING_WIDTH else PLAIN_WIDTH)
        val l = when (align) {
            TextAlign.START -> x
            TextAlign.CENTER -> x - w / 2f
            TextAlign.END -> x - w
        }
        val role = when (style) {
            TextStyle.TITLE -> PlannerRole.PAGE_TITLE
            TextStyle.HEADING -> PlannerRole.SECTION_HEADING
            TextStyle.LABEL -> PlannerRole.LABEL
        }
        val s = resolve(role, l, baseline - size, l + w, baseline + size * DESCENT, text = true, line = false, shade = false)
        inner.styledText(text, x, baseline, size, s?.text ?: color, style, align, s?.font)
    }

    override fun turnedText(text: String, cx: Float, cy: Float, size: Float, degrees: Float, color: Int, style: TextStyle) {
        val w = text.length * size * (if (style == TextStyle.HEADING) HEADING_WIDTH else PLAIN_WIDTH) / 2f
        val role = if (style == TextStyle.HEADING) PlannerRole.SECTION_HEADING else PlannerRole.LABEL
        val s = resolve(role, cx - size, cy - w, cx + size, cy + w, text = true, line = false, shade = false)
        inner.styledTurnedText(text, cx, cy, size, degrees, s?.text ?: color, style, s?.font)
    }

    /** The style of the element being drawn, or null outside one. */
    private fun scopeStyle(line: Boolean): RoleStyle? {
        val s = scope ?: return null
        if (s.plain) return null
        if (line) s.element?.hasLine = true
        return s.style
    }

    private companion object {
        const val HEADING_WIDTH = 0.7f
        const val PLAIN_WIDTH = 0.55f
        const val DESCENT = 0.3f
        val PLAIN = Scope(null, null, true)
    }
}
