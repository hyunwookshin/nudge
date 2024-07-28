#!/usr/bin/pytohn3

class RemindJob():

    def __init__(self, datasource, actions):
        self.datasource = datasource
        self.actions = actions

    def run(self, ignorets=False, dryrun=False):
        reminders = self.datasource.loadReminders()
        for reminder in reminders:
            reminder.updateRead()
            self.actions.actuate(reminder, ignorets, dryrun)

        self.datasource.storeReminders(reminders)
