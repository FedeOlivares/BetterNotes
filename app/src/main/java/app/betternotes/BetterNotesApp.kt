package app.betternotes

import android.app.Application
import app.betternotes.canvas.BrushTextures
import app.betternotes.fonts.FontLibrary
import app.betternotes.library.LibraryPreferences

class BetterNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BrushTextures.install(assets)
        FontLibrary.install(this)
        // Reads the saved template language, so templates drawn before any screen opens already use it.
        LibraryPreferences.get(this)
    }
}
