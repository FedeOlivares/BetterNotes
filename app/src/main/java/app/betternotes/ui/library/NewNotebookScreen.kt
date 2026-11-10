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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.planner.PlannerPage
import app.betternotes.core.planner.PlannerPages
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
import app.betternotes.ui.theme.LocalAppColors

private val BLUE = Color(0xFF0A84FF)
private val TileShape = RoundedCornerShape(12.dp)

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
    /** The owner chose Edit: the notebook is made with the cover's design as items to edit, and opens on the cover. */
    val edit: Boolean = false,
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
    val plannerPattern = remember { PageTemplate.Planner(PlannerPage.Cover, PlannerPages.defaultSetup(java.time.LocalDate.now())) }
    if (planner) landscape = false
    val page = if (landscape) PageSize(PageSize.A4.heightPt, PageSize.A4.widthPt) else PageSize.A4
    val template = remember(kind) { kind.newTemplate() }

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
                .shadow(32.dp, DialogShape)
                .clip(DialogShape)
                .background(colors.background)
                .border(1.5.dp, colors.border, DialogShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 18.dp),
                ) {
                    if (!coverOnly) Text(stringResource(R.string.notebook_title), color = colors.title, fontSize = 17.sp)
                    if (!coverOnly) Spacer(Modifier.height(8.dp))
                    if (!coverOnly) Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.raisedSurface)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        if (title.isEmpty()) Text(initialTitle, color = colors.secondaryText, fontSize = 20.sp, maxLines = 1)
                        BasicTextField(
                            title,
                            { title = it.take(MAX_TITLE) },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.title, fontSize = 20.sp),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!coverOnly) Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.notebook_cover), color = colors.title, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    ChoiceCard(tab == Tab.COVER, if (planner) plannerName else coverName(coverId), { tab = Tab.COVER }) {
                        CoverPreview(
                            if (planner) CoverDesign.PLANNER else CoverDesign.byId(coverId),
                            page,
                            Modifier.fillMaxWidth(if (landscape) 0.9f else 0.62f).aspectRatio(page.widthPt / page.heightPt),
                            if (planner) plannerPattern else PageTemplate.Blank,
                        )
                    }
                    if (!coverOnly && !planner) {
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.notebook_paper), color = colors.title, fontSize = 17.sp)
                        Spacer(Modifier.height(6.dp))
                        ChoiceCard(tab == Tab.PAPER, stringResource(R.string.notebook_paper_name, kindName(kind)), { tab = Tab.PAPER }) {
                            TemplatePreview(template, paperColor, page, Modifier.fillMaxWidth(if (landscape) 0.9f else 0.62f).aspectRatio(page.widthPt / page.heightPt))
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Both orientations are shown, the chosen one highlighted blue, centered above the list.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (!planner) {
                            OrientationOption(landscape = false, selected = !landscape) { landscape = false }
                            Spacer(Modifier.width(16.dp))
                            OrientationOption(landscape = true, selected = landscape) { landscape = true }
                        }
                        if (!coverOnly) {
                            // The language of the words on templates, next to the orientation.
                            if (!planner) Spacer(Modifier.width(40.dp))
                            LanguageOption("English", language == "en") { language = "en" }
                            Spacer(Modifier.width(16.dp))
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
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chosenDesign = CoverDesign.byId(coverId)
                if (onEditDesign != null && chosenDesign != null && chosenDesign.id != CoverDesign.CUSTOM_ID) {
                    Pill(stringResource(R.string.notebook_edit_design), colors.title, null, colors.border) {
                        onEditDesign(NotebookChoice(title.trim().ifEmpty { initialTitle }, coverId, PageBackground(paperColor, template), landscape, language))
                    }
                    Spacer(Modifier.width(14.dp))
                }
                if (!coverOnly && !planner && chosenDesign != null) {
                    // Once a cover is chosen, Edit makes the notebook and takes the owner straight to the cover to edit it.
                    Pill(stringResource(R.string.notebook_edit), colors.title, null, colors.border) {
                        onCreate(NotebookChoice(title.trim().ifEmpty { initialTitle }, coverId, PageBackground(paperColor, template), landscape, language, planner = false, edit = true))
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Pill(stringResource(R.string.notebook_cancel), colors.title, null, colors.border, onCancel)
                Spacer(Modifier.width(14.dp))
                Pill(stringResource(if (coverOnly) R.string.notebook_save_cover else R.string.notebook_create), Color.White, BLUE, null) {
                    onCreate(NotebookChoice(title.trim().ifEmpty { initialTitle }, coverId, PageBackground(paperColor, template), landscape, language, planner))
                }
            }
        }
        }
    }
}

