package app.betternotes.ui.settings

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.betternotes.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.betternotes.ui.theme.opensFrom
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.PAPER_COLORS
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TEMPLATE_LEVELS
import app.betternotes.core.template.TemplateKind
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.TextAlign
import app.betternotes.core.template.TextStyle
import app.betternotes.canvas.TemplateText
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.util.Locale
import app.betternotes.core.template.densityLevel
import app.betternotes.core.template.draw
import app.betternotes.core.template.kind
import app.betternotes.core.template.lineWeightLevel
import app.betternotes.core.template.newTemplate
import app.betternotes.core.template.withDensityLevel
import app.betternotes.core.template.withLineWeightLevel
import app.betternotes.ui.common.AddCustomColor
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalSettingsColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import kotlin.math.roundToInt

/** Which pages an apply reaches. */
enum class ApplyScope { CURRENT_PAGE, ALL_PAGES }

/** The page sizes on offer. "Keep" leaves the page's own size alone. */
private val PAGE_SIZES = listOf(PageSize.A4, PageSize.A5, PageSize.Letter, PageSize.Legal)

/** The sample each thumbnail shows, in points, so that a pattern looks as it will on a page. */
private val THUMBNAIL_PAGE = PageSize(420f, 420f)

/**
 * The template picker over the editor. Matches the owner's screenshot. It changes nothing until Apply,
 * and the caller turns what comes back into undoable edits.
 *
 * [pageSize] is null on an infinite page, and [canResize] is false on a PDF page, whose size is fixed.
 * [canBeInfinite] offers "Infinite" in the size list, which is how an infinite canvas is made.
 */
@Composable
fun TemplateSheet(
    current: PageBackground,
    pageSize: PageSize?,
    canResize: Boolean,
    canBeInfinite: Boolean,
    customLayouts: List<CustomLayout>,
    onApply: (background: PageBackground, layoutSize: PageSize?, infinite: Boolean, scope: ApplyScope) -> Unit,
    onNewLayout: () -> Unit,
    onEditLayout: (CustomLayout) -> Unit,
    onClose: () -> Unit,
    onDeleteLayout: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    var paper by remember { mutableIntStateOf(current.color) }
    var template by remember { mutableStateOf(current.template) }
    var size by remember { mutableStateOf(pageSize) }
    var customTab by remember { mutableStateOf(current.template is PageTemplate.Custom) }
    val collapsed = remember { mutableStateListOf<TemplateGroup>() }
    // The layout a long press picked for deleting, and how far through the two warnings it is.
    var deleting by remember { mutableStateOf<String?>(null) }
    var deleteStep by remember { mutableIntStateOf(0) }

    SettingsSheet(stringResource(R.string.template_title), onClose, modifier, headerHeight = 88.dp) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChoiceChip(stringResource(R.string.template_built_in), !customTab) { customTab = false }
                    Spacer(Modifier.width(Spacing.S11))
                    ChoiceChip(stringResource(R.string.template_custom), customTab) { customTab = true }
                    if (pageSize != null || canResize) {
                        Spacer(Modifier.width(Spacing.S30))
                        SizeChooser(size, enabled = canResize, canBeInfinite = canBeInfinite) {
                            size = it
                            if (it == null && !template.tiles) template = TemplateKind.BLANK.newTemplate()
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.S30))
                PaperRow(paper) { paper = it }
                Spacer(Modifier.height(45.dp))
                LevelRow(
                    stringResource(R.string.template_density),
                    template.densityLevel,
                ) { template = template.withDensityLevel(it) }
                Spacer(Modifier.height(Spacing.S30))
                LevelRow(
                    stringResource(R.string.template_line_width),
                    template.lineWeightLevel,
                ) { template = template.withLineWeightLevel(it) }
                Spacer(Modifier.height(Spacing.S18))

                if (customTab) {
                    CustomLayouts(customLayouts, template, paper, onSelect = {
                        template = PageTemplate.Custom(it)
                        paper = it.paper
                    }, onEdit = onEditLayout, onNew = onNewLayout, onLongPress = {
                        template = PageTemplate.Custom(it)
                        paper = it.paper
                        deleting = it.name
                    })
                } else {
                    for (group in TemplateGroup.entries) {
                        GroupHeader(group, selected = template.kind in group.kinds, collapsed = group in collapsed) {
                            if (group in collapsed) collapsed -= group else collapsed += group
                        }
                        if (group !in collapsed) {
                            ThumbnailRow(group, template, paper, onCanvas = size == null) { kind ->
                                template = kind.newTemplate().keepingLevelsOf(template)
                            }
                        }
                    }
                }
            }
            deleting?.takeIf { customTab }?.let { name ->
                Text(
                    stringResource(R.string.layout_delete),
                    color = colors.danger,
                    fontSize = TextSize.S22,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.S15, bottom = Spacing.S14)
                        .clip(AppShapes.Circle)
                        .background(colors.chipIdle)
                        .clickable(role = Role.Button) { deleteStep = 1 }
                        .padding(horizontal = Spacing.S32, vertical = Spacing.S18),
                )
                if (deleteStep > 0) {
                    DeleteWarning(name, deleteStep, onBack = { deleteStep = 0 }, onNext = { deleteStep = 2 }) {
                        onDeleteLayout(name)
                        deleteStep = 0
                        deleting = null
                        template = TemplateKind.BLANK.newTemplate()
                    }
                }
            }
            ApplyButton(
                enabled = template !is PageTemplate.Custom || size != null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.S15, bottom = Spacing.S14),
            ) { scope ->
                val layout = (template as? PageTemplate.Custom)?.layout
                val sizeToApply = layout?.size ?: size.takeIf { it != pageSize }
                onApply(PageBackground(paper, template), sizeToApply, layout == null && size == null && pageSize != null, scope)
            }
        }
    }
}

