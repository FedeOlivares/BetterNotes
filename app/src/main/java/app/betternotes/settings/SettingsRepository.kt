package app.betternotes.settings

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.KeyBindings
import app.betternotes.core.settings.KeyChord
import app.betternotes.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The app's settings: one current value to watch, written to disk in the background after each change.
 * [update] is for the main thread. The file is small enough to read during startup.
 */
class SettingsRepository(private val store: SettingsStore) {
    constructor(context: Context) : this(SettingsStore(File(context.filesDir, "settings.bns")))

    private val state = MutableStateFlow(store.read())
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "settings-io") }

    val settings: StateFlow<AppSettings> = state

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(state.value)
        if (next == state.value) return
        state.value = next
        writer.execute {
            try {
                store.write(next)
            } catch (e: IOException) {
                Log.e(TAG, "Could not save the settings", e)
            }
        }
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}

/**
 * The action a key press asks for, or null. Pass only keys nothing else consumed, so that typing in a
 * text field never fires a single-letter shortcut. A held key repeats only for actions that allow it.
 */
fun KeyBindings.actionFor(event: KeyEvent): EditorAction? {
    if (event.action != KeyEvent.ACTION_DOWN || KeyEvent.isModifierKey(event.keyCode)) return null
    var modifiers = 0
    if (event.isCtrlPressed) modifiers += KeyChord.CTRL
    if (event.isShiftPressed) modifiers += KeyChord.SHIFT
    if (event.isAltPressed) modifiers += KeyChord.ALT
    if (event.isMetaPressed) modifiers += KeyChord.META
    val action = actionFor(KeyChord(event.keyCode, modifiers)) ?: return null
    return action.takeIf { event.repeatCount == 0 || it.repeats }
}
