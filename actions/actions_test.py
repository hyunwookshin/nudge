#!/usr/bin/python3

import unittest
from datetime import datetime, timezone, timedelta

from models import reminder
import emailaction
from fake import FakeServer
import acts

class TestEmail():
    def getSender(self):
        return "_sender@gmail.com"

    def getPassword(self):
        return "test_password"
    
    def getRecepientLow(self):
        return "_low@gmail.com"

    def getRecepientHigh(self):
        return "_high@gmail.com"

class TestConfig():
    def getEmail(self):
        return TestEmail()

r = reminder.Reminder({
    "Title": "some title",
    "Description": "some description",
    "Time": "2024-01-01 12:00:00",
    "Link": "https://google.com",
    "Priority": 1,
    "Closed": False
})

class TestEmailAction(unittest.TestCase):

    def setUp(self):
        # Mock configuration
        self.config = TestConfig()

    def test_email_action_low(self):
        test_server = FakeServer()
        self.assertEqual(test_server.tls, False)
        email_action = emailaction.EmailAction(self.config,
                False, test_server)
        email_action.actuate(r)

        self.assertEqual(test_server.tls, True)
        self.assertEqual(test_server.sender, "_sender@gmail.com")
        self.assertEqual(test_server.password, "test_password")
        self.assertEqual(test_server.recepient, "_low@gmail.com")
        self.assertIn("Subject: some title", test_server.text)
        self.assertIn("some description", test_server.text)

    def test_email_action_high(self):
        test_server = FakeServer()
        self.assertEqual(test_server.tls, False)
        email_action = emailaction.EmailAction(self.config,
                True, test_server)
        email_action.actuate(r)

        self.assertEqual(test_server.tls, True)
        self.assertEqual(test_server.sender, "_sender@gmail.com")
        self.assertEqual(test_server.password, "test_password")
        self.assertEqual(test_server.recepient, "_high@gmail.com")
        self.assertIn("Subject: some title", test_server.text)
        self.assertIn("some description", test_server.text)

class TestActions(unittest.TestCase):

    def test_actions(self):
        priority = TestPriority()
        priorities = [priority]
        actions = acts.Actions(priorities)
        action = priority.getActions()[0]
        self.assertEqual(action.actuated, False)
        r.time = actions.getCurrentUTCTime() + timedelta(minutes=1)
        actions.actuate(r)

        action = priority.getActions()[0]
        self.assertEqual(action.actuated, True)

class TestPriority:
    def __init__(self):
        self.actions = [TestAction()]

    def applies(self, reminder):
        return True

    def getActions(self):
        return self.actions

class TestAction:
    def __init__(self):
        self.actuated = False

    def actuate(self, reminder, dryrun=False):
        self.actuated = True

if __name__ == "__main__":
    unittest.main()
