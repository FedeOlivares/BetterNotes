package app.betternotes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.betternotes.R
import app.betternotes.core.geometry.PageSize
import app.betternotes.core.template.PAPER_COLORS
import app.betternotes.core.template.PageTemplate
import app.betternotes.core.template.density
import app.betternotes.core.template.kind
import app.betternotes.core.template.withDensity
import app.betternotes.ui.theme.OpenFrom
import app.betternotes.ui.theme.opensFrom

/** What the rail on the left opens beside it. Book style is a switch and opens nothing. */
internal enum class LayoutPanel { BACKGROUND, PATTERN, FRAME, ELEMENTS }

internal val RailWidth = 132.dp

private val PatternPage = PageSize(120f, 120f)

private object LayoutIcons {
    val Book by lazy { lineIcon("book", "M4 5h6a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H4zM20 5h-6a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h6z") }
    val Pattern by lazy { lineIcon("pattern", "M4 4h16v16H4zM4 9.3h16M4 14.7h16M9.3 4v16M14.7 4v16") }
    val Frame by lazy { lineIcon("frame", "M4 4h16v16H4zM4 8.5h16M9 8.5V20") }
    val Elements by lazy { lineIcon("elements", "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3") }
}

/** The blue-ish column down the left of the frame. */
@Composable
internal fun LayoutRail(
    paper: Int,
    bookStyle: Boolean,
    open: LayoutPanel?,
    onToggleBook: () -> Unit,
    onOpen: (LayoutPanel) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSettingsColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(RailWidth).background(colors.menu).padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        RailButton(stringResource(R.string.layout_book_style), bookStyle, onToggleBook) {
            Icon(LayoutIcons.Book, null, tint = it, modifier = Modifier.size(36.dp))
        }
        RailButton(stringResource(R.string.layout_background), open == LayoutPanel.BACKGROUND, { onOpen(LayoutPanel.BACKGROUND) }) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(paper)).border(2.dp, it, CircleShape))
        }
        RailButton(stringResource(R.string.layout_pattern), open == LayoutPanel.PATTERN, { onOpen(LayoutPanel.PATTERN) }) {
            Icon(LayoutIcons.Pattern, null, tint = it, modifier = Modifier.size(36.dp))
        }
        RailButton(stringResource(R.string.layout_frame), open == LayoutPanel.FRAME, { onOpen(LayoutPanel.FRAME) }) {
            Icon(LayoutIcons.Frame, null, tint = it, modifier = Modifier.size(36.dp))
        }
        RailButton(stringResource(R.string.layout_elements), open == LayoutPanel.ELEMENTS, { onOpen(LayoutPanel.ELEMENTS) }) {
            Icon(LayoutIcons.Elements, null, tint = it, modifier = Modifier.size(36.dp))
        }
        if (onDelete != null) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.layout_delete),
                color = colors.danger,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onDelete).padding(10.dp),
            )
        }
    }
}

@Composable
private fun RailButton(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val colors = LocalSettingsColors.current
    val tint = if (selected) colors.chipSelectedText else colors.chipIdleText
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.chipSelected else colors.chipIdle)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        icon(tint)
        Spacer(Modifier.height(6.dp))
        ShrinkText(label, tint, 16.sp, textAlign = TextAlign.Center)
    }
}

/** A panel that grows out of the rail's edge, in the menus' shared open animation. */
@Composable
internal fun RailPanel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalSettingsColors.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .opensFrom(OpenFrom.Start)
            .shadow(16.dp, shape, clip = false)
            .clip(shape)
            .background(colors.sheet)
            .clickable(enabled = false) {}
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(title, color = colors.title, fontSize = 22.sp)
        content()
    }
}

@Composable
internal fun BackgroundPanel(paper: Int, onPick: (Int) -> Unit) {
    val colors = LocalSettingsColors.current
    RailPanel(stringResource(R.string.layout_background)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (color in PAPER_COLORS) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (color == paper) 3.dp else 1.dp, if (color == paper) colors.accent else colors.divider, CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(color) },
                )
            }
        }
    }
}

/** What the pattern and frame panels offer, in the order they show. */
private val Patterns: List<Pair<Int, PageTemplate>> = listOf(
    R.string.layout_pattern_blank to PageTemplate.Blank,
    R.string.layout_pattern_dotted to PageTemplate.Dotted(),
    R.string.layout_pattern_ruled to PageTemplate.Lined(),
    R.string.layout_pattern_grid to PageTemplate.Grid(),
    R.string.layout_pattern_hexagonal to PageTemplate.Hexagonal(),
)

private val Frames: List<Pair<Int, PageTemplate>> = listOf(
    R.string.layout_frame_cornell to PageTemplate.Cornell(),
    R.string.layout_frame_legal to PageTemplate.Legal(),
)

@Composable
internal fun PatternPanel(base: PageTemplate, paper: Int, onPick: (PageTemplate) -> Unit) {
    RailPanel(stringResource(R.string.layout_pattern)) {
        TemplateTiles(Patterns, base, paper, onPick)
        val density = base.density
        if (density != null) {
            SettingRow(stringResource(R.string.layout_spacing)) {
                RoundIcon(SettingsIcons.Minus, 26.dp) { onPick(base.withDensity(density - 2f)) }
                ValuePill(density.toInt().toString())
                RoundIcon(SettingsIcons.Plus, 26.dp) { onPick(base.withDensity(density + 2f)) }
            }
        }
    }
}

@Composable
internal fun FramePanel(base: PageTemplate, paper: Int, onPick: (PageTemplate) -> Unit) {
    RailPanel(stringResource(R.string.layout_frame)) {
        // Tapping the frame that is on takes it off again.
        TemplateTiles(Frames, base, paper) { onPick(if (it.kind == base.kind) PageTemplate.Blank else it) }
    }
}

@Composable
private fun TemplateTiles(options: List<Pair<Int, PageTemplate>>, current: PageTemplate, paper: Int, onPick: (PageTemplate) -> Unit) {
    val colors = LocalSettingsColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for ((label, template) in options) {
            val selected = current.kind == template.kind
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(shape)
                        .then(if (selected) Modifier.border(3.dp, colors.accent, shape) else Modifier.border(1.dp, colors.divider, shape))
                        .clickable(role = Role.RadioButton) { onPick(if (selected) current else template) },
                ) {
                    TemplatePreview(template, paper, PatternPage, Modifier.size(110.dp))
                }
                Spacer(Modifier.height(8.dp))
                ShrinkText(stringResource(label), if (selected) colors.accent else colors.text, 16.sp)
            }
        }
    }
}

@Composable
internal fun ElementsPanel(tools: List<Pair<LayoutTool, String>>, current: LayoutTool, onPick: (LayoutTool) -> Unit) {
    RailPanel(stringResource(R.string.layout_elements), Modifier.width(220.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((tool, label) in tools) ChoiceChip(label, current == tool) { onPick(tool) }
        }
    }
}
