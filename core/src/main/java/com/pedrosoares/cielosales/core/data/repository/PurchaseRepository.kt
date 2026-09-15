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
import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.api.ObservabilityDimension
import com.pedrosoares.cielosales.observability.api.ObservabilityEventName
import com.pedrosoares.cielosales.observability.api.ObservabilityErrorCode
import com.pedrosoares.cielosales.observability.api.ObservabilityStage
import com.pedrosoares.cielosales.observability.api.record
import com.pedrosoares.cielosales.observability.api.track

@Singleton
class RoomPurchaseRepository @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val observability: Observability
) : PurchaseRepository {
    private companion object {
        /** Two rows let us distinguish one recoverable purchase from an ambiguous history. */
        const val PENDING_PURCHASE_AMBIGUITY_LIMIT = 2
    }
    override suspend fun createOrGetPending(purchase: Purchase): PendingPurchaseResult {
        val result = purchaseDao.createOrGetPending(purchase.toEntity())
        observability.track(
            ObservabilityEventName.PURCHASE_PERSISTED,
            ObservabilityStage.PERSISTENCE,
            ObservabilityDimension.RESULT to if (result.created) "CREATED" else "EXISTING",
            ObservabilityDimension.STATUS to result.purchase.paymentStatus.name
        )
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
        val updated = purchaseDao.updateStatusIfCurrent(
            key = key,
            currentStatus = PurchaseStatus.PENDING,
            status = status,
            transactionId = transactionId,
            reason = reason
        ) == 1
        if (updated) {
            observability.track(
                ObservabilityEventName.PAYMENT_STATE_TRANSITION,
                ObservabilityStage.PERSISTENCE,
                ObservabilityDimension.PREVIOUS_STATUS to PurchaseStatus.PENDING.name,
                ObservabilityDimension.STATUS to status.name
            )
        } else {
            val stored = purchaseDao.getPurchaseByIdempotencyKey(key)
            if (stored == null) {
                observability.record(
                    ObservabilityErrorCode.PURCHASE_NOT_FOUND,
                    ObservabilityStage.PERSISTENCE
                )
            } else {
                observability.track(
                    ObservabilityEventName.LATE_CALLBACK_IGNORED,
                    ObservabilityStage.PERSISTENCE,
                    ObservabilityDimension.STATUS to stored.paymentStatus.name,
                    ObservabilityDimension.RESULT to status.name
                )
            }
        }
        return updated
    }

    override suspend fun getPurchase(key: String): Purchase? {
        return purchaseDao.getPurchaseByIdempotencyKey(key)?.toDomain()
    }

    override suspend fun getSinglePendingPurchase(): Purchase? {
        val pending = purchaseDao.getPurchasesByStatus(
            status = PurchaseStatus.PENDING,
            limit = PENDING_PURCHASE_AMBIGUITY_LIMIT
        )
        observability.track(
            ObservabilityEventName.PAYMENT_RECOVERY_EVALUATED,
            ObservabilityStage.RECOVERY,
            ObservabilityDimension.RECOVERY_SOURCE to when (pending.size) {
                0 -> "DATABASE_NONE"
                1 -> "DATABASE"
                else -> "DATABASE_AMBIGUOUS"
            }
        )
        return pending.singleOrNull()?.toDomain()
    }

    override fun observePurchases(): Flow<List<Purchase>> = purchaseDao.getAllPurchases().map { purchases ->
        purchases.map(PurchaseEntity::toDomain)
    }

    override suspend fun resetTechnicalFailureForRetry(key: String): Boolean {
        val updated = purchaseDao.updateStatusIfCurrent(
            key = key,
            currentStatus = PurchaseStatus.FAILED_TECHNICAL,
            status = PurchaseStatus.PENDING,
            transactionId = null,
            reason = null
        ) == 1
        if (updated) {
            observability.track(
                ObservabilityEventName.PAYMENT_STATE_TRANSITION,
                ObservabilityStage.RETRY,
                ObservabilityDimension.PREVIOUS_STATUS to PurchaseStatus.FAILED_TECHNICAL.name,
                ObservabilityDimension.STATUS to PurchaseStatus.PENDING.name
            )
        }
        return updated
    }
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