/** Moving to another template keeps the density and line width the sliders are showing. */
private fun PageTemplate.keepingLevelsOf(previous: PageTemplate): PageTemplate {
    val density = previous.densityLevel
    val weight = previous.lineWeightLevel
    var next = this
    if (density != null) next = next.withDensityLevel(density)
    if (weight != null) next = next.withLineWeightLevel(weight)
    return next
}

private enum class TemplateGroup(val title: Int, val kinds: List<TemplateKind>) {
    BASIC(R.string.template_group_basic, listOf(TemplateKind.BLANK, TemplateKind.LINED, TemplateKind.GRID, TemplateKind.DOTTED)),
    EDUCATION(
        R.string.template_group_education,
        listOf(TemplateKind.HEXAGONAL, TemplateKind.CORNELL_RULED, TemplateKind.CORNELL, TemplateKind.LEGAL, TemplateKind.ENGINEERING),
    ),
    MUSIC(R.string.template_group_music, listOf(TemplateKind.MUSIC_SCORE, TemplateKind.GUITAR_TAB, TemplateKind.GUITAR_SCORE)),
    PLANNING(R.string.template_group_planning, listOf(TemplateKind.SEMESTER, TemplateKind.MIND_MAP, TemplateKind.WEEK_PLANNER)),
}

@Composable
internal fun kindName(kind: TemplateKind) = stringResource(
    when (kind) {
        TemplateKind.BLANK -> R.string.template_blank
        TemplateKind.LINED -> R.string.template_rule
        TemplateKind.GRID -> R.string.template_grid
        TemplateKind.DOTTED -> R.string.template_dot
        TemplateKind.HEXAGONAL -> R.string.template_hexagonal
        TemplateKind.CORNELL_RULED -> R.string.template_cornell_a
        TemplateKind.CORNELL -> R.string.template_cornell_b
        TemplateKind.LEGAL -> R.string.template_legal
        TemplateKind.ENGINEERING -> R.string.template_engineering
        TemplateKind.MUSIC_SCORE -> R.string.template_music
        TemplateKind.GUITAR_TAB -> R.string.template_guitar_tab
        TemplateKind.GUITAR_SCORE -> R.string.template_guitar_score
        TemplateKind.SEMESTER -> R.string.template_semester
        TemplateKind.MIND_MAP -> R.string.template_mind_map
        TemplateKind.WEEK_PLANNER -> R.string.template_week_planner
    },
)

