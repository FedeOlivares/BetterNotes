package app.betternotes.ui.editor

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.betternotes.R
import app.betternotes.core.planner.PlannerPage
import app.betternotes.core.planner.PlannerPages
import app.betternotes.core.planner.PlannerSetup
import app.betternotes.ui.library.PlannerColorRow
import app.betternotes.ui.theme.AppShapes
import app.betternotes.ui.theme.LocalEditorColors
import app.betternotes.ui.theme.Spacing
import app.betternotes.ui.theme.TextSize
import app.betternotes.ui.theme.UiColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** What a tap on a planner opened. */
internal sealed interface PlannerMenu {
    /** The weekday list of the day page for the date [dayIndex] of [page]. */
    data class Weekday(val dayIndex: Int, val page: PlannerPage) : PlannerMenu

    /** The cover's choice of mid-year or full year. */
    data object Years : PlannerMenu

    /** The cover's title. */
    data object Title : PlannerMenu

    /** A semester's start or [end] date. */
    data class Date(val semester: Int, val end: Boolean) : PlannerMenu
}

private val BLUE = UiColors.SystemBlue

/** The two small panels a planner opens: the weekday list, and the years chooser on the cover. */
@Composable
internal fun PlannerMenus(state: EditorState, menu: PlannerMenu) {
    val setup = state.plannerSetup ?: return
    val close = { state.plannerMenu = null }
    when (menu) {
        is PlannerMenu.Weekday -> WeekdayMenu(setup, menu, close) { weekday ->
            close()
            state.setPlannerSetup(setup.withWeekday(setup.day(menu.dayIndex), weekday), menu.page)
        }
        is PlannerMenu.Date -> SemesterDateMenu(setup, state.plannerSemesters, menu, close) { epochDay ->
            close()
            state.setSemesterDate(menu.semester, menu.end, epochDay)
        }
        PlannerMenu.Title -> TitleMenu(state.plannerTitle, close) { text ->
            close()
            state.setPlannerTitle(text)
        }
        PlannerMenu.Years -> YearsMenu(setup, state.plannerColors, close) { chosen, colors ->
            close()
            // The colors first: a change of years builds its pages with the colors the planner has.
            state.setPlannerColors(colors)
            state.setPlannerSetup(chosen.copy(weekdayShift = 0), null)
        }
    }
}

@Composable
private fun Panel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalEditorColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(340.dp)
                .shadow(24.dp, AppShapes.Rounded24)
                .clip(AppShapes.Rounded24)
                .background(colors.panel)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.S20),
        ) { content() }
    }
}

