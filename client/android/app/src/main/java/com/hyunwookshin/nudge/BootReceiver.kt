package com.hyunwookshin.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDb.get(context)
                val reminders = db.reminderDao().getAll().map { it.toDomain() }
                ReminderScheduler.scheduleAll(context, reminders)
            } finally {
                pending.finish()
            }
        }
    }
}