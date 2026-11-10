package app.betternotes.ui.theme

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/** The languages the app's text comes in. A name is shown in its own language. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    SPANISH("es-419", "Español");

    /** Switches the whole app and remembers the choice. The system recreates what is on screen. */
    fun apply(context: Context) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }

    companion object {
        /** The language in use: the one chosen, or before any choice, the closest to the device's. */
        fun current(context: Context): AppLanguage {
            val locale = context.resources.configuration.locales[0]
            return if (locale.language == "es") SPANISH else ENGLISH
        }
    }
}
