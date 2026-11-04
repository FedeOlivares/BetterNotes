package app.betternotes.core.settings

import app.betternotes.core.format.ByteReader
import app.betternotes.core.format.ByteWriter
import app.betternotes.core.format.WireEnum
import app.betternotes.core.format.customLayout
import app.betternotes.core.format.decodeFile
import app.betternotes.core.format.encodeFile
import app.betternotes.core.format.pageSize
import app.betternotes.core.format.template
import app.betternotes.core.gesture.TwoFingerAction
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.template.PageBackground

private const val SETTINGS_MAGIC = "BNST"

private const val SECTION_GESTURES = 1
private const val SECTION_SCROLL = 2
private const val SECTION_NEW_PAGES = 3
private const val SECTION_LAYOUTS = 4
private const val SECTION_KEYS = 5
private const val SECTION_CALIBRATION = 6
private const val SECTION_FINGERS = 7
private const val SECTION_STYLUS = 8
private const val SECTION_CANVAS = 9
private const val SECTION_NOTE_NAME = 10

private val ONE_FINGER = WireEnum(OneFingerSetting.IGNORED, OneFingerSetting.DRAW, OneFingerSetting.SCROLL)
private val TWO_FINGERS = WireEnum(TwoFingerAction.IGNORED, TwoFingerAction.ZOOM_AND_PAN, TwoFingerAction.SCROLL)
private val STYLUS_ACTIONS = WireEnum(
    StylusAction.NONE,
    StylusAction.PEN,
    StylusAction.HIGHLIGHTER,
    StylusAction.ERASER,
    StylusAction.LASSO,
    StylusAction.UNDO,
    StylusAction.REDO,
)
private val HOLD_ACTIONS = WireEnum(StylusHoldAction.NONE, StylusHoldAction.ERASE)
private val ERASER_ENDS = WireEnum(StylusEraserEnd.ERASER, StylusEraserEnd.IGNORED)
private val NOTE_NAMES = WireEnum(NewNoteName.DATED, NewNoteName.UNTITLED)

private val SCROLL_DIRECTIONS =
    WireEnum(ScrollDirection.VERTICAL, ScrollDirection.LEFT_TO_RIGHT, ScrollDirection.RIGHT_TO_LEFT)

/**
 * The settings file. The body is a list of sections, each with a tag and a length. Settings change
 * far more often than documents do, and this lets one be added without a new format version: an older
 * file reads with defaults for what it lacks, and an older app skips what it does not know.
 *
 * Actions are stored by name, because the enum will keep growing in the middle.
 */
object SettingsCodec {
    fun encode(settings: AppSettings): ByteArray = encodeFile(SETTINGS_MAGIC) {
        section(SECTION_GESTURES) {
            long(settings.gestures.multiTapWindowMs)
            long(settings.gestures.tapMaxDurationMs)
        }
        section(SECTION_SCROLL) { SCROLL_DIRECTIONS.write(this, settings.scrollDirection) }
        section(SECTION_NEW_PAGES) {
            pageSize(settings.newPages.size)
            int(settings.newPages.background.color)
            template(settings.newPages.background.template)
        }
        section(SECTION_LAYOUTS) {
            varint(settings.customLayouts.size)
            settings.customLayouts.forEach { customLayout(it) }
        }
        section(SECTION_KEYS) {
            val overrides = settings.keyBindings.overrides
            varint(overrides.size)
            for ((action, chord) in overrides) {
                string(action.name)
                bool(chord != null)
                if (chord != null) {
                    varint(chord.keyCode)
                    byte(chord.modifiers)
                }
            }
        }
        settings.calibration?.let { calibration -> section(SECTION_CALIBRATION) { float(calibration.dpi) } }
        section(SECTION_FINGERS) {
            ONE_FINGER.write(this, settings.gestures.oneFinger)
            TWO_FINGERS.write(this, settings.gestures.twoFingers)
            bool(settings.gestures.undoTaps)
            bool(settings.gestures.redoTaps)
            bool(settings.gestures.doubleTapZoom)
        }
        section(SECTION_STYLUS) {
            bool(settings.stylus.predictInk)
            STYLUS_ACTIONS.write(this, settings.stylus.primaryButton)
            STYLUS_ACTIONS.write(this, settings.stylus.secondaryButton)
            HOLD_ACTIONS.write(this, settings.stylus.primaryHold)
            ERASER_ENDS.write(this, settings.stylus.eraserEnd)
        }
        section(SECTION_CANVAS) {
            bool(settings.canvas.continueFromLastPage)
            bool(settings.canvas.keepScreenOn)
            bool(settings.canvas.hideSystemBars)
        }
        section(SECTION_NOTE_NAME) { NOTE_NAMES.write(this, settings.newNoteName) }
    }

    fun decode(bytes: ByteArray): AppSettings = decodeFile(bytes, SETTINGS_MAGIC) {
        var settings = AppSettings()
        while (remaining > 0) {
            val tag = varint()
            val size = count(1)
            val endsWithRemaining = remaining - size
            settings = when (tag) {
                SECTION_GESTURES -> settings.copy(
                    gestures = settings.gestures.copy(multiTapWindowMs = long(), tapMaxDurationMs = long()),
                )
                SECTION_FINGERS -> settings.copy(
                    gestures = settings.gestures.copy(
                        oneFinger = ONE_FINGER.read(this),
                        twoFingers = TWO_FINGERS.read(this),
                        undoTaps = bool(),
                        redoTaps = bool(),
                        doubleTapZoom = bool(),
                    ),
                )
                SECTION_STYLUS -> settings.copy(
                    stylus = StylusSettings(
                        bool(),
                        STYLUS_ACTIONS.read(this),
                        STYLUS_ACTIONS.read(this),
                        HOLD_ACTIONS.read(this),
                        ERASER_ENDS.read(this),
                    ),
                )
                SECTION_CANVAS -> settings.copy(canvas = CanvasSettings(bool(), bool(), bool()))
                SECTION_NOTE_NAME -> settings.copy(newNoteName = NOTE_NAMES.read(this))
                SECTION_SCROLL -> settings.copy(scrollDirection = SCROLL_DIRECTIONS.read(this))
                SECTION_NEW_PAGES -> settings.copy(
                    newPages = NewPageDefaults(pageSize(), PageBackground(int(), template())),
                )
                SECTION_LAYOUTS -> settings.copy(customLayouts = List(count(1)) { customLayout() })
                SECTION_KEYS -> settings.copy(keyBindings = readKeys())
                SECTION_CALIBRATION -> settings.copy(calibration = ScreenCalibration(float()))
                else -> settings
            }
            // Whatever a newer version added to the end of a section, or a section unknown here.
            skip(remaining - endsWithRemaining)
        }
        settings
    }
}

private inline fun ByteWriter.section(tag: Int, body: ByteWriter.() -> Unit) {
    val section = ByteWriter(capacity = 256).apply(body).toByteArray()
    varint(tag)
    varint(section.size)
    bytes(section)
}

private fun ByteReader.readKeys(): KeyBindings {
    val actions = EditorAction.entries.associateBy { it.name }
    val overrides = HashMap<EditorAction, KeyChord?>()
    repeat(count(1)) {
        val action = actions[string()]
        val chord = if (bool()) KeyChord(varint(), byte()) else null
        // An action only a newer version knows is dropped.
        if (action != null) overrides[action] = chord
    }
    return KeyBindings.ofOverrides(overrides)
}
