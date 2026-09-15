package com.pedrosoares.cielosales.purchases.presentation

import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.api.ObservabilityEventName
import io.mockk.verify

@OptIn(ExperimentalCoroutinesApi::class)
class PurchasesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PurchaseRepository>()
    private val observability = mockk<Observability>(relaxed = true)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `When approved filter is selected then only approved purchases are exposed`() = runTest {
        val approved = purchase("approved", PurchaseStatus.APPROVED)
        val canceled = purchase("canceled", PurchaseStatus.CANCELED)
        every { repository.observePurchases() } returns flowOf(listOf(approved, canceled))

        val viewModel = PurchasesViewModel(repository, observability)
        val collection = backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.selectFilter(PurchaseFilter.APPROVED)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PurchaseFilter.APPROVED, viewModel.uiState.value.filter)
        assertEquals(listOf(approved), viewModel.uiState.value.purchases)
        verify {
            observability.track(match { it.name == ObservabilityEventName.PURCHASE_FILTER_SELECTED })
        }
        collection.cancel()
    }

    @Test
    fun `When approved ticket QR is opened then should track the interaction`() {
        every { repository.observePurchases() } returns flowOf(emptyList())
        val viewModel = PurchasesViewModel(repository, observability)

        viewModel.onTicketQrOpened()

        verify {
            observability.track(match { it.name == ObservabilityEventName.TICKET_QR_OPENED })
        }
    }

    private fun purchase(key: String, status: PurchaseStatus) = Purchase(
        idempotencyKey = key,
        eventId = "event",
        eventName = "Event",
        quantity = 1,
        totalAmountInCents = 1000,
        paymentStatus = status,
        cieloTransactionId = null
    )
}
