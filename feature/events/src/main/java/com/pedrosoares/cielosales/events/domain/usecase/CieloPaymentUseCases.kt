package com.pedrosoares.cielosales.events.domain.usecase

import android.net.Uri
import com.pedrosoares.cielosales.core.domain.gateway.PaymentGateway
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase
import javax.inject.Inject
import com.pedrosoares.cielosales.observability.api.NoOpObservability
import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.api.ObservabilityDimension
import com.pedrosoares.cielosales.observability.api.ObservabilityEventName
import com.pedrosoares.cielosales.observability.api.ObservabilityErrorCode
import com.pedrosoares.cielosales.observability.api.ObservabilityStage
import com.pedrosoares.cielosales.observability.api.record
import com.pedrosoares.cielosales.observability.api.track

class BuildCieloPaymentUriUseCase @Inject constructor(
    private val paymentGateway: PaymentGateway,
    private val observability: Observability = NoOpObservability
) {
    operator fun invoke(purchase: Purchase): Uri {
        val uri = try {
            paymentGateway.buildPaymentUri(purchase)
        } catch (exception: Exception) {
            observability.record(
                ObservabilityErrorCode.CIELO_DEEP_LINK_BUILD_FAILED,
                ObservabilityStage.DEEP_LINK,
                exception
            )
            throw exception
        }
        observability.track(
            ObservabilityEventName.CIELO_DEEP_LINK_BUILT,
            ObservabilityStage.DEEP_LINK,
            ObservabilityDimension.QUANTITY to purchase.quantity.toString()
        )
        return uri
    }
}

class ParseCieloCallbackUseCase @Inject constructor(
    private val paymentGateway: PaymentGateway,
    private val observability: Observability = NoOpObservability
) {
    operator fun invoke(uri: Uri, expectedIdempotencyKey: String): PaymentResult {
        val result = paymentGateway.parseCallback(uri, expectedIdempotencyKey)
        observability.track(
            ObservabilityEventName.CALLBACK_PARSED,
            ObservabilityStage.CALLBACK,
            ObservabilityDimension.RESULT to result.javaClass.simpleName
        )
        return result
    }
}
