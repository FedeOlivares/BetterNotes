package app.betternotes.ui.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import kotlin.math.abs
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.betternotes.R
import app.betternotes.canvas.AudioPlayback
import app.betternotes.canvas.CanvasView
import app.betternotes.core.model.AudioItem
import app.betternotes.ui.icons.EditorIcons
import app.betternotes.ui.theme.LocalEditorColors
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.util.UUID

/** Records and plays the document's voice recordings. Main thread only. */
@Stable
internal class RecorderState(private val context: Context, private val canvas: CanvasView) : AudioPlayback {
    var menuOpen by mutableStateOf(false)
    var recording by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var playingId by mutableStateOf<Long?>(null)
        private set

    /** The recording being dragged out of the menu, and where the pen is, in window pixels. */
    var dragging by mutableStateOf<AudioItem?>(null)
        private set
    var dragX by mutableFloatStateOf(0f)
        private set
    var dragY by mutableFloatStateOf(0f)
        private set

    init {
        canvas.audio = this
    }

    fun dragTo(item: AudioItem, windowX: Float, windowY: Float) {
        dragging = item
        dragX = windowX
        dragY = windowY
    }

    /** Ends a drag from the menu, putting the recording on the canvas if it was let go over a page. */
    fun drop(item: AudioItem) {
        dragging = null
        val at = IntArray(2)
        canvas.getLocationInWindow(at)
        if (canvas.placeAudio(item.id, dragX - at[0], dragY - at[1])) menuOpen = false
    }

    fun cancelDrag() {
        dragging = null
    }

    override fun isPlaying(id: Long) = playingId == id

    override fun fraction(id: Long): Float {
        val active = player ?: return 0f
        if (playingId != id) return 0f
        val length = active.duration
        return if (length > 0) (active.currentPosition.toFloat() / length).coerceIn(0f, 1f) else 0f
    }

    override fun toggle(item: AudioItem) = play(item)

    override fun seek(item: AudioItem, fraction: Float) {
        if (playingId != item.id) play(item)
        val active = player ?: return
        if (playingId == item.id) active.seekTo((active.duration * fraction).toInt())
    }

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var assetId = ""
    private var file: File? = null
    private var resumedAtMs = 0L
    private var banked = 0L

    fun start() {
        val id = UUID.randomUUID().toString()
        val target = canvas.assetFile(id) ?: return
        val started = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(BIT_RATE)
            setAudioSamplingRate(SAMPLE_RATE)
            setOutputFile(target)
        }
        try {
            started.prepare()
            started.start()
        } catch (e: IOException) {
            Log.e(TAG, "Could not start recording", e)
            started.release()
            return
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Could not start recording", e)
            started.release()
            return
        }
        stopPlaying()
        recorder = started
        assetId = id
        file = target
        banked = 0L
        elapsedMs = 0L
        resumedAtMs = SystemClock.elapsedRealtime()
        paused = false
        recording = true
        menuOpen = false
    }

    /** Called about once a second, to keep the clock current. */
    fun tick() {
        if (recording && !paused) elapsedMs = banked + SystemClock.elapsedRealtime() - resumedAtMs
    }

    fun togglePause() {
        val active = recorder ?: return
        if (paused) {
            active.resume()
            resumedAtMs = SystemClock.elapsedRealtime()
        } else {
            active.pause()
            banked += SystemClock.elapsedRealtime() - resumedAtMs
        }
        paused = !paused
    }

    fun stop() {
        val active = recorder ?: return
        tick()
        recorder = null
        recording = false
        try {
            active.stop()
            canvas.addAudio(assetId, elapsedMs)
        } catch (e: RuntimeException) {
            // MediaRecorder reports a recording too short to hold anything this way.
            Log.e(TAG, "Could not finish recording", e)
            file?.delete()
        } finally {
            active.release()
        }
    }

    fun play(item: AudioItem, then: () -> Unit = {}) {
        val current = playingId
        stopPlaying()
        if (current == item.id) return
        val file = canvas.assetFile(item.assetId) ?: return
        try {
            player = MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener {
                    stopPlaying()
                    then()
                }
                prepare()
                start()
            }
            playingId = item.id
        } catch (e: IOException) {
            Log.e(TAG, "Could not play a recording", e)
            stopPlaying()
        }
    }

    fun playAll(items: List<AudioItem>) {
        val first = items.firstOrNull() ?: return
        play(first) { playAll(items.drop(1)) }
    }

    fun stopPlaying() {
        player?.release()
        player = null
        playingId = null
    }

    /** Ends everything, for when the editor goes away. */
    fun release() {
        stop()
        stopPlaying()
    }

    private companion object {
        const val TAG = "Recorder"
        const val BIT_RATE = 128_000
        const val SAMPLE_RATE = 44_100
    }
}

