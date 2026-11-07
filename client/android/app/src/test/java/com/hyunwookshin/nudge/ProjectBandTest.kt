package com.hyunwookshin.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectBandTest {

    private fun d(s: String) = LocalDate.parse(s)
    // anchor Sat Nov 7 2026: rows start Sun Oct 25, Nov 1, Nov 8, Nov 15 (Sat Nov 21 is the last cell)
    private val days = MiniCalendarMath.visibleRange(d("2026-11-07")).let { r -> (0..27).map { r.start.plusDays(it.toLong()) } }
    private fun segments(start: String, end: String) = MiniCalendarMath.bandSegments(days, d(start), d(end))

    @Test
    fun project_inside_one_row_is_one_fully_rounded_segment() {
        // Mon Nov 9 .. Thu Nov 12 -> row 2, columns 1..4
        assertEquals(listOf(BandSegment(2, 1, 4, startsHere = true, endsHere = true)), segments("2026-11-09", "2026-11-12"))
    }

    @Test
    fun one_day_project_is_a_single_cell() {
        assertEquals(listOf(BandSegment(1, 6, 6, true, true)), segments("2026-11-07", "2026-11-07"))   // a Saturday
        assertEquals(listOf(BandSegment(1, 0, 0, true, true)), segments("2026-11-01", "2026-11-01"))   // a Sunday
    }

    @Test
    fun band_wraps_to_the_next_row_with_flat_joins_and_rounded_ends() {
        // Thu Nov 5 .. Tue Nov 10: row 1 cols 4..6, then row 2 cols 0..2
        val s = segments("2026-11-05", "2026-11-10")
        assertEquals(
            listOf(BandSegment(1, 4, 6, startsHere = true, endsHere = false),
                   BandSegment(2, 0, 2, startsHere = false, endsHere = true)), s)
    }

    @Test
    fun band_spanning_all_rows_has_flat_middle_rows() {
        val s = segments("2026-10-28", "2026-11-19")
        assertEquals(4, s.size)
        assertEquals(BandSegment(0, 3, 6, true, false), s[0])
        assertEquals(BandSegment(1, 0, 6, false, false), s[1])
        assertEquals(BandSegment(2, 0, 6, false, false), s[2])
        assertEquals(BandSegment(3, 0, 4, false, true), s[3])
    }

    @Test
    fun project_that_began_before_the_window_has_no_rounded_start() {
        val s = segments("2026-09-01", "2026-11-03")
        assertEquals(BandSegment(0, 0, 6, false, false), s[0])
        assertEquals(BandSegment(1, 0, 2, false, true), s[1])        // ends Tue Nov 3
        assertEquals(2, s.size)
    }

    @Test
    fun project_that_continues_past_the_window_has_no_rounded_end() {
        val s = segments("2026-11-19", "2027-02-01")
        assertEquals(listOf(BandSegment(3, 4, 6, true, false)), s)    // Thu Nov 19 .. Sat Nov 21
    }

    @Test
    fun project_outside_the_window_draws_nothing() {
        assertTrue(segments("2026-09-01", "2026-10-24").isEmpty())
        assertTrue(segments("2026-11-22", "2026-12-30").isEmpty())
    }

    @Test
    fun project_covering_the_whole_window_is_four_flat_rows() {
        val s = segments("2026-01-01", "2027-12-31")
        assertEquals(4, s.size)
        assertTrue(s.all { it.firstCol == 0 && it.lastCol == 6 && !it.startsHere && !it.endsHere })
    }

    @Test
    fun segments_never_leave_the_row_and_are_in_order() {
        // every start/end pair inside the window yields ordered, in-bounds segments covering exactly its days
        for (i in days.indices) for (j in i until days.size) {
            val s = MiniCalendarMath.bandSegments(days, days[i], days[j])
            val covered = s.flatMap { seg -> (seg.firstCol..seg.lastCol).map { seg.row * 7 + it } }
            assertEquals("$i..$j", (i..j).toList(), covered)
            assertEquals("$i..$j", true, s.first().startsHere)
            assertEquals("$i..$j", true, s.last().endsHere)
            assertTrue("$i..$j", s.dropLast(1).none { it.endsHere } && s.drop(1).none { it.startsHere })
        }
    }
}
