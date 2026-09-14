package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri
import android.util.Base64
import com.pedrosoares.cielosales.cielo.data.config.CieloConfig
import com.pedrosoares.cielosales.core.domain.model.PurchaseConstraints
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class CieloPayloadBuilder @Inject constructor(
    private val config: CieloConfig
) {
    private companion object {
        const val INSTALLMENTS_SINGLE_PAYMENT = 0
        const val UNIT_OF_MEASURE_TICKET = "unidade"
        const val PAYMENT_CODE_DEBIT_AT_SIGHT = "DEBITO_AVISTA"
    }

    fun buildPaymentUri(
        unitPriceInCents: Long,
        idempotencyKey: String,
        eventId: String,
        eventName: String,
        quantity: Int
    ): Uri {
        require(config.clientId.isNotBlank()) { "Cielo Client ID is not configured" }
        require(config.accessToken.isNotBlank()) { "Cielo Access Token is not configured" }
        require(quantity in PurchaseConstraints.MIN_TICKETS_PER_ORDER..PurchaseConstraints.MAX_TICKETS_PER_ORDER) {
            "Quantity must be between ${PurchaseConstraints.MIN_TICKETS_PER_ORDER} and ${PurchaseConstraints.MAX_TICKETS_PER_ORDER}"
        }
        require(unitPriceInCents > 0) { "Unit price must be positive" }
        val totalInCents = Math.multiplyExact(unitPriceInCents, quantity.toLong())
        require(unitPriceInCents <= Int.MAX_VALUE) { "Unit price exceeds Cielo payload limit" }

        val json = JSONObject().apply {
            put("accessToken", config.accessToken)
            put("clientID", config.clientId)
            put("reference", idempotencyKey)
            put("installments", INSTALLMENTS_SINGLE_PAYMENT)

            val items = JSONArray().apply {
                val item = JSONObject().apply {
                    put("name", eventName)
                    put("quantity", quantity)
                    put("sku", eventId)
                    put("unitOfMeasure", UNIT_OF_MEASURE_TICKET)
                    put("unitPrice", unitPriceInCents.toInt())
                }
                put(item)
            }
            put("items", items)
            put("paymentCode", PAYMENT_CODE_DEBIT_AT_SIGHT)
            put("value", totalInCents.toString())
        }

        val base64Payload = Base64.encodeToString(
            json.toString().toByteArray(),
            Base64.NO_WRAP
        )

        return Uri.Builder()
            .scheme(CieloDeepLinkContract.PAYMENT_SCHEME)
            .authority(CieloDeepLinkContract.PAYMENT_HOST)
            .appendQueryParameter(CieloDeepLinkContract.REQUEST_PARAMETER, base64Payload)
            .appendQueryParameter(
                CieloDeepLinkContract.CALLBACK_PARAMETER,
                CieloDeepLinkContract.callbackUri.toString()
            )
            .build()
    }
}
