package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri
import android.util.Base64
import com.pedrosoares.cielosales.cielo.domain.model.PaymentResult
import org.json.JSONObject
import javax.inject.Inject

class CieloCallbackParser @Inject constructor() {

    fun parse(uri: Uri, expectedReference: String): PaymentResult {
        val responseBase64 = uri.getQueryParameter("response")

        if (responseBase64.isNullOrBlank() || expectedReference.isBlank()) {
            return PaymentResult.Error("Missing 'response' parameter", expectedReference)
        }

        return try {
            val jsonStr = String(Base64.decode(responseBase64, Base64.DEFAULT))
            val json = JSONObject(jsonStr)
            val returnedReference = json.optString("reference", "")
            val internalCode = json.optInt("code", -1)
            val reference = if (returnedReference.isBlank() && internalCode in 1..4) {
                expectedReference
            } else {
                returnedReference
            }

            // Validação estrita de correlação
            if (reference != expectedReference) {
                return PaymentResult.FailedTechnical(
                    "Correlation failure: expected $expectedReference but got $reference",
                    expectedReference
                )
            }

            if (json.has("code")) {
                return when (internalCode) {
                    0 -> successfulPaymentOrFailure(json, reference)
                1 -> PaymentResult.Canceled(reference)
                2 -> PaymentResult.FailedTechnical("Technical error on the terminal", reference)
                3 -> PaymentResult.Denied("Payment error", reference)
                4 -> PaymentResult.FailedTechnical("Authentication or credentials error", reference)
                else -> PaymentResult.FailedTechnical(
                        "Invalid error code: ${json.optString("reason", "unknown")}",
                        reference
                    )
                }
            }

            val payment = json.optJSONArray("payments")?.optJSONObject(0)
            val statusCode = json.optInt("statusCode", payment?.optInt("statusCode", -1) ?: -1)
            if (statusCode in setOf(0, 1)) {
                successfulPaymentOrFailure(json, reference)
            } else {
                PaymentResult.FailedTechnical("Payment response has no valid approved transaction", reference)
            }
        } catch (e: Exception) {
            PaymentResult.FailedTechnical("Invalid payment response", expectedReference)
        }
    }

    private fun successfulPaymentOrFailure(json: JSONObject, reference: String): PaymentResult {
        val transactionId = json.optString("paymentTransactionId")
            .ifBlank {
                json.optJSONArray("payments")
                    ?.optJSONObject(0)
                    ?.optString("paymentTransactionId")
                    .orEmpty()
            }
        return if (transactionId.isBlank()) {
            PaymentResult.FailedTechnical("Approved response without transaction ID", reference)
        } else {
            PaymentResult.Success(transactionId = transactionId, idempotencyKey = reference)
        }
    }
}
