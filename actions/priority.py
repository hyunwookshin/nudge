from datetime import datetime, timezone

from actions import emailaction
from actions.fake import FakeServer

def getPriorities(config, ignorets=False):
    return [VeryHighPriority(config, ignorets),
            HighPriority(config, ignorets),
            MediumPriority(config, ignorets),
            LowPriority(config, ignorets)]

class Priority():
    def __init__(self, value, actions, notif_time_seconds):
        self.value = value
        self.notif_time_seconds = notif_time_seconds
        self.actions = actions

    def applies(self, reminder):
        return self.value == reminder.priority

    def getActions(self):
        return self.actions

class VeryHighPriority(Priority):
    def __init__(self, config, ignorets=False):
        test_server = FakeServer() if ignorets else None
        super().__init__(0, [emailaction.EmailAction(config, True, test_server)], 60*60*2)

class HighPriority(Priority):
    def __init__(self, config, ignorets=False):
        test_server = FakeServer() if ignorets else None
        super().__init__(1, [emailaction.EmailAction(config, True, test_server)], 60*60)

class MediumPriority(Priority):
    def __init__(self, config, ignorets=False):
        test_server = FakeServer() if ignorets else None
        super().__init__(2, [emailaction.EmailAction(config, False, test_server)], 60*60*6)

class LowPriority(Priority):
    def __init__(self, config, ignorets=False):
        test_server = FakeServer() if ignorets else None
        super().__init__(3, [emailaction.EmailAction(config, False, test_server)], 60*60*24)
