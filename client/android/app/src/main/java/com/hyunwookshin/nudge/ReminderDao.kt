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

    @Query("SELECT * FROM reminders WHERE isPending = 1 ORDER BY time ASC")
    suspend fun getPending(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReminderEntity)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun getById(id: String): ReminderEntity?

    @Transaction
    suspend fun replaceAll(items: List<ReminderEntity>) {
        clearAll()
        upsertAll(items)
    }
}
