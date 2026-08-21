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

            val approvedPayment = findApprovedPayment(json)
            if (approvedPayment != null) {
                successfulPaymentOrFailure(json, reference, approvedPayment)
            } else {
                PaymentResult.FailedTechnical("Payment response has no valid approved transaction", reference)
            }
        } catch (_: Exception) {
            PaymentResult.FailedTechnical("Invalid payment response", expectedReference)
        }
    }

    private fun successfulPaymentOrFailure(
        json: JSONObject,
        reference: String,
        approvedPayment: JSONObject? = findApprovedPayment(json)
    ): PaymentResult {
        val transactionId = json.optString("paymentTransactionId")
            .ifBlank {
                approvedPayment?.optString("paymentTransactionId").orEmpty()
            }
            .ifBlank {
                approvedPayment
                    ?.optJSONObject("paymentFields")
                    ?.optString("paymentTransactionId")
                    .orEmpty()
            }
            .ifBlank {
                approvedPayment?.optString("externalId").orEmpty()
            }
        return if (transactionId.isBlank()) {
            PaymentResult.FailedTechnical("Approved response without transaction ID", reference)
        } else {
            PaymentResult.Success(transactionId = transactionId, idempotencyKey = reference)
        }
    }

    private fun findApprovedPayment(json: JSONObject): JSONObject? {
        val payments = json.optJSONArray("payments") ?: return null
        val rootStatusCode = json.optInt("statusCode", -1)
        for (index in 0 until payments.length()) {
            val payment = payments.optJSONObject(index) ?: continue
            val paymentFields = payment.optJSONObject("paymentFields")
            val statusCode = payment.optInt(
                "statusCode",
                paymentFields?.optString("statusCode")?.toIntOrNull() ?: rootStatusCode
            )
            if (statusCode == 0 || statusCode == 1) return payment
        }
        return null
    }
}
