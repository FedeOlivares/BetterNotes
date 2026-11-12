package app.betternotes.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.betternotes.ui.theme.isCompactScreen
import app.betternotes.R
import app.betternotes.ui.common.AddCustomColor
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.planner.PlannerColors
import app.betternotes.core.planner.PlannerPage
import app.betternotes.core.planner.PlannerPages
import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import app.betternotes.core.template.palette
import app.betternotes.library.CoverDraft
import app.betternotes.library.ThumbnailRenderer
import app.betternotes.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.betternotes.core.template.CoverDesign
import app.betternotes.core.template.CoverGroup
import app.betternotes.core.template.PAPER_COLORS
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TemplateKind
import app.betternotes.core.template.newTemplate
import app.betternotes.library.LibraryPreferences
import app.betternotes.ui.theme.TemplateLanguage
import app.betternotes.ui.theme.TemplateText
import app.betternotes.ui.settings.PreviewSink
import app.betternotes.ui.settings.TemplatePreview
import app.betternotes.ui.settings.kindName
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalAppColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.Swatches
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors

private val BLUE = UiColors.SystemBlue
/** On a phone: the left column is this share of its width, the cover and paper choices this share of theirs (40% smaller), and the orientation and language buttons this share. */
private const val COMPACT_LEFT = 0.4f
private const val COMPACT_CHOICE = 0.6f
private const val COMPACT_SELECTOR = 0.7f

/** What the chooser sets: a cover (null for none) and the paper of the pages. */
internal class NotebookChoice(
    val title: String,
    val coverId: String?,
    val paper: PageBackground,
    val landscape: Boolean,
    /** The language of the notebook's templates, "en" or "es". */
    val language: String,
    /** A planner, which overrides the cover, paper and orientation: it has its own of each. */
    val planner: Boolean = false,
    /** Colors of the cover's design the owner changed: each key is swapped for its value. */
    val recolor: Map<Int, Int> = emptyMap(),
    /** A planner's colors ([PlannerColors]: cover, spine, stripe, page accent), or none for the defaults. */
    val plannerColors: List<Int> = emptyList(),
    /** The cover was edited on a canvas, and is in the cover draft; it goes on the notebook's cover as it is. */
    val draft: Boolean = false,
)

private enum class Tab { COVER, PAPER }

/**
 * The New Notebook screen: a title, a Cover chooser and a Paper chooser, as in the reference. The two cards on the left
 * choose which list is shown on the right. Nothing is made until "Create Notebook".
 */
