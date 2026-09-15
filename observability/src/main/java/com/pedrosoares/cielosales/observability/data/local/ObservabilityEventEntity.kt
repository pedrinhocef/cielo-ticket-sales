package com.pedrosoares.cielosales.observability.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_events")
data class ObservabilityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val stage: String,
    val severity: String,
    val dimensions: String,
    val errorCode: String? = null,
    val exceptionType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
