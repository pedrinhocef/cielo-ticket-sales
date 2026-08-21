package com.pedrosoares.cielosales.core.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pedrosoares.cielosales.core.data.local.AppDatabase
import com.pedrosoares.cielosales.core.data.local.entity.PurchaseEntity
import com.pedrosoares.cielosales.core.domain.model.PurchaseStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PurchaseDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var purchaseDao: PurchaseDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        purchaseDao = database.purchaseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `When inserting and getting purchase then should return same data`() = runBlocking {
        val purchase = PurchaseEntity(
            idempotencyKey = "key-1",
            eventId = "evt-1",
            eventName = "Event A",
            quantity = 2,
            totalAmountInCents = 2000,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )

        purchaseDao.insertPurchase(purchase)
        val result = purchaseDao.getPurchaseByIdempotencyKey("key-1")

        assertNotNull(result)
        assertEquals(purchase.idempotencyKey, result?.idempotencyKey)
        assertEquals(PurchaseStatus.PENDING, result?.paymentStatus)
    }

    @Test
    fun `When createOrGetPending with new key then should insert`() = runBlocking {
        val purchase = PurchaseEntity(
            idempotencyKey = "new-key",
            eventId = "evt-1",
            eventName = "Event A",
            quantity = 1,
            totalAmountInCents = 1000,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )

        val result = purchaseDao.createOrGetPending(purchase)
        assertEquals("new-key", result.purchase.idempotencyKey)
        assertEquals(true, result.created)

        val dbResult = purchaseDao.getPurchaseByIdempotencyKey("new-key")
        assertNotNull(dbResult)
    }

    @Test
    fun `When updateStatus then should update correct fields`() = runBlocking {
        val purchase = PurchaseEntity(
            idempotencyKey = "key-update",
            eventId = "evt-1",
            eventName = "Event A",
            quantity = 1,
            totalAmountInCents = 1000,
            paymentStatus = PurchaseStatus.PENDING,
            cieloTransactionId = null
        )
        purchaseDao.insertPurchase(purchase)

        purchaseDao.updateStatusIfCurrent(
            key = "key-update",
            currentStatus = PurchaseStatus.PENDING,
            status = PurchaseStatus.APPROVED,
            transactionId = "TX-123",
            reason = null
        )

        val result = purchaseDao.getPurchaseByIdempotencyKey("key-update")
        assertEquals(PurchaseStatus.APPROVED, result?.paymentStatus)
        assertEquals("TX-123", result?.cieloTransactionId)
    }

    @Test
    fun `When purchase is terminal then a late callback should not overwrite it`() = runBlocking {
        val purchase = PurchaseEntity(
            idempotencyKey = "key-terminal",
            eventId = "evt-1",
            eventName = "Event A",
            quantity = 1,
            totalAmountInCents = 1000,
            paymentStatus = PurchaseStatus.APPROVED,
            cieloTransactionId = "TX-123"
        )
        purchaseDao.insertPurchase(purchase)

        val rowsUpdated = purchaseDao.updateStatusIfCurrent(
            key = purchase.idempotencyKey,
            currentStatus = PurchaseStatus.PENDING,
            status = PurchaseStatus.CANCELED,
            transactionId = null,
            reason = "Late callback"
        )

        val stored = purchaseDao.getPurchaseByIdempotencyKey(purchase.idempotencyKey)
        assertEquals(0, rowsUpdated)
        assertEquals(PurchaseStatus.APPROVED, stored?.paymentStatus)
        assertEquals("TX-123", stored?.cieloTransactionId)
    }
}
