#!/usr/bin/python3

import unittest
from datetime import datetime, timezone
import yaml

from models import reminder
from data import yamldatasource

# Sample reminder data in YAML format
sample_yaml_data = """
- Title: Meeting
  Description: Team meeting to discuss project updates
  Time: "2024-07-14 10:00:00"
  Link: "http://example.com"
  Closed: false
  Priority: 2
"""

class TestConfig():
    def getStorePath(self):
        return "test"

class TestYamlDataSource(unittest.TestCase):

    def setUp(self):
        # Mock configuration
        self.config = TestConfig()
        self.data_source = yamldatasource.YamlDataSource(self.config)

    def tearDown(self):
        with open(self.config.getStorePath() + "/reminders.yaml", "w") as f:
            f.write('')

    def test_loadReminders(self):
        # Test loading reminders from YAML data
        with open(self.config.getStorePath() + "/reminders.yaml", "w") as f:
            f.write(sample_yaml_data)

        reminders = self.data_source.loadReminders()
        self.assertEqual(len(reminders), 1)
        self.assertEqual(reminders[0].title, "Meeting")
        self.assertEqual(reminders[0].description, "Team meeting to discuss project updates")
        self.assertEqual(reminders[0].time, datetime(2024, 7, 14, 10, 0, 0, tzinfo=timezone.utc))
        self.assertEqual(reminders[0].link, "http://example.com")
        self.assertEqual(reminders[0].priority, 2)
        self.assertEqual(reminders[0].closed, False)

    def test_storeReminders(self):
        # Create a sample reminder object
        r = reminder.Reminder({
            "Title": "Meeting 2",
            "Description": "Second team meeting to discuss project updates",
            "Time": "2024-07-14 10:00:00",
            "Link": "http://example.com",
            "Closed": False,
            "Priority": 2
        })

        # Test storing reminders to YAML file
        self.data_source.storeReminders([r])
        with open(self.config.getStorePath() + "/reminders.yaml", "r") as f:
            yaml_data_written = f.read()

        reminders_from_yaml = yaml.safe_load(yaml_data_written)
        self.assertEqual(len(reminders_from_yaml), 1)
        self.assertEqual(reminders_from_yaml[0]["Title"], "Meeting 2")
        self.assertEqual(reminders_from_yaml[0]["Description"], "Second team meeting to discuss project updates")
        self.assertEqual(reminders_from_yaml[0]["Time"], "2024-07-14 10:00:00")
        self.assertEqual(reminders_from_yaml[0]["Link"], "http://example.com")
        self.assertEqual(reminders_from_yaml[0]["Closed"], False)
        self.assertEqual(reminders_from_yaml[0]["Priority"], 2)

if __name__ == "__main__":
    unittest.main()
