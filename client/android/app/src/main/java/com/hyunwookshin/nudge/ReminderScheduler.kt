package com.hyunwookshin.nudge

import android.app.AlarmManager
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReminderScheduler {

    const val ACTION_REMINDER_NOTIFY = "com.hyunwookshin.nudge.REMINDER_NOTIFY"

    private val OFFSETS = listOf(
        java.time.Duration.ofHours(24),
        java.time.Duration.ofHours(3)
    )

    fun scheduleAll(context: Context, reminders: List<Reminder>) {
        reminders.forEach { reminder ->
            scheduleReminder(context, reminder)
        }
    }
    private fun parseReminderDateTime(r: Reminder): LocalDateTime? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            LocalDateTime.parse(r.Time, formatter)
        } catch (e: Exception) {
            null
        }
    }

    fun scheduleReminder(context: Context, reminder: Reminder) {

        val reminderDateTime = parseReminderDateTime(reminder)
        val now = LocalDateTime.now()

        if (reminderDateTime == null) return

        OFFSETS.forEachIndexed { index, offset ->

            val triggerTime = reminderDateTime.minus(offset)

            val signature = computeSignature(reminder)

            // Skip if reminder already past
            if (reminderDateTime.isBefore(now)) return@forEachIndexed

            // Already fired?
            if (FiredStore.hasFired(context, reminder.Id, index, signature)) {
                return@forEachIndexed
            }

            if (triggerTime.isAfter(now)) {
                scheduleExact(context, reminder.Id, triggerTime, index, signature)
            } else {
                Log.d("ReminderScheduler", "Skipping past offset index=$index for ${reminder.Id}")
            }
        }
    }

    private fun scheduleExact(
        context: Context,
        reminderId: String,
        triggerTime: LocalDateTime,
        offsetIdx: Int,
        signature: Int
    ) {

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }
        Log.d("AlarmCheck", "Can schedule exact: ${alarmManager.canScheduleExactAlarms()}")
        Log.d("ReminderScheduler", "Scheduling ${reminderId} at $triggerTime")

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_NOTIFY
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.EXTRA_OFFSET_IDX, offsetIdx)
            putExtra(ReminderAlarmReceiver.EXTRA_SIGNATURE, signature)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + offsetIdx).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerMillis = triggerTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent
        )
    }

    private fun fireNow(
        context: Context,
        reminderId: String,
        offsetIdx: Int,
        signature: Int
    ) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_NOTIFY
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.EXTRA_OFFSET_IDX, offsetIdx)
            putExtra(ReminderAlarmReceiver.EXTRA_SIGNATURE, signature)
        }

        context.sendBroadcast(intent)
    }

    private fun computeSignature(reminder: Reminder): Int {
        return (reminder.Date + reminder.Time).hashCode()
    }

    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        OFFSETS.indices.forEach { index ->
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_REMINDER_NOTIFY
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId + index).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
        }

        FiredStore.clear(context, reminderId)
    }
}