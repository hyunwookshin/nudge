package com.hyunwookshin.nudge

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/add_reminder")
    fun addReminder(@Body reminder: Reminder): Call<Void>
}