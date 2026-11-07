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

    // ---- majority month (dimming of the neighbouring month) ----

    private fun window(anchor: String) =
        MiniCalendarMath.visibleRange(d(anchor)).let { r -> (0..27).map { r.start.plusDays(it.toLong()) } }

    @Test
    fun majority_is_the_month_with_most_of_the_28_days() {
        // anchor Nov 10: Oct 25 .. Nov 21 -> November has 21 of 28 days
        assertEquals(java.time.YearMonth.of(2026, 11), MiniCalendarMath.majorityMonth(window("2026-11-10"), java.time.YearMonth.of(2026, 11)))
        // anchor Nov 30: Nov 22 .. Dec 19 -> December has 19 of 28, even though the anchor is in November
        assertEquals(java.time.YearMonth.of(2026, 12), MiniCalendarMath.majorityMonth(window("2026-11-30"), java.time.YearMonth.of(2026, 11)))
    }

    @Test
    fun tie_goes_to_the_preferred_month_otherwise_the_later_one() {
        // 14/14 split: Jan 31 2027 anchor? build one explicitly instead of hunting for it
        val dates = (0..13).map { d("2026-11-17").plusDays(it.toLong()) } + (0..13).map { d("2026-12-01").plusDays(it.toLong()) }
        // Nov 17..30 (14 days) and Dec 1..14 (14 days)
        assertEquals(java.time.YearMonth.of(2026, 11), MiniCalendarMath.majorityMonth(dates, java.time.YearMonth.of(2026, 11)))
        assertEquals(java.time.YearMonth.of(2026, 12), MiniCalendarMath.majorityMonth(dates, java.time.YearMonth.of(2026, 12)))
        assertEquals(java.time.YearMonth.of(2026, 12), MiniCalendarMath.majorityMonth(dates, java.time.YearMonth.of(2027, 3)))   // unrelated preferred
    }

    @Test
    fun majority_month_is_always_decided_and_the_window_touches_at_most_two_months() {
        var day = d("2026-01-01")
        while (day < d("2029-01-01")) {            // every day of three years, incl. a leap February
            val w = MiniCalendarMath.visibleRange(day).let { r -> (0..27).map { r.start.plusDays(it.toLong()) } }
            val months = w.map { java.time.YearMonth.from(it) }.distinct()
            assertTrue(day.toString(), months.size <= 2)
            val majority = MiniCalendarMath.majorityMonth(w, java.time.YearMonth.from(day))
            assertTrue(day.toString(), majority in months)
            // at least half of the days are in the majority month
            assertTrue(day.toString(), w.count { java.time.YearMonth.from(it) == majority } >= 14)
            day = day.plusDays(1)
        }
    }

    @Test
    fun month_arrows_anchor_keeps_the_title_month_as_the_majority() {
        // jumpMonth anchors on the 10th: the shown month must be the majority month every time
        for (m in 1..12) {
            val anchor = d("2026-01-10").plusMonths(m.toLong() - 1)
            assertEquals(anchor.toString(), java.time.YearMonth.from(anchor),
                MiniCalendarMath.majorityMonth(window(anchor.toString()), java.time.YearMonth.from(anchor)))
        }
    }
}
