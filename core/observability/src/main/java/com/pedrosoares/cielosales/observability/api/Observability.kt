package com.pedrosoares.cielosales.observability.api

enum class ObservabilityEventName {
    PAYMENT_START_REQUESTED,
    PURCHASE_PERSISTED,
    PAYMENT_RECOVERY_EVALUATED,
    PAYMENT_RECOVERED,
    PAYMENT_RETRY_REQUESTED,
    PAYMENT_RETRY_COMPLETED,
    CIELO_DEEP_LINK_BUILT,
    CIELO_LAUNCH_REQUESTED,
    CIELO_LAUNCH_SUCCEEDED,
    CIELO_LAUNCH_FAILED,
    APP_RETURNED_WITHOUT_CALLBACK,
    CALLBACK_RECEIVED,
    CALLBACK_PARSED,
    PAYMENT_STATE_TRANSITION,
    LATE_CALLBACK_IGNORED,
    PURCHASE_FILTER_SELECTED,
    TICKET_QR_OPENED
}

enum class ObservabilityStage {
    PAYMENT_START,
    PERSISTENCE,
    RECOVERY,
    RETRY,
    DEEP_LINK,
    CIELO_LAUNCH,
    CALLBACK,
    HISTORY,
    QR_CODE
}

enum class ObservabilitySeverity { INFO, WARNING, ERROR }

enum class ObservabilityDimension {
    QUANTITY,
    STATUS,
    PREVIOUS_STATUS,
    RECOVERY_SOURCE,
    RESULT,
    FILTER
}

enum class ObservabilityErrorCode {
    PAYMENT_START_FAILED,
    PAYMENT_RETRY_FAILED,
    PURCHASE_PERSISTENCE_FAILED,
    CIELO_DEEP_LINK_BUILD_FAILED,
    CIELO_APP_NOT_FOUND,
    CALLBACK_INVALID,
    CALLBACK_CORRELATION_FAILED,
    CALLBACK_PERSISTENCE_FAILED,
    PURCHASE_NOT_FOUND,
    QR_CODE_GENERATION_FAILED
}

data class ObservabilityEvent(
    val name: ObservabilityEventName,
    val stage: ObservabilityStage,
    val severity: ObservabilitySeverity = ObservabilitySeverity.INFO,
    val dimensions: Map<ObservabilityDimension, String> = emptyMap()
)

data class ObservabilityError(
    val code: ObservabilityErrorCode,
    val stage: ObservabilityStage,
    val exceptionType: String? = null
)

interface Observability {
    fun track(event: ObservabilityEvent)
    fun record(error: ObservabilityError)
}

object NoOpObservability : Observability {
    override fun track(event: ObservabilityEvent) = Unit
    override fun record(error: ObservabilityError) = Unit
}

fun Observability.track(
    name: ObservabilityEventName,
    stage: ObservabilityStage,
    vararg dimensions: Pair<ObservabilityDimension, String>
) {
    track(ObservabilityEvent(name, stage, dimensions = mapOf(*dimensions)))
}

fun Observability.record(
    code: ObservabilityErrorCode,
    stage: ObservabilityStage,
    cause: Throwable? = null
) {
    record(
        ObservabilityError(
            code = code,
            stage = stage,
            exceptionType = cause?.javaClass?.simpleName
        )
    )
}
