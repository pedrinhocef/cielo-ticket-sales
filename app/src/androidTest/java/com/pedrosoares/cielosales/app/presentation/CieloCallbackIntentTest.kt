package com.pedrosoares.cielosales.app.presentation

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CieloCallbackIntentTest {
    @Test
    fun whenCallbackDeepLinkIsReceived_thenResolvesToResponseActivity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("cielotickets://payment-response?response=test")
        ).setPackage(appContext.packageName)

        val resolvedActivity = appContext.packageManager.resolveActivity(intent, 0)

        assertEquals(CieloResponseActivity::class.java.name, resolvedActivity?.activityInfo?.name)
    }
}
