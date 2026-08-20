package com.pedrosoares.cielosales.cielo.data.config

import com.pedrosoares.cielosales.cielo.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CieloConfig @Inject constructor() {
    val clientId: String = BuildConfig.CIELO_CLIENT_ID
    val accessToken: String = BuildConfig.CIELO_ACCESS_TOKEN
    val merchantCode: String? = null
}
