package com.pedrosoares.cielosales.events.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedrosoares.cielosales.cielo.model.PaymentResult
import com.pedrosoares.cielosales.core.data.local.dao.PurchaseDao
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.data.repository.EventRepository
import com.pedrosoares.cielosales.core.domain.model.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UiState {
    data object Loading : UiState
    data class EventList(val events: List<Event>) : UiState
    data class PaymentSuccess(val purchase: PurchaseEntity) : UiState
    data class PaymentError(val message: String) : UiState
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val purchaseDao: PurchaseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadEvents()
    }

    fun loadEvents() {
        viewModelScope.launch {
            eventRepository.getEvents().collect { events ->
                _uiState.value = UiState.EventList(events)
            }
        }
    }

    fun processPayment(result: PaymentResult, selectedEvent: Event, quantity: Int) {
        viewModelScope.launch {
            when (result) {
                is PaymentResult.Success -> {
                    val purchase = PurchaseEntity(
                        idempotencyKey = result.idempotencyKey,
                        eventId = selectedEvent.id,
                        eventName = selectedEvent.title,
                        quantity = quantity,
                        totalAmountInCents = selectedEvent.priceInCents * quantity,
                        paymentStatus = "APPROVED",
                        cieloTransactionId = result.transactionId
                    )
                    purchaseDao.insertPurchase(purchase)
                    _uiState.value = UiState.PaymentSuccess(purchase)
                }
                is PaymentResult.Error -> {
                    _uiState.value = UiState.PaymentError(result.message)
                }
                is PaymentResult.Canceled -> {
                    _uiState.value = UiState.PaymentError("Payment canceled by user")

                }
            }
        }
    }
}
