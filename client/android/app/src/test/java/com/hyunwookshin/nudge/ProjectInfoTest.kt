package com.hyunwookshin.nudge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ProjectInfoTest {

    private fun d(s: String) = LocalDate.parse(s)
    private val start = d("2026-11-06")
    private val end = d("2026-11-30")

    @Test
    fun range_label_shows_the_year_only_when_it_differs() {
        assertEquals("Nov 6 – Nov 30", ProjectInfo.rangeLabel(start, end, d("2026-11-07"), Locale.US))
        assertEquals("Dec 28 – Jan 3, 2027", ProjectInfo.rangeLabel(d("2026-12-28"), d("2027-01-03"), d("2026-11-07"), Locale.US))
        assertEquals("Nov 6, 2026 – Nov 30, 2026", ProjectInfo.rangeLabel(start, end, d("2027-02-01"), Locale.US))
    }

    @Test
    fun duration_counts_both_end_days() {
        assertEquals(1, ProjectInfo.durationDays(d("2026-11-07"), d("2026-11-07")))
        assertEquals(25, ProjectInfo.durationDays(start, end))
        assertEquals(3, ProjectInfo.durationDays(d("2028-02-28"), d("2028-03-01")))   // leap year
        assertEquals("1 day", ProjectInfo.durationLabel(d("2026-11-07"), d("2026-11-07")))
        assertEquals("25 days", ProjectInfo.durationLabel(start, end))
    }

    @Test
    fun status_before_during_and_after() {
        assertEquals("Starts in 5 days", ProjectInfo.status(start, end, d("2026-11-01")))
        assertEquals("Starts tomorrow", ProjectInfo.status(start, end, d("2026-11-05")))
        assertEquals("Starts today", ProjectInfo.status(start, end, d("2026-11-06")))
        assertEquals("In progress · 23 days left", ProjectInfo.status(start, end, d("2026-11-07")))
        assertEquals("In progress · 1 day left", ProjectInfo.status(start, end, d("2026-11-29")))
        assertEquals("Last day", ProjectInfo.status(start, end, d("2026-11-30")))
        assertEquals("Ended yesterday", ProjectInfo.status(start, end, d("2026-12-01")))
        assertEquals("Ended 30 days ago", ProjectInfo.status(start, end, d("2026-12-30")))
    }

    @Test
    fun one_day_project_status() {
        val day = d("2026-11-07")
        assertEquals("Starts tomorrow", ProjectInfo.status(day, day, d("2026-11-06")))
        assertEquals("Today", ProjectInfo.status(day, day, day))
        assertEquals("Ended yesterday", ProjectInfo.status(day, day, d("2026-11-08")))
    }

    @Test
    fun event_count_label() {
        assertEquals("No events yet", ProjectInfo.eventCountLabel(0))
        assertEquals("1 event", ProjectInfo.eventCountLabel(1))
        assertEquals("12 events", ProjectInfo.eventCountLabel(12))
    }
}
