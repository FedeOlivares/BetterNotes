package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.PageItem
import app.betternotes.core.model.PathShapeItem
import app.betternotes.core.model.ShapeItem
import app.betternotes.core.model.ShapeKind
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePoints
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** What a hand-drawn stroke was closest to, in page points. */
sealed interface FittedShape {
    /** An ellipse or a rectangle, [degrees] turned about its center. */
    data class Box(val kind: ShapeKind, val cx: Float, val cy: Float, val width: Float, val height: Float, val degrees: Float) : FittedShape

    /** A straight line, or an arrow with its head at ([x1], [y1]). A head of 0 length is a plain line. */
    data class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val headLength: Float = 0f, val headWidth: Float = 0f) : FittedShape

    /** Corners, or the control points of a smooth curve, as packed x,y pairs. */
    data class Path(val points: FloatArray, val closed: Boolean, val smooth: Boolean) : FittedShape
}

/**
 * Finds the geometric shape a finished stroke is closest to: ellipse, rectangle, triangle or other polygon when it
 * closes on itself, line, arrow or smooth curve when it does not. It answers null when nothing fits well, so the
 * stroke stays as drawn. All distances are relative to the stroke's own size, so a small and a large drawing are
 * judged alike.
 */
object ShapeFit {
    private const val MIN_LENGTH_PT = 30f
    private const val RESAMPLE_POINTS = 96
    private const val CLOSED_GAP = 0.22f
    private const val ELLIPSE_TOLERANCE = 0.075f
    private const val RECT_TOLERANCE = 0.06f
    private const val POLYGON_TOLERANCE = 0.05f
    private const val LINE_TOLERANCE = 0.035f
    private const val SIMPLIFY = 0.07f
    private const val CURVE_SIMPLIFY = 0.03f
    private const val MAX_CORNERS = 8
    private const val CURVE_MIN_POINTS = 3
    private const val CURVE_MAX_POINTS = 6
    private const val CURVE_MEAN = 0.018f
    private const val CURVE_WORST = 0.05f
    private const val AXIS_SNAP_DEGREES = 10f

    fun fit(points: StrokePoints): FittedShape? = fit(packed(points))

    /** [path] is packed x,y pairs. */
    fun fit(path: FloatArray): FittedShape? {
        if (path.size < 8) return null
        val length = pathLength(path)
        if (length < MIN_LENGTH_PT) return null
        val gap = hypot(path[path.size - 2] - path[0], path[path.size - 1] - path[1])
        val closed = gap <= CLOSED_GAP * length
        val even = resample(path, closed)
        return if (closed) fitClosed(even) else fitOpen(even, length)
    }

    // ---- Closed ----

    private fun fitClosed(p: FloatArray): FittedShape? {
        val diag = diagonal(p)
        if (diag <= 0f) return null
        val ellipse = fitEllipse(p)
        val rect = fitRect(p)
        val polygon = fitPolygon(p, diag)
        // Corners decide first: three or four clear ones are never an ellipse.
        val corners = polygon?.points?.size?.div(2) ?: 0
        if (corners == 3 && polygon != null && cornerError(p, polygon.points, true) / diag <= POLYGON_TOLERANCE) return polygon
        if (corners == 4 && rect != null && rect.second / diag <= RECT_TOLERANCE) return rect.first
        if (corners == 4 && polygon != null && cornerError(p, polygon.points, true) / diag <= POLYGON_TOLERANCE) return polygon
        if (ellipse != null && ellipse.second <= ELLIPSE_TOLERANCE) return ellipse.first
        if (corners in 5..MAX_CORNERS && polygon != null && cornerError(p, polygon.points, true) / diag <= POLYGON_TOLERANCE) return polygon
        return null
    }

