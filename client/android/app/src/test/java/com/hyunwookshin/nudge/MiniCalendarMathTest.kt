package com.hyunwookshin.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MiniCalendarMathTest {

    private fun d(s: String) = LocalDate.parse(s)
    private fun topRow(anchor: LocalDate) = MiniCalendarMath.visibleRange(anchor).start

    @Test
    fun visibleRange_is_four_sunday_weeks_with_anchor_week_second() {
        val r = MiniCalendarMath.visibleRange(d("2026-11-07"))   // Saturday
        assertEquals(d("2026-10-25"), r.start)                    // Sunday a week before
        assertEquals(DayOfWeek.SUNDAY, r.start.dayOfWeek)
        assertEquals(d("2026-11-21"), r.endInclusive)
    }

    @Test
    fun visible_date_keeps_the_current_anchor() {
        val anchor = d("2026-11-07")
        assertEquals(anchor, MiniCalendarMath.anchorToShow(d("2026-11-19"), anchor))
        assertEquals(anchor, MiniCalendarMath.anchorToShow(d("2026-10-26"), anchor))   // other month, but on screen
    }

    @Test
    fun other_month_puts_the_first_in_the_top_row() {
        val anchor = MiniCalendarMath.anchorToShow(d("2026-12-04"), d("2026-11-07"))
        val range = MiniCalendarMath.visibleRange(anchor)
        assertTrue(d("2026-12-01") in range)
        assertEquals(d("2026-11-29"), topRow(anchor))               // week of Tue Dec 1
        assertTrue(d("2026-12-04") in range)
        assertEquals(12, anchor.monthValue)                         // title shows December
    }

    @Test
    fun late_date_that_does_not_fit_goes_to_the_bottom_row() {
        // Aug 2026: the 1st is a Saturday, so first-on-top shows Jul 26 .. Aug 22
        val anchor = MiniCalendarMath.anchorToShow(d("2026-08-30"), d("2026-11-07"))
        val range = MiniCalendarMath.visibleRange(anchor)
        assertTrue(d("2026-08-30") in range)
        assertEquals(d("2026-08-30"), range.endInclusive.minusDays(6))   // its week is the last row
        assertEquals(8, anchor.monthValue)
    }

    @Test
    fun the_31st_still_fits_when_possible_otherwise_bottom_row() {
        for (date in listOf("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31", "2026-10-31", "2026-12-31")) {
            val anchor = MiniCalendarMath.anchorToShow(d(date), d("2026-11-07").minusYears(1))
            val range = MiniCalendarMath.visibleRange(anchor)
            assertTrue(date, d(date) in range)
            assertEquals(date, d(date).monthValue, anchor.monthValue)
        }
    }

    @Test
    fun february_and_year_boundaries() {
        for (date in listOf("2027-02-01", "2027-02-14", "2027-02-28", "2028-02-29", "2027-01-01", "2026-12-31")) {
            val anchor = MiniCalendarMath.anchorToShow(d(date), d("2026-06-15"))
            assertTrue(date, d(date) in MiniCalendarMath.visibleRange(anchor))
            assertEquals(date, d(date).monthValue, anchor.monthValue)
        }
    }

    @Test
    fun early_date_never_needs_the_fallback() {
        for (day in 1..21) {
            val date = d("2026-08-01").withDayOfMonth(day)   // worst case: month starting on Saturday
            val anchor = MiniCalendarMath.anchorToShow(date, d("2026-01-15"))
            assertEquals(date.toString(), d("2026-07-26"), topRow(anchor))
        }
    }
}
