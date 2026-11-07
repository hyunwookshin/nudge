import os
import pymysql
import pymysql.cursors

from models import reminder
from models import template
from auth import crypto_utils

HOST = os.getenv("NUDGE_MYSQL_HOST", "nudge.cmqx6tpknayx.us-east-2.rds.amazonaws.com")
DB   = "nudge"


def _connect(user, password):
    return pymysql.connect(
        host=HOST,
        user=user,
        password=password,
        database=DB,
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


class MySQLDataSource:

    def __init__(self, config, user="", encrypt=False, password_hash=""):
        self.config = config
        self.user = user
        self.encrypt = encrypt
        # Same key-derivation as YamlDataSource: SHA-256 the hash a second time
        # so the raw hash never touches the encryption layer directly.
        import hashlib
        self.password_hash = hashlib.sha256(password_hash.encode("utf-8")).digest() if password_hash else b""
        self.db_user = config.getMySQLUser()
        self.db_password = config.getMySQLPassword()

    # ------------------------------------------------------------------
    # Public interface (mirrors YamlDataSource)
    # ------------------------------------------------------------------

    def loadReminders(self):
        conn = _connect(self.db_user, self.db_password)
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id, title, description, link, time, read_time, "
                    "       priority, closed, snooze, repeat_days, "
                    "       kind, end_date, project_type, parent_id "
                    "FROM reminders WHERE username = %s",
                    (self.user,),
                )
                rows = cur.fetchall()
        finally:
            conn.close()

        results = []
        for row in rows:
            info = self._row_to_info(row)
            if self.encrypt:
                info = crypto_utils.decrypt_reminder_fields(info, self.password_hash)
            results.append(reminder.Reminder(info))
        return results

    def storeReminders(self, reminders):
        conn = _connect(self.db_user, self.db_password)
        try:
            with conn.cursor() as cur:
                # Replace all rows for this user atomically.
                cur.execute("DELETE FROM reminders WHERE username = %s", (self.user,))
                for r in reminders:
                    info = r.toInfo()
                    if self.encrypt:
                        info = crypto_utils.encrypt_reminder_fields(info, self.password_hash)
                    cur.execute(
                        "INSERT INTO reminders "
                        "  (id, username, title, description, link, time, read_time, "
                        "   priority, closed, snooze, repeat_days, "
                        "   kind, end_date, project_type, parent_id) "
                        "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                        (
                            info["Id"],
                            self.user,
                            info["Title"],
                            info["Description"],
                            info["Link"],
                            info["Time"],        # "yyyy-MM-dd HH:mm:ss" UTC string
                            info["Read"],
                            info["Priority"],
                            info["Closed"],
                            info["Snooze"],
                            info["Repeat"],
                            info["Kind"],
                            info["EndDate"] or None,   # DATE column: NULL for plain events
                            info["ProjectType"],
                            info["ParentId"],
                        ),
                    )
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def loadTemplates(self):
        conn = _connect(self.db_user, self.db_password)
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT id, title, description, link, priority "
                    "FROM templates WHERE username = %s",
                    (self.user,),
                )
                rows = cur.fetchall()
        finally:
            conn.close()

        results = []
        for row in rows:
            info = self._template_row_to_info(row)
            if self.encrypt:
                info = crypto_utils.decrypt_reminder_fields(info, self.password_hash)
            results.append(template.Template(info))
        return results

    def storeTemplates(self, templates):
        conn = _connect(self.db_user, self.db_password)
        try:
            with conn.cursor() as cur:
                # Replace all rows for this user atomically.
                cur.execute("DELETE FROM templates WHERE username = %s", (self.user,))
                for t in templates:
                    info = t.toInfo()
                    if self.encrypt:
                        info = crypto_utils.encrypt_reminder_fields(info, self.password_hash)
                    cur.execute(
                        "INSERT INTO templates "
                        "  (id, username, title, description, link, priority) "
                        "VALUES (%s, %s, %s, %s, %s, %s)",
                        (
                            info["Id"],
                            self.user,
                            info["Title"],
                            info["Description"],
                            info["Link"],
                            info["Priority"],
                        ),
                    )
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def deleteUser(self):
        conn = _connect(self.db_user, self.db_password)
        try:
            with conn.cursor() as cur:
                cur.execute("DELETE FROM reminders WHERE username = %s", (self.user,))
                cur.execute("DELETE FROM templates WHERE username = %s", (self.user,))
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _row_to_info(self, row):
        """Convert a DictCursor row to the info dict shape Reminder.__init__ expects."""
        return {
            "Id":          row["id"],
            "Title":       row["title"],
            "Description": row["description"],
            "Link":        row["link"],
            # pymysql returns DATETIME columns as datetime objects; convert to the
            # string format Reminder.__init__ parses: "%Y-%m-%d %H:%M:%S"
            "Time":        row["time"].strftime("%Y-%m-%d %H:%M:%S"),
            "Read":        row["read_time"].strftime("%Y-%m-%d %H:%M:%S"),
            "Priority":    row["priority"],
            "Closed":      bool(row["closed"]),
            "Snooze":      row["snooze"],
            "Repeat":      row["repeat_days"],
            "Kind":        row["kind"],
            "EndDate":     row["end_date"].strftime("%Y-%m-%d") if row["end_date"] else "",
            "ProjectType": row["project_type"],
            "ParentId":    row["parent_id"],
        }

    def _template_row_to_info(self, row):
        """Convert a DictCursor row to the info dict shape Template.__init__ expects."""
        return {
            "Id":          row["id"],
            "Title":       row["title"],
            "Description": row["description"],
            "Link":        row["link"],
            "Priority":    row["priority"],
        }
