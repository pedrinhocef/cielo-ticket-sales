package com.pedrosoares.cielosales.purchases.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import com.pedrosoares.cielosales.core.domain.model.PurchaseConstraints
import com.pedrosoares.cielosales.core.ui.TicketQrCodeGenerator
import com.pedrosoares.cielosales.purchases.R
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(viewModel: PurchasesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PurchasesContent(state = state, onFilterSelected = viewModel::selectFilter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchasesContent(
    state: PurchasesUiState,
    onFilterSelected: (PurchaseFilter) -> Unit
) {
    var selectedPurchase by remember { mutableStateOf<Purchase?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.purchases_title)) })
        FilterRow(selected = state.filter, onSelected = onFilterSelected)
        if (state.purchases.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { Text(stringResource(R.string.purchases_empty_state)) }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.purchases, key = { it.idempotencyKey }) { purchase ->
                    PurchaseItem(
                        purchase = purchase,
                        onApprovedPurchaseSelected = { selectedPurchase = it }
                    )
                }
            }
        }
    }

    selectedPurchase?.let { purchase ->
        TicketQrCodeDialog(purchase = purchase, onDismiss = { selectedPurchase = null })
    }
}

@Composable
private fun FilterRow(selected: PurchaseFilter, onSelected: (PurchaseFilter) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().height(54.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PurchaseFilter.entries.forEach { filter ->
                    AssistChip(
                        onClick = { onSelected(filter) },
                        label = { Text(filter.label()) },
                        leadingIcon = if (filter == selected) ({ Text("✓") }) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun PurchaseItem(
    purchase: Purchase,
    onApprovedPurchaseSelected: (Purchase) -> Unit
) {
    val isApproved = purchase.paymentStatus == PurchaseStatus.APPROVED
    val openTicketLabel = stringResource(R.string.ticket_qr_open, purchase.eventName)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isApproved) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = openTicketLabel
                    ) { onApprovedPurchaseSelected(purchase) }
                }
                else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(purchase.eventName, style = MaterialTheme.typography.titleMedium)
                Text(purchase.paymentStatus.label(), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.purchase_quantity_and_total, purchase.quantity, formatMoney(purchase.totalAmountInCents)))
            Text(formatDate(purchase.timestamp), style = MaterialTheme.typography.bodySmall)
            purchase.reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TicketQrCodeDialog(purchase: Purchase, onDismiss: () -> Unit) {
    val bitmap = remember(purchase.idempotencyKey) {
        TicketQrCodeGenerator.generate(purchase.idempotencyKey)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ticket_qr_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(purchase.eventName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.ticket_qr_description),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ticket_qr_close)) }
        }
    )
}

@Composable
private fun PurchaseFilter.label(): String = stringResource(when (this) {
    PurchaseFilter.ALL -> R.string.purchase_filter_all
    PurchaseFilter.APPROVED -> R.string.purchase_filter_approved
    PurchaseFilter.PENDING -> R.string.purchase_filter_pending
    PurchaseFilter.CANCELED -> R.string.purchase_filter_canceled
    PurchaseFilter.DENIED -> R.string.purchase_filter_denied
    PurchaseFilter.FAILED -> R.string.purchase_filter_failed
})

@Composable
private fun PurchaseStatus.label(): String = stringResource(when (this) {
    PurchaseStatus.PENDING -> R.string.purchase_status_pending
    PurchaseStatus.APPROVED -> R.string.purchase_status_approved
    PurchaseStatus.DENIED -> R.string.purchase_status_denied
    PurchaseStatus.CANCELED -> R.string.purchase_status_canceled
    PurchaseStatus.FAILED_TECHNICAL -> R.string.purchase_status_failed
})

private fun formatMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    .format(cents.toBigDecimal().divide(PurchaseConstraints.CENTS_PER_BRL.toBigDecimal()))

private fun formatDate(timestamp: Long): String = java.text.DateFormat.getDateTimeInstance(
    java.text.DateFormat.MEDIUM,
    java.text.DateFormat.SHORT,
    Locale.forLanguageTag("pt-BR")
).format(Date(timestamp))

@Preview(showBackground = true)
@Composable
private fun PurchasesContentPreview() {
    MaterialTheme {
        PurchasesContent(
            state = PurchasesUiState(
                purchases = listOf(
                    previewPurchase("approved", "Festival de Verão", PurchaseStatus.APPROVED),
                    previewPurchase(
                        "pending",
                        "Show de Jazz",
                        PurchaseStatus.PENDING,
                        quantity = 2
                    ),
                    previewPurchase(
                        "denied",
                        "Teatro Municipal",
                        PurchaseStatus.DENIED,
                        reason = "Pagamento negado"
                    )
                ),
                filter = PurchaseFilter.ALL
            ),
            onFilterSelected = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PurchasesEmptyContentPreview() {
    MaterialTheme {
        PurchasesContent(
            state = PurchasesUiState(
                purchases = emptyList(),
                filter = PurchaseFilter.APPROVED
            ),
            onFilterSelected = {}
        )
    }
}

private fun previewPurchase(
    idempotencyKey: String,
    eventName: String,
    status: PurchaseStatus,
    quantity: Int = 1,
    reason: String? = null
) = Purchase(
    idempotencyKey = idempotencyKey,
    eventId = "preview-event-$idempotencyKey",
    eventName = eventName,
    quantity = quantity,
    totalAmountInCents = 15_000,
    paymentStatus = status,
    cieloTransactionId = null,
    reason = reason,
    timestamp = 0
)
