package app.betternotes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.ink.brush.TextureBitmapStore
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pictures the creative brushes paint with. All of them are drawn here, in code, in white: the brush's
 * color is kept where the picture is opaque, so they take any color. Ids are part of the saved look of a
 * stroke, because pages are meshed again on load, so an id's picture may change but its meaning may not.
 */
internal object BrushTextures : TextureBitmapStore {
    const val GRAIN = "grain-v1"
    const val WASH = "wash-v1"
    const val LEAVES = "leaves-v1"
    private const val LEAF_KINDS = 8
    private const val LEAF_TILE_CELLS = 4

    private const val LEAF_CELL = 128
    private val cache = ConcurrentHashMap<String, Bitmap>()

    override fun get(clientTextureId: String): Bitmap = cache.getOrPut(clientTextureId) {
        when (clientTextureId) {
            GRAIN -> grain()
            WASH -> wash()
            LEAVES -> leaves()
            else -> Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
        }
    }

    /** Speckle for graphite: most of it solid, with tiny holes, tiling without a visible seam. */
    private fun grain(): Bitmap {
        val size = 128
        val random = Random(7)
        val pixels = IntArray(size * size) {
            // Mostly strong, with a scatter of pale and empty specks.
            val r = random.nextFloat()
            val alpha = when {
                r < 0.18f -> 0.0f
                r < 0.45f -> 0.35f + random.nextFloat() * 0.3f
                else -> 0.75f + random.nextFloat() * 0.25f
            }
            Color.argb((alpha * 255).toInt(), 255, 255, 255)
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Mottled pigment: blotches of heavier and thinner paint, repeating without a seam. */
    private fun wash(): Bitmap {
        val size = 256
        val random = Random(11)
        fun lattice(cells: Int) = Array(cells) { FloatArray(cells) { random.nextFloat() } }
        val coarse = lattice(6)
        val fine = lattice(16)
        fun sample(grid: Array<FloatArray>, x: Float, y: Float): Float {
            val n = grid.size
            val gx = x * n
            val gy = y * n
            val x0 = gx.toInt() % n
            val y0 = gy.toInt() % n
            val x1 = (x0 + 1) % n
            val y1 = (y0 + 1) % n
            var fx = gx - gx.toInt()
            var fy = gy - gy.toInt()
            fx = fx * fx * (3 - 2 * fx)
            fy = fy * fy * (3 - 2 * fy)
            val top = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
            val bottom = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
            return top * (1 - fy) + bottom * fy
        }
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val u = x / size.toFloat()
                val v = y / size.toFloat()
                val value = 0.65f * sample(coarse, u, v) + 0.35f * sample(fine, u, v)
                // Spread the middle values out, so there are clear heavy and thin patches.
                val patch = ((value - 0.5f) * 2.2f + 0.5f).coerceIn(0f, 1f)
                val alpha = 0.3f + 0.7f * patch
                pixels[y * size + x] = Color.argb((alpha * 255).toInt(), 255, 255, 255)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** A tile strewn with tree leaves: oak, maple, birch, ginkgo, willow, linden, elm and aspen, at random angles and sizes. */
    private fun leaves(): Bitmap {
        val cell = LEAF_CELL
        val side = cell * LEAF_TILE_CELLS
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val vein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(120, 255, 255, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        val random = Random(5)
        for (row in 0 until LEAF_TILE_CELLS) {
            for (column in 0 until LEAF_TILE_CELLS) {
                val kind = (row * 3 + column * 5 + random.nextInt(3)) % LEAF_KINDS
                canvas.save()
                canvas.translate(
                    column * cell + cell / 2f + (random.nextFloat() - 0.5f) * 14f,
                    row * cell + cell / 2f + (random.nextFloat() - 0.5f) * 14f,
                )
                canvas.rotate(random.nextFloat() * 360f)
                val scale = 0.62f + random.nextFloat() * 0.2f
                canvas.scale(scale, scale)
                canvas.translate(-cell / 2f, -cell / 2f)
                drawLeaf(canvas, kind, fill, vein)
                canvas.restore()
            }
        }
        return bitmap
    }

    /** A leaf pointing up in its cell, its stem at the bottom. [halfWidth] gives how far the edge is from the middle at each height. */
    private fun outline(steps: Int, top: Float, bottom: Float, left: (Float) -> Float, right: (Float) -> Float): Path {
        val centerX = LEAF_CELL / 2f
        val path = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = centerX + right(t) * LEAF_CELL
            val y = top + t * (bottom - top)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val t = i.toFloat() / steps
            path.lineTo(centerX - left(t) * LEAF_CELL, top + t * (bottom - top))
        }
        path.close()
        return path
    }

    private fun drawLeaf(canvas: Canvas, kind: Int, fill: Paint, vein: Paint) {
        val cell = LEAF_CELL.toFloat()
        val mid = cell / 2f
        val top = 8f
        val bottom = cell - 18f
        // The body of the leaf: sin(pi t) rises from nothing at the tip, through its widest, to nothing at the base.
        fun body(t: Float, power: Float = 1f) = sin(PI.toFloat() * t.pow(power))
        val path: Path = when (kind) {
            // Oak: a long leaf with rounded lobes.
            0 -> outline(160, top, bottom, { t -> 0.34f * body(t, 0.85f) * (0.72f + 0.28f * abs(cos(t * PI.toFloat() * 3.5f))) }, { t -> 0.34f * body(t, 0.85f) * (0.72f + 0.28f * abs(cos(t * PI.toFloat() * 3.5f + 0.6f))) })
            // Maple: five pointed lobes from the stem.
            1 -> maple(mid, bottom - 10f, 50f)
            // Birch: a pointed oval with a toothed edge.
            2 -> outline(120, top, bottom, { t -> 0.32f * body(t, 0.75f) * (0.9f + 0.1f * saw(t * 14f)) }, { t -> 0.32f * body(t, 0.75f) * (0.9f + 0.1f * saw(t * 14f + 0.5f)) })
            // Ginkgo: a fan with a notch in its rim.
            3 -> ginkgo(mid, bottom - 4f, 52f)
            // Willow: long and narrow.
            4 -> outline(100, top, bottom, { t -> 0.13f * body(t, 0.9f) }, { t -> 0.13f * body(t, 0.9f) })
            // Linden: a heart.
            5 -> outline(140, top, bottom, { t -> 0.42f * body(t, 0.6f) * (if (t > 0.88f) 1f - (t - 0.88f) * 4f else 1f).coerceAtLeast(0f) }, { t -> 0.42f * body(t, 0.6f) * (if (t > 0.88f) 1f - (t - 0.88f) * 4f else 1f).coerceAtLeast(0f) })
            // Elm: an oval whose two sides are not the same.
            6 -> outline(120, top, bottom, { t -> 0.34f * body(t, 0.8f) * (0.9f + 0.1f * saw(t * 11f)) }, { t -> 0.26f * body(t, 0.7f) * (0.9f + 0.1f * saw(t * 11f + 0.3f)) })
            // Aspen: nearly round, with a drawn-out tip.
            else -> outline(120, top, bottom, { t -> 0.40f * sqrt(max(0f, body(t, 0.55f))) * (if (t < 0.18f) t / 0.18f else 1f) }, { t -> 0.40f * sqrt(max(0f, body(t, 0.55f))) * (if (t < 0.18f) t / 0.18f else 1f) })
        }
        canvas.drawPath(path, fill)
        // A short stem, and veins.
        canvas.drawRect(mid - 1.6f, bottom - 4f, mid + 1.6f, cell - 4f, fill)
        canvas.drawLine(mid, bottom - 2f, mid, top + (bottom - top) * 0.12f, vein)
        for (v in 1..4) {
            val y = bottom - (bottom - top) * (0.12f + v * 0.17f)
            val reach = (bottom - top) * 0.17f * (if (kind == 4) 0.5f else 1.3f)
            canvas.drawLine(mid, y, mid - reach, y - reach * 0.75f, vein)
            canvas.drawLine(mid, y, mid + reach, y - reach * 0.75f, vein)
        }
    }

    private fun saw(t: Float) = t - kotlin.math.floor(t)

    /** A maple leaf: a rim that runs out to five points. */
    private fun maple(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        val steps = 220
        for (i in 0..steps) {
            // Angles from just right of the stem, round the top, to just left of it.
            val a = (-PI * 0.5 + (i.toFloat() / steps - 0.5f) * 2.0 * PI * 0.92).toFloat() - (PI * 0.0).toFloat()
            val relative = (i.toFloat() / steps)
            val lobes = abs(cos(relative * PI.toFloat() * 2.5f))
            val reach = size * (0.38f + 0.62f * lobes.pow(1.6f))
            val x = cx + cos(a) * reach
            val y = cy + sin(a) * reach
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(cx, cy)
        path.close()
        return path
    }

    /** A ginkgo leaf: a half disc opening upward, with a notch at the top. */
    private fun ginkgo(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        path.moveTo(cx, cy)
        val steps = 120
        for (i in 0..steps) {
            val relative = i.toFloat() / steps
            val a = (-PI * 0.5 + (relative - 0.5f) * PI * 0.92).toFloat()
            // The rim dips in the middle and has a gentle scallop.
            val notch = 1f - 0.45f * max(0f, 1f - abs(relative - 0.5f) * 16f)
            val reach = size * notch * (0.96f + 0.04f * cos(relative * PI.toFloat() * 10f))
            path.lineTo(cx + cos(a) * reach, cy + sin(a) * reach)
        }
        path.close()
        return path
    }
}
