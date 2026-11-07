package com.hyunwookshin.nudge

data class ReminderResponse(
    val reminders: List<Reminder>,
    // only in the answer to ?parent=<project id>: the project itself
    val project: Reminder? = null,
)
