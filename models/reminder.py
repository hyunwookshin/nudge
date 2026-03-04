from datetime import datetime, timezone
import time
import hashlib

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
        self.repeat = info.get("Repeat", 0)
        timestamp_str = info.get("Read", info["Time"])
        self.read = datetime.strptime(timestamp_str, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
        self.id = info.get("Id", self.gen_id(str(time.time())))
        if not self.id:
            self.id = self.gen_id(str(time.time()))

    def updateRead(self):
        self.read = self.get_current_utc()

    def get_current_utc(self):
        return datetime.utcnow().replace(tzinfo=timezone.utc)

    def gen_id(self, salt):
        data = self.title + "|" + self.description + "|" + salt
        return hashlib.md5(data.encode()).hexdigest()

    def toInfo(self):
        info = {
            "Title": self.title,
            "Description": self.description,
            "Time": self.time.strftime("%Y-%m-%d %H:%M:%S"),
            "Read": self.read.strftime("%Y-%m-%d %H:%M:%S"),
            "Link": self.link,
            "Priority": self.priority,
            "Closed": self.closed,
            "Snooze": self.snooze,
            "Repeat": self.repeat,
            "Id": self.id
        }
        return info
