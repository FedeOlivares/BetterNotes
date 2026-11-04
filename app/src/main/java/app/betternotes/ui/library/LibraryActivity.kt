package app.betternotes.ui.library

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import app.betternotes.library.LibraryPreferences
import app.betternotes.library.LibraryRepository
import app.betternotes.ui.MainActivity
import app.betternotes.ui.requestHighestRefreshRate
import app.betternotes.ui.theme.AppTheme
import app.betternotes.ui.theme.ReferenceScale
import app.betternotes.ui.theme.ThemeMode

/** The home screen, which the app opens on. */
class LibraryActivity : ComponentActivity() {
    private val repository by lazy { LibraryRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        val preferences = LibraryPreferences(this)
        setContent {
            val mode = preferences.theme
            LaunchedEffect(mode) {
                val bars = if (mode == ThemeMode.DARK) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            AppTheme(mode) {
                ReferenceScale {
                    LibraryScreen(repository, preferences, ::openDocument)
                }
            }
        }
    }

    /** Picks up what changed while the home screen was out of view, such as the note just closed. */
    override fun onStart() {
        super.onStart()
        repository.refresh()
    }

    private fun openDocument(id: Long, startAction: String?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DOCUMENT_ID, id)
                .putExtra(MainActivity.EXTRA_START_ACTION, startAction),
        )
    }
}
