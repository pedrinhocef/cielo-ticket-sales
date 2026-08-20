package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri
import android.util.Base64
import com.pedrosoares.cielosales.cielo.data.config.CieloConfig
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class CieloPayloadBuilder @Inject constructor(
    private val config: CieloConfig
) {
    fun buildPaymentUri(
        unitPriceInCents: Long,
        idempotencyKey: String,
        eventId: String,
        eventName: String,
        quantity: Int
    ): Uri {
        require(quantity in 1..10) { "Quantity must be between 1 and 10" }
        require(unitPriceInCents > 0) { "Unit price must be positive" }
        val totalInCents = Math.multiplyExact(unitPriceInCents, quantity.toLong())
        require(unitPriceInCents <= Int.MAX_VALUE) { "Unit price exceeds Cielo payload limit" }

        val json = JSONObject().apply {
            put("accessToken", config.accessToken)
            put("clientID", config.clientId)
            put("reference", idempotencyKey)
            put("installments", 0)

            val items = JSONArray().apply {
                val item = JSONObject().apply {
                    put("name", eventName)
                    put("quantity", quantity)
                    put("sku", eventId)
                    put("unitOfMeasure", "unidade")
                    put("unitPrice", unitPriceInCents.toInt())
                }
                put(item)
            }
            put("items", items)
            put("paymentCode", "DEBITO_AVISTA")
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
