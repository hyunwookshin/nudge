import yaml
import os

from models import reminder

FILE_NAME = "reminders.yaml"

class YamlDataSource:

    def __init__(self, config):
        self.config = config

    def getYamlReminderPath(self):
        return self.config.getStorePath() + "/" + FILE_NAME

    def loadReminders(self):
        with open(self.getYamlReminderPath(), "r") as f:
            infos = yaml.safe_load(f.read().strip())
        return [ reminder.Reminder(info) for info in infos ]

    def storeReminders(self, reminders):
        reminders = reminders[::-1]
        filtered = []
        unclosed = set()
        for reminder in reminders:
            if not reminder.closed:
                if reminder.title in unclosed:
                    continue
                unclosed.add(reminder.title)
            filtered.append(reminder)

        reminders = filtered[::-1]
        with open(self.getYamlReminderPath(), "w") as f:
            infos = yaml.safe_dump([r.toInfo() for r in reminders],
                    default_flow_style=False, indent=4)
            f.write(infos.strip())
