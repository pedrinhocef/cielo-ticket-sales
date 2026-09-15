package com.pedrosoares.cielosales.observability.data

import android.util.Log
import com.pedrosoares.cielosales.observability.api.ObservabilityError
import com.pedrosoares.cielosales.observability.api.ObservabilityEvent
import com.pedrosoares.cielosales.observability.data.local.ObservabilityEventDao
import com.pedrosoares.cielosales.observability.data.local.ObservabilityEventEntity
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

internal interface ObservabilitySink {
    fun track(event: ObservabilityEvent)
    fun record(error: ObservabilityError)
}

@Singleton
internal class LogcatObservabilitySink @Inject constructor() : ObservabilitySink {
    override fun track(event: ObservabilityEvent) {
        Log.i(LOG_TAG, "event=${event.name} stage=${event.stage} severity=${event.severity} dimensions=${event.safeDimensions()}")
    }

    override fun record(error: ObservabilityError) {
        Log.e(LOG_TAG, "error=${error.code} stage=${error.stage} exception=${error.exceptionType.orEmpty()}")
    }

    private companion object {
        const val LOG_TAG = "CieloObservability"
    }
}

@Singleton
internal class LocalObservabilitySink @Inject constructor(
    private val dao: ObservabilityEventDao
) : ObservabilitySink {
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        Thread(it, "cielo-observability").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun track(event: ObservabilityEvent) {
        scope.launch {
            runCatching {
                dao.insertAndTrim(event.toEntity(), MAXIMUM_LOCAL_EVENTS)
            }
        }
    }

    override fun record(error: ObservabilityError) {
        scope.launch {
            runCatching {
                dao.insertAndTrim(error.toEntity(), MAXIMUM_LOCAL_EVENTS)
            }
        }
    }

    private companion object {
        const val MAXIMUM_LOCAL_EVENTS = 300
    }
}

private fun ObservabilityEvent.toEntity() = ObservabilityEventEntity(
    name = name.name,
    stage = stage.name,
    severity = severity.name,
    dimensions = safeDimensions()
)

private fun ObservabilityError.toEntity() = ObservabilityEventEntity(
    name = "ERROR_RECORDED",
    stage = stage.name,
    severity = "ERROR",
    dimensions = "",
    errorCode = code.name,
    exceptionType = exceptionType?.take(MAXIMUM_DIMENSION_LENGTH)
)

private fun ObservabilityEvent.safeDimensions(): String = dimensions.entries
    .sortedBy { it.key.name }
    .joinToString(separator = ",") { (key, value) ->
        "${key.name}=${value.replace('\n', ' ').replace('\r', ' ').take(MAXIMUM_DIMENSION_LENGTH)}"
    }

private const val MAXIMUM_DIMENSION_LENGTH = 64
