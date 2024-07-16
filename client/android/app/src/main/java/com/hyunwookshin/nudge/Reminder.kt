package com.hyunwookshin.nudge

data class Reminder(
    val Title: String,
    val Description: String,
    val Date: String,
    val Time: String,
    val Link: String,
    val Priority: Int,
    val Key: String
)