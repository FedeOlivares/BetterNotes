package app.betternotes.core.tools

import java.util.BitSet

/**
 * The part of the paint bucket that needs no screen: given a picture of what is drawn on a page as a grid of pixels (true where something
 * is), it finds the closed region around a point and turns it into outlines.
 */
object FloodFill {

    /**
     * The pixels of the region around ([startX], [startY]) as a mask, or null when there is no region to fill: the point is on a line, or the
     * region is not closed, which is when it reaches the edge of the grid. Gaps up to about twice [gapPx] in the lines are bridged.
     */
    fun region(
        walls: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        gapPx: Int,
        /** If given, element 0 is set when the region was refused because it reaches the edge of the grid, as opposed to the point being on a line. */
        reachedEdge: BooleanArray? = null,
    ): BooleanArray? {
        if (startX !in 0 until width || startY !in 0 until height) return null
        val closed = if (gapPx > 0) dilate(walls, width, height, gapPx) else walls
        if (closed[startY * width + startX]) return null
        val filled = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0
        queue[tail++] = startY * width + startX
        filled[startY * width + startX] = true
        while (head < tail) {
            val at = queue[head++]
            val x = at % width
            val y = at / width
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                reachedEdge?.set(0, true)
                return null
            }
            for (next in intArrayOf(at - 1, at + 1, at - width, at + width)) {
                if (!filled[next] && !closed[next]) {
                    filled[next] = true
                    queue[tail++] = next
                }
            }
        }
        if (gapPx <= 0) return toLineCenters(walls, filled, width, height, queue)
        // The lines were thickened to bridge their gaps, so the region stopped short of them and the thickened zone is still unclaimed.
        // The page outside the shape is found the same way, and the two share the zone, each pixel of it going to whichever reaches
        // it first: the color then runs right up to the lines, and across a bridged gap it ends in a clean cut.
        val owner = ByteArray(width * height)
        head = 0
        tail = 0
        // The queue is reused: it was spent by the first fill.
        for (i in filled.indices) {
            if (filled[i]) {
                owner[i] = INSIDE
                queue[tail++] = i
            }
        }
        val outside = BooleanArray(width * height)
        val outsideQueue = IntArray(width * height)
        var outsideTail = 0
        fun seed(x: Int, y: Int) {
            val at = y * width + x
            if (!closed[at] && !outside[at]) {
                outside[at] = true
                outsideQueue[outsideTail++] = at
            }
        }
        for (x in 0 until width) {
            seed(x, 0)
            seed(x, height - 1)
        }
        for (y in 0 until height) {
            seed(0, y)
            seed(width - 1, y)
        }
        var outsideHead = 0
        while (outsideHead < outsideTail) {
            val at = outsideQueue[outsideHead++]
            val x = at % width
            val y = at / width
            if (x > 0) outsideStep(at - 1, closed, outside, outsideQueue, outsideTail).also { outsideTail = it }
            if (x < width - 1) outsideStep(at + 1, closed, outside, outsideQueue, outsideTail).also { outsideTail = it }
            if (y > 0) outsideStep(at - width, closed, outside, outsideQueue, outsideTail).also { outsideTail = it }
            if (y < height - 1) outsideStep(at + width, closed, outside, outsideQueue, outsideTail).also { outsideTail = it }
        }
        for (i in 0 until outsideTail) {
            owner[outsideQueue[i]] = OUTSIDE
            queue[tail++] = outsideQueue[i]
        }
        // The color may enter a line but goes no further from it: it never runs through a line into the zone on its far side.
        while (head < tail) {
            val at = queue[head++]
            if (walls[at]) continue
            val x = at % width
            val y = at / width
            for (dy in -1..1) {
                val ny = y + dy
                if (ny < 0 || ny >= height) continue
                for (dx in -1..1) {
                    val nx = x + dx
                    if (nx < 0 || nx >= width) continue
                    val next = ny * width + nx
                    if (owner[next] == NONE && closed[next]) {
                        owner[next] = owner[at]
                        queue[tail++] = next
                    }
                }
            }
        }
        return BooleanArray(width * height) { owner[it] == INSIDE }
    }

    /**
     * [filled] grown over the lines around it as far as their middle: each pixel of a line goes to the region on whichever side is nearer,
     * so the color of two neighbors meets in the middle of the line between them.
     */
    private fun toLineCenters(walls: BooleanArray, filled: BooleanArray, width: Int, height: Int, queue: IntArray): BooleanArray {
        val owner = ByteArray(width * height)
        var head = 0
        var tail = 0
        fun touchesLine(x: Int, y: Int) =
            (x > 0 && walls[y * width + x - 1]) || (x < width - 1 && walls[y * width + x + 1]) ||
                (y > 0 && walls[(y - 1) * width + x]) || (y < height - 1 && walls[(y + 1) * width + x])
        for (y in 0 until height) {
            for (x in 0 until width) {
                val at = y * width + x
                if (walls[at]) continue
                owner[at] = if (filled[at]) INSIDE else OUTSIDE
                if (touchesLine(x, y)) queue[tail++] = at
            }
        }
        // One ring of pixels at a time, so that a pixel both sides reach together, the middle of a line, is left to neither.
        val pending = ByteArray(width * height)
        while (head < tail) {
            val end = tail
            for (k in head until end) {
                val at = queue[k]
                if (owner[at] == TIE) continue
                val x = at % width
                val y = at / width
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val next = ny * width + nx
                        if (owner[next] != NONE || !walls[next]) continue
                        if (pending[next] == NONE) {
                            pending[next] = owner[at]
                            queue[tail++] = next
                        } else if (pending[next] != owner[at]) {
                            pending[next] = TIE
                        }
                    }
                }
            }
            for (k in end until tail) owner[queue[k]] = pending[queue[k]]
            head = end
        }
        return BooleanArray(width * height) { owner[it] == INSIDE }
    }

    private const val NONE: Byte = 0
    private const val INSIDE: Byte = 1
    private const val OUTSIDE: Byte = 2
    private const val TIE: Byte = 3

    private fun outsideStep(next: Int, closed: BooleanArray, outside: BooleanArray, queue: IntArray, tail: Int): Int {
        if (closed[next] || outside[next]) return tail
        outside[next] = true
        queue[tail] = next
        return tail + 1
    }

    /** [mask] with every true pixel grown by [radius] pixels in each direction, a square at a time. */
    fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val across = BooleanArray(width * height)
        val sums = IntArray(maxOf(width, height) + 1)
        for (y in 0 until height) {
            for (x in 0 until width) sums[x + 1] = sums[x] + if (mask[y * width + x]) 1 else 0
            for (x in 0 until width) {
                across[y * width + x] = sums[minOf(width, x + radius + 1)] - sums[maxOf(0, x - radius)] > 0
            }
        }
        val grown = BooleanArray(width * height)
        for (x in 0 until width) {
            for (y in 0 until height) sums[y + 1] = sums[y] + if (across[y * width + x]) 1 else 0
            for (y in 0 until height) {
                grown[y * width + x] = sums[minOf(height, y + radius + 1)] - sums[maxOf(0, y - radius)] > 0
            }
        }
        return grown
    }

    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, 1, 0, -1)

    /**
     * The outlines of [mask] as loops of packed x,y pairs in pixel corner coordinates: the outside of each region, and the edge of each hole
     * in it. Corners are cut off where the outline runs straight or nearly so, within [tolerancePx].
     */
    fun outlines(mask: BooleanArray, width: Int, height: Int, tolerancePx: Float): List<FloatArray> {
        val stride = width + 1
        val edges = BitSet((width + 1) * (height + 1) * 4)

        // An edge for each side of a filled pixel that has an empty one beside it, with the filled pixel on the right as it is followed.
        fun edge(x: Int, y: Int, dir: Int) = (y * stride + x) * 4 + dir
        fun filled(x: Int, y: Int) = x in 0 until width && y in 0 until height && mask[y * width + x]
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!mask[y * width + x]) continue
                if (!filled(x, y - 1)) edges.set(edge(x, y, 0))
                if (!filled(x + 1, y)) edges.set(edge(x + 1, y, 1))
                if (!filled(x, y + 1)) edges.set(edge(x + 1, y + 1, 2))
                if (!filled(x - 1, y)) edges.set(edge(x, y + 1, 3))
            }
        }
        val loops = ArrayList<FloatArray>()
        var from = edges.nextSetBit(0)
        while (from >= 0) {
            val points = ArrayList<Float>()
            var at = from
            do {
                edges.clear(at)
                val dir = at % 4
                val vertex = at / 4
                points += (vertex % stride).toFloat()
                points += (vertex / stride).toFloat()
                val x = vertex % stride + DX[dir]
                val y = vertex / stride + DY[dir]
                // Where two corners of the region touch, turning toward the filled side keeps them as separate loops.
                at = intArrayOf((dir + 1) % 4, dir, (dir + 3) % 4)
                    .map { edge(x, y, it) }
                    .firstOrNull { edges.get(it) } ?: from
            } while (at != from && edges.get(at))
            if (points.size >= 6) loops += simplify(smooth(points.toFloatArray()), tolerancePx)
            from = edges.nextSetBit(from)
        }
        return loops.filter { it.size >= 6 }
    }

    /** The loop with its corners rounded off by a pixel or two, so the steps of the pixel grid do not show as steps at any zoom. */
    private fun smooth(loop: FloatArray): FloatArray {
        val count = loop.size / 2
        if (count < 8) return loop
        var current = loop
        repeat(SMOOTH_PASSES) {
            val next = FloatArray(current.size)
            for (i in 0 until count) {
                val before = (i + count - 1) % count
                val after = (i + 1) % count
                next[i * 2] = (current[before * 2] + 2f * current[i * 2] + current[after * 2]) / 4f
                next[i * 2 + 1] = (current[before * 2 + 1] + 2f * current[i * 2 + 1] + current[after * 2 + 1]) / 4f
            }
            current = next
        }
        return current
    }

    private const val SMOOTH_PASSES = 3

    /** A closed loop with the points that stray less than [tolerance] from the line between their neighbors taken out. */
    fun simplify(loop: FloatArray, tolerance: Float): FloatArray {
        val count = loop.size / 2
        if (count <= 3) return loop
        // Two far apart points split the loop into two runs, and each run is simplified on its own.
        var far = 0
        var best = -1f
        for (i in 1 until count) {
            val d = (loop[i * 2] - loop[0]) * (loop[i * 2] - loop[0]) + (loop[i * 2 + 1] - loop[1]) * (loop[i * 2 + 1] - loop[1])
            if (d > best) {
                best = d
                far = i
            }
        }
        val keep = BooleanArray(count)
        keep[0] = true
        keep[far] = true
        reduce(loop, 0, far, tolerance, keep)
        reduce(loop, far, count, tolerance, keep)
        val out = ArrayList<Float>()
        for (i in 0 until count) {
            if (keep[i]) {
                out += loop[i * 2]
                out += loop[i * 2 + 1]
            }
        }
        return out.toFloatArray()
    }

    /** Marks the points of the run from [first] to [last] (which may be the start again) worth keeping. */
    private fun reduce(loop: FloatArray, first: Int, last: Int, tolerance: Float, keep: BooleanArray) {
        val count = loop.size / 2
        val ax = loop[first * 2]
        val ay = loop[first * 2 + 1]
        val bx = loop[(last % count) * 2]
        val by = loop[(last % count) * 2 + 1]
        var worst = -1
        var worstDistance = tolerance
        val length = Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        for (i in first + 1 until last) {
            val px = loop[i * 2]
            val py = loop[i * 2 + 1]
            val d = if (length == 0f) {
                Math.hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat()
            } else {
                Math.abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / length
            }
            if (d > worstDistance) {
                worstDistance = d
                worst = i
            }
        }
        if (worst >= 0) {
            keep[worst] = true
            reduce(loop, first, worst, tolerance, keep)
            reduce(loop, worst, last, tolerance, keep)
        }
    }
}
