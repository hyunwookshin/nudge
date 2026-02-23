import os
import yaml

from models import reminder

# NEW
from crypto_yaml import encrypt_yaml_to_b64, decrypt_yaml_from_b64

FILE_NAME = "reminders.yaml"


class YamlDataSource:
    def __init__(self, config, user="", encrypt=False, password_hash_bytes=None):
        """
        encrypt:
            If True, reminders.yaml will store base64(AESGCM(...)) instead of plain YAML.

        password_hash_bytes:
            bytes used as the secret input to HKDF key derivation.
            If you already have a password hash string, pass hash_str.encode("utf-8").
        """
        self.config = config
        self.user = user
        self.encrypt = encrypt
        self.password_hash_bytes = password_hash_bytes  # must be bytes when encrypt=True

    def getYamlReminderPath(self):
        base = self.config.getStorePath()
        user_dir = os.path.join(base, "users", self.user)
        os.makedirs(user_dir, exist_ok=True)
        return os.path.join(user_dir, FILE_NAME)

    def _require_key(self):
        if not self.encrypt:
            return None
        if not self.password_hash_bytes:
            raise ValueError("encrypt=True requires password_hash_bytes (bytes)")
        if not isinstance(self.password_hash_bytes, (bytes, bytearray)):
            raise ValueError("password_hash_bytes must be bytes")
        return bytes(self.password_hash_bytes)

    def loadReminders(self):
        path = self.getYamlReminderPath()
        if not os.path.exists(path):
            return []

        if not self.encrypt:
            with open(path, "r", encoding="utf-8") as f:
                raw = f.read().strip()
            if not raw:
                return []
            infos = yaml.safe_load(raw) or []
            return [reminder.Reminder(info) for info in infos]

        # Encrypted path (file is base64 text)
        key = self._require_key()
        with open(path, "rb") as f:
            b64 = f.read().strip()
        if not b64:
            return []
        infos = decrypt_yaml_from_b64(b64, key) or []
        return [reminder.Reminder(info) for info in infos]

    def storeReminders(self, reminders):
        reminders = reminders[::-1]
        filtered = []
        for r in reminders:
            filtered.append(r)
        reminders = filtered[::-1]

        infos_list = [r.toInfo() for r in reminders]

        path = self.getYamlReminderPath()

        if not self.encrypt:
            with open(path, "w", encoding="utf-8") as f:
                txt = yaml.safe_dump(infos_list, default_flow_style=False, indent=4)
                f.write(txt.strip())
            return

        # Encrypted write (base64)
        key = self._require_key()
        b64 = encrypt_yaml_to_b64(infos_list, key)
        with open(path, "wb") as f:
            f.write(b64.strip())
