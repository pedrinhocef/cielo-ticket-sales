package com.pedrosoares.cielosales.core.domain.model

data class Event(
    val id: String,
    val title: String,
    val location: String,
    val priceInCents: Long,
    val imageUrl: String
)
