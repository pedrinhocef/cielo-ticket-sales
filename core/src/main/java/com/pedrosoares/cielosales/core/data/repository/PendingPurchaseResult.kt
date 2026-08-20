package com.pedrosoares.cielosales.core.data.repository

import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity

sealed interface PendingPurchaseResult {
    data class Created(val purchase: PurchaseEntity) : PendingPurchaseResult
    data class ExistingPending(val purchase: PurchaseEntity) : PendingPurchaseResult
    data class ExistingTerminal(val purchase: PurchaseEntity) : PendingPurchaseResult
}