@Composable
private fun SizeChooser(size: PageSize?, enabled: Boolean, canBeInfinite: Boolean, onChange: (PageSize?) -> Unit) {
    val colors = LocalSettingsColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = enabled) { open = true },
        ) {
            Text(
                if (size == null) {
                    stringResource(R.string.template_size_infinite)
                } else {
                    stringResource(R.string.template_size, size.widthPt.roundToInt(), size.heightPt.roundToInt())
                },
                color = if (enabled) colors.title else colors.secondary,
                fontSize = TextSize.S19_5,
            )
            if (enabled) {
                Spacer(Modifier.width(Spacing.S8))
                Icon(SettingsIcons.Down, null, tint = colors.title, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(open, { open = false }) {
            for (option in PAGE_SIZES) {
                DropdownMenuItem(
                    text = { Text(sizeName(option), fontSize = TextSize.S20) },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                )
            }
            if (canBeInfinite) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.size_infinite), fontSize = TextSize.S20) },
                    onClick = {
                        open = false
                        onChange(null)
                    },
                )
            }
        }
    }
}

@Composable
private fun sizeName(size: PageSize) = when (size) {
    PageSize.A4 -> stringResource(R.string.size_a4)
    PageSize.A5 -> stringResource(R.string.size_a5)
    PageSize.Letter -> stringResource(R.string.size_letter)
    else -> stringResource(R.string.size_legal)
}

@Composable
private fun PaperRow(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalSettingsColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S11, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        for (paper in PAPER_COLORS) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(AppShapes.Circle)
                    .background(Color(paper))
                    .then(if (paper == selected) Modifier.border(3.dp, colors.accent, AppShapes.Circle) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(paper) },
            )
        }
        AddCustomColor(selected, colors.title, onSelect, 60.dp)
    }
}

@Composable
private fun LevelRow(label: String, level: Int?, onChange: (Int) -> Unit) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = if (level != null) colors.title else colors.secondary, fontSize = TextSize.S19_5)
        Spacer(Modifier.width(Spacing.S30))
        StepSlider(level ?: 1, TEMPLATE_LEVELS.last, onChange, enabled = level != null)
        Spacer(Modifier.width(Spacing.S30))
        Text(level?.toString() ?: "-", color = colors.title, fontSize = TextSize.S19_5, modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun GroupHeader(group: TemplateGroup, selected: Boolean, collapsed: Boolean, onToggle: () -> Unit) {
    val colors = LocalSettingsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 48.dp),
    ) {
        Text(stringResource(group.title), color = colors.title, fontSize = TextSize.S22, fontWeight = FontWeight.Medium)
        if (selected) {
            Spacer(Modifier.width(Spacing.S14))
            Box(Modifier.size(9.dp).background(colors.accent, AppShapes.Circle))
        }
        Spacer(Modifier.weight(1f))
        Icon(if (collapsed) SettingsIcons.Down else SettingsIcons.Up, null, tint = colors.title, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ThumbnailRow(group: TemplateGroup, selected: PageTemplate, paper: Int, onCanvas: Boolean, onSelect: (TemplateKind) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S34),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Spacing.S35, end = Spacing.S35, top = 48.dp, bottom = Spacing.S12),
    ) {
        for (kind in group.kinds) {
            val template = if (selected.kind == kind) selected else kind.newTemplate().keepingLevelsOf(selected)
            // A canvas has no edges, so only a pattern that repeats means anything on one.
            Thumbnail(kindName(kind), template, paper, selected.kind == kind, enabled = !onCanvas || template.tiles) { onSelect(kind) }
        }
    }
}

