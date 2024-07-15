from datetime import datetime, timezone

class Actions():
    def __init__(self, priorities):
        self.priorities = priorities

    def getCurrentUTCTime(self):
        return datetime.utcnow().replace(tzinfo=timezone.utc)

    def actuate(self, reminder, ignorets=False, dryrun=False):
        current_utc_time = self.getCurrentUTCTime()
        time_difference = reminder.time - current_utc_time
        for priority in self.priorities:
            if priority.applies(reminder) and \
                (0 <= time_difference.total_seconds() <= 60*60 or ignorets):
                for action in priority.getActions():
                    action.actuate(reminder, dryrun)
                    return

class Action():
    def __init__(self):
        pass
