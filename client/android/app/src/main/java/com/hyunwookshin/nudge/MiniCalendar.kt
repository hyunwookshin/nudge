package com.hyunwookshin.nudge

import java.time.LocalDate
import java.time.YearMonth

/** A project's span on the calendar, drawn as one band in [color] (see ProjectBandDecoration). */
data class ProjectBand(val start: LocalDate, val end: LocalDate, val color: Int)

data class DayState(
    val date: LocalDate,
    val count: Int,
    val hasHigh: Boolean,
    val hasLow: Boolean,
    // false for days outside the month that most of the visible weeks belong to (their number is dimmed)
    val inMajorityMonth: Boolean = true
)

/** One row's worth of a project band: cells [firstCol]..[lastCol] of calendar row [row] (0 = top). */
data class BandSegment(val row: Int, val firstCol: Int, val lastCol: Int, val startsHere: Boolean, val endsHere: Boolean)

/** Layout math for the 4-week mini calendar (pure functions, unit tested in MiniCalendarMathTest). */
object MiniCalendarMath {
    /**
     * Where a project spanning [start]..[end] (inclusive) is drawn over [days] (rows of 7, Sunday first):
     * one segment per row it touches. [BandSegment.startsHere]/[BandSegment.endsHere] are true only on the
     * rows holding the project's real first/last day, so only those ends get a rounded cap and a band that
     * wraps from one row to the next looks like one snake.
     */
    fun bandSegments(days: List<LocalDate>, start: LocalDate, end: LocalDate): List<BandSegment> {
        val segments = mutableListOf<BandSegment>()
        for (row in 0 until days.size / 7) {
            val first = row * 7
            var a = -1
            var b = -1
            for (col in 0..6) {
                val day = days[first + col]
                if (!day.isBefore(start) && !day.isAfter(end)) {
                    if (a < 0) a = col
                    b = col
                }
            }
            if (a >= 0) segments.add(BandSegment(row, a, b, days[first + a] == start, days[first + b] == end))
        }
        return segments
    }

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

    /**
     * The 28 [DayState]s for [anchor]: per day, how many of [reminders] fall on it and whether any is
     * high priority (<= 1) or low, for the cell's fill.
     */
    fun buildDays(reminders: List<Reminder>, anchor: LocalDate): List<DayState> {
        val range = visibleRange(anchor)
        val agg = mutableMapOf<LocalDate, Triple<Int, Boolean, Boolean>>()
        for (r in reminders) {
            val date = runCatching { LocalDate.parse(r.Time.take(10)) }.getOrNull() ?: continue
            if (date !in range) continue
            val prev = agg[date] ?: Triple(0, false, false)
            agg[date] = Triple(prev.first + 1, prev.second || r.Priority <= 1, prev.third || r.Priority > 1)
        }
        val dates = (0..27).map { range.start.plusDays(it.toLong()) }
        val majority = majorityMonth(dates, YearMonth.from(anchor))
        return dates.map { d ->
            val t = agg[d] ?: Triple(0, false, false)
            DayState(d, t.first, t.second, t.third, inMajorityMonth = YearMonth.from(d) == majority)
        }
    }
}
