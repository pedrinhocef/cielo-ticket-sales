package com.pedrosoares.cielosales.events.domain.usecase

import android.net.Uri
import com.pedrosoares.cielosales.core.domain.gateway.PaymentGateway
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase
import javax.inject.Inject

class BuildCieloPaymentUriUseCase @Inject constructor(
    private val paymentGateway: PaymentGateway
) {
    operator fun invoke(purchase: Purchase): Uri {
        return paymentGateway.buildPaymentUri(purchase)
    }
}

class ParseCieloCallbackUseCase @Inject constructor(
    private val paymentGateway: PaymentGateway
) {
    operator fun invoke(uri: Uri, expectedIdempotencyKey: String): PaymentResult =
        paymentGateway.parseCallback(uri, expectedIdempotencyKey)
}
