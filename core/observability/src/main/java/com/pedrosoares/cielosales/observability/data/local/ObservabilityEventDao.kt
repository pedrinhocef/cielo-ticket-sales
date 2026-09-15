package com.pedrosoares.cielosales.observability.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ObservabilityEventDao {
    @Insert
    suspend fun insert(event: ObservabilityEventEntity)

    @Query("DELETE FROM diagnostic_events WHERE id NOT IN (SELECT id FROM diagnostic_events ORDER BY id DESC LIMIT :maximumEntries)")
    suspend fun trimTo(maximumEntries: Int)

    @Query("SELECT * FROM diagnostic_events ORDER BY id DESC")
    suspend fun getAll(): List<ObservabilityEventEntity>

    @Transaction
    suspend fun insertAndTrim(event: ObservabilityEventEntity, maximumEntries: Int) {
        insert(event)
        trimTo(maximumEntries)
    }
}
