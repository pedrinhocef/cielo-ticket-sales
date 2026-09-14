package com.pedrosoares.cielosales.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pedrosoares.cielosales.app.navigation.CieloTicketSalesApp
import com.pedrosoares.cielosales.cielo.data.remote.CieloDeepLinkContract
import com.pedrosoares.cielosales.events.presentation.EventsViewModel
import com.pedrosoares.cielosales.purchases.presentation.PurchasesViewModel
import com.pedrosoares.cielosales.ui.theme.CieloTicketSalesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val eventsViewModel: EventsViewModel by viewModels()
    private val purchasesViewModel: PurchasesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { CieloTicketSalesTheme { CieloTicketSalesApp(eventsViewModel, purchasesViewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.takeIf { uri ->
            uri.scheme == CieloDeepLinkContract.CALLBACK_SCHEME && uri.host == CieloDeepLinkContract.CALLBACK_HOST
        }?.let(eventsViewModel::onPaymentResultReceived)
    }
}
