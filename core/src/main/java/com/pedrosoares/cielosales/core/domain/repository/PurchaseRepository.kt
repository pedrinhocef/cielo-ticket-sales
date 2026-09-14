package com.pedrosoares.cielosales.core.domain.repository

import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import kotlinx.coroutines.flow.Flow

interface PurchaseRepository {
    suspend fun createOrGetPending(purchase: Purchase): PendingPurchaseResult
    suspend fun completePending(
        key: String,
        status: PurchaseStatus,
        transactionId: String? = null,
        reason: String? = null
    ): Boolean
    suspend fun getPurchase(key: String): Purchase?
    suspend fun getSinglePendingPurchase(): Purchase?
    fun observePurchases(): Flow<List<Purchase>>
    suspend fun resetTechnicalFailureForRetry(key: String): Boolean
}

sealed interface PendingPurchaseResult {
    data class Created(val purchase: Purchase) : PendingPurchaseResult
    data class ExistingPending(val purchase: Purchase) : PendingPurchaseResult
    data class ExistingTerminal(val purchase: Purchase) : PendingPurchaseResult
}
