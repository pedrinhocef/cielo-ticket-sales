package com.pedrosoares.cielosales.events.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import com.pedrosoares.cielosales.core.domain.repository.PendingPurchaseResult
import com.pedrosoares.cielosales.core.util.UiText
import com.pedrosoares.cielosales.events.domain.usecase.BuildCieloPaymentUriUseCase
import com.pedrosoares.cielosales.events.domain.usecase.ObserveEventsUseCase
import com.pedrosoares.cielosales.events.domain.usecase.ParseCieloCallbackUseCase
import com.pedrosoares.cielosales.events.domain.usecase.PaymentUseCases
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
import javax.inject.Inject

sealed interface UiState {
    data object Loading : UiState
    data class EventList(val events: List<Event>) : UiState
    data class PaymentPending(val purchase: Purchase) : UiState
    data class PaymentSuccess(val purchase: Purchase) : UiState
    data class PaymentError(val message: UiText, val retryPurchase: Purchase? = null) : UiState
}

sealed interface EventsEffect {
    data class LaunchCieloPayment(val uri: android.net.Uri, val idempotencyKey: String) : EventsEffect
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val observeEvents: ObserveEventsUseCase,
    private val paymentUseCases: PaymentUseCases,
    private val buildCieloPaymentUri: BuildCieloPaymentUriUseCase,
    private val parseCieloCallback: ParseCieloCallbackUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effect = Channel<EventsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val _isPaymentLaunchInProgress = MutableStateFlow(false)
    val isPaymentLaunchInProgress: StateFlow<Boolean> = _isPaymentLaunchInProgress.asStateFlow()

    private val paymentMutex = Mutex()
    private var eventsJob: Job? = null
    private var launchedPaymentKey: String? = null

    companion object {
        const val MAX_QUANTITY_PER_ORDER = com.pedrosoares.cielosales.core.domain.model.PurchaseConstraints.MAX_TICKETS_PER_ORDER
        private const val KEY_IDEMPOTENCY = "current_idempotency_key"
    }

    init {
        loadEvents()
        checkPendingPurchase()
    }

    private fun checkPendingPurchase() {
        viewModelScope.launch {
            val purchase = paymentUseCases.recoverPendingPurchase(savedStateHandle.get(KEY_IDEMPOTENCY))

            when (purchase?.paymentStatus) {
                PurchaseStatus.PENDING -> {
                    savedStateHandle[KEY_IDEMPOTENCY] = purchase.idempotencyKey
                    _uiState.value = UiState.PaymentPending(purchase)
                }
                PurchaseStatus.FAILED_TECHNICAL -> {
                    savedStateHandle[KEY_IDEMPOTENCY] = purchase.idempotencyKey
                    _uiState.value = UiState.PaymentError(UiText.StringResource(R.string.payment_attention), purchase)
                }
                else -> savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
            }
        }
    }

    fun startPaymentFlow(event: Event, quantity: Int) {
        viewModelScope.launch {
            paymentMutex.withLock {
                if (_isPaymentLaunchInProgress.value || _uiState.value is UiState.PaymentPending) return@withLock
                try {
                    val pendingPurchase = paymentUseCases.recoverPendingPurchase(savedStateHandle.get(KEY_IDEMPOTENCY))
                    if (pendingPurchase?.paymentStatus == PurchaseStatus.PENDING) {
                        _uiState.value = UiState.PaymentPending(pendingPurchase)
                        return@withLock
                    }
                    when (val result = paymentUseCases.startPayment(event, quantity)) {
                        is PendingPurchaseResult.Created -> launchPurchase(result.purchase)
                        is PendingPurchaseResult.ExistingPending -> launchPurchase(result.purchase)
                        is PendingPurchaseResult.ExistingTerminal -> showPersistedResult(result.purchase)
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

    fun retryPayment(purchase: Purchase) {
        viewModelScope.launch {
            paymentMutex.withLock {
                if (_isPaymentLaunchInProgress.value) return@withLock
                try {
                    val stored = paymentUseCases.retryPayment(purchase.idempotencyKey) ?: return@withLock
                    if (stored.paymentStatus != PurchaseStatus.PENDING) {
                        showPersistedResult(stored)
                        return@withLock
                    }
                    launchPurchase(stored)
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    _uiState.value = UiState.PaymentError(UiText.StringResource(R.string.error_retry_payment), purchase)
                }
            }
        }
    }

    private suspend fun launchPurchase(purchase: Purchase) {
        savedStateHandle[KEY_IDEMPOTENCY] = purchase.idempotencyKey
        val uri = buildCieloPaymentUri(purchase)
        _isPaymentLaunchInProgress.value = true
        _effect.send(EventsEffect.LaunchCieloPayment(uri, purchase.idempotencyKey))
    }

    fun loadEvents() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            observeEvents().collect { events ->
                if (_uiState.value !is UiState.PaymentPending) {
                    _uiState.value = UiState.EventList(events)
                }
            }
        }
    }

    fun onPaymentResultReceived(uri: android.net.Uri) {
        val expectedKey = savedStateHandle.get<String>(KEY_IDEMPOTENCY).orEmpty()
        processPaymentResult(parseCieloCallback(uri, expectedKey))
    }

    fun onCieloLaunchFailed(idempotencyKey: String) {
        viewModelScope.launch {
            paymentMutex.withLock {
                _isPaymentLaunchInProgress.value = false
                launchedPaymentKey = null
                paymentUseCases.registerLaunchFailure(idempotencyKey)?.let { purchase ->
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

    fun onCieloPaymentLaunched(idempotencyKey: String) {
        launchedPaymentKey = idempotencyKey
    }

    fun onHostResumed() {
        val idempotencyKey = launchedPaymentKey ?: return

        viewModelScope.launch {
            try {
                paymentUseCases.findPendingPurchase(idempotencyKey)?.let { purchase ->
                    _uiState.value = UiState.PaymentPending(purchase)
                }
            } finally {
                _isPaymentLaunchInProgress.value = false
                launchedPaymentKey = null
            }
        }
    }

    fun processPaymentResult(result: PaymentResult) {
        viewModelScope.launch {
            paymentMutex.withLock {
                try {
                    _isPaymentLaunchInProgress.value = false
                    launchedPaymentKey = null
                    val purchase = paymentUseCases.completePayment(result)
                    if (purchase == null) {
                        _uiState.value = UiState.PaymentError(
                            UiText.StringResource(R.string.error_purchase_not_found)
                        )
                    } else {
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

    private fun showPersistedResult(purchase: Purchase, callback: PaymentResult? = null) {
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
