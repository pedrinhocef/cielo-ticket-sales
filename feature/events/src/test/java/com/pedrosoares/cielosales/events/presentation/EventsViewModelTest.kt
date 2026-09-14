package com.pedrosoares.cielosales.events.presentation

import androidx.lifecycle.SavedStateHandle
import com.pedrosoares.cielosales.core.domain.gateway.PaymentGateway
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.repository.PendingPurchaseResult
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import com.pedrosoares.cielosales.core.domain.repository.EventRepository
import com.pedrosoares.cielosales.core.util.UiText
import com.pedrosoares.cielosales.events.domain.usecase.BuildCieloPaymentUriUseCase
import com.pedrosoares.cielosales.events.domain.usecase.CompletePaymentUseCase
import com.pedrosoares.cielosales.events.domain.usecase.FindPendingPurchaseUseCase
import com.pedrosoares.cielosales.events.domain.usecase.ObserveEventsUseCase
import com.pedrosoares.cielosales.events.domain.usecase.ParseCieloCallbackUseCase
import com.pedrosoares.cielosales.events.domain.usecase.PaymentUseCases
import com.pedrosoares.cielosales.events.domain.usecase.RecoverPendingPurchaseUseCase
import com.pedrosoares.cielosales.events.domain.usecase.RegisterCieloLaunchFailureUseCase
import com.pedrosoares.cielosales.events.domain.usecase.RetryPaymentUseCase
import com.pedrosoares.cielosales.events.domain.usecase.StartPaymentUseCase
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
    private val paymentGateway: PaymentGateway = mockk()
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
    private val observeEventsUseCase = ObserveEventsUseCase(eventRepository)
    private val paymentUseCases = PaymentUseCases(
        RecoverPendingPurchaseUseCase(purchaseRepository),
        StartPaymentUseCase(purchaseRepository),
        RetryPaymentUseCase(purchaseRepository),
        CompletePaymentUseCase(purchaseRepository),
        RegisterCieloLaunchFailureUseCase(purchaseRepository),
        FindPendingPurchaseUseCase(purchaseRepository)
    )
    private val buildCieloPaymentUriUseCase = BuildCieloPaymentUriUseCase(paymentGateway)
    private val parseCieloCallbackUseCase = ParseCieloCallbackUseCase(paymentGateway)

    private lateinit var viewModel: EventsViewModel

    private val sampleEvent = Event("evt-1", "Test Show", "Test Local", 10000, "url")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { eventRepository.observeEvents() } returns flowOf(emptyList())
        coEvery { purchaseRepository.getSinglePendingPurchase() } returns null
        viewModel = EventsViewModel(
            observeEventsUseCase,
            paymentUseCases,
            buildCieloPaymentUriUseCase,
            parseCieloCallbackUseCase,
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
        every { eventRepository.observeEvents() } returns flowOf(events)

        viewModel.loadEvents()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.EventList)
        assertEquals(events, (state as UiState.EventList).events)
    }

    @Test
    fun `When starting payment flow then should keep the event context, save PENDING and emit effect`() {
        val quantity = 3
        val uri = android.net.Uri.parse("lio://payment?request=123")

        every { paymentGateway.buildPaymentUri(any()) } returns uri
        coEvery { purchaseRepository.createOrGetPending(any()) } returns PendingPurchaseResult.Created(
            Purchase("key-123", "evt-1", "Test Show", 3, 30000, PurchaseStatus.PENDING, null)
        )

        viewModel.startPaymentFlow(sampleEvent, quantity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.EventList)
        assertTrue(viewModel.isPaymentLaunchInProgress.value)
        assertEquals("key-123", savedStateHandle.get<String>("current_idempotency_key"))

        coVerify { purchaseRepository.createOrGetPending(match {
            it.eventId == sampleEvent.id && it.quantity == quantity && it.paymentStatus == PurchaseStatus.PENDING
        }) }
    }

    @Test
    fun `When payment is started twice then should create and launch only once`() {
        val uri = android.net.Uri.parse("lio://payment?request=123")
        val purchase = Purchase("key-once", "evt-1", "Test Show", 1, 10000, PurchaseStatus.PENDING, null)
        every { paymentGateway.buildPaymentUri(any()) } returns uri
        coEvery { purchaseRepository.createOrGetPending(any()) } returns PendingPurchaseResult.Created(purchase)

        viewModel.startPaymentFlow(sampleEvent, 1)
        viewModel.startPaymentFlow(sampleEvent, 1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { purchaseRepository.createOrGetPending(any()) }
        assertTrue(viewModel.isPaymentLaunchInProgress.value)
    }

    @Test
    fun `When a persisted payment is pending then should show it instead of creating a new purchase`() {
        val pendingPurchase = Purchase(
            idempotencyKey = "pending-key",
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = sampleEvent.priceInCents,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )
        savedStateHandle["current_idempotency_key"] = pendingPurchase.idempotencyKey
        coEvery { purchaseRepository.getPurchase(pendingPurchase.idempotencyKey) } returns pendingPurchase

        viewModel.startPaymentFlow(sampleEvent, quantity = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.PaymentPending(pendingPurchase), viewModel.uiState.value)
        coVerify(exactly = 0) { purchaseRepository.createOrGetPending(any()) }
    }

    @Test
    fun `When ViewModel is recreated with pending key then should restore pending purchase`() {
        val pendingPurchase = Purchase(
            idempotencyKey = "restored-key",
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 2,
            totalAmountInCents = 20000,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )
        val restoredState = SavedStateHandle(mapOf("current_idempotency_key" to pendingPurchase.idempotencyKey))
        coEvery { purchaseRepository.getPurchase(pendingPurchase.idempotencyKey) } returns pendingPurchase

        val restoredViewModel = EventsViewModel(
            observeEventsUseCase,
            paymentUseCases,
            buildCieloPaymentUriUseCase,
            parseCieloCallbackUseCase,
            restoredState
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.PaymentPending(pendingPurchase), restoredViewModel.uiState.value)
    }

    @Test
    fun `When ViewModel is recreated without saved state then should restore the single pending database purchase`() {
        val pendingPurchase = Purchase(
            idempotencyKey = "database-pending-key",
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = sampleEvent.priceInCents,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )
        coEvery { purchaseRepository.getSinglePendingPurchase() } returns pendingPurchase

        val restoredViewModel = EventsViewModel(
            observeEventsUseCase,
            paymentUseCases,
            buildCieloPaymentUriUseCase,
            parseCieloCallbackUseCase,
            SavedStateHandle()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.PaymentPending(pendingPurchase), restoredViewModel.uiState.value)
    }

    @Test
    fun `When Cielo starts then should keep the loading overlay until the host app resumes`() {
        val purchase = Purchase(
            idempotencyKey = "pending-key",
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = sampleEvent.priceInCents,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )
        coEvery { purchaseRepository.getPurchase(purchase.idempotencyKey) } returns purchase
        coEvery { purchaseRepository.createOrGetPending(any()) } returns PendingPurchaseResult.Created(purchase)
        every { paymentGateway.buildPaymentUri(any()) } returns android.net.Uri.parse("lio://payment?request=pending")

        viewModel.startPaymentFlow(sampleEvent, quantity = 1)
        viewModel.onCieloPaymentLaunched(purchase.idempotencyKey)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isPaymentLaunchInProgress.value)
    }

    @Test
    fun `When host resumes without a callback then should show the pending purchase actions`() {
        val purchase = Purchase(
            idempotencyKey = "pending-key",
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = sampleEvent.priceInCents,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )
        coEvery { purchaseRepository.getPurchase(purchase.idempotencyKey) } returns purchase
        coEvery { purchaseRepository.createOrGetPending(any()) } returns PendingPurchaseResult.Created(purchase)
        every { paymentGateway.buildPaymentUri(any()) } returns android.net.Uri.parse("lio://payment?request=pending")

        viewModel.startPaymentFlow(sampleEvent, quantity = 1)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onCieloPaymentLaunched(purchase.idempotencyKey)
        viewModel.onHostResumed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.PaymentPending(purchase), viewModel.uiState.value)
        assertTrue(!viewModel.isPaymentLaunchInProgress.value)
    }

    @Test
    fun `When retrying technical failure then should reuse the same idempotency key`() {
        val failedPurchase = Purchase(
            idempotencyKey = "retry-key",
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = sampleEvent.priceInCents,
            paymentStatus = PurchaseStatus.FAILED_TECHNICAL,
            cieloTransactionId = null
        )
        val pendingPurchase = failedPurchase.copy(paymentStatus = PurchaseStatus.PENDING)
        val uri = android.net.Uri.parse("lio://payment?request=retry")
        coEvery { purchaseRepository.getPurchase(failedPurchase.idempotencyKey) } returnsMany
            listOf(failedPurchase, pendingPurchase)
        coEvery { purchaseRepository.resetTechnicalFailureForRetry(failedPurchase.idempotencyKey) } returns true
        every { paymentGateway.buildPaymentUri(any()) } returns uri

        viewModel.retryPayment(failedPurchase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(failedPurchase.idempotencyKey, savedStateHandle.get<String>("current_idempotency_key"))
        assertTrue(viewModel.isPaymentLaunchInProgress.value)
        coVerify(exactly = 0) { purchaseRepository.createOrGetPending(any()) }
    }

    @Test
    fun `When processing successful payment then should update status and success state`() {
        val idempotencyKey = "key-123"
        val transactionId = "TX-999"
        val result = PaymentResult.Success(transactionId, idempotencyKey)

        val purchase = Purchase(
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

        coVerify { purchaseRepository.completePending(idempotencyKey, PurchaseStatus.APPROVED, transactionId, null) }

        val state = viewModel.uiState.value
        assertTrue(state is UiState.PaymentSuccess)
        assertEquals(purchase, (state as UiState.PaymentSuccess).purchase)
        assertTrue(savedStateHandle.get<String>("current_idempotency_key") == null)
    }

    @Test
    fun `When processing canceled payment then should update status and error state`() {
        val idempotencyKey = "key-cancel"
        val result = PaymentResult.Canceled(idempotencyKey)

        val purchase = Purchase(
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

        coVerify { purchaseRepository.completePending(idempotencyKey, PurchaseStatus.CANCELED, null, null) }

        val state = viewModel.uiState.value
        assertTrue(state is UiState.PaymentError)
        val message = (state as UiState.PaymentError).message
        assertTrue(message is UiText.StringResource)
        assertEquals(R.string.payment_canceled, (message as UiText.StringResource).resId)
    }

    @Test
    fun `When Cielo launch fails then should update state to retryable PaymentError`() {
        val purchase = Purchase("key-123", "evt-1", "Test Show", 1, 10000, PurchaseStatus.FAILED_TECHNICAL, null)
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
        savedStateHandle["current_idempotency_key"] = idempotencyKey

        val result = PaymentResult.Success("TX-123", idempotencyKey)
        every { paymentGateway.parseCallback(uri, idempotencyKey) } returns result

        viewModel.onPaymentResultReceived(uri)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { purchaseRepository.completePending(idempotencyKey, PurchaseStatus.APPROVED, "TX-123", any()) }
    }

    @Test
    fun `When callback result has no persisted purchase then should expose a safe error`() {
        val result = PaymentResult.Success("TX-404", "missing-purchase")
        coEvery { purchaseRepository.getPurchase("missing-purchase") } returns null

        viewModel.processPaymentResult(result)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.PaymentError)
        assertEquals(
            R.string.error_purchase_not_found,
            ((state as UiState.PaymentError).message as UiText.StringResource).resId
        )
    }

    @Test
    fun `When late cancellation arrives after approval then approved state should be preserved`() {
        val idempotencyKey = "key-approved"
        val approvedPurchase = Purchase(
            idempotencyKey = idempotencyKey,
            eventId = sampleEvent.id,
            eventName = sampleEvent.title,
            quantity = 1,
            totalAmountInCents = sampleEvent.priceInCents,
            paymentStatus = PurchaseStatus.APPROVED,
            cieloTransactionId = "TX-123"
        )
        coEvery {
            purchaseRepository.completePending(idempotencyKey, PurchaseStatus.CANCELED, null, null)
        } returns false
        coEvery { purchaseRepository.getPurchase(idempotencyKey) } returns approvedPurchase

        viewModel.processPaymentResult(PaymentResult.Canceled(idempotencyKey))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.PaymentSuccess(approvedPurchase), viewModel.uiState.value)
    }
}
