package com.pedrosoares.cielosales.events.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.core.util.UiText
import com.pedrosoares.cielosales.events.R
import com.pedrosoares.cielosales.events.util.QrCodeGenerator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is EventsEffect.LaunchCieloPayment -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, effect.uri)
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onCieloLaunchFailed(effect.idempotencyKey)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.events_title)) }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is UiState.ProcessingPayment -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_payment))
                    }
                }

                is UiState.PaymentPending -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.payment_attention), style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.pending_payment_msg, state.purchase.eventName))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryPayment(state.purchase) }) {
                            Text(stringResource(R.string.retry_payment))
                        }
                        TextButton(onClick = { viewModel.loadEvents() }) {
                            Text(stringResource(R.string.back_to_list))
                        }
                    }
                }

                is UiState.EventList -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        items(state.events) { event ->
                            EventItem(
                                event = event,
                                maxQuantity = EventsViewModel.MAX_QUANTITY_PER_ORDER,
                                onSelect = { qty -> viewModel.startPaymentFlow(event, qty) }
                            )
                        }
                    }
                }

                is UiState.PaymentSuccess -> {
                    val bitmap = remember(state.purchase.idempotencyKey) {
                        QrCodeGenerator.generateQrCode(state.purchase.idempotencyKey)
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.payment_approved), style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.event_label, state.purchase.eventName))
                        Text(stringResource(R.string.quantity_label, state.purchase.quantity))
                        Spacer(modifier = Modifier.height(16.dp))
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_code_description),
                                modifier = Modifier.size(200.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadEvents() }) {
                            Text(stringResource(R.string.new_purchase))
                        }
                    }
                }

                is UiState.PaymentError -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.payment_attention), style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message.asString(context),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        state.retryPurchase?.let { purchase ->
                            Button(onClick = { viewModel.retryPayment(purchase) }) {
                                Text(stringResource(R.string.retry_payment))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(onClick = { viewModel.loadEvents() }) {
                            Text(stringResource(R.string.back_to_list))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventItem(
    event: Event,
    maxQuantity: Int,
    onSelect: (quantity: Int) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = event.title, style = MaterialTheme.typography.titleLarge)
            Text(text = event.location, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (quantity > 1) quantity-- },
                        enabled = quantity > 1
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "$quantity",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { if (quantity < maxQuantity) quantity++ },
                        enabled = quantity < maxQuantity
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }

                val totalCents = event.priceInCents * quantity
                val totalInReais = java.math.BigDecimal(totalCents)
                    .divide(java.math.BigDecimal(100), 2, java.math.RoundingMode.HALF_EVEN)

                val formattedPrice = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                    .format(totalInReais)

                Button(onClick = { onSelect(quantity) }) {
                    Text(stringResource(R.string.pay_amount, formattedPrice))
                }
            }
        }
    }
}
