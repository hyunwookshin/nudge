from datetime import datetime, timezone

class Reminder():

    def __init__(self, info):
        self.title = info["Title"]
        self.description = info["Description"]
        timestamp_str = info["Time"]
        self.time = datetime.strptime(timestamp_str, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
        self.link = info["Link"]
        self.priority = info["Priority"]
        self.closed = info["Closed"]
        self.snooze = info.get("Snooze", 0)
        self.read = self.get_current_utc()

    def get_current_utc(self):
        return datetime.utcnow().replace(tzinfo=timezone.utc)

    def toInfo(self):
        info = {
            "Title": self.title,
            "Description": self.description,
            "Time": self.time.strftime("%Y-%m-%d %H:%M:%S"),
            "Read": self.read,
            "Link": self.link,
            "Priority": self.priority,
            "Closed": self.closed,
            "Snooze": self.snooze
        }
        return info
