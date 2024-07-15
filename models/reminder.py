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

    def toInfo(self):
        info = {
            "Title": self.title,
            "Description": self.description,
            "Time": self.time.strftime("%Y-%m-%d %H:%M:%S"),
            "Link": self.link,
            "Priority": self.priority,
            "Closed": self.closed
        }
        return info
