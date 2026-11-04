package app.betternotes.core.settings

import app.betternotes.core.format.writeAtomically
import java.io.File
import java.io.IOException

/** The settings file. Calls block on the disk. */
class SettingsStore(private val file: File) {
    /**
     * The saved settings, or the defaults if there are none or they cannot be read. Nothing here is
     * worth refusing to start the app over, and a damaged file gets replaced by the next save.
     */
    fun read(): AppSettings {
        if (!file.exists()) return AppSettings()
        return try {
            SettingsCodec.decode(file.readBytes())
        } catch (_: IOException) {
            AppSettings()
        }
    }

    fun write(settings: AppSettings) = writeAtomically(file, SettingsCodec.encode(settings))
}
