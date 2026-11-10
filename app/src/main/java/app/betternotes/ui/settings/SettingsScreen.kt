package app.betternotes.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.library.LibraryPreferences
import app.betternotes.ui.library.TextDialog
import app.betternotes.ui.theme.AppLanguage
import app.betternotes.ui.theme.ThemeMode
import app.betternotes.core.layout.ScrollDirection
import app.betternotes.core.settings.AppSettings
import app.betternotes.core.settings.AutoName
import app.betternotes.core.settings.EditorAction
import app.betternotes.core.settings.KeyChord
import app.betternotes.core.settings.NewNoteName
import app.betternotes.ui.theme.TemplateLanguage
import app.betternotes.core.settings.ScreenCalibration
import app.betternotes.search.InkModels
import android.view.KeyEvent
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val BYTES_PER_MB = 1024.0 * 1024.0

/** The longest whole length that fits on the tablet's short side, as a longer line gives a truer reading. */
private const val CALIBRATION_LINE_CM = 8f
private const val CM_PER_INCH = 2.54f

private enum class SettingsPage { MAIN, SCROLL, SHORTCUTS, CALIBRATION }

/**
 * The app's settings screen. Matches the owner's screenshot, without what depends on being online or
 * on an account. Scroll and layout, shortcuts, calibration and handwriting have no screenshot, so they
 * follow the same style. [reportedDpi] is what the device claims, which calibration corrects.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    reportedDpi: Float,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    val back = { if (page == SettingsPage.MAIN) onBack() else page = SettingsPage.MAIN }
    val title = stringResource(
        when (page) {
            SettingsPage.MAIN -> R.string.settings_title
            SettingsPage.SCROLL -> R.string.settings_scroll_layout
            SettingsPage.SHORTCUTS -> R.string.settings_shortcuts
            SettingsPage.CALIBRATION -> R.string.settings_calibration
        },
    )
    androidx.activity.compose.BackHandler(onBack = back)
    SettingsPageFrame(title, back, modifier) {
        when (page) {
            SettingsPage.MAIN -> MainPage(settings, onChange) { page = it }
            SettingsPage.SCROLL -> ScrollPage(settings.scrollDirection) { value -> onChange { it.copy(scrollDirection = value) } }
            SettingsPage.SHORTCUTS -> ShortcutsPage(settings, onChange)
            SettingsPage.CALIBRATION -> CalibrationPage(settings, reportedDpi, onChange)
        }
    }
}

@Composable
private fun SettingsPageFrame(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSettingsColors.current
    Column(modifier.fillMaxSize().background(colors.screen)) {
        Box(Modifier.fillMaxWidth().padding(top = 52.dp).height(72.dp)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 22.dp)) {
                RoundIcon(SettingsIcons.Back, 30.dp, stringResource(R.string.settings_back), onBack)
            }
            Text(title, color = colors.title, fontSize = 24.5.sp, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(27.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 137.dp, vertical = 25.dp),
            content = content,
        )
    }
}

@Composable
private fun MainPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, open: (SettingsPage) -> Unit) {
    val canvas = settings.canvas
    GeneralCard()
    val context = LocalContext.current
    SettingsCard {
        CardTitle(stringResource(R.string.settings_notes))
        DropdownRow(
            stringResource(R.string.settings_new_note_name),
            settings.newNoteName,
            NewNoteName.entries,
            { noteName(it) },
        ) { value -> onChange { it.copy(newNoteName = value) } }
        DropdownRow(
            stringResource(R.string.settings_auto_name),
            settings.autoName,
            AutoName.entries,
            { stringResource(if (it == AutoName.AUTO) R.string.settings_auto_name_auto else R.string.settings_auto_name_off) },
            detail = stringResource(R.string.settings_auto_name_detail),
        ) { value -> onChange { it.copy(autoName = value) } }
    }
    FontsCard()
    SettingsCard {
        CardTitle(stringResource(R.string.settings_canvas))
        ToggleRow(stringResource(R.string.settings_continue), canvas.continueFromLastPage) { value ->
            onChange { it.copy(canvas = it.canvas.copy(continueFromLastPage = value)) }
        }
        ToggleRow(stringResource(R.string.settings_keep_on), canvas.keepScreenOn, stringResource(R.string.settings_keep_on_detail)) { value ->
            onChange { it.copy(canvas = it.canvas.copy(keepScreenOn = value)) }
        }
        ToggleRow(stringResource(R.string.settings_hide_bars), canvas.hideSystemBars, stringResource(R.string.settings_hide_bars_detail)) { value ->
            onChange { it.copy(canvas = it.canvas.copy(hideSystemBars = value)) }
        }
        ToggleRow(stringResource(R.string.settings_snap), canvas.snapToAlign, stringResource(R.string.settings_snap_detail)) { value ->
            onChange { it.copy(canvas = it.canvas.copy(snapToAlign = value)) }
        }
        LinkRow(stringResource(R.string.settings_scroll_layout)) { open(SettingsPage.SCROLL) }
        LinkRow(stringResource(R.string.settings_shortcuts)) { open(SettingsPage.SHORTCUTS) }
        LinkRow(stringResource(R.string.settings_calibration), stringResource(R.string.settings_calibration_detail)) { open(SettingsPage.CALIBRATION) }
    }
    PomodoroCard(settings, onChange)
    HandwritingCard()
    StorageCard()
    Spacer(Modifier.height(12.dp))
}

/** No screenshot shows these, since Notewise's account screen held them. They share its cards' style. */
@Composable
private fun GeneralCard() {
    val context = LocalContext.current
    val preferences = remember { LibraryPreferences.get(context) }
    var askingName by remember { mutableStateOf(false) }
    SettingsCard {
        CardTitle(stringResource(R.string.library_general))
        DropdownRow(
            stringResource(R.string.library_language),
            AppLanguage.current(context),
            AppLanguage.entries,
            { it.nativeName },
        ) { it.apply(context) }
        DropdownRow(
            stringResource(R.string.settings_template_language),
            preferences.templateLanguage,
            TemplateLanguage.entries,
            {
                when (it) {
                    TemplateLanguage.AUTO -> stringResource(R.string.settings_template_language_auto)
                    TemplateLanguage.ENGLISH -> "English"
                    TemplateLanguage.SPANISH -> "Español"
                }
            },
            detail = stringResource(R.string.settings_template_language_detail),
        ) { preferences.updateTemplateLanguage(it) }
        ToggleRow(stringResource(R.string.library_dark_mode), preferences.theme == ThemeMode.DARK) { dark ->
            preferences.updateTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        }
        LinkRow(stringResource(R.string.library_change_name), preferences.userName) { askingName = true }
    }
    if (askingName) {
        TextDialog(
            stringResource(R.string.library_ask_name),
            initial = preferences.userName.orEmpty(),
            confirmLabel = stringResource(R.string.library_save),
            onConfirm = {
                preferences.updateUserName(it)
                askingName = false
            },
            onDismiss = { askingName = false },
        )
    }
}

