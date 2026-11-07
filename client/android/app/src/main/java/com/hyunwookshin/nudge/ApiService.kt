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

data class SetProjectRequest(val Id: String, val ParentId: String)

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
    fun getReminders(
        @Query("include") include: Int? = null,
        @Query("kind") kind: String? = null,   // "project": every project, for the Projects page
        @Query("parent") parent: String? = null,   // a project's id: that project and all its events, past too
    ): Call<ReminderResponse>

    /** Adds an event to a project (empty ParentId removes it); the server checks the date range. */
    @POST("/set_project")
    fun setProject(@Body req: SetProjectRequest): Call<MessageResponse>

    @GET("/templates")
    fun getTemplates(): Call<TemplateResponse>

    @POST("/add_template")
    fun addTemplate(@Body template: Template): Call<Void>

    @POST("/delete_template")
    fun deleteTemplate(@Body template: Template): Call<Void>

    @POST("/change_password")
    fun changePassword(@Body req: ChangePasswordRequest): Call<MessageResponse>

    @POST("/delete_account")
    fun deleteAccount(): Call<MessageResponse>
}