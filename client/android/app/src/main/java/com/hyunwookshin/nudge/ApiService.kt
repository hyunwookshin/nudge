package com.hyunwookshin.nudge

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET

interface ApiService {
    @POST("/add_reminder")
    fun addReminder(@Body reminder: Reminder): Call<Void>

    @GET("/reminders")
    fun getReminders(): Call<ReminderResponse>
}