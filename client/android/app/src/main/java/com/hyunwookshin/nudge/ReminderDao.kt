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

    // Only deletes synced reminders; pending (offline-created) rows are preserved
    // so they survive a cache refresh and can still be uploaded on reconnect.
    @Query("DELETE FROM reminders WHERE isPending = 0")
    suspend fun clearSynced()

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun getById(id: String): ReminderEntity?

    /**
     * Replaces the cached server reminders with a fresh list while preserving any
     * locally-created pending rows (isPending = 1).
     *
     * IMPORTANT: do NOT call clearAll() here. The reconnect flow is:
     *   1. fetchReminders() succeeds → calls replaceAll() to refresh the cache
     *   2. showOffline(false) detects the offline→online transition → calls syncPendingReminders()
     *   3. syncPendingReminders() uploads each pending row then deletes it with deleteById()
     *
     * If clearAll() were used in step 1, the pending rows would be gone before step 3
     * ever runs, silently dropping reminders the user created while offline.
     */
    @Transaction
    suspend fun replaceAll(items: List<ReminderEntity>) {
        clearSynced()   // only wipe isPending=0 rows; pending rows survive until syncPendingReminders deletes them
        upsertAll(items)
    }
}