@Composable
private fun Thumbnail(name: String, template: PageTemplate, paper: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    val shape = AppShapes.Rounded9
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(138.dp).clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(138.dp)
                .shadow(3.dp, shape)
                .clip(shape)
                .then(if (selected) Modifier.border(3.dp, colors.accent, shape) else Modifier),
        ) {
            TemplatePreview(template, paper, THUMBNAIL_PAGE, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(46.dp))
        Text(
            name,
            color = when {
                selected -> colors.accent
                enabled -> colors.title
                else -> colors.secondary
            },
            fontSize = TextSize.S17,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Paints a template with the same code the page uses, so that the preview cannot disagree with it. */
@Composable
internal fun TemplatePreview(template: PageTemplate, paper: Int, page: PageSize, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) {
        drawRect(Color(paper))
        val factor = size.width / page.widthPt
        scale(factor, factor, Offset.Zero) {
            TemplatePainter.paint(this, template, page, minLine = 1f / factor, paper = paper)
        }
    }
}

internal object TemplatePainter {
    /** [minLine] keeps a layout's dark lines visible when small, while faint ruling stays faint, as in the reference. */
    fun paint(scope: DrawScope, template: PageTemplate, page: PageSize, minLine: Float = 0f, paper: Int = PageBackground.DEFAULT_PAPER_COLOR) {
        template.draw(page, Bounds(0f, 0f, page.widthPt, page.heightPt), PreviewSink(scope, minLine), paper = paper)
    }
}

private const val DARK_LINE_LUMINANCE = 0.3f

internal class PreviewSink(private val scope: DrawScope, private val minLine: Float) : TemplateSink {
    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val paint = Color(color)
        val drawn = if (paint.luminance() < DARK_LINE_LUMINANCE) maxOf(width, minLine) else width
        scope.drawLine(paint, Offset(x0, y0), Offset(x1, y1), drawn)
    }

    override fun dot(x: Float, y: Float, radius: Float, color: Int) {
        scope.drawCircle(Color(color), radius, Offset(x, y))
    }

    override fun fill(x: Float, y: Float, width: Float, height: Float, color: Int) {
        scope.drawRect(Color(color), Offset(x, y), Size(width, height))
    }

    override fun text(text: String, x: Float, baseline: Float, size: Float, color: Int, style: TextStyle, align: TextAlign) {
        scope.drawIntoCanvas { words.draw(it.nativeCanvas, text, x, baseline, size, color, style, align) }
    }

    override val language: String get() = app.betternotes.ui.theme.TemplateText.code()

    private val words = TemplateText()
}

@Composable
private fun ColumnScope.CustomLayouts(
    layouts: List<CustomLayout>,
    selected: PageTemplate,
    paper: Int,
    onSelect: (CustomLayout) -> Unit,
    onEdit: (CustomLayout) -> Unit,
    onNew: () -> Unit,
    onLongPress: (CustomLayout) -> Unit,
) {
    val colors = LocalSettingsColors.current
    val shape = AppShapes.Rounded9
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S34),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.S34, vertical = Spacing.S14),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(138.dp).clickable(role = Role.Button, onClick = onNew),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(138.dp).clip(shape).background(colors.chipIdle),
            ) {
                Icon(SettingsIcons.Plus, null, tint = colors.title, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(Spacing.S26))
            Text(stringResource(R.string.layout_new), color = colors.title, fontSize = TextSize.S17)
        }
        for (layout in layouts) {
            val isSelected = (selected as? PageTemplate.Custom)?.layout?.name == layout.name
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(138.dp)) {
                Box(
                    Modifier
                        .size(138.dp)
                        .shadow(3.dp, shape)
                        .clip(shape)
                        .then(if (isSelected) Modifier.border(3.dp, colors.accent, shape) else Modifier)
                        .combinedClickable(role = Role.RadioButton, onClickLabel = layout.name, onLongClick = { onLongPress(layout) }) { onSelect(layout) },
                ) {
                    TemplatePreview(PageTemplate.Custom(layout), layout.paper, layout.size, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(Spacing.S16))
                Text(
                    layout.name,
                    color = if (isSelected) colors.accent else colors.title,
                    fontSize = TextSize.S17,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.layout_edit),
                    color = colors.accent,
                    fontSize = TextSize.S16,
                    modifier = Modifier.clickable { onEdit(layout) }.padding(Spacing.S4),
                )
            }
        }
    }
}

