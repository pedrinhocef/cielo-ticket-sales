package com.pedrosoares.cielosales

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pedrosoares.cielosales.cielo.data.remote.CieloDeepLinkContract
import com.pedrosoares.cielosales.events.presentation.EventsScreen
import com.pedrosoares.cielosales.events.presentation.EventsViewModel
import com.pedrosoares.cielosales.ui.theme.CieloTicketSalesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val eventsViewModel: EventsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            CieloTicketSalesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    EventsScreen(viewModel = eventsViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == CieloDeepLinkContract.CALLBACK_SCHEME &&
                uri.host == CieloDeepLinkContract.CALLBACK_HOST
            ) {
                eventsViewModel.onPaymentResultReceived(uri)
            }
        }
    }
}
