package app.betternotes.ui.library

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.betternotes.core.model.DocumentKind
import app.betternotes.library.LibraryPreferences
import app.betternotes.library.LibraryRepository
import app.betternotes.settings.SettingsRepository
import app.betternotes.ui.MainActivity
import app.betternotes.ui.requestHighestRefreshRate
import app.betternotes.ui.text.TextNoteActivity
import app.betternotes.ui.theme.AppTheme
import app.betternotes.ui.theme.ReferenceScale
import app.betternotes.ui.theme.ThemeMode

/** The home screen, which the app opens on. */
class LibraryActivity : ComponentActivity() {
    private val repository by lazy { LibraryRepository.get(this) }

    /** A PDF or notebook file another app asked us to open, until the screen has taken it. */
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored copy has seen its intent already; reading it again would import the file twice.
        if (savedInstanceState == null) takeIncoming(intent)
        requestHighestRefreshRate()
        val preferences = LibraryPreferences.get(this)
        val settings = SettingsRepository(this)
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
                    LibraryScreen(repository, preferences, settings, ::openDocument, incoming) { incoming = null }
                }
            }
        }
    }

    /** The app is already open in front: "Open with" lands here instead of starting a second home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeIncoming(intent)
    }

    private fun takeIncoming(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) incoming = intent.data
    }

    /** Picks up what changed while the home screen was out of view, such as the note just closed. */
    override fun onStart() {
        super.onStart()
        repository.refresh()
    }

    private fun openDocument(id: Long, startAction: String?) {
        if (repository.library.value?.get(id)?.document?.kind == DocumentKind.TEXT) {
            startActivity(Intent(this, TextNoteActivity::class.java).putExtra(TextNoteActivity.EXTRA_DOCUMENT_ID, id))
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DOCUMENT_ID, id)
                .putExtra(MainActivity.EXTRA_START_ACTION, startAction),
        )
    }
}
