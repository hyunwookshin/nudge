"""Rules for projects: a project spans start..end (inclusive local dates) and events can join it."""
from datetime import date

NOT_IN_RANGE = "Not part of the project's date range"
BAD_END_DATE = "End date must be on or after the start date"
NO_SUCH_PROJECT = "Project not found"


def parse_day(text):
    """yyyy-mm-dd -> date, or None if it is not a valid date."""
    try:
        return date.fromisoformat(text)
    except (TypeError, ValueError):
        return None


def day_in_range(day, start_day, end_day):
    return start_day <= day <= end_day


def check_project_dates(start_day, end_text):
    """Error message if the project's end date is missing/invalid/before the start, else None."""
    end_day = parse_day(end_text)
    if end_day is None or end_day < start_day:
        return BAD_END_DATE
    return None


def check_member(event_day, project_start_day, project_end_text):
    """Error message if an event on event_day may not join the project, else None."""
    end_day = parse_day(project_end_text)
    if end_day is None or not day_in_range(event_day, project_start_day, end_day):
        return NOT_IN_RANGE
    return None


def outside_members(member_days, start_day, end_day):
    """How many of the members' days fall outside start_day..end_day."""
    return sum(1 for day in member_days if not day_in_range(day, start_day, end_day))
