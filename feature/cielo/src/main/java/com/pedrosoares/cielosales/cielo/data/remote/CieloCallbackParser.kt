package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri
import android.util.Base64
import com.pedrosoares.cielosales.core.domain.model.PaymentResult
import org.json.JSONObject
import javax.inject.Inject

class CieloCallbackParser @Inject constructor() {
    private companion object {
        const val CALLBACK_CODE_APPROVED = 0
        const val CALLBACK_CODE_CANCELED = 1
        const val CALLBACK_CODE_TECHNICAL_FAILURE = 2
        const val CALLBACK_CODE_DENIED = 3
        const val CALLBACK_CODE_AUTHENTICATION_FAILURE = 4
        const val UNKNOWN_CALLBACK_CODE = -1
        const val PAYMENT_STATUS_CODE_APPROVED_ALTERNATIVE = 1
    }

    fun parse(uri: Uri, expectedReference: String): PaymentResult {
        val responseBase64 = uri.getQueryParameter("response")

        if (responseBase64.isNullOrBlank()) {
            return PaymentResult.Error("Missing 'response' parameter", expectedReference)
        }

        return try {
            val jsonStr = String(Base64.decode(responseBase64, Base64.DEFAULT))
            val json = JSONObject(jsonStr)
            val returnedReference = json.optString("reference", "")
            val internalCode = json.optInt("code", UNKNOWN_CALLBACK_CODE)
            val reference = if (returnedReference.isBlank() && expectedReference.isNotBlank() &&
                internalCode in CALLBACK_CODE_CANCELED..CALLBACK_CODE_AUTHENTICATION_FAILURE
            ) {
                expectedReference
            } else {
                returnedReference
            }

            if (reference.isBlank()) {
                return PaymentResult.FailedTechnical("Payment response has no reference", expectedReference)
            }

            if (expectedReference.isNotBlank() && reference != expectedReference) {
                return PaymentResult.FailedTechnical(
                    "Correlation failure: expected $expectedReference but got $reference",
                    expectedReference
                )
            }

            if (json.has("code")) {
                return when (internalCode) {
                    CALLBACK_CODE_APPROVED -> successfulPaymentOrFailure(json, reference)
                    CALLBACK_CODE_CANCELED -> PaymentResult.Canceled(reference)
                    CALLBACK_CODE_TECHNICAL_FAILURE -> PaymentResult.FailedTechnical("Technical error on the terminal", reference)
                    CALLBACK_CODE_DENIED -> PaymentResult.Denied("Payment error", reference)
                    CALLBACK_CODE_AUTHENTICATION_FAILURE -> PaymentResult.FailedTechnical("Authentication or credentials error", reference)
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
        return if (transactionId.isBlank()) {
            PaymentResult.FailedTechnical("Approved response without transaction ID", reference)
        } else {
            PaymentResult.Success(transactionId = transactionId, idempotencyKey = reference)
        }
    }

    private fun findApprovedPayment(json: JSONObject): JSONObject? {
        val payments = json.optJSONArray("payments") ?: return null
        val rootStatusCode = json.optInt("statusCode", UNKNOWN_CALLBACK_CODE)
        for (index in 0 until payments.length()) {
            val payment = payments.optJSONObject(index) ?: continue
            val paymentFields = payment.optJSONObject("paymentFields")
            val statusCode = payment.optInt(
                "statusCode",
                paymentFields?.optString("statusCode")?.toIntOrNull() ?: rootStatusCode
            )
            if (statusCode == CALLBACK_CODE_APPROVED || statusCode == PAYMENT_STATUS_CODE_APPROVED_ALTERNATIVE) return payment
        }
        return null
    }
}
