package app.betternotes.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.betternotes.canvas.CanvasView
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.changeAppearance
import app.betternotes.search.DocumentSearch
import app.betternotes.settings.SettingsRepository

/** What the settings UI is showing over the editor, if anything. */
enum class SettingsOverlay { SCREEN, STYLUS_FINGER, TEMPLATE, EXPORT, SEARCH }

/** The dimming behind a sheet, measured from the reference. */
private val Scrim = Color(0x54000000)

private val StylusSheetWidth = 770.dp
private val StylusSheetTop = 100.dp
private val StylusSheetBottom = 100.dp
private val TemplateSheetWidth = 854.dp
private val ExportSheetWidth = 790.dp
private val ExportSheetMargin = 70.dp
private val TemplateSheetMargin = 36.dp

/**
 * Shows [overlay] and connects it to the settings and the open document. Whoever owns the editor's
 * toolbars only has to choose which overlay to show.
 */
@Composable
fun SettingsOverlayHost(
    overlay: SettingsOverlay?,
    repository: SettingsRepository,
    canvas: CanvasView,
    onDismiss: () -> Unit,
    dark: Boolean = true,
    search: DocumentSearch? = null,
) {
    if (overlay == null) return
    val settings by repository.settings.collectAsState()
    SettingsTheme(dark) {
        when (overlay) {
            SettingsOverlay.EXPORT -> canvas.openEditor?.let { editor ->
                SheetFrame(ExportSheetWidth, ExportSheetMargin, ExportSheetMargin, onDismiss) { modifier ->
                    ExportSheet(editor, onDismiss, modifier)
                }
            }
            SettingsOverlay.SEARCH -> canvas.openEditor?.let { SearchHost(it, search, canvas, onDismiss) }
            SettingsOverlay.SCREEN -> SettingsScreen(settings, canvas.reportedDpi, repository::update, onDismiss)
            SettingsOverlay.STYLUS_FINGER -> SheetFrame(StylusSheetWidth, StylusSheetTop, StylusSheetBottom, onDismiss) { modifier ->
                StylusFingerSheet(settings, repository::update, onDismiss, modifier)
            }
            SettingsOverlay.TEMPLATE -> TemplateHost(repository, canvas, onDismiss)
        }
    }
}

@Composable
private fun TemplateHost(repository: SettingsRepository, canvas: CanvasView, onDismiss: () -> Unit) {
    val document = canvas.openDocument ?: return
    val settings by repository.settings.collectAsState()
    val page = document.pages[canvas.currentPageIndex.coerceIn(document.pages.indices)]
    // A layout being edited: null when not editing, and an empty holder for a new one.
    var editing by remember { mutableStateOf<LayoutEdit?>(null) }

    val edit = editing
    if (edit != null) {
        CustomLayoutSheet(
            edit.layout,
            onSave = { layout ->
                repository.update { it.withCustomLayout(layout) }
                editing = null
            },
            onDelete = { name ->
                repository.update { it.withoutCustomLayout(name) }
                editing = null
            },
            onClose = { editing = null },
        )
        return
    }
    SheetFrame(TemplateSheetWidth, TemplateSheetMargin, TemplateSheetMargin, onDismiss) { modifier ->
        TemplateSheet(
            current = page.background,
            pageSize = page.size,
            canResize = page.isResizable,
            canBeInfinite = document.canBeInfinite,
            customLayouts = settings.customLayouts,
            onApply = { background, size, infinite, scope ->
                val ids = if (scope == ApplyScope.CURRENT_PAGE) listOf(page.id) else document.pages.map { it.id }
                document.changeAppearance(ids, background, size, infinite)?.let(canvas::perform)
                onDismiss()
            },
            onNewLayout = { editing = LayoutEdit(null) },
            onEditLayout = { editing = LayoutEdit(it) },
            onDeleteLayout = { name -> repository.update { it.withoutCustomLayout(name) } },
            onClose = onDismiss,
            modifier = modifier,
        )
    }
}

private class LayoutEdit(val layout: CustomLayout?)

/** Centers a sheet over a dimmed editor. Tapping outside it or pressing back closes it. */
@Composable
private fun SheetFrame(width: Dp, top: Dp, bottom: Dp, onDismiss: () -> Unit, sheet: @Composable (Modifier) -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        sheet(
            Modifier
                .padding(top = top, bottom = bottom)
                .widthIn(max = width)
                .fillMaxWidth()
                .fillMaxHeight()
                // Taps on the sheet must not reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
    }
}
