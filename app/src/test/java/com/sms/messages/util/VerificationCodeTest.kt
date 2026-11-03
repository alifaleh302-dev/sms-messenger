package com.sms.messages.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The "copy code" action must fire only for real verification codes. Anything else would
 * offer to copy a price, a date or a phone number out of an ordinary message.
 */
class VerificationCodeTest {

    @Test
    fun `extracts a code from a typical English message`() {
        assertEquals(
            "482913",
            VerificationCode.extract("Your verification code is 482913. Do not share it."),
        )
    }

    @Test
    fun `extracts a code from a typical Arabic message`() {
        assertEquals(
            "771204",
            VerificationCode.extract("رمز التحقق الخاص بك هو 771204"),
        )
    }

    @Test
    fun `extracts a code written with an otp prefix`() {
        assertEquals("9021", VerificationCode.extract("OTP: 9021"))
    }

    @Test
    fun `ignores a plain message with numbers but no code wording`() {
        assertNull(VerificationCode.extract("Your order 55231 shipped on 2024"))
    }

    @Test
    fun `ignores an empty message`() {
        assertNull(VerificationCode.extract(""))
        assertNull(VerificationCode.extract("   "))
    }

    @Test
    fun `ignores a code shorter than four digits`() {
        assertNull(VerificationCode.extract("Your code is 123"))
    }

    @Test
    fun `does not stitch digits out of a longer run`() {
        // A 12-digit tracking number must not yield a 4-8 digit slice.
        assertNull(VerificationCode.extract("Your verification tracking number is 123456789012"))
    }

    @Test
    fun `picks the first code when a message repeats it`() {
        assertEquals("4455", VerificationCode.extract("Verification code 4455. Code 4455 again."))
    }
}
