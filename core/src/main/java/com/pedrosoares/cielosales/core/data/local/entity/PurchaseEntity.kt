package com.pedrosoares.cielosales.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val idempotencyKey: String,
    val eventId: String,
    val eventName: String,
    val quantity: Int,
    val totalAmountInCents: Long,
    val paymentStatus: String,
    val cieloTransactionId: String?,
    val timestamp: Long = System.currentTimeMillis()
)
