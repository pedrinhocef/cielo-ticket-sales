package com.pedrosoares.cielosales.core.data.repository

import com.pedrosoares.cielosales.core.data.local.dao.PurchaseDao
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import javax.inject.Inject
import com.pedrosoares.cielosales.core.domain.repository.PendingPurchaseResult
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomPurchaseRepository @Inject constructor(
    private val purchaseDao: PurchaseDao
) : PurchaseRepository {
    private companion object {
        /** Two rows let us distinguish one recoverable purchase from an ambiguous history. */
        const val PENDING_PURCHASE_AMBIGUITY_LIMIT = 2
    }
    override suspend fun createOrGetPending(purchase: Purchase): PendingPurchaseResult {
        val result = purchaseDao.createOrGetPending(purchase.toEntity())
        return if (result.created) {
            PendingPurchaseResult.Created(result.purchase.toDomain())
        } else {
            if (result.purchase.paymentStatus == PurchaseStatus.PENDING) {
                PendingPurchaseResult.ExistingPending(result.purchase.toDomain())
            } else {
                PendingPurchaseResult.ExistingTerminal(result.purchase.toDomain())
            }
        }
    }

    override suspend fun completePending(
        key: String,
        status: PurchaseStatus,
        transactionId: String?,
        reason: String?
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

    override suspend fun getPurchase(key: String): Purchase? {
        return purchaseDao.getPurchaseByIdempotencyKey(key)?.toDomain()
    }

    override suspend fun getSinglePendingPurchase(): Purchase? {
        val pending = purchaseDao.getPurchasesByStatus(
            status = PurchaseStatus.PENDING,
            limit = PENDING_PURCHASE_AMBIGUITY_LIMIT
        )
        return pending.singleOrNull()?.toDomain()
    }

    override fun observePurchases(): Flow<List<Purchase>> = purchaseDao.getAllPurchases().map { purchases ->
        purchases.map(PurchaseEntity::toDomain)
    }

    override suspend fun resetTechnicalFailureForRetry(key: String): Boolean =
        purchaseDao.updateStatusIfCurrent(
            key = key,
            currentStatus = PurchaseStatus.FAILED_TECHNICAL,
            status = PurchaseStatus.PENDING,
            transactionId = null,
            reason = null
        ) == 1
}

private fun Purchase.toEntity() = PurchaseEntity(
    idempotencyKey = idempotencyKey,
    eventId = eventId,
    eventName = eventName,
    quantity = quantity,
    totalAmountInCents = totalAmountInCents,
    paymentStatus = paymentStatus,
    cieloTransactionId = cieloTransactionId,
    reason = reason,
    timestamp = timestamp
)

private fun PurchaseEntity.toDomain() = Purchase(
    idempotencyKey = idempotencyKey,
    eventId = eventId,
    eventName = eventName,
    quantity = quantity,
    totalAmountInCents = totalAmountInCents,
    paymentStatus = paymentStatus,
    cieloTransactionId = cieloTransactionId,
    reason = reason,
    timestamp = timestamp
)
