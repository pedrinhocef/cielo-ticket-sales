package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri
import com.pedrosoares.cielosales.core.domain.gateway.PaymentGateway
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase
import javax.inject.Inject

class CieloPaymentGateway @Inject constructor(
    private val payloadBuilder: CieloPayloadBuilder,
    private val callbackParser: CieloCallbackParser
) : PaymentGateway {
    override fun buildPaymentUri(purchase: Purchase): Uri {
        val unitPrice = purchase.totalAmountInCents / purchase.quantity
        require(unitPrice * purchase.quantity == purchase.totalAmountInCents) { "Invalid persisted purchase total" }
        return payloadBuilder.buildPaymentUri(
            unitPrice, purchase.idempotencyKey, purchase.eventId, purchase.eventName, purchase.quantity
        )
    }

    override fun parseCallback(uri: Uri, expectedIdempotencyKey: String): PaymentResult =
        callbackParser.parse(uri, expectedIdempotencyKey)
}