@Composable
internal fun NewNotebookScreen(
    initialTitle: String,
    onCreate: (NotebookChoice) -> Unit,
    onCancel: () -> Unit,
    /** Only the cover is chosen, as when a notebook that exists gets or changes its cover: no title, paper or orientation. */
    coverOnly: Boolean = false,
    initialCover: String? = CoverDesign.DEFAULT_ID,
    initialLandscape: Boolean = false,
    /** Only when a cover is being edited: turns the chosen design into items the owner can edit, instead of keeping it drawn. */
    onEditDesign: ((NotebookChoice) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val compact = isCompactScreen()
    // The language of this notebook's templates: the app's choice to start with, then whatever is picked here. The previews
    // follow it while the dialog is open; it is kept with the notebook, and the app-wide choice is not changed.
    var language by remember { mutableStateOf(TemplateText.newNotebookCode()) }
    if (!coverOnly) {
        androidx.compose.runtime.DisposableEffect(language) {
            TemplateText.documentLanguage = language
            onDispose { TemplateText.documentLanguage = null }
        }
    }
    var title by remember { mutableStateOf("") }
    var coverId by remember { mutableStateOf(initialCover) }
    // Matches what a new note got before the chooser: cream paper with dots.
    var kind by remember { mutableStateOf(TemplateKind.DOTTED) }
    var paperColor by remember { mutableIntStateOf(PageBackground.DEFAULT_PAPER_COLOR) }
    var tab by remember { mutableStateOf(Tab.COVER) }
    var landscape by remember { mutableStateOf(initialLandscape) }
    // A planner is chosen below the covers and needs nothing else decided: its cover, paper and A4 portrait page are its own.
    var planner by remember { mutableStateOf(false) }
    val plannerName = if (language == "es") "Agenda" else "Planner"
    var plannerColors by remember { mutableStateOf(PlannerColors.DEFAULT) }
    val plannerPattern = remember(plannerColors) {
        PageTemplate.Planner(PlannerPage.Cover, PlannerPages.defaultSetup(java.time.LocalDate.now()), colors = PlannerColors.normalized(plannerColors))
    }
    if (planner) landscape = false
    val page = if (landscape) PageSize(PageSize.A4.heightPt, PageSize.A4.widthPt) else PageSize.A4
    val template = remember(kind) { kind.newTemplate() }

    // The cover's own colors, which the owner can change below its picture and see at once, before anything is made.
    val context = androidx.compose.ui.platform.LocalContext.current
    var recolor by remember(coverId) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var pickingColor by remember { mutableStateOf<Int?>(null) }
    // The cover was edited on a canvas: it is in the cover draft, and its picture is the draft's. Changing the design, the
    // orientation or the colors afterwards starts over from the design, so the draft is let go.
    var draftEdited by remember { mutableStateOf(false) }
    var draftVersion by remember { mutableStateOf(0) }
    LaunchedEffect(coverId, landscape, recolor, planner) {
        if (draftEdited) {
            draftEdited = false
            CoverDraft.discard(context)
        }
    }
    val draftPicture by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, draftEdited, draftVersion) {
        value = if (draftEdited) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    ThumbnailRenderer.renderWhole(CoverDraft.store(context), COVER_PICTURE_PX)
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }
    pickingColor?.let { original ->
        ColorSwatchDialog(
            recolor[original] ?: original,
            onPick = { picked ->
                recolor = recolor + (original to picked)
                pickingColor = null
            },
            onReset = {
                recolor = recolor - original
                pickingColor = null
            },
            onDismiss = { pickingColor = null },
        )
    }
    val editScope = androidx.compose.runtime.rememberCoroutineScope()
    val draftEditor = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) {
        // Whatever way the editor was left, the draft holds the cover as it was left.
        draftEdited = true
        draftVersion++
    }

    // The title: above the choices on a phone, whose column for them is too narrow, and at the top of that column otherwise.
    val titleBlock: @Composable () -> Unit = {
        Text(stringResource(R.string.notebook_title), color = colors.title, fontSize = TextSize.S17)
        Spacer(Modifier.height(Spacing.S8))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.Rounded10)
                .background(colors.raisedSurface)
                .padding(horizontal = Spacing.S16, vertical = Spacing.S16),
        ) {
            if (title.isEmpty()) Text(initialTitle, color = colors.secondaryText, fontSize = TextSize.S20, maxLines = 1)
            BasicTextField(
                title,
                { title = it.take(MAX_TITLE) },
                singleLine = true,
                textStyle = TextStyle(color = colors.title, fontSize = TextSize.S20),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // A dialog over the library: a touch on the dimmed area outside closes it, and the panel swallows its own touches.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            // Only a thin frost over the blurred home, which stays visible behind the dialog; the scrim color is opaque.
            .background(colors.background.copy(alpha = 0.28f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 1040.dp)
                .fillMaxHeight(0.82f)
                .shadow(32.dp, AppShapes.NewNotebookDialog)
                .clip(AppShapes.NewNotebookDialog)
                .background(colors.background)
                .border(1.5.dp, colors.border, AppShapes.NewNotebookDialog)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
        Column(Modifier.fillMaxSize()) {
            if (!coverOnly && compact) Column(Modifier.fillMaxWidth().background(colors.surface).padding(start = Spacing.S14, end = Spacing.S14, top = Spacing.S14)) { titleBlock() }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .width(if (compact) 300.dp * COMPACT_LEFT else 300.dp)
                        .fillMaxHeight()
                        .background(colors.surface)
                        .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = 18.dp),
                ) {
                    if (!coverOnly && !compact) {
                        titleBlock()
                        Spacer(Modifier.height(Spacing.S22))
                    }
                    Text(stringResource(R.string.notebook_cover), color = colors.title, fontSize = if (compact) TextSize.S13 else TextSize.S17)
                    Spacer(Modifier.height(Spacing.S6))
                    ChoiceCard(tab == Tab.COVER, if (planner) plannerName else coverName(coverId), { tab = Tab.COVER }) {
                        val picture = draftPicture
                        if (picture != null) {
                            // The cover as it was left on the canvas.
                            androidx.compose.foundation.Image(
                                picture.asImageBitmap(),
                                null,
                                modifier = Modifier
                                    .fillMaxWidth(if (landscape) 0.9f else 0.62f)
                                    .aspectRatio(page.widthPt / page.heightPt)
                                    .shadow(3.dp, AppShapes.Rounded4)
                                    .clip(AppShapes.Rounded4),
                            )
                        } else {
                            CoverPreview(
                                if (planner) CoverDesign.PLANNER else CoverDesign.byId(coverId),
                                page,
                                Modifier.fillMaxWidth(if (landscape) 0.9f else 0.62f).aspectRatio(page.widthPt / page.heightPt),
                                if (planner) plannerPattern else PageTemplate.Blank,
                                recolor,
                            )
                        }
                    }
                    if (planner) {
                        Spacer(Modifier.height(Spacing.S10))
                        PlannerColorRow(plannerColors, compact, colors.secondaryText, colors.border) { plannerColors = it }
                    }
                    // The colors the design is made of: tap one to change it, and the picture above follows.
                    val design = if (planner || draftEdited) null else CoverDesign.byId(coverId)?.takeIf { it.id != CoverDesign.CUSTOM_ID }
                    if (design != null) {
                        val palette = remember(design) { design.palette() }
                        Spacer(Modifier.height(Spacing.S10))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            for (original in palette) {
                                val shown = recolor[original] ?: original
                                Box(
                                    Modifier
                                        .padding(horizontal = if (compact) 2.dp else 4.dp)
                                        .size(if (compact) 20.dp else 30.dp)
                                        .clip(AppShapes.Circle)
                                        .background(Color(shown))
                                        .border(if (original in recolor) 3.dp else 1.dp, if (original in recolor) BLUE else colors.border, AppShapes.Circle)
                                        .clickable { pickingColor = original },
                                )
                            }
                        }
                    }
                    if (!coverOnly && !planner) {
                        Spacer(Modifier.height(Spacing.S18))
                        Text(stringResource(R.string.notebook_paper), color = colors.title, fontSize = if (compact) TextSize.S13 else TextSize.S17)
                        Spacer(Modifier.height(Spacing.S6))
                        ChoiceCard(tab == Tab.PAPER, stringResource(R.string.notebook_paper_name, kindName(kind)), { tab = Tab.PAPER }) {
                            TemplatePreview(template, paperColor, page, Modifier.fillMaxWidth(if (landscape) 0.9f else 0.62f).aspectRatio(page.widthPt / page.heightPt))
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Both orientations are shown, the chosen one highlighted blue, centered above the list.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 24.dp, vertical = if (compact) 8.dp else 14.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (!planner) {
                            OrientationOption(landscape = false, selected = !landscape) { landscape = false }
                            Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
                            OrientationOption(landscape = true, selected = landscape) { landscape = true }
                        }
                        if (!coverOnly) {
                            // The language of the words on templates, next to the orientation.
                            if (!planner) Spacer(Modifier.width(40.dp))
                            LanguageOption("English", language == "en") { language = "en" }
                            Spacer(Modifier.width(Spacing.S16))
                            LanguageOption("Español", language == "es") { language = "es" }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            Tab.COVER -> CoverList(
                                coverId,
                                page,
                                if (coverOnly) null else PlannerChoice(plannerName, planner, plannerPattern) { planner = true },
                            ) {
                                planner = false
                                coverId = it
                            }
                            Tab.PAPER -> PaperList(kind, paperColor, page, { kind = it }, { paperColor = it })
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Spacing.S24, vertical = Spacing.S16),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chosenDesign = CoverDesign.byId(coverId)
                if (onEditDesign != null && chosenDesign != null && chosenDesign.id != CoverDesign.CUSTOM_ID) {
                    Pill(stringResource(R.string.notebook_edit_design), colors.title, null, colors.border) {
                        onEditDesign(NotebookChoice(title.trim().ifEmpty { initialTitle }, coverId, PageBackground(paperColor, template), landscape, language, recolor = recolor))
                    }
                    Spacer(Modifier.width(Spacing.S14))
                }
                if (!coverOnly && !planner && chosenDesign != null) {
                    // Edit opens the cover on a canvas of its own, before any notebook exists; coming back, the dialog shows it as edited.
                    Pill(stringResource(R.string.notebook_edit), colors.title, null, colors.border) {
                        val design = chosenDesign
                        editScope.launch {
                            withContext(Dispatchers.IO) { CoverDraft.prepare(context, design, page, recolor) }
                            draftEditor.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_COVER_DRAFT, true))
                        }
                    }
                    Spacer(Modifier.width(Spacing.S14))
                }
                Pill(stringResource(R.string.notebook_cancel), colors.title, null, colors.border, onCancel)
                Spacer(Modifier.width(Spacing.S14))
                Pill(stringResource(if (coverOnly) R.string.notebook_save_cover else R.string.notebook_create), UiColors.OnFilled, BLUE, null) {
                    onCreate(NotebookChoice(title.trim().ifEmpty { initialTitle }, coverId, PageBackground(paperColor, template), landscape, language, planner, recolor, PlannerColors.normalized(plannerColors), draftEdited))
                }
            }
        }
        }
    }
}


