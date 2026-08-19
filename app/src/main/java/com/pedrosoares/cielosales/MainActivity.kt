package com.pedrosoares.cielosales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pedrosoares.cielosales.cielo.PaymentManager
import com.pedrosoares.cielosales.events.presentation.EventsScreen
import com.pedrosoares.cielosales.events.presentation.EventsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var paymentManager: PaymentManager

    private val viewModel: EventsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EventsScreen(
                viewModel = viewModel,
                paymentManager = paymentManager
            )
        }
    }
}