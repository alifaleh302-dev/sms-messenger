package com.sms.messages.sms

import android.app.Application
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSmsManager

/**
 * Covers the guards in [SmsSender]. The multipart split itself delegates to
 * `SmsManager.divideMessage`, which Robolectric does not implement, so these tests verify
 * that the sender degrades to a clean failure instead of crashing when the platform cannot
 * cooperate — and that nothing is ever handed to the platform in that case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmsSenderTest {

    private lateinit var context: Application
    private lateinit var sender: SmsSender
    private lateinit var smsShadow: ShadowSmsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sender = SmsSender(context)
        smsShadow = shadowOf(context.getSystemService(SmsManager::class.java))
        smsShadow.clearLastSentTextMessageParams()
        smsShadow.clearLastSentMultipartTextMessageParams()
    }

    private fun enableTelephony(enabled: Boolean) {
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_TELEPHONY, enabled)
    }

    private fun message(body: String) = MessageEntity(
        id = 1L,
        conversationId = 1L,
        address = "+966512345678",
        body = body,
        timestamp = 1_000L,
        type = MessageType.OUTBOX,
        status = MessageStatus.PENDING,
    )

    @Test
    fun `a device without telephony reports a failure and never touches the platform`() {
        enableTelephony(false)
        assertTrue(!sender.hasTelephony())

        val result = sender.send(message("hello"), -1, requestDeliveryReport = false)

        assertTrue("expected a failure result", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertNull(smsShadow.lastSentTextMessageParams)
        assertNull(smsShadow.lastSentMultipartTextMessageParams)
    }

    @Test
    fun `telephony is detected through the package manager`() {
        enableTelephony(true)
        assertTrue(sender.hasTelephony())
    }

    @Test
    fun `a message the platform cannot split fails cleanly instead of crashing`() {
        enableTelephony(true)

        val result = sender.send(message("hello there"), -1, requestDeliveryReport = false)

        // Robolectric does not implement divideMessage, so the sender must fail safely.
        assertTrue("expected a failure result", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertNull("nothing must be sent when the split fails", smsShadow.lastSentTextMessageParams)
        assertNull(smsShadow.lastSentMultipartTextMessageParams)
    }

    @Test
    fun `part count falls back to one when the platform cannot divide`() {
        enableTelephony(true)
        assertEquals(1, sender.partCount("short", -1))
        assertEquals(1, sender.partCount("y".repeat(500), -1))
    }

    @Test
    fun `part count falls back to one without telephony`() {
        enableTelephony(false)
        assertEquals(1, sender.partCount("anything", -1))
    }
}
