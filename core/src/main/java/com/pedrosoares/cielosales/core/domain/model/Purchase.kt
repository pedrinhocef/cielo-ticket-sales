package com.pedrosoares.cielosales.core.domain.model

data class Purchase(
    val idempotencyKey: String,
    val eventId: String,
    val eventName: String,
    val quantity: Int,
    val totalAmountInCents: Long,
    val paymentStatus: PurchaseStatus,
    val cieloTransactionId: String?,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
