package com.pedrosoares.cielosales.cielo.model

sealed interface PaymentResult {
    data class Success(val transactionId: String, val idempotencyKey: String) : PaymentResult
    data class Error(val message: String, val idempotencyKey: String) : PaymentResult
    data class Canceled(val idempotencyKey: String) : PaymentResult
}
