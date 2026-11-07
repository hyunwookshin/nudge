import os
import shutil
import yaml
import hashlib

from models import reminder
from models import template

from auth import crypto_utils

FILE_NAME = "reminders.yaml"
TEMPLATE_FILE_NAME = "templates.yaml"

def derive_key_from_hash(password_hash: str) -> bytes:
     return hashlib.sha256(password_hash.encode("utf-8")).digest()


class YamlDataSource:

    def __init__(self, config, user="", encrypt=False, password_hash=""):
        self.config = config
        self.user = user
        self.encrypt = encrypt
        self.password_hash = derive_key_from_hash(password_hash)

    def getYamlUserPath(self, file_name):
        base = self.config.getStorePath()
        user_dir = os.path.join(base, "users")
        user_dir = os.path.join(user_dir, self.user)
        os.makedirs(user_dir, exist_ok=True)
        return os.path.join(user_dir, file_name)

    def getYamlReminderPath(self):
        return self.getYamlUserPath(FILE_NAME)

    def getYamlTemplatePath(self):
        return self.getYamlUserPath(TEMPLATE_FILE_NAME)

    def readInfos(self, path):
        if not os.path.exists(path):
            return []

        with open(path, "r") as f:
            raw = f.read().strip()
            infos = yaml.safe_load(raw) or []

        # Field-level decrypt (only if encrypt=True)
        if self.encrypt:
            if not self.password_hash:
                raise ValueError("password_hash required when encrypt=True")
            infos = [crypto_utils.decrypt_reminder_fields(info, self.password_hash)
                     if isinstance(info, dict) else info for info in infos]
        return infos

    def writeInfos(self, path, infos):
        # Field-level encrypt (only if encrypt=True)
        if self.encrypt:
            if not self.password_hash:
                raise ValueError("password_hash required when encrypt=True")
            infos = [crypto_utils.encrypt_reminder_fields(info, self.password_hash)
                     if isinstance(info, dict) else info for info in infos]

        with open(path, "w") as f:
            text = yaml.safe_dump(infos, default_flow_style=False, indent=4)
            f.write(text.strip())

    def loadReminders(self):
        infos = self.readInfos(self.getYamlReminderPath())
        return [reminder.Reminder(info) for info in infos]

    def storeReminders(self, reminders):
        self.writeInfos(self.getYamlReminderPath(), [r.toInfo() for r in reminders])

    def loadTemplates(self):
        infos = self.readInfos(self.getYamlTemplatePath())
        return [template.Template(info) for info in infos]

    def storeTemplates(self, templates):
        self.writeInfos(self.getYamlTemplatePath(), [t.toInfo() for t in templates])

    def deleteUser(self):
        user_dir = os.path.join(self.config.getStorePath(), "users", self.user)
        if os.path.exists(user_dir):
            shutil.rmtree(user_dir)
