package app.betternotes.canvas

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.betternotes.core.model.BrushKind
import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.InkTool
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** The ink library rejects some brushes only when it builds them (at most 10 layers, distinct ranges). */
@RunWith(AndroidJUnit4::class)
class InkBrushesTest {
    @Test
    fun everyCreativeBrushBuilds() {
        for (kind in BrushKind.entries) {
            assertNotNull(kind.name, InkBrushes.brush(InkStyle(tool = InkTool.PENCIL, brush = kind)))
        }
    }
}
