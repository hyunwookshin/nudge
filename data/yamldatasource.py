import os
import shutil
import yaml
import hashlib

from models import reminder

from auth import crypto_utils

FILE_NAME = "reminders.yaml"

def derive_key_from_hash(password_hash: str) -> bytes:
     return hashlib.sha256(password_hash.encode("utf-8")).digest()


class YamlDataSource:

    def __init__(self, config, user="", encrypt=False, password_hash=""):
        self.config = config
        self.user = user
        self.encrypt = encrypt
        self.password_hash = derive_key_from_hash(password_hash)

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
            raw = f.read().strip()
            infos = yaml.safe_load(raw) or []

        # Field-level decrypt (only if encrypt=True)
        if self.encrypt:
            if not self.password_hash:
                raise ValueError("password_hash required when encrypt=True")

            decrypted_infos = []
            for info in infos:
                if isinstance(info, dict):
                    info = crypto_utils.decrypt_reminder_fields(info, self.password_hash)
                decrypted_infos.append(info)
            infos = decrypted_infos

        return [reminder.Reminder(info) for info in infos]

    def storeReminders(self, reminders):
        reminders = reminders[::-1]
        filtered = []
        for r in reminders:
            filtered.append(r)
        reminders = filtered[::-1]

        infos = [r.toInfo() for r in reminders]

        # Field-level encrypt (only if encrypt=True)
        if self.encrypt:
            if not self.password_hash:
                raise ValueError("password_hash required when encrypt=True")

            encrypted_infos = []
            for info in infos:
                if isinstance(info, dict):
                    info = crypto_utils.encrypt_reminder_fields(info, self.password_hash)
                encrypted_infos.append(info)
            infos = encrypted_infos

        with open(self.getYamlReminderPath(), "w") as f:
            text = yaml.safe_dump(infos, default_flow_style=False, indent=4)
            f.write(text.strip())

    def deleteUser(self):
        user_dir = os.path.join(self.config.getStorePath(), "users", self.user)
        if os.path.exists(user_dir):
            shutil.rmtree(user_dir)