/** How wide the edited cover is drawn for the dialog, in pixels. */
private const val COVER_PICTURE_PX = 600

private const val MAX_TITLE = 120

@Composable
private fun Pill(label: String, textColor: Color, fill: Color?, border: Color?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(52.dp)
            .clip(AppShapes.Circle)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (border != null) Modifier.border(1.5.dp, border, AppShapes.Circle) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.S30),
    ) {
        Text(label, color = textColor, fontSize = TextSize.S18, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, label: String, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.NewNotebookTile)
            .border(if (selected) 2.dp else 0.dp, if (selected) BLUE else Color.Transparent, AppShapes.NewNotebookTile)
            .clickable(onClick = onClick)
            .padding(vertical = if (isCompactScreen()) 8.dp else 14.dp),
    ) {
        preview()
        Spacer(Modifier.height(if (isCompactScreen()) 4.dp else 8.dp))
        Text(label, color = colors.title, fontSize = if (isCompactScreen()) TextSize.S12 else TextSize.S16, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Draws a cover with the same code the page uses. Null draws the dashed empty tile of "No cover". */
@Composable
private fun CoverPreview(design: CoverDesign?, page: PageSize, modifier: Modifier, pattern: PageTemplate = PageTemplate.Blank, recolor: Map<Int, Int> = emptyMap()) {
    if (design == null) {
        val line = LocalAppColors.current.secondaryText
        Canvas(modifier) {
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawRoundRect(
                line,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f, pathEffect = dash),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
        }
        return
    }
    Canvas(modifier.shadow(3.dp, AppShapes.Rounded4).clip(AppShapes.Rounded4)) {
        val factor = size.width / page.widthPt
        scale(factor, factor, Offset.Zero) {
            if (recolor.isEmpty()) design.draw(PreviewSink(this, 0f), page, pattern) else design.draw(PreviewSink(this, 0f), page.widthPt, page.heightPt, recolor)
        }
    }
}

@Composable
private fun coverName(id: String?) = CoverDesign.byId(id)?.displayName() ?: stringResource(R.string.notebook_no_cover)

@Composable
private fun groupName(group: CoverGroup) = stringResource(
    when (group) {
        CoverGroup.SIMPLE -> R.string.cover_group_simple
        CoverGroup.SOLID -> R.string.cover_group_solid
        CoverGroup.ENCLOSED -> R.string.cover_group_enclosed
        CoverGroup.BLACK_AND_WHITE -> R.string.cover_group_black_white
        CoverGroup.BROWN -> R.string.cover_group_brown
        CoverGroup.PLAIN -> R.string.cover_group_plain
        CoverGroup.BRIGHT -> R.string.cover_group_bright
        // The blank cover is in the Common row, so this group has no row of its own.
        CoverGroup.CUSTOM -> R.string.cover_group_common
    },
)

@Composable
private fun CoverList(selected: String?, page: PageSize, planner: PlannerChoice?, onSelect: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(if (isCompactScreen()) 14.dp else 24.dp)) {
        item {
            CoverRow(stringResource(R.string.cover_group_common), listOf<CoverDesign?>(null) + CoverDesign.COMMON, selected, page, onSelect)
        }
        for (group in CoverGroup.entries.filter { it != CoverGroup.CUSTOM }) {
            val covers = CoverDesign.ALL.filter { it.group == group }
            if (covers.isNotEmpty()) item(group) { CoverRow(groupName(group), covers, selected, page, onSelect) }
        }
        // Below every cover: the planner, which brings its own cover, paper and size.
        if (planner != null) item("planner") { PlannerRow(planner) }
    }
}

/** The planner entry under the covers. */
private class PlannerChoice(val name: String, val selected: Boolean, val pattern: PageTemplate, val onSelect: () -> Unit)

@Composable
private fun PlannerRow(choice: PlannerChoice) {
    val colors = LocalAppColors.current
    val k = if (isCompactScreen()) COMPACT_CHOICE else 1f
    Text(choice.name, color = colors.title, fontSize = TextSize.S22 * k, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = Spacing.S18 * k, bottom = Spacing.S12 * k))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(132.dp * k)
            .clip(AppShapes.NewNotebookTile)
            .border(if (choice.selected) 2.dp else 0.dp, if (choice.selected) BLUE else Color.Transparent, AppShapes.NewNotebookTile)
            .clickable(onClick = choice.onSelect)
            .padding(Spacing.S8 * k),
    ) {
        CoverPreview(CoverDesign.PLANNER, PageSize.A4, Modifier.fillMaxWidth().aspectRatio(PageSize.A4.widthPt / PageSize.A4.heightPt), choice.pattern)
        Spacer(Modifier.height(Spacing.S8 * k))
        Text(choice.name, color = if (choice.selected) BLUE else colors.secondaryText, fontSize = TextSize.S14 * k, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CoverRow(label: String, covers: List<CoverDesign?>, selected: String?, page: PageSize, onSelect: (String?) -> Unit) {
    val colors = LocalAppColors.current
    val k = if (isCompactScreen()) COMPACT_CHOICE else 1f
    Text(label, color = colors.title, fontSize = TextSize.S22 * k, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = Spacing.S18 * k, bottom = Spacing.S12 * k))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.S18 * k)) {
        for (design in covers) {
            val chosen = design?.id == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width((if (page.widthPt > page.heightPt) 190.dp else 132.dp) * k)
                    .clip(AppShapes.NewNotebookTile)
                    .border(if (chosen) 2.dp else 0.dp, if (chosen) BLUE else Color.Transparent, AppShapes.NewNotebookTile)
                    .clickable { onSelect(design?.id) }
                    .padding(Spacing.S8 * k),
            ) {
                CoverPreview(design, page, Modifier.fillMaxWidth().aspectRatio(page.widthPt / page.heightPt))
                Spacer(Modifier.height(Spacing.S8 * k))
                Text(
                    coverName(design?.id),
                    color = if (chosen) BLUE else colors.secondaryText,
                    fontSize = TextSize.S14 * k,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PaperList(kind: TemplateKind, paperColor: Int, page: PageSize, onKind: (TemplateKind) -> Unit, onColor: (Int) -> Unit) {
    val colors = LocalAppColors.current
    val k = if (isCompactScreen()) COMPACT_CHOICE else 1f
    // Scrolls as one page when the templates are taller than the screen.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (isCompactScreen()) 14.dp else 24.dp)) {
        Text(stringResource(R.string.notebook_paper_color), color = colors.title, fontSize = TextSize.S22 * k, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Spacing.S12 * k))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S14 * k)) {
            for (color in PAPER_COLORS) {
                Box(
                    Modifier
                        .size(44.dp * k)
                        .clip(AppShapes.Circle)
                        .background(Color(color))
                        .border(if (color == paperColor) 3.dp else 1.dp, if (color == paperColor) BLUE else colors.border, AppShapes.Circle)
                        .clickable { onColor(color) },
                )
            }
            AddCustomColor(paperColor, colors.title, onColor, 44.dp * k)
        }
        Spacer(Modifier.height(Spacing.S24 * k))
        Text(stringResource(R.string.notebook_paper_template), color = colors.title, fontSize = TextSize.S22 * k, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Spacing.S12 * k))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S18 * k), verticalArrangement = Arrangement.spacedBy(Spacing.S14 * k)) {
            for (option in TemplateKind.entries) {
                val chosen = option == kind
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(132.dp * k)
                        .clip(AppShapes.NewNotebookTile)
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) BLUE else Color.Transparent, AppShapes.NewNotebookTile)
                        .clickable { onKind(option) }
                        .padding(Spacing.S8 * k),
                ) {
                    TemplatePreview(
                        option.newTemplate(),
                        paperColor,
                        page,
                        Modifier.fillMaxWidth().aspectRatio(page.widthPt / page.heightPt).clip(AppShapes.Rounded4),
                    )
                    Spacer(Modifier.height(Spacing.S8 * k))
                    Text(kindName(option), color = if (chosen) BLUE else colors.secondaryText, fontSize = TextSize.S14 * k, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One language for the words on templates. The chosen one is blue, like the orientation. */
@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val k = if (isCompactScreen()) COMPACT_SELECTOR else 1f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 104.dp * k, height = 56.dp * k)
            .clip(AppShapes.Rounded14)
            .then(if (selected) Modifier.background(BLUE.copy(alpha = UiColors.SYSTEM_BLUE_TINT_ALPHA)) else Modifier)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) BLUE else colors.border, AppShapes.Rounded14)
            .clickable(onClick = onClick),
    ) {
        Text(label, color = if (selected) BLUE else colors.title, fontSize = TextSize.S17 * k, fontWeight = FontWeight.Medium)
    }
}

