package com.pedrosoares.cielosales.core.domain.repository

import com.pedrosoares.cielosales.core.domain.model.Event
import kotlinx.coroutines.flow.Flow

/** Source of events shown by the sales flow. The MVP uses a local fake implementation. */
interface EventRepository {
    fun observeEvents(): Flow<List<Event>>
}
