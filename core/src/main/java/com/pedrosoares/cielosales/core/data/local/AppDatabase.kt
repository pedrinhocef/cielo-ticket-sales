package com.pedrosoares.cielosales.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pedrosoares.cielosales.core.data.local.dao.PurchaseDao
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity

@Database(entities = [PurchaseEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
}
