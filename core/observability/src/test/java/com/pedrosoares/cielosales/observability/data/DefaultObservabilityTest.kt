package com.pedrosoares.cielosales.observability.data

import com.pedrosoares.cielosales.observability.api.ObservabilityError
import com.pedrosoares.cielosales.observability.api.ObservabilityErrorCode
import com.pedrosoares.cielosales.observability.api.ObservabilityEvent
import com.pedrosoares.cielosales.observability.api.ObservabilityEventName
import com.pedrosoares.cielosales.observability.api.ObservabilityStage
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultObservabilityTest {
    @Test
    fun `When one sink fails then should continue notifying the remaining sinks`() {
        val recordingSink = RecordingSink()
        val observability = DefaultObservability(setOf(FailingSink(), recordingSink))
        val event = ObservabilityEvent(
            ObservabilityEventName.PAYMENT_START_REQUESTED,
            ObservabilityStage.PAYMENT_START
        )
        val error = ObservabilityError(
            ObservabilityErrorCode.PAYMENT_START_FAILED,
            ObservabilityStage.PAYMENT_START
        )

        observability.track(event)
        observability.record(error)

        assertEquals(listOf(event), recordingSink.events)
        assertEquals(listOf(error), recordingSink.errors)
    }

    private class FailingSink : ObservabilitySink {
        override fun track(event: ObservabilityEvent) = error("sink failure")
        override fun record(error: ObservabilityError) = error("sink failure")
    }

    private class RecordingSink : ObservabilitySink {
        val events = mutableListOf<ObservabilityEvent>()
        val errors = mutableListOf<ObservabilityError>()

        override fun track(event: ObservabilityEvent) {
            events += event
        }

        override fun record(error: ObservabilityError) {
            errors += error
        }
    }
}
