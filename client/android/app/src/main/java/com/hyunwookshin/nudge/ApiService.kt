package com.hyunwookshin.nudge

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Query

data class LoginRequest(val Username: String, val Password: String)
data class LoginResponse(val token: String, val username: String)

data class SignupRequest(
    val Username: String,
    val Password: String,
    val PasswordConfirm: String
)

data class ChangePasswordRequest(
    val CurrentPassword: String,
    val NewPassword: String,
    val ConfirmPassword: String
)

data class MessageResponse(val message: String)

interface ApiService {
    @POST("/login")
    fun login(@Body req: LoginRequest): Call<LoginResponse>

    @POST("/signup")
    fun signup(@Body req: SignupRequest): Call<LoginResponse>

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

    @POST("/change_password")
    fun changePassword(@Body req: ChangePasswordRequest): Call<MessageResponse>
}