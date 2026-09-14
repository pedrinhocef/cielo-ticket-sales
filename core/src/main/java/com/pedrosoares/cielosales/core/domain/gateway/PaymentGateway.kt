package com.pedrosoares.cielosales.core.domain.gateway

import android.net.Uri
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import com.pedrosoares.cielosales.core.domain.model.Purchase

interface PaymentGateway {
    fun buildPaymentUri(purchase: Purchase): Uri
    fun parseCallback(uri: Uri, expectedIdempotencyKey: String): PaymentResult
}
