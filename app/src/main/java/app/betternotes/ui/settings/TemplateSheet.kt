package app.betternotes.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.geometry.Bounds
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.template.CustomLayout
import app.betternotes.core.template.PAPER_COLORS
import app.betternotes.core.template.PageBackground
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.TEMPLATE_LEVELS
import app.betternotes.core.template.TemplateKind
import app.betternotes.core.template.TemplateSink
import app.betternotes.core.template.densityLevel
import app.betternotes.core.template.draw
import app.betternotes.core.template.kind
import app.betternotes.core.template.lineWeightLevel
import app.betternotes.core.template.newTemplate
import app.betternotes.core.template.withDensityLevel
import app.betternotes.core.template.withLineWeightLevel
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
 * [pageSize] is null on a canvas, and [canResize] is false on a PDF page, whose size is fixed.
 */
@Composable
fun TemplateSheet(
    current: PageBackground,
    pageSize: PageSize?,
    canResize: Boolean,
    customLayouts: List<CustomLayout>,
    onApply: (background: PageBackground, layoutSize: PageSize?, scope: ApplyScope) -> Unit,
    onNewLayout: () -> Unit,
    onEditLayout: (CustomLayout) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    var paper by remember { mutableIntStateOf(current.color) }
    var template by remember { mutableStateOf(current.template) }
    var size by remember { mutableStateOf(pageSize) }
    var customTab by remember { mutableStateOf(current.template is PageTemplate.Custom) }
    val collapsed = remember { mutableStateListOf<TemplateGroup>() }

    SettingsSheet(stringResource(R.string.template_title), onClose, modifier, headerHeight = 88.dp) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChoiceChip(stringResource(R.string.template_built_in), !customTab) { customTab = false }
                    Spacer(Modifier.width(11.dp))
                    ChoiceChip(stringResource(R.string.template_custom), customTab) { customTab = true }
                    if (pageSize != null) {
                        Spacer(Modifier.width(30.dp))
                        SizeChooser(size!!, enabled = canResize) { size = it }
                    }
                }
                Spacer(Modifier.height(30.dp))
                PaperRow(paper) { paper = it }
                Spacer(Modifier.height(45.dp))
                LevelRow(
                    stringResource(R.string.template_density),
                    template.densityLevel,
                ) { template = template.withDensityLevel(it) }
                Spacer(Modifier.height(30.dp))
                LevelRow(
                    stringResource(R.string.template_line_width),
                    template.lineWeightLevel,
                ) { template = template.withLineWeightLevel(it) }
                Spacer(Modifier.height(18.dp))

                if (customTab) {
                    CustomLayouts(customLayouts, template, paper, onSelect = { template = PageTemplate.Custom(it) }, onEdit = onEditLayout, onNew = onNewLayout)
                } else {
                    for (group in TemplateGroup.entries) {
                        GroupHeader(group, selected = template.kind in group.kinds, collapsed = group in collapsed) {
                            if (group in collapsed) collapsed -= group else collapsed += group
                        }
                        if (group !in collapsed) {
                            ThumbnailRow(group, template, paper, onCanvas = pageSize == null) { kind ->
                                template = kind.newTemplate().keepingLevelsOf(template)
                            }
                        }
                    }
                }
            }
            ApplyButton(
                enabled = template !is PageTemplate.Custom || pageSize != null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 15.dp, bottom = 14.dp),
            ) { scope ->
                val layout = (template as? PageTemplate.Custom)?.layout
                val sizeToApply = layout?.size ?: size.takeIf { it != pageSize }
                onApply(PageBackground(paper, template), sizeToApply, scope)
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
}

@Composable
private fun kindName(kind: TemplateKind) = stringResource(
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
    },
)

