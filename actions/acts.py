from datetime import datetime, timezone

class Actions():
    def __init__(self, priorities):
        self.priorities = priorities
        self.history = {}

    def getCurrentUTCTime(self):
        return datetime.utcnow().replace(tzinfo=timezone.utc)

    def actuate(self, reminder, ignorets=False, dryrun=False):
        current_utc_time = self.getCurrentUTCTime()
        time_difference = reminder.time - current_utc_time
        for priority in self.priorities:
            if priority.applies(reminder) and \
                (0 <= time_difference.total_seconds() <= 60*60 or ignorets):
                for action in priority.getActions():
                    action.actuate(reminder, self.history, dryrun)
            break

class Action():
    def __init__(self):
        pass

    def actuate(self, reminder):
        reminder.closed = True
