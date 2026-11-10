package app.betternotes.core.planner

import java.time.LocalDate

/**
 * Which dates a planner covers, and the weekday of the first day it is shown on.
 *
 * [startMonth] 1 is a calendar year (January to December of [startYear]). Any other month is a mid-year planner
 * that runs from the 1st of that month to the same month of the next year, which is why it has 13 months, as in the reference.
 * [weekdayShift] moves every date's weekday, so the owner can set a date to the weekday they want and the whole calendar follows.
 * It is 0 for the true calendar.
 */
data class PlannerSetup(val startYear: Int, val startMonth: Int = 1, val weekdayShift: Int = 0) {
    init {
        require(startYear in MIN_YEAR..MAX_YEAR) { "Year out of range: $startYear" }
        require(startMonth in 1..12) { "Month out of range: $startMonth" }
        require(weekdayShift in 0..6) { "Weekday shift out of range: $weekdayShift" }
    }

    val isFullYear get() = startMonth == 1

    /** 12 for a calendar year, 13 for a mid-year planner. */
    val monthCount get() = if (isFullYear) 12 else 13

    val firstDay: LocalDate get() = LocalDate.of(startYear, startMonth, 1)

    /** The 1st of the month after the last one the planner covers. */
    private val endExclusive: LocalDate get() = firstDay.plusMonths(monthCount.toLong())

    val lastDay: LocalDate get() = endExclusive.minusDays(1)

    val dayCount: Int get() = (endExclusive.toEpochDay() - firstDay.toEpochDay()).toInt()

    /** The first day of month [index], counted from 0 at the start of the planner. */
    fun monthStart(index: Int): LocalDate = firstDay.plusMonths(index.toLong())

    fun daysInMonth(index: Int): Int = monthStart(index).lengthOfMonth()

    /** The date [dayIndex] days after the first day. */
    fun day(dayIndex: Int): LocalDate = firstDay.plusDays(dayIndex.toLong())

    fun dayIndex(date: LocalDate): Int = (date.toEpochDay() - firstDay.toEpochDay()).toInt()

    fun contains(date: LocalDate) = !date.isBefore(firstDay) && date.isBefore(endExclusive)

    /** The planner month (from 0) a date falls in, or -1 outside the planner. */
    fun monthIndexOf(date: LocalDate): Int =
        if (!contains(date)) -1 else (date.year - startYear) * 12 + date.monthValue - startMonth

    /** 0 for Monday to 6 for Sunday, as the planner shows [date] once the weekday shift is applied. */
    fun weekday(date: LocalDate): Int = Math.floorMod(date.toEpochDay() + THURSDAY_EPOCH_OFFSET + weekdayShift, 7L).toInt()

    /** This planner with [date] on [weekday] (0 Monday to 6 Sunday), and every other date moved with it. */
    fun withWeekday(date: LocalDate, weekday: Int): PlannerSetup {
        val real = Math.floorMod(date.toEpochDay() + THURSDAY_EPOCH_OFFSET, 7L).toInt()
        return copy(weekdayShift = Math.floorMod(weekday - real, 7))
    }

    /** The Monday of the week that holds the first day. Weeks are counted from here. */
    val firstMonday: LocalDate get() = firstDay.minusDays(weekday(firstDay).toLong())

    fun mondayOf(date: LocalDate): LocalDate = date.minusDays(weekday(date).toLong())

    /** Number of weeks, from the one holding the first day to the one holding the last. */
    val weekCount: Int get() = ((mondayOf(lastDay).toEpochDay() - firstMonday.toEpochDay()) / 7).toInt() + 1

    fun weekMonday(weekIndex: Int): LocalDate = firstMonday.plusDays(weekIndex * 7L)

    /** The week (from 0) that holds [date]. */
    fun weekIndexOf(date: LocalDate): Int = ((mondayOf(date).toEpochDay() - firstMonday.toEpochDay()) / 7).toInt()

    /**
     * The planner month a week is filed under: the month of its Thursday, held to the planner's first and last months, so a week
     * that straddles two months sits with the one that has most of its days.
     */
    fun monthOfWeek(weekIndex: Int): Int {
        val thursday = weekMonday(weekIndex).plusDays(3)
        return monthIndexOf(if (thursday.isBefore(firstDay)) firstDay else if (thursday.isAfter(lastDay)) lastDay else thursday)
    }

    /** The label of the year or years, as "2026 - 2027" for a mid-year planner and "2027" for a calendar year. */
    val yearLabel: String get() = if (isFullYear) "$startYear" else "$startYear - ${startYear + 1}"

    companion object {
        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2200

        /** 1 January 1970 was a Thursday, which is 3 days after Monday. */
        private const val THURSDAY_EPOCH_OFFSET = 3L
    }
}
