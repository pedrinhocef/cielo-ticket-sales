package com.pedrosoares.cielosales.purchases.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pedrosoares.cielosales.core.domain.model.Purchase
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PurchasesScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun whenPurchasesAreDisplayedAndFilterIsSelected_thenForwardsSelectedFilter() {
        var selectedFilter = PurchaseFilter.ALL
        composeRule.setContent {
            MaterialTheme {
                PurchasesContent(
                    state = PurchasesUiState(
                        purchases = listOf(
                            purchase("approved", "Show aprovado", PurchaseStatus.APPROVED),
                            purchase("pending", "Show pendente", PurchaseStatus.PENDING)
                        )
                    ),
                    onFilterSelected = { selectedFilter = it }
                )
            }
        }

        composeRule.onNodeWithText("Meus ingressos").assertIsDisplayed()
        composeRule.onNodeWithText("Show aprovado").assertIsDisplayed()
        composeRule.onNodeWithText("Show pendente").assertIsDisplayed()
        composeRule.onNodeWithText("Aprovados").performClick()

        assertEquals(PurchaseFilter.APPROVED, selectedFilter)
    }

    @Test
    fun whenSelectedFilterHasNoPurchases_thenDisplaysEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                PurchasesContent(PurchasesUiState(filter = PurchaseFilter.CANCELED), onFilterSelected = {})
            }
        }

        composeRule.onNodeWithText("Nenhum ingresso encontrado para este filtro.").assertIsDisplayed()
    }

    @Test
    fun whenApprovedPurchaseIsSelected_thenDisplaysItsTicketQrCode() {
        composeRule.setContent {
            MaterialTheme {
                PurchasesContent(
                    state = PurchasesUiState(
                        purchases = listOf(purchase("approved", "Show aprovado", PurchaseStatus.APPROVED))
                    ),
                    onFilterSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Show aprovado").performClick()

        composeRule.onNodeWithText("Ingresso").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("QR Code do ingresso").assertIsDisplayed()
    }

    private fun purchase(key: String, eventName: String, status: PurchaseStatus) = Purchase(
        idempotencyKey = key,
        eventId = "event-$key",
        eventName = eventName,
        quantity = 1,
        totalAmountInCents = 10_000,
        paymentStatus = status,
        cieloTransactionId = null
    )
}