@Composable
private fun SizeChooser(size: PageSize, enabled: Boolean, onChange: (PageSize) -> Unit) {
    val colors = LocalSettingsColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = enabled) { open = true },
        ) {
            Text(
                stringResource(R.string.template_size, size.widthPt.roundToInt(), size.heightPt.roundToInt()),
                color = if (enabled) colors.title else colors.secondary,
                fontSize = 19.5.sp,
            )
            if (enabled) {
                Spacer(Modifier.width(8.dp))
                Icon(SettingsIcons.Down, null, tint = colors.title, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(open, { open = false }) {
            for (option in PAGE_SIZES) {
                DropdownMenuItem(
                    text = { Text(sizeName(option), fontSize = 20.sp) },
                    onClick = {
                        open = false
                        onChange(option)
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
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        for (paper in PAPER_COLORS) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(paper))
                    .then(if (paper == selected) Modifier.border(3.dp, colors.accent, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(paper) },
            )
        }
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
        Text(label, color = if (level != null) colors.title else colors.secondary, fontSize = 19.5.sp)
        Spacer(Modifier.width(30.dp))
        StepSlider(level ?: 1, TEMPLATE_LEVELS.last, onChange, enabled = level != null)
        Spacer(Modifier.width(30.dp))
        Text(level?.toString() ?: "-", color = colors.title, fontSize = 19.5.sp, modifier = Modifier.width(24.dp))
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
        Text(stringResource(group.title), color = colors.title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        if (selected) {
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(9.dp).background(colors.accent, CircleShape))
        }
        Spacer(Modifier.weight(1f))
        Icon(if (collapsed) SettingsIcons.Down else SettingsIcons.Up, null, tint = colors.title, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ThumbnailRow(group: TemplateGroup, selected: PageTemplate, paper: Int, onCanvas: Boolean, onSelect: (TemplateKind) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(34.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 35.dp, end = 35.dp, top = 48.dp, bottom = 12.dp),
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
    val shape = RoundedCornerShape(9.dp)
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
            fontSize = 17.sp,
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
            TemplatePainter.paint(this, template, page, minLine = 1f / factor)
        }
    }
}

internal object TemplatePainter {
    /** [minLine] keeps a layout's dark lines visible when small, while faint ruling stays faint, as in the reference. */
    fun paint(scope: DrawScope, template: PageTemplate, page: PageSize, minLine: Float = 0f) {
        template.draw(page, Bounds(0f, 0f, page.widthPt, page.heightPt), PreviewSink(scope, minLine))
    }
}

private const val DARK_LINE_LUMINANCE = 0.3f

private class PreviewSink(private val scope: DrawScope, private val minLine: Float) : TemplateSink {
    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val paint = Color(color)
        val drawn = if (paint.luminance() < DARK_LINE_LUMINANCE) maxOf(width, minLine) else width
        scope.drawLine(paint, Offset(x0, y0), Offset(x1, y1), drawn)
    }

    override fun dot(x: Float, y: Float, radius: Float, color: Int) {
        scope.drawCircle(Color(color), radius, Offset(x, y))
    }
}

@Composable
private fun ColumnScope.CustomLayouts(
    layouts: List<CustomLayout>,
    selected: PageTemplate,
    paper: Int,
    onSelect: (CustomLayout) -> Unit,
    onEdit: (CustomLayout) -> Unit,
    onNew: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(34.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 34.dp, vertical = 14.dp),
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
            Spacer(Modifier.height(26.dp))
            Text(stringResource(R.string.layout_new), color = colors.title, fontSize = 17.sp)
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
                        .clickable(role = Role.RadioButton, onClickLabel = layout.name) { onSelect(layout) },
                ) {
                    TemplatePreview(PageTemplate.Custom(layout), paper, layout.size, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    layout.name,
                    color = if (isSelected) colors.accent else colors.title,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.layout_edit),
                    color = colors.accent,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onEdit(layout) }.padding(4.dp),
                )
            }
        }
    }
}

/** The pill at the corner: apply to the current page, or open it to apply to all of them. */
@Composable
private fun ApplyButton(enabled: Boolean, modifier: Modifier = Modifier, onApply: (ApplyScope) -> Unit) {
    val colors = LocalSettingsColors.current
    var open by remember { mutableStateOf(false) }
    val background = if (enabled) colors.accent else colors.sliderIdle
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(66.dp).clip(CircleShape).background(background),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(66.dp)
                    .clickable(enabled = enabled, role = Role.Button) { onApply(ApplyScope.CURRENT_PAGE) }
                    .padding(start = 32.dp, end = 21.dp),
            ) {
                Text(stringResource(R.string.template_apply_current), color = colors.onAccent, fontSize = 19.5.sp)
            }
            Box(Modifier.size(2.dp, 41.dp).background(colors.divider))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(66.dp)
                    .clickable(enabled = enabled, role = Role.Button) { open = true }
                    .padding(start = 24.dp, end = 28.dp),
            ) {
                Icon(SettingsIcons.Up, stringResource(R.string.template_apply_more), tint = colors.onAccent, modifier = Modifier.size(26.dp))
            }
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.template_apply_all), fontSize = 20.sp) },
                onClick = {
                    open = false
                    onApply(ApplyScope.ALL_PAGES)
                },
            )
        }
    }
}
