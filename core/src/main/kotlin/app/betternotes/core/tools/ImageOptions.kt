package app.betternotes.core.tools

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ImageItem
import app.betternotes.core.model.ImageShape

/** What a new picture is framed with. A [borderWidth] of 0 means no border. */
data class ImageOptions(
    val shape: ImageShape = ImageShape.RECTANGLE,
    val cornerRadius: Int = 0,
    val borderWidth: Int = 0,
    val borderColor: Int = 0xFF000000.toInt(),
) {
    /**
     * The picture centered on a point, no wider than [maxWidth] nor taller than [maxHeight], in
     * points. Its proportions are kept.
     */
    fun newImage(
        assetId: String,
        pixelWidth: Int,
        pixelHeight: Int,
        centerX: Float,
        centerY: Float,
        maxWidth: Float,
        maxHeight: Float,
        ids: IdSource = IdSource.Random,
    ): ImageItem {
        val fit = minOf(maxWidth / pixelWidth, maxHeight / pixelHeight)
        val width = pixelWidth * fit
        val height = pixelHeight * fit
        return ImageItem(
            id = ids.next(),
            frame = Frame(centerX - width / 2f, centerY - height / 2f, width, height),
            assetId = assetId,
            shape = shape,
            cornerRadius = cornerRadius * POINTS_PER_RADIUS,
            borderColor = if (borderWidth > 0) borderColor else 0,
            borderWidth = borderWidth * POINTS_PER_BORDER,
        )
    }

    companion object {
        const val MAX_CORNER_RADIUS = 10
        const val MAX_BORDER = 5
        const val POINTS_PER_RADIUS = 3f
        const val POINTS_PER_BORDER = 1f
    }
}