/** One orientation, drawn as a page with a few scribbles as if it held text. The chosen one is blue. */
@Composable
private fun OrientationOption(landscape: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val k = if (isCompactScreen()) COMPACT_SELECTOR else 1f
    val ink = if (selected) BLUE else colors.title
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 72.dp * k, height = 56.dp * k)
            .clip(AppShapes.Rounded14)
            .then(if (selected) Modifier.background(BLUE.copy(alpha = UiColors.SYSTEM_BLUE_TINT_ALPHA)) else Modifier)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) BLUE else colors.border, AppShapes.Rounded14)
            .clickable(onClick = onClick),
    ) {
        val w = (if (landscape) 34.dp else 25.dp) * k
        val h = (if (landscape) 25.dp else 34.dp) * k
        Canvas(Modifier.size(w, h)) {
            drawRoundRect(ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
            val left = size.width * 0.22f
            val right = size.width * 0.78f
            for ((i, share) in listOf(0.3f, 0.5f, 0.7f).withIndex()) {
                val end = if (i == 2) left + (right - left) * 0.55f else right
                drawLine(ink, Offset(left, size.height * share), Offset(end, size.height * share), 2f)
            }
        }
    }
}

private val SWATCH_SIZE = 40.dp

/** A grid of colors to pick from, with the one now in use marked, and a way back to the design's own. */
@Composable
internal fun ColorSwatchDialog(current: Int, onPick: (Int) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.notebook_pick_color), color = colors.title) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S10),
                verticalArrangement = Arrangement.spacedBy(Spacing.S10),
            ) {
                for (swatch in Swatches.Cover) {
                    Box(
                        Modifier
                            .size(SWATCH_SIZE)
                            .clip(AppShapes.Circle)
                            .background(Color(swatch))
                            .border(if (swatch == current) 3.dp else 1.dp, if (swatch == current) BLUE else colors.border, AppShapes.Circle)
                            .clickable { onPick(swatch) },
                    )
                }
                AddCustomColor(current, colors.title, onPick, SWATCH_SIZE)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onReset) { Text(stringResource(R.string.notebook_color_reset), color = BLUE) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.notebook_cancel), color = colors.title) }
        },
    )
}