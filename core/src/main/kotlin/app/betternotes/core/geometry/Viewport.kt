package app.betternotes.core.geometry

/**
 * Maps world points to screen pixels.
 *
 * Zoom 1.0 is true physical size: one world centimetre covers one real centimetre of glass,
 * given [screenDpi]. The DPI is a parameter because reported values can be wrong; a calibration
 * setting can supply a measured one.
 */
class Viewport(var screenDpi: Float) {
    var zoom = 1f
        private set
    var panX = 0f
    var panY = 0f

    val pxPerPt get() = zoom * screenDpi / 72f

    fun toScreenX(worldX: Float) = worldX * pxPerPt + panX
    fun toScreenY(worldY: Float) = worldY * pxPerPt + panY
    fun toWorldX(screenX: Float) = (screenX - panX) / pxPerPt
    fun toWorldY(screenY: Float) = (screenY - panY) / pxPerPt

    /** Zooms to [newZoom] while keeping the world point under the focal pixel fixed. */
    fun zoomTo(newZoom: Float, focusX: Float, focusY: Float) {
        val worldX = toWorldX(focusX)
        val worldY = toWorldY(focusY)
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = focusX - worldX * pxPerPt
        panY = focusY - worldY * pxPerPt
    }

    fun visibleWorld(screenWidth: Int, screenHeight: Int) = Bounds(
        toWorldX(0f),
        toWorldY(0f),
        toWorldX(screenWidth.toFloat()),
        toWorldY(screenHeight.toFloat()),
    )

    companion object {
        const val MIN_ZOOM = 0.1f

        /** 1500%. */
        const val MAX_ZOOM = 15f
    }
}
