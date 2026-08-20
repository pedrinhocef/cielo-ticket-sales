package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri
import android.util.Base64
import com.pedrosoares.cielosales.cielo.domain.model.PaymentResult
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
class CieloCallbackParserTest {

    private lateinit var parser: CieloCallbackParser

    @Before
    fun setUp() {
        parser = CieloCallbackParser()
    }

    @Test
    fun `When successful payment response then should return Success with reference and transactionId`() {
        val reference = "key-123"
        val txId = "cielo-tx-999"
        val json = JSONObject().apply {
            put("reference", reference)
            put("statusCode", 1)
            put("payments", org.json.JSONArray().put(JSONObject().put("paymentTransactionId", txId)))
        }
        val base64 = Base64.encodeToString(json.toString().toByteArray(), Base64.DEFAULT)
        val uri = Uri.parse("cielotickets://payment-response?response=$base64&responsecode=0")

        val result = parser.parse(uri, reference)

        assertTrue(result is PaymentResult.Success)
        assertEquals(reference, (result as PaymentResult.Success).idempotencyKey)
        assertEquals(txId, result.transactionId)
    }

    @Test
    fun `When responsecode is 0 but internal code is 1 then should return Canceled`() {
        val reference = "key-123"
        val json = JSONObject().apply {
            put("reference", reference)
            put("code", 1)
        }
        val base64 = Base64.encodeToString(json.toString().toByteArray(), Base64.DEFAULT)
        val uri = Uri.parse("cielotickets://payment-response?response=$base64&responsecode=0")
        val result = parser.parse(uri, reference)

        assertTrue(result is PaymentResult.Canceled)
        assertEquals(reference, (result as PaymentResult.Canceled).idempotencyKey)
    }

    @Test
    fun `When responsecode is 2 then should return FailedTechnical`() {
        val reference = "key-fail"
        val json = JSONObject().apply {
            put("reference", reference)
            put("code", 2)
        }
        val base64 = Base64.encodeToString(json.toString().toByteArray(), Base64.DEFAULT)
        val uri = Uri.parse("cielotickets://payment-response?response=$base64&responsecode=2")
        val result = parser.parse(uri, reference)

        assertTrue(result is PaymentResult.FailedTechnical)
        assertEquals(reference, (result as PaymentResult.FailedTechnical).idempotencyKey)
        assertEquals("Technical error on the terminal", result.message)
    }

    @Test
    fun `When responsecode is 3 then should return Denied`() {
        val reference = "key-denied"
        val json = JSONObject().apply {
            put("reference", reference)
            put("code", 3)
        }
        val base64 = Base64.encodeToString(json.toString().toByteArray(), Base64.DEFAULT)
        val uri = Uri.parse("cielotickets://payment-response?response=$base64&responsecode=3")
        val result = parser.parse(uri, reference)

        assertTrue(result is PaymentResult.Denied)
        assertEquals(reference, (result as PaymentResult.Denied).idempotencyKey)
        assertEquals("Payment error", result.reason)
    }

    @Test
    fun `When responsecode is 4 then should return FailedTechnical with auth error`() {
        val reference = "key-auth"
        val json = JSONObject().apply {
            put("reference", reference)
            put("code", 4)
        }
        val base64 = Base64.encodeToString(json.toString().toByteArray(), Base64.DEFAULT)
        val uri = Uri.parse("cielotickets://payment-response?response=$base64&responsecode=4")
        val result = parser.parse(uri, reference)

        assertTrue(result is PaymentResult.FailedTechnical)
        assertEquals(reference, (result as PaymentResult.FailedTechnical).idempotencyKey)
        assertEquals("Authentication or credentials error", result.message)
    }

    @Test
    fun `When reference does not match then should return FailedTechnical`() {
        val reference = "key-123"
        val json = JSONObject().apply {
            put("reference", "WRONG")
            put("code", 0)
        }
        val base64 = Base64.encodeToString(json.toString().toByteArray(), Base64.DEFAULT)
        val uri = Uri.parse("cielotickets://payment-response?response=$base64&responsecode=0")

        val result = parser.parse(uri, reference)

        assertTrue(result is PaymentResult.FailedTechnical)
        assertTrue((result as PaymentResult.FailedTechnical).message.contains("Correlation failure"))
    }

    @Test
    fun `When JSON or Base64 is invalid then should return a technical failure`() {
        val uri = Uri.parse("cielotickets://payment-response?response=INVALID&responsecode=0")
        val result = parser.parse(uri, "key-err")

        assertTrue(result is PaymentResult.FailedTechnical)
        assertEquals("key-err", (result as PaymentResult.FailedTechnical).idempotencyKey)
    }
}
