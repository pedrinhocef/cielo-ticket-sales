package com.pedrosoares.cielosales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.pedrosoares.cielosales.cielo.data.remote.CieloDeepLinkContract
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CieloResponseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.data
            ?.takeIf {
                it.scheme == CieloDeepLinkContract.CALLBACK_SCHEME &&
                    it.host == CieloDeepLinkContract.CALLBACK_HOST
            }
            ?.let { uri ->
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = uri
            }
            startActivity(mainIntent)
        }
        finish()
    }
}
