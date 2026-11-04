package app.betternotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.BuildConfig
import app.betternotes.canvas.CanvasTool
import app.betternotes.canvas.CanvasView
import app.betternotes.core.gesture.InputMode
import app.betternotes.core.model.InkStyle
import app.betternotes.core.tools.EraseMode
import kotlin.math.roundToInt

private val PillColor = Color(0xE01C1E22)
private const val ERASER_RADIUS_PT = 6f

/**
 * A plain stand-in for the real toolbars, just enough to exercise the canvas. It has no screenshot to
 * match on purpose: the editor UI workstream replaces it.
 */
@Composable
internal fun DevChrome(canvas: CanvasView, readout: State<String>, openScratch: (canvasKind: Boolean) -> Unit) {
    var mode by remember { mutableStateOf(canvas.inputMode) }
    var erasing by remember { mutableStateOf(false) }
    var showingCanvas by remember { mutableStateOf(false) }
    var showReadout by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(canvas.viewport.zoom) }
    canvas.onZoomChanged = { zoom = it }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(PillColor, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            ChromeButton("Undo") { canvas.undo() }
            ChromeButton("Redo") { canvas.redo() }
            ChromeButton(if (mode == InputMode.FINGER_DRAW) "Finger draws" else "Stylus draws") {
                mode = if (mode == InputMode.FINGER_DRAW) InputMode.STYLUS_DRAW else InputMode.FINGER_DRAW
                canvas.inputMode = mode
            }
            ChromeButton(if (erasing) "Eraser" else "Pen") {
                erasing = !erasing
                canvas.tool = if (erasing) {
                    CanvasTool.Eraser(EraseMode.PARTIAL, ERASER_RADIUS_PT)
                } else {
                    CanvasTool.Ink(InkStyle())
                }
            }
            ChromeButton(if (mode == InputMode.VIEW_ONLY) "View only" else "Editing") {
                mode = if (mode == InputMode.VIEW_ONLY) InputMode.STYLUS_DRAW else InputMode.VIEW_ONLY
                canvas.inputMode = mode
            }
            ChromeButton("${(zoom * 100).roundToInt()}%") { canvas.zoomTo(1f) }
            ChromeButton(if (showingCanvas) "Canvas" else "Note") {
                showingCanvas = !showingCanvas
                openScratch(showingCanvas)
            }
            if (BuildConfig.PERF_TOOLS) {
                ChromeButton(if (showReadout) "Perf on" else "Perf off") { showReadout = !showReadout }
            }
        }
        if (showReadout && readout.value.isNotEmpty()) {
            Text(
                readout.value,
                color = Color(0xFFB8F0C0),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(PillColor, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun ChromeButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    )
}
