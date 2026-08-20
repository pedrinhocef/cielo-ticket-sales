package com.pedrosoares.cielosales.cielo.data.remote

import android.util.Base64
import com.pedrosoares.cielosales.cielo.data.config.CieloConfig
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CieloPayloadBuilderTest {

    private val config: CieloConfig = mockk()
    private lateinit var builder: CieloPayloadBuilder

    @Before
    fun setUp() {
        every { config.clientId } returns "client-id"
        every { config.accessToken } returns "token-abc"
        builder = CieloPayloadBuilder(config)
    }

    @Test
    fun `When buildPaymentUri then should return correct URI with Base64 payload`() {
        val amount = 1000L
        val key = "key-123"
        val event = "Show A"
        val qty = 2

        val uri = builder.buildPaymentUri(
            unitPriceInCents = amount / qty,
            idempotencyKey = key,
            eventId = "event-42",
            eventName = event,
            quantity = qty
        )

        assertEquals("lio", uri.scheme)
        assertEquals("payment", uri.host)
        assertEquals("cielotickets://payment-response", uri.getQueryParameter("urlCallback"))

        val request = uri.getQueryParameter("request")
        assertTrue(request != null)

        val decoded = String(Base64.decode(request, Base64.NO_WRAP))
        val json = JSONObject(decoded)

        assertEquals("1000", json.getString("value"))
        assertEquals("client-id", json.getString("clientID"))
        assertEquals("token-abc", json.getString("accessToken"))
        assertEquals(key, json.getString("reference"))

        val items = json.getJSONArray("items")
        assertEquals(1, items.length())
        val item = items.getJSONObject(0)
        assertEquals(event, item.getString("name"))
        assertEquals("event-42", item.getString("sku"))
        assertEquals(500, item.getInt("unitPrice"))
        assertEquals(qty, item.getInt("quantity"))
    }
}
