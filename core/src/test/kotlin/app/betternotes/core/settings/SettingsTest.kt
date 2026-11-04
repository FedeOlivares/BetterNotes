package app.betternotes.core.settings

import app.betternotes.core.format.ByteWriter
import app.betternotes.core.format.encodeFile
import app.betternotes.core.geometry.LengthUnit
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.gesture.GestureConfig
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.LayoutElement
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SettingsTest {
    private val layout = CustomLayout(
        "Lab",
        PageSize(300f, 400f),
        LengthUnit.INCH,
        listOf(LayoutElement.Table(1f, 2f, 100f, 50f, 2, 3, 0.5f, 0xFF112233.toInt())),
    )

    private val changed = AppSettings(
        gestures = GestureSettings(multiTapWindowMs = 450, tapMaxDurationMs = 200),
        scrollDirection = ScrollDirection.RIGHT_TO_LEFT,
        newPages = NewPageDefaults(PageSize.Letter, PageBackground(0xFF000000.toInt(), PageTemplate.Grid(spacing = 20f))),
        customLayouts = listOf(layout),
        keyBindings = KeyBindings.Default
            .rebind(EditorAction.PEN, KeyChord(KeyCodes.Q, KeyChord.ALT))
            .unbind(EditorAction.LOUPE),
        calibration = ScreenCalibration(345f),
    )

    @Test
    fun `settings survive the round trip`() {
        assertEquals(changed, SettingsCodec.decode(SettingsCodec.encode(changed)))
        assertEquals(AppSettings(), SettingsCodec.decode(SettingsCodec.encode(AppSettings())))
    }

    @Test
    fun `templates added after format version 1 keep their values`() {
        for (template in listOf(PageTemplate.CornellRuled(20f, 0.6f, 7), PageTemplate.Engineering(11f, 0.4f, 8), PageTemplate.GuitarScore(8f, 40f, 1f, 6))) {
            val settings = AppSettings(newPages = NewPageDefaults(background = PageBackground(template = template)))
            assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        }
    }

    @Test
    fun `a section this version does not know is skipped`() {
        val bytes = encodeFile("BNST") {
            varint(99)
            val unknown = ByteArray(5)
            varint(unknown.size)
            bytes(unknown)
            varint(2)
            varint(1)
            byte(1)
        }
        assertEquals(ScrollDirection.LEFT_TO_RIGHT, SettingsCodec.decode(bytes).scrollDirection)
    }

    @Test
    fun `a file missing sections reads with defaults for them`() {
        val bytes = encodeFile("BNST") { ByteWriter() }
        assertEquals(AppSettings(), SettingsCodec.decode(bytes))
    }

    @Test
    fun `a damaged file falls back to the defaults`() {
        val dir = Files.createTempDirectory("settings").toFile()
        try {
            val file = File(dir, "settings.bns")
            val store = SettingsStore(file)
            assertEquals(AppSettings(), store.read())
            store.write(changed)
            assertEquals(changed, store.read())
            file.writeBytes(ByteArray(40) { 7 })
            assertEquals(AppSettings(), store.read())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `timing settings reach the gesture config and leave the rest of it alone`() {
        val config = GestureSettings(multiTapWindowMs = 500, tapMaxDurationMs = 150).applyTo(GestureConfig(touchSlopPx = 3f))
        assertEquals(500L, config.multiTapWindowMs)
        assertEquals(150L, config.tapMaxDurationMs)
        assertEquals(3f, config.touchSlopPx)
    }

    @Test
    fun `timings outside the range are refused`() {
        assertThrows(IllegalArgumentException::class.java) { GestureSettings(multiTapWindowMs = 10) }
        assertThrows(IllegalArgumentException::class.java) { GestureSettings(tapMaxDurationMs = 5000) }
    }

    @Test
    fun `calibration corrects the density from a measured line`() {
        // A line meant to be 10 cm came out 9.75 cm, so the screen is denser than reported.
        val calibration = ScreenCalibration.fromMeasurement(336.45f, 10f, 9.75f)
        assertEquals(345.08f, calibration.dpi, 0.01f)
        assertThrows(IllegalArgumentException::class.java) { ScreenCalibration.fromMeasurement(336f, 10f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { ScreenCalibration(1f) }
    }

    @Test
    fun `saving a layout under a used name replaces it`() {
        val renamed = layout.copy(size = PageSize(100f, 100f))
        val settings = AppSettings().withCustomLayout(layout).withCustomLayout(renamed)
        assertEquals(listOf(renamed), settings.customLayouts)
        assertEquals(emptyList<CustomLayout>(), settings.withoutCustomLayout("Lab").customLayouts)
    }
}

class KeyBindingsTest {
    @Test
    fun `every action has a default and no two share one`() {
        assertEquals(EditorAction.entries.toSet(), KeyBindings.DEFAULTS.keys)
        assertEquals(KeyBindings.DEFAULTS.size, KeyBindings.DEFAULTS.values.toSet().size)
    }

    @Test
    fun `defaults resolve both ways`() {
        val undo = KeyChord(KeyCodes.Z, KeyChord.CTRL)
        assertEquals(undo, KeyBindings.Default.chordFor(EditorAction.UNDO))
        assertEquals(EditorAction.UNDO, KeyBindings.Default.actionFor(undo))
        assertNull(KeyBindings.Default.actionFor(KeyChord(KeyCodes.Z, KeyChord.META)))
    }

    @Test
    fun `taking a chord from another action leaves that action unbound`() {
        val chord = KeyBindings.DEFAULTS.getValue(EditorAction.ERASER)
        assertEquals(EditorAction.ERASER, KeyBindings.Default.conflictFor(EditorAction.PEN, chord))

        val bindings = KeyBindings.Default.rebind(EditorAction.PEN, chord)
        assertEquals(EditorAction.PEN, bindings.actionFor(chord))
        assertNull(bindings.chordFor(EditorAction.ERASER))
        assertNull(bindings.actionFor(KeyBindings.DEFAULTS.getValue(EditorAction.PEN)))
    }

    @Test
    fun `a saved set keeps a stolen chord away from its old owner`() {
        val chord = KeyBindings.DEFAULTS.getValue(EditorAction.ERASER)
        val bindings = KeyBindings.Default.rebind(EditorAction.PEN, chord)
        val settings = SettingsCodec.decode(SettingsCodec.encode(AppSettings(keyBindings = bindings)))
        assertEquals(bindings, settings.keyBindings)
        assertNull(settings.keyBindings.chordFor(EditorAction.ERASER))
    }

    @Test
    fun `reset goes back to the default and clears the holder`() {
        val penChord = KeyBindings.DEFAULTS.getValue(EditorAction.PEN)
        val moved = KeyBindings.Default
            .rebind(EditorAction.PEN, KeyChord(KeyCodes.Q, KeyChord.ALT))
            .rebind(EditorAction.TABLE, penChord)
        val reset = moved.reset(EditorAction.PEN)
        assertEquals(penChord, reset.chordFor(EditorAction.PEN))
        assertNull(reset.chordFor(EditorAction.TABLE))
    }

    @Test
    fun `rebinding to the default chord is the same as never changing it`() {
        val chord = KeyBindings.DEFAULTS.getValue(EditorAction.PEN)
        val bindings = KeyBindings.Default.rebind(EditorAction.PEN, KeyChord(KeyCodes.Q, KeyChord.ALT)).rebind(EditorAction.PEN, chord)
        assertEquals(KeyBindings.Default, bindings)
    }

    @Test
    fun `unbinding frees the chord`() {
        val chord = KeyBindings.DEFAULTS.getValue(EditorAction.PEN)
        val bindings = KeyBindings.Default.unbind(EditorAction.PEN)
        assertNull(bindings.chordFor(EditorAction.PEN))
        assertNull(bindings.actionFor(chord))
    }
}