private const val APPLY_PREFS = "template_sheet"
private const val APPLY_DEFAULT_KEY = "apply_all_pages"

/**
 * One blue pill. It shows the way to apply that was used last (the first time, the current page), and the arrow
 * grows the pill to show the other way underneath. Whichever the owner picks becomes the one on top next time.
 */
@Composable
private fun ApplyButton(enabled: Boolean, modifier: Modifier = Modifier, onApply: (ApplyScope) -> Unit) {
    val colors = LocalSettingsColors.current
    val preferences = LocalContext.current.getSharedPreferences(APPLY_PREFS, Context.MODE_PRIVATE)
    var first by remember {
        mutableStateOf(if (preferences.getBoolean(APPLY_DEFAULT_KEY, false)) ApplyScope.ALL_PAGES else ApplyScope.CURRENT_PAGE)
    }
    var open by remember { mutableStateOf(false) }
    val second = if (first == ApplyScope.CURRENT_PAGE) ApplyScope.ALL_PAGES else ApplyScope.CURRENT_PAGE
    fun choose(scope: ApplyScope) {
        first = scope
        open = false
        preferences.edit().putBoolean(APPLY_DEFAULT_KEY, scope == ApplyScope.ALL_PAGES).apply()
        onApply(scope)
    }

    Column(
        modifier
            .animateContentSize()
            .width(IntrinsicSize.Max)
            .clip(AppShapes.Rounded33)
            .background(if (enabled) colors.accent else colors.sliderIdle),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(66.dp)) {
            ApplyLabel(first, enabled, Modifier.weight(1f).padding(start = Spacing.S32, end = Spacing.S21)) { choose(first) }
            Box(Modifier.size(2.dp, 41.dp).background(colors.divider))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(66.dp)
                    .clickable(enabled = enabled, role = Role.Button) { open = !open }
                    .padding(start = Spacing.S24, end = Spacing.S28),
            ) {
                Icon(
                    if (open) SettingsIcons.Down else SettingsIcons.Up,
                    stringResource(R.string.template_apply_more),
                    tint = colors.onAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.divider))
            ApplyLabel(second, enabled, Modifier.fillMaxWidth().padding(horizontal = Spacing.S32)) { choose(second) }
        }
    }
}

@Composable
private fun ApplyLabel(scope: ApplyScope, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalSettingsColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(66.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        ShrinkText(
            stringResource(if (scope == ApplyScope.CURRENT_PAGE) R.string.template_apply_current else R.string.template_apply_all),
            colors.onAccent,
            TextSize.S19_5,
        )
    }
}

/** Two warnings in a row before a layout goes: a first question, then a last one that says it cannot be undone. */
@Composable
private fun DeleteWarning(name: String, step: Int, onBack: () -> Unit, onNext: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalSettingsColors.current
    Box(
        Modifier.fillMaxSize().background(UiColors.ScrimStrong).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.S18),
            modifier = Modifier.opensFrom(OpenFrom.Center).width(460.dp).clip(AppShapes.Rounded24).background(colors.card).padding(Spacing.S28),
        ) {
            val first = step == 1
            Text(
                if (first) stringResource(R.string.layout_delete_title) else stringResource(R.string.layout_delete_final_title, name),
                color = colors.text,
                fontSize = TextSize.S22,
            )
            Text(
                stringResource(if (first) R.string.layout_delete_message else R.string.layout_delete_final_message),
                color = colors.text,
                fontSize = TextSize.S19_5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S11), verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip(stringResource(R.string.layout_cancel), true, onBack)
                Text(
                    stringResource(if (first) R.string.layout_delete_continue else R.string.layout_delete_forever),
                    color = colors.danger,
                    fontSize = TextSize.S19_5,
                    modifier = Modifier
                        .clip(AppShapes.Rounded10)
                        .clickable(role = Role.Button, onClick = if (first) onNext else onDelete)
                        .padding(horizontal = Spacing.S22, vertical = Spacing.S10),
                )
            }
        }
    }
}
