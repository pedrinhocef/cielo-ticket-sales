package com.pedrosoares.cielosales.events.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedrosoares.cielosales.cielo.data.remote.CieloCallbackParser
import com.pedrosoares.cielosales.cielo.data.remote.CieloPayloadBuilder
import com.pedrosoares.cielosales.cielo.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.data.repository.EventRepository
import com.pedrosoares.cielosales.core.data.repository.PendingPurchaseResult
import com.pedrosoares.cielosales.core.data.repository.PurchaseRepository
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import com.pedrosoares.cielosales.core.util.UiText
import com.pedrosoares.cielosales.events.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

sealed interface UiState {
    data object Loading : UiState
    data class EventList(val events: List<Event>) : UiState
    data class ProcessingPayment(val idempotencyKey: String) : UiState
    data class PaymentPending(val purchase: PurchaseEntity) : UiState
    data class PaymentSuccess(val purchase: PurchaseEntity) : UiState
    data class PaymentError(val message: UiText, val retryPurchase: PurchaseEntity? = null) : UiState
}

sealed interface EventsEffect {
    data class LaunchCieloPayment(val uri: android.net.Uri, val idempotencyKey: String) : EventsEffect
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val purchaseRepository: PurchaseRepository,
    private val payloadBuilder: CieloPayloadBuilder,
    private val callbackParser: CieloCallbackParser,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effect = Channel<EventsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val paymentMutex = Mutex()
    private var eventsJob: Job? = null

    companion object {
        const val MAX_QUANTITY_PER_ORDER = 10
        private const val KEY_IDEMPOTENCY = "current_idempotency_key"
    }

    init {
        loadEvents()
        checkPendingPurchase()
    }

    private fun checkPendingPurchase() {
        savedStateHandle.get<String>(KEY_IDEMPOTENCY)?.let { pendingKey ->
            viewModelScope.launch {
                val purchase = purchaseRepository.getPurchase(pendingKey)
                when (purchase?.paymentStatus) {
                    PurchaseStatus.PENDING -> _uiState.value = UiState.PaymentPending(purchase)
                    PurchaseStatus.FAILED_TECHNICAL -> _uiState.value = UiState.PaymentError(
                        UiText.StringResource(R.string.payment_attention), purchase
                    )
                    else -> savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
                }
            }
        }
    }

    fun startPaymentFlow(event: Event, quantity: Int) {
        viewModelScope.launch {
            paymentMutex.withLock {
                if (_uiState.value is UiState.ProcessingPayment || _uiState.value is UiState.PaymentPending) return@withLock
                try {
                    val pendingPurchase = savedStateHandle.get<String>(KEY_IDEMPOTENCY)
                        ?.let { purchaseRepository.getPurchase(it) }
                    if (pendingPurchase?.paymentStatus == PurchaseStatus.PENDING) {
                        _uiState.value = UiState.PaymentPending(pendingPurchase)
                        return@withLock
                    }
                    val validQuantity = quantity.coerceIn(1, MAX_QUANTITY_PER_ORDER)
                    val purchase = PurchaseEntity(
                        idempotencyKey = UUID.randomUUID().toString(),
                        eventId = event.id,
                        eventName = event.title,
                        quantity = validQuantity,
                        totalAmountInCents = Math.multiplyExact(event.priceInCents, validQuantity.toLong()),
                        paymentStatus = PurchaseStatus.PENDING,
                        cieloTransactionId = null
                    )
                    when (val result = purchaseRepository.createOrGetPending(purchase)) {
                        is PendingPurchaseResult.Created -> launchPurchase(result.purchase)
                        is PendingPurchaseResult.ExistingPending -> launchPurchase(result.purchase)
                        is PendingPurchaseResult.ExistingTerminal -> showTerminalPurchase(result.purchase)
                    }
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    _uiState.value = UiState.PaymentError(
                        UiText.StringResource(R.string.error_start_payment)
                    )
                }
            }
        }
    }

    fun retryPayment(purchase: PurchaseEntity) {
        viewModelScope.launch {
            paymentMutex.withLock {
                if (_uiState.value is UiState.ProcessingPayment) return@withLock
                try {
                    val stored = purchaseRepository.getPurchase(purchase.idempotencyKey) ?: return@withLock
                    val activePurchase = when (stored.paymentStatus) {
                        PurchaseStatus.PENDING -> stored
                        PurchaseStatus.FAILED_TECHNICAL -> {
                            if (!purchaseRepository.resetTechnicalFailureForRetry(stored.idempotencyKey)) return@withLock
                            purchaseRepository.getPurchase(stored.idempotencyKey) ?: return@withLock
                        }
                        else -> {
                            showTerminalPurchase(stored)
                            return@withLock
                        }
                    }
                    launchPurchase(activePurchase)
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    _uiState.value = UiState.PaymentError(UiText.StringResource(R.string.error_retry_payment), purchase)
                }
            }
        }
    }

