package com.hyunwookshin.nudge

interface ReminderCallback {
    fun onShowReminders()
    fun onEditReminder(reminder: Reminder)
}