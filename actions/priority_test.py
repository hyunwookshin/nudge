#!/usr/bin/python3

import priority
import unittest

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

class TestPriorities(unittest.TestCase):
    def test_priorities(self):
        config = TestConfig()
        lowPriority = priority.LowPriority(config)
        self.assertEqual(lowPriority.value, 3)
        self.assertEqual(lowPriority.notif_time_seconds, 60*60*24)
        self.assertEqual(lowPriority.actions[0].high_priority, False)

        medPriority = priority.MediumPriority(config)
        self.assertEqual(medPriority.value, 2)
        self.assertEqual(medPriority.notif_time_seconds, 60*60*3)
        self.assertEqual(medPriority.actions[0].high_priority, False)

        highPriority = priority.HighPriority(config)
        self.assertEqual(highPriority.value, 1)
        self.assertEqual(highPriority.notif_time_seconds, 60*60*2)
        self.assertEqual(highPriority.actions[0].high_priority, True)

        veryHighPriority = priority.VeryHighPriority(config)
        self.assertEqual(veryHighPriority.value, 0)
        self.assertEqual(veryHighPriority.notif_time_seconds, 60*60)
        self.assertEqual(veryHighPriority.actions[0].high_priority, True)

if __name__ == "__main__":
    unittest.main()