    private suspend fun launchPurchase(purchase: PurchaseEntity) {
        savedStateHandle[KEY_IDEMPOTENCY] = purchase.idempotencyKey
        _uiState.value = UiState.ProcessingPayment(purchase.idempotencyKey)
        val unitPrice = purchase.totalAmountInCents / purchase.quantity
        require(unitPrice * purchase.quantity == purchase.totalAmountInCents) { "Invalid persisted purchase total" }
        val uri = payloadBuilder.buildPaymentUri(
            unitPriceInCents = unitPrice,
            idempotencyKey = purchase.idempotencyKey,
            eventId = purchase.eventId,
            eventName = purchase.eventName,
            quantity = purchase.quantity
        )
        _effect.send(EventsEffect.LaunchCieloPayment(uri, purchase.idempotencyKey))
    }

    fun loadEvents() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            eventRepository.getEvents().collect { events ->
                if (_uiState.value !is UiState.PaymentPending && _uiState.value !is UiState.ProcessingPayment) {
                    _uiState.value = UiState.EventList(events)
                }
            }
        }
    }

    fun onPaymentResultReceived(uri: android.net.Uri) {
        val expectedKey = savedStateHandle.get<String>(KEY_IDEMPOTENCY).orEmpty()
        processPaymentResult(callbackParser.parse(uri, expectedKey))
    }

    fun onCieloLaunchFailed(idempotencyKey: String) {
        viewModelScope.launch {
            paymentMutex.withLock {
                purchaseRepository.completePending(
                    key = idempotencyKey,
                    status = PurchaseStatus.FAILED_TECHNICAL,
                    reason = "Cielo payment application was not found"
                )
                purchaseRepository.getPurchase(idempotencyKey)?.let { purchase ->
                    savedStateHandle[KEY_IDEMPOTENCY] = idempotencyKey
                    if (purchase.paymentStatus == PurchaseStatus.FAILED_TECHNICAL) {
                        _uiState.value = UiState.PaymentError(
                            UiText.StringResource(R.string.payment_app_not_found), purchase
                        )
                    } else {
                        showPersistedResult(purchase)
                    }
                }
            }
        }
    }

    fun processPaymentResult(result: PaymentResult) {
        viewModelScope.launch {
            paymentMutex.withLock {
                val status = when (result) {
                    is PaymentResult.Success -> PurchaseStatus.APPROVED
                    is PaymentResult.Denied -> PurchaseStatus.DENIED
                    is PaymentResult.Canceled -> PurchaseStatus.CANCELED
                    is PaymentResult.FailedTechnical, is PaymentResult.Error -> PurchaseStatus.FAILED_TECHNICAL
                }
                try {
                    purchaseRepository.completePending(
                        key = result.idempotencyKey,
                        status = status,
                        transactionId = (result as? PaymentResult.Success)?.transactionId,
                        reason = (result as? PaymentResult.Denied)?.reason
                            ?: (result as? PaymentResult.FailedTechnical)?.message
                            ?: (result as? PaymentResult.Error)?.message
                    )
                    purchaseRepository.getPurchase(result.idempotencyKey)?.let { purchase ->
                        showPersistedResult(purchase, result)
                    }
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    _uiState.value = UiState.PaymentError(
                        UiText.StringResource(R.string.error_database)
                    )
                }
            }
        }
    }

    private fun showTerminalPurchase(purchase: PurchaseEntity) {
        showPersistedResult(purchase)
    }

    private fun showPersistedResult(purchase: PurchaseEntity, callback: PaymentResult? = null) {
        when (purchase.paymentStatus) {
            PurchaseStatus.APPROVED -> {
                _uiState.value = UiState.PaymentSuccess(purchase)
                savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
            }
            PurchaseStatus.FAILED_TECHNICAL -> {
                val message = when (callback) {
                    is PaymentResult.FailedTechnical -> UiText.DynamicString(callback.message)
                    is PaymentResult.Error -> UiText.DynamicString(callback.message)
                    else -> purchase.reason?.let(UiText::DynamicString)
                        ?: UiText.StringResource(R.string.payment_attention)
                }
                _uiState.value = UiState.PaymentError(message, retryPurchase = purchase)
            }
            PurchaseStatus.CANCELED -> {
                _uiState.value = UiState.PaymentError(UiText.StringResource(R.string.payment_canceled))
                savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
            }
            PurchaseStatus.DENIED -> {
                _uiState.value = UiState.PaymentError(
                    purchase.reason?.let(UiText::DynamicString)
                        ?: UiText.StringResource(R.string.payment_attention)
                )
                savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
            }
            PurchaseStatus.PENDING -> _uiState.value = UiState.PaymentPending(purchase)
        }
    }
}
