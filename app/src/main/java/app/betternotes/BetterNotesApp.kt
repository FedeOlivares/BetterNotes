package app.betternotes

import android.app.Application
import app.betternotes.canvas.BrushTextures

class BetterNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BrushTextures.install(assets)
    }
}
