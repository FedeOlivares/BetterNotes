package app.betternotes.core.model

import app.betternotes.core.geometry.Frame
import app.betternotes.core.model.AudioBar.Zone
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class AudioBarTest {
    private val open = AudioBar.centeredAt(100f, 100f, open = true)
    private val collapsed = AudioBar.withOpen(open, open = false)

    @Test fun `a zero frame is not placed and the two states are told apart by width`() {
        assertFalse(AudioBar.isPlaced(Frame(0f, 0f, 0f, 0f)))
        assertTrue(AudioBar.isOpen(open))
        assertFalse(AudioBar.isOpen(collapsed))
        assertEquals(open.left, collapsed.left, 0f)
    }

    @Test fun `zones of an open bar`() {
        assertEquals(Zone.PLAY, AudioBar.zoneAt(open, open.left + 5f, open.centerY))
        assertEquals(Zone.WAVE, AudioBar.zoneAt(open, open.centerX, open.centerY))
        assertEquals(Zone.COLLAPSE, AudioBar.zoneAt(open, open.left + open.width - 3f, open.top + 3f))
        assertEquals(Zone.WAVE, AudioBar.zoneAt(open, open.left + open.width - 3f, open.top + open.height - 3f))
        assertNull(AudioBar.zoneAt(open, open.left - 1f, open.centerY))
    }

    @Test fun `zones of a collapsed bar`() {
        assertEquals(Zone.PLAY, AudioBar.zoneAt(collapsed, collapsed.left + 5f, collapsed.centerY))
        assertEquals(Zone.EXPAND, AudioBar.zoneAt(collapsed, collapsed.left + collapsed.width - 3f, collapsed.centerY))
    }

    @Test fun `the fraction runs along the waveform and is clamped`() {
        assertEquals(0f, AudioBar.fractionAt(open, open.left), 0f)
        assertEquals(1f, AudioBar.fractionAt(open, open.left + open.width), 0f)
        val middle = open.left + open.height + (open.width - open.height - AudioBar.WAVE_PADDING) / 2f
        assertEquals(0.5f, AudioBar.fractionAt(open, middle), 1e-4f)
    }
}
