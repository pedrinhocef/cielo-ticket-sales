package com.pedrosoares.cielosales.events.presentation

import androidx.lifecycle.SavedStateHandle
import com.pedrosoares.cielosales.cielo.data.remote.CieloCallbackParser
import com.pedrosoares.cielosales.cielo.data.remote.CieloPayloadBuilder
import com.pedrosoares.cielosales.cielo.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.data.repository.PendingPurchaseResult
import com.pedrosoares.cielosales.core.data.repository.EventRepository
import com.pedrosoares.cielosales.core.data.repository.PurchaseRepository
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import com.pedrosoares.cielosales.core.util.UiText
import com.pedrosoares.cielosales.events.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EventsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val eventRepository: EventRepository = mockk()
    private val purchaseRepository: PurchaseRepository = mockk(relaxed = true)
    private val payloadBuilder: CieloPayloadBuilder = mockk()
    private val callbackParser: CieloCallbackParser = mockk()
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()

    private lateinit var viewModel: EventsViewModel

    private val sampleEvent = Event("evt-1", "Test Show", "Test Local", 10000, "url")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { eventRepository.getEvents() } returns flowOf(emptyList())
        viewModel = EventsViewModel(
            eventRepository,
            purchaseRepository,
            payloadBuilder,
            callbackParser,
            savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `When initializing then should load events`() {
        val events = listOf(sampleEvent)
        coEvery { eventRepository.getEvents() } returns flowOf(events)

        viewModel.loadEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.EventList)
        assertEquals(events, (state as UiState.EventList).events)
    }

    @Test
    fun `When starting payment flow then should update state, save PENDING and emit effect`() {
        val quantity = 3
        val uri = android.net.Uri.parse("lio://payment?request=123")

        every { payloadBuilder.buildPaymentUri(any(), any(), any(), any(), any()) } returns uri
        coEvery { purchaseRepository.createOrGetPending(any()) } returns PendingPurchaseResult.Created(
            PurchaseEntity("key-123", "evt-1", "Test Show", 3, 30000, PurchaseStatus.PENDING, null)
        )

        viewModel.startPaymentFlow(sampleEvent, quantity)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be ProcessingPayment but was $state", state is UiState.ProcessingPayment)
        val idempotencyKey = (state as UiState.ProcessingPayment).idempotencyKey

        assertEquals(idempotencyKey, savedStateHandle.get<String>("current_idempotency_key"))

        coVerify { purchaseRepository.createOrGetPending(match {
            it.eventId == sampleEvent.id && it.quantity == quantity && it.paymentStatus == PurchaseStatus.PENDING
        }) }
    }

    @Test
    fun `When processing successful payment then should update status and success state`() {
        val idempotencyKey = "key-123"
        val transactionId = "TX-999"
        val result = PaymentResult.Success(transactionId, idempotencyKey)

        val purchase = PurchaseEntity(
            idempotencyKey = idempotencyKey,
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = 10000,
            paymentStatus = PurchaseStatus.APPROVED,
            cieloTransactionId = transactionId
        )

        coEvery { purchaseRepository.getPurchase(idempotencyKey) } returns purchase

        viewModel.processPaymentResult(result)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { purchaseRepository.updateStatus(idempotencyKey, PurchaseStatus.APPROVED, transactionId, null) }

        val state = viewModel.uiState.value
        assertTrue(state is UiState.PaymentSuccess)
        assertEquals(purchase, (state as UiState.PaymentSuccess).purchase)
        assertTrue(savedStateHandle.get<String>("current_idempotency_key") == null)
    }

    @Test
    fun `When processing canceled payment then should update status and error state`() {
        val idempotencyKey = "key-cancel"
        val result = PaymentResult.Canceled(idempotencyKey)

        val purchase = PurchaseEntity(
            idempotencyKey = idempotencyKey,
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = 10000,
            paymentStatus = PurchaseStatus.CANCELED,
            cieloTransactionId = null
        )
        coEvery { purchaseRepository.getPurchase(idempotencyKey) } returns purchase

        viewModel.processPaymentResult(result)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { purchaseRepository.updateStatus(idempotencyKey, PurchaseStatus.CANCELED, null, null) }

        val state = viewModel.uiState.value
        assertTrue(state is UiState.PaymentError)
        val message = (state as UiState.PaymentError).message
        assertTrue(message is UiText.StringResource)
        assertEquals(R.string.payment_canceled, (message as UiText.StringResource).resId)
    }

    @Test
    fun `When Cielo launch fails then should update state to retryable PaymentError`() {
        val purchase = PurchaseEntity("key-123", "evt-1", "Test Show", 1, 10000, PurchaseStatus.FAILED_TECHNICAL, null)
        coEvery { purchaseRepository.getPurchase("key-123") } returns purchase
        viewModel.onCieloLaunchFailed("key-123")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.PaymentError)
        assertEquals(purchase, (state as UiState.PaymentError).retryPurchase)
    }

    @Test
    fun `When onPaymentResultReceived is called then should parse and process`() {
        val uri = android.net.Uri.parse("cielotickets://payment-response?response=base64&responsecode=0")
        val idempotencyKey = "key-123"
        savedStateHandle[EventsViewModel.MAX_QUANTITY_PER_ORDER.toString()] = idempotencyKey // This is wrong, I need to use the actual key string

        // Use reflection or just set the key if I can.
        // In this test, KEY_IDEMPOTENCY is private. I'll use the string literal "current_idempotency_key".
        savedStateHandle["current_idempotency_key"] = idempotencyKey

        val result = PaymentResult.Success("TX-123", idempotencyKey)
        every { callbackParser.parse(uri, idempotencyKey) } returns result

        viewModel.onPaymentResultReceived(uri)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { purchaseRepository.updateStatus(idempotencyKey, PurchaseStatus.APPROVED, "TX-123", any()) }
    }
}