@Composable
private fun noteName(name: NewNoteName) = when (name) {
    NewNoteName.DATED -> stringResource(R.string.settings_note_dated, LocalDate.now().toString())
    NewNoteName.UNTITLED -> stringResource(R.string.settings_note_untitled)
}

/**
 * The models' state and the one button in the app that downloads anything. It never downloads by
 * itself, as the owner decided, so if the models go missing it only says so.
 */
@Composable
private fun HandwritingCard() {
    val colors = LocalSettingsColors.current
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { missing = withContext(Dispatchers.IO) { missingModels() } }
    SettingsCard {
        CardTitle(stringResource(R.string.settings_handwriting))
        val models = missing
        val detail = when {
            working -> stringResource(R.string.settings_models_working)
            failed -> stringResource(R.string.settings_models_failed)
            models == null -> stringResource(R.string.settings_models_checking)
            models.isEmpty() -> stringResource(R.string.settings_models_ready)
            else -> stringResource(R.string.settings_models_missing)
        }
        SettingRow(stringResource(R.string.settings_models), detail = detail) {
            if (!working && !models.isNullOrEmpty()) {
                Text(
                    stringResource(R.string.settings_models_download),
                    color = colors.accent,
                    fontSize = 19.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            working = true
                            failed = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try {
                                        InkModels.download()
                                        true
                                    } catch (_: IOException) {
                                        false
                                    }
                                }
                                failed = !ok
                                missing = withContext(Dispatchers.IO) { missingModels() }
                                working = false
                            }
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}

private fun missingModels(): List<String>? = try {
    InkModels.missing()
} catch (_: IOException) {
    null
}

/** How much the app's files take, and the one thing safe to delete: the cache. */
@Composable
private fun StorageCard() {
    val colors = LocalSettingsColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableLongStateOf(0L) }
    var cache by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notes = sizeOf(File(context.filesDir, "documents"))
            cache = sizeOf(context.cacheDir)
        }
    }
    SettingsCard {
        CardTitle(stringResource(R.string.settings_storage))
        Spacer(Modifier.height(12.dp))
        StorageLine(stringResource(R.string.settings_storage_notes), notes, stringResource(R.string.settings_storage_notes_detail))
        Spacer(Modifier.height(18.dp))
        StorageLine(stringResource(R.string.settings_storage_cache), cache, stringResource(R.string.settings_storage_cache_detail))
        Text(
            stringResource(R.string.settings_clear_cache),
            color = colors.accent,
            fontSize = 19.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.End)
                .clickable {
                    scope.launch {
                        cache = withContext(Dispatchers.IO) {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            sizeOf(context.cacheDir)
                        }
                    }
                }
                .padding(16.dp),
        )
    }
}

