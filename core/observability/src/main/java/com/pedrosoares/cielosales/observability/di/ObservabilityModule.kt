package com.pedrosoares.cielosales.observability.di

import android.content.Context
import androidx.room.Room
import com.pedrosoares.cielosales.observability.api.Observability
import com.pedrosoares.cielosales.observability.data.DefaultObservability
import com.pedrosoares.cielosales.observability.data.LocalObservabilitySink
import com.pedrosoares.cielosales.observability.data.LogcatObservabilitySink
import com.pedrosoares.cielosales.observability.data.ObservabilitySink
import com.pedrosoares.cielosales.observability.data.local.ObservabilityDatabase
import com.pedrosoares.cielosales.observability.data.local.ObservabilityEventDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservabilityModule {
    @Binds
    @Singleton
    internal abstract fun bindObservability(implementation: DefaultObservability): Observability

    @Binds
    @IntoSet
    internal abstract fun bindLogcatSink(implementation: LogcatObservabilitySink): ObservabilitySink

    @Binds
    @IntoSet
    internal abstract fun bindLocalSink(implementation: LocalObservabilitySink): ObservabilitySink

    companion object {
        @Provides
        @Singleton
        internal fun provideDatabase(@ApplicationContext context: Context): ObservabilityDatabase =
            Room.databaseBuilder(
                context,
                ObservabilityDatabase::class.java,
                "cielo_observability.db"
            ).fallbackToDestructiveMigration(dropAllTables = true).build()

        @Provides
        internal fun provideEventDao(database: ObservabilityDatabase): ObservabilityEventDao =
            database.eventDao()
    }
}