    /**
     * The ellipse that best fits: for each tilt the box around the points gives the axes, and the tilt where the points sit
     * closest to that ellipse's rim wins. Error is the mean radial miss in units of the ellipse's own size.
     */
    private fun fitEllipse(p: FloatArray): Pair<FittedShape.Box, Float>? {
        var best: FittedShape.Box? = null
        var bestError = Float.MAX_VALUE
        var degrees = 0f
        while (degrees < 180f) {
            val candidate = ellipseAt(p, degrees)
            if (candidate != null && candidate.second < bestError) {
                best = candidate.first
                bestError = candidate.second
            }
            degrees += 5f
        }
        val start = best?.degrees ?: return null
        var step = 2.5f
        var at = start
        while (step > 0.3f) {
            for (d in floatArrayOf(at - step, at + step)) {
                val candidate = ellipseAt(p, d) ?: continue
                if (candidate.second < bestError) {
                    best = candidate.first
                    bestError = candidate.second
                    at = d
                }
            }
            step /= 2f
        }
        val found = best ?: return null
        // Near upright, or a circle, the axes line up with the page.
        val tilt = quarterTilt(found.degrees)
        val round = abs(found.width - found.height) < 0.06f * maxOf(found.width, found.height)
        if (tilt <= AXIS_SNAP_DEGREES || round) ellipseAt(p, 0f)?.let { return it.first to bestError }
        return found to bestError
    }

    private fun ellipseAt(p: FloatArray, degrees: Float): Pair<FittedShape.Box, Float>? {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var i = 0
        while (i < p.size) {
            val u = p[i] * c + p[i + 1] * s
            val v = -p[i] * s + p[i + 1] * c
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            if (v < minV) minV = v
            if (v > maxV) maxV = v
            i += 2
        }
        val rx = (maxU - minU) / 2f
        val ry = (maxV - minV) / 2f
        if (rx < 1f || ry < 1f) return null
        val cu = (maxU + minU) / 2f
        val cv = (maxV + minV) / 2f
        var sum = 0f
        i = 0
        while (i < p.size) {
            val u = p[i] * c + p[i + 1] * s
            val v = -p[i] * s + p[i + 1] * c
            sum += abs(hypot((u - cu) / rx, (v - cv) / ry) - 1f)
            i += 2
        }
        val cx = cu * c - cv * s
        val cy = cu * s + cv * c
        return FittedShape.Box(ShapeKind.ELLIPSE, cx, cy, rx * 2f, ry * 2f, normalized(degrees)) to sum / (p.size / 2)
    }

    /** The turned rectangle around the points where they hug its sides most; error is the mean distance to the nearest side, in points. */
    private fun fitRect(p: FloatArray): Pair<FittedShape.Box, Float>? {
        var best: FittedShape.Box? = null
        var bestError = Float.MAX_VALUE
        var degrees = 0f
        while (degrees < 90f) {
            val candidate = rectAt(p, degrees)
            if (candidate != null && candidate.second < bestError) {
                best = candidate.first
                bestError = candidate.second
            }
            degrees += 3f
        }
        val start = best?.degrees ?: return null
        var step = 1.5f
        var at = start
        while (step > 0.2f) {
            for (d in floatArrayOf(at - step, at + step)) {
                val candidate = rectAt(p, d) ?: continue
                if (candidate.second < bestError) {
                    best = candidate.first
                    bestError = candidate.second
                    at = d
                }
            }
            step /= 2f
        }
        val found = best ?: return null
        // Within ten degrees of upright, the sides are made fully vertical and horizontal.
        val tilt = quarterTilt(found.degrees)
        if (tilt <= AXIS_SNAP_DEGREES) rectAt(p, 0f)?.let { return it.first to bestError }
        return found to bestError
    }

    private fun rectAt(p: FloatArray, degrees: Float): Pair<FittedShape.Box, Float>? {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var i = 0
        while (i < p.size) {
            val u = p[i] * c + p[i + 1] * s
            val v = -p[i] * s + p[i + 1] * c
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            if (v < minV) minV = v
            if (v > maxV) maxV = v
            i += 2
        }
        val w = maxU - minU
        val h = maxV - minV
        if (w < 2f || h < 2f) return null
        var sum = 0f
        i = 0
        while (i < p.size) {
            val u = p[i] * c + p[i + 1] * s
            val v = -p[i] * s + p[i + 1] * c
            sum += minOf(u - minU, maxU - u, v - minV, maxV - v)
            i += 2
        }
        val cu = (maxU + minU) / 2f
        val cv = (maxV + minV) / 2f
        val box = FittedShape.Box(ShapeKind.RECTANGLE, cu * c - cv * s, cu * s + cv * c, w, h, normalized(degrees))
        return box to sum / (p.size / 2)
    }

