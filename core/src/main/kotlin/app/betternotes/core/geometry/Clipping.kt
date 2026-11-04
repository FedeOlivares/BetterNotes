package app.betternotes.core.geometry

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A convex region that reports which part of a segment lies inside it. Convexity means that part
 * is always one unbroken interval, so stroke cutting needs no list of pieces per segment.
 *
 * Instances hold their last result in fields so the eraser allocates nothing per segment,
 * which makes them unsafe to share between threads.
 */
abstract class ConvexClipper {
    /** Where the segment last passed to [clip] enters the region, as a fraction of its length. */
    var enter = 0f
        protected set

    /** Where that segment leaves the region. */
    var exit = 1f
        protected set

    /** True if the segment touches the region after growing the region by [margin] on all sides. */
    abstract fun clip(x0: Float, y0: Float, x1: Float, y1: Float, margin: Float = 0f): Boolean

    private var slabEnter = 0f
    private var slabExit = 1f

    protected fun resetSlab() {
        slabEnter = 0f
        slabExit = 1f
    }

    /** Narrows the interval to where `start + t * delta` lies within [low]..[high]. */
    protected fun slab(start: Float, delta: Float, low: Float, high: Float): Boolean {
        if (delta == 0f) {
            if (start < low || start > high) slabExit = -1f
        } else {
            val a = (low - start) / delta
            val b = (high - start) / delta
            slabEnter = max(slabEnter, min(a, b))
            slabExit = min(slabExit, max(a, b))
        }
        return slabEnter <= slabExit
    }

    protected val slabStart get() = slabEnter
    protected val slabEnd get() = slabExit
}

/** The area swept by a disc moving from A to B, which is the path of an eraser tip. */
class Capsule(
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val radius: Float,
) : ConvexClipper() {
    private val length = hypot(bx - ax, by - ay)
    private val ux = if (length > 0f) (bx - ax) / length else 1f
    private val uy = if (length > 0f) (by - ay) / length else 0f

    val bounds = Bounds(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)).outset(radius)

    private var low = 0f
    private var high = 0f

    override fun clip(x0: Float, y0: Float, x1: Float, y1: Float, margin: Float): Boolean {
        val r = radius + margin
        val dx = x1 - x0
        val dy = y1 - y0
        low = Float.POSITIVE_INFINITY
        high = Float.NEGATIVE_INFINITY

        addDisc(x0, y0, dx, dy, ax, ay, r)
        if (length > 0f) {
            addDisc(x0, y0, dx, dy, bx, by, r)
            val relX = x0 - ax
            val relY = y0 - ay
            resetSlab()
            val inside = slab(relX * ux + relY * uy, dx * ux + dy * uy, 0f, length) &&
                slab(relY * ux - relX * uy, dy * ux - dx * uy, -r, r)
            if (inside) widen(slabStart, slabEnd)
        }

        if (low > high) return false
        enter = low
        exit = high
        return true
    }

    private fun addDisc(x0: Float, y0: Float, dx: Float, dy: Float, cx: Float, cy: Float, r: Float) {
        val fx = x0 - cx
        val fy = y0 - cy
        val a = dx * dx + dy * dy
        val c = fx * fx + fy * fy - r * r
        if (a == 0f) {
            if (c <= 0f) widen(0f, 1f)
            return
        }
        val halfB = fx * dx + fy * dy
        val discriminant = halfB * halfB - a * c
        if (discriminant < 0f) return
        val root = sqrt(discriminant)
        widen((-halfB - root) / a, (-halfB + root) / a)
    }

    private fun widen(from: Float, to: Float) {
        val clippedFrom = max(from, 0f)
        val clippedTo = min(to, 1f)
        if (clippedFrom <= clippedTo) {
            low = min(low, clippedFrom)
            high = max(high, clippedTo)
        }
    }
}

/** An axis-aligned rectangle, used to clear only the displayed part of an infinite canvas. */
class RectClipper(private val rect: Bounds) : ConvexClipper() {
    override fun clip(x0: Float, y0: Float, x1: Float, y1: Float, margin: Float): Boolean {
        resetSlab()
        val inside = slab(x0, x1 - x0, rect.left - margin, rect.right + margin) &&
            slab(y0, y1 - y0, rect.top - margin, rect.bottom + margin)
        if (!inside) return false
        enter = slabStart
        exit = slabEnd
        return true
    }
}

/** Even-odd test against a polygon stored as packed x,y pairs. The closing edge is implied. */
fun polygonContains(polygon: FloatArray, x: Float, y: Float): Boolean {
    var inside = false
    var prevX = polygon[polygon.size - 2]
    var prevY = polygon[polygon.size - 1]
    var i = 0
    while (i < polygon.size) {
        val curX = polygon[i]
        val curY = polygon[i + 1]
        if ((curY > y) != (prevY > y) && x < (prevX - curX) * (y - curY) / (prevY - curY) + curX) {
            inside = !inside
        }
        prevX = curX
        prevY = curY
        i += 2
    }
    return inside
}
