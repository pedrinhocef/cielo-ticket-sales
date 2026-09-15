package com.pedrosoares.cielosales.events.domain.usecase

import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.model.PurchaseConstraints
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import com.pedrosoares.cielosales.core.domain.repository.PendingPurchaseResult
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import java.util.UUID
import javax.inject.Inject
import com.pedrosoares.cielosales.observability.api.NoOpObservability
import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.api.ObservabilityDimension
import com.pedrosoares.cielosales.observability.api.ObservabilityEventName
import com.pedrosoares.cielosales.observability.api.ObservabilityStage
import com.pedrosoares.cielosales.observability.api.track

class PaymentUseCases @Inject constructor(
    val recoverPendingPurchase: RecoverPendingPurchaseUseCase,
    val startPayment: StartPaymentUseCase,
    val retryPayment: RetryPaymentUseCase,
    val completePayment: CompletePaymentUseCase,
    val registerLaunchFailure: RegisterCieloLaunchFailureUseCase,
    val findPendingPurchase: FindPendingPurchaseUseCase
)

class RecoverPendingPurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val observability: Observability = NoOpObservability
) {
    suspend operator fun invoke(savedIdempotencyKey: String?): Purchase? {
        val savedPurchase = savedIdempotencyKey
            ?.takeIf(String::isNotBlank)
            ?.let { purchaseRepository.getPurchase(it) }
        if (savedPurchase != null) {
            observability.track(
                ObservabilityEventName.PAYMENT_RECOVERED,
                ObservabilityStage.RECOVERY,
                ObservabilityDimension.RECOVERY_SOURCE to "SAVED_STATE",
                ObservabilityDimension.STATUS to savedPurchase.paymentStatus.name
            )
            return savedPurchase
        }
        return purchaseRepository.getSinglePendingPurchase()
    }
}

class StartPaymentUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val observability: Observability = NoOpObservability
) {
    suspend operator fun invoke(event: Event, quantity: Int): PendingPurchaseResult {
        val validQuantity = quantity.coerceIn(
            PurchaseConstraints.MIN_TICKETS_PER_ORDER,
            PurchaseConstraints.MAX_TICKETS_PER_ORDER
        )
        observability.track(
            ObservabilityEventName.PAYMENT_START_REQUESTED,
            ObservabilityStage.PAYMENT_START,
            ObservabilityDimension.QUANTITY to validQuantity.toString()
        )
        return purchaseRepository.createOrGetPending(
            Purchase(
                idempotencyKey = UUID.randomUUID().toString(),
                eventId = event.id,
                eventName = event.title,
                quantity = validQuantity,
                totalAmountInCents = Math.multiplyExact(event.priceInCents, validQuantity.toLong()),
                paymentStatus = PurchaseStatus.PENDING,
                cieloTransactionId = null
            )
        )
    }
}

class RetryPaymentUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val observability: Observability = NoOpObservability
) {
    suspend operator fun invoke(idempotencyKey: String): Purchase? {
        val stored = purchaseRepository.getPurchase(idempotencyKey) ?: return null
        observability.track(
            ObservabilityEventName.PAYMENT_RETRY_REQUESTED,
            ObservabilityStage.RETRY,
            ObservabilityDimension.STATUS to stored.paymentStatus.name
        )
        val result = when (stored.paymentStatus) {
            PurchaseStatus.PENDING -> stored
            PurchaseStatus.FAILED_TECHNICAL -> {
                if (!purchaseRepository.resetTechnicalFailureForRetry(stored.idempotencyKey)) null
                else purchaseRepository.getPurchase(stored.idempotencyKey)
            }
            else -> stored
        }
        observability.track(
            ObservabilityEventName.PAYMENT_RETRY_COMPLETED,
            ObservabilityStage.RETRY,
            ObservabilityDimension.RESULT to (result?.paymentStatus?.name ?: "NOT_FOUND")
        )
        return result
    }
}

class CompletePaymentUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) {
    suspend operator fun invoke(result: PaymentResult): Purchase? {
        purchaseRepository.completePending(
            key = result.idempotencyKey,
            status = result.toPurchaseStatus(),
            transactionId = (result as? PaymentResult.Success)?.transactionId,
            reason = result.failureReason()
        )
        return purchaseRepository.getPurchase(result.idempotencyKey)
    }

    private fun PaymentResult.toPurchaseStatus() = when (this) {
        is PaymentResult.Success -> PurchaseStatus.APPROVED
        is PaymentResult.Denied -> PurchaseStatus.DENIED
        is PaymentResult.Canceled -> PurchaseStatus.CANCELED
        is PaymentResult.FailedTechnical, is PaymentResult.Error -> PurchaseStatus.FAILED_TECHNICAL
    }

    private fun PaymentResult.failureReason(): String? = when (this) {
        is PaymentResult.Denied -> reason
        is PaymentResult.FailedTechnical -> message
        is PaymentResult.Error -> message
        else -> null
    }
}

class RegisterCieloLaunchFailureUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) {
    suspend operator fun invoke(idempotencyKey: String): Purchase? {
        purchaseRepository.completePending(
            key = idempotencyKey,
            status = PurchaseStatus.FAILED_TECHNICAL,
            reason = CIELO_APP_NOT_FOUND_REASON
        )
        return purchaseRepository.getPurchase(idempotencyKey)
    }

    private companion object {
        const val CIELO_APP_NOT_FOUND_REASON = "Cielo payment application was not found"
    }
}

class FindPendingPurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) {
    suspend operator fun invoke(idempotencyKey: String): Purchase? =
        purchaseRepository.getPurchase(idempotencyKey)?.takeIf { it.paymentStatus == PurchaseStatus.PENDING }
}