    /** The stroke boiled down to its corners. */
    private fun fitPolygon(p: FloatArray, diag: Float): FittedShape.Path? {
        val corners = simplifyClosed(p, SIMPLIFY * diag)
        if (corners.size / 2 !in 3..MAX_CORNERS) return null
        return FittedShape.Path(corners, closed = true, smooth = false)
    }

    /** Mean distance from the points to the nearest side of the polygon. */
    private fun cornerError(p: FloatArray, corners: FloatArray, closed: Boolean): Float {
        var sum = 0f
        var i = 0
        val sides = if (closed) corners.size / 2 else corners.size / 2 - 1
        while (i < p.size) {
            var near = Float.MAX_VALUE
            for (k in 0 until sides) {
                val next = (k + 1) % (corners.size / 2)
                near = minOf(near, distanceToSegment(p[i], p[i + 1], corners[k * 2], corners[k * 2 + 1], corners[next * 2], corners[next * 2 + 1]))
            }
            sum += near
            i += 2
        }
        return sum / (p.size / 2)
    }

    // ---- Open ----

    private fun fitOpen(p: FloatArray, length: Float): FittedShape? {
        val x0 = p[0]
        val y0 = p[1]
        val x1 = p[p.size - 2]
        val y1 = p[p.size - 1]
        arrow(p, length)?.let { return it }
        var worst = 0f
        var i = 0
        while (i < p.size) {
            worst = maxOf(worst, distanceToSegment(p[i], p[i + 1], x0, y0, x1, y1))
            i += 2
        }
        if (worst <= LINE_TOLERANCE * length) return FittedShape.Line(x0, y0, x1, y1)
        val diag = diagonal(p)
        if (simplifyOpen(p, CURVE_SIMPLIFY * diag).size / 2 < 3) return FittedShape.Line(x0, y0, x1, y1)
        return FittedShape.Path(curveControls(p, diag), closed = false, smooth = true)
    }

    /**
     * The fewest control points whose smooth curve stays close to the stroke: the stroke is first evened out so hand tremor
     * does not become bends, then 3, 4, 5 and 6 evenly spaced points are tried in turn. A simple bow gets 3, not a handle at
     * every wobble.
     */
    private fun curveControls(p: FloatArray, diag: Float): FloatArray {
        val even = smoothed(p)
        var controls = spread(even, CURVE_MAX_POINTS)
        for (count in CURVE_MIN_POINTS..CURVE_MAX_POINTS) {
            val candidate = spread(even, count)
            val outline = app.betternotes.core.model.smoothOutline(candidate, false)
            var sum = 0f
            var worst = 0f
            var i = 0
            while (i < p.size) {
                var near = Float.MAX_VALUE
                var k = 0
                while (k < outline.size - 2) {
                    near = minOf(near, distanceToSegment(p[i], p[i + 1], outline[k], outline[k + 1], outline[k + 2], outline[k + 3]))
                    k += 2
                }
                sum += near
                worst = maxOf(worst, near)
                i += 2
            }
            controls = candidate
            if (sum / (p.size / 2) <= CURVE_MEAN * diag && worst <= CURVE_WORST * diag) break
        }
        return controls
    }

    /** A moving average over a few neighbors, three times over, with both ends kept where the pen began and lifted. */
    private fun smoothed(p: FloatArray): FloatArray {
        var cur = p.copyOf()
        val n = p.size / 2
        repeat(3) {
            val next = cur.copyOf()
            for (k in 1 until n - 1) {
                val lo = maxOf(0, k - 2)
                val hi = minOf(n - 1, k + 2)
                var sx = 0f
                var sy = 0f
                for (j in lo..hi) {
                    sx += cur[j * 2]
                    sy += cur[j * 2 + 1]
                }
                next[k * 2] = sx / (hi - lo + 1)
                next[k * 2 + 1] = sy / (hi - lo + 1)
            }
            cur = next
        }
        return cur
    }

