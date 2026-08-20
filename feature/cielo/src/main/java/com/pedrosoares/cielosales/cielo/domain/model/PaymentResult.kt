package com.pedrosoares.cielosales.cielo.domain.model

sealed interface PaymentResult {
    val idempotencyKey: String

    data class Success(
        val transactionId: String,
        override val idempotencyKey: String
    ) : PaymentResult

    data class Denied(
        val reason: String,
        override val idempotencyKey: String
    ) : PaymentResult

    data class FailedTechnical(
        val message: String,
        override val idempotencyKey: String
    ) : PaymentResult

    data class Error(
        val message: String,
        override val idempotencyKey: String
    ) : PaymentResult

    data class Canceled(
        override val idempotencyKey: String
    ) : PaymentResult
}
