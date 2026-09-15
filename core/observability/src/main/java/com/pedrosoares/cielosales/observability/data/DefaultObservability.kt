package com.pedrosoares.cielosales.observability.data

import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.api.ObservabilityError
import com.pedrosoares.cielosales.observability.api.ObservabilityEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultObservability @Inject constructor(
    private val sinks: Set<@JvmSuppressWildcards ObservabilitySink>
) : Observability {
    override fun track(event: ObservabilityEvent) {
        sinks.forEach { sink -> runCatching { sink.track(event) } }
    }

    override fun record(error: ObservabilityError) {
        sinks.forEach { sink -> runCatching { sink.record(error) } }
    }
}
