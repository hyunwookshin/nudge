package com.hyunwookshin.nudge

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

data class LoginRequest(val Username: String, val Password: String)
data class LoginResponse(val token: String, val username: String)

interface ApiService {
    @POST("/login")
    fun login(@Body req: LoginRequest): Call<LoginResponse>

    @POST("/add_reminder")
    fun addReminder(@Body reminder: Reminder): Call<Void>

    @POST("/delete_reminder")
    fun deleteReminder(@Body reminder: Reminder): Call<Void>

    @POST("add_reminder_ai")
    fun addReminderAI(@Body req: AddReminderAiRequest): Call<AddReminderAiResponse>

    @GET("/reminders")
    fun getReminders(): Call<ReminderResponse>

    @GET("/reminders")
    fun getAllReminders(@Query("include") include: String = "all"): Call<ReminderResponse>
}