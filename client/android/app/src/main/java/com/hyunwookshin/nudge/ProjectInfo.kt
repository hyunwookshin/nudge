package com.hyunwookshin.nudge

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How a project's dates are described on its cards and its page (pure, unit tested). */
object ProjectInfo {

    /** "Nov 6 – Nov 30"; a date outside [today]'s year also shows its year. */
    fun rangeLabel(start: LocalDate, end: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        fun fmt(d: LocalDate) =
            d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "MMM d" else "MMM d, yyyy", locale))
        return "${fmt(start)} – ${fmt(end)}"
    }

    /** Days the project covers, both ends included (a one-day project is 1). */
    fun durationDays(start: LocalDate, end: LocalDate): Int = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    fun durationLabel(start: LocalDate, end: LocalDate): String =
        durationDays(start, end).let { if (it == 1) "1 day" else "$it days" }

    /** Where the project stands on [today]. */
    fun status(start: LocalDate, end: LocalDate, today: LocalDate): String = when {
        today.isBefore(start) -> ChronoUnit.DAYS.between(today, start).let { if (it == 1L) "Starts tomorrow" else "Starts in $it days" }
        today.isAfter(end) -> ChronoUnit.DAYS.between(end, today).let { if (it == 1L) "Ended yesterday" else "Ended $it days ago" }
        start == end -> "Today"
        today == start -> "Starts today"
        today == end -> "Last day"
        else -> ChronoUnit.DAYS.between(today, end).let { "In progress · ${if (it == 1L) "1 day" else "$it days"} left" }
    }

    fun eventCountLabel(count: Int): String = when (count) {
        0 -> "No events yet"
        1 -> "1 event"
        else -> "$count events"
    }
}