@Composable
private fun StorageLine(label: String, bytes: Long, detail: String) {
    val colors = LocalSettingsColors.current
    Row {
        Text(label, color = colors.text, fontSize = 19.5.sp, modifier = Modifier.width(122.dp))
        Text(stringResource(R.string.settings_storage_size, bytes / BYTES_PER_MB), color = colors.text, fontSize = 19.5.sp)
    }
    Text(detail, color = colors.secondary, fontSize = 17.sp)
}

private fun sizeOf(root: File): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
private fun ScrollPage(value: ScrollDirection, onChange: (ScrollDirection) -> Unit) {
    SettingsCard {
        RadioRow(stringResource(R.string.scroll_vertical), value == ScrollDirection.VERTICAL) { onChange(ScrollDirection.VERTICAL) }
        RadioRow(stringResource(R.string.scroll_left_to_right), value == ScrollDirection.LEFT_TO_RIGHT) { onChange(ScrollDirection.LEFT_TO_RIGHT) }
        RadioRow(stringResource(R.string.scroll_right_to_left), value == ScrollDirection.RIGHT_TO_LEFT) { onChange(ScrollDirection.RIGHT_TO_LEFT) }
    }
}

/** Every action with its chord. Tapping one waits for the next key combination pressed. */
@Composable
private fun ShortcutsPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val colors = LocalSettingsColors.current
    var capturing by remember { mutableStateOf<EditorAction?>(null) }
    var conflict by remember { mutableStateOf<Pair<EditorAction, KeyChord>?>(null) }
    val focus = remember { FocusRequester() }
    val bindings = settings.keyBindings
    LaunchedEffect(capturing) { if (capturing != null) focus.requestFocus() }

    SettingsCard(
        Modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val action = capturing ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val code = event.key.nativeKeyCode
                if (KeyEvent.isModifierKey(code)) return@onPreviewKeyEvent true
                var modifiers = 0
                if (event.isCtrlPressed) modifiers += KeyChord.CTRL
                if (event.isShiftPressed) modifiers += KeyChord.SHIFT
                if (event.isAltPressed) modifiers += KeyChord.ALT
                if (event.isMetaPressed) modifiers += KeyChord.META
                val chord = KeyChord(code, modifiers)
                conflict = bindings.conflictFor(action, chord)?.let { it to chord }
                onChange { it.copy(keyBindings = it.keyBindings.rebind(action, chord)) }
                capturing = null
                true
            }
            .focusable(),
    ) {
        Text(
            when {
                capturing != null -> stringResource(R.string.shortcuts_press)
                conflict != null -> stringResource(R.string.shortcuts_taken, actionName(conflict!!.first), chordName(conflict!!.second))
                else -> stringResource(R.string.shortcuts_hint)
            },
            color = colors.secondary,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        for (action in EditorAction.entries) {
            val chord = bindings.chordFor(action)
            SettingRow(actionName(action), onClick = { capturing = action }) {
                val text = when {
                    capturing == action -> stringResource(R.string.shortcuts_waiting)
                    chord != null -> chordName(chord)
                    else -> stringResource(R.string.shortcuts_none)
                }
                ValuePill(text)
            }
        }
        Text(
            stringResource(R.string.shortcuts_reset),
            color = colors.accent,
            fontSize = 19.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onChange { it.copy(keyBindings = app.betternotes.core.settings.KeyBindings.Default) } }
                .padding(16.dp),
        )
    }
}

