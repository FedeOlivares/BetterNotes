package app.betternotes.core.format

import app.betternotes.core.model.InkStyle
import app.betternotes.core.model.PenType
import app.betternotes.core.model.StrokeItem
import app.betternotes.core.model.StrokePointsBuilder
import app.betternotes.core.model.TipShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureStyleTest {
    private fun stroke(style: InkStyle) = StrokeItem(
        1L,
        style,
        StrokePointsBuilder().apply {
            add(0f, 0f, 0.3f, 0f, 0f, 0f)
            add(5f, 5f, 0.6f, 0f, 0f, 10f)
        }.build(),
    )

    @Test
    fun aStrokeKeepsWhetherItFollowsPressure() {
        for (pressure in listOf(true, false)) {
            for (tip in TipShape.entries) {
                val style = InkStyle(penType = PenType.BALL, tip = tip, pressure = pressure)
                val back = PageCodec.decode(PageCodec.encode(listOf(stroke(style)))).single() as StrokeItem
                assertEquals(style, back.style)
            }
        }
    }

    @Test
    fun aStrokeFromBeforeThePressureSettingHasNone() {
        // Written as an older build did, the byte of the tip has its top bit clear and is read as no pressure.
        val bytes = PageCodec.encode(listOf(stroke(InkStyle(pressure = false))))
        val back = PageCodec.decode(bytes).single() as StrokeItem
        assertFalse(back.style.pressure)
        val with = PageCodec.decode(PageCodec.encode(listOf(stroke(InkStyle(pressure = true))))).single() as StrokeItem
        assertTrue(with.style.pressure)
    }
}
