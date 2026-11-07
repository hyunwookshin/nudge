package com.hyunwookshin.nudge

import java.time.LocalDate
import java.time.YearMonth

data class DayState(
    val date: LocalDate,
    val count: Int,
    val hasHigh: Boolean,
    val hasLow: Boolean,
    // false for days outside the month that most of the visible weeks belong to (their number is dimmed)
    val inMajorityMonth: Boolean = true
)

/** Layout math for the 4-week mini calendar (pure functions, unit tested in MiniCalendarMathTest). */
object MiniCalendarMath {
    /**
     * The month most of [dates] fall in. A tie goes to [preferred] (the month the title shows) when it is
     * one of the tied months, otherwise to the later month.
     */
    fun majorityMonth(dates: List<LocalDate>, preferred: YearMonth): YearMonth {
        val counts = dates.groupingBy { YearMonth.from(it) }.eachCount()
        val best = counts.values.maxOrNull() ?: return preferred
        val leaders = counts.filterValues { it == best }.keys
        return if (preferred in leaders) preferred else leaders.max()
    }

    /** Days shown for [anchor]: weeks start on Sunday, the anchor's week is the 2nd of 4 rows. */
    fun visibleRange(anchor: LocalDate): ClosedRange<LocalDate> {
        val start = anchor.minusDays((anchor.dayOfWeek.value % 7).toLong()).minusDays(7)
        return start..start.plusDays(27)
    }

    /**
     * Anchor that brings [date] into view with as little movement as possible:
     * - already visible with [currentAnchor]: keep it
     * - otherwise show its month with the 1st in the top row,
     * - unless [date] would not fit that way (late in the month): its week becomes the bottom row.
     */
    fun anchorToShow(date: LocalDate, currentAnchor: LocalDate): LocalDate {
        if (date in visibleRange(currentAnchor)) return currentAnchor
        // the anchor's week is the 2nd row, so anchoring a week after the 1st puts the 1st in the top row
        val firstOnTop = date.withDayOfMonth(1).plusWeeks(1)
        if (date in visibleRange(firstOnTop)) return firstOnTop
        return date.minusWeeks(2)   // date's week = 4th (last) row
    }
}
