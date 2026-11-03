package com.sms.messages

import com.sms.messages.util.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `short codes are preserved verbatim`() {
        assertEquals("900", PhoneNumbers.normalize("900"))
        assertEquals("123456", PhoneNumbers.normalize("123456"))
    }

    @Test
    fun `formatting characters are stripped`() {
        assertEquals("+966512345678", PhoneNumbers.normalize("+966 51 234 5678"))
        assertEquals("0512345678", PhoneNumbers.normalize("(051) 234-5678"))
    }

    @Test
    fun `leading zeros in international prefixes are dropped`() {
        assertEquals("+966512345678", PhoneNumbers.normalize("00966512345678"))
    }

    @Test
    fun `blank and missing input normalises to empty`() {
        assertEquals("", PhoneNumbers.normalize("   "))
        assertEquals("", PhoneNumbers.normalize(null))
    }

    @Test
    fun `validity rejects letters and too few digits`() {
        assertFalse(PhoneNumbers.isValid(""))
        assertFalse(PhoneNumbers.isValid("abc"))
        assertFalse(PhoneNumbers.isValid("12"))
        assertTrue(PhoneNumbers.isValid("0512345678"))
        assertTrue(PhoneNumbers.isValid("900"))
    }

    @Test
    fun `matches treats the same number written differently as equal`() {
        assertTrue(PhoneNumbers.matches("0512345678", "+966512345678"))
        assertFalse(PhoneNumbers.matches("0512345678", "0500000000"))
        assertFalse(PhoneNumbers.matches("", "0512345678"))
    }

    @Test
    fun `format never returns null and keeps short codes`() {
        assertEquals("900", PhoneNumbers.format("900"))
        assertTrue(PhoneNumbers.format("+966512345678").isNotEmpty())
        assertEquals("", PhoneNumbers.format(null))
    }
}
