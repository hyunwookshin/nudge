from datetime import datetime, timezone
import time
import hashlib

KIND_EVENT = "event"
KIND_PROJECT = "project"

class Reminder():

    def is_project(self):
        return self.kind == KIND_PROJECT

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
        # Projects are a specialised reminder: "begins" at its Time, runs until end_date (a local
        # yyyy-mm-dd). project_type picks its color/icon; parent_id links an event to its project.
        self.kind = info.get("Kind") or KIND_EVENT
        self.end_date = info.get("EndDate") or ""
        self.project_type = info.get("ProjectType") or ""
        self.parent_id = info.get("ParentId") or ""
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
            "Kind": self.kind,
            "EndDate": self.end_date,
            "ProjectType": self.project_type,
            "ParentId": self.parent_id,
            "Id": self.id
        }
        return info
