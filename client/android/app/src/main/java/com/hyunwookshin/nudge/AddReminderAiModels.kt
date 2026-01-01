package com.hyunwookshin.nudge

data class AddReminderAiRequest(
    val Text: String,
    val Key: String
)

data class AddReminderAiResponse(
    val message: String,
    val parsed: ParsedReminder
)

data class ParsedReminder(
    val Title: String,
    val Description: String,
    val Date: String,     // yyyy-MM-dd
    val Time: String,     // HH:mm:ss
    val Link: String?
)
