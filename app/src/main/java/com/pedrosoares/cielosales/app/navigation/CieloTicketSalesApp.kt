package com.pedrosoares.cielosales.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pedrosoares.cielosales.R
import com.pedrosoares.cielosales.events.presentation.EventsScreen
import com.pedrosoares.cielosales.events.presentation.EventsViewModel
import com.pedrosoares.cielosales.purchases.presentation.PurchasesScreen
import com.pedrosoares.cielosales.purchases.presentation.PurchasesViewModel

private enum class AppDestination(
    val route: String,
    @StringRes val labelResId: Int,
    val icon: String
) {
    EVENTS("events", R.string.navigation_events, "◫"),
    PURCHASES("purchases", R.string.navigation_purchases, "▣")
}

@Composable
fun CieloTicketSalesApp(
    eventsViewModel: EventsViewModel,
    purchasesViewModel: PurchasesViewModel
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(AppDestination.EVENTS.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.icon) },
                        label = { Text(stringResource(destination.labelResId)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.EVENTS.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppDestination.EVENTS.route) { EventsScreen(eventsViewModel) }
            composable(AppDestination.PURCHASES.route) { PurchasesScreen(purchasesViewModel) }
        }
    }
}
