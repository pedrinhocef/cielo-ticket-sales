package com.pedrosoares.cielosales.cielo.di

import com.pedrosoares.cielosales.cielo.data.remote.CieloPaymentGateway
import com.pedrosoares.cielosales.core.domain.gateway.PaymentGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CieloGatewayModule {
    @Binds
    abstract fun bindPaymentGateway(implementation: CieloPaymentGateway): PaymentGateway
}
