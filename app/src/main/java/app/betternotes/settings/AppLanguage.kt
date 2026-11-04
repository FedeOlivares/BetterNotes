package app.betternotes.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * The languages the app's screens come in. The system stores the choice and recreates the activity
 * when it changes, so it isn't in the app's settings file.
 */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    SPANISH_LATIN_AMERICA("es-419"),
    ;

    companion object {
        /** The one in use: the choice made here, or else the closest to the device's own language. */
        fun current(context: Context): AppLanguage {
            val chosen = context.getSystemService(LocaleManager::class.java).applicationLocales
            val locale = if (chosen.isEmpty) Locale.getDefault() else chosen[0]
            return if (locale.language == "es") SPANISH_LATIN_AMERICA else ENGLISH
        }

        fun choose(context: Context, language: AppLanguage) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        }
    }
}
