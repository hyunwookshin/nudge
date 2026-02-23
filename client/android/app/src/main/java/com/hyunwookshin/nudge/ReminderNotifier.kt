package com.hyunwookshin.nudge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object ReminderNotifier {

    const val CHANNEL_ID = "reminders"
    private const val CHANNEL_NAME = "Reminders"
    private const val CHANNEL_DESC = "Reminder notifications"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
        }

        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, r: Reminder, whenLabel: String) {
        ensureChannel(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = "Reminder in $whenLabel: ${r.Title}"
        val body = buildString {
            append(r.Description)
            if (r.Link.isNotBlank()) {
                append("\n")
                append(r.Link)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(title)
            .setContentText(r.Description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(notificationId(r.Id, whenLabel), notification)
    }

    private fun notificationId(reminderId: String, whenLabel: String): Int {
        return reminderId.hashCode() xor whenLabel.hashCode()
    }
}