package com.pedrosoares.cielosales.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.pedrosoares.cielosales.cielo.data.remote.CieloDeepLinkContract
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CieloResponseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.takeIf { uri ->
            uri.scheme == CieloDeepLinkContract.CALLBACK_SCHEME && uri.host == CieloDeepLinkContract.CALLBACK_HOST
        }?.let { uri ->
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = uri
            })
        }
        finish()
    }
}
