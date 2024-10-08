#!/usr/bin/python3

import unittest

import remind

class TestDataStore():
    def __init__(self, reminders):
        self.reminders = reminders

    def loadReminders(self):
        return self.reminders

    def storeReminders(self, reminders):
        self.reminders = reminders

class TestReminder():
    def __init__(self):
        self.actuated = False

    def updateRead(self):
        pass

class TestActions():
    def actuate(self, reminder, ignoretes=False, dryrun=False):
        reminder.actuated = True

class TestRemind(unittest.TestCase):
    def test_remind(self):
        r = TestReminder()
        datastore = TestDataStore([r])
        acts = TestActions()
        self.assertEqual(r.actuated, False)

        job = remind.RemindJob(datastore, acts)
        job.run()

        self.assertEqual(r.actuated, True)

if __name__ == "__main__":
    unittest.main()