/** The seven weekdays, the date's current one marked. Picking one gives that date the weekday, and every other date moves with it. */
@Composable
private fun WeekdayMenu(setup: PlannerSetup, menu: PlannerMenu.Weekday, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = LocalEditorColors.current
    val current = setup.weekday(setup.day(menu.dayIndex))
    val locale = LocalLocale.current.platformLocale
    Panel(onDismiss) {
        Text(stringResource(R.string.planner_weekday_title), color = colors.title, fontSize = TextSize.S20, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Spacing.S12))
        for (day in 0 until 7) {
            val name = DayOfWeek.of(day + 1).getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.Rounded12)
                    .then(if (day == current) Modifier.background(BLUE.copy(alpha = UiColors.SYSTEM_BLUE_TINT_ALPHA)) else Modifier)
                    .clickable { onPick(day) }
                    .padding(horizontal = Spacing.S14, vertical = Spacing.S12),
            ) {
                Text(name, color = if (day == current) BLUE else colors.title, fontSize = TextSize.S18, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Mid-year (the year and the next, July to July) or a full calendar year (January to December), and which year. The weekdays
 * follow the real calendar again for the new dates.
 */
@Composable
private fun YearsMenu(setup: PlannerSetup, planColors: List<Int>, onDismiss: () -> Unit, onPick: (PlannerSetup, List<Int>) -> Unit) {
    val colors = LocalEditorColors.current
    var full by remember { mutableStateOf(setup.isFullYear) }
    var year by remember { mutableIntStateOf(setup.startYear) }
    var chosenColors by remember { mutableStateOf(planColors) }
    val chosen = PlannerSetup(year, if (full) 1 else 7)
    Panel(onDismiss) {
        Text(stringResource(R.string.planner_years_title), color = colors.title, fontSize = TextSize.S20, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Spacing.S14))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S10), modifier = Modifier.fillMaxWidth()) {
            Choice(stringResource(R.string.planner_years_mid), !full, Modifier.weight(1f)) { full = false }
            Choice(stringResource(R.string.planner_years_full), full, Modifier.weight(1f)) { full = true }
        }
        Spacer(Modifier.height(Spacing.S6))
        Text(
            stringResource(if (full) R.string.planner_years_full_detail else R.string.planner_years_mid_detail),
            color = colors.label,
            fontSize = TextSize.S14,
        )
        Spacer(Modifier.height(Spacing.S16))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Step("-") { if (year > PlannerSetup.MIN_YEAR) year-- }
            Text(chosen.yearLabel, color = colors.title, fontSize = TextSize.S26, fontWeight = FontWeight.Medium)
            Step("+") { if (year < PlannerSetup.MAX_YEAR - 1) year++ }
        }
        Spacer(Modifier.height(Spacing.S16))
        Text(stringResource(R.string.planner_colors_title), color = colors.title, fontSize = TextSize.S16, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Spacing.S8))
        PlannerColorRow(chosenColors, compact = true, label = colors.label, border = colors.panelDivider) { chosenColors = it }
        Spacer(Modifier.height(Spacing.S20))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(AppShapes.Circle)
                .background(BLUE)
                .clickable { onPick(chosen, chosenColors) },
        ) {
            Text(stringResource(R.string.planner_years_apply), color = UiColors.OnFilled, fontSize = TextSize.S18, fontWeight = FontWeight.Medium)
        }
    }
}

/** The calendar for a semester's start or end date. "Clear" takes the date off every page that shows it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SemesterDateMenu(setup: PlannerSetup, dates: List<Int>, menu: PlannerMenu.Date, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val slot = menu.semester * 2 + if (menu.end) 1 else 0
    val current = dates.getOrNull(slot)?.takeIf { it != 0 }
    // The picker counts in milliseconds in UTC, so a date lands on the same day wherever the tablet is.
    val initial = (current ?: setup.firstDay.toEpochDay().toInt()) * MILLIS_PER_DAY
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { state.selectedDateMillis?.let { onPick((it / MILLIS_PER_DAY).toInt()) } }) {
                Text(stringResource(R.string.planner_years_apply))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.planner_date_clear)) }
        },
    ) {
        androidx.compose.material3.DatePicker(state)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** A field for the cover's title. Empty brings back the default, "Planner" or "Agenda". */
@Composable
private fun TitleMenu(current: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val colors = LocalEditorColors.current
    var text by remember { mutableStateOf(current) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    Panel(onDismiss) {
        Text(stringResource(R.string.planner_title_title), color = colors.title, fontSize = TextSize.S20, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(Spacing.S14))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.Rounded12)
                .background(colors.field)
                .padding(horizontal = Spacing.S16, vertical = Spacing.S14),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                text,
                { text = it.take(PlannerPages.MAX_TITLE) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.title, fontSize = TextSize.S20),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
        Spacer(Modifier.height(Spacing.S18))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(AppShapes.Circle)
                .background(BLUE)
                .clickable { onApply(text) },
        ) {
            Text(stringResource(R.string.planner_years_apply), color = UiColors.OnFilled, fontSize = TextSize.S18, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(AppShapes.Rounded14)
            .then(if (selected) Modifier.background(BLUE.copy(alpha = UiColors.SYSTEM_BLUE_TINT_ALPHA)) else Modifier)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) BLUE else colors.pillBorder, AppShapes.Rounded14)
            .clickable(onClick = onClick),
    ) {
        Text(label, color = if (selected) BLUE else colors.title, fontSize = TextSize.S16, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    val colors = LocalEditorColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(AppShapes.Circle)
            .border(1.5.dp, colors.pillBorder, AppShapes.Circle)
            .clickable(onClick = onClick),
    ) {
        Text(label, color = colors.title, fontSize = TextSize.S24)
    }
}