    /**
     * A shaft with its head drawn in the same stroke: out along the shaft to the tip, back out along one barb, back to
     * the tip, and out along the other barb. Seen as corners that is A, tip, barb, tip, barb.
     */
    private fun arrow(p: FloatArray, length: Float): FittedShape.Line? {
        val n = p.size / 2
        // The farthest the pen got from where it began is about the tip's distance.
        var far = 0f
        for (k in 1 until n) far = maxOf(far, hypot(p[k * 2] - p[0], p[k * 2 + 1] - p[1]))
        if (far < 0.45f * length) return null
        val corners = simplifyOpen(p, 0.07f * far)
        if (corners.size / 2 < 4) return null
        val ax = corners[0]
        val ay = corners[1]
        // The shaft is one straight run to the first corner that reaches that far; the tip is where it ends. The head's
        // second visit to the tip may be a hair farther, so it is the first that counts.
        if (hypot(corners[2] - ax, corners[3] - ay) < 0.9f * far) return null
        val tx = corners[2]
        val ty = corners[3]
        val shaft = hypot(tx - ax, ty - ay)
        val dx = (tx - ax) / shaft
        val dy = (ty - ay) / shaft
        var firstBack = 0f
        var firstSide = 0f
        var lastBack = 0f
        var lastSide = 0f
        var found = 0
        for (k in 2 until corners.size / 2) {
            val bx = corners[k * 2] - tx
            val by = corners[k * 2 + 1] - ty
            if (hypot(bx, by) < 0.1f * shaft) continue
            val back = -(bx * dx + by * dy)
            val side = -bx * dy + by * dx
            if (found == 0) {
                firstBack = back
                firstSide = side
            }
            lastBack = back
            lastSide = side
            found++
        }
        // Two barbs, both sweeping back from the tip to opposite sides.
        if (found < 2 || firstBack <= 0f || lastBack <= 0f || firstSide * lastSide >= 0f) return null
        val head = (firstBack + lastBack) / 2f
        if (head > 0.6f * shaft || head < 0.06f * shaft) return null
        // One head size and one width for both barbs, so the tip is symmetrical about the shaft.
        return FittedShape.Line(ax, ay, tx, ty, headLength = head, headWidth = abs(firstSide) + abs(lastSide))
    }

    // ---- Geometry ----

    private fun packed(points: StrokePoints) = FloatArray(points.size * 2).also {
        for (i in 0 until points.size) {
            it[i * 2] = points.x(i)
            it[i * 2 + 1] = points.y(i)
        }
    }

