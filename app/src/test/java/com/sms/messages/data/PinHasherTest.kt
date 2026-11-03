package com.sms.messages.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The PIN is the only thing standing between a locked phone and the messages, so the hash
 * has to do two things: accept the right PIN, and never store the PIN itself.
 */
@RunWith(RobolectricTestRunner::class)
class PinHasherTest {

    @Test
    fun `the correct pin verifies`() {
        val stored = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", stored))
    }

    @Test
    fun `a wrong pin does not verify`() {
        val stored = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("4321", stored))
    }

    @Test
    fun `the stored value never contains the pin`() {
        val stored = PinHasher.hash("1234")
        assertNotEquals("1234", stored)
        assertFalse("the digits must not appear in the clear", stored.contains("1234"))
    }

    @Test
    fun `the same pin hashes differently each time`() {
        // A per-install salt means one precomputed table cannot cover every install.
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test
    fun `a malformed stored value fails closed`() {
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "not-a-hash"))
        assertFalse(PinHasher.verify("1234", "!!!:!!!"))
    }
}