private val DialogShape = RoundedCornerShape(28.dp)

private const val MAX_TITLE = 120

@Composable
private fun Pill(label: String, textColor: Color, fill: Color?, border: Color?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(52.dp)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp),
    ) {
        Text(label, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, label: String, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) BLUE else Color.Transparent, TileShape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        preview()
        Spacer(Modifier.height(8.dp))
        Text(label, color = colors.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Draws a cover with the same code the page uses. Null draws the dashed empty tile of "No cover". */
@Composable
private fun CoverPreview(design: CoverDesign?, page: PageSize, modifier: Modifier, pattern: PageTemplate = PageTemplate.Blank) {
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
    Canvas(modifier.shadow(3.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))) {
        val factor = size.width / page.widthPt
        scale(factor, factor, Offset.Zero) { design.draw(PreviewSink(this, 0f), page, pattern) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
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
    Text(choice.name, color = colors.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(132.dp)
            .clip(TileShape)
            .border(if (choice.selected) 2.dp else 0.dp, if (choice.selected) BLUE else Color.Transparent, TileShape)
            .clickable(onClick = choice.onSelect)
            .padding(8.dp),
    ) {
        CoverPreview(CoverDesign.PLANNER, PageSize.A4, Modifier.fillMaxWidth().aspectRatio(PageSize.A4.widthPt / PageSize.A4.heightPt), choice.pattern)
        Spacer(Modifier.height(8.dp))
        Text(choice.name, color = if (choice.selected) BLUE else colors.secondaryText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CoverRow(label: String, covers: List<CoverDesign?>, selected: String?, page: PageSize, onSelect: (String?) -> Unit) {
    val colors = LocalAppColors.current
    Text(label, color = colors.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        for (design in covers) {
            val chosen = design?.id == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(if (page.widthPt > page.heightPt) 190.dp else 132.dp)
                    .clip(TileShape)
                    .border(if (chosen) 2.dp else 0.dp, if (chosen) BLUE else Color.Transparent, TileShape)
                    .clickable { onSelect(design?.id) }
                    .padding(8.dp),
            ) {
                CoverPreview(design, page, Modifier.fillMaxWidth().aspectRatio(page.widthPt / page.heightPt))
                Spacer(Modifier.height(8.dp))
                Text(
                    coverName(design?.id),
                    color = if (chosen) BLUE else colors.secondaryText,
                    fontSize = 14.sp,
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
    // Scrolls as one page when the templates are taller than the screen.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.notebook_paper_color), color = colors.title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (color in PAPER_COLORS) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (color == paperColor) 3.dp else 1.dp, if (color == paperColor) BLUE else colors.border, CircleShape)
                        .clickable { onColor(color) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.notebook_paper_template), color = colors.title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (option in TemplateKind.entries) {
                val chosen = option == kind
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(132.dp)
                        .clip(TileShape)
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) BLUE else Color.Transparent, TileShape)
                        .clickable { onKind(option) }
                        .padding(8.dp),
                ) {
                    TemplatePreview(
                        option.newTemplate(),
                        paperColor,
                        page,
                        Modifier.fillMaxWidth().aspectRatio(page.widthPt / page.heightPt).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(kindName(option), color = if (chosen) BLUE else colors.secondaryText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One language for the words on templates. The chosen one is blue, like the orientation. */
@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 104.dp, height = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(BLUE.copy(alpha = 0.16f)) else Modifier)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) BLUE else colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Text(label, color = if (selected) BLUE else colors.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

/** One orientation, drawn as a page with a few scribbles as if it held text. The chosen one is blue. */
@Composable
private fun OrientationOption(landscape: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val ink = if (selected) BLUE else colors.title
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 72.dp, height = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(BLUE.copy(alpha = 0.16f)) else Modifier)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) BLUE else colors.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        val w = if (landscape) 34.dp else 25.dp
        val h = if (landscape) 25.dp else 34.dp
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