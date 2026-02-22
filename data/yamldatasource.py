import yaml
import os

from models import reminder

FILE_NAME = "reminders.yaml"

class YamlDataSource:

    def __init__(self, config, user=""):
        self.config = config
        self.user = user

    def getYamlReminderPath(self):
        base = self.config.getStorePath()
        user_dir = os.path.join(base, "users")
        user_dir = os.path.join(user_dir, self.user)
        os.makedirs(user_dir, exist_ok=True)
        return os.path.join(user_dir, FILE_NAME)

    def loadReminders(self):
        path = self.getYamlReminderPath()
        if not os.path.exists(path):
            return []
        with open(path, "r") as f:
            infos = yaml.safe_load(f.read().strip())
        return [ reminder.Reminder(info) for info in infos ]

    def storeReminders(self, reminders):
        reminders = reminders[::-1]
        filtered = []
        for reminder in reminders:
            filtered.append(reminder)

        reminders = filtered[::-1]
        with open(self.getYamlReminderPath(), "w") as f:
            infos = yaml.safe_dump([r.toInfo() for r in reminders],
                    default_flow_style=False, indent=4)
            f.write(infos.strip())
