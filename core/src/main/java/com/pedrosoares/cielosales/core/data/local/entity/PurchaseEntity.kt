package com.pedrosoares.cielosales.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val idempotencyKey: String,
    val eventId: String,
    val eventName: String,
    val quantity: Int,
    val totalAmountInCents: Long,
    val paymentStatus: PurchaseStatus,
    val cieloTransactionId: String?,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
