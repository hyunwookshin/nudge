package com.hyunwookshin.nudge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE isPending = 1")
    suspend fun getPending(): List<TemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TemplateEntity)

    // Only deletes synced templates; pending (offline-created) rows are kept until uploaded.
    @Query("DELETE FROM templates WHERE isPending = 0")
    suspend fun clearSynced()

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Refreshes the cache from the server, keeping pending rows (same rule as ReminderDao.replaceAll). */
    @Transaction
    suspend fun replaceAll(items: List<TemplateEntity>) {
        clearSynced()
        upsertAll(items)
    }
}
