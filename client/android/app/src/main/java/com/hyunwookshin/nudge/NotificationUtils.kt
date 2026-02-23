package com.hyunwookshin.nudge

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}