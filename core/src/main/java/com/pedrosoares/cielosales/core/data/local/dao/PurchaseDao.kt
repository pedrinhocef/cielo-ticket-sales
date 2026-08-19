package com.pedrosoares.cielosales.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(purchase: PurchaseEntity)

    @Query("SELECT * FROM purchases WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getPurchaseByIdempotencyKey(key: String): PurchaseEntity?

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>
}
