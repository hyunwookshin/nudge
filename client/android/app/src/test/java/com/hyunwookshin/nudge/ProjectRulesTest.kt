package com.hyunwookshin.nudge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRulesTest {

    private fun project(start: String, end: String) =
        Reminder(Title = "Japan", Time = "$start 09:00:00", EndDate = end, Kind = Reminder.KIND_PROJECT, Id = "p1")

    private fun event(day: String, parent: String = "") =
        Reminder(Title = "Flight", Description = "x", Time = "$day 11:30:00", Id = "e1", ParentId = parent)

    @Test
    fun event_must_be_inside_the_project_range_inclusive() {
        val p = project("2026-12-04", "2026-12-10")
        assertTrue(AddToProjectDialog.inRange(event("2026-12-04"), p))    // first day
        assertTrue(AddToProjectDialog.inRange(event("2026-12-07"), p))
        assertTrue(AddToProjectDialog.inRange(event("2026-12-10"), p))    // last day
        assertFalse(AddToProjectDialog.inRange(event("2026-12-03"), p))
        assertFalse(AddToProjectDialog.inRange(event("2026-12-11"), p))
    }

    @Test
    fun range_works_across_months_and_years() {
        val p = project("2026-12-28", "2027-01-03")
        assertTrue(AddToProjectDialog.inRange(event("2027-01-01"), p))
        assertFalse(AddToProjectDialog.inRange(event("2027-01-04"), p))
    }

    @Test
    fun kind_defaults_and_helpers() {
        assertFalse(event("2026-12-05").isProject)
        assertTrue(project("2026-12-04", "2026-12-10").isProject)
        assertEquals("event", Reminder().Kind)
        assertEquals("", Reminder().ParentId)
    }

    @Test
    fun project_types_are_unique_start_with_none_and_fall_back_to_none() {
        val keys = ProjectTypes.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("", keys.first())
        assertEquals(null, ProjectTypes.byKey("").iconRes)
        assertEquals("", ProjectTypes.byKey("no-such-type").key)
        assertNotNull(ProjectTypes.iconFor("trip"))
    }

    @Test
    fun every_project_type_with_an_icon_has_a_distinct_known_label() {
        val labels = ProjectTypes.all.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(ProjectTypes.all.filter { it.key.isNotEmpty() }.all { it.iconRes != null })
    }
}
