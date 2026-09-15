package com.pedrosoares.cielosales.purchases.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.pedrosoares.cielosales.observability.api.NoOpObservability
import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.api.ObservabilityDimension
import com.pedrosoares.cielosales.observability.api.ObservabilityEventName
import com.pedrosoares.cielosales.observability.api.ObservabilityErrorCode
import com.pedrosoares.cielosales.observability.api.ObservabilityStage
import com.pedrosoares.cielosales.observability.api.record
import com.pedrosoares.cielosales.observability.api.track

enum class PurchaseFilter(val status: PurchaseStatus?) {
    ALL(null), APPROVED(PurchaseStatus.APPROVED), PENDING(PurchaseStatus.PENDING),
    CANCELED(PurchaseStatus.CANCELED), DENIED(PurchaseStatus.DENIED), FAILED(PurchaseStatus.FAILED_TECHNICAL)
}

data class PurchasesUiState(
    val filter: PurchaseFilter = PurchaseFilter.ALL,
    val purchases: List<Purchase> = emptyList()
)

@HiltViewModel
class PurchasesViewModel @Inject constructor(
    purchaseRepository: PurchaseRepository,
    private val observability: Observability = NoOpObservability
) : ViewModel() {
    private companion object {
        const val STOP_OBSERVING_DELAY_MILLIS = 5_000L
    }
    private val selectedFilter = MutableStateFlow(PurchaseFilter.ALL)

    val uiState: StateFlow<PurchasesUiState> = combine(
        purchaseRepository.observePurchases(),
        selectedFilter
    ) { purchases, filter ->
        PurchasesUiState(filter, purchases.filter { filter.status == null || it.paymentStatus == filter.status })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_OBSERVING_DELAY_MILLIS),
        initialValue = PurchasesUiState()
    )

    fun selectFilter(filter: PurchaseFilter) {
        selectedFilter.value = filter
        observability.track(
            ObservabilityEventName.PURCHASE_FILTER_SELECTED,
            ObservabilityStage.HISTORY,
            ObservabilityDimension.FILTER to filter.name
        )
    }

    fun onTicketQrOpened() {
        observability.track(
            ObservabilityEventName.TICKET_QR_OPENED,
            ObservabilityStage.QR_CODE
        )
    }

    fun onQrCodeGenerationFailed(exception: Exception) {
        observability.record(
            ObservabilityErrorCode.QR_CODE_GENERATION_FAILED,
            ObservabilityStage.QR_CODE,
            exception
        )
    }
}
