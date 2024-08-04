package com.hyunwookshin.nudge

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @POST("/add_reminder")
    fun addReminder(@Body reminder: Reminder): Call<Void>

    @GET("/reminders")
    fun getReminders(): Call<ReminderResponse>

    @GET("/reminders")
    fun getAllReminders(@Query("include") include: String = "all"): Call<ReminderResponse>
}