package com.hyunwookshin.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER_NOTIFY) return

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val offsetIdx = intent.getIntExtra(EXTRA_OFFSET_IDX, -1)
        val signature = intent.getIntExtra(EXTRA_SIGNATURE, 0)
        if (offsetIdx !in 0..1) return
        if (signature == 0) return

        Log.d("ReminderAlarmReceiver", "Received alarm for $reminderId offset=$offsetIdx")

        // Prevent repeats
        if (FiredStore.hasFired(context, reminderId, offsetIdx, signature)) return


        Log.d("ReminderAlarmReceiver", "Received fresh alarm for $reminderId offset=$offsetIdx")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // You already have AppDb caching reminders offline.
                val db = AppDb.get(context)
                Log.d("ReminderNotifier", "accessing reminder for ${reminderId}")

                val entity = db.reminderDao().getById(reminderId)
                val r = entity?.toDomain()

                if (r != null) {
                    val whenLabel = when (offsetIdx) {
                        0 -> "24 hours"
                        1 -> "3 hours"
                        else -> "soon"
                    }

                    // Mark fired BEFORE notifying to be extra safe against rapid duplicates
                    FiredStore.markFired(context, reminderId, offsetIdx, signature)
                    Log.d("ReminderNotifier", "Showing notification for ${r.Id}")
                    ReminderNotifier.show(context, r, whenLabel)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OFFSET_IDX = "offset_idx"
        const val EXTRA_SIGNATURE = "signature"
    }
}