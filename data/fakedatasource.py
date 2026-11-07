import yaml
from models import reminder

sample_yaml_data = """
Title: Meeting
Description: Team meeting to discuss project updates
Time: "2024-07-14 10:00:00"
Link: "http://example.com"
Closed: false
Priority: 2
"""

class FakeDataSource:

    def __init__(self, config):
        self.reminders = [ reminder.Reminder(sample_yaml_data) ]
        self.templates = []

    def loadReminders(self):
        return self.reminders

    def storeReminders(self, reminders):
        self.reminders = reminders

    def loadTemplates(self):
        return self.templates

    def storeTemplates(self, templates):
        self.templates = templates

    def deleteUser(self):
        self.reminders = []
        self.templates = []
