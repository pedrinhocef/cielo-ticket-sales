package com.pedrosoares.cielosales.core.data.repository

import com.pedrosoares.cielosales.core.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class EventRepository @Inject constructor() {
    fun getEvents(): Flow<List<Event>> = flow {
        val mockEvents = listOf(
            Event("1", "Silva Live Show", "Saquarema Arena", 12000L, "https://picsum.photos/300/200"),
            Event("2", "Winter Festival", "Convention Center", 25000L, "https://picsum.photos/300/201"),
            Event("3", "Children's Theater", "Municipal Theater", 4500L, "https://picsum.photos/300/202")
        )
        emit(mockEvents)
    }
}
