package com.pedrosoares.cielosales.cielo

import android.content.Intent
import com.pedrosoares.cielosales.cielo.model.PaymentResult
import java.util.UUID
import javax.inject.Inject

class PaymentManager @Inject constructor() {

    fun createCieloPaymentIntent(
        amountInCents: Long,
        paymentType: String = "DEBIT",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Intent {
        return Intent("com.cielo.ordermanager.action.PAYMENT").apply {
            putExtra("AMOUNT", amountInCents)
            putExtra("ORDER_ID", idempotencyKey)
            putExtra("PAYMENT_TYPE", paymentType)
        }
    }

    fun parsePaymentResult(resultCode: Int, data: Intent?, idempotencyKey: String): PaymentResult {
        if (data == null) return PaymentResult.Error("Null response from cielo", idempotencyKey)

        return when (resultCode) {
            -1 -> {
                val txId = data.getStringExtra("TRANSACTION_ID") ?: UUID.randomUUID().toString()
                PaymentResult.Success(transactionId = txId, idempotencyKey = idempotencyKey)
            }

            0 -> PaymentResult.Canceled(idempotencyKey)
            else -> {
                val errorReason = data.getStringExtra("ERROR_REASON") ?: "Unknown error"
                PaymentResult.Error(message = errorReason, idempotencyKey = idempotencyKey)
            }
        }
    }
}