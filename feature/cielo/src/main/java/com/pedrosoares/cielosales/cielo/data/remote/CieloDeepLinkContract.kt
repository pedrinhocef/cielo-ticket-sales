package com.pedrosoares.cielosales.cielo.data.remote

import android.net.Uri

object CieloDeepLinkContract {
    const val PAYMENT_SCHEME = "lio"
    const val PAYMENT_HOST = "payment"
    const val REQUEST_PARAMETER = "request"
    const val CALLBACK_PARAMETER = "urlCallback"

    const val CALLBACK_SCHEME = "cielotickets"
    const val CALLBACK_HOST = "payment-response"

    val callbackUri: Uri = Uri.Builder()
        .scheme(CALLBACK_SCHEME)
        .authority(CALLBACK_HOST)
        .build()
}
