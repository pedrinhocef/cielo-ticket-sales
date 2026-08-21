package com.pedrosoares.cielosales.core.data.repository

import com.pedrosoares.cielosales.core.data.local.dao.PurchaseDao
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val purchaseDao: PurchaseDao
) {
    suspend fun createOrGetPending(purchase: PurchaseEntity): PendingPurchaseResult {
        val result = purchaseDao.createOrGetPending(purchase)
        return if (result.created) {
            PendingPurchaseResult.Created(result.purchase)
        } else {
            if (result.purchase.paymentStatus == PurchaseStatus.PENDING) {
                PendingPurchaseResult.ExistingPending(result.purchase)
            } else {
                PendingPurchaseResult.ExistingTerminal(result.purchase)
            }
        }
    }

    suspend fun completePending(
        key: String,
        status: PurchaseStatus,
        transactionId: String? = null,
        reason: String? = null
    ): Boolean {
        require(status != PurchaseStatus.PENDING) { "A pending purchase must complete with a terminal status" }
        return purchaseDao.updateStatusIfCurrent(
            key = key,
            currentStatus = PurchaseStatus.PENDING,
            status = status,
            transactionId = transactionId,
            reason = reason
        ) == 1
    }

    suspend fun getPurchase(key: String): PurchaseEntity? {
        return purchaseDao.getPurchaseByIdempotencyKey(key)
    }

    suspend fun resetTechnicalFailureForRetry(key: String): Boolean =
        purchaseDao.updateStatusIfCurrent(
            key = key,
            currentStatus = PurchaseStatus.FAILED_TECHNICAL,
            status = PurchaseStatus.PENDING,
            transactionId = null,
            reason = null
        ) == 1
}
