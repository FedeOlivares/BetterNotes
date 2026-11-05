package app.betternotes.canvas

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.ink.brush.TextureBitmapStore
import java.util.concurrent.ConcurrentHashMap

/**
 * The pictures the creative brushes paint with: white, with the ink's amount as alpha, so they take any color.
 * They are made from the owner's reference strokes by tools/extract_brush_textures.py and kept in assets.
 * Ids are part of the saved look of a stroke, because pages are meshed again on load, so an id's picture
 * may change but its meaning may not.
 */
internal object BrushTextures : TextureBitmapStore {
    const val GRAIN = "grain-stamp"
    const val BLOT = "blot-stamp"
    const val OIL = "oil-stamp"
    const val LEAVES = "leaf-stamps"
    const val LEAF_COLUMNS = 3
    const val LEAF_ROWS = 2
    const val LEAF_COUNT = LEAF_COLUMNS * LEAF_ROWS

    private const val FOLDER = "brush_textures/"
    private var assets: AssetManager? = null
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun install(assets: AssetManager) {
        this.assets = assets
    }

    override fun get(clientTextureId: String): Bitmap = cache.getOrPut(clientTextureId) {
        val manager = checkNotNull(assets) { "BrushTextures.install was not called" }
        manager.open("$FOLDER$clientTextureId.png").use { BitmapFactory.decodeStream(it) }
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
    }
}
