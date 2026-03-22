#!/usr/bin/env python3

import unittest
import sys
import os
from datetime import datetime, timezone
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from models import reminder
from data import mysqldatasource


class TestConfig:
    def getMySQLUser(self):
        return "test_user"

    def getMySQLPassword(self):
        return "test_password"


def _make_reminder(title="Meeting", description="Team meeting", time="2024-07-14 10:00:00"):
    return reminder.Reminder({
        "Title":       title,
        "Description": description,
        "Time":        time,
        "Read":        "2024-07-13 10:00:00",
        "Link":        "http://example.com",
        "Closed":      False,
        "Priority":    2,
        "Snooze":      0,
        "Repeat":      0,
        "Id":          "abc123",
    })


def _make_row(title="Meeting", description="Team meeting"):
    """Return a dict shaped like a pymysql DictCursor row."""
    return {
        "id":          "abc123",
        "title":       title,
        "description": description,
        "link":        "http://example.com",
        "time":        datetime(2024, 7, 14, 10, 0, 0),
        "read_time":   datetime(2024, 7, 13, 10, 0, 0),
        "priority":    2,
        "closed":      False,
        "snooze":      0,
        "repeat_days": 0,
    }


class TestMySQLDataSource(unittest.TestCase):

    def setUp(self):
        self.config = TestConfig()
        self.ds = mysqldatasource.MySQLDataSource(self.config, user="alice")

    # ------------------------------------------------------------------
    # loadReminders
    # ------------------------------------------------------------------

    @patch("data.mysqldatasource._connect")
    def test_loadReminders(self, mock_connect):
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value.fetchall.return_value = [_make_row()]

        reminders = self.ds.loadReminders()

        self.assertEqual(len(reminders), 1)
        self.assertEqual(reminders[0].title, "Meeting")
        self.assertEqual(reminders[0].description, "Team meeting")
        self.assertEqual(reminders[0].time, datetime(2024, 7, 14, 10, 0, 0, tzinfo=timezone.utc))
        self.assertEqual(reminders[0].link, "http://example.com")
        self.assertEqual(reminders[0].priority, 2)
        self.assertEqual(reminders[0].closed, False)
        mock_conn.close.assert_called_once()

    @patch("data.mysqldatasource._connect")
    def test_loadReminders_empty(self, mock_connect):
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value.fetchall.return_value = []

        reminders = self.ds.loadReminders()

        self.assertEqual(reminders, [])
        mock_conn.close.assert_called_once()

    # ------------------------------------------------------------------
    # storeReminders
    # ------------------------------------------------------------------

    @patch("data.mysqldatasource._connect")
    def test_storeReminders(self, mock_connect):
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        mock_cur = mock_conn.cursor.return_value.__enter__.return_value

        r = _make_reminder()
        self.ds.storeReminders([r])

        # DELETE then INSERT
        calls = mock_cur.execute.call_args_list
        self.assertEqual(len(calls), 2)
        self.assertIn("DELETE", calls[0][0][0])
        self.assertIn("INSERT", calls[1][0][0])

        insert_args = calls[1][0][1]
        self.assertEqual(insert_args[0], "abc123")   # id
        self.assertEqual(insert_args[1], "alice")    # username
        self.assertEqual(insert_args[2], "Meeting")  # title

        mock_conn.commit.assert_called_once()
        mock_conn.close.assert_called_once()

    @patch("data.mysqldatasource._connect")
    def test_storeReminders_rollback_on_error(self, mock_connect):
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value.execute.side_effect = Exception("DB error")

        with self.assertRaises(Exception):
            self.ds.storeReminders([_make_reminder()])

        mock_conn.rollback.assert_called_once()
        mock_conn.close.assert_called_once()

    # ------------------------------------------------------------------
    # deleteUser
    # ------------------------------------------------------------------

    @patch("data.mysqldatasource._connect")
    def test_deleteUser(self, mock_connect):
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        mock_cur = mock_conn.cursor.return_value.__enter__.return_value

        self.ds.deleteUser()

        sql, params = mock_cur.execute.call_args[0]
        self.assertIn("DELETE", sql)
        self.assertEqual(params, ("alice",))
        mock_conn.commit.assert_called_once()
        mock_conn.close.assert_called_once()

    @patch("data.mysqldatasource._connect")
    def test_deleteUser_rollback_on_error(self, mock_connect):
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        mock_conn.cursor.return_value.__enter__.return_value.execute.side_effect = Exception("DB error")

        with self.assertRaises(Exception):
            self.ds.deleteUser()

        mock_conn.rollback.assert_called_once()
        mock_conn.close.assert_called_once()

    # ------------------------------------------------------------------
    # _row_to_info
    # ------------------------------------------------------------------

    def test_row_to_info(self):
        row = _make_row()
        info = self.ds._row_to_info(row)

        self.assertEqual(info["Id"],          "abc123")
        self.assertEqual(info["Title"],       "Meeting")
        self.assertEqual(info["Description"], "Team meeting")
        self.assertEqual(info["Link"],        "http://example.com")
        self.assertEqual(info["Time"],        "2024-07-14 10:00:00")
        self.assertEqual(info["Read"],        "2024-07-13 10:00:00")
        self.assertEqual(info["Priority"],    2)
        self.assertEqual(info["Closed"],      False)
        self.assertEqual(info["Snooze"],      0)
        self.assertEqual(info["Repeat"],      0)


if __name__ == "__main__":
    unittest.main()
