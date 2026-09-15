package com.pedrosoares.cielosales.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TicketQrCodeGeneratorTest {
    @Test
    fun `When QR content is invalid then should notify the diagnostic callback`() {
        var reportedException: Exception? = null

        val bitmap = TicketQrCodeGenerator.generate("") { reportedException = it }

        assertNull(bitmap)
        assertEquals(IllegalArgumentException::class.java, reportedException?.javaClass)
    }
}
