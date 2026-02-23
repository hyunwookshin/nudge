package com.hyunwookshin.nudge

import android.content.Context

object FiredStore {

    private const val PREF_NAME = "reminder_fired_store"

    private fun key(reminderId: String, offsetIdx: Int, signature: Int): String {
        return "${reminderId}_${offsetIdx}_$signature"
    }

    fun hasFired(
        context: Context,
        reminderId: String,
        offsetIdx: Int,
        signature: Int
    ): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(key(reminderId, offsetIdx, signature), false)
    }

    fun markFired(
        context: Context,
        reminderId: String,
        offsetIdx: Int,
        signature: Int
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(key(reminderId, offsetIdx, signature), true)
            .apply()
    }

    /**
     * Clears all fired records for this reminder.
     * Used when deleting a reminder.
     */
    fun clear(context: Context, reminderId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val allKeys = prefs.all.keys
        for (k in allKeys) {
            if (k.startsWith("${reminderId}_")) {
                editor.remove(k)
            }
        }

        editor.apply()
    }
}