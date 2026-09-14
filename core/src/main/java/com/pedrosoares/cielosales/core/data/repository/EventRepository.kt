package com.pedrosoares.cielosales.core.data.repository

import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class FakeEventRepository @Inject constructor() : EventRepository {
    override fun observeEvents(): Flow<List<Event>> = flow {
        val mockEvents = listOf(
            Event("event-001", "Silva Live Show", "Saquarema Arena", 12000L, "https://picsum.photos/seed/silva/800/450"),
            Event("event-002", "Winter Festival", "Convention Center", 25000L, "https://picsum.photos/seed/winter/800/450"),
            Event("event-003", "Children's Theater", "Municipal Theater", 4500L, "https://picsum.photos/seed/theater/800/450"),
            Event("event-004", "Jazz Under the Stars", "Riverside Park", 18000L, "https://picsum.photos/seed/jazz/800/450"),
            Event("event-005", "Tech Innovation Summit", "Expo Center", 35000L, "https://picsum.photos/seed/tech/800/450"),
            Event("event-006", "Comedy Night", "Downtown Theater", 9000L, "https://picsum.photos/seed/comedy/800/450"),
            Event("event-007", "Indie Music Weekend", "Open Air Stage", 22000L, "https://picsum.photos/seed/indie/800/450"),
            Event("event-008", "Art and Design Expo", "Cultural Hall", 15000L, "https://picsum.photos/seed/art/800/450")
        )
        emit(mockEvents)
    }
}
