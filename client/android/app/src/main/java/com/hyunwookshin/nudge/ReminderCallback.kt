package com.hyunwookshin.nudge

interface ReminderCallback {
    fun onShowReminders()
    fun onEditReminder(reminder: Reminder, isOffline: Boolean = false)

    fun onCopyReminder(reminder: Reminder)

    fun onDeleteReminder(reminder: Reminder)
    
    fun onReminderUpdated()

    fun onAbortEditReminder()
}