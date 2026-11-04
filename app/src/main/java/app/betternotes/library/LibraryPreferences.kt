package app.betternotes.library

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import app.betternotes.core.library.LibrarySort
import app.betternotes.ui.theme.ThemeMode

enum class LibraryLayout { GRID, LIST }

/** Small choices on the home screen, kept between runs. Observable from Compose. Use from the main thread. */
class LibraryPreferences private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme by mutableStateOf(enumOrDefault(preferences.getString(KEY_THEME, null), ThemeMode.DARK))
        private set

    var sort by mutableStateOf(enumOrDefault(preferences.getString(KEY_SORT, null), LibrarySort.MODIFIED))
        private set

    var layout by mutableStateOf(enumOrDefault(preferences.getString(KEY_LAYOUT, null), LibraryLayout.GRID))
        private set

    /** What the greeting calls the owner. Null until they have been asked, since there is no account. */
    var userName by mutableStateOf(preferences.getString(KEY_NAME, null))
        private set

    fun updateLayout(value: LibraryLayout) {
        layout = value
        preferences.edit { putString(KEY_LAYOUT, value.name) }
    }

    fun updateUserName(name: String) {
        userName = name
        preferences.edit { putString(KEY_NAME, name) }
    }

    fun updateTheme(mode: ThemeMode) {
        theme = mode
        preferences.edit { putString(KEY_THEME, mode.name) }
    }

    fun updateSort(order: LibrarySort) {
        sort = order
        preferences.edit { putString(KEY_SORT, order.name) }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_SORT = "library_sort"
        private const val KEY_LAYOUT = "library_layout"
        private const val KEY_NAME = "user_name"

        private var instance: LibraryPreferences? = null

        /** One instance, so a choice made in settings shows at once on the screen behind it. */
        fun get(context: Context): LibraryPreferences =
            instance ?: LibraryPreferences(context.applicationContext).also { instance = it }
    }
}
