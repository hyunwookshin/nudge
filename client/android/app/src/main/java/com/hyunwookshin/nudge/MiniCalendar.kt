package com.hyunwookshin.nudge

import java.time.LocalDate

data class DayState(
    val date: LocalDate,
    val count: Int,
    val hasHigh: Boolean,
    val hasLow: Boolean
)

