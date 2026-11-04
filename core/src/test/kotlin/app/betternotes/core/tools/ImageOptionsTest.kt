package app.betternotes.core.tools

import app.betternotes.core.model.IdSource
import app.betternotes.core.model.ImageShape
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageOptionsTest {
    private val ids = IdSource { 3L }

    @Test
    fun aPictureFitsItsLimitsAndKeepsItsProportions() {
        val wide = ImageOptions().newImage("a", 4000, 1000, 100f, 100f, 200f, 200f, ids)
        assertEquals(200f, wide.frame.width, 0.01f)
        assertEquals(50f, wide.frame.height, 0.01f)
        assertEquals(0f, wide.frame.left, 0.01f)
        assertEquals(75f, wide.frame.top, 0.01f)

        val tall = ImageOptions().newImage("a", 1000, 4000, 100f, 100f, 200f, 200f, ids)
        assertEquals(50f, tall.frame.width, 0.01f)
        assertEquals(200f, tall.frame.height, 0.01f)
    }

    @Test
    fun itTakesTheOptionsItIsFramedWith() {
        val image = ImageOptions(ImageShape.ELLIPSE, cornerRadius = 2, borderWidth = 3, borderColor = 0xFF112233.toInt())
            .newImage("a", 10, 10, 0f, 0f, 10f, 10f, ids)
        assertEquals(ImageShape.ELLIPSE, image.shape)
        assertEquals(2 * ImageOptions.POINTS_PER_RADIUS, image.cornerRadius, 0f)
        assertEquals(3 * ImageOptions.POINTS_PER_BORDER, image.borderWidth, 0f)
        assertEquals(0xFF112233.toInt(), image.borderColor)
    }

    @Test
    fun noBorderWidthMeansNoBorderColor() {
        assertEquals(0, ImageOptions().newImage("a", 10, 10, 0f, 0f, 10f, 10f, ids).borderColor)
    }
}
