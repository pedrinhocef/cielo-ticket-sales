package com.pedrosoares.cielosales.observability.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ObservabilityEventDaoTest {
    private lateinit var database: ObservabilityDatabase
    private lateinit var dao: ObservabilityEventDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ObservabilityDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.eventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `When events exceed retention then should keep only the newest entries`() = runTest {
        repeat(5) { index ->
            dao.insertAndTrim(
                ObservabilityEventEntity(
                    name = "EVENT_$index",
                    stage = "TEST",
                    severity = "INFO",
                    dimensions = ""
                ),
                maximumEntries = 3
            )
        }

        assertEquals(listOf("EVENT_4", "EVENT_3", "EVENT_2"), dao.getAll().map { it.name })
    }
}