    private fun pathLength(p: FloatArray): Float {
        var total = 0f
        var i = 2
        while (i < p.size) {
            total += hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1])
            i += 2
        }
        return total
    }

    private fun diagonal(p: FloatArray): Float {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        var i = 0
        while (i < p.size) {
            l = minOf(l, p[i]); r = maxOf(r, p[i]); t = minOf(t, p[i + 1]); b = maxOf(b, p[i + 1])
            i += 2
        }
        return hypot(r - l, b - t)
    }

    /** [RESAMPLE_POINTS] points at equal steps along the stroke, so a slow part of the stroke counts no more than a fast one. */
    private fun resample(p: FloatArray, closed: Boolean): FloatArray {
        val total = pathLength(p)
        val out = FloatArray(RESAMPLE_POINTS * 2)
        val step = total / if (closed) RESAMPLE_POINTS else RESAMPLE_POINTS - 1
        var seg = 2
        var segStart = 0f
        var segLength = hypot(p[2] - p[0], p[3] - p[1])
        for (k in 0 until RESAMPLE_POINTS) {
            val target = k * step
            while (target > segStart + segLength && seg < p.size - 2) {
                segStart += segLength
                seg += 2
                segLength = hypot(p[seg] - p[seg - 2], p[seg + 1] - p[seg - 1])
            }
            val t = if (segLength == 0f) 0f else ((target - segStart) / segLength).coerceIn(0f, 1f)
            out[k * 2] = p[seg - 2] + (p[seg] - p[seg - 2]) * t
            out[k * 2 + 1] = p[seg - 1] + (p[seg + 1] - p[seg - 1]) * t
        }
        return out
    }

    /** Douglas-Peucker for an open stroke. */
    private fun simplifyOpen(p: FloatArray, tolerance: Float): FloatArray {
        val keep = BooleanArray(p.size / 2)
        keep[0] = true
        keep[keep.size - 1] = true
        reduce(p, 0, keep.size - 1, tolerance, keep)
        return pick(p, keep)
    }

    /** For a closed stroke: split at the point farthest from the start, simplify both halves. */
    private fun simplifyClosed(p: FloatArray, tolerance: Float): FloatArray {
        val n = p.size / 2
        var far = 0
        var best = 0f
        for (k in 1 until n) {
            val d = hypot(p[k * 2] - p[0], p[k * 2 + 1] - p[1])
            if (d > best) {
                best = d
                far = k
            }
        }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[far] = true
        reduce(p, 0, far, tolerance, keep)
        // The way back runs on to the start again, so the first point is appended in place.
        val back = FloatArray((n - far + 1) * 2)
        System.arraycopy(p, far * 2, back, 0, (n - far) * 2)
        back[back.size - 2] = p[0]
        back[back.size - 1] = p[1]
        val keepBack = BooleanArray(n - far + 1)
        keepBack[0] = true
        keepBack[keepBack.size - 1] = true
        reduce(back, 0, keepBack.size - 1, tolerance, keepBack)
        val first = pick(p, keep)
        val second = pick(back, keepBack)
        // Drop the second half's first point (the far corner, already in the first half) and its last (the start).
        val out = FloatArray(first.size + second.size - 4)
        System.arraycopy(first, 0, out, 0, first.size)
        System.arraycopy(second, 2, out, first.size, second.size - 4)
        return out
    }

    private fun reduce(p: FloatArray, from: Int, to: Int, tolerance: Float, keep: BooleanArray) {
        if (to - from < 2) return
        var worst = 0f
        var at = -1
        for (k in from + 1 until to) {
            val d = distanceToSegment(p[k * 2], p[k * 2 + 1], p[from * 2], p[from * 2 + 1], p[to * 2], p[to * 2 + 1])
            if (d > worst) {
                worst = d
                at = k
            }
        }
        if (at < 0 || worst <= tolerance) return
        keep[at] = true
        reduce(p, from, at, tolerance, keep)
        reduce(p, at, to, tolerance, keep)
    }

    private fun pick(p: FloatArray, keep: BooleanArray): FloatArray {
        val out = ArrayList<Float>()
        for (k in keep.indices) {
            if (keep[k]) {
                out += p[k * 2]
                out += p[k * 2 + 1]
            }
        }
        return out.toFloatArray()
    }

    /** [count] points at equal steps along the stroke, ends included. */
    private fun spread(p: FloatArray, count: Int): FloatArray {
        val n = p.size / 2
        val out = FloatArray(count * 2)
        for (k in 0 until count) {
            val at = ((n - 1) * k.toDouble() / (count - 1)).toInt()
            out[k * 2] = p[at * 2]
            out[k * 2 + 1] = p[at * 2 + 1]
        }
        return out
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** How far a turn is from the nearest multiple of 90 degrees, the upright positions. */
    private fun quarterTilt(degrees: Float): Float {
        val m = ((degrees % 90f) + 90f) % 90f
        return minOf(m, 90f - m)
    }

    private fun normalized(degrees: Float): Float {
        var d = degrees % 180f
        if (d < 0f) d += 180f
        return d
    }

    // ---- To items ----

    /**
     * The page item for a fit, drawn like the stroke it replaces: its color and width, as a solid line.
     * Rectangles and ellipses are ordinary shapes, so they keep every handle those have.
     */
    fun toItem(fit: FittedShape, style: InkStyle, id: Long = IdSource.Random.next()): PageItem {
        val color = style.color
        val width = style.width
        return when (fit) {
            is FittedShape.Box -> ShapeItem(
                id = id,
                frame = Frame(fit.cx - fit.width / 2f, fit.cy - fit.height / 2f, fit.width, fit.height, fit.degrees),
                kind = fit.kind,
                strokeColor = color,
                strokeWidth = width,
            )
            is FittedShape.Line -> {
                val arrow = fit.headLength > 0f
                ShapeItem(
                    id = id,
                    frame = shapeFrame(if (arrow) ShapeKind.ARROW else ShapeKind.LINE, fit.x0, fit.y0, fit.x1, fit.y1),
                    kind = if (arrow) ShapeKind.ARROW else ShapeKind.LINE,
                    strokeColor = color,
                    strokeWidth = width,
                    tipLength = if (arrow) fit.headLength else 0f,
                    tipWidth = if (arrow) fit.headWidth else 0f,
                )
            }
            is FittedShape.Path -> PathShapeItem(id, fit.points, fit.closed, fit.smooth, color, width)
        }
    }

    /** The whole step: the stroke as the page item it should become, or null if it is no shape. */
    fun convert(stroke: StrokeItem): PageItem? = fit(stroke.points)?.let { toItem(it, stroke.style) }
}
