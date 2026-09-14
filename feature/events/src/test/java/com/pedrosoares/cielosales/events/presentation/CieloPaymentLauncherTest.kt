package com.pedrosoares.cielosales.events.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CieloPaymentLauncherTest {

    @Test
    fun `When no application handles Cielo Deep Link then should report launch failure`() {
        var launchFailed = false
        val effect = EventsEffect.LaunchCieloPayment(
            uri = Uri.parse("lio://payment?request=test"),
            idempotencyKey = "same-reference"
        )
        val context = mockk<Context>()
        every { context.startActivity(any()) } throws ActivityNotFoundException()

        launchCieloPayment(
            context = context,
            effect = effect,
            onLaunchStarted = {},
            onLaunchFailed = { launchFailed = true }
        )

        assertTrue(launchFailed)
    }
}
