import time
import hashlib

class Template():
    '''
    A reusable reminder blueprint: the content of a reminder without its
    date/time or recurrence. Creating an event from a template copies these
    fields into a new reminder.
    '''

    def __init__(self, info):
        self.title = info["Title"]
        self.description = info["Description"]
        self.link = info.get("Link", "")
        self.priority = info.get("Priority", 2)
        self.id = info.get("Id")
        if not self.id:
            self.id = self.gen_id(str(time.time()))

    def gen_id(self, salt):
        data = self.title + "|" + self.description + "|" + salt
        return hashlib.md5(data.encode()).hexdigest()

    def toInfo(self):
        info = {
            "Title": self.title,
            "Description": self.description,
            "Link": self.link,
            "Priority": self.priority,
            "Id": self.id
        }
        return info
