package com.pedrosoares.cielosales.events.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.pedrosoares.cielosales.cielo.PaymentManager
import com.pedrosoares.cielosales.core.domain.model.Event
import com.pedrosoares.cielosales.events.util.QrCodeGenerator
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    viewModel: EventsViewModel,
    paymentManager: PaymentManager
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedEvent by remember { mutableStateOf<Event?>(null) }
    var selectedQuantity by remember { mutableIntStateOf(1) }
    var currentIdempotencyKey by remember { mutableStateOf("") }

    val paymentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        selectedEvent?.let { event ->
            val paymentResult = paymentManager.parsePaymentResult(
                resultCode = result.resultCode,
                data = result.data,
                idempotencyKey = currentIdempotencyKey
            )
            viewModel.processPayment(paymentResult, event, selectedQuantity)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ingressos Cielo Lio") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is UiState.EventList -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        items(state.events) { event ->
                            EventItem(
                                event = event,
                                onSelect = { qty ->
                                    selectedEvent = event
                                    selectedQuantity = qty
                                    currentIdempotencyKey = UUID.randomUUID().toString()
                                    val intent = paymentManager.createCieloPaymentIntent(
                                        amountInCents = event.priceInCents * qty,
                                        idempotencyKey = currentIdempotencyKey
                                    )
                                    paymentLauncher.launch(intent)
                                }
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
                        Text("Pagamento Aprovado!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Evento: ${state.purchase.eventName}")
                        Text("Qtd: ${state.purchase.quantity}")
                        Spacer(modifier = Modifier.height(16.dp))
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR Code Ingresso",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadEvents() }) {
                            Text("Nova Compra")
                        }
                    }
                }
                is UiState.PaymentError -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Erro no pagamento:", style = MaterialTheme.typography.titleMedium)
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadEvents() }) {
                            Text("Voltar")
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
                    IconButton(onClick = { quantity++ }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }

                val totalCents = event.priceInCents * quantity
                val formattedPrice = String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", totalCents / 100.0)

                Button(onClick = { onSelect(quantity) }) {
                    Text("Pagar $formattedPrice")
                }
            }
        }
    }
}