/** The menu under the microphone. */
@Composable
internal fun RecorderMenu(state: EditorState, recorder: RecorderState, modifier: Modifier) {
    val context = LocalContext.current
    val colors = LocalEditorColors.current
    val startWithPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.start()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) state.importAudio(uri)
    }
    val recordings = remember(state.historyVersion) { state.recordings() }
    Column(
        modifier
            .width(MENU_WIDTH)
            .heightIn(max = MENU_MAX_HEIGHT)
            .shadow(12.dp, PanelShape, clip = false)
            .clip(PanelShape)
            .background(colors.panel)
            .alpha(if (recorder.dragging != null) 0.25f else 1f)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        MenuRow(EditorIcons.Plus, R.string.recorder_new) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                recorder.start()
            } else {
                startWithPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        MenuRow(EditorIcons.Download, R.string.recorder_import) { importer.launch("audio/*") }
        MenuRow(EditorIcons.PlayCircle, R.string.recorder_play_all) { recorder.playAll(recordings) }
        if (recordings.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.panelDivider))
            Spacer(Modifier.height(4.dp))
        }
        recordings.forEachIndexed { index, item ->
            RecordingRow(state, recorder, item, index + 1)
        }
    }
    recorder.dragging?.let { DraggedRecording(recorder.dragX, recorder.dragY) }
}

/** What follows the pen while a recording is dragged out of the menu: the bar it will become. */
@Composable
private fun DraggedRecording(x: Float, y: Float) {
    val colors = LocalEditorColors.current
    Popup(popupPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
            IntOffset((x - popupContentSize.width / 2f).toInt(), (y - popupContentSize.height / 2f).toInt())
    }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .padding(4.dp),
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF3B6FF5)), contentAlignment = Alignment.Center) {
                Icon(EditorIcons.Play, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(90.dp).height(3.dp).clip(CircleShape).background(colors.label))
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = colors.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(stringResource(label), color = colors.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RecordingRow(state: EditorState, recorder: RecorderState, item: AudioItem, number: Int) {
    val colors = LocalEditorColors.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val file = state.assetFile(item.assetId)
    val date = remember(file) {
        val modified = file?.lastModified() ?: 0L
        "${DateFormat.getMediumDateFormat(context).format(modified)}, ${DateFormat.getTimeFormat(context).format(modified)}"
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .clickable(role = Role.Button) { recorder.play(item) }
            .pointerInput(item.id) {
                // A sideways drag takes the recording out onto the canvas. An up-and-down one scrolls the list.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val moved = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        if (abs(over.x) > abs(over.y)) change.consume()
                    } ?: return@awaitEachGesture
                    recorder.dragTo(item, origin.x + moved.position.x, origin.y + moved.position.y)
                    val finished = drag(moved.id) { change ->
                        change.consume()
                        recorder.dragTo(item, origin.x + change.position.x, origin.y + change.position.y)
                    }
                    if (finished) recorder.drop(item) else recorder.cancelDrag()
                }
            }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(
            if (recorder.playingId == item.id) EditorIcons.Pause else EditorIcons.Play,
            stringResource(R.string.recorder_play),
            tint = colors.icon,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.recorder_item, number), color = colors.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${clockText(item.durationMs)}  |  $date", color = colors.label, fontSize = 12.sp)
        }
        Box {
            ToolbarIcon(EditorIcons.More, stringResource(R.string.editor_more), onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recorder_delete), fontSize = 14.sp) },
                    onClick = {
                        menu = false
                        recorder.stopPlaying()
                        state.deleteRecording(item)
                    },
                )
            }
        }
    }
}

/** Under the toolbar while recording: the pen, for writing notes alongside, and the recording's controls. */
@Composable
internal fun RecordingControls(state: EditorState, recorder: RecorderState, modifier: Modifier) {
    val colors = LocalEditorColors.current
    LaunchedEffect(recorder.recording, recorder.paused) {
        while (recorder.recording && !recorder.paused) {
            recorder.tick()
            delay(CLOCK_STEP_MS)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PillHeight)
                .shadow(4.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(colors.pill)
                .clickable(role = Role.Button) { state.select(ActiveTool.PEN) },
        ) {
            Icon(EditorIcons.Pen, stringResource(R.string.tool_pen), tint = colors.icon, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Pill {
            Text(
                clockText(recorder.elapsedMs),
                color = RECORDING_COLOR,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            ToolbarIcon(EditorIcons.StopCircle, stringResource(R.string.recorder_stop), tintOverride = RECORDING_COLOR, onClick = recorder::stop)
            ToolbarIcon(
                if (recorder.paused) EditorIcons.Play else EditorIcons.Pause,
                stringResource(R.string.recorder_pause),
                tintOverride = RECORDING_COLOR,
                onClick = recorder::togglePause,
            )
        }
    }
}

/** Takes the microphone's place in the toolbar while recording. */
@Composable
internal fun StopRecordingButton(recorder: RecorderState) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(ToolbarCell, PillHeight).tappable(radius = 20.dp, onClick = recorder::stop),
    ) {
        Box(Modifier.size(32.dp).background(RECORDING_COLOR, CircleShape))
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF25282D)))
    }
}

/** The dot on the right pill that says the microphone is on. */
@Composable
internal fun RecordingDot(modifier: Modifier = Modifier) {
    Box(modifier.offset(x = (-6).dp, y = 8.dp).size(7.dp).background(Color(0xFF1DD05D), CircleShape))
}

private fun clockText(ms: Long): String {
    val seconds = ms / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private val RECORDING_COLOR = Color(0xFFFFB0A6)
private val MENU_WIDTH = 340.dp
private val MENU_MAX_HEIGHT = 540.dp
private const val CLOCK_STEP_MS = 500L
