package com.pedrosoares.cielosales.observability.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ObservabilityEventEntity::class], version = 1, exportSchema = false)
abstract class ObservabilityDatabase : RoomDatabase() {
    abstract fun eventDao(): ObservabilityEventDao
}
