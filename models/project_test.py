#!/usr/bin/python3
import unittest
from datetime import date

from models import project


class TestProjectRules(unittest.TestCase):

    START = date(2026, 12, 4)

    def test_parse_day(self):
        self.assertEqual(project.parse_day("2026-12-10"), date(2026, 12, 10))
        for bad in ("", None, "2026-13-01", "12/10/2026", "2026-12-32"):
            self.assertIsNone(project.parse_day(bad))

    def test_end_date_must_not_be_before_start(self):
        self.assertIsNone(project.check_project_dates(self.START, "2026-12-04"))   # one-day project
        self.assertIsNone(project.check_project_dates(self.START, "2026-12-20"))
        self.assertEqual(project.check_project_dates(self.START, "2026-12-03"), project.BAD_END_DATE)
        self.assertEqual(project.check_project_dates(self.START, ""), project.BAD_END_DATE)
        self.assertEqual(project.check_project_dates(self.START, "garbage"), project.BAD_END_DATE)

    def test_member_must_be_inside_range_inclusive(self):
        end = "2026-12-10"
        for day in (date(2026, 12, 4), date(2026, 12, 7), date(2026, 12, 10)):
            self.assertIsNone(project.check_member(day, self.START, end), day)
        for day in (date(2026, 12, 3), date(2026, 12, 11), date(2027, 12, 5)):
            self.assertEqual(project.check_member(day, self.START, end), project.NOT_IN_RANGE, day)

    def test_member_check_with_broken_project_end_is_rejected(self):
        self.assertEqual(project.check_member(self.START, self.START, ""), project.NOT_IN_RANGE)

    def test_range_crossing_year_and_leap_day(self):
        self.assertIsNone(project.check_member(date(2027, 1, 1), date(2026, 12, 28), "2027-01-03"))
        self.assertIsNone(project.check_member(date(2028, 2, 29), date(2028, 2, 27), "2028-03-01"))
        self.assertEqual(project.check_member(date(2027, 2, 29 - 1), date(2027, 3, 1), "2027-03-05"), project.NOT_IN_RANGE)

    def test_outside_members_counts_only_those_outside(self):
        days = [date(2026, 12, 3), date(2026, 12, 4), date(2026, 12, 9), date(2026, 12, 11)]
        self.assertEqual(project.outside_members(days, date(2026, 12, 4), date(2026, 12, 10)), 2)
        self.assertEqual(project.outside_members([], date(2026, 12, 4), date(2026, 12, 10)), 0)


if __name__ == "__main__":
    unittest.main()
