package com.pedrosoares.cielosales.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getPurchaseByIdempotencyKey(key: String): PurchaseEntity?

    @Transaction
    suspend fun createOrGetPending(purchase: PurchaseEntity): CreateOrGetPurchase {
        val inserted = insertPurchase(purchase)
        val stored = if (inserted != -1L) purchase else getPurchaseByIdempotencyKey(purchase.idempotencyKey)
            ?: error("Purchase was not found after an ignored insert")
        return CreateOrGetPurchase(purchase = stored, created = inserted != -1L)
    }

    @Query("UPDATE purchases SET paymentStatus = :status, cieloTransactionId = :transactionId, reason = :reason WHERE idempotencyKey = :key AND paymentStatus = :currentStatus")
    suspend fun updateStatusIfCurrent(
        key: String,
        currentStatus: PurchaseStatus,
        status: PurchaseStatus,
        transactionId: String? = null,
        reason: String?
    ): Int

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE paymentStatus = :status ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getPurchasesByStatus(status: PurchaseStatus, limit: Int): List<PurchaseEntity>
}

data class CreateOrGetPurchase(
    val purchase: PurchaseEntity,
    val created: Boolean
)
