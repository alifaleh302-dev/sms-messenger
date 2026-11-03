package com.sms.messages

import com.sms.messages.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimeFormatTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `recent messages use compact relative labels`() {
        assertEquals("now", TimeFormat.relative(now - 10_000, now))
        assertEquals("5m", TimeFormat.relative(now - TimeUnit.MINUTES.toMillis(5), now))
        assertEquals("3h", TimeFormat.relative(now - TimeUnit.HOURS.toMillis(3), now))
        assertEquals("2d", TimeFormat.relative(now - TimeUnit.DAYS.toMillis(2), now))
    }

    @Test
    fun `older messages fall back to a date instead of an empty label`() {
        val label = TimeFormat.relative(now - TimeUnit.DAYS.toMillis(30), now)
        assertTrue(label.isNotEmpty())
        assertTrue(label.any { it.isDigit() })
    }

    @Test
    fun `day label recognises today and yesterday`() {
        assertEquals("Today", TimeFormat.dayLabel(now, now))
        assertEquals("Yesterday", TimeFormat.dayLabel(now - TimeUnit.DAYS.toMillis(1), now))
    }

    @Test
    fun `clock is zero padded`() {
        assertEquals(5, TimeFormat.clock(now).length)
    }
}
