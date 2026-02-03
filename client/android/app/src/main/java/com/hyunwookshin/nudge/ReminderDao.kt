package com.hyunwookshin.nudge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY time ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ReminderEntity>)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<ReminderEntity>) {
        clearAll()
        upsertAll(items)
    }
}