/** The names are in a resource array, in the order of the enum, so that they can be translated. */
@Composable
private fun actionName(action: EditorAction): String = stringArrayResource(R.array.editor_action_names)[action.ordinal]

private fun chordName(chord: KeyChord): String {
    val parts = ArrayList<String>()
    if (chord.modifiers and KeyChord.CTRL != 0) parts += "Ctrl"
    if (chord.modifiers and KeyChord.ALT != 0) parts += "Alt"
    if (chord.modifiers and KeyChord.SHIFT != 0) parts += "Shift"
    if (chord.modifiers and KeyChord.META != 0) parts += "Meta"
    parts += KeyEvent.keyCodeToString(chord.keyCode).removePrefix("KEYCODE_").replace('_', ' ')
    return parts.joinToString("+")
}

/**
 * Shows a line meant to be 10 cm, drawn with the density in use. If a real ruler disagrees, entering
 * what it measures corrects the density, and so everything shown at real size.
 */
@Composable
private fun CalibrationPage(settings: AppSettings, reportedDpi: Float, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val colors = LocalSettingsColors.current
    val dpi = settings.calibration?.dpi ?: reportedDpi
    var measured by remember { mutableStateOf("") }
    val measuredCm = measured.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
    SettingsCard {
        Text(stringResource(R.string.calibration_hint, CALIBRATION_LINE_CM.toInt()), color = colors.text, fontSize = 20.sp, modifier = Modifier.padding(bottom = 20.dp))
        val lineWidthPx = CALIBRATION_LINE_CM / CM_PER_INCH * dpi
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val y = size.height / 2f
            val stroke = 3f
            drawLine(colors.accent, Offset(0f, y), Offset(lineWidthPx, y), stroke)
            drawLine(colors.accent, Offset(0f, 0f), Offset(0f, size.height), stroke)
            drawLine(colors.accent, Offset(lineWidthPx, 0f), Offset(lineWidthPx, size.height), stroke)
        }
        Spacer(Modifier.height(20.dp))
        SettingRow(stringResource(R.string.calibration_density)) {
            ValuePill(dpi.roundToInt().toString())
        }
        SettingRow(stringResource(R.string.calibration_measured)) {
            BasicTextField(
                measured,
                { measured = it.take(MAX_MEASURE_CHARS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(color = colors.text, fontSize = 19.5.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.sheet)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            if (settings.calibration != null) {
                TextAction(stringResource(R.string.calibration_reset)) {
                    onChange { it.copy(calibration = null) }
                    measured = ""
                }
            }
            if (measuredCm != null) {
                TextAction(stringResource(R.string.calibration_apply)) {
                    val next = runCatching { ScreenCalibration.fromMeasurement(dpi, CALIBRATION_LINE_CM, measuredCm) }.getOrNull()
                    if (next != null) onChange { it.copy(calibration = next) }
                    measured = ""
                }
            }
        }
    }
}

private const val MAX_MEASURE_CHARS = 6

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = LocalSettingsColors.current.accent,
        fontSize = 19.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick).padding(16.dp),
    )
}

