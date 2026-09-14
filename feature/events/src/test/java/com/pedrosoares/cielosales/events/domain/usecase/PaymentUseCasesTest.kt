package com.pedrosoares.cielosales.events.domain.usecase

import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import com.pedrosoares.cielosales.core.domain.model.PurchaseConstraints
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentUseCasesTest {
    private val purchaseRepository: PurchaseRepository = mockk(relaxed = true)

    @Test
    fun `When saved reference is unavailable then should restore the single pending database purchase`() = runTest {
        val pendingPurchase = purchase(status = PurchaseStatus.PENDING)
        coEvery { purchaseRepository.getSinglePendingPurchase() } returns pendingPurchase

        val recovered = RecoverPendingPurchaseUseCase(purchaseRepository)(savedIdempotencyKey = null)

        assertEquals(pendingPurchase, recovered)
        coVerify(exactly = 0) { purchaseRepository.getPurchase(any()) }
    }

    @Test
    fun `When retrying technical failure then should reset and reuse the same purchase`() = runTest {
        val failedPurchase = purchase(status = PurchaseStatus.FAILED_TECHNICAL)
        val pendingPurchase = failedPurchase.copy(paymentStatus = PurchaseStatus.PENDING)
        coEvery { purchaseRepository.getPurchase(failedPurchase.idempotencyKey) } returnsMany
            listOf(failedPurchase, pendingPurchase)
        coEvery { purchaseRepository.resetTechnicalFailureForRetry(failedPurchase.idempotencyKey) } returns true

        val retriedPurchase = RetryPaymentUseCase(purchaseRepository)(failedPurchase.idempotencyKey)

        assertEquals(pendingPurchase, retriedPurchase)
        coVerify { purchaseRepository.resetTechnicalFailureForRetry(failedPurchase.idempotencyKey) }
    }

    @Test
    fun `When completing approved payment then should persist approved status and transaction`() = runTest {
        val approvedPurchase = purchase(status = PurchaseStatus.APPROVED).copy(cieloTransactionId = "transaction-1")
        coEvery { purchaseRepository.getPurchase(approvedPurchase.idempotencyKey) } returns approvedPurchase

        val completedPurchase = CompletePaymentUseCase(purchaseRepository)(
            PaymentResult.Success(
                transactionId = "transaction-1",
                idempotencyKey = approvedPurchase.idempotencyKey
            )
        )

        assertEquals(approvedPurchase, completedPurchase)
        coVerify {
            purchaseRepository.completePending(
                approvedPurchase.idempotencyKey,
                PurchaseStatus.APPROVED,
                "transaction-1",
                null
            )
        }
    }

    @Test
    fun `When retrying terminal purchase then should not change its status`() = runTest {
        val approvedPurchase = purchase(status = PurchaseStatus.APPROVED)
        coEvery { purchaseRepository.getPurchase(approvedPurchase.idempotencyKey) } returns approvedPurchase

        val result = RetryPaymentUseCase(purchaseRepository)(approvedPurchase.idempotencyKey)

        assertEquals(approvedPurchase, result)
        coVerify(exactly = 0) { purchaseRepository.resetTechnicalFailureForRetry(any()) }
    }

    private fun purchase(status: PurchaseStatus) = Purchase(
        idempotencyKey = "purchase-1",
        eventId = "event-1",
        eventName = "Show",
        quantity = PurchaseConstraints.MIN_TICKETS_PER_ORDER,
        totalAmountInCents = 10_000,
        paymentStatus = status,
        cieloTransactionId = null
    )
}
