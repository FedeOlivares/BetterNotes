package app.betternotes.ui

import android.app.Activity

/** Asks for the fastest mode at the current resolution: 144 Hz on the Pad 7. The system may refuse. */
internal fun Activity.requestHighestRefreshRate() {
    val current = display.mode
    val best = display.supportedModes
        .filter {
            it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight
        }
        .maxByOrNull { it.refreshRate } ?: return
    window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    window.isFrameRatePowerSavingsBalanced = false
}
