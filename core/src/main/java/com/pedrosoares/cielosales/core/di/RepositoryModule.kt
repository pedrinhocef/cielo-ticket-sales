package com.pedrosoares.cielosales.core.di

import com.pedrosoares.cielosales.core.data.repository.FakeEventRepository
import com.pedrosoares.cielosales.core.data.repository.RoomPurchaseRepository
import com.pedrosoares.cielosales.core.domain.repository.EventRepository
import com.pedrosoares.cielosales.core.domain.repository.PurchaseRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEventRepository(implementation: FakeEventRepository): EventRepository

    @Binds
    @Singleton
    abstract fun bindPurchaseRepository(implementation: RoomPurchaseRepository): PurchaseRepository
}
